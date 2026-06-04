# BeatMyBeat — Cambios implementados (hasta 2026-05-27)

Este documento resume los cambios realizados en la app (Android / Compose) a lo largo de las últimas iteraciones: mejoras UI/UX, descargas, estabilidad/memoria, escaneo de biblioteca, selección múltiple, shuffle/cola y rediseños de pantallas.

> Nota: Los cambios se describen por **funcionalidad** y se citan los **archivos principales** donde se implementaron.

---

## Descargas

### Indicador de progreso solo en pantalla de descargas + botón cancelar
- **Comportamiento**: el indicador de descarga activa se muestra **solo** en `AnalyzeScreen` (pantalla de descargas), no como banner global en el resto de la app.
- **Cancelación**: se añadió botón/acción para **cancelar** la descarga activa.
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/analyze/AnalyzeScreen.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/SongDownloadService.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/MainActivity.kt` (eliminación del banner global)

### Descargas en segundo plano más eficientes (notificaciones “throttle”)
- **Motivo**: reducir spam de `notify()` y carga del sistema.
- **Cambio**: throttle de actualizaciones de notificación/progreso durante descargas.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/SongDownloadService.kt`

### Rediseño “Playlist” → “URL” con preview (sin backend)
- **UI**: la pestaña de descargas cambia de “Playlist” a **“URL”** y mantiene la pestaña **“Song”**.
- **Entrada**: la sección “URL” acepta enlaces de:
  - YouTube playlist
  - YouTube Music playlist/álbum (si el enlace incluye `list=...` se trata como playlist/álbum)
  - Canción individual
- **Regla importante**: si una URL contiene `list=` (aunque sea una URL de canción con `v=`) se trata como **playlist/álbum**.
- **Preview**:
  - Para playlist/álbum: resuelve IDs y muestra lista (título, artista, thumbnail) antes de descargar.
  - Para canción: muestra preview de esa pista antes de descargar.
  - La metadata de cada vídeo se resuelve vía **oEmbed** (no hay middleware).
- **Descarga desde preview**:
  - Si es 1 pista: descarga directa.
  - Si son varias: descarga en batch (playlist/álbum).
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/analyze/AnalyzeScreen.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/YouTubeSearchClient.kt` (playlist info + extracción IDs)
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/YouTubeMetadata.kt` (oEmbed)
  - `composeApp/src/androidMain/res/values*/strings.xml` (renombres y strings nuevas)

### Descargar playlist/álbum y crear playlist automáticamente en la app
- **Requisito**: al descargar una playlist/álbum desde URL, se crea automáticamente la playlist en la app para no obligar al usuario a crearla manualmente.
- **Implementación**:
  - `enqueuePlaylistDownload(...)` acepta `playlistName`.
  - Al finalizar la descarga batch, se localizan los temas descargados (MediaStore por `DISPLAY_NAME`) y se escribe/actualiza `playlists_json` en las mismas prefs que usa el reproductor.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/SongDownloadService.kt`

---

## Estabilidad / Memoria (OOM y fugas)

### PlaybackService: reducción de recomposiciones y fugas
- **Problema**: tick/handler enviando estado muy frecuentemente + no detener correctamente en `onDestroy`/pausa.
- **Cambios**:
  - Se asegura detener el runnable en `onDestroy()` y cuando procede.
  - Se hace **throttle** del push de estado para reducir recomposiciones.
  - Se evita retener bitmaps grandes y se recicla artwork de notificación cuando cambia el tema.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/PlaybackService.kt`

### Caches de artwork por bytes + decodificación con sampling
- **Problema**: caches por “número de items” retenían bitmaps enormes → OOM.
- **Cambios**:
  - `ArtworkCache` y `RemoteArtworkCache` pasan a limitar por **bytes**.
  - Se introduce decodificación de bitmaps con **inSampleSize** y `RGB_565` para UI.
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/ArtworkCache.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/RemoteArtworkCache.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/BitmapDecoding.kt` (nuevo)

### Reutilización de OkHttpClient
- **Problema**: creación repetida de clientes HTTP.
- **Cambio**: singleton `OkHttpClient` para reutilizar conexiones.
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/AppHttpClient.kt` (nuevo)
  - Usos actualizados en pantallas/red.

---

## Biblioteca local (MediaStore) y duplicados

### Escaneo ampliado pero filtrando tonos del sistema
- **Mejora**: escaneo más robusto para detectar música “antigua” y múltiples ubicaciones/volúmenes.
- **Filtro**: exclusión explícita de ringtones/notificaciones/alarmas:
  - flags `IS_RINGTONE`, `IS_NOTIFICATION`, `IS_ALARM`
  - heurística por rutas típicas del sistema
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/data/MediaStoreScanner.kt`

### Bug: descargas duplicadas en biblioteca (misma canción 2 veces)
- **Causa**: el mismo fichero aparecía en más de una colección (Audio vs Files) / distintas URIs.
- **Fix**:
  - dedupe por combinación de URI + canonical path + clave de almacenamiento (carpeta+nombre).
  - preferencia por entradas con metadata más rica.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/data/MediaStoreScanner.kt`

### Escaneo de carpeta personalizada recursivo
- **Mejora**: al seleccionar una carpeta custom, ahora se escanean subcarpetas recursivamente y se amplían extensiones soportadas.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/storage/StorageSettings.kt`

---

## Reproductor (Player) — UX y bugs

### Selección múltiple mejorada (long-press para activar + click para toggle)
- **UX**:
  - long-press activa modo selección
  - click alterna selección
  - back sale del modo selección
  - salir de pantalla / cambiar filtros limpia la selección
- **Robustez**:
  - selección por `track.uri` en vez de `track.id` para evitar colisiones/duplicados de MediaStore
  - “suppressClickAfterLongPress” para evitar doble toggle tras long-press
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`

### Confirmación al borrar canciones del dispositivo
- **Cambio**: `AlertDialog` para confirmar borrado (1 o varias).
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`

### Bug: artwork persistente en canciones sin carátula
- **Causa**: no se reseteaba artwork al cambiar a una pista sin imagen.
- **Fix**:
  - reseteo explícito a `null` antes de cargar nuevo artwork
  - muestreo/decodificación eficiente
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/PlaybackService.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/PlaybackArtworkHelper.kt` (límite bytes)

### Bug: shuffle/cola incompleta (p. ej. 9/26)
- **Causa**: al iniciar reproducción en shuffle desde una pista intermedia, la cola enviada a ExoPlayer no incluía el pool completo.
- **Fix**:
  - en “Play All” shuffle: se pone el `startTrack` en índice 0 y se añaden el resto en orden barajado.
  - al pulsar una pista en shuffle: se rota el `shuffleOrder` para colocar la pista en cabeza y preservar el resto.
  - dedupe del pool por `uri`.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`

### “Topic” en artistas al descargar desde YouTube
- **Causa**: oEmbed/metadata de YouTube a veces devuelve `author_name` con sufijos como “- Topic”.
- **Fix**: normalización del artista para letras / display.
- **Archivos**:
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/YouTubeMetadata.kt`
  - `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/MiddlewareApi.kt` (limpieza aplicada en parseos donde aplica)

---

## Pantalla “Playlists” (sección Playlist del Player) — problema de espacio/scroll

### Master–Detail para playlists (evitar falta de espacio al tener muchas)
- **Problema**: con muchas playlists, la lista ocupa demasiado alto y deja poco espacio para scrollear canciones.
- **Nuevo comportamiento**:
  - **Modo lista**: se muestran **todas** las playlists (scrollable); no se muestra la lista de canciones.
  - **Modo detalle** (al tocar una playlist): la cabecera de playlist pasa arriba (compacta con back + menú) y debajo se muestra la lista de canciones ocupando el espacio completo.
  - Back hardware vuelve al modo lista.
  - Si se borra la playlist activa desde detalle, se sale de detalle.
- **Archivo**: `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`

---

## Strings / Recursos
- Se añadieron/actualizaron strings para:
  - cancelar descarga
  - selección múltiple y acciones bulk
  - confirmaciones de borrado
  - pestaña “URL” + preview de contenidos
- **Archivos**: `composeApp/src/androidMain/res/values*/strings.xml`

---

## Letras — LRCLIB (preparación para sincronización)

### Integración LRCLIB + fallback lyrics.ovh
- **Objetivo**: guardar letra plana y **LRC sincronizado** para implementar karaoke/scroll sincronizado más adelante.
- **Flujo**: caché local → LRCLIB (`/api/get-cached` → `/api/get` → `/api/search`) → lyrics.ovh.
- **Caché**: fichero JSON por pista (`plain`, `syncedLrc`, `source`, `lrclibId`); compatible con `.txt` legado.
- **Parser LRC**: `LrcParser` (parseo de líneas con timestamp y `lineAtPosition` para uso futuro con ExoPlayer).
- **Archivos nuevos**:
  - `ui/network/LrcLibApi.kt`
  - `ui/network/LyricsFetcher.kt`
  - `ui/network/LrcParser.kt`
- **Archivos actualizados**:
  - `ui/network/LyricsCache.kt`
  - `ui/network/MiddlewareApi.kt` (`LyricsResponse` con `syncedLrc`)
  - `ui/feature/player/PlayerScreen.kt`
  - `ui/network/AudioDownloader.kt`

### UI de letras sincronizadas (pre-karaoke)
- **Reproductor expandido**: si hay LRC en caché, muestra `SyncedLyricsView` en lugar de texto plano.
- **Comportamiento** (100 % local en reproducción):
  - Resalta la línea activa según `playbackPositionMs` / slider.
  - Scroll automático centrando la línea actual.
  - Tap en una línea → seek a ese instante (`onSeekToLyricsPosition`).
  - Etiqueta «Letra sincronizada» cuando hay LRC.
- Sin LRC → modo texto plano anterior (lyrics.ovh / solo `plain`).
- **Archivo nuevo**: `ui/feature/player/SyncedLyricsView.kt`

---

## Archivos clave tocados (lista rápida)
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/SongDownloadService.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/service/PlaybackService.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/analyze/AnalyzeScreen.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/player/PlayerScreen.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/data/MediaStoreScanner.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/storage/StorageSettings.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/YouTubeSearchClient.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/YouTubeMetadata.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/ArtworkCache.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/RemoteArtworkCache.kt`
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/BitmapDecoding.kt` (nuevo)
- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/network/AppHttpClient.kt` (nuevo)
- `composeApp/src/androidMain/res/values*/strings.xml`

