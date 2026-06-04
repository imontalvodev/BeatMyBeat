# Fase 3 — Polish y detalles

Implementación alineada con la [Fase 3 de mejoras.md](mejoras.md): shimmer/skeletons, animaciones con APIs nativas de Compose, estado vacío de biblioteca, primeras mejoras de accesibilidad y transiciones de navegación. **No** se añadieron Lottie (3.5) ni Accompanist permisos (3.6), que siguen documentados como opcionales en `mejoras.md`.

---

## Dependencias

**Archivo:** `gradle/libs.versions.toml`

- `shimmer = "1.3.1"` en `[versions]`.
- `compose-shimmer = { module = "com.valentinilk.shimmer:compose-shimmer", version.ref = "shimmer" }`.

**Archivo:** `composeApp/build.gradle.kts` (`androidMain.dependencies`)

- `implementation(libs.compose.shimmer)`

---

## 3.1 Shimmer — skeletons de carga

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/theme/LoadingSkeletons.kt`

**Qué:**

- **`TrackListSkeleton`**: filas tipo fila de pista (avatar + dos líneas) con `rememberShimmer(shimmerBounds = ShimmerBounds.Window)` y `Modifier.shimmer`, para la biblioteca mientras no hay datos visibles pero la sincronización está en curso.
- **`SuggestionListSkeleton`**: variante más compacta para la lista de sugerencias en Analyze.

**Por qué:** Evita pantallas en blanco y refuerza la percepción de que la app está trabajando.

**Archivo:** `PlayerViewModel.kt`

- **`librarySyncing: StateFlow<Boolean>`**: se pone en `true` al iniciar `syncLibrary` y en `false` en `finally`, para distinguir “aún no hay pistas” de “estamos indexando”.

**Archivo:** `PlayerScreen.kt`

- Con permiso de audio, lista vacía y `librarySyncing == true`: se muestra **`TrackListSkeleton`** en lugar del contenido principal.

**Archivo:** `AnalyzeScreen.kt`

- Mientras `searchingSuggestions` es verdadero: **`SuggestionListSkeleton()`** en lugar del texto plano de “buscando”.

---

## 3.2 Animaciones (Compose Animation)

### 3.2a Navegación

**Archivo:** `MainActivity.kt`

- **`NavHost`** con `enterTransition`, `exitTransition`, `popEnterTransition` y `popExitTransition` (combinación de `fadeIn` / `fadeOut` con `slideInHorizontally` / `slideOutHorizontally`), sin Accompanist.

### 3.2b Mini reproductor

**Archivo:** `PlayerScreen.kt`

- **`AnimatedVisibility(currentTrack != null, …)`** alrededor de **`MiniPlayerBar`**: entrada/salida con slide vertical + fade.

### 3.2c Cambio de sección

**Archivo:** `PlayerScreen.kt`

- El cuerpo de la lista principal va dentro de **`AnimatedContent(selectedSection, …)`** con transición tipo fade + slide horizontal entre Songs / Favorites / Playlist.
- La función **`buildVisibleTracksForSection`** quedó extraída (tras los enums de sección y orden) para reutilizar la misma lógica de filtrado dentro del bloque animado.

### 3.2d Crossfade de artwork

**Archivo:** `PlayerScreen.kt`

- **`ArtworkThumbnail`**: el contenido dependiente de los bytes resueltos va envuelto en **`Crossfade(imageData, …)`** para suavizar el cambio de carátula en lista.
- **`MiniPlayerBar`** y **`ExpandedPlayerOverlay`**: crossfade similar sobre la imagen actual del tema.

### 3.2e Botón Play / Pause

**Archivo:** `PlayerScreen.kt`

- **`animateFloatAsState`** (`androidx.compose.animation.core`) con **`spring`** y **`Modifier.scale`** en los botones de play/pause del mini reproductor y del overlay expandido.

---

## 3.3 Estado vacío de biblioteca

**Archivo:** `PlayerScreen.kt` — composable **`LibraryEmptyState`**

**Qué:** Si hay permiso de audio, la lista visible está vacía y no hay sincronización en curso, se muestra icono `LibraryMusic`, textos desde recursos, y **`OutlinedButton`** que navega al flujo de descarga/análisis mediante el callback **`onNavigateToDownloader`** (por defecto no-op).

**Archivo:** `MainActivity.kt`

- **`PlayerScreen(onNavigateToDownloader = { navController.navigate("analyze") { launchSingleTop = true } })`**.

**Archivo:** `res/values/strings.xml`

- Cadenas para título, subtítulo, botón “Ir a Descargar” y mensajes de permiso donde aplica.

---

## 3.4 Accesibilidad (primer paso)

**Archivo:** `PlayerScreen.kt`

- **`contentDescription`** en iconos relevantes del reproductor (play, pause, cola, más opciones, cerrar expandido, etc.) usando **`stringResource`**.
- **`Slider`**: **`Modifier.semantics { contentDescription = … }`** con posición formateada (mini y expandido).
- **`MiniPlayerBar`**: parámetro **`sliderAccessibilityLabel`** para reutilizar la misma cadena semántica.

**Archivo:** `components.kt` — **`ModeChip`**

- **`Modifier.semantics { role = Role.Button }`** para que lectores de pantalla traten el chip como botón.

**Pendiente respecto a la tabla completa de `mejoras.md`:** revisión exhaustiva de todos los `IconButton`, touch targets mínimos de 48dp y el resto de pantallas.

---

## Resumen de archivos

| Archivo | Cambio |
|---------|--------|
| `gradle/libs.versions.toml` | Versión y entrada `compose-shimmer`. |
| `composeApp/build.gradle.kts` | Dependencia shimmer en `androidMain`. |
| `ui/theme/LoadingSkeletons.kt` | `TrackListSkeleton`, `SuggestionListSkeleton`. |
| `PlayerViewModel.kt` | `librarySyncing` + actualización en `syncLibrary`. |
| `PlayerScreen.kt` | Skeleton, empty state, animaciones, crossfade, a11y parcial. |
| `MainActivity.kt` | Transiciones `NavHost` + callback a `analyze`. |
| `AnalyzeScreen.kt` | Skeleton mientras se buscan sugerencias. |
| `components.kt` | Semántica de rol en `ModeChip`. |
| `res/values/strings.xml` | Textos biblioteca vacía, permiso, CD de controles. |

---

## No incluido (opcional en mejoras.md)

- **3.5 Lottie** (ecualizador, empty state animado, descargas).
- **3.6 Accompanist** (`rememberPermissionState`, system bars): el permiso de audio sigue con `rememberLauncherForActivityResult` como antes.

---

*Documento generado al cerrar la Fase 3 — Mayo 2026*
