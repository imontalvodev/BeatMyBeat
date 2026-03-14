#!/usr/bin/env python3
"""
Spotify Web API client para obtener ítems de playlists.
Usa Client Credentials Flow. Para playlists de terceros puede ser necesario
OAuth de usuario (ver documentación: Get Playlist Items).
Ref: https://developer.spotify.com/documentation/web-api/reference/get-playlists-items
"""

import os
import re
import base64
import requests


def _extract_playlist_id(url: str) -> str | None:
    """Extrae el ID de la playlist desde una URL de Spotify."""
    if not url or "playlist/" not in url:
        return None
    m = re.search(r"playlist/([a-zA-Z0-9]+)", url)
    return m.group(1) if m else None


def _ms_to_mm_ss(ms: int) -> str:
    """Convierte duración en ms a formato mm:ss."""
    if ms is None or ms <= 0:
        return "0:00"
    secs = ms // 1000
    return f"{secs // 60}:{secs % 60:02d}"


class SpotifyWebAPI:
    """
    Cliente para la Spotify Web API (Client Credentials).
    Obtiene todos los ítems de una playlist con paginación (limit 50).
    """

    def __init__(self, client_id: str | None = None, client_secret: str | None = None):
        self.client_id = client_id or os.environ.get("SPOTIFY_CLIENT_ID", "").strip()
        self.client_secret = client_secret or os.environ.get("SPOTIFY_CLIENT_SECRET", "").strip()
        self.access_token: str | None = None

    def is_configured(self) -> bool:
        return bool(self.client_id and self.client_secret)

    def get_access_token(self) -> str:
        """Obtiene el access token usando Client Credentials Flow."""
        if not self.client_id or not self.client_secret:
            raise ValueError("SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET son obligatorios")

        auth_string = f"{self.client_id}:{self.client_secret}"
        auth_bytes = auth_string.encode("utf-8")
        auth_base64 = base64.b64encode(auth_bytes).decode("utf-8")

        url = "https://accounts.spotify.com/api/token"
        headers = {
            "Authorization": f"Basic {auth_base64}",
            "Content-Type": "application/x-www-form-urlencoded",
        }
        data = {"grant_type": "client_credentials"}

        response = requests.post(url, headers=headers, data=data, timeout=15)

        if response.status_code != 200:
            raise Exception(f"Error obteniendo token: {response.status_code} - {response.text}")

        self.access_token = response.json()["access_token"]
        return self.access_token

    def get_playlist_items(self, playlist_id: str) -> list[dict]:
        """
        Obtiene TODOS los ítems de una playlist (solo tracks, paginación automática).
        playlist_id: ID de la playlist (o URL; se extrae el ID).
        """
        pid = _extract_playlist_id(playlist_id) if "/" in playlist_id else playlist_id
        if not pid:
            raise ValueError("ID o URL de playlist no válida")

        if not self.access_token:
            self.get_access_token()

        url = f"https://api.spotify.com/v1/playlists/{pid}"
        headers = {"Authorization": f"Bearer {self.access_token}"}

        # Primero obtener nombre y total (opcional)
        meta = requests.get(url, headers=headers, timeout=15)
        if meta.status_code != 200:
            raise Exception(f"Error obteniendo playlist: {meta.status_code} - {meta.text}")

        playlist_meta = meta.json()
        name = playlist_meta.get("name") or "Unknown Playlist"

        # Items con paginación (endpoint: Get Playlist Items)
        items_url = f"https://api.spotify.com/v1/playlists/{pid}/items"
        params = {"limit": 50, "offset": 0}
        all_items = []

        while True:
            response = requests.get(items_url, headers=headers, params=params, timeout=15)

            if response.status_code == 403:
                raise Exception(
                    "403 Forbidden: Este endpoint solo permite playlists del usuario actual. "
                    "Usa OAuth (Authorization Code) con una cuenta de usuario para playlists propias."
                )
            if response.status_code != 200:
                raise Exception(f"Error: {response.status_code} - {response.text}")

            data = response.json()
            all_items.extend(data.get("items", []))

            if data.get("next") is None:
                break
            params["offset"] += params["limit"]

        return all_items, name, playlist_meta.get("external_urls", {}).get("spotify", "")

    def items_to_canciones(self, items: list, playlist_url: str = "") -> list[dict]:
        """
        Convierte la respuesta de la API al mismo formato que el scraper:
        id, titulo, artistas, album, duracion_segundos, duracion, spotify_url, imagen_url.
        """
        canciones = []
        for item in items:
            track = item.get("track")
            if not track or track.get("type") != "track":
                continue

            duration_ms = track.get("duration_ms") or 0
            secs = duration_ms // 1000

            artistas = ", ".join(a.get("name", "") for a in track.get("artists", []) if a.get("name"))
            album_obj = track.get("album") or {}
            album_name = album_obj.get("name") or "Unknown Album"
            images = album_obj.get("images") or []
            imagen_url = images[0].get("url", "") if images else ""

            canciones.append({
                "id": track.get("id", ""),
                "titulo": track.get("name") or "Unknown",
                "artistas": artistas or "Unknown Artist",
                "album": album_name,
                "duracion_segundos": secs,
                "duracion": _ms_to_mm_ss(duration_ms),
                "spotify_url": (track.get("external_urls") or {}).get("spotify", ""),
                "imagen_url": imagen_url,
            })
        return canciones

    def obtener_canciones_playlist(self, url: str) -> dict:
        """
        Obtiene las canciones de una playlist vía Web API y devuelve el mismo
        formato que SpotifyPlaylistScraper.obtener_canciones_playlist para drop-in.
        """
        try:
            items, nombre_playlist, playlist_url_api = self.get_playlist_items(url)
            playlist_url = playlist_url_api or url

            canciones = self.items_to_canciones(items, playlist_url)

            return {
                "success": True,
                "playlist": {
                    "nombre": nombre_playlist,
                    "total_canciones": len(canciones),
                    "url": playlist_url,
                },
                "canciones": canciones,
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
            }
