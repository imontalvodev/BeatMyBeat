"""SaveTune Python backend (FastAPI) - Spotify Web API + Selenium fallback."""

import os
import tempfile
import re
from typing import Generator

from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse, HTMLResponse

from services.spotify import SpotifyPlaylistScraper
from services.spotify_api import SpotifyWebAPI
import yt_dlp
from mutagen import File as MutagenFile


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


def _fetch_playlist_via_api(url: str) -> dict | None:
    """Obtiene la playlist vía Spotify Web API. Mismo formato que el scraper."""
    api = SpotifyWebAPI()
    if not api.is_configured():
        return None
    try:
        result = api.obtener_canciones_playlist(url)
    except Exception as e:
        print("Spotify API error:", e)
        return None
    if not result.get("success"):
        print("Spotify API:", result.get("error", "unknown"))
        return None
    playlist = result["playlist"]
    canciones = result["canciones"]
    canciones_validas = [
        c for c in canciones
        if c.get("id") and c.get("titulo") and c["titulo"] != "Unknown"
    ]
    print("API obtuvo", len(canciones_validas), "canciones")
    songs = [
        {
            "id": c["id"],
            "title": c["titulo"],
            "artist": c["artistas"],
            "album": c["album"],
            "imageUrl": c.get("imagen_url", ""),
            "duration": c["duracion_segundos"],
        }
        for c in canciones_validas
    ]
    return {
        "success": True,
        "playlist": {"name": playlist.get("nombre", "Unknown Playlist"), "totalTracks": len(songs)},
        "songs": songs,
    }


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

    # Convertir canciones básicas (ya vienen enriquecidas desde el scraper)
    songs = [
        {
            "id": c["id"],
            "title": c["titulo"],
            "artist": c["artistas"],
            "album": c["album"],
            "imageUrl": c["imagen_url"],
            "duration": c["duracion_segundos"],
        }
        for c in canciones_validas
    ]

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
            .highlight { background:#1e293b; padding:0.25rem 0.5rem; border-radius:4px; color:#22d3ee; }
        </style>
    </head>
    <body>
        <h1>SaveTune Backend (Python)</h1>
        <p class="small">Panel para probar los endpoints del backend</p>

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

        <div class="card" style="border: 2px solid #22d3ee;">
            <h2>🆕 /api/download-auto <span class="highlight">NUEVO</span></h2>
            <p class="small">Busca y descarga automáticamente sin necesidad de videoId</p>
            
            <label>Título de la canción</label>
            <input id="auto-title" type="text" placeholder="SexyBack" />
            
            <label>Artista</label>
            <input id="auto-artist" type="text" placeholder="Justin Timberlake" />
            
            <label>Álbum (opcional)</label>
            <input id="auto-album" type="text" placeholder="FutureSex/LoveSounds" />
            
            <button onclick="callDownloadAuto()">🎵 Buscar y Descargar</button>
            <p class="small" style="margin-top:0.5rem;">También puedes usar solo una query general</p>
            <input id="auto-query" type="text" placeholder="SexyBack Justin Timberlake official audio" />
            <button onclick="callDownloadAutoQuery()">🔍 Descargar por Query</button>
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
            async function callDownloadAuto() {
                const title = document.getElementById('auto-title').value.trim();
                const artist = document.getElementById('auto-artist').value.trim();
                const album = document.getElementById('auto-album').value.trim();
                
                if (!title && !artist) {
                    alert('Ingresa al menos el título o artista');
                    return;
                }
                
                let url = '/api/download-auto?';
                if (title) url += 'title=' + encodeURIComponent(title) + '&';
                if (artist) url += 'artist=' + encodeURIComponent(artist) + '&';
                if (album) url += 'album=' + encodeURIComponent(album);
                
                const a = document.createElement('a');
                a.href = url;
                a.download = '';
                document.body.appendChild(a);
                a.click();
                a.remove();
            }
            async function callDownloadAutoQuery() {
                const query = document.getElementById('auto-query').value.trim();
                if (!query) {
                    alert('Ingresa una consulta de búsqueda');
                    return;
                }
                
                const a = document.createElement('a');
                a.href = '/api/download-auto?query=' + encodeURIComponent(query);
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
    api = SpotifyWebAPI()
    return {
        "status": "ok",
        "backend": "python",
        "scraper": "selenium",
        "spotify_api_configured": api.is_configured(),
    }


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
    print(f"Solicitud de playlist: {url}")
    print(f"{'='*60}")

    api_result = _fetch_playlist_via_api(url)
    if api_result and api_result.get("success"):
        return api_result

    scraper_result = _fetch_playlist_via_scraper(url)

    if not scraper_result or not scraper_result.get("success"):
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "error": "PlaylistFetchError",
                "message": "No se pudo obtener la playlist (API y scraper fallaron)",
            },
        )

    return scraper_result


@app.get("/api/search-youtube")
def api_search_youtube(
    query: str | None = Query(None, alias="query"),
    title: str | None = Query(None, alias="title"),
    artist: str | None = Query(None, alias="artist"),
    album: str | None = Query(None, alias="album"),
):
    """
    Busca un video en YouTube.
    - Si se recibe title/artist/album, se construye una query más precisa del tipo:
      "title artist album official audio".
    - Si no, se usa el parámetro query tal cual (modo legacy).
    """
    parts: list[str] = []
    if title and title.strip():
        parts.append(title.strip())
    if artist and artist.strip() and artist.lower() != "unknown artist":
        parts.append(artist.strip())
    if album and album.strip() and album.lower() != "unknown album":
        parts.append(album.strip())

    if parts:
        parts.append("official audio")
        final_query = " ".join(parts)
    else:
        final_query = (query or "").strip()

    if not final_query:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Missing query",
                "message": "Please provide a search query or song metadata",
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
        print(f"🔍 Buscando en YouTube: {final_query}")
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(final_query, download=False)

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


@app.get("/api/download-auto")
def api_download_auto(
    query: str | None = Query(None, alias="query"),
    title: str | None = Query(None, alias="title"),
    artist: str | None = Query(None, alias="artist"),
    album: str | None = Query(None, alias="album"),
):
    """
    🆕 Busca automáticamente en YouTube y descarga el audio.
    No necesitas el videoId, solo los datos de la canción.

    Ejemplos:
    - /api/download-auto?query=SexyBack Timbaland
    - /api/download-auto?title=SexyBack&artist=Justin Timberlake&album=FutureSex/LoveSounds
    """
    # Construir la query de búsqueda
    parts: list[str] = []
    if title and title.strip():
        parts.append(title.strip())
    if artist and artist.strip() and artist.lower() != "unknown artist":
        parts.append(artist.strip())
    if album and album.strip() and album.lower() != "unknown album":
        parts.append(album.strip())

    if parts:
        parts.append("official audio")
        final_query = " ".join(parts)
    else:
        final_query = (query or "").strip()

    if not final_query:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Missing query",
                "message": "Please provide a search query or song metadata (title, artist, album)",
            },
        )

    print(f"\n🎵 Búsqueda y descarga automática: {final_query}")

    # Usar yt-dlp con búsqueda automática (ytsearch1)
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
        "default_search": "ytsearch1",  # 🔍 Busca automáticamente en YouTube
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

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(final_query, download=True)

        if not info:
            raise ValueError("No se pudo obtener información del vídeo")

        # Obtener info del video descargado
        if "entries" in info:
            video_info = info["entries"][0]
        else:
            video_info = info

        video_id = video_info.get("id") or "unknown"
        raw_title = video_info.get("title") or "audio"
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

        # Enriquecer metadatos ID3 con los datos originales de Spotify
        if ext == "mp3":
            track_title = title or raw_title
            track_artist = artist or video_info.get("artist") or video_info.get("uploader") or ""
            track_album = album or video_info.get("album") or ""

            # Si no hay artista y el título tiene formato "Artista - Canción"
            if " - " in raw_title and not track_artist:
                parts_split = raw_title.rsplit(" - ", 1)
                if len(parts_split) == 2:
                    left, right = parts_split[0].strip(), parts_split[1].strip()
                    if 2 <= len(right) <= 40:
                        track_title, track_artist = left, right

            try:
                audio = MutagenFile(path_file, easy=True)
                if audio is not None:
                    audio["title"] = [track_title]
                    if track_artist:
                        audio["artist"] = [track_artist]
                    if track_album:
                        audio["album"] = [track_album]
                    audio.save()
                    print(f"✅ Metadatos guardados: {track_title} - {track_artist}")
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

        print(f"✅ Descarga completada: {filename}")

        return StreamingResponse(
            stream_file(),
            media_type=media_type,
            headers={
                "Content-Disposition": f'attachment; filename="{filename}"',
            },
        )

    except Exception as e:
        print(f"❌ Error en descarga automática: {e}")
        return JSONResponse(
            status_code=503,
            content={
                "success": False,
                "error": "AutoDownloadError",
                "message": str(e),
            },
        )


if __name__ == "__main__":
    import uvicorn

    port = int(os.environ.get("PORT", 4000))
    print(f"\n🚀 Iniciando SaveTune Backend en puerto {port}...")
    print(f"📝 Panel de pruebas: http://localhost:{port}/")
    print(f"🔧 Scraper: Selenium (sin necesidad de Spotify API)")
    print(f"🆕 Nuevo endpoint: /api/download-auto (descarga automática)")

    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)