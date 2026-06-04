# Fase 1 — Implementación (Material 3 y base de UI)

Este documento describe **qué se ha implementado** de la *Fase 1* del plan en [mejoras.md](mejoras.md), **por qué**, y **dónde** está el código.

Restricciones respetadas: **no se ha cambiado la paleta de colores de marca** (los mismos `Color` de `Color.kt` y perfiles de tema); **no se ha modificado la lógica de los servicios** (`PlaybackService`, `SongDownloadService`, `BeatMyBeatForegroundService`, descargas, etc.).

---

## 1. Esquema de color Material 3 completo

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/theme/Theme.kt`

**Qué:** `BeatMyBeatTheme` ahora construye un `darkColorScheme` con roles adicionales: contenedores primarios/secundarios/terciarios, variantes de superficie (`surfaceVariant`, `surfaceContainer*`), `outline` / `outlineVariant`, error y contenedores de error, `inverse*`, `scrim`, `surfaceTint`, etc. Se añadió un helper privado `Color.bumpChannels()` para derivar superficies ligeramente más claras **a partir del mismo `surface` del perfil**, sin introducir colores nuevos de marca.

**Por qué:** Muchos componentes M3 (`OutlinedTextField`, `Slider`, `ModalBottomSheet`, `ListItem`, chips) leen `surfaceVariant`, `onSurfaceVariant` u otros roles. Si no se definen, el esquema oscuro por defecto deja bordes y fondos en negro o poco contrastados. Con el esquema completo, la UI se ve coherente con el tema Bluewave existente.

---

## 2. Tipografía M3 explícita

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/theme/Type.kt`

**Qué:** La `Typography` define de forma explícita `displayLarge` … `labelSmall` con tamaños, interlineado y `letterSpacing` alineados con la escala Material 3, usando `FontFamily.Default`.

**Por qué:** Antes solo se personalizaba `bodyLarge`; el resto dependía de valores implícitos. Una escala explícita mejora la jerarquía visual en títulos, pestañas y listas.

**Nota respecto a [mejoras.md](mejoras.md):** el plan sugería fuentes descargables (p. ej. Nunito). Para no añadir binarios `.ttf` ni configuración extra en esta fase, se mantuvo la familia del sistema; el beneficio principal aquí es la **escala tipográfica completa**, no una nueva familia.

---

## 3. `ModeChip` → `FilterChip` y `PrimaryButton` → `Button` M3

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/theme/components.kt`

**Qué:**

- `ModeChip` conserva el nombre para no romper llamadas (`AnalyzeScreen`, `ThemeCustomizerScreen`), pero internamente usa **`FilterChip`** con icono de check cuando está seleccionado, ripple y estados `enabled`.
- `PrimaryButton` usa **`Button`** de M3 con forma en píldora, colores del `colorScheme`, elevación y estados deshabilitados correctos (sin duplicar `Surface` + gradiente manual).

**Por qué:** Mejor accesibilidad (objetivo táctil y semántica), menos código custom y comportamiento de deshabilitado más fiable que el `Surface` + `clickable` anterior.

---

## 4. Barra inferior de navegación y `Scaffold`

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/MainActivity.kt`

**Qué:**

- `NavHost` va dentro de un **`Scaffold`** con `innerPadding` aplicado al `NavHost` (`Modifier.padding(innerPadding)`).
- **`NavigationBar`** con tres destinos: Descargar (`analyze`), Reproductor (`player`), Perfil (`profile`), con iconos y etiquetas desde strings (`nav_download`, `nav_player`, `nav_profile`).
- La barra solo se muestra cuando la ruta actual es una de esas tres (no en splash, personalizador de tema ni playlist con argumentos).

**Por qué:** Patrón estándar de apps de consumo: navegación predecible sin depender de botones sueltos en cada pantalla.

**Ajustes de pantallas:**

- **`AnalyzeScreen`:** se eliminó el botón textual «Ir al reproductor» del encabezado; el acceso es la barra inferior.
- **`PlayerScreen`:** se eliminaron el acceso rápido «Downloader» y el icono de perfil del encabezado; la navegación es la misma barra inferior.

---

## 5. Snackbars en lugar de Toasts (UI)

**Archivos:**

- `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/LocalSnackbarHost.kt` — `CompositionLocal` `LocalSnackbarHostState`.
- `MainActivity.kt` — `remember { SnackbarHostState() }`, `CompositionLocalProvider`, `Scaffold(snackbarHost = { SnackbarHost(...) })`.
- `AnalyzeScreen.kt` — mensajes de playlist (inicio y resumen de descarga) vía `showSnack(...)`.
- `PlayerScreen.kt` — helper `showSnack(message, long = false)`; permiso de audio denegado usa duración larga; el resto de feedback que antes era `Toast` pasa por el mismo host.

**Por qué:** Los snackbars respetan el tema Material, se integran con el `Scaffold` (encima de la `NavigationBar` cuando aplica) y son más consistentes que los toasts del sistema. **No** se tocaron los `Toast` dentro de `SongDownloadService` (servicio en segundo plano), alineado con la restricción de no cambiar el comportamiento de servicios.

**MainActivity:** al fallar abrir la carpeta de música, el mensaje pasa a `snackbarHostState.showSnackbar` con el string `profile_folder_open_failed`.

---

## 6. Edge-to-edge

**Archivo:** `MainActivity.kt` (`enableEdgeToEdge()` en `onCreate`)

**Qué:** Ya estaba activado; se mantiene. El `Scaffold` de Material 3 reparte el contenido con `innerPadding` para respetar barras del sistema en combinación con el modo edge-to-edge.

**Por qué:** Contenido no queda oculto bajo la status bar / navigation bar de forma incorrecta cuando el sistema aplica insets.

---

## 7. Pestañas en descargas: `PrimaryTabRow` + `Tab`

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/analyze/AnalyzeScreen.kt`

**Qué:** Los modos Playlist / Canción usan **`PrimaryTabRow`** y **`Tab`** en lugar de dos `ModeChip` en fila.

**Por qué:** Es el patrón M3 para cambiar de “vista” dentro de la misma pantalla, con indicador de pestaña y mejor semántica que chips sueltos.

---

## 8. Perfil: `ListItem` Material 3

**Archivo:** `composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat/ui/feature/profile/ProfileScreen.kt`

**Qué:** Las filas de opciones usan **`ListItem`** con `leadingContent` (icono por fila: idioma, carpeta, explorador, paleta, texto), `headlineContent`, `supportingContent` opcional y flecha en `trailingContent`. El modificador `clickable` va en el `ListItem` con colores de contenedor transparentes.

**Por qué:** Alineación visual, altura de fila y ripples coherentes con Material; iconos ayudan a escanear la lista.

---

## 9. Campo de URL de playlist: `supportingText` + `isError`

**Archivo:** `AnalyzeScreen.kt`

**Qué:** El `OutlinedTextField` de la URL en modo playlist usa `isError` y `supportingText` cuando hay `playlistInputError`. Se eliminó el bloque duplicado que mostraba el mismo error como `Text` aparte bajo el formulario.

**Por qué:** TalkBack y el propio M3 anuncian el error ligado al campo; menos duplicación de UI.

Se añadió **`leadingIcon`** con `Icons.Filled.Link` para reforzar el significado del campo.

---

## 10. Strings nuevos

**Archivo:** `composeApp/src/androidMain/res/values/strings.xml`

- `nav_download`, `nav_player`, `nav_profile` — etiquetas de la barra inferior.
- `profile_folder_open_failed` — mensaje al no poder abrir la carpeta desde perfil.

---

## 11. API de pantallas tocada

| Pantalla / API | Cambio |
|----------------|--------|
| `AnalyzeScreen(themeName, onOpenPlayer)` | Ahora **`AnalyzeScreen()`** sin parámetros no usados. |
| `PlayerScreen(onOpenProfile, onOpenDownloader)` | Ahora **`PlayerScreen()`**; navegación por `MainActivity`. |

---

## Resumen de archivos modificados o nuevos

| Archivo | Rol |
|---------|-----|
| `LocalSnackbarHost.kt` | `CompositionLocal` del `SnackbarHostState`. |
| `MainActivity.kt` | `Scaffold`, `NavigationBar`, `SnackbarHost`, `CompositionLocalProvider`, llamadas a pantallas actualizadas. |
| `Theme.kt` | `darkColorScheme` ampliado. |
| `Type.kt` | Tipografía M3 completa. |
| `components.kt` | `FilterChip` / `Button`. |
| `AnalyzeScreen.kt` | Tabs M3, snackbars, campo URL con error integrado, sin cabecera «Ir al reproductor». |
| `ProfileScreen.kt` | `ListItem` + iconos. |
| `PlayerScreen.kt` | Snackbars, cabecera simplificada, firma sin callbacks de navegación. |
| `strings.xml` | Cadenas de navegación y error de carpeta. |

---

*Documento generado al cerrar la implementación de la Fase 1 — Mayo 2026*
