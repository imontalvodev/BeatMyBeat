# BeatMyBeat — Bugs, mejoras UX y Modo Karaoke

> Diagnóstico realizado sobre el código fuente actual (`androidMain`): reproducción (`PlaybackService`,
> `PlayerViewModel`), descargas (`AudioDownloader`, `SongDownloadService`) y letras sincronizadas
> (`LrcParser`, `SyncedLyricsView`). Complementa a `[mejoras.md](mejoras.md)` (que cubre pulido visual
> con librerías M3/Coil/Shimmer, ya implementado en Fases 1–3); este documento cubre **corrección de
> bugs de fondo**, **feedback de usuario** y el **diseño del Modo Karaoke**.

---

## Diagnóstico: bugs encontrados


| #   | Bug                                        | Archivo                                      | Severidad | Estado            |
| --- | ------------------------------------------ | -------------------------------------------- | --------- | ----------------- |
| 1   | Errores de ExoPlayer no capturados         | `service/PlaybackService.kt:109-137`         | Alta      | ✅ Fijado (Fase A) |
| 2   | Cola corrupta = Play silencioso            | `service/PlaybackService.kt:219-220,417-435` | Alta      | ✅ Fijado (Fase A) |
| 3   | Descarga por chunks sin validar rango HTTP | `ui/network/AudioDownloader.kt:130-160`      | Media     | ✅ Fijado (Fase B) |
| 4   | Race condition en `syncLibrary`            | `ui/feature/player/PlayerViewModel.kt:66-80` | Media     | ✅ Fijado (Fase B) |
| 5   | Race condition en `downloadJob`            | `service/SongDownloadService.kt:249-263`     | Media     | ✅ Fijado (Fase B) |


### Ronda 2 — update/instalación de APK y pipeline de letras


| #   | Bug                                                                                                                                                                                    | Archivo                                                                                    | Severidad                  | Estado            |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | -------------------------- | ----------------- |
| 6   | Sin verificación de integridad/identidad del APK antes de instalar; `findApkDownloadUrl` caía al primer `.apk` del release si no había uno con el nombre esperado                      | `ui/network/ReleaseUpdateClient.kt:78-97`, `ui/feature/update/ApkUpdateInstaller.kt:82-89` | **Alta (seguridad)**       | ✅ Fijado          |
| 7   | Fallback a URI `file://` sin `FileProvider` declarado → `FileUriExposedException` en Android moderno, tragado en silencio                                                              | `ui/feature/update/ApkUpdateInstaller.kt:54-69`                                            | **Alta (seguridad)**       | ✅ Fijado          |
| 8   | `VersionCompare.parseSegments` descarta segmentos no numéricos en vez de tratarlos como 0 → dirección de comparación incorrecta con tags raros                                         | `core/VersionCompare.kt:12-16`                                                             | Media                      | ✅ Fijado (Fase G) |
| 9   | `StorageSettings.saveToCustomTree` deja archivo truncado si falla la copia (sin cleanup, a diferencia de `saveToDefaultPublicFolder`)                                                  | `ui/storage/StorageSettings.kt:142-158`                                                    | Media                      | ✅ Fijado (Fase G) |
| 10  | Descarga de actualización puede quedar atascada para siempre (pausada/cancelada desde Downloads del sistema no limpia el pending id ni desregistra el receiver)                        | `ui/feature/update/ApkUpdateInstaller.kt:34-50`                                            | Baja                       | ✅ Fijado (Fase G) |
| 11  | `Mp4TagWriter` inserta `udta/meta/ilst` como átomo top-level en vez de hijo de `moov` → tag "se escribe con éxito" pero la mayoría de reproductores no lo leen                         | `ui/network/Mp4TagWriter.kt:121-144`                                                       | Alta (rompe feature clave) | ✅ Fijado (Fase G) |
| 12  | `parseTopLevelAtoms` no maneja tamaño de átomo 0/1 (EOF/extendido) → corta el escaneo antes de tiempo, inserta el tag después de `mdat`                                                | `ui/network/Mp4TagWriter.kt:148-158`                                                       | Media                      | ✅ Fijado (Fase G) |
| 13  | TOCTOU en deduplicación de peticiones de letras: `scope.async{}` arranca antes de comprobar `putIfAbsent`, cancelar el `Deferred` perdedor no interrumpe la llamada OkHttp ya en curso | `ui/network/LyricsFetchCoordinator.kt:33-47`                                               | Media                      | ✅ Fijado (Fase G) |
| 14  | Fallos de escritura en caché de letras silenciosos (`runCatching` sin log ni feedback)                                                                                                 | `ui/network/LyricsCache.kt:75-89`                                                          | Baja                       | ✅ Fijado (Fase G) |
| 15  | Lote de letras sin timeout agregado; una pista lenta bloquea el batch entero minutos                                                                                                   | `ui/network/LrcLibApi.kt:62-81`, `service/LyricsBatchService.kt:71-132`                    | Baja                       | ✅ Fijado (Fase G) |


### Ronda 3 — la biblioteca se quedaba sin letra sincronizada

| #  | Bug                                                                                                       | Archivo                                        | Severidad                  | Estado   |
| -- | --------------------------------------------------------------------------------------------------------- | ---------------------------------------------- | -------------------------- | -------- |
| 16 | Un timeout de LRCLIB se mostraba como "no hay letra para esta canción"                                     | `ui/feature/player/PlayerScreen.kt`            | Media (engaña al usuario)  | ✅ Fijado |
| 17 | Tras un timeout se caía a lyrics.ovh y el texto plano se cacheaba, dejando la pista sin karaoke **para siempre** | `ui/network/LyricsFetcher.kt`, `LyricsCache.kt` | **Alta (pérdida silenciosa de una feature)** | ✅ Fijado |
| 18 | Se agotaba el presupuesto de 20s lanzando peticiones sabiendo que la red no respondía                     | `ui/network/LrcLibApi.kt`                      | Baja (espera innecesaria)  | ✅ Fijado |

**Cómo se manifestaba (bug 17, el grave):** al descargar canciones en lote, LRCLIB responde lento o
corta (2 peticiones concurrentes, timeout de lectura de 10s). `LyricsFetcher` trataba *cualquier* fallo
de LRCLIB como "no la tiene" y caía a lyrics.ovh, que **solo devuelve texto plano, sin marcas de
tiempo**. Ese texto se guardaba en caché, y `getEntry(...).hasAnyLyrics()` respondía a partir de
entonces: LRCLIB no se volvía a consultar **nunca**. Resultado: pistas que sí tienen LRC sincronizado en
LRCLIB se quedaban con letra plana permanentemente, y con ellas el Modo Karaoke — que exige LRC — dejaba
de estar disponible sin que nada lo indicara. Una caída pasajera de red degradaba la biblioteca de forma
irreversible (salvo "refrescar letra" a mano, pista por pista).

**Fix aplicado:**

1. `LrcLibApi.executeGetRaw` distingue "red no alcanzable" (`SocketTimeoutException`,
   `InterruptedIOException`, `UnknownHostException`, `ConnectException`) de un fallo cualquiera, y lanza
   `LyricsNetworkUnreachable`. `fetchLyrics` la captura y **aborta la búsqueda entera** en vez de seguir
   probando combinaciones que van a fallar igual (el doble bucle se extrajo a `searchAllCombinations`
   para poder envolverlo; el flujo interno no cambia). Deja de gastar los 20s de presupuesto.
2. `LyricsFetcher` **ya no cae a lyrics.ovh cuando el fallo es de red** — solo cuando LRCLIB ha
   respondido de verdad que no tiene la letra. Ante un fallo pasajero es mejor no tener letra ahora que
   tener la mala para siempre.
3. `LyricsCacheEntry.lrclibChecked` (nuevo, persistido en el JSON): marca si LRCLIB llegó a contestar.
   Una entrada de texto plano guardada **sin** haber podido preguntarle ya no bloquea el reintento. Las
   entradas antiguas no llevan la marca, así que se reintenta LRCLIB una vez por pista y a partir de ahí
   queda resuelto — la biblioteca ya degradada se recupera sola.
4. La UI distingue los dos casos: nueva cadena `player_lyrics_network_error` ("No se pudo conectar…") en
   las 6 locales, frente a `player_lyrics_unavailable`. El botón de reintentar que ya existía ahora
   tiene sentido cuando aparece.

**Tests:** `ui/network/LyricsFailureKindTest.kt` (3) sobre la clasificación del error y
`ui/network/LyricsCacheEntryTest.kt` (5) sobre la condición de servir-desde-caché, incluida la regresión
explícita del bug 17 y el caso de las entradas antiguas sin marca.

---

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

### 3. Descarga por chunks no valida el rango HTTP devuelto (✅ Fijado — Fase B)

**Archivo:** `ui/network/AudioDownloader.kt:130-160`

El bucle de descarga por chunks aceptaba cualquier respuesta 2xx (`isSuccessful`) sin comprobar
`code == 206` ni validar que el rango devuelto coincide con el solicitado.

**Escenario de fallo:** si el CDN o un proxy intermedio ignora el header `Range` y devuelve el archivo
completo con `200 OK` en cada petición, el bucle escribía el archivo entero repetidamente (el offset
avanza el tamaño completo de la respuesta) → archivo de salida duplicado/corrupto que falla al
reproducir o al escribir metadatos (`Mp4TagWriter`).

**Fix aplicado:** cada chunk debe venir con `code == 206`, salvo la primera iteración (`offset == 0`),
donde un `200` significa "el servidor no soporta rangos": esa única respuesta se trata como el archivo
completo y se detiene ahí (sin más iteraciones). Un `200` en cualquier iteración **posterior**
(offset > 0) aborta la descarga entera (`totalWritten = 0`) en vez de seguir acumulando datos
duplicados — cae en la ruta de error `ZeroBytes` ya existente.

---

### 4. Condición de carrera en `syncLibrary` (✅ Fijado — Fase B)

**Archivo:** `ui/feature/player/PlayerViewModel.kt:66-80`

No se cancelaba el job anterior antes de lanzar un nuevo `syncLibrary`. `_tracks`, `_librarySyncing` y
las playlists son estado mutable compartido escrito desde corrutinas independientes.

**Escenario de fallo:** el auto-sync inicial todavía corre y el usuario hace pull-to-refresh; la
corrutina que termine última "gana", pudiendo sobrescribir un escaneo reciente con datos obsoletos.

**Fix aplicado:**

```kotlin
private var syncJob: Job? = null

fun syncLibrary(auto: Boolean) {
    syncJob?.cancel()
    syncJob = viewModelScope.launch { /* ... */ }
}
```

---

### 5. Condición de carrera en `downloadJob` (✅ Fijado — Fase B)

**Archivo:** `service/SongDownloadService.kt:249-263`

`downloadJob` es un `var` plano leído/escrito tanto por el hilo que lanza la descarga como desde dentro
de la corrutina (en `Dispatchers.IO`). La comprobación de identidad en el bloque `finally`
(`downloadJob === this.coroutineContext[Job]`) competía con la asignación externa `downloadJob = job`.

**Escenario de fallo:** un fallo muy rápido podía ejecutar el `finally` antes de que la asignación
externa fuera visible, dejando `downloadJob` apuntando a un job obsoleto en vez de `null`, lo que
corrompía comprobaciones posteriores de cancelación/progreso activo.

**Fix aplicado:** misma técnica que en el bug 13 (`LyricsFetchCoordinator`) — `scope.launch(start = CoroutineStart.LAZY)`, se asigna `downloadJob = job` **antes** de llamar a `job.start()`, así el cuerpo
(y su `finally`) nunca puede ejecutarse antes de que la asignación sea visible. Se añadió además
`@Volatile` al campo para cubrir otras lecturas/escrituras cruzadas entre hilos (p. ej.
`cancelActiveWork` desde el hilo principal).

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


| Mejora                                             | Justificación                                                                                                                        | Archivo(s)                                                     |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------- |
| Feedback visible de error de reproducción/descarga | Bugs 1-3 dejan al usuario sin ninguna señal cuando algo falla                                                                        | `PlaybackService.kt`, `AudioDownloader.kt`, capa UI (Snackbar) |
| Completar o retirar funciones a medio implementar  | `ProfileScreen.kt:129` (cambiar foto) y `PlayerLibraryUi.kt:295` (ocultar canción) son `TODO` pero potencialmente visibles/clicables | `ProfileScreen.kt`, `PlayerLibraryUi.kt`                       |
| Progreso granular de descarga por chunks           | Ya existe la lógica de chunks; exponerlo mejora la percepción de velocidad                                                           | `AudioDownloader.kt`, `DownloadProgressBus.kt`                 |


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

### Fase B — Concurrencia (prioridad media) ✅ Completada

**Objetivo:** eliminar las dos condiciones de carrera detectadas antes de que se manifiesten en
producción con datos reales.

1. `PlayerViewModel.syncLibrary`: cancelar job previo (`Job` guardado en propiedad).
2. `SongDownloadService.downloadJob`: `CoroutineStart.LAZY` + `@Volatile` (ver detalle bug 5).
3. `AudioDownloader`: validar `code == 206` antes de aceptar un chunk; `200` solo se acepta como
  descarga completa en la primera iteración.

**Riesgo:** bajo-medio. Cambios localizados. Sin tests JVM: los tres tocan `Context`/`Service`/red real
(`PlayerViewModel` necesita `Application`, `SongDownloadService` es un `Service`, `AudioDownloader` hace
peticiones HTTP reales) — no hay Robolectric ni MockWebServer en el proyecto para simularlos en
`androidUnitTest`. Verificado manualmente en emulador + `assembleDebug`/`testDebugUnitTest` en verde.

### Fase C — UX de feedback y limpieza de TODOs (prioridad media) ✅ Completada

1. **"Cambiar foto" en `ProfileScreen`:** decisión del usuario → eliminada. Se quitó el `clickable`
  TODO del logo, el texto `profile_change_photo` y su string en las 6 locales. El espaciado
   (`ProfileLayout.logoToPhotoSpacing`, ahora sin uso) se absorbió en `headerToListSpacing`.
2. **"Ocultar canción" en `PlayerLibraryUi`:** decisión del usuario → eliminada del menú de 3 puntos
  (`TrackOverflowMenu`: parámetro `onHide`, `DropdownMenuItem` y string `player_action_hide` en las
   6 locales). Ya existe "Eliminar del teléfono" para quitar una canción.
3. **Progreso por chunk en la UI de descarga:** ya resuelto sin querer al arreglar el bug 3 en Fase B
  — `AudioDownloader` ya reporta `fraction` por chunk y `AnalyzeScreen`/`ActiveDownloadProgressSection`
   ya lo pintan. No hizo falta ningún cambio.

**Riesgo:** bajo. Cambios de UI aislados, sin dependencias entre sí. `assembleDebug` y
`testDebugUnitTest` (58/59, solo el fallo preexistente) en verde.

### Fase D — Modo Karaoke: base de letras (prioridad media, sin dependencias nuevas) 

Reutiliza infraestructura ya existente: `LrcParser.kt` (parser LRC) y `SyncedLyricsView.kt` (scroll
centrado + resaltado por línea).

1. `LrcLine` ahora tiene `words: List<LrcWord>` (vacío si la fuente no trae timestamps por palabra).
  `LrcParser.parse` detecta marcas `<mm:ss.xx>palabra` (LRC "enhanced", formato que LRCLIB soporta en
   parte de su catálogo) dentro de una línea y las extrae; el texto plano de la línea (`LrcLine.text`)
   sigue siendo el mismo de siempre (unión de las palabras, sin marcas). `toPlainText` también limpia
   las marcas `<...>` además de las `[...]` ya soportadas.
> ⚠️ **Deriva doc/código detectada en Fase E:** el punto 2 describe una firma
> `karaokeHighlightLength(line, lineEndMs, positionMs)` con fallback por interpolación de caracteres.
> El código real es `karaokeHighlightLength(line, positionMs)` y **solo** resalta por palabra cuando la
> fuente trae timestamps reales (ver comentario en `SyncedLyricsView.kt`). Como buena parte del catálogo
> de LRCLIB no es LRC "enhanced", el Modo Karaoke degrada a resaltado por línea completa en muchas
> pistas — de ahí la etiqueta "Modo karaoke · por línea" añadida en Fase E.

2. Nuevo `LrcParser.karaokeHighlightLength(line, lineEndMs, positionMs)`: si la línea trae `words`,
  el resaltado avanza por palabra (busca la última palabra cuyo `startMs <= positionMs`); si no,
   interpola linealmente por proporción de caracteres entre `line.startMs` y `lineEndMs` (fallback
   determinista, sin red adicional). `lineEndMs` es el `startMs` de la línea siguiente o, si es la
   última línea del LRC, un estimado (`LrcParser.estimatedLineEndMs`: ~150ms/carácter con un suelo de
   2000ms) para que el resaltado de la última línea no se quede congelado en 0%.
2b. **Estado de intro** (añadido durante el lavado de UI): antes del primer timestamp
  `lineAtPosition` devuelve -1 y ninguna línea se destacaba — con intros de 15–30s la letra se veía
   como un bloque plano, y al buscar hacia atrás hasta la intro se quedaba clavada donde estuviera.
   Nuevo `LrcParser.focusLineAtPosition`: durante la intro enfoca la línea 0, que se pinta al tamaño
   de la activa pero atenuada y en `SemiBold` en vez de `Bold` — se lee ya la primera frase y la
   siguiente, sin fingir que alguien está cantando. Tests en `LrcParserTest` (3 casos).

3. `SyncedLyricsView` pinta la línea activa con un `AnnotatedString` de dos tramos de color (cantado en
  `primary`, pendiente en `primary` atenuado) en vez de un color sólido; las líneas pasadas/futuras
   mantienen el resaltado por línea completa de antes.

**Riesgo:** bajo. Extiende modelos y composables existentes sin nuevas dependencias externas.

**Tests:** `ui/network/LrcParserTest.kt` (9 tests) — parseo LRC plano sin cambios de comportamiento,
extracción de timestamps por palabra (incluyendo texto inicial sin marca propia), `toPlainText` con
marcas de palabra, y todos los casos límite de `karaokeHighlightLength`/`estimatedLineEndMs` (antes del
inicio, en/después del fin, interpolación por caracteres, límites por palabra). `SyncedLyricsView` es
Compose puro sin Robolectric en el proyecto — verificación visual pendiente en emulador (letras
"enhanced" de LRCLIB si el beta tester encuentra una pista con esa cobertura; si no, se ve el fallback
por interpolación de caracteres con cualquier letra sincronizada normal).

### Fase E — Modo Karaoke: conmutador de modo, tono y velocidad (prioridad media) ✅ Completada

**Conmutador de modo** (previo a tono/velocidad: sin un Modo Karaoke explícito no hay dónde colgar los
controles). Referencias analizadas: Rhythm, Metrolist, OuterTune — ninguna trata el karaoke como pantalla
aparte, sino como estado de presentación del reproductor.

1. `PlayerViewModel.karaokeMode: StateFlow<Boolean>` — vive en la sesión (sobrevive a plegar el overlay,
  cambio de canción y rotación), **no** se guarda en `prefs`: al reabrir la app se vuelve al modo escucha.
2. Botón de micrófono en la fila superior de `ExpandedPlayerOverlay`, visible solo si hay letra
  sincronizada (`AnimatedVisibility`), tintado con `primary` cuando está activo — misma convención que
   shuffle/repeat.
3. Auto-apagado: `LaunchedEffect(hasSyncedLyrics, karaokeMode)` desactiva el modo si la pista no tiene
  LRC. El render usa `karaokeActive = karaokeMode && hasSyncedLyrics` para que no haya ni un frame con
   modo activo y letra vacía.
4. Layout: la carátula colapsa con peso animado `1f → 0.001f` + alpha (Compose exige peso > 0, de ahí el
  valor mínimo en vez de un `if`); la letra sube a 28sp activa / 20sp resto vía los nuevos parámetros
   `activeFontSize`/`inactiveFontSize` de `SyncedLyricsView` (defaults 18/15sp: modo escucha sin cambios).
   `lineHeight` pasa a derivarse del tamaño (`fontSize * 1.35f`) — con 22sp fijo el texto grande se solapaba.
5. Etiqueta de modo con 3 estados: "Letra sincronizada" / "Modo karaoke" / "Modo karaoke · por línea",
  este último cuando el LRC no trae timestamps por palabra, para que el resaltado por línea no se lea
   como un fallo de sincronía.

**Tono y velocidad:**

6. `PlaybackService.setPlaybackTuning(speed, pitch)` aplica `PlaybackParameters` (soporte nativo de
  ExoPlayer, sin librería adicional). Rangos en el companion: velocidad 0.5–1.5x, tono ±6 semitonos.
7. `KaraokeTuning` (nuevo) convierte semitonos ↔ ratio de tono (`2^(n/12)`): el slider habla en semitonos
  porque es la unidad de quien canta; `PlaybackParameters.pitch` es un multiplicador de frecuencia.
   `pitch` va desacoplado de `speed`, así que transportar la canción no la acelera.
8. Estado en `PlayerViewModel` (`karaokePitchSemitones`, `karaokeSpeed`), aplicado por un
  `LaunchedEffect(boundService, karaokeMode, ...)` que **fuerza valores neutros fuera del Modo Karaoke**:
   el ajuste se conserva para cuando el usuario vuelva a entrar, pero nunca tiñe la escucha normal. La
   dependencia de `boundService` reaplica el ajuste si el servicio muere y se vuelve a bindar.
9. UI: fila compacta plegable dentro de la tarjeta de controles, visible solo con `karaokeActive`. Muestra
  los valores actuales sin desplegar y ofrece "Restablecer" solo cuando el ajuste no es neutro. El slider
   de tono usa `steps = 11` (13 posiciones, -6..+6) para que el tono caiga siempre en un semitono exacto.

**Riesgo:** bajo. API nativa de Media3/ExoPlayer ya en uso; el resto son cambios de UI aislados.

**Tests:** `ui/feature/player/KaraokeTuningTest.kt` (8 tests) — conversión semitonos→ratio, reciprocidad
de subir/bajar el mismo intervalo, recorte al rango de `PlaybackService` y formato de etiquetas. El
conmutador y el layout son Compose puro sin Robolectric en el proyecto, así que van por verificación
manual: **tono verificado en dispositivo** (transporta sin acelerar) y **conmutador + layout de karaoke
verificados en emulador** junto con el rediseño de la Fase U1 (ver
`[plan-lavado-ui.md](plan-lavado-ui.md)`).

**Strings:** 9 nuevas en las 6 locales (`values`, `-es`, `-en`, `-de`, `-pt`, `-hr`).

### Fase F — Modo Karaoke: grabación ✅ Completada (núcleo)

**Nota de referencia:** [Rhythm](https://github.com/cromaguy/Rhythm), la referencia de diseño del
resto del proyecto, **no graba nada** — es solo reproductor. Aquí no había a quién copiar.

**Restricción de plataforma:** Android no ofrece ninguna API que capture "micro + lo que suena" en un
archivo. `MediaRecorder` graba del micro y ya. Mezclar exige decodificar ambas pistas a PCM, sumarlas
y recodificar con MediaCodec/MediaMuxer. Eso condiciona todo el diseño de abajo.

**Política de espacio (decidida con el usuario):**

| Formato | Por minuto | Toma de 3:30 |
| ------- | ---------- | ------------ |
| AAC 64 kbps mono (elegido) | 0,47 MB | **~1,6 MB** |
| AAC 128 kbps estéreo | 0,94 MB | ~3,3 MB |
| Mezcla exportada | 0,94 MB | ~3,3 MB extra |

Una canción descargada ocupa 3,5–7 MB, así que **una toma cuesta menos de la mitad que una canción**.
Mono es lo correcto para voz: un micro, un cantante.

**La palanca de espacio no es el bitrate, es no guardar por defecto.** La mayoría de tomas se
descartan al oírlas. Preguntar "guardar o descartar" al parar elimina el grueso del problema sin
comprimir nada.

**Implementado:**

1. `RECORD_AUDIO` en el Manifest. **No** se añadió servicio en primer plano de tipo `microphone`: se
   graba solo con el reproductor en pantalla, lo que evita el permiso `FOREGROUND_SERVICE_MICROPHONE`
   y su superficie asociada. Al salir de la pantalla se cancela la toma (`DisposableEffect`), para no
   dejar el micro tomado ni un archivo a medias.
2. `KaraokeRecorder`: `MediaRecorder` a AAC mono 64 kbps. Usa `AudioSource.MIC` y **no**
   `VOICE_COMMUNICATION` — este último aplica el cancelador de eco del sistema, pensado para llamadas
   (mono de banda reducida, AGC agresivo), y para cantar suena mal. El eco se evita pidiendo
   auriculares, no degradando la voz.
3. `KaraokeRecordings`: almacén en `getExternalFilesDir` (privado, se limpia al desinstalar), nombres
   `track<id>_<instante>.m4a` para listar por canción, y contabilidad de tamaño.
4. **Revisión sin mezclar archivos:** al parar, la canción vuelve a `trackOffsetMs` (la posición en
   que arrancó la toma) y un `MediaPlayer` reproduce la voz encima. Dos reproductores arrancados a la
   vez, coste cero. Oírse a capela no permite juzgar nada.
5. Estado `Idle` / `Recording` / `Review` en `PlayerViewModel`. La toma en revisión **existe en disco
   pero no está guardada**: descartar la borra.
6. **Auriculares recomendados, nunca obligatorios.** Se puede grabar con o sin ellos. El aviso va
   *inline* bajo el botón de grabar, no en un diálogo modal: un diálogo cada vez que quieres cantar
   es una barrera, no una recomendación. El estado se sigue con `AudioDeviceCallback`
   (`rememberHeadphonesConnected`) en vez de sondear, así que al enchufar los auriculares el aviso
   desaparece solo.
7. En Perfil: espacio ocupado por las grabaciones y borrado con confirmación. Sin esto, la única
   forma de recuperar el espacio sería desinstalar.

**Tests:** `ui/feature/player/KaraokeRecordingsTest.kt` (5) — codificación y lectura del nombre,
archivos ajenos ignorados, presupuesto de tamaño por toma (falla si alguien sube el bitrate sin
querer) y formato legible respetando la locale.

**Fuera de esta tanda, a propósito:** la **exportación con mezcla** (MediaCodec + MediaMuxer). Es el
trozo caro y la política dice que la mezcla solo se genera al exportar, así que no bloquea el uso
normal. Pendiente también decidir si al exportar se usa `StorageSettings` para dejarla en la carpeta
pública del usuario.

**Riesgo:** medio. **Sin verificar en dispositivo:** grabar exige micro real y auriculares; el
emulador de esta máquina además no admite instalar (`/data` al 92%).

### Fase G — Corrección de tags MP4 y robustez de letras (prioridad media-alta) ✅ Completada

**Objetivo:** arreglar bugs 8-15 (ronda 2). Prioridad alta dentro de la fase para 11-12: el escritor de
tags MP4 reportaba éxito pero el resultado no era válido, lo cual es más grave que un simple fallo visible.

1. `**Mp4TagWriter**` (11+12, acoplados): `replaceOrInsertUdta` ahora localiza `moov`, busca/reemplaza
  `udta` **entre sus hijos** (no a nivel raíz) y, si no existe, lo inserta como último hijo de `moov`
   parcheando los 4 bytes de tamaño de `moov` para reflejar el nuevo contenido. El parser de átomos
   (`parseAtoms`, generalizado desde `parseTopLevelAtoms` para poder acotarse a un rango, usado tanto a
   nivel raíz como dentro de `moov`) ahora maneja tamaño `0` (átomo hasta el final del rango que lo
   contiene) y tamaño `1` (tamaño real de 64 bits en los 8 bytes siguientes al header), en vez de cortar
   el escaneo. Si el archivo no tiene `moov` (corrupto/no-MP4), `write()` lanza excepción explícita en
   vez de escribir un tag no válido — el llamador (`AudioDownloader`) ya lo trata como *best-effort* con
   `runCatching` + fallback a `meta.json`.
2. `**VersionCompare.parseSegments**`: solo se recorta el sufijo no numérico **final** (`-beta`, `-rc1`,
  como antes); un segmento no numérico intercalado (`1.a.2`) ya no desaparece, cuenta como `0` en su
   posición para no desalinear los segmentos siguientes.
3. `**StorageSettings.saveToCustomTree**`: copia envuelta en try/finally que borra el `DocumentFile`
  destino si la copia no se completó, igual que ya hacía `saveToDefaultPublicFolder`.
4. `**ApkUpdateInstaller.handleDownloadFinished**`: nuevo caso `STATUS_PAUSED` — si `COLUMN_REASON` es
  `PAUSED_UNKNOWN` (sin reintento automático esperable) limpia pending id + receiver y avisa al
   usuario; el resto de pausas (cola wifi/red/reintento) se dejan intactas porque `DownloadManager` las
   resuelve solo.
5. `**LyricsFetchCoordinator**`: `scope.async` pasa a `CoroutineStart.LAZY` — el cuerpo (semáforo +
  llamada HTTP bloqueante) ya no arranca hasta que alguien hace `await()`; si la corrutina pierde la
   carrera de `putIfAbsent`, `cancel()` ahora es gratis (nunca llegó a ejecutar nada).
6. `**LyricsCache.putEntry**`: `runCatching { ... }.onFailure { Logger.e(...) }` — el fallo de escritura
  sigue sin ser fatal para el llamador, pero ya queda registrado.
7. `**LrcLibApi.fetchLyrics**`: presupuesto acumulado de 20s (`MAX_FETCH_BUDGET_MS`) para toda la
  búsqueda de una pista (todas las combinaciones título×artista); al agotarse, aborta con
   `failure("Timeout", null)` en vez de seguir encadenando llamadas.

**Riesgo:** medio, materializado en tests — 11-12 tocaban un parser binario propio (mayor riesgo de
regresión).

**Tests:** `ui/network/Mp4TagWriterTest.kt` (3 tests: inserta `udta` como hijo de `moov`, reemplaza sin
duplicar, lanza si no hay `moov` — construye MP4s mínimos sintéticos y los re-verifica con un parser de
átomos independiente del de producción). `core/VersionCompareTest.kt` ampliado con 2 casos de regresión
para el bug 8. 4-7 y 9-10 dependen de `DownloadManager`/`ContentResolver`/coroutines reales — verificados
manualmente, no por unit test JVM.

---

## Resumen priorizado


| Fase | Contenido                                                        | Complejidad | Depende de                  | Estado       |
| ---- | ---------------------------------------------------------------- | ----------- | --------------------------- | ------------ |
| A    | Errores de reproducción/cola visibles                            | Baja        | —                           | ✅ Completada |
| A2   | Seguridad update/instalación de APK                              | Baja-Media  | —                           | ✅ Completada |
| B    | Condiciones de carrera (library sync, download job, chunks HTTP) | Baja-Media  | —                           | ✅ Completada |
| C    | Feedback UX + TODOs pendientes                                   | Baja        | Fase A (reutiliza Snackbar) | ✅ Completada |
| D    | Karaoke: resaltado por palabra                                   | Baja        | —                           | ✅ Completada |
| E    | Karaoke: conmutador de modo + tono/velocidad                     | Baja        | Fase D (mismo overlay)      | ✅ Completada |
| F    | Karaoke: grabación (núcleo; export con mezcla pendiente)         | Media-Alta  | Validación de Fases D-E     | ✅ Completada |
| G    | Tags MP4 + robustez letras/update                                | Media       | —                           | ✅ Completada |


---

*Documento actualizado — Julio 2026*