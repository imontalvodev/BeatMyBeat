# SaveTune en Docker (Casa + LAN + NordVPN Mesh)

Este setup levanta:
- `backend` (FastAPI, host `41321` -> contenedor `4001`)
- `middleware` (Express, host `41311` -> contenedor `3001`)
- `lyrics` (`lyrics.ovh`, host API `41331` -> contenedor `8080`)

## 1) Requisitos previos

- Docker + Docker Compose.
- Repo `lyrics.ovh` clonado en carpeta hermana:
  - `../lyrics.ovh` respecto a este proyecto.

Referencia del proveedor: [NTag/lyrics.ovh](https://github.com/NTag/lyrics.ovh)

## 2) Arranque

Desde la raíz `Savetune/Savetune`:

```bash
docker compose -f docker-compose.home.yml up -d --build
```

## 3) Endpoints esperados

- Middleware: `http://<IP_LAN_O_MESH>:41311/health`
- Backend: `http://<IP_LAN_O_MESH>:41321/health`
- Lyrics API local: `http://<IP_LAN_O_MESH>:41331/v1/<artist>/<title>`

## 4) Variables importantes (override opcional)

Puedes exportarlas antes de levantar:

```bash
export LYRICS_PROVIDER=hybrid
export LYRICS_OVH_BASE_URL=http://lyrics:8080
export LYRICS_OVH_TIMEOUT_SECONDS=8

# Puertos host (recomendado para evitar colisiones con otros stacks):
export HOST_MIDDLEWARE_PORT=41311
export HOST_BACKEND_PORT=41321
export HOST_LYRICS_API_PORT=41331
export HOST_LYRICS_WEB_PORT=41332
```

Notas:
- `LYRICS_PROVIDER=hybrid`: primero `lyrics.ovh`, fallback interno (letras.com) si falla.
- Si quieres usar la API publica, cambia:
  - `LYRICS_OVH_BASE_URL=https://api.lyrics.ovh`

## 5) App Android para test

En `BackendConfig.kt`, apunta a la IP de tu servidor de casa:
- `MIDDLEWARE_BASE_URL=http://<IP_LAN_O_MESH>:41311`

## 6) Comandos utiles

```bash
docker compose -f docker-compose.home.yml ps
docker compose -f docker-compose.home.yml logs -f middleware
docker compose -f docker-compose.home.yml logs -f backend
docker compose -f docker-compose.home.yml logs -f lyrics
docker compose -f docker-compose.home.yml down
```

