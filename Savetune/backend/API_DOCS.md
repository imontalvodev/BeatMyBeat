## SaveTune Backend (FastAPI) – Documentación para Frontend

Backend en Python con FastAPI. Expone endpoints para:
- **Salud del servicio**
- **Lectura de playlists de Spotify**
- **Búsqueda de vídeos en YouTube**
- **Descarga de audio desde YouTube (por `videoId` o búsqueda automática)**

El backend está pensado para correr en `http://localhost:4000` en desarrollo (ver `main.py`), pero puedes ajustar la URL base según tu entorno.

---

## Configuración general

- **Base URL (dev sugerida)**: `http://localhost:4000`
- **CORS**: configurado con:
  - `allow_origins=["*"]`
  - `allow_methods=["*"]`
  - `allow_headers=["*"]`

Desde el frontend se puede hacer `fetch` directamente al backend sin configuración especial de CORS.

---

## 1. `GET /health`

**Descripción**: Comprobación de salud del backend y estado de configuración de Spotify API.

**Request**
- Método: `GET`
- URL: `/health`
- Parámetros: ninguno

**Response 200 – JSON**

```json
{
  "status": "ok",
  "backend": "python",
  "scraper": "selenium",
  "spotify_api_configured": true
}
```

- `spotify_api_configured`: `true` si las credenciales de Spotify están bien configuradas en el servidor, si no `false`.

---

## 2. `GET /api/playlist`

**Descripción**: Obtiene la información de una playlist de Spotify (título, nº de canciones y lista de canciones) a partir de la URL de la playlist.

El backend intenta primero usar la **Spotify Web API** y, si falla, usa un **scraper con Selenium**. El formato de respuesta es el mismo para ambos.

**Request**
- Método: `GET`
- URL: `/api/playlist`
- Query params:
  - `url` (obligatorio): URL completa de la playlist de Spotify.

Ejemplo:

```text
GET /api/playlist?url=https://open.spotify.com/playlist/XXXXXXXXXXXX
```

**Response 200 – JSON (éxito)**

```json
{
  "success": true,
  "playlist": {
    "name": "Nombre de la playlist",
    "totalTracks": 42
  },
  "songs": [
    {
      "id": "5VnDkUNQs5QJQoOEQLsKER",
      "title": "Nombre de la canción",
      "artist": "Nombre del artista",
      "album": "Nombre del álbum",
      "imageUrl": "https://i.scdn.co/image/...",
      "duration": 215
    }
  ]
}
```

- `duration`: duración en **segundos**.
- La lista `songs` sólo incluye canciones **válidas** (con id, título no vacío ni "Unknown").

**Errores**

- **400 – URL inválida**

```json
{
  "success": false,
  "error": "Invalid Spotify URL",
  "message": "Please provide a valid Spotify playlist URL"
}
```

- **500 – Fallo tanto en API como en scraper**

```json
{
  "success": false,
  "error": "PlaylistFetchError",
  "message": "No se pudo obtener la playlist (API y scraper fallaron)"
}
```

---

## 3. `GET /api/search-youtube`

**Descripción**: Busca un vídeo en YouTube y devuelve el mejor resultado encontrado. Este endpoint **no descarga**, sólo busca y devuelve información del vídeo.

Se puede usar de dos formas:
- Proporcionando **metadatos de canción** (`title`, `artist`, `album`) y se construye una query del tipo `"title artist album official audio"`.
- O bien usando directamente el parámetro `query` (modo "legacy").

**Request**
- Método: `GET`
- URL: `/api/search-youtube`
- Query params (alternativas):
  - Opción 1 (recomendada): metadatos de canción
    - `title` (opcional pero recomendado)
    - `artist` (opcional)
    - `album` (opcional)
  - Opción 2: query libre
    - `query` (string) – consulta de búsqueda completa.

Ejemplos:

```text
GET /api/search-youtube?title=SexyBack&artist=Justin%20Timberlake&album=FutureSex%2FLoveSounds

GET /api/search-youtube?query=SexyBack%20Justin%20Timberlake%20official%20audio
```

**Response 200 – JSON (éxito)**

```json
{
  "success": true,
  "video": {
    "id": "3gOHvDP_vCs",
    "title": "SexyBack (Audio)",
    "url": "https://www.youtube.com/watch?v=3gOHvDP_vCs",
    "thumbnail": "https://i.ytimg.com/vi/3gOHvDP_vCs/hqdefault.jpg"
  }
}
```

Si no se encuentra nada:

```json
{
  "success": true,
  "video": {
    "id": "",
    "title": "",
    "url": "",
    "thumbnail": ""
  }
}
```

**Errores**

- **400 – Falta query o metadatos**

```json
{
  "success": false,
  "error": "Missing query",
  "message": "Please provide a search query or song metadata"
}
```

- **500 – Error interno en la búsqueda de YouTube**

```json
{
  "success": false,
  "error": "YouTubeSearchError",
  "message": "mensaje de error interno"
}
```

---

## 4. `GET /api/download`

**Descripción**: Descarga el audio de un vídeo de YouTube a partir de su `videoId`. Devuelve un **stream de audio** (no JSON).

**Request**
- Método: `GET`
- URL: `/api/download`
- Query params:
  - `videoId` (obligatorio): id del vídeo de YouTube (por ejemplo `3gOHvDP_vCs`).

Ejemplo:

```text
GET /api/download?videoId=3gOHvDP_vCs
```

**Response 200 – Audio stream**

- Tipo de respuesta: `StreamingResponse`
- Cabeceras:
  - `Content-Disposition: attachment; filename="<titulo-sanitizado>.<ext>"`
- `Content-Type`:
  - `"audio/mpeg"` si es `.mp3`
  - `"audio/mp4"` si es `.m4a`/`.mp4`
  - `"audio/webm"` para el resto

En la práctica, normalmente será `audio/mpeg` (MP3).

**Cómo consumir desde el frontend**

- Para **forzar descarga**:

```ts
const url = `${BASE_URL}/api/download?videoId=${encodeURIComponent(videoId)}`;
window.open(url, "_blank");
```

- Si necesitas mostrar un **botón de descarga**:

```ts
const a = document.createElement("a");
a.href = url;
a.download = ""; // dejar vacío deja que el servidor proponga el nombre
document.body.appendChild(a);
a.click();
a.remove();
```

**Errores**

- **400 – Falta videoId**

```json
{
  "success": false,
  "error": "Missing videoId",
  "message": "Please provide a YouTube video ID"
}
```

- **503 – Error al descargar**

```json
{
  "success": false,
  "error": "DownloadUnavailable",
  "message": "mensaje de error interno"
}
```

---

## 5. `GET /api/download-auto`

**Descripción**: Endpoint **nuevo** que:
1. Construye una búsqueda en YouTube a partir de metadatos de la canción (o una query libre).
2. Descarga automáticamente el **mejor resultado**.
3. Devuelve un **stream de audio** (misma idea que `/api/download` pero sin necesidad de `videoId`).

**Request**
- Método: `GET`
- URL: `/api/download-auto`
- Query params (alternativas):
  - Opción 1 (recomendada): metadatos de canción
    - `title` (opcional pero recomendado)
    - `artist` (opcional)
    - `album` (opcional)
  - Opción 2: query libre
    - `query` (string)

Ejemplos:

```text
GET /api/download-auto?query=SexyBack%20Timbaland

GET /api/download-auto?title=SexyBack&artist=Justin%20Timberlake&album=FutureSex%2FLoveSounds
```

**Construcción de la búsqueda**

- Si se pasan `title`/`artist`/`album`:
  - Se unen los valores no vacíos y se añade `"official audio"`, por ejemplo:
  - `"SexyBack Justin Timberlake FutureSex/LoveSounds official audio"`
- Si no se pasa ningún metadato, se usa el valor de `query` tal cual.

**Response 200 – Audio stream**

Igual que `/api/download`:
- Tipo: `StreamingResponse`
- `Content-Type`: depende de la extensión (`audio/mpeg` si `.mp3`, etc.).
- `Content-Disposition` con un nombre de archivo generado a partir del título del vídeo.

**Uso típico desde frontend**

Ejemplo TypeScript para disparar la descarga:

```ts
const params = new URLSearchParams({
  title: song.title,
  artist: song.artist,
  album: song.album ?? ""
});

const url = `${BASE_URL}/api/download-auto?${params.toString()}`;
const a = document.createElement("a");
a.href = url;
a.download = "";
document.body.appendChild(a);
a.click();
a.remove();
```

**Errores**

- **400 – Falta query o metadatos**

```json
{
  "success": false,
  "error": "Missing query",
  "message": "Please provide a search query or song metadata (title, artist, album)"
}
```

- **503 – Error interno en la descarga automática**

```json
{
  "success": false,
  "error": "AutoDownloadError",
  "message": "mensaje de error interno"
}
```

---

## 6. Consideraciones de integración desde frontend

- **Base URL configurable**: se recomienda tener una variable de entorno o configuración (`BACKEND_URL`) para que el frontend no asuma `localhost:4000` en producción.
- **Manejo de errores**:
  - Todos los endpoints JSON (`/health`, `/api/playlist`, `/api/search-youtube`) devuelven un campo `success` y `error`/`message` en caso de fallo.
  - Los endpoints de descarga (`/api/download`, `/api/download-auto`) devuelven JSON de error cuando algo va mal (`status` 4xx/5xx), por lo que conviene:
    - Si se llama con `fetch`, revisar `res.ok` antes de tratarlo como blob.
    - Si se llama abriendo una pestaña/descarga directa, el navegador mostrará el JSON como texto en caso de error.
- **Formato interno de canción (frontend)**:
  - El modelo recomendado para representar una canción en frontend es compatible con el que devuelve `/api/playlist`:
    - `id: string`
    - `title: string`
    - `artist: string`
    - `album: string`
    - `imageUrl: string`
    - `duration: number` (segundos)

Con esta documentación el equipo de frontend puede consumir todos los endpoints actuales del backend sin tener que leer el código de Python.

