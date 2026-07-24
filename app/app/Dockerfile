FROM python:3.12-slim

# app.py resolves the project root as the parent of its own folder, so the
# layout inside the container mirrors the repo: /app/app/app.py -> root /app
# (volumes are mounted at /app/data and /app/output).
WORKDIR /app/app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

EXPOSE 8501
CMD ["streamlit", "run", "app.py", "--server.address=0.0.0.0", "--server.port=8501", "--server.headless=true"]
