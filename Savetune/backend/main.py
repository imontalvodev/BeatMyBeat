"""SaveTune Python backend (FastAPI)"""

import os
import tempfile
from typing import Generator

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse, HTMLResponse

from services.spotify import SpotifyPlaylistScraper
import yt_dlp


app = FastAPI(title="SaveTune Python Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/", response_class=HTMLResponse)
def index():
  """Pequeña interfaz gráfica para probar la API desde el navegador."""
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
    <p class="small">Panel rápido para probar los endpoints. Los mismos que usa el middleware JS.</p>

    <div class="card">
      <h2>/health</h2>
      <button onclick="callHealth()">Probar /health</button>
      <pre id="health-output"></pre>
    </div>

    <div class="card">
      <h2>/api/playlist</h2>
      <label>Spotify playlist URL</label>
      <input id="playlist-url" type="text" placeholder="https://open.spotify.com/playlist/..." />
      <button onclick="callPlaylist()">Probar /api/playlist</button>
      <pre id="playlist-output"></pre>
    </div>

    <div class="card">
      <h2>/api/search-youtube</h2>
      <label>Consulta</label>
      <input id="yt-query" type="text" placeholder="SexyBack Timbaland" />
      <button onclick="callSearch()">Probar /api/search-youtube</button>
      <pre id="search-output"></pre>
    </div>

    <div class="card">
      <h2>/api/download</h2>
      <p class="small">
        Introduce un <code>videoId</code> de YouTube y se descargará el audio.
        (Ejemplo: <code>3gOHvDP_vCs</code>).
      </p>
      <label>videoId</label>
      <input id="yt-id" type="text" placeholder="3gOHvDP_vCs" />
      <button onclick="callDownload()">Descargar audio</button>
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
    return {"status": "ok", "backend": "python"}


@app.get("/api/playlist")
def api_playlist(url: str = Query(..., alias="url")):
    """Envuelve SpotifyPlaylistScraper y adapta el JSON al formato del middleware."""
    if not url or "spotify.com/playlist/" not in url:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "Invalid Spotify URL",
                "message": "Please provide a valid Spotify playlist URL",
            },
        )

    scraper = SpotifyPlaylistScraper(headless=True)
    try:
        result = scraper.obtener_canciones_playlist(url)
    finally:
        scraper.cerrar_driver()

    if not result.get("success"):
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "error": "SpotifyScrapeError",
                "message": result.get("error", "Failed to scrape Spotify playlist"),
            },
        )

    playlist = result["playlist"]
    canciones = result["canciones"]

    songs = [
        {
            "id": c["id"],
            "title": c["titulo"],
            "artist": c["artistas"],
            "album": c["album"],
            "imageUrl": c["imagen_url"],
            "duration": c["duracion_segundos"],
        }
        for c in canciones
    ]

    return {
        "success": True,
        "playlist": {
            "name": playlist.get("nombre", "Unknown Playlist"),
            "totalTracks": len(songs),
        },
        "songs": songs,
    }


@app.get("/api/search-youtube")
def api_search_youtube(query: str = Query(..., alias="query")):
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


def _download_with_yt_dlp(video_url: str) -> tuple[str, Generator[bytes, None, None], str]:
    """Devuelve (filename, stream, media_type)."""
    tmp_dir = tempfile.gettempdir()
    out_tmpl = os.path.join(tmp_dir, "savetune_%(id)s.%(ext)s")
    opts = {
        "format": "bestaudio/best",
        "outtmpl": out_tmpl,
        "quiet": True,
        "no_warnings": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(video_url, download=True)

    if not info:
        raise ValueError("No se pudo obtener información del vídeo")

    video_id = info.get("id") or "unknown"
    title = (info.get("title") or "audio").replace("/", "-").replace("\\", "-")[:200]

    # Buscar el archivo descargado (m4a/webm/mp3)
    path_file = ""
    for ext in (".mp3", ".m4a", ".webm", ".opus", ".ogg"):
        candidate = os.path.join(tmp_dir, f"savetune_{video_id}{ext}")
        if os.path.isfile(candidate):
            path_file = candidate
            break
    if not path_file:
        # fallback: primer fichero que empiece por savetune_id
        for f in os.listdir(tmp_dir):
            if f.startswith(f"savetune_{video_id}"):
                path_file = os.path.join(tmp_dir, f)
                break
    if not path_file:
        raise ValueError("No se encontró el archivo de audio descargado")

    ext = os.path.splitext(path_file)[1].lstrip(".")
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

    filename = f"{title}.{ext}"
    return filename, stream_file(), media_type


@app.get("/api/download")
def api_download(videoId: str = Query(..., alias="videoId")):
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
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)

