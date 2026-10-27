FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY catalog_service.py .
COPY primo_catalog.yaml .

EXPOSE 8000

CMD ["uvicorn", "catalog_service:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]