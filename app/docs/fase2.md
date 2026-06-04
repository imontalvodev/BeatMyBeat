# Fase 2 — Experiencia visual (Coil + Palette API)

Implementación alineada con la [Fase 2 de mejoras.md](mejoras.md): carga de imágenes con **Coil**, color atmosférico con **Palette** en el reproductor expandido, sin cambiar la paleta de marca global ni la lógica de servicios.

---

## Dependencias

**Archivo:** `gradle/libs.versions.toml`

- `coil = "2.7.0"` en `[versions]`.
- `coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }`.
- `androidx-palette = "1.0.0"` y `androidx-palette-ktx = { group = "androidx.palette", name = "palette-ktx", version.ref = "androidx-palette" }`.

**Archivo:** `composeApp/build.gradle.kts` (`androidMain.dependencies`)

- `implementation(libs.coil.compose)`
- `implementation(libs.androidx.palette.ktx)`

---

## 2.1 Coil — miniaturas de sugerencias (Analyze)

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/analyze/AnalyzeScreen.kt`

**Qué:** `SuggestionThumbnail` deja de usar `OkHttpClient` + `BitmapFactory` + `RemoteArtworkCache` por composición. Ahora usa **`AsyncImage`** con `ImageRequest.Builder` (URL de YouTube), `crossfade(true)`, `ContentScale.Crop`, esquinas con `clip(RoundedCornerShape(10.dp))` y `contentDescription` basado en el título de la sugerencia.

**Por qué:** Coil aplica cache en memoria y disco, reutiliza conexiones y evita crear un cliente OkHttp por composición. Mejor rendimiento y menos código mantenido a mano.

**Nota:** No se añadió `ic_music_placeholder` (no existía en `res`); el fondo usa `surfaceVariant` semitransparente y Coil gestiona el estado vacío de URL.

---

## 2.1 Coil — carátulas en lista y mini reproductor

**Archivo:** `PlayerScreen.kt` — composable **`ArtworkThumbnail`**

**Qué:** La carga prioriza **`PlaybackArtworkHelper.resolveArtworkBytes`** (misma prioridad que antes: etiqueta embebida, luego `.meta.json` en descargas), decodifica a `Bitmap` en IO y alimenta **`AsyncImage`** con `ImageRequest` (crossfade, `placeholder` / `error` con `ic_launcher_foreground`).

**Por qué:** Misma fuente de bytes que el servicio de notificaciones / sesión, sin duplicar reglas; Coil centraliza decode y transiciones.

**Mini player:** la carátula del tema actual se muestra con **`AsyncImage`** sobre el `Bitmap` ya resuelto por el `LaunchedEffect` existente de `currentTrack` (sin tocar `PlaybackService`).

**Overlay expandido — carátula nítida:** el `Card` principal de la portada usa **`AsyncImage`** con el mismo `Bitmap` y crossfade.

---

## 2.1 Desenfoque de fondo en pantalla completa

**Archivo:** `PlayerScreen.kt` — **`ExpandedPlayerOverlay`**

**Qué:** Capa de fondo a pantalla completa con la misma carátula, **`Modifier.blur(28.dp)`** + `alpha(0.38f)` y `ContentScale.Crop`.

**Por qué:** En este entorno KMP, la clase **`coil.transform.BlurTransformation`** no quedó resuelta en el classpath de compilación Android; **`Modifier.blur`** de Compose ofrece un efecto similar sin depender de transformaciones internas de Coil y evita añadir módulos extra.

---

## 2.2 Palette API — tinte dinámico del fondo

**Archivo:** `PlayerScreen.kt` — **`ExpandedPlayerOverlay`**

**Qué:**

- Se obtiene el perfil con **`currentBeatMyBeatThemeProfile()`** (no se modifica el perfil guardado).
- Con **`LaunchedEffect(artwork, track?.id, …)`** se calcula en **`Dispatchers.Default`** un color dominante con **`Palette.from(bitmap).generate()`**, priorizando `darkVibrantSwatch`, luego `mutedSwatch`, luego `dominantSwatch`.
- Ese color (con alpha ~0.82) anima hacia el valor nuevo con **`animateColorAsState`** + **`tween(600)`**.
- Un **`Brush.verticalGradient`** une ese tono animado con **`backgroundBottom`** del perfil y se dibuja encima del fondo desenfocado.

**Por qué:** Sensación “now playing” acorde al artwork sin alterar el tema global de la app (`BeatMyBeatTheme` / perfiles).

**Detalle:** El parámetro **`bgBrush`** del overlay se eliminó: el fondo lo componen blur + gradiente + paleta; el resto de la UI (cards, letra, transporte) se mantiene igual en comportamiento.

---

## Resumen de archivos

| Archivo | Cambio |
|---------|--------|
| `gradle/libs.versions.toml` | Versiones y entradas Coil + Palette. |
| `composeApp/build.gradle.kts` | `coil-compose` y `palette-ktx` en `androidMain`. |
| `AnalyzeScreen.kt` | `SuggestionThumbnail` con Coil + accesibilidad. |
| `PlayerScreen.kt` | `ArtworkThumbnail` y mini/expanded con Coil; overlay con blur Compose + Palette + gradiente. |

---

## No incluido (voluntario)

- **`PlayerViewModel`:** no fue necesario mover estado; la extracción de color vive en el overlay.
- **`SongDownloadService` / Toasts en servicio:** sin cambios, según restricción de servicios.

---

*Documento generado al cerrar la Fase 2 — Mayo 2026*
