#!/usr/bin/env python3
"""
Cliente mínimo de la API de Spotify para enriquecer metadatos de tracks.
Se usa únicamente para obtener artista(s) y álbum a partir del track_id.
"""

import os
import time
import base64
import json
from typing import Optional, Tuple
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode


_SPOTIFY_TOKEN: Optional[str] = None
_SPOTIFY_TOKEN_EXPIRES_AT: float = 0.0

# Token de usuario (Authorization Code Flow)
_USER_ACCESS_TOKEN: Optional[str] = None
_USER_REFRESH_TOKEN: Optional[str] = None
_USER_TOKEN_EXPIRES_AT: float = 0.0


def _load_env_file_once() -> None:
    """
    Carga un archivo .env de forma muy simple si existe.
    Busca primero en la raíz del proyecto y luego en la carpeta backend.
    """
    env_loaded_flag = "_SPOTIFY_ENV_LOADED"
    if os.environ.get(env_loaded_flag) == "1":
        return

    # Intentar en raíz del proyecto y en backend/
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
    candidate_paths = [
        os.path.join(base_dir, ".env"),
        os.path.join(os.path.dirname(__file__), "..", ".env"),
    ]

    for path in candidate_paths:
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
            print(f"⚠️ No se pudo leer .env ({path}): {e}")

    os.environ[env_loaded_flag] = "1"


def _get_client_credentials() -> Tuple[str, str]:
    # Nos aseguramos de haber intentado cargar .env antes de leer variables
    _load_env_file_once()
    client_id = os.getenv("SPOTIFY_CLIENT_ID", "").strip()
    client_secret = os.getenv("SPOTIFY_CLIENT_SECRET", "").strip()
    if not client_id or not client_secret:
        raise RuntimeError("SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET no configurados")
    return client_id, client_secret


def _get_redirect_uri() -> str:
    _load_env_file_once()
    redirect_uri = os.getenv("SPOTIFY_REDIRECT_URI", "").strip()
    if not redirect_uri:
        # Valor por defecto: backend local en puerto 4000
        redirect_uri = "http://localhost:4000/spotify/callback"
    return redirect_uri


def _refresh_access_token() -> str:
    client_id, client_secret = _get_client_credentials()

    creds = f"{client_id}:{client_secret}".encode("utf-8")
    basic_token = base64.b64encode(creds).decode("ascii")

    data = urlencode({"grant_type": "client_credentials"}).encode("utf-8")
    req = Request(
        "https://accounts.spotify.com/api/token",
        data=data,
        headers={
            "Authorization": f"Basic {basic_token}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )

    with urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read().decode("utf-8") or "{}")

    access_token = payload.get("access_token", "")
    expires_in = int(payload.get("expires_in", 0)) or 0

    if not access_token:
        raise RuntimeError(f"No se pudo obtener token de Spotify: {payload!r}")

    global _SPOTIFY_TOKEN, _SPOTIFY_TOKEN_EXPIRES_AT
    _SPOTIFY_TOKEN = access_token
    # Refrescar un poco antes del vencimiento real
    _SPOTIFY_TOKEN_EXPIRES_AT = time.time() + max(60, expires_in - 60)

    return access_token


def get_authorize_url(state: str = "savetune") -> str:
    """
    Construye la URL de autorización de Spotify para el Authorization Code Flow.
    El usuario debe abrir esta URL en el navegador para conceder permisos.
    """
    _load_env_file_once()
    client_id, _ = _get_client_credentials()
    params = {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": _get_redirect_uri(),
        # Solo necesitamos acceso de lectura básica
        "scope": "user-read-email",
        "state": state,
    }
    return "https://accounts.spotify.com/authorize?" + urlencode(params)


def handle_authorization_callback(code: str) -> bool:
    """
    Maneja el callback de Spotify (Authorization Code Flow):
    - Intercambia el 'code' por access_token + refresh_token
    - Guarda los tokens en memoria para usarlos en peticiones posteriores.
    """
    global _USER_ACCESS_TOKEN, _USER_REFRESH_TOKEN, _USER_TOKEN_EXPIRES_AT

    if not code:
        return False

    client_id, client_secret = _get_client_credentials()
    redirect_uri = _get_redirect_uri()

    data = urlencode(
        {
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": redirect_uri,
        }
    ).encode("utf-8")

    creds = f"{client_id}:{client_secret}".encode("utf-8")
    basic_token = base64.b64encode(creds).decode("ascii")

    req = Request(
        "https://accounts.spotify.com/api/token",
        data=data,
        headers={
            "Authorization": f"Basic {basic_token}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )

    try:
        with urlopen(req, timeout=10) as resp:
            payload = json.loads(resp.read().decode("utf-8") or "{}")
    except Exception as e:
        print(f"⚠️ Error intercambiando code de Spotify: {e}")
        return False

    access_token = payload.get("access_token", "")
    refresh_token = payload.get("refresh_token", "")
    expires_in = int(payload.get("expires_in", 0)) or 0

    if not access_token:
        print(f"⚠️ Respuesta inválida de Spotify en callback: {payload!r}")
        return False

    _USER_ACCESS_TOKEN = access_token
    if refresh_token:
        _USER_REFRESH_TOKEN = refresh_token
    _USER_TOKEN_EXPIRES_AT = time.time() + max(60, expires_in - 60)

    print("✅ Token de usuario de Spotify guardado correctamente")
    return True


def _get_access_token() -> Optional[str]:
    global _USER_ACCESS_TOKEN, _USER_TOKEN_EXPIRES_AT, _USER_REFRESH_TOKEN
    now = time.time()

    # 1) Preferir token de usuario si existe y sigue siendo válido
    if _USER_ACCESS_TOKEN and now < _USER_TOKEN_EXPIRES_AT:
        return _USER_ACCESS_TOKEN

    # 2) Si hay refresh_token de usuario y el access ha caducado, intentar refrescarlo
    if _USER_REFRESH_TOKEN:
        try:
            client_id, client_secret = _get_client_credentials()
            data = urlencode(
                {
                    "grant_type": "refresh_token",
                    "refresh_token": _USER_REFRESH_TOKEN,
                }
            ).encode("utf-8")
            creds = f"{client_id}:{client_secret}".encode("utf-8")
            basic_token = base64.b64encode(creds).decode("ascii")
            req = Request(
                "https://accounts.spotify.com/api/token",
                data=data,
                headers={
                    "Authorization": f"Basic {basic_token}",
                    "Content-Type": "application/x-www-form-urlencoded",
                },
            )
            with urlopen(req, timeout=10) as resp:
                payload = json.loads(resp.read().decode("utf-8") or "{}")
            access_token = payload.get("access_token", "")
            expires_in = int(payload.get("expires_in", 0)) or 0
            if access_token:
                _USER_ACCESS_TOKEN = access_token
                _USER_TOKEN_EXPIRES_AT = time.time() + max(60, expires_in - 60)
                return _USER_ACCESS_TOKEN
        except Exception as e:
            print(f"⚠️ No se pudo refrescar token de usuario de Spotify: {e}")

    # 3) Si no hay token de usuario válido, no intentamos usar client_credentials
    #    para /v1/tracks, ya que provoca 403 y no aporta mejoras en precisión.
    return None


def get_track_metadata(track_id: str) -> dict:
    """
    Devuelve metadatos básicos de un track:
    {
      "artist": "Artista principal",
      "artists": "Artista 1, Artista 2",
      "album": "Nombre del álbum"
    }

    Si hay cualquier error (incluido no tener credenciales), devuelve
    un dict vacío {} para que el caller pueda hacer fallback.
    """
    track_id = (track_id or "").strip()
    if not track_id:
        return {}

    token = _get_access_token()
    if not token:
        return {}

    url = f"https://api.spotify.com/v1/tracks/{track_id}"
    req = Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        },
    )

    try:
        with urlopen(req, timeout=10) as resp:
            payload = json.loads(resp.read().decode("utf-8") or "{}")
    except HTTPError as e:
        # Leer cuerpo de error para entender el motivo exacto del 403/401/etc.
        try:
            body = e.read().decode("utf-8", errors="ignore")
        except Exception:
            body = ""
        print(
            f"⚠️ Error llamando a Spotify tracks/{track_id}: "
            f"{e.code} {e.reason} - {body}"
        )
        return {}
    except Exception as e:
        print(f"⚠️ Error llamando a Spotify tracks/{track_id}: {e}")
        return {}

    try:
        artists = payload.get("artists") or []
        artist_names = [a.get("name", "").strip() for a in artists if a.get("name")]
        main_artist = artist_names[0] if artist_names else ""
        album = payload.get("album") or {}
        album_name = (album.get("name") or "").strip()

        result = {}
        if main_artist:
            result["artist"] = main_artist
        if artist_names:
            result["artists"] = ", ".join(artist_names)
        if album_name:
            result["album"] = album_name

        return result
    except Exception as e:
        print(f"⚠️ Error parseando metadatos de Spotify para track {track_id}: {e}")
        return {}

