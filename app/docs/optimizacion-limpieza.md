# BeatMyBeat — Plan de optimización y limpieza de código

> **Auditoría estática** del repositorio (`composeApp/`, `iosApp/`, Gradle, recursos).  
> Fecha de referencia: mayo 2026.  
> Objetivo: inventariar residuos, deuda técnica y mejoras **antes** de ejecutar refactors.

---

## Resumen ejecutivo

| Métrica | Valor |
|---|---|
| Archivos Kotlin en `androidMain` | ~55 |
| Líneas Kotlin (aprox.) | ~12 200 |
| Archivo más grande | `PlayerScreen.kt` (~4 024 líneas) |
| Targets KMP activos en producción | Solo **Android** |
| Tests automatizados útiles | Prácticamente **ninguno** |

La app funciona, pero arrastra restos del template KMP, del middleware eliminado, servicios duplicados y un reproductor monolítico. La limpieza prioritaria es **borrar muerto**, **unificar HTTP/feedback**, **partir `PlayerScreen`** y **alinear documentación**.

---

## Prioridades

| Nivel | Significado |
|---|---|
| **P0** | Riesgo de bugs, confusión alta o peso innecesario claro |
| **P1** | Mejora mantenibilidad / tamaño APK / claridad |
| **P2** | Pulido, nice-to-have |

---

## 1. Arquitectura y estructura del proyecto

### 1.1 Kotlin Multiplatform sin uso real (P0)

| Elemento | Estado | Acción recomendada |
|---|---|---|
| `commonMain/App.kt`, `Greeting.kt` | Template “Click me!” | Eliminar o dejar de compilar si se abandona iOS |
| `commonMain/composeResources/compose-multiplatform.xml` | Sin uso en Android | Eliminar con el template |
| `iosMain/` + `iosApp/` | No se mantiene iOS | Quitar targets `iosArm64` / `iosSimulatorArm64` del `build.gradle.kts` y carpeta `iosApp/` (o documentar “congelado”) |
| `Platform.kt` / `getPlatform()` | Solo lo usa `Greeting` | Eliminar con el template |
| `README.md` raíz | Describe KMP genérico | Reescribir: “app Android + Compose; fork KMP histórico” |

**Beneficio:** builds más rápidos, menos confusión para quien entre al repo.

### 1.2 UI 100 % en `androidMain` (informativo)

Toda la app real está en `androidMain`. No hay beneficio actual en forzar código a `commonMain` salvo que se retome iOS.

### 1.3 Documentación interna desactualizada (P1)

| Documento | Problema |
|---|---|
| `docs/mejoras.md` | Dice que Coil no está instalado, que `PlaylistScreen` está registrada, Snackbar en todo el player, etc. — **obsoleto** |
| `docs/cambios.md` | Útil como histórico; conviene enlazar desde README |
| `README.md` | No refleja arquitectura real ni `docs/riesgos-legales.md` |

**Acción:** actualizar `mejoras.md` o marcarlo como archivo histórico; README con enlaces a `docs/`.

---

## 2. Código muerto y residuos

### 2.1 Servicio `BeatMyBeatForegroundService` (P0)

- **Ubicación:** `service/BeatMyBeatForegroundService.kt`
- **Uso real:** solo `BeatMyBeatForegroundService.stopPlayback()` desde `PlayerScreen` al borrar la pista actual.
- **No usado:** `ACTION_START_DOWNLOAD`, `ACTION_START_PLAYBACK`, `startDownload`, `startPlayback`, etc.
- La reproducción y descargas viven en **`PlaybackService`** y **`SongDownloadService`** con sus propias notificaciones.

**Acción:** sustituir `stopPlayback` por parada vía `PlaybackService` / ExoPlayer y **eliminar** el servicio + entrada en `AndroidManifest.xml`.

### 2.2 `RemoteArtworkCache.kt` (P0)

- Solo lo usaba **`PlaylistScreen.kt`** (eliminada).
- Ninguna otra referencia en el proyecto.

**Acción:** borrar el archivo.

### 2.3 API `@Deprecated` en `PlayerViewModel` (P1)

Métodos legacy de cola/shuffle (`loadShufflePersistedOrder`, `persistManualQueueUris`, `loadManualQueueUris`, etc.) — **9 funciones** marcadas `@Deprecated`.

**Acción:** buscar referencias (deberían ser 0), eliminar métodos y, si la migración ya es estable, quitar prefs legacy en una versión posterior (`PREF_MANUAL_QUEUE_*`, `PREF_SHUFFLE_*` antiguos).

### 2.4 Strings de playlist / middleware (P1)

Tras quitar `PlaylistScreen` y el middleware, siguen en **6 locales** (`values*`) sin uso en Kotlin:

- `playlist_error_cannot_connect` (texto de “servidor de playlists”)
- `playlist_error_cannot_load`
- `playlist_default_title`
- `playlist_loading`
- `playlist_tracks_found`
- `playlist_download_all` (ZIP vía servidor)

**Acción:** eliminar de todos los `strings.xml` o migrar textos reutilizables si alguno encaja en la UI de playlists del reproductor.

### 2.5 Parámetro muerto en `PlaybackService.loadQueue` (P2)

- `shuffleEnabled: Boolean` — `@Suppress("UNUSED_PARAMETER")`; la UI ya envía la cola ordenada.

**Acción:** quitar del API interno si no hay llamadas externas.

### 2.6 TODOs en código (P2)

| Archivo | Nota |
|---|---|
| `PlayerScreen.kt` ~2593 | `onHide = { /* TODO */ }` |
| `ProfileScreen.kt` | `/* TODO: cambiar foto */` |
| `data_extraction_rules.xml` | TODO plantilla Android |

**Acción:** implementar, eliminar menú “ocultar” si no aplica, o crear issues.

### 2.7 Texto hardcodeado sin i18n (P1)

- `PlayerScreen.kt`: `"Ya existe una playlist con ese nombre."` — debería ser `strings.xml` en todos los idiomas.

---

## 3. Archivos monolíticos (refactor)

### 3.1 `PlayerScreen.kt` (~4 024 líneas) (P0)

Concentra:

- Navegación interna (secciones, playlists, selección múltiple)
- Cola, shuffle, repeat, persistencia
- Mini reproductor + overlay expandido + letras sincronizadas
- Diálogos (playlist, borrado, etc.)
- Composables privados (`TrackRow`, `MiniPlayerBar`, `ExpandedPlayerOverlay`, `ArtworkThumbnail`, …)

**Propuesta de división:**

```
ui/feature/player/
  PlayerScreen.kt          // composable raíz + wiring ViewModel
  PlayerQueueController.kt // cola, shuffle, insertTracksPlayNext, snapshot (lógica)
  PlayerLibraryUi.kt       // lista, filtros, selección, TrackRow
  PlayerMiniBar.kt
  PlayerExpandedOverlay.kt
  PlayerArtwork.kt         // ArtworkThumbnail, carga carátula
  PlayerDialogs.kt         // añadir a playlist, confirmaciones
```

**Beneficio:** reviews, tests unitarios de cola, menos conflictos en git.

### 3.2 Otros archivos grandes (P1)

| Archivo | Líneas | Nota |
|---|---|---|
| `AnalyzeScreen.kt` | ~875 | Separar preview URL / búsqueda / descarga |
| `MediaStoreScanner.kt` | ~627 | Aceptable si está cohesionado; extraer parsers de metadatos |
| `ThemeCustomizerScreen.kt` | ~488 | OK tras arreglo del color picker |
| `AudioDownloader.kt` | ~464 | Separar FFmpeg/transcode vs descarga HTTP |
| `PlayerViewModel.kt` | ~453 | Tras limpiar deprecated, revisar responsabilidades |

---

## 4. Red, caché y dependencias

### 4.1 Clientes HTTP duplicados (P1)

| Componente | Cliente |
|---|---|
| `AppHttpClient` | Singleton lazy ✅ |
| `YouTubeSearchClient` | `OkHttpClient.Builder()` propio |
| `LrcLibApi` | propio |
| `LyricsOvhApi` | propio |
| `NewPipeStreamExtractor` | propio (+ inner `OkHttpDownloader`) |
| `AudioDownloader` | `OkHttpClient.Builder()` por descarga |
| `YouTubeMetadata` | `OkHttpClient()` **nuevo cada llamada** |

**Acción:** unificar en `AppHttpClient.instance` (o factory con timeouts por caso) y reutilizar connection pool.

### 4.2 Dependencia `androidx.media` (no Media3) (P1)

- Declarada en `build.gradle.kts` (`androidx.media:media`).
- **Sin imports** en el código fuente.

**Acción:** eliminar si `assembleDebug` no la requiere transitivamente.

### 4.3 `documentfile` duplicado en Gradle (P2)

- `androidx-documentfile` (catalog) y `documentfile` 1.1.0 — revisar si hace falta una sola.

### 4.4 Logs de depuración en release (P1)

`android.util.Log` en producción:

| Archivo | Aprox. |
|---|---|
| `AudioDownloader.kt` | 15 |
| `NewPipeStreamExtractor.kt` | 7 |
| `PlaybackService.kt` | 1 |
| `AnalyzeScreen.kt` | 1 |

**Acción:** wrapper `Logger` con `if (BuildConfig.DEBUG)` o Timber con no-op en release.

### 4.5 NewPipe / InnerTube (P2 — mantenimiento)

- `INNERTUBE_KEY` y versiones de cliente hardcodeadas en `YouTubeSearchClient`.
- APIs deprecadas en NewPipe (`isVideoOnly`, `resolution`) — warnings en compilación.

**Acción:** centralizar constantes; planificar actualización de NewPipe Extractor.

---

## 5. UI, feedback y recursos

### 5.1 Snackbar vs Toast (P1)

| Pantalla | Estado |
|---|---|
| `PlayerScreen` | Migrado a **Toast** ✅ |
| `AnalyzeScreen` | Sigue usando **Snackbar** vía `LocalSnackbarHostState` |
| `MainActivity` | `Scaffold` + `SnackbarHost` global (p. ej. permisos almacenamiento) |

**Acción:** valorar Toast también en Analyze o Snackbar solo en `MainActivity` para acciones globales; documentar convención en el README del módulo.

### 5.2 `LocalSnackbarHost` + Scaffold (P2)

Si el player ya no usa Snackbar, simplificar `MainActivity` (host solo para flujos que lo necesiten).

### 5.3 Duplicación de strings entre locales (P1)

- `values/strings.xml` es el más completo.
- `values-es`, `en`, `de`, `pt`, `hr` tienen subconjuntos distintos; riesgo de **fallback silencioso** al español del sistema.

**Acción:** script o convención “`values/strings.xml` base + traducciones completas por locale”; eliminar claves huérfanas.

### 5.4 `Color.kt` vs perfiles de tema (P2)

`Color.kt` define Neon/Cherry; `ThemeProfilesStore` tiene perfiles dinámicos. Coherente pero mezcla “temas fijos” y “personalizados” — documentar en `Theme.kt`.

---

## 6. Build, APK y calidad

### 6.1 Release no optimizado (P1)

```kotlin
// composeApp/build.gradle.kts
isMinifyEnabled = false
```

**Acción:** habilitar R8/ProGuard en `release` con reglas para NewPipe, FFmpeg-kit, Media3; medir tamaño y probar descarga/reproducción.

### 6.2 Sin `LICENSE` en raíz (P1)

Relevante para GitHub / F-Droid (`docs/riesgos-legales.md` ya lo menciona).

### 6.3 Tests (P1)

- `ComposeAppCommonTest`: `assertEquals(3, 1 + 2)` — plantilla inútil.
- Sin tests de `PlaybackQueueSnapshot`, `parseYouTubeInput`, `resolvePlaybackQueueSnapshot`, `insertTracksPlayNext`, etc.

**Acción mínima:** tests unitarios para cola y parsing URL; opcional UI tests críticos.

### 6.4 Artefactos de build en el workspace (P2)

- `composeApp/build/` puede estar versionado o ensuciar IDE — verificar `.gitignore`.

---

## 7. Rendimiento y memoria (ya abordado parcialmente)

Referencia: `docs/cambios.md`. Mantener y no revertir:

| Área | Estado |
|---|---|
| `ArtworkCache` / bytes + sampling | ✅ |
| `PlaybackService.replaceMediaItems` | ✅ |
| Throttle notificaciones descarga | ✅ |
| Cola ExoPlayer: solo actual + pendientes | ✅ |

### Mejoras pendientes (P1)

- **Recomposiciones** en `PlayerScreen`: muchos `remember` + `LaunchedEffect`; revisar keys y `derivedStateOf` para listas grandes.
- **Palette** en overlay expandido: trabajo en `Dispatchers.Default` — OK; cachear por `track.uri`.
- **MediaStoreScanner** en bibliotecas grandes: ya async en ViewModel; perfilar ANR si hay reportes.

---

## 8. Seguridad y configuración

| Tema | Estado |
|---|---|
| `network_security_config` | Solo HTTPS ✅ (middleware HTTP eliminado) |
| Permisos | Razonables para lector de audio + notificaciones + FGS |
| Backup rules | Plantilla por revisar (`data_extraction_rules.xml`) |

---

## 9. Plan de ejecución sugerido (orden)

### Fase A — Limpieza rápida (1–2 días)

1. Eliminar `RemoteArtworkCache.kt`
2. Eliminar o refactorizar `BeatMyBeatForegroundService`
3. Quitar strings `playlist_*` huérfanas (6 locales)
4. Eliminar métodos `@Deprecated` no usados en `PlayerViewModel`
5. Quitar target iOS + template `commonMain` (si confirmado)
6. Añadir string para playlist duplicada; quitar log en release (wrapper)

### Fase B — Mantenibilidad (3–5 días)

7. Dividir `PlayerScreen.kt` en 4–6 archivos
8. Unificar `OkHttp` en `AppHttpClient`
9. Toast en `AnalyzeScreen` o convención documentada
10. Actualizar `docs/mejoras.md`, `README.md`

### Fase C — Calidad release (2–3 días)

11. `LICENSE` + README distribución
12. R8 + reglas ProGuard
13. Tests unitarios cola / URL / snapshot

### Fase D — Pulido UI (opcional, ver `mejoras.md`)

14. Completar `darkColorScheme` M3
15. Tipografía / shimmer / transiciones navegación

---

## 10. Qué no limpiar sin decisión de producto

- **NewPipe + descarga YouTube** — núcleo de la app; no “optimizar” eliminando sin sustituto.
- **Migración prefs legacy** — esperar 1–2 versiones tras cola unificada por si usuarios antiguos restauran backup.
- **`ffmpeg-kit`** — pesado pero necesario para formatos; alternativa es reducir formatos exportados.

---

## 11. Checklist de verificación post-limpieza

- [ ] `./gradlew :composeApp:assembleDebug` OK
- [ ] Reproducir / cola / shuffle / “reproducir a continuación” / cambiar sección con música activa
- [ ] Descarga canción + playlist desde Analizar
- [ ] Notificación Media3: icono y logo fallback
- [ ] Instalar release minificado en dispositivo real
- [ ] Buscar en repo: `middleware`, `PlaylistScreen`, `178.104`, `showSnackbar` en player

---

## 12. Relación con otros documentos

| Documento | Contenido |
|---|---|
| [riesgos-legales.md](./riesgos-legales.md) | Distribución APK, F-Droid, GitHub |
| [cambios.md](./cambios.md) | Histórico de features implementadas |
| [mejoras.md](./mejoras.md) | Ideas UI (parcialmente desactualizado) |

---

*Generado como inventario previo a refactor. No implica que todos los ítems deban hacerse: priorizar Fase A + división de `PlayerScreen`.*

---

## Registro de ejecución

### Fase A — Limpieza rápida ✅ (compila: `assembleDebug` OK)

1. **`RemoteArtworkCache.kt` eliminado.** Sin referencias en el proyecto (solo lo usaba `PlaylistScreen`, ya borrada).
2. **`BeatMyBeatForegroundService` eliminado.**
   - Borrado el archivo `service/BeatMyBeatForegroundService.kt` y su entrada `<service>` en `AndroidManifest.xml`.
   - La única llamada real (`stopPlayback` al borrar la pista en reproducción) se sustituyó por `context.sendPlaybackForegroundAction(PlaybackService.ACTION_STOP)`, que para ExoPlayer y retira la notificación vía `PlaybackService`.
   - Quitado el import obsoleto en `PlayerScreen.kt`.
3. **Strings `playlist_*` huérfanas eliminadas** (`playlist_error_cannot_connect`, `playlist_error_cannot_load`, `playlist_default_title`, `playlist_loading`, `playlist_tracks_found`, `playlist_download_all`) en los 6 locales (`values`, `values-es`, `values-en`, `values-de`, `values-pt`, `values-hr`). Confirmado 0 usos en Kotlin.
4. **9 métodos `@Deprecated` eliminados de `PlayerViewModel`** (`loadShufflePersistedOrder`, `loadShuffleIndex`, `persistShuffleState`, `loadManualQueueUris`, `persistManualQueueUris`, `clearManualQueuePersistence`, `loadLastPendingQueueUris`, `persistLastPendingQueue`, `clearLastPendingQueuePersistence`). 0 referencias. Las prefs legacy se mantienen (las usa `migrateLegacyQueueSnapshot`).
5. **Target iOS + template `commonMain` eliminados** (confirmado: iOS descartado).
   - `build.gradle.kts`: quitado el bloque `iosArm64()/iosSimulatorArm64()`.
   - Borrados `commonMain/App.kt`, `Greeting.kt`, `Platform.kt`, `composeResources/`, `iosMain/` (`Platform.ios.kt`, `MainViewController.kt`), `androidMain/Platform.android.kt` y la carpeta `iosApp/`.
6. **String de playlist duplicada i18n + wrapper de logs.**
   - `PlayerScreen.kt`: el texto hardcodeado `"Ya existe una playlist con ese nombre."` ahora usa `R.string.player_playlist_name_exists` (ya existente en los 6 locales).
   - Nuevo `core/Logger.kt`: envoltura que solo loguea si `BuildConfig.DEBUG` (no-op en release). Habilitado `buildFeatures { buildConfig = true }`.
   - Migrados todos los `android.util.Log.*` a `Logger.*` en `AudioDownloader.kt` (15), `NewPipeStreamExtractor.kt` (7), `PlaybackService.kt` (1) y `AnalyzeScreen.kt` (1).

### Fase B — Mantenibilidad (en progreso)

7. **`PlayerScreen.kt` dividido (4 024 → 2 459 líneas).** El monolito se repartió en el mismo paquete `ui/feature/player/` (las declaraciones extraídas pasaron de `private` a `internal` para verse entre archivos; el contenido se movió tal cual, sin reescribir lógica):
   - `PlayerScreen.kt` — composable raíz + wiring del `PlayerViewModel` y `sendPlaybackForegroundAction` (privada, solo se usa aquí).
   - `PlayerLibraryUi.kt` — `TrackRow`, `TrackSelectionOverflowMenu`, `TrackOverflowMenu`, `LibraryFiltersMenu`, `PlaylistDetailHeader`, `PlaylistPickerBar`, `ActionPillButton`, `PrimaryPillButton`, `LibraryEmptyState`.
   - `PlayerArtwork.kt` — `ArtworkThumbnail` y helpers de color de carátula (`androidArgbIntToComposeColor`, `expandedPlayerOverlayGradientTop`).
   - `PlayerMiniBar.kt` — `MiniPlayerBar`.
   - `PlayerExpandedOverlay.kt` — `ExpandedPlayerOverlay`.
   - `PlayerModels.kt` — tipos y helpers compartidos: `RepeatMode`, `PlayerSection`, `SortOption`, `LyricsUiState`, `TrackLyricsMetadata`, constantes de tamaño de carátula y funciones puras (`resolveTrackFromPlaybackMediaId`, `buildVisibleTracksForSection`, `resolveTrackMetadata`, `resolveTrackMeta`, `toTitleCaseSimple`, `formatMs`).
   - Verificado: `compileDebugKotlin` OK.

8. **Clientes OkHttp unificados en `AppHttpClient`.** Antes había 6 builders/instancias duplicados (cada uno con su connection pool y dispatcher propios).
   - `AppHttpClient` ahora expone `withTimeouts(connect/read/write/call, followRedirects)`, que deriva del `instance` base con `newBuilder()` para **reutilizar el connection pool, el dispatcher y la caché**.
   - Migrados a `AppHttpClient.withTimeouts(...)` (conservando los timeouts originales de cada caso): `YouTubeSearchClient`, `LrcLibApi`, `LyricsOvhApi`, `NewPipeStreamExtractor` (con `followRedirects = true`) y el cliente de descarga de `AudioDownloader`.
   - `YouTubeMetadata` (que creaba un `OkHttpClient()` nuevo en **cada** llamada) ahora usa `AppHttpClient.instance`.
   - Eliminados los imports de `OkHttpClient`/`TimeUnit` que quedaron sin uso.
   - Verificado: `compileDebugKotlin` OK.

9. **`AnalyzeScreen` migrado a Toast (convención de feedback unificada).**
   - `showSnack(...)` ahora usa `Toast.makeText(context.applicationContext, ...)` en lugar de `LocalSnackbarHostState.showSnackbar(...)`; todas las llamadas existentes quedan iguales.
   - Eliminados imports sin uso (`SnackbarDuration`, `LocalSnackbarHostState`).
   - **Convención resultante:** las pantallas de contenido (`PlayerScreen`, `AnalyzeScreen`) usan **Toast**; el `Snackbar` global queda **solo en `MainActivity`** (`LocalSnackbarHost`) para flujos de app como permisos de almacenamiento.
   - Verificado: `compileDebugKotlin` OK.

10. **Documentación alineada (`README.md` y `docs/mejoras.md`).**
    - `README.md`: reescrito desde el template KMP genérico. Ahora describe la arquitectura real (app **solo Android** con Compose/Media3/NewPipe), la estructura de `androidMain`, cómo compilar y una tabla de enlaces a `docs/`.
    - `docs/mejoras.md`: añadido banner de "documento parcialmente histórico" y corregido el diagnóstico (Coil **sí** instalado, iOS eliminado, `PlaylistScreen` eliminada, skeletons propios existentes y convención de feedback Toast/Snackbar). Marcada como no aplicable la mejora 1.6 (Toast→Snackbar).

### Fase B — Verificación final ✅ (`assembleDebug` OK)

### Fase C — Calidad release (en progreso)

11. **`LICENSE` (GPL-3.0) + README de distribución.**
    - Añadido `LICENSE` con el texto de la **GNU GPL v3.0**. Se elige GPL-3.0 por compatibilidad copyleft con **NewPipe Extractor** (GPL-3.0), del que depende la app.
    - `README.md`: nuevas secciones **Licencia**, **Distribución y uso responsable** (con el aviso legal sugerido en `docs/riesgos-legales.md`) y enlace al documento de riesgos legales.

12. **R8/ProGuard habilitado en `release`.**
    - `composeApp/build.gradle.kts`: `release` pasa de `isMinifyEnabled = false` a `isMinifyEnabled = true` + `isShrinkResources = true` + `proguardFiles(proguard-android-optimize.txt, proguard-rules.pro)`.
    - Nuevo `composeApp/proguard-rules.pro` con reglas para: **NewPipe Extractor** (Rhino/Mozilla JS para descifrar firmas, nanojson, jsoup, autolink), **ffmpeg-kit** (clases JNI `com.arthenica.*`), **Media3** y `-dontwarn` de OkHttp/Okio.
    - **Tamaño APK:** debug **128,8 MB** → release minificado **107,2 MB** (≈ −17 %). El peso restante lo dominan las librerías nativas de `ffmpeg-kit` (todas las ABIs), que R8 no reduce.
    - Verificado: `assembleRelease` OK (los `WARNING: R8 ... kotlin metadata` son benignos: versión de R8 anterior a la de Kotlin). Pendiente de validación funcional en dispositivo real (reproducción/descarga), según checklist.

13. **Tests unitarios (cola + parsing de URL).**
    - Eliminada la plantilla inútil `ComposeAppCommonTest` (`assertEquals(3, 1 + 2)`).
    - Nuevo source set `androidUnitTest` (con `libs.kotlin.test`) en `build.gradle.kts`.
    - `parseYouTubeInput`, `ParsedYouTubeInput` y `extractYouTubeVideoId` pasaron de `private` a `internal` para poder testearse.
    - `PlaybackQueueSnapshotTest` (8 tests): `resolvePlaybackQueueSnapshot` con cola vacía, URIs inexistentes, omisión de pistas borradas conservando orden, mapeo de `currentIndex`, avance cuando la pista actual desaparece, fallback a la anterior y _clamp_ de índices fuera de rango.
    - `YouTubeInputParsingTest` (11 tests): formato inválido, host no permitido, `watch`/`youtu.be`/`shorts`/`live`, host `music.youtube.com`, playlist, prioridad del parámetro `list` sobre el vídeo y validación de longitud del videoId.
    - `insertTracksPlayNext` **no** se testea aún: es una función local dentro del composable `PlayerScreen` y requeriría extraer su lógica de estado (queda para un refactor posterior). El round-trip JSON de `PlaybackQueueSnapshot` tampoco, porque `org.json` no está disponible en unit tests JVM puros.
    - Verificado: `./gradlew :composeApp:testDebugUnitTest` → **19 tests, 0 fallos**.

### Fase C — Verificación final ✅ (`assembleRelease` OK · `testDebugUnitTest` 19/19 OK)

### Fase D — Pulido UI (opcional) ✅ ya implementada (verificada: `assembleDebug` + `testDebugUnitTest` OK)

Al revisar el código, los dos ítems de la Fase D ya estaban implementados (trabajo previo de UI; `mejoras.md` estaba desactualizado). No se requieren cambios:

14. **`darkColorScheme` M3 completo** — `ui/theme/Theme.kt` ya mapea ~30 roles (primary/secondary/tertiary + sus *container*, `surface*` con `bumpChannels`, `outline`, `inverse*`, `error*`, `surfaceTint = Transparent`), no solo los 6 básicos que mencionaba `mejoras.md`.
15. **Tipografía / shimmer / transiciones de navegación:**
    - **Tipografía:** `ui/theme/Type.kt` define la escala M3 completa (display/headline/title/body/label). Se mantiene `FontFamily.Default` **por decisión explícita** (no añadir binarios de fuentes); la propuesta de fuente custom de `mejoras.md` 1.2 queda descartada para no depender de binarios ni de Google Play Services (relevante para F-Droid).
    - **Shimmer:** `ui/theme/LoadingSkeletons.kt` (`TrackListSkeleton`, `SuggestionListSkeleton`) con `com.valentinilk.shimmer`, ya cableados en `PlayerScreen` (lista de biblioteca) y `AnalyzeScreen` (sugerencias).
    - **Transiciones de navegación:** `MainActivity` define `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` (fade + slide) en el `NavHost`, además de `enableEdgeToEdge()` y `NavigationBar`/`Scaffold`.

> El resto de ideas de `mejoras.md` (FilterChip, Button M3, ListItem, `OutlinedTextField` con `supportingText`, crossfade de carátula, animación spring del play, accesibilidad, Lottie, Accompanist) **no forman parte del alcance estricto de la Fase D** (ítems 14–15) y quedan como pulido opcional futuro.
