## Middleware SaveTune – Conexión Front ↔ Middle ↔ Python Backend

Este middleware es un servidor Express (Node.js) que:
- Expone una API HTTP simple hacia el **frontend**.
- Reenvía las peticiones al **backend Python (FastAPI)**.
- Normaliza errores y gestiona el *streaming* de audio para las descargas.

Se asume que:
- El middleware corre, por ejemplo, en `https://api.savetune.com` o `http://localhost:3000` en desarrollo.
- El backend Python corre en `PY_BACKEND_URL` (por defecto `http://localhost:4000`).

---

## 1. Configuración de entorno

En el middleware:

- **Variable de entorno importante**
  - `PY_BACKEND_URL`: URL base del backend Python.
    - Ejemplo dev: `PY_BACKEND_URL=http://localhost:4000`
    - Ejemplo prod: `PY_BACKEND_URL=https://python-backend.savetune.com`

En el frontend:

- **Recomendado** usar algo como:
  - `MIDDLEWARE_URL`: URL base del middleware.
    - Ejemplo dev: `MIDDLEWARE_URL=http://localhost:3000`
    - Ejemplo prod: `MIDDLEWARE_URL=https://api.savetune.com`

---

## 2. Flujo general de peticiones

1. **Frontend → Middleware**
   - El frontend llama a rutas como:
     - `GET {MIDDLEWARE_URL}/api/playlist`
     - `GET {MIDDLEWARE_URL}/api/search-youtube`
     - `GET {MIDDLEWARE_URL}/api/lyrics`
     - `GET {MIDDLEWARE_URL}/api/download`
     - `GET {MIDDLEWARE_URL}/api/download-auto`

2. **Middleware → Backend Python**
   - El middleware reenvía la petición a:
     - `GET {PY_BACKEND_URL}/api/playlist`
     - `GET {PY_BACKEND_URL}/api/search-youtube`
     - `GET {PY_BACKEND_URL}/api/lyrics`
     - `GET {PY_BACKEND_URL}/api/download`
     - `GET {PY_BACKEND_URL}/api/download-auto`

3. **Respuestas**
   - Para endpoints JSON, el middleware:
     - Copia el status code.
     - Copia el `Content-Type`.
     - Reenvía el cuerpo tal cual.
   - Para endpoints de descarga (audio), el middleware:
     - Copia `Content-Type` y `Content-Disposition`.
     - Crea un *stream* Node.js desde el cuerpo de la respuesta Python y lo envía al cliente.

---

## 3. Endpoints JSON: Front ↔ Middle ↔ Back

### 3.1 `GET /api/playlist`

**Frontend → Middleware**

- Método: `GET`
- URL: `{MIDDLEWARE_URL}/api/playlist?url=<spotify_playlist_url>`

**Middleware → Backend Python**

- Método: `GET`
- URL: `{PY_BACKEND_URL}/api/playlist?url=<spotify_playlist_url>`

**Validación en middleware**

- Se valida que el parámetro `url` sea una URL de playlist de Spotify válida.
- Si es inválida, responde directamente:

```json
{
  "success": false,
  "error": "Invalid Spotify URL",
  "message": "Please provide a valid Spotify playlist URL"
}
```

**Respuesta al frontend**

- Reenvía el JSON que devuelve el backend Python (ver `backend/API_DOCS.md`).

Uso típico desde frontend:

```ts
const params = new URLSearchParams({ url: playlistUrl });
const res = await fetch(`${MIDDLEWARE_URL}/api/playlist?${params.toString()}`);
const data = await res.json();
```

---

### 3.2 `GET /api/search-youtube`

**Frontend → Middleware**

- Método: `GET`
- URL: `{MIDDLEWARE_URL}/api/search-youtube`
- Query params (alternativas):
  - Opción 1 (metadatos de canción):
    - `title` (opcional)
    - `artist` (opcional)
    - `album` (opcional)
  - Opción 2 (query libre):
    - `query`

Ejemplos:

```text
GET {MIDDLEWARE_URL}/api/search-youtube?title=SexyBack&artist=Justin%20Timberlake
GET {MIDDLEWARE_URL}/api/search-youtube?query=SexyBack%20Justin%20Timberlake%20official%20audio
```

**Middleware → Backend Python**

- Método: `GET`
- URL: `{PY_BACKEND_URL}/api/search-youtube` con los mismos query params recibidos.

**Validación en middleware**

- Si **no** se proporciona `query`, `title`, `artist` ni `album`, responde:

```json
{
  "success": false,
  "error": "Missing query",
  "message": "Please provide a search query or song metadata"
}
```

**Respuesta al frontend**

- Reenvía tal cual la respuesta JSON del backend Python (éxito o error).

Uso típico desde frontend:

```ts
const params = new URLSearchParams({
  title: song.title,
  artist: song.artist,
  album: song.album ?? ""
});

const res = await fetch(`${MIDDLEWARE_URL}/api/search-youtube?${params.toString()}`);
const data = await res.json(); // data.video.id, data.video.title, etc.
```

---

### 3.3 `GET /api/lyrics` (nuevo)

Este endpoint expone letras de canciones hacia el frontend, proxyeando el endpoint del backend Python:
- Backend Python: `GET {PY_BACKEND_URL}/api/lyrics`

**Frontend → Middleware**

- Método: `GET`
- URL: `{MIDDLEWARE_URL}/api/lyrics`
- Query params:
  - `title` (obligatorio)
  - `artist` (obligatorio)

Ejemplo:

```text
GET {MIDDLEWARE_URL}/api/lyrics?title=In%20The%20End&artist=Linkin%20Park
```

**Middleware → Backend Python**

- Método: `GET`
- URL: `{PY_BACKEND_URL}/api/lyrics` con los mismos query params.

**Validación en middleware**

- Si falta `title` o `artist`, el middleware puede responder directamente:

```json
{
  "success": false,
  "error": "MissingMetadata",
  "message": "Please provide title and artist"
}
```

**Respuesta al frontend**

- Reenvía tal cual el JSON del backend Python (ver contrato en `backend/API_DOCS.md`):
  - `success`, `source`, `sourceUrl`, `lyrics` (y campos de apoyo `pageTitle`, `pageArtist`)
  - o error `LyricsNotFound` con status `404`.

Uso típico desde frontend:

```ts
const params = new URLSearchParams({ title: song.title, artist: song.artist });
const res = await fetch(`${MIDDLEWARE_URL}/api/lyrics?${params.toString()}`);
const data = await res.json(); // data.lyrics
```

## 4. Endpoints de descarga: *streaming* de audio

### 4.1 `GET /api/download`

**Frontend → Middleware**

- Método: `GET`
- URL: `{MIDDLEWARE_URL}/api/download?videoId=<youtube_video_id>`

Ejemplo:

```text
GET {MIDDLEWARE_URL}/api/download?videoId=3gOHvDP_vCs
```

**Middleware → Backend Python**

- Método: `GET`
- URL: `{PY_BACKEND_URL}/api/download?videoId=<youtube_video_id>`

**Comportamiento del middleware**

1. Si falta `videoId`, responde directamente con JSON de error.
2. Llama al backend Python.
3. Si el backend devuelve error con `Content-Type: application/json`, el middleware:
   - Reenvía el JSON de error tal cual (mismo status code y `Content-Type`).
4. Si la respuesta es un stream de audio:
   - Copia `Content-Disposition` (nombre del archivo).
   - Copia `Content-Type` (tipo de audio).
   - Crea un `Readable` desde el cuerpo de la respuesta y hace `pipe` hacia el cliente.

**Uso típico desde frontend**

```ts
const url = `${MIDDLEWARE_URL}/api/download?videoId=${encodeURIComponent(videoId)}`;
const a = document.createElement("a");
a.href = url;
a.download = "";
document.body.appendChild(a);
a.click();
a.remove();
```

---

### 4.2 `GET /api/download-auto` (nuevo)

Este endpoint utiliza el nuevo endpoint del backend Python `/api/download-auto` y permite:
- Pasar metadatos de canción (`title`, `artist`, `album`).
- O pasar una query libre (`query`).
- Recibir directamente el stream de audio del mejor resultado encontrado.

**Frontend → Middleware**

- Método: `GET`
- URL: `{MIDDLEWARE_URL}/api/download-auto`
- Query params:
  - Opción 1 (recomendada – metadatos):
    - `title` (opcional pero recomendado)
    - `artist` (opcional)
    - `album` (opcional)
  - Opción 2 (query libre):
    - `query`

Ejemplos:

```text
GET {MIDDLEWARE_URL}/api/download-auto?title=SexyBack&artist=Justin%20Timberlake&album=FutureSex%2FLoveSounds
GET {MIDDLEWARE_URL}/api/download-auto?query=SexyBack%20Timbaland
```

**Middleware → Backend Python**

- Método: `GET`
- URL: `{PY_BACKEND_URL}/api/download-auto` con los mismos query params recibidos.

**Validación en middleware**

- Si **no** se proporciona `query`, `title`, `artist` ni `album`, responde:

```json
{
  "success": false,
  "error": "Missing query",
  "message": "Please provide a search query or song metadata (title, artist, album)"
}
```

**Manejo de respuesta**

1. Si el backend Python devuelve error JSON (`application/json` y `!ok`), el middleware reenvía:
   - Mismo status code.
   - Mismo `Content-Type`.
   - Cuerpo JSON tal cual.
2. Si la respuesta es un stream de audio:
   - Copia `Content-Disposition` (nombre del archivo sugerido).
   - Copia `Content-Type` (`audio/mpeg`, etc.).
   - Crea un `Readable` desde el cuerpo y hace `pipe` a la respuesta.

**Uso típico desde frontend**

```ts
const params = new URLSearchParams({
  title: song.title,
  artist: song.artist,
  album: song.album ?? ""
});

const url = `${MIDDLEWARE_URL}/api/download-auto?${params.toString()}`;
const a = document.createElement("a");
a.href = url;
a.download = "";
document.body.appendChild(a);
a.click();
a.remove();
```

También se puede usar el modo query libre:

```ts
const params = new URLSearchParams({
  query: "SexyBack Justin Timberlake official audio"
});

const url = `${MIDDLEWARE_URL}/api/download-auto?${params.toString()}`;
// Forzar descarga igual que arriba
```

---

## 5. Resumen de contratos Front ↔ Middle ↔ Back

- **Frontend ↔ Middleware**
  - Trabaja siempre con URLs base del tipo `{MIDDLEWARE_URL}/api/...`.
  - Sólo necesita conocer:
    - Parámetros de query soportados.
    - Formato JSON de respuestas (para los endpoints JSON).
    - Cómo disparar descargas para los endpoints de audio.

- **Middleware ↔ Backend Python**
  - El middleware es un proxy fino:
    - No transforma el JSON del backend, solo lo reenvía.
    - Valida parámetros básicos y devuelve errores legibles si falta algo.
    - Gestiona correctamente el streaming de audio y los headers.

Con este documento, el equipo de frontend puede entender:
- Qué endpoints consumir en el middleware.
- Qué parámetros enviar.
- Qué esperar de las respuestas.
- Cómo se conectan internamente con el backend Python ya documentado en `backend/API_DOCS.md`.

