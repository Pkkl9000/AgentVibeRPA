from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
from typing import Optional, List
import yaml
from pathlib import Path

app = FastAPI(
    title="Primo RPA Activity Catalog",
    description="Сервис каталога активностей с разделением по доменам",
    version="1.0.0",
)

BASE_DIR = Path(__file__).parent
CATALOG_PATH = BASE_DIR / "primo_catalog.yaml"
DOMAINS_DIR = BASE_DIR / "domains"

_cache = None


def load_catalog():
    """Загружает манифест и все файлы доменов из папки domains/."""
    global _cache
    if _cache is not None:
        return _cache

    # 1. Загружаем манифест платформы
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        manifest = yaml.safe_load(f)

    # 2. Сканируем папку domains/ и загружаем все YAML-файлы
    domains = {}
    if not DOMAINS_DIR.exists():
        DOMAINS_DIR.mkdir(parents=True, exist_ok=True)

    for domain_file in DOMAINS_DIR.glob("*.yaml"):
        try:
            with open(domain_file, "r", encoding="utf-8") as f:
                domain_data = yaml.safe_load(f)
            
            if not domain_data or "domain" not in domain_data:
                continue
            
            domain_id = domain_data["domain"]
            # Добавляем поле domain в каждую активность (для удобства API)
            for activity in domain_data.get("activities", []):
                activity["domain"] = domain_id
            
            domains[domain_id] = {
                "id": domain_id,
                "name": domain_data.get("name", domain_id),
                "description": domain_data.get("description", ""),
                "activities": domain_data.get("activities", []),
            }
        except Exception as e:
            print(f"⚠️ Ошибка загрузки домена из {domain_file.name}: {e}")

    _cache = {
        "manifest": manifest,
        "domains": domains,
    }
    return _cache


def reload_catalog():
    global _cache
    _cache = None
    return load_catalog()


def _all_activities():
    """Возвращает плоский список всех активностей из всех доменов."""
    catalog = load_catalog()
    result = []
    for domain_data in catalog["domains"].values():
        result.extend(domain_data["activities"])
    return result


# ==================== DTO ====================

class DomainInfo(BaseModel):
    id: str
    name: str
    description: str
    activity_count: int


class ThinActivity(BaseModel):
    id: str
    name: str
    domain: str
    short: str
    is_container: bool


# ==================== Эндпоинты ====================

@app.get("/health")
def health():
    catalog = load_catalog()
    manifest = catalog["manifest"]
    total_activities = sum(len(d["activities"]) for d in catalog["domains"].values())
    return {
        "status": "ok",
        "platform": manifest.get("platform", "Unknown"),
        "version": manifest.get("version", "Unknown"),
        "updated": manifest.get("updated", "Unknown"),
        "domains_count": len(catalog["domains"]),
        "activities_count": total_activities,
    }


@app.get("/domains", response_model=List[DomainInfo])
def get_domains():
    """Список всех доменов с количеством активностей."""
    catalog = load_catalog()
    return [
        DomainInfo(
            id=d["id"],
            name=d["name"],
            description=d["description"],
            activity_count=len(d["activities"]),
        )
        for d in catalog["domains"].values()
    ]


@app.get("/domains/{domain_id}")
def get_domain(domain_id: str):
    """Детали конкретного домена (с тонкими карточками активностей)."""
    catalog = load_catalog()
    if domain_id not in catalog["domains"]:
        raise HTTPException(404, f"Домен '{domain_id}' не найден")
    
    domain = catalog["domains"][domain_id]
    return {
        "id": domain["id"],
        "name": domain["name"],
        "description": domain["description"],
        "activities": [
            {
                "id": a["id"],
                "name": a["name"],
                "short": a["short"],
                "is_container": a["is_container"],
            }
            for a in domain["activities"]
        ],
    }


@app.get("/activities", response_model=List[ThinActivity])
def list_activities(
    domain: Optional[str] = Query(None, description="Фильтр по домену"),
    container_only: Optional[bool] = Query(None),
    tags: Optional[str] = Query(None, description="Фильтр по тегу (через запятую)"),
):
    """Тонкий список активностей с фильтрами."""
    activities = _all_activities()

    if domain:
        activities = [a for a in activities if a.get("domain") == domain]
    if container_only is not None:
        activities = [a for a in activities if a.get("is_container") == container_only]
    if tags:
        tag_list = [t.strip().lower() for t in tags.split(",")]
        activities = [
            a for a in activities
            if any(t in [at.lower() for at in a.get("tags", [])] for t in tag_list)
        ]

    return [
        ThinActivity(
            id=a["id"],
            name=a["name"],
            domain=a["domain"],
            short=a["short"],
            is_container=a["is_container"],
        )
        for a in activities
    ]


@app.get("/activities/{activity_id}")
def get_activity_card(activity_id: str, thin: bool = Query(False)):
    """Полная карточка активности по ID."""
    for a in _all_activities():
        if a["id"] == activity_id:
            if thin:
                return {
                    "id": a["id"],
                    "name": a["name"],
                    "domain": a["domain"],
                    "short": a["short"],
                    "is_container": a["is_container"],
                }
            return a

    available = [a["id"] for a in _all_activities()]
    raise HTTPException(
        404,
        detail={"error": f"Активность '{activity_id}' не найдена", "available_ids": available},
    )


@app.get("/search")
def search(q: str = Query(..., min_length=2), limit: int = Query(20, le=100)):
    """Нечёткий поиск по name, short, tags, notes."""
    q_lower = q.lower()
    results = []
    for a in _all_activities():
        text = " ".join([
            a.get("name", ""),
            a.get("short", ""),
            a.get("notes", ""),
            " ".join(a.get("tags", [])),
        ]).lower()
        if q_lower in text or q_lower in a["id"].lower():
            results.append(
                ThinActivity(
                    id=a["id"],
                    name=a["name"],
                    domain=a["domain"],
                    short=a["short"],
                    is_container=a["is_container"],
                )
            )
            if len(results) >= limit:
                break
    return results


@app.post("/reload")
def reload():
    """Перезагружает каталог из файлов."""
    catalog = reload_catalog()
    total = sum(len(d["activities"]) for d in catalog["domains"].values())
    return {
        "status": "reloaded",
        "domains_count": len(catalog["domains"]),
        "activities_count": total,
    }