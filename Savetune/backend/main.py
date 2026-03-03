"""SaveTune Python backend (FastAPI) - Solo con Selenium Scraper (+ opcional Spotify API BYO)"""

import os
import tempfile
import re
from typing import Generator

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse, HTMLResponse, RedirectResponse

from services.spotify import SpotifyPlaylistScraper
import yt_dlp
from mutagen import File as MutagenFile


def _load_env_file_once() -> None:
    """
    Carga un archivo .env de forma muy simple si existe.
    Se usa para habilitar fácilmente el modo BYO Spotify API en entornos locales.
    """
    flag = "_SAVETUNE_ENV_LOADED"
    if os.environ.get(flag) == "1":
        return

    base_dir = os.path.dirname(os.path.dirname(__file__))  # .../Savetune
    candidates = [
        os.path.join(base_dir, ".env"),
        os.path.join(os.path.dirname(__file__), ".env"),  # backend/.env
    ]

    for path in candidates:
        path = os.path.abspath(path)
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    key = key.strip()
                    value = value.strip().strip('"').strip("'")
                    if key and key not in os.environ:
                        os.environ[key] = value
        except Exception as e:
            print(f"⚠️ No se pudo leer .env en {path}: {e}")

    os.environ[flag] = "1"


# Asegurarnos de que .env se carga antes de leer USE_SPOTIFY_API
_load_env_file_once()

# Modo BYO Spotify API: solo se usa si el usuario aporta sus propias credenciales
USE_SPOTIFY_API = os.getenv("USE_SPOTIFY_API", "false").lower() == "true"

if USE_SPOTIFY_API:
    try:
        from services.spotify_api import (
            get_track_metadata,
            get_authorize_url,
            handle_authorization_callback,
        )
    except Exception as e:  # pragma: no cover - solo para entornos sin módulo/config
        print(f"⚠️ No se pudo inicializar Spotify API opcional: {e}")
        USE_SPOTIFY_API = False


app = FastAPI(title="SaveTune Python Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _extract_playlist_id(url: str) -> str | None:
    """Extrae el ID de la playlist desde una URL"""
    if "playlist/" in url:
        m = re.search(r"playlist/([a-zA-Z0-9]+)", url)
        if m:
            return m.group(1)
    return None


def _fetch_playlist_via_scraper(url: str) -> dict | None:
    """Usa Selenium para scrapear la playlist"""
    print("🔄 Usando scraper de Selenium...")

    scraper = SpotifyPlaylistScraper(headless=True)
    try:
        result = scraper.obtener_canciones_playlist(url)
    finally:
        scraper.cerrar_driver()

    if not result.get("success"):
        return None

    playlist = result["playlist"]
    canciones = result["canciones"]

    # Filtrar canciones vacías o inválidas
    canciones_validas = [
        c for c in canciones
        if c.get("id") and c.get("titulo") and c["titulo"] != "Unknown"
    ]

    print(f"✅ Scraper obtuvo {len(canciones_validas)} canciones válidas")

    # Convertir canciones básicas
    songs = []
    for c in canciones_validas:
        song = {
            "id": c["id"],
            "title": c["titulo"],
            "artist": c["artistas"],
            "album": c["album"],
            "imageUrl": c["imagen_url"],
            "duration": c["duracion_segundos"],
        }

        # Enriquecer metadatos con la API oficial de Spotify cuando falten (modo BYO)
        if USE_SPOTIFY_API:
            needs_artist = not song["artist"] or song["artist"] == "Unknown Artist"
            needs_album = not song["album"] or song["album"] == "Unknown Album"

            if (needs_artist or needs_album) and song["id"]:
                try:
                    meta = get_track_metadata(song["id"])
                    if meta:
                        if needs_artist:
                            # Priorizar lista completa de artistas si está disponible
                            song["artist"] = meta.get("artists") or meta.get("artist") or song["artist"]
                        if needs_album:
                            song["album"] = meta.get("album") or song["album"]
                except Exception as e:
                    print(f"⚠️ Error enriqueciendo metadatos para track {song['id']}: {e}")

        songs.append(song)

    return {
        "success": True,
        "playlist": {
            "name": playlist.get("nombre", "Unknown Playlist"),
            "totalTracks": len(songs),
        },
        "songs": songs,
    }


@app.get("/", response_class=HTMLResponse)
def index():
    """Interfaz gráfica para probar la API desde el navegador"""
    return """
    <!doctype html>
    <html lang="es">
    <head>
        <meta charset="utf-8" />
        <title>SaveTune Backend (Python)</title>
        <style>
            body { font-family: system-ui, sans-serif; margin: 2rem; background:#0b1020; color:#f5f5f5; }
            h1 { margin-bottom: 0.5rem; }
            h2 { margin-top: 2rem; }
            label { display:block; margin-top:0.5rem; }
            input[type="text"] { width: 100%; padding: 0.5rem; border-radius: 6px; border: 1px solid #444; background:#171c2f; color:#f5f5f5; }
            button { margin-top:0.75rem; padding:0.5rem 1rem; border-radius:6px; border:none; background:#4f46e5; color:#fff; cursor:pointer; }
            button:hover { background:#6366f1; }
            pre { background:#020617; padding:1rem; border-radius:8px; overflow:auto; max-height:300px; }
            .card { border-radius:12px; padding:1.5rem; background:#111827; margin-top:1.5rem; box-shadow:0 10px 20px rgba(0,0,0,0.4); }
            .small { font-size:0.875rem; color:#9ca3af; }
            a { color:#38bdf8; }
        </style>
    </head>
    <body>
        <h1>SaveTune Backend (Python)</h1>
        <p class="small">Panel para probar los endpoints del backend</p>

        <!-- Bloque de login de Spotify solo visible si el backend tiene activada la API opcional -->
        <div class="card">
            <h2>Spotify Login (opcional)</h2>
            <p class="small">
                Este backend puede usar tu propia cuenta de desarrollador de Spotify para mejorar la precisión
                de artista/álbum. Solo está activo si el servidor se ha configurado con USE_SPOTIFY_API=true.
            </p>
            <button onclick="window.location.href='/spotify/login'">Conectar con Spotify</button>
        </div>

        <div class="card">
            <h2>/health</h2>
            <button onclick="callHealth()">Probar /health</button>
            <pre id="health-output"></pre>
        </div>

        <div class="card">
            <h2>/api/playlist</h2>
            <label>Spotify playlist URL</label>
            <input id="playlist-url" type="text" placeholder="https://open.spotify.com/playlist/..." />
            <button onclick="callPlaylist()">Obtener Playlist</button>
            <pre id="playlist-output"></pre>
        </div>

        <div class="card">
            <h2>/api/search-youtube</h2>
            <label>Consulta de búsqueda</label>
            <input id="yt-query" type="text" placeholder="SexyBack Timbaland" />
            <button onclick="callSearch()">Buscar en YouTube</button>
            <pre id="search-output"></pre>
        </div>

        <div class="card">
            <h2>/api/download</h2>
            <p class="small">Introduce un videoId de YouTube (ej: 3gOHvDP_vCs)</p>
            <label>Video ID</label>
            <input id="yt-id" type="text" placeholder="3gOHvDP_vCs" />
            <button onclick="callDownload()">Descargar Audio</button>
        </div>

        <script>
            async function callHealth() {
                const res = await fetch('/health');
                const json = await res.json();
                document.getElementById('health-output').textContent = JSON.stringify(json, null, 2);
            }
            async function callPlaylist() {
                const url = document.getElementById('playlist-url').value.trim();
                if (!url) return;
                const res = await fetch('/api/playlist?url=' + encodeURIComponent(url));
                const text = await res.text();
                try {
                    document.getElementById('playlist-output').textContent = JSON.stringify(JSON.parse(text), null, 2);
                } catch {
                    document.getElementById('playlist-output').textContent = text;
                }
            }
            async function callSearch() {
                const q = document.getElementById('yt-query').value.trim();
                if (!q) return;
                const res = await fetch('/api/search-youtube?query=' + encodeURIComponent(q));
                const json = await res.json();
                document.getElementById('search-output').textContent = JSON.stringify(json, null, 2);
            }
            async function callDownload() {
                const id = document.getElementById('yt-id').value.trim();
                if (!id) return;
                const a = document.createElement('a');
                a.href = '/api/download?videoId=' + encodeURIComponent(id);
                a.download = '';
                document.body.appendChild(a);
                a.click();
                a.remove();
            }
        </script>
    </body>
    </html>
    """


@app.get("/health")
def health():
    """Health check endpoint"""
    return {
        "status": "ok",
        "backend": "python",
        "scraper": "selenium"
    }


@app.get("/spotify/login")
def spotify_login():
    """
    Redirige al usuario a la pantalla de autorización de Spotify.
    Usa Authorization Code Flow para obtener un token de usuario.
    """
    if not USE_SPOTIFY_API:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "SpotifyAPIDisabled",
                "message": "Spotify API login is disabled. Set USE_SPOTIFY_API=true and configure credentials to enable it.",
            },
        )

    url = get_authorize_url()
    return RedirectResponse(url)


@app.get("/spotify/callback", response_class=HTMLResponse)
def spotify_callback(code: str | None = None, error: str | None = None):
    """
    Endpoint de callback para Spotify.
    Una vez autorizado, guarda el token de usuario en memoria.
    """
    if error:
        return f"<h1>Error de Spotify</h1><p>{error}</p>"

    if not code:
        return "<h1>Falta 'code' en el callback de Spotify</h1>"

    if not USE_SPOTIFY_API:
        return "<h1>Spotify API BYO está desactivada en este servidor.</h1>"

    ok = handle_authorization_callback(code)
    if not ok:
        return "<h1>No se pudo completar la autorización con Spotify.</h1>"

    return """
    <h1>Spotify conectado correctamente ✅</h1>
    <p>Ya puedes cerrar esta pestaña y volver a SaveTune.</p>
    """


@app.get("/api/playlist")
def api_playlist(url: str = Query(..., alias="url")):
    """Obtiene canciones de una playlist de Spotify usando Selenium"""
    if not url or "spotify.com/playlist/" not in url:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Invalid Spotify URL",
                "message": "Please provide a valid Spotify playlist URL",
            },
        )

    print(f"\n{'='*60}")
    print(f"📝 Solicitud de playlist: {url}")
    print(f"{'='*60}")

    # Obtener playlist con scraper
    scraper_result = _fetch_playlist_via_scraper(url)

    if not scraper_result or not scraper_result.get("success"):
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "error": "PlaylistFetchError",
                "message": "No se pudo obtener la playlist con el scraper",
            },
        )

    print(f"✅ Playlist obtenida exitosamente")
    return scraper_result


@app.get("/api/search-youtube")
def api_search_youtube(query: str = Query(..., alias="query")):
    """Busca un video en YouTube"""
    if not query or not query.strip():
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Missing query",
                "message": "Please provide a search query",
            },
        )

    opts = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": False,
        "default_search": "ytsearch1",
        "noplaylist": True,
    }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(query, download=False)

        if not info or "entries" not in info or not info["entries"]:
            return {
                "success": True,
                "video": {"id": "", "title": "", "url": "", "thumbnail": ""},
            }

        entry = info["entries"][0]
        video_id = entry.get("id") or ""
        video = {
            "id": video_id,
            "title": entry.get("title") or "",
            "url": f"https://www.youtube.com/watch?v={video_id}" if video_id else "",
            "thumbnail": entry.get("thumbnail") or "",
        }
        return {"success": True, "video": video}

    except Exception as e:
        print(f"❌ Error buscando en YouTube: {e}")
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "error": "YouTubeSearchError",
                "message": str(e),
            },
        )


def _download_with_yt_dlp(video_url: str) -> tuple[str, Generator[bytes, None, None], str]:
    """Descarga audio de YouTube y devuelve (filename, stream, media_type)"""
    tmp_dir = tempfile.gettempdir()
    out_tmpl = os.path.join(tmp_dir, "savetune_%(id)s.%(ext)s")
    opts = {
        "format": "bestaudio/best",
        "outtmpl": out_tmpl,
        "quiet": True,
        "no_warnings": True,
        "prefer_ffmpeg": True,
        "keepvideo": False,
        "writethumbnail": True,
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            },
            {
                "key": "FFmpegMetadata",
            },
            {
                "key": "EmbedThumbnail",
            },
        ],
    }

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(video_url, download=True)

    if not info:
        raise ValueError("No se pudo obtener información del vídeo")

    video_id = info.get("id") or "unknown"
    raw_title = info.get("title") or "audio"
    title_safe = raw_title.replace("/", "-").replace("\\", "-")[:200]

    # Buscar el archivo descargado
    path_file = ""
    for ext in (".mp3", ".m4a", ".webm", ".opus", ".ogg"):
        candidate = os.path.join(tmp_dir, f"savetune_{video_id}{ext}")
        if os.path.isfile(candidate):
            path_file = candidate
            break

    if not path_file:
        for f in os.listdir(tmp_dir):
            if f.startswith(f"savetune_{video_id}"):
                path_file = os.path.join(tmp_dir, f)
                break

    if not path_file:
        raise ValueError("No se encontró el archivo de audio descargado")

    ext = os.path.splitext(path_file)[1].lstrip(".")

    # Enriquecer metadatos ID3
    if ext == "mp3":
        track_title = raw_title
        artist = info.get("artist") or info.get("uploader") or ""
        album = info.get("album") or ""

        if " - " in raw_title and not artist:
            parts = raw_title.rsplit(" - ", 1)
            if len(parts) == 2:
                left, right = parts[0].strip(), parts[1].strip()
                if 2 <= len(right) <= 40:
                    track_title, artist = left, right

        try:
            audio = MutagenFile(path_file, easy=True)
            if audio is not None:
                audio["title"] = [track_title]
                if artist:
                    audio["artist"] = [artist]
                if album:
                    audio["album"] = [album]
                audio.save()
        except Exception as e:
            print(f"⚠️ Error guardando metadatos: {e}")

    media_type = (
        "audio/mpeg"
        if ext == "mp3"
        else "audio/mp4"
        if ext in ("m4a", "mp4")
        else "audio/webm"
    )

    def stream_file() -> Generator[bytes, None, None]:
        try:
            with open(path_file, "rb") as f:
                while chunk := f.read(8192):
                    yield chunk
        finally:
            try:
                os.remove(path_file)
            except OSError:
                pass

    filename = f"{title_safe}.{ext}"
    return filename, stream_file(), media_type


@app.get("/api/download")
def api_download(videoId: str = Query(..., alias="videoId")):
    """Descarga audio de un video de YouTube"""
    if not videoId or not videoId.strip():
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Missing videoId",
                "message": "Please provide a YouTube video ID",
            },
        )

    video_url = f"https://www.youtube.com/watch?v={videoId.strip()}"

    try:
        filename, stream_gen, media_type = _download_with_yt_dlp(video_url)
    except Exception as e:
        print(f"❌ Error descargando: {e}")
        return JSONResponse(
            status_code=503,
            content={
                "success": False,
                "error": "DownloadUnavailable",
                "message": str(e),
            },
        )

    return StreamingResponse(
        stream_gen,
        media_type=media_type,
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
        },
    )


if __name__ == "__main__":
    import uvicorn

    port = int(os.environ.get("PORT", 4000))
    print(f"\n🚀 Iniciando SaveTune Backend en puerto {port}...")
    print(f"📝 Panel de pruebas: http://localhost:{port}/")
    print(f"🔧 Scraper: Selenium (sin necesidad de Spotify API)")

    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)