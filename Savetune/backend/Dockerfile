FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# ffmpeg para yt-dlp + quickjs para resolver JS challenges de YouTube
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    ca-certificates \
    curl \
    quickjs \
    && rm -rf /var/lib/apt/lists/*

# Verificar que quickjs está en PATH y crear symlink si hace falta
RUN ln -sf $(which qjs) /usr/local/bin/quickjs 2>/dev/null || true

COPY requirements.txt /app/requirements.txt
RUN pip install --upgrade pip && pip install -r /app/requirements.txt

COPY . /app

EXPOSE 4001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "4001"]
