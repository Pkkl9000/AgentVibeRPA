import re
import codecs
import httpx

# Обратносовместимый импорт для разных версий MCP SDK
try:
    from mcp.server.fastmcp import FastMCP as MCPServer
except ImportError:
    from mcp.server.mcpserver import MCPServer

mcp = MCPServer("PrimoRPA_Generator")

CATALOG_API_URL = "http://localhost:8000"
_DECL_ENCODING = re.compile(r'<\?xml[^>]*encoding\s*=\s*["\']([^"\']+)["\']', re.IGNORECASE)


def _resolve_encoding(content: str) -> str:
    """Определяет кодировку из XML-декларации. Primo требует utf-16."""
    match = _DECL_ENCODING.search(content)
    if not match:
        return "utf-8"
    declared = match.group(1).strip().lower()
    if declared in ("utf-16", "utf16"):
        return "utf-16"
    if declared in ("utf-16le", "utf16le"):
        return "utf-16-le"
    if declared in ("utf-16be", "utf16be"):
        return "utf-16-be"
    if declared in ("utf-8", "utf8"):
        return "utf-8"
    try:
        return codecs.lookup(declared).name
    except LookupError:
        return "utf-8"


@mcp.tool()
async def get_activity_card(activity_id: str) -> str:
    """
    Получает полную карточку активности (xmlSkeleton, параметры) по её ID.
    Используй это для каждого шага из plan.json перед генерацией XML.
    
    Args:
        activity_id: ID активности из каталога (например, WFAttachExcel, WFIf, WFAddToLog)
    """
    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(f"{CATALOG_API_URL}/activities/{activity_id}")
            response.raise_for_status()
            return response.text
        except httpx.HTTPStatusError as e:
            return f"Ошибка: Активность '{activity_id}' не найдена. Доступные: {e.response.text}"
        except Exception as e:
            return f"Ошибка соединения с каталогом: {str(e)}"


@mcp.tool()
async def list_activities(domain: str = None) -> str:
    """
    Возвращает краткий список активностей для указанного домена.
    Доступные домены: workflow, excel, mail, system, data, browser.
    
    Args:
        domain: Домен активностей. Если не указан — все активности.
    """
    async with httpx.AsyncClient() as client:
        try:
            params = {"domain": domain} if domain else {}
            response = await client.get(f"{CATALOG_API_URL}/activities", params=params)
            response.raise_for_status()
            return response.text
        except Exception as e:
            return f"Ошибка: {str(e)}"


@mcp.tool()
async def search_activities(q: str) -> str:
    """
    Нечёткий поиск активностей по имени или описанию.
    
    Args:
        q: Поисковый запрос (например, "excel", "лог", "условие")
    """
    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(f"{CATALOG_API_URL}/search", params={"q": q})
            response.raise_for_status()
            return response.text
        except Exception as e:
            return f"Ошибка: {str(e)}"


@mcp.tool()
def write_ltw_file(filename: str, content: str) -> str:
    """
    Записывает сгенерированный XML-код в файл .ltw с правильной кодировкой.
    Primo RPA требует UTF-16 с декларацией encoding="utf-16".
    
    Args:
        filename: Путь к файлу (например, "TestProcess.ltw")
        content: XML-содержимое файла
    """
    try:
        content = content.lstrip('\ufeff')
        encoding = _resolve_encoding(content)
        
        with open(filename, 'w', encoding=encoding) as f:
            f.write(content)
        return f"✅ Файл {filename} успешно создан (кодировка: {encoding})."
    except Exception as e:
        return f"❌ Ошибка записи файла: {e}"


if __name__ == "__main__":
    mcp.run()