"""SaveTune Python backend (FastAPI) - Spotify Web API + Selenium fallback."""

import os
import tempfile
import re
import json
import unicodedata
import shutil
import uuid
import threading
import queue as queue_module
import time
from collections import deque
from functools import lru_cache
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
import requests
from bs4 import BeautifulSoup


app = FastAPI(title="SaveTune Python Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Descargas concurrentes + cola para multijugador ---
# Objetivo: máximo de `MAX_CONCURRENT_DOWNLOADS` descargas en paralelo y,
# si se supera, informar al frontend con una posición en cola.
MAX_CONCURRENT_DOWNLOADS = int(os.environ.get("MAX_CONCURRENT_DOWNLOADS", "5"))
MAX_DOWNLOAD_QUEUE_SIZE = int(os.environ.get("MAX_DOWNLOAD_QUEUE_SIZE", "200"))
DOWNLOAD_JOB_TTL_SECONDS = int(os.environ.get("DOWNLOAD_JOB_TTL_SECONDS", "1800"))
DOWNLOAD_JOB_CLEANUP_INTERVAL_SECONDS = int(os.environ.get("DOWNLOAD_JOB_CLEANUP_INTERVAL_SECONDS", "60"))

# --- Filtros para evitar "letra rara" cuando la canción no tiene vocals ---
MIN_LYRICS_CHARS = int(os.environ.get("MIN_LYRICS_CHARS", "250"))
MIN_LYRICS_LINES = int(os.environ.get("MIN_LYRICS_LINES", "6"))
MIN_LYRICS_SCORE = int(os.environ.get("MIN_LYRICS_SCORE", "3"))

NO_LYRICS_TEXT_MARKERS = [
    "instrumental",
    "sin letra",
    "no tiene letra",
    "no hay letra",
    "solo instrumental",
    "letra instrumental",
    "sin vocals",
]
_download_semaphore = threading.Semaphore(MAX_CONCURRENT_DOWNLOADS)
_downloads_lock = threading.Lock()
_active_downloads = 0  # descargas en fase "yt-dlp/ffmpeg" (no streaming)

_download_job_queue = queue_module.Queue()
_pending_job_ids = deque()  # en orden FIFO
_download_jobs: dict[str, dict] = {}  # jobId -> info
_workers_started = False
_cleanup_started = False


def _get_job_snapshot_position(job_id: str) -> int:
    """
    Posición aproximada en la cola: número de trabajos (incluyendo activos) que
    van antes que este job. Se recalcula sobre `_pending_job_ids`.
    """
    with _downloads_lock:
        try:
            idx = list(_pending_job_ids).index(job_id)
        except ValueError:
            idx = -1
        # activos + trabajos en pending antes de este + este mismo
        return _active_downloads + (idx if idx >= 0 else 0) + 1


def _download_youtube_audio_to_file(
    video_url: str,
    job_dir: str,
    *,
    spotify_title: str | None = None,
    spotify_artist: str | None = None,
    spotify_album: str | None = None,
    force_spotify_metadata: bool = False,
) -> tuple[str, str, str, str]:
    """
    Descarga audio a disco y devuelve (file_path, filename, media_type, ext).
    No limpia `job_dir`; lo hace el caller cuando sirva/termine el job.
    """
    out_tmpl = os.path.join(job_dir, "audio_%(id)s.%(ext)s")
    opts = {
        "format": "bestaudio/best",
        "outtmpl": out_tmpl,
        "quiet": True,
        "no_warnings": True,
        "prefer_ffmpeg": True,
        "keepvideo": False,
        "noplaylist": True,
        "writethumbnail": True,
        "concurrent_fragment_downloads": 4,
        "retries": 3,
        "fragment_retries": 3,
        "socket_timeout": 15,
        "postprocessors": [
            {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"},
            {"key": "FFmpegMetadata"},
            {"key": "EmbedThumbnail"},
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
        candidate = os.path.join(job_dir, f"audio_{video_id}{ext}")
        if os.path.isfile(candidate):
            path_file = candidate
            break

    if not path_file:
        for f in os.listdir(job_dir):
            if f.startswith(f"audio_{video_id}"):
                path_file = os.path.join(job_dir, f)
                break

    if not path_file:
        raise ValueError("No se encontró el archivo de audio descargado")

    ext = os.path.splitext(path_file)[1].lstrip(".")

    # Enriquecer metadatos ID3
    if ext == "mp3":
        if force_spotify_metadata:
            track_title = spotify_title or raw_title
            track_artist = spotify_artist or info.get("artist") or info.get("uploader") or ""
            track_album = spotify_album or info.get("album") or ""
        else:
            track_title = raw_title
            track_artist = info.get("artist") or info.get("uploader") or ""
            track_album = info.get("album") or ""

            if " - " in raw_title and not track_artist:
                parts = raw_title.rsplit(" - ", 1)
                if len(parts) == 2:
                    left, right = parts[0].strip(), parts[1].strip()
                    if 2 <= len(right) <= 40:
                        track_title, track_artist = left, right

        audio = MutagenFile(path_file, easy=True)
        if audio is not None:
            audio["title"] = [track_title]
            if track_artist:
                audio["artist"] = [track_artist]
            if track_album:
                audio["album"] = [track_album]
            audio.save()

    media_type = (
        "audio/mpeg"
        if ext == "mp3"
        else "audio/mp4"
        if ext in ("m4a", "mp4")
        else "audio/webm"
    )

    filename = f"{title_safe}.{ext}"
    return path_file, filename, media_type, ext


def _download_auto_audio_to_file(
    final_query: str,
    job_dir: str,
    *,
    spotify_title: str | None = None,
    spotify_artist: str | None = None,
    spotify_album: str | None = None,
) -> tuple[str, str, str, str]:
    """
    Variante de descarga automática (ytsearch1) que devuelve el audio a disco.
    """
    out_tmpl = os.path.join(job_dir, "audio_%(id)s.%(ext)s")

    opts = {
        "format": "bestaudio/best",
        "outtmpl": out_tmpl,
        "quiet": True,
        "no_warnings": True,
        "prefer_ffmpeg": True,
        "keepvideo": False,
        "noplaylist": True,
        "writethumbnail": True,
        "concurrent_fragment_downloads": 4,
        "retries": 3,
        "fragment_retries": 3,
        "socket_timeout": 15,
        "default_search": "ytsearch1",
        "postprocessors": [
            {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"},
            {"key": "FFmpegMetadata"},
            {"key": "EmbedThumbnail"},
        ],
    }

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(final_query, download=True)

    if not info:
        raise ValueError("No se pudo obtener información del vídeo")

    video_info = info["entries"][0] if "entries" in info and info["entries"] else info

    video_id = (video_info.get("id") or "unknown") if isinstance(video_info, dict) else "unknown"
    raw_title = (video_info.get("title") or "audio") if isinstance(video_info, dict) else "audio"
    title_safe = raw_title.replace("/", "-").replace("\\", "-")[:200]

    path_file = ""
    for ext in (".mp3", ".m4a", ".webm", ".opus", ".ogg"):
        candidate = os.path.join(job_dir, f"audio_{video_id}{ext}")
        if os.path.isfile(candidate):
            path_file = candidate
            break

    if not path_file:
        for f in os.listdir(job_dir):
            if f.startswith(f"audio_{video_id}"):
                path_file = os.path.join(job_dir, f)
                break

    if not path_file:
        raise ValueError("No se encontró el archivo de audio descargado")

    ext = os.path.splitext(path_file)[1].lstrip(".")

    if ext == "mp3":
        track_title = spotify_title or raw_title
        track_artist = spotify_artist or (
            video_info.get("artist") if isinstance(video_info, dict) else ""
        ) or (video_info.get("uploader") if isinstance(video_info, dict) else "") or ""
        track_album = spotify_album or (video_info.get("album") if isinstance(video_info, dict) else "") or ""

        if " - " in raw_title and not track_artist:
            parts_split = raw_title.rsplit(" - ", 1)
            if len(parts_split) == 2:
                left, right = parts_split[0].strip(), parts_split[1].strip()
                if 2 <= len(right) <= 40:
                    track_title, track_artist = left, right

        audio = MutagenFile(path_file, easy=True)
        if audio is not None:
            audio["title"] = [track_title]
            if track_artist:
                audio["artist"] = [track_artist]
            if track_album:
                audio["album"] = [track_album]
            audio.save()

    media_type = (
        "audio/mpeg"
        if ext == "mp3"
        else "audio/mp4"
        if ext in ("m4a", "mp4")
        else "audio/webm"
    )

    filename = f"{title_safe}.{ext}"
    return path_file, filename, media_type, ext


def _download_job_worker():
    """
    Worker FIFO: cada job descarga a disco y queda listo para streaming.
    """
    global _active_downloads
    while True:
        job_id = _download_job_queue.get()
        if not job_id:
            continue

        job = _download_jobs.get(job_id)
        if not job:
            _download_job_queue.task_done()
            continue

        try:
            # Reservar un slot de descarga (yt-dlp/ffmpeg). Se libera al terminar de descargar.
            _download_semaphore.acquire()
            with _downloads_lock:
                _active_downloads += 1
                # sacar de pending (si está)
                try:
                    # pending mantiene FIFO; debería estar por delante pero no asumimos.
                    if job_id in _pending_job_ids:
                        _pending_job_ids.remove(job_id)
                except ValueError:
                    pass
                job["status"] = "processing"
                job["startedAt"] = int(time.time())

            job_dir = tempfile.mkdtemp(prefix="savetune_job_")
            job["job_dir"] = job_dir

            job_type = job.get("type")
            if job_type == "download":
                video_url = f"https://www.youtube.com/watch?v={job['videoId']}"
                file_path, filename, media_type, _ = _download_youtube_audio_to_file(
                    video_url,
                    job_dir,
                    force_spotify_metadata=False,
                )
            elif job_type == "download-auto":
                final_query = job.get("final_query")
                file_path, filename, media_type, _ = _download_auto_audio_to_file(
                    final_query,
                    job_dir,
                    spotify_title=job.get("title"),
                    spotify_artist=job.get("artist"),
                    spotify_album=job.get("album"),
                )
            else:
                raise ValueError(f"Unknown job type: {job_type}")

            with _downloads_lock:
                job["status"] = "ready"
                job["file_path"] = file_path
                job["filename"] = filename
                job["media_type"] = media_type
                job["readyAt"] = int(time.time())

        except Exception as e:
            with _downloads_lock:
                job["status"] = "error"
                job["error"] = str(e)
                job["errorAt"] = int(time.time())

            # Evitar fugas de disco si el job falla (cleanup del job_dir si existe)
            try:
                jd = job.get("job_dir")
                if jd and os.path.isdir(jd):
                    shutil.rmtree(jd, ignore_errors=True)
            except Exception:
                pass
        finally:
            with _downloads_lock:
                _active_downloads = max(0, _active_downloads - 1)
            _download_semaphore.release()
            _download_job_queue.task_done()


def _ensure_workers_started():
    global _workers_started
    if _workers_started:
        return
    _workers_started = True
    for _ in range(MAX_CONCURRENT_DOWNLOADS):
        t = threading.Thread(target=_download_job_worker, daemon=True)
        t.start()

    # Lanzar cleanup para evitar crecimiento sin fin de `_download_jobs`
    global _cleanup_started
    if _cleanup_started:
        return
    _cleanup_started = True

    def _job_cleanup_loop():
        while True:
            time.sleep(DOWNLOAD_JOB_CLEANUP_INTERVAL_SECONDS)
            now = int(time.time())
            to_delete: list[tuple[str, str]] = []
            with _downloads_lock:
                for job_id, job in list(_download_jobs.items()):
                    created_at = job.get("createdAt") or job.get("readyAt") or job.get("errorAt") or 0
                    status = job.get("status")
                    if status in ("ready", "error") and created_at:
                        if now - int(created_at) > DOWNLOAD_JOB_TTL_SECONDS:
                            job_dir = job.get("job_dir") or ""
                            to_delete.append((job_id, job_dir))

                for job_id, _job_dir in to_delete:
                    _download_jobs.pop(job_id, None)

            # Borrar fuera del lock (reduce el tiempo bloqueando el estado global)
            for _job_id, job_dir in to_delete:
                if job_dir and os.path.isdir(job_dir):
                    try:
                        shutil.rmtree(job_dir, ignore_errors=True)
                    except OSError:
                        pass

    threading.Thread(target=_job_cleanup_loop, daemon=True).start()


def _extract_playlist_id(url: str) -> str | None:
    """Extrae el ID de la playlist desde una URL"""
    if "playlist/" in url:
        m = re.search(r"playlist/([a-zA-Z0-9]+)", url)
        if m:
            return m.group(1)
    return None


def _strip_accents(s: str) -> str:
    s = unicodedata.normalize("NFKD", s)
    return "".join(ch for ch in s if not unicodedata.combining(ch))


def _slugify_letras(s: str) -> str:
    """
    Convierte texto a slug estilo letras.com.
    Ej: "Creep (Acoustic)" -> "creep-acoustic"
    """
    s = (s or "").strip().lower()
    s = _strip_accents(s)
    # Normalizaciones comunes
    s = s.replace("&", " and ")
    s = re.sub(r"\b(feat|ft)\.?\b", "", s, flags=re.I)
    s = re.sub(r"[\[\]\(\)\{\}]", " ", s)  # quita paréntesis/llaves
    s = re.sub(r"[\"'“”‘’`´]", "", s)
    s = re.sub(r"[^a-z0-9]+", "-", s)  # todo lo no alfanumérico a '-'
    s = re.sub(r"-{2,}", "-", s).strip("-")
    return s


def _candidate_title_variants(title: str) -> list[str]:
    t = (title or "").strip()
    if not t:
        return []

    # Quitar contenido entre paréntesis (versiones, remasters, etc.)
    no_paren = re.sub(r"\s*[\(\[].*?[\)\]]\s*", " ", t).strip()

    # Quitar sufijos comunes
    no_suffix = re.sub(
        r"\s*-\s*(remaster(ed)?(\s*\d{4})?|radio edit|edit|mono|stereo|live|acoustic|demo|official.*)$",
        "",
        t,
        flags=re.I,
    ).strip()

    # Prioridad: variantes "limpias" primero, y el título original al final.
    # Esto evita quedarnos con páginas tipo "(Demo)" cuando también existe la versión normal.
    variants: list[str] = []
    for cand in (no_paren, no_suffix, t):
        if cand and cand.strip():
            variants.append(cand.strip())

    # Deduplicar preservando orden
    out: list[str] = []
    seen = set()
    for v in variants:
        key = v.lower()
        if key not in seen:
            seen.add(key)
            out.append(v)
    return out


def _letras_candidate_urls(artist: str, title: str) -> list[str]:
    artist_slug = _slugify_letras(artist)
    if not artist_slug:
        return []

    urls: list[str] = []
    for t in _candidate_title_variants(title):
        title_slug = _slugify_letras(t)
        if not title_slug:
            continue
        urls.append(f"https://m.letras.com/{artist_slug}/{title_slug}/")
        urls.append(f"https://www.letras.com/{artist_slug}/{title_slug}/")

    # Deduplicar preservando orden
    out: list[str] = []
    seen = set()
    for u in urls:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return out


def _extract_lyrics_from_jsonld(soup: BeautifulSoup) -> str | None:
    scripts = soup.find_all("script", attrs={"type": "application/ld+json"})
    for sc in scripts:
        raw = sc.string or sc.get_text(strip=True) or ""
        if not raw:
            continue
        try:
            data = json.loads(raw)
        except Exception:
            continue

        # JSON-LD puede venir como lista o dict
        nodes = data if isinstance(data, list) else [data]
        for node in nodes:
            if not isinstance(node, dict):
                continue
            lyrics = node.get("lyrics")
            if isinstance(lyrics, dict):
                text = lyrics.get("text")
                if isinstance(text, str) and text.strip():
                    return text.strip()
            if isinstance(lyrics, str) and lyrics.strip():
                return lyrics.strip()
    return None


def _normalize_lyrics_text(text: str) -> str:
    # Mantener saltos de línea “bonitos”
    text = re.sub(r"\r\n?", "\n", text or "")
    # Colapsar demasiadas líneas vacías
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    return text


def _page_canonical_url(soup: BeautifulSoup) -> str:
    link = soup.find("link", attrs={"rel": "canonical"})
    href = link.get("href") if link else ""
    return href or ""


def _extract_lyrics_from_known_letras_containers(soup: BeautifulSoup) -> str | None:
    """
    Letras.com suele renderizar la letra dentro de contenedores concretos, por ejemplo:
    - div.lyric-original  (muy común; el que enseñas en la captura)
    - div.lyric-cnt / div#lyrics / article div.lyric (variantes)

    Aquí construimos la letra a partir de <p> y <br> para preservar saltos.
    """
    selectors = [
        "div.lyric-original",
        "div.lyric-cnt",
        "div#lyrics",
        "article div.lyric",
        "article div.lyric-original",
    ]

    for sel in selectors:
        node = soup.select_one(sel)
        if not node:
            continue

        # Si hay <p>, usar esos como líneas/estrofas
        ps = node.find_all("p")
        if ps:
            parts: list[str] = []
            for p in ps:
                txt = p.get_text("\n", strip=True)
                if txt:
                    parts.append(txt)
                else:
                    parts.append("")
            out = "\n\n".join(parts)
            out = _normalize_lyrics_text(out)
            if len(out) >= 80 and out.count("\n") >= 3:
                return out

        # Si no hay <p>, respetar <br> usando separador \n
        txt = node.get_text("\n", strip=True)
        txt = _normalize_lyrics_text(txt)
        if len(txt) >= 80 and txt.count("\n") >= 3:
            return txt

    return None


def _extract_lyrics_from_dom(soup: BeautifulSoup) -> str | None:
    """
    Fallback heurístico: intenta encontrar el bloque de letra como el texto más largo
    dentro de contenedores cuyo class/id sugiere 'lyric/letra'.
    """
    # Primero: contenedores conocidos de letras.com
    known = _extract_lyrics_from_known_letras_containers(soup)
    if known:
        return known

    candidates: list[str] = []

    # 1) Contenedores obvios por class/id
    for tag in soup.find_all(["div", "section", "article"]):
        attrs = " ".join(
            [
                " ".join(tag.get("class", []) or []),
                tag.get("id") or "",
                tag.get("data-testid") or "",
            ]
        ).lower()
        if not attrs:
            continue
        if ("lyric" in attrs) or ("lyrics" in attrs) or ("letra" in attrs):
            txt = tag.get_text("\n", strip=True)
            if txt and len(txt) >= 200:
                candidates.append(txt)

    # 2) Evitar el "body completo" (mete menús, CTAs, etc.).
    # Si no hay candidatos razonables, preferimos fallar para que el caller pruebe otra URL.
    if not candidates:
        return None

    best = max(candidates, key=lambda t: len(t), default="")
    if not best or len(best) < 200:
        return None

    # Recorte: cortar a partir de "Written by:" si existe
    cut_markers = ["Written by:", "Escrita por:", "Composicao:", "Composição:", "Written-by:"]
    low = best.lower()
    for m in cut_markers:
        idx = low.find(m.lower())
        if idx != -1:
            best = best[:idx].strip()
            break

    # Quitar ruido típico de navegación/acciones
    noise_lines = {
        "lyrics",
        "meaning",
        "translations",
        "letra",
        "traducción",
        "restoreapply",
        "clear selection",
        "join the community",
        "most popular",
        "most played",
        "related playlists",
        "agregar a favoritos",
        "agregar a playlist",
        "tamaño de la fuente",
        "acordes",
        "imprimir",
        "corregir",
        "desplazamiento automático",
        "anotaciones",
        "habilitadas",
        "deshabilitadas",
    }
    lines = [ln.strip() for ln in best.splitlines()]
    cleaned: list[str] = []
    for ln in lines:
        if not ln:
            cleaned.append("")
            continue
        if ln.lower() in noise_lines:
            continue
        cleaned.append(ln)

    out = "\n".join(cleaned)
    out = _normalize_lyrics_text(out)

    # Heurística final: asegurarnos de que sea "poesía" (varias líneas)
    if out.count("\n") < 3:
        return None
    return out


def _page_song_artist(soup: BeautifulSoup) -> tuple[str, str]:
    """
    Obtiene (song_title, artist_name) desde encabezados visibles.
    En letras.com suele ser h1 = canción, h2 = artista.
    """
    h1 = soup.find("h1")
    h2 = soup.find("h2")
    song = h1.get_text(" ", strip=True) if h1 else ""
    artist = h2.get_text(" ", strip=True) if h2 else ""
    return song, artist


def _score_letras_candidate(
    requested_title: str, requested_artist: str, page_title: str, page_artist: str
) -> int:
    """
    Puntúa una página candidata para escoger la mejor cuando hay variantes
    (demo, acústica, etc.).
    """
    req_base = _slugify_letras(re.sub(r"\s*[\(\[].*?[\)\]]\s*", " ", requested_title).strip())
    page_slug = _slugify_letras(page_title)

    score = 0

    if page_slug == req_base:
        score += 5
    elif req_base and page_slug.startswith(req_base):
        score += 2
    elif req_base and req_base in page_slug:
        score += 1

    # Coincidencia de artista (suave: en letras puede venir sin "The", etc.)
    req_artist = _slugify_letras(requested_artist)
    page_artist_slug = _slugify_letras(page_artist)
    if req_artist and page_artist_slug == req_artist:
        score += 2
    elif req_artist and req_artist in page_artist_slug:
        score += 1

    # Penalizar "demo" si el usuario no lo pidió explícitamente
    req_has_demo = bool(re.search(r"\bdemo\b", requested_title, flags=re.I))
    page_has_demo = bool(re.search(r"\bdemo\b", page_title, flags=re.I))
    if page_has_demo and not req_has_demo:
        score -= 4

    return score


def _is_demo_title(page_title: str) -> bool:
    return bool(re.search(r"\bdemo\b", page_title or "", flags=re.I))


def _discover_song_urls_from_artist_page(artist_slug: str, title_base_slug: str) -> list[str]:
    """
    Fallback: muchas canciones tienen URL numérica (ej: /linkin-park/23091/).
    Para evitar quedarnos con versiones "demo", consultamos la página del artista
    y extraemos enlaces que parezcan canciones del artista.
    """
    if not artist_slug:
        return []

    candidates: list[str] = []
    for base in (f"https://www.letras.com/{artist_slug}/", f"https://m.letras.com/{artist_slug}/"):
        try:
            status, html = _fetch_letras_page(base)
            if status != 200 or not html:
                continue
            soup = BeautifulSoup(html, "html.parser")
            for a in soup.find_all("a", href=True):
                href = a["href"]
                text = a.get_text(" ", strip=True) or ""
                if not href.startswith("http"):
                    # normalizar a absoluto
                    if href.startswith("/"):
                        href = "https://www.letras.com" + href
                    else:
                        continue

                # Solo enlaces del propio artista
                if f"/{artist_slug}/" not in href:
                    continue

                text_slug = _slugify_letras(text)

                # Coincidencia fuerte por texto del enlace (normalmente es el título de la canción)
                if title_base_slug and text_slug == title_base_slug:
                    candidates.append(href)
                    continue

                # Coincidencia por slug del título en el href (si lo tiene)
                if title_base_slug and title_base_slug in href:
                    candidates.append(href)
                    continue

                # Enlaces numéricos SOLO si el texto parece el título buscado (o lo contiene)
                if re.search(rf"/{re.escape(artist_slug)}/\d+/?$", href):
                    if title_base_slug and (title_base_slug in text_slug or text_slug in title_base_slug):
                        candidates.append(href)

        except Exception:
            continue

    # Deduplicar preservando orden
    out: list[str] = []
    seen = set()
    for u in candidates:
        u = u.split("#", 1)[0]
        if u not in seen:
            seen.add(u)
            out.append(u)
    # No necesitamos muchos: con 10-15 suele bastar y reduce latencia muchísimo.
    return out[:15]


@lru_cache(maxsize=512)
def _fetch_letras_page(url: str) -> tuple[int, str]:
    # Session global simple para keep-alive (mejora latencia en múltiples requests)
    global _LETRAS_SESSION  # type: ignore[name-defined]
    try:
        sess = _LETRAS_SESSION  # type: ignore[name-defined]
    except Exception:
        sess = requests.Session()
        _LETRAS_SESSION = sess  # type: ignore[name-defined]

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122 Safari/537.36",
        "Accept-Language": "es-ES,es;q=0.9,en;q=0.8",
    }
    resp = sess.get(url, headers=headers, timeout=10, allow_redirects=True)
    return resp.status_code, resp.text


@lru_cache(maxsize=256)
def _get_lyrics_from_letras(artist: str, title: str) -> dict:
    urls = _letras_candidate_urls(artist, title)
    if not urls:
        return {"success": False, "error": "MissingMetadata", "message": "artist y title son obligatorios"}

    # Fallback: añadir URLs descubiertas desde la página del artista (incluye IDs numéricos)
    artist_slug = _slugify_letras(artist)
    title_base_slug = _slugify_letras(re.sub(r"\s*[\(\[].*?[\)\]]\s*", " ", title).strip())
    discovered = _discover_song_urls_from_artist_page(artist_slug, title_base_slug)
    for u in discovered:
        if u not in urls:
            urls.append(u)

    last_error = ""
    best: dict | None = None
    best_score = -10_000
    for url in urls:
        try:
            status, html = _fetch_letras_page(url)
            if status != 200 or not html:
                last_error = f"HTTP {status}"
                continue

            soup = BeautifulSoup(html, "html.parser")
            page_title, page_artist = _page_song_artist(soup)
            canonical = _page_canonical_url(soup)

            lyrics = _extract_lyrics_from_jsonld(soup)
            if not lyrics:
                lyrics = _extract_lyrics_from_dom(soup)

            if lyrics:
                # Si es demo y no la pidieron, no aceptarla como único resultado
                if _is_demo_title(page_title) and not re.search(r"\bdemo\b", title, flags=re.I):
                    last_error = "DemoVariantSkipped"
                    continue

                normalized = _normalize_lyrics_text(lyrics)
                lines_count = normalized.count("\n") + 1 if normalized else 0

                # Filtros: longitud mínima y marcadores de "sin letra"
                lowered = (normalized or "").lower()
                if (
                    (len(normalized) < MIN_LYRICS_CHARS)
                    or (lines_count < MIN_LYRICS_LINES)
                    or any(marker in lowered for marker in NO_LYRICS_TEXT_MARKERS)
                    or any(marker in (page_title or "").lower() for marker in NO_LYRICS_TEXT_MARKERS)
                ):
                    last_error = "LyricsFilteredLowQuality"
                    continue

                candidate = {
                    "success": True,
                    "source": "letras.com",
                    "sourceUrl": canonical or url,
                    "lyrics": normalized,
                    "pageTitle": page_title,
                    "pageArtist": page_artist,
                }
                score = _score_letras_candidate(title, artist, page_title, page_artist)
                if score > best_score:
                    best_score = score
                    best = candidate
                # Si es match perfecto, podemos cortar
                if best_score >= 7:
                    break

            last_error = "NoLyricsExtracted"
        except Exception as e:
            last_error = str(e)
            continue

    if best and best_score >= MIN_LYRICS_SCORE:
        return best

    return {
        "success": False,
        "error": "LyricsNotFound",
        "message": f"No se encontraron letras en letras.com ({last_error})",
        "triedUrls": urls[:10],
    }


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

        <div class="card">
            <h2>/api/lyrics</h2>
            <label>Título</label>
            <input id="lyrics-title" type="text" placeholder="Photograph" />
            <label>Artista</label>
            <input id="lyrics-artist" type="text" placeholder="Ed Sheeran" />
            <button onclick="callLyrics()">Obtener letra</button>
            <pre id="lyrics-output"></pre>
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
            async function callLyrics() {
                const title = document.getElementById('lyrics-title').value.trim();
                const artist = document.getElementById('lyrics-artist').value.trim();
                if (!title || !artist) return;
                const res = await fetch('/api/lyrics?title=' + encodeURIComponent(title) + '&artist=' + encodeURIComponent(artist));
                const text = await res.text();
                try {
                    document.getElementById('lyrics-output').textContent = JSON.stringify(JSON.parse(text), null, 2);
                } catch {
                    document.getElementById('lyrics-output').textContent = text;
                }
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


@app.get("/api/lyrics")
def api_lyrics(
    title: str = Query(..., alias="title"),
    artist: str = Query(..., alias="artist"),
):
    """
    Obtiene letras desde letras.com (scraping).
    """
    if not title or not title.strip() or not artist or not artist.strip():
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": "MissingMetadata",
                "message": "Please provide title and artist",
            },
        )

    result = _get_lyrics_from_letras(artist.strip(), title.strip())
    if result.get("success"):
        return result

    return JSONResponse(status_code=404, content=result)


def _download_with_yt_dlp(video_url: str) -> tuple[str, Generator[bytes, None, None], str]:
    """Descarga audio de YouTube y devuelve (filename, stream, media_type)"""
    # Directorio único por petición para evitar colisiones (WinError 32)
    job_dir = tempfile.mkdtemp(prefix="savetune_")
    out_tmpl = os.path.join(job_dir, "audio_%(id)s.%(ext)s")
    opts = {
        "format": "bestaudio/best",
        "outtmpl": out_tmpl,
        "quiet": True,
        "no_warnings": True,
        "prefer_ffmpeg": True,
        "keepvideo": False,
        "noplaylist": True,
        "writethumbnail": True,
        "concurrent_fragment_downloads": 4,
        "retries": 3,
        "fragment_retries": 3,
        "socket_timeout": 15,
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
        candidate = os.path.join(job_dir, f"audio_{video_id}{ext}")
        if os.path.isfile(candidate):
            path_file = candidate
            break

    if not path_file:
        for f in os.listdir(job_dir):
            if f.startswith(f"audio_{video_id}"):
                path_file = os.path.join(job_dir, f)
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
            # Limpieza completa del job dir (audio + thumbnail/temp)
            try:
                shutil.rmtree(job_dir, ignore_errors=True)
            except OSError:
                pass

    filename = f"{title_safe}.{ext}"
    return filename, stream_file(), media_type


@app.get("/api/download")
def api_download(videoId: str = Query(..., alias="videoId")):
    """Descarga audio de un video de YouTube"""
    global _active_downloads
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

    # Intentar ocupar uno de los slots (máx. 5 descargas simultáneas).
    acquired = _download_semaphore.acquire(blocking=False)
    if not acquired:
        _ensure_workers_started()
        with _downloads_lock:
            if len(_pending_job_ids) >= MAX_DOWNLOAD_QUEUE_SIZE:
                return JSONResponse(
                    status_code=429,
                    content={
                        "success": False,
                        "error": "QueueFull",
                        "message": "Cola llena. Vuelve a intentarlo en unos segundos.",
                    },
                )
        job_id = str(uuid.uuid4())

        with _downloads_lock:
            queue_position = _active_downloads + len(_pending_job_ids) + 1
            _download_jobs[job_id] = {
                "type": "download",
                "videoId": videoId.strip(),
                "status": "queued",
                "queuePosition": queue_position,
                "createdAt": int(time.time()),
            }
            _pending_job_ids.append(job_id)

        _download_job_queue.put(job_id)

        return JSONResponse(
            status_code=202,
            content={
                "success": False,
                "error": "Queued",
                "message": f"Servidor petado: eres el numero {queue_position}. Te toca cuando se libere el numero {max(queue_position - 1, 0)}.",
                "jobId": job_id,
                "queuePosition": queue_position,
            },
        )

    with _downloads_lock:
        _active_downloads += 1

    try:
        filename, stream_gen, media_type = _download_with_yt_dlp(video_url)
    except Exception as e:
        with _downloads_lock:
            _active_downloads = max(0, _active_downloads - 1)
        _download_semaphore.release()
        print(f"❌ Error descargando: {e}")
        return JSONResponse(
            status_code=503,
            content={
                "success": False,
                "error": "DownloadUnavailable",
                "message": str(e),
            },
        )

    # El trabajo pesado (yt-dlp/ffmpeg) ya terminó; liberamos el slot.
    with _downloads_lock:
        _active_downloads = max(0, _active_downloads - 1)
    _download_semaphore.release()

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
    global _active_downloads
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

    # Intentar ocupar uno de los slots (máx. 5 descargas simultáneas).
    acquired = _download_semaphore.acquire(blocking=False)
    if not acquired:
        _ensure_workers_started()
        with _downloads_lock:
            if len(_pending_job_ids) >= MAX_DOWNLOAD_QUEUE_SIZE:
                return JSONResponse(
                    status_code=429,
                    content={
                        "success": False,
                        "error": "QueueFull",
                        "message": "Cola llena. Vuelve a intentarlo en unos segundos.",
                    },
                )
        job_id = str(uuid.uuid4())
        with _downloads_lock:
            queue_position = _active_downloads + len(_pending_job_ids) + 1
            _download_jobs[job_id] = {
                "type": "download-auto",
                "final_query": final_query,
                "title": title,
                "artist": artist,
                "album": album,
                "status": "queued",
                "queuePosition": queue_position,
                "createdAt": int(time.time()),
            }
            _pending_job_ids.append(job_id)
        _download_job_queue.put(job_id)

        return JSONResponse(
            status_code=202,
            content={
                "success": False,
                "error": "Queued",
                "message": f"Servidor petado: eres el numero {queue_position}. Te toca cuando se libere el numero {max(queue_position - 1, 0)}.",
                "jobId": job_id,
                "queuePosition": queue_position,
            },
        )

    with _downloads_lock:
        _active_downloads += 1

    # Usar un directorio único por petición para evitar colisiones en Temp (WinError 32)
    job_dir = tempfile.mkdtemp(prefix="savetune_")
    out_tmpl = os.path.join(job_dir, "audio_%(id)s.%(ext)s")

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
            candidate = os.path.join(job_dir, f"audio_{video_id}{ext}")
            if os.path.isfile(candidate):
                path_file = candidate
                break

        if not path_file:
            for f in os.listdir(job_dir):
                if f.startswith(f"audio_{video_id}"):
                    path_file = os.path.join(job_dir, f)
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
                    shutil.rmtree(job_dir, ignore_errors=True)
                except OSError:
                    pass

        filename = f"{title_safe}.{ext}"

        print(f"✅ Descarga completada: {filename}")

        resp = StreamingResponse(
            stream_file(),
            media_type=media_type,
            headers={
                "Content-Disposition": f'attachment; filename="{filename}"',
            },
        )

        # El trabajo pesado ya terminó; liberamos el slot (no hace falta mantenerlo durante el streaming).
        with _downloads_lock:
            _active_downloads = max(0, _active_downloads - 1)
        _download_semaphore.release()

        return resp

    except Exception as e:
        print(f"❌ Error en descarga automática: {e}")
        # Limpieza si falló antes de crear el stream
        try:
            shutil.rmtree(job_dir, ignore_errors=True)
        except Exception:
            pass

        with _downloads_lock:
            _active_downloads = max(0, _active_downloads - 1)
        _download_semaphore.release()

        return JSONResponse(
            status_code=503,
            content={
                "success": False,
                "error": "AutoDownloadError",
                "message": str(e),
            },
        )


@app.get("/api/download-job")
def api_download_job(jobId: str = Query(..., alias="jobId")):
    """
    Estado de un job en cola para descargas.
    Usado cuando /api/download o /api/download-auto devuelven 'error=Queued'.
    """
    job = _download_jobs.get(jobId)
    if not job:
        return JSONResponse(
            status_code=404,
            content={"success": False, "error": "JobNotFound", "message": "No existe el job"},
        )

    status = job.get("status") or "queued"
    queue_position = job.get("queuePosition")
    if status == "queued":
        queue_position = _get_job_snapshot_position(jobId)

    payload: dict = {
        "success": True,
        "jobId": jobId,
        "status": status,
    }
    if queue_position is not None:
        payload["queuePosition"] = queue_position

    if status == "ready":
        payload["filename"] = job.get("filename") or ""
        payload["media_type"] = job.get("media_type") or job.get("mediaType") or ""
    if status == "error":
        payload["message"] = job.get("error") or ""

    return payload


@app.get("/api/download-job/stream")
def api_download_job_stream(jobId: str = Query(..., alias="jobId")):
    """Stream de audio para un job listo."""
    job = _download_jobs.get(jobId)
    if not job:
        return JSONResponse(
            status_code=404,
            content={"success": False, "error": "JobNotFound", "message": "No existe el job"},
        )

    status = job.get("status")
    if status != "ready":
        if status == "error":
            return JSONResponse(
                status_code=500,
                content={
                    "success": False,
                    "error": "JobError",
                    "message": job.get("error") or "Job falló",
                },
            )

        return JSONResponse(
            status_code=425,
            content={
                "success": False,
                "error": "NotReady",
                "status": status,
                "message": "Aún no está listo para descargar",
                "queuePosition": _get_job_snapshot_position(jobId),
            },
        )

    file_path = job.get("file_path") or ""
    media_type = job.get("media_type") or "audio/mpeg"
    filename = job.get("filename") or "audio.mp3"
    job_dir = job.get("job_dir") or ""

    if not file_path or not os.path.isfile(file_path):
        return JSONResponse(
            status_code=503,
            content={"success": False, "error": "FileMissing", "message": "El archivo no está disponible"},
        )

    def stream_file() -> Generator[bytes, None, None]:
        try:
            with open(file_path, "rb") as f:
                while chunk := f.read(8192):
                    yield chunk
        finally:
            # Eliminar recursos del job al terminar el streaming.
            try:
                if job_dir:
                    shutil.rmtree(job_dir, ignore_errors=True)
            except OSError:
                pass
            with _downloads_lock:
                _download_jobs.pop(jobId, None)

    return StreamingResponse(
        stream_file(),
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
    print(f"🆕 Nuevo endpoint: /api/download-auto (descarga automática)")

    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)