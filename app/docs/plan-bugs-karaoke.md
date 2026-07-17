# BeatMyBeat — Bugs, mejoras UX y Modo Karaoke

> Diagnóstico realizado sobre el código fuente actual (`androidMain`): reproducción (`PlaybackService`,
> `PlayerViewModel`), descargas (`AudioDownloader`, `SongDownloadService`) y letras sincronizadas
> (`LrcParser`, `SyncedLyricsView`). Complementa a [`mejoras.md`](mejoras.md) (que cubre pulido visual
> con librerías M3/Coil/Shimmer, ya implementado en Fases 1–3); este documento cubre **corrección de
> bugs de fondo**, **feedback de usuario** y el **diseño del Modo Karaoke**.

---

## Diagnóstico: bugs encontrados

| # | Bug | Archivo | Severidad | Estado |
|---|---|---|---|---|
| 1 | Errores de ExoPlayer no capturados | `service/PlaybackService.kt:109-137` | Alta | ✅ Fijado (Fase A) |
| 2 | Cola corrupta = Play silencioso | `service/PlaybackService.kt:219-220,417-435` | Alta | ✅ Fijado (Fase A) |
| 3 | Descarga por chunks sin validar rango HTTP | `ui/network/AudioDownloader.kt:130-160` | Media | Pendiente |
| 4 | Race condition en `syncLibrary` | `ui/feature/player/PlayerViewModel.kt:66-80` | Media | Pendiente |
| 5 | Race condition en `downloadJob` | `service/SongDownloadService.kt:249-263` | Media | Pendiente |

### Ronda 2 — update/instalación de APK y pipeline de letras

| # | Bug | Archivo | Severidad | Estado |
|---|---|---|---|---|
| 6 | Sin verificación de integridad/identidad del APK antes de instalar; `findApkDownloadUrl` caía al primer `.apk` del release si no había uno con el nombre esperado | `ui/network/ReleaseUpdateClient.kt:78-97`, `ui/feature/update/ApkUpdateInstaller.kt:82-89` | **Alta (seguridad)** | ✅ Fijado |
| 7 | Fallback a URI `file://` sin `FileProvider` declarado → `FileUriExposedException` en Android moderno, tragado en silencio | `ui/feature/update/ApkUpdateInstaller.kt:54-69` | **Alta (seguridad)** | ✅ Fijado |
| 8 | `VersionCompare.parseSegments` descarta segmentos no numéricos en vez de tratarlos como 0 → dirección de comparación incorrecta con tags raros | `core/VersionCompare.kt:12-16` | Media | Pendiente |
| 9 | `StorageSettings.saveToCustomTree` deja archivo truncado si falla la copia (sin cleanup, a diferencia de `saveToDefaultPublicFolder`) | `ui/storage/StorageSettings.kt:142-158` | Media | Pendiente |
| 10 | Descarga de actualización puede quedar atascada para siempre (pausada/cancelada desde Downloads del sistema no limpia el pending id ni desregistra el receiver) | `ui/feature/update/ApkUpdateInstaller.kt:34-50` | Baja | Pendiente |
| 11 | `Mp4TagWriter` inserta `udta/meta/ilst` como átomo top-level en vez de hijo de `moov` → tag "se escribe con éxito" pero la mayoría de reproductores no lo leen | `ui/network/Mp4TagWriter.kt:121-144` | Alta (rompe feature clave) | Pendiente |
| 12 | `parseTopLevelAtoms` no maneja tamaño de átomo 0/1 (EOF/extendido) → corta el escaneo antes de tiempo, inserta el tag después de `mdat` | `ui/network/Mp4TagWriter.kt:148-158` | Media | Pendiente |
| 13 | TOCTOU en deduplicación de peticiones de letras: `scope.async{}` arranca antes de comprobar `putIfAbsent`, cancelar el `Deferred` perdedor no interrumpe la llamada OkHttp ya en curso | `ui/network/LyricsFetchCoordinator.kt:33-47` | Media | Pendiente |
| 14 | Fallos de escritura en caché de letras silenciosos (`runCatching` sin log ni feedback) | `ui/network/LyricsCache.kt:75-89` | Baja | Pendiente |
| 15 | Lote de letras sin timeout agregado; una pista lenta bloquea el batch entero minutos | `ui/network/LrcLibApi.kt:62-81`, `service/LyricsBatchService.kt:71-132` | Baja | Pendiente |

### 1. Errores de reproducción no capturados

**Archivo:** `service/PlaybackService.kt:109-137`

El `Player.Listener` registrado en `onCreate()` no sobreescribe `onPlayerError`. Si ExoPlayer falla
(archivo movido/borrado, códec no soportado, contenedor corrupto), el error no se captura ni se
propaga a ningún sitio — cero referencias a `onPlayerError`/`PlaybackException` en todo el proyecto.

**Escenario de fallo:** el usuario borra o mueve un archivo descargado y pulsa Play sobre esa pista.
ExoPlayer pasa a estado de error silenciosamente; `pushState` sigue reportando `isPlaying`/posición
obsoletos. El usuario ve "no pasa nada", sin ningún mensaje.

**Fix propuesto:**

```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        Logger.e("PlaybackService", "Playback error", error)
        // emitir estado de error observable (StateFlow) para que la UI muestre Snackbar
    }
})
```

---

### 2. Cola corrupta deja Play como no-op total

**Archivo:** `service/PlaybackService.kt:219-220` (`loadQueue`), `417-435` (`parseQueue`)

`parseQueue` usa `runCatching { }.getOrNull() ?: return emptyList()` y `mapNotNull`; `loadQueue` hace
`if (items.isEmpty()) return` sin propagar ningún error al llamador ni a la UI.

**Escenario de fallo:** un `queueJson` corrupto o truncado (p. ej. restauración de preferencias
fallida tras una actualización de la app) hace que Play no haga absolutamente nada — sin toast, sin
log visible, sin cambio de estado.

**Fix propuesto:** loguear el fallo de parseo y, si la cola queda vacía tras un intento de carga con
datos previos no vacíos, emitir un estado de error explícito en vez de retornar en silencio.

---

### 3. Descarga por chunks no valida el rango HTTP devuelto

**Archivo:** `ui/network/AudioDownloader.kt:130-160`

El bucle de descarga por chunks acepta cualquier respuesta 2xx (`isSuccessful`, línea 142) sin
comprobar `code == 206` ni validar que el rango devuelto coincide con el solicitado.

**Escenario de fallo:** si el CDN o un proxy intermedio ignora el header `Range` y devuelve el archivo
completo con `200 OK` en cada petición, el bucle escribe el archivo entero repetidamente (el offset
avanza el tamaño completo de la respuesta) → archivo de salida duplicado/corrupto que falla al
reproducir o al escribir metadatos (`Mp4TagWriter`).

**Fix propuesto:** verificar `code == 206` y que el header `Content-Range` de la respuesta coincide con
el rango pedido; si no, reintentar sin rango (descarga completa) en vez de acumular datos incorrectos.

---

### 4. Condición de carrera en `syncLibrary`

**Archivo:** `ui/feature/player/PlayerViewModel.kt:66-80`

No se cancela el job anterior antes de lanzar un nuevo `syncLibrary`. `_tracks`, `_librarySyncing` y
las playlists son estado mutable compartido escrito desde corrutinas independientes.

**Escenario de fallo:** el auto-sync inicial todavía corre y el usuario hace pull-to-refresh; la
corrutina que termine última "gana", pudiendo sobrescribir un escaneo reciente con datos obsoletos.

**Fix propuesto:**

```kotlin
private var syncJob: Job? = null

fun syncLibrary() {
    syncJob?.cancel()
    syncJob = viewModelScope.launch { /* ... */ }
}
```

---

### 5. Condición de carrera en `downloadJob`

**Archivo:** `service/SongDownloadService.kt:249-263`

`downloadJob` es un `var` plano leído/escrito tanto por el hilo que lanza la descarga como desde dentro
de la corrutina (en `Dispatchers.IO`). La comprobación de identidad en el bloque `finally`
(`downloadJob === this.coroutineContext[Job]`) compite con la asignación externa `downloadJob = job`.

**Escenario de fallo:** un fallo muy rápido puede ejecutar el `finally` antes de que la asignación
externa sea visible, dejando `downloadJob` apuntando a un job obsoleto en vez de `null`, lo que
corrompe comprobaciones posteriores de cancelación/progreso activo.

**Fix propuesto:** usar `AtomicReference<Job?>` o `Mutex` para el acceso a `downloadJob`, o eliminar la
comparación de identidad y usar un `job.isActive` centralizado.

---

### 6-7. Instalación de actualizaciones sin verificar el APK (✅ Fijado)

**Archivos:** `ui/network/ReleaseUpdateClient.kt`, `ui/feature/update/ApkUpdateInstaller.kt`,
`AndroidManifest.xml`, `res/xml/file_paths.xml`

**Antes:**
- `findApkDownloadUrl` caía al primer asset `.apk` del release de GitHub si no encontraba uno llamado
  exactamente `BeatMyBeat.apk` — un asset inesperado (cuenta comprometida, CI con artefacto raro) se
  habría ofrecido como actualización sin ningún filtro.
- El instalador lanzaba `ACTION_VIEW` directo sobre la URI que diera `DownloadManager`, sin comprobar
  que el paquete del APK descargado fuera realmente `com.imontalvodev.beatmybeat`, y con un fallback a
  URI `file://` que en Android moderno lanza `FileUriExposedException` (tragada por `runCatching`, el
  usuario solo veía "no se pudo instalar" sin explicación ni salida real).

**Fix aplicado:**
1. `findApkDownloadUrl` ya **no tiene fallback**: solo acepta el asset llamado exactamente
   `BeatMyBeat.apk` (case-insensitive). Cualquier otro `.apk` en el release se ignora.
2. `ApkUpdateInstaller` copia el APK descargado (vía `DownloadManager.openDownloadedFile`, gestionado
   por el sistema, fuera del alcance de otras apps) a caché **privada** de la app, verifica con
   `PackageManager.getPackageArchiveInfo` que su `packageName` coincide con `context.packageName`, y
   solo entonces lo expone al instalador mediante un `FileProvider` (URI `content://`, con permisos
   de lectura acotados y revocables) — se añadió el `<provider>` correspondiente al Manifest y
   `res/xml/file_paths.xml`.
3. Si la copia falla o el `packageName` no coincide, se borra el archivo y se muestra el error
   existente (`update_install_failed`) — ya no hay instalación silenciosa de un APK no verificado ni
   crash por `file://` expuesto.

**Tests:** `ui/network/ReleaseUpdateClientTest.kt` (6 tests) cubre `findApkDownloadUrl`, incluyendo el
caso de regresión explícito ("asset `.apk` inesperado se ignora, sin fallback"). La verificación de
`packageName`/`FileProvider` en `ApkUpdateInstaller` depende de `PackageManager`/`DownloadManager`
reales — se valida manualmente en el emulador, no por unit test JVM.

---

## Mejoras UX detectadas

| Mejora | Justificación | Archivo(s) |
|---|---|---|
| Feedback visible de error de reproducción/descarga | Bugs 1-3 dejan al usuario sin ninguna señal cuando algo falla | `PlaybackService.kt`, `AudioDownloader.kt`, capa UI (Snackbar) |
| Completar o retirar funciones a medio implementar | `ProfileScreen.kt:129` (cambiar foto) y `PlayerLibraryUi.kt:295` (ocultar canción) son `TODO` pero potencialmente visibles/clicables | `ProfileScreen.kt`, `PlayerLibraryUi.kt` |
| Progreso granular de descarga por chunks | Ya existe la lógica de chunks; exponerlo mejora la percepción de velocidad | `AudioDownloader.kt`, `DownloadProgressBus.kt` |

---

## Plan de implementación por fases

### Fase A — Bugs críticos de reproducción y cola (prioridad alta) ✅ Completada

**Objetivo:** eliminar los fallos silenciosos que hacen parecer la app "rota" sin explicación.

1. Implementar `onPlayerError` en `PlaybackService` + `StateFlow<PlaybackError?>` observable.
2. Loguear y exponer error cuando `parseQueue`/`loadQueue` fallan.
3. Conectar ambos a un `Snackbar` global (ya existe el patrón en `MainActivity`, ver `mejoras.md` 1.6).

**Riesgo:** bajo. No toca lógica de negocio, solo añade rutas de error antes ausentes.

**Nota de implementación:** se usó `Toast` (no `Snackbar`) para respetar la convención real ya vigente
en `PlayerScreen`/`AnalyzeScreen` (`Snackbar` global queda reservado a flujos de `MainActivity`, ver
`mejoras.md`). Tests: `service/PlaybackErrorHelpersTest.kt`.

### Fase A2 — Seguridad de actualización/instalación de APK (prioridad alta) ✅ Completada

**Objetivo:** cerrar la superficie de ataque en el flujo de auto-actualización (bugs 6-7).

1. `ReleaseUpdateClient.findApkDownloadUrl` sin fallback a `.apk` arbitrario.
2. `ApkUpdateInstaller` copia a caché privada + verifica `packageName` + expone vía `FileProvider`.
3. `<provider>` FileProvider + `res/xml/file_paths.xml` añadidos al Manifest.

**Riesgo:** bajo. Cambios acotados a la ruta de instalación de updates, sin afectar reproducción/descarga
de música. Tests: `ui/network/ReleaseUpdateClientTest.kt`.

### Fase B — Concurrencia (prioridad media)

**Objetivo:** eliminar las dos condiciones de carrera detectadas antes de que se manifiesten en
producción con datos reales.

1. `PlayerViewModel.syncLibrary`: cancelar job previo (`Job` guardado en propiedad).
2. `SongDownloadService.downloadJob`: sustituir por `AtomicReference` o `Mutex`.
3. `AudioDownloader`: validar `code == 206` y `Content-Range` antes de aceptar un chunk.

**Riesgo:** bajo-medio. Cambios localizados, cubrir con test unitario donde ya existe suite (`androidUnitTest`).

### Fase C — UX de feedback y limpieza de TODOs (prioridad media)

1. Decidir: implementar "cambiar foto" en `ProfileScreen` u ocultar la opción de la UI.
2. Decidir: implementar "ocultar canción" en `PlayerLibraryUi` u ocultar el control.
3. Exponer progreso por chunk en la UI de descarga (`DownloadProgressOverlay.kt`).

**Riesgo:** bajo. Cambios de UI aislados, sin dependencias entre sí.

### Fase D — Modo Karaoke: base de letras (prioridad media, sin dependencias nuevas)

Reutiliza infraestructura ya existente: `LrcParser.kt` (parser LRC) y `SyncedLyricsView.kt` (scroll
centrado + resaltado por línea).

1. Extender `LrcLine` para soportar timestamps por palabra (formato LRC enriquecido
   `<mm:ss.xx>palabra`) cuando la fuente los provea (LRCLIB soporta "enhanced LRC" en parte de su catálogo).
2. Si no hay timestamps por palabra, interpolar el resaltado dentro de la línea por proporción de
   caracteres respecto a la duración de la línea (fallback determinista, sin red adicional).
3. Modificar `SyncedLyricsView` para pintar el resaltado intra-línea (no solo la línea completa).

**Riesgo:** bajo. Extiende modelos y composables existentes sin nuevas dependencias externas.

### Fase E — Modo Karaoke: control de tono y velocidad (prioridad media)

1. Exponer `PlaybackParameters(speed, pitch)` de ExoPlayer (soporte nativo, sin librería adicional) en
   `PlayerViewModel`/`PlayerScreen`.
2. Añadir control deslizante de tono en el overlay expandido del reproductor, activo solo en Modo Karaoke.

**Riesgo:** bajo. API nativa de Media3/ExoPlayer ya en uso.

### Fase F — Modo Karaoke: grabación (prioridad baja, mayor coste)

**Prerrequisito:** validar con usuarios las Fases D-E antes de invertir aquí — es la parte más costosa.

1. Añadir permiso `RECORD_AUDIO` al `AndroidManifest.xml` (actualmente ausente).
2. Capturar voz con `MediaRecorder` en paralelo a la reproducción de la pista.
3. Guardar grabación (voz sola o mezclada con la pista) en el almacenamiento de la app.
4. UI de reproducción/descarte de la grabación tras finalizar la sesión de karaoke.

**Riesgo:** medio-alto. Nueva superficie de permisos, gestión de recursos de audio concurrente
(reproducción + grabación), y necesidad de mezcla de audio si se decide combinar voz + pista.

### Fase G — Corrección de tags MP4 y robustez de letras (prioridad media-alta)

**Objetivo:** arreglar bugs 8-15 (ronda 2). Prioridad alta dentro de la fase para 11-12: el escritor de
tags MP4 reporta éxito pero el resultado no es válido, lo cual es más grave que un simple fallo visible.

1. `Mp4TagWriter`: mover el bloque `udta/meta/ilst` a hijo de `moov` (no top-level) y hacer que
   `parseTopLevelAtoms` maneje tamaño `0` (hasta EOF) y `1` (tamaño extendido de 64 bits) en vez de
   abortar el escaneo.
2. `VersionCompare.parseSegments`: mapear segmentos no numéricos a `0` en vez de descartarlos con
   `mapNotNull`.
3. `StorageSettings.saveToCustomTree`: envolver la copia en try/finally que borre el documento
   destino si falla, igual que `saveToDefaultPublicFolder`.
4. `ApkUpdateInstaller.handleDownloadFinished`: manejar también estados no terminales/cancelados de
   `DownloadManager` (limpiar pending id + receiver, mostrar error) en vez de solo `STATUS_SUCCESSFUL`/`STATUS_FAILED`.
5. `LyricsFetchCoordinator`: evitar que dos corrutinas concurrentes por la misma pista lancen ambas la
   llamada de red (comprobar `putIfAbsent` antes de lanzar el `async`, no después).
6. `LyricsCache.putEntry`: loguear (`Logger.e`) el fallo de escritura en vez de tragarlo en silencio.
7. `LrcLibApi`/`LyricsBatchService`: cap de tiempo por pista en el batch, o cancelación entre sub-peticiones.

**Riesgo:** medio. 11-12 tocan un parser binario propio (mayor riesgo de regresión, requiere probar
con archivos reales tageados antes/después); el resto son cambios localizados y de bajo riesgo.

---

## Resumen priorizado

| Fase | Contenido | Complejidad | Depende de | Estado |
|---|---|---|---|---|
| A | Errores de reproducción/cola visibles | Baja | — | ✅ Completada |
| A2 | Seguridad update/instalación de APK | Baja-Media | — | ✅ Completada |
| B | Condiciones de carrera (library sync, download job, chunks HTTP) | Baja-Media | — | Pendiente |
| C | Feedback UX + TODOs pendientes | Baja | Fase A (reutiliza Snackbar) | Pendiente |
| D | Karaoke: resaltado por palabra | Baja | — | Pendiente |
| E | Karaoke: tono/velocidad | Baja | Fase D (mismo overlay) | Pendiente |
| F | Karaoke: grabación | Media-Alta | Validación de Fases D-E | Pendiente |
| G | Tags MP4 + robustez letras/update | Media | — | Pendiente |

---

*Documento actualizado — Julio 2026*
