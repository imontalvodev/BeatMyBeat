# BeatMyBeat — Plan de mejoras UI

> Análisis realizado sobre el código fuente actual (`androidMain`).
> Restricciones respetadas: **no se modifica la paleta de colores ni la lógica de servicios**.
> Las mejoras se agrupan por librería y fase de implementación, de menor a mayor complejidad.

> ⚠️ **Documento parcialmente histórico (actualizado jun. 2026).** Tras las Fases A y B de
> [`optimizacion-limpieza.md`](./optimizacion-limpieza.md), parte del diagnóstico original quedó obsoleto.
> El diagnóstico de abajo está corregido al estado real; las propuestas de las Fases 1–3 se mantienen
> como ideas de pulido pendiente (equivalen a la **Fase D — Pulido UI** del plan de optimización).

---

## Estado actual (diagnóstico rápido)

| Área | Estado |
|---|---|
| Compose Multiplatform | App **solo Android**. El target iOS y el template `commonMain`/`iosApp` se eliminaron en la Fase A. |
| Material 3 | Parcialmente aprovechado. Varios tokens de color y componentes estándar no se usan. |
| Typography | Solo `bodyLarge` sobreescrito. El resto usa valores por defecto de M3. Sin fuente personalizada. |
| Coil | **Instalado y en uso.** El artwork se carga con `AsyncImage` (Coil) sobre el `ArtworkCache`/`PlaybackArtworkHelper`. |
| Shimmer | No usa la librería externa, pero ya hay skeletons propios (`TrackListSkeleton`, `LoadingSkeletons.kt`). |
| Lottie | **No instalado.** |
| Accompanist | **No instalado.** No hay control de barras del sistema ni manejo declarativo de permisos. |
| Navegación | `NavHost` plano sin transiciones animadas. `PlaylistScreen` **eliminada** (las playlists viven dentro del reproductor). |
| Feedback del usuario | **Convención actual:** `Toast` en pantallas de contenido (`PlayerScreen`, `AnalyzeScreen`); `Snackbar` global solo en `MainActivity` para flujos de app (p. ej. permisos). |

> Nota: la mejora **1.6 (Toast → Snackbar)** ya **no aplica**: se decidió la convención inversa (Toast en
> pantallas, Snackbar solo global). Ver Fase B.9 en `optimizacion-limpieza.md`.

---

## Fase 1 — Base sólida (Compose Multiplatform + Material 3)

### 1.1 Completar el esquema de color M3

**Archivo:** `ui/theme/Theme.kt`

El `darkColorScheme` actual solo mapea 6 de los ~30 roles de color de M3. Los componentes internos de M3 (como `OutlinedTextField`, `Slider`, `ModalBottomSheet`, `Card`) usan roles como `surfaceVariant`, `onSurfaceVariant`, `surfaceContainerHigh`, `primaryContainer`, `onPrimaryContainer`, etc. Al no mapearlos, esos componentes caen al color negro por defecto de `darkColorScheme`, lo que produce bordes y fondos incorrectos.

**Mejora concreta:** añadir los roles faltantes al `darkColorScheme` usando los colores del perfil activo:

```kotlin
val baseScheme = darkColorScheme(
    primary            = themeProfile.primary,
    onPrimary          = Color.Black,
    primaryContainer   = themeProfile.primaryVariant.copy(alpha = 0.25f),
    onPrimaryContainer = themeProfile.primary,
    secondary          = themeProfile.secondary,
    onSecondary        = Color.Black,
    secondaryContainer = themeProfile.secondary.copy(alpha = 0.18f),
    onSecondaryContainer = themeProfile.secondary,
    background         = themeProfile.backgroundBottom,
    onBackground       = themeProfile.onSurface,
    surface            = themeProfile.surface,
    onSurface          = themeProfile.onSurface,
    surfaceVariant     = themeProfile.surface.copy(alpha = 0.85f),
    onSurfaceVariant   = themeProfile.onSurfaceMuted,
    surfaceContainer   = themeProfile.surface,
    surfaceContainerHigh = themeProfile.surface.copy(red = (themeProfile.surface.red + 0.03f).coerceAtMost(1f)),
    outline            = themeProfile.primary.copy(alpha = 0.40f),
    outlineVariant     = themeProfile.onSurfaceMuted.copy(alpha = 0.20f),
    error              = Color(0xFFCF6679),
    onError            = Color.Black,
)
```

**Impacto:** los `OutlinedTextField`, `Card`, `ModalBottomSheet` y `Slider` del reproductor mostrarán bordes y fondos coherentes con el tema sin cambiar la paleta.

---

### 1.2 Sistema de tipografía completo con fuente personalizada

**Archivo:** `ui/theme/Type.kt`

Solo `bodyLarge` está sobreescrito. Toda la escala tipográfica (headline, title, label, display, body) usa Roboto con los pesos y tamaños por defecto de M3.

**Mejora:** usar **Nunito** o **Inter** (disponibles en Google Fonts vía `DownloadableFonts` o como asset). Estas fuentes dan un look más moderno y alineado con apps de música como Spotify/Tidal sin afectar la legibilidad.

```kotlin
// En Type.kt — ejemplo con Nunito
val NunitoFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium,  FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold,    FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

val Typography = Typography(
    displayLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 57.sp),
    headlineLarge = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 28.sp),
    titleLarge    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 22.sp),
    titleMedium   = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, letterSpacing = 0.1.sp),
    titleSmall    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,    fontSize = 14.sp),
    bodyLarge     = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,    fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp, letterSpacing = 0.5.sp),
)
```

**Impacto:** jerarquía visual clara y consistente en todas las pantallas.

---

### 1.3 Reemplazar `ModeChip` con `FilterChip` de M3

**Archivo:** `ui/theme/components.kt`, usado en `AnalyzeScreen` y `PlayerScreen`

El componente `ModeChip` actual es un `Text` con `background + clickable`. Le faltan: estado `selected` visual con checkmark, ripple, touch target mínimo de 48dp, y semántica de accesibilidad.

**Mejora:** sustituir por `FilterChip` de M3:

```kotlin
// Antes (ModeChip custom)
ModeChip(text = "Playlist", selected = mode == "playlist", onClick = { mode = "playlist" })

// Después (FilterChip M3)
FilterChip(
    selected = mode == "playlist",
    onClick  = { mode = "playlist" },
    label    = { Text("Playlist") },
    leadingIcon = if (mode == "playlist") {
        { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
    } else null,
)
```

**Impacto:** feedback visual de selección más claro, accesibilidad mejorada, y coherencia con M3.

---

### 1.4 Reemplazar `PrimaryButton` con `Button` de M3

**Archivo:** `ui/theme/components.kt`

El `PrimaryButton` actual replica manualmente con `Surface + Brush + clickable` lo que M3 `Button` ya proporciona (ripple, touch target, estado disabled, elevation). La implementación actual tiene un bug visual: aplica `background` con el gradiente y también `Surface` con `color = Color.Transparent`, duplicando capas innecesariamente.

**Mejora:** usar `Button` de M3 con forma sobreescrita y mantener el gradiente solo donde sea imprescindible:

```kotlin
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier.height(56.dp),
        shape    = RoundedCornerShape(999.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor    = MaterialTheme.colorScheme.primary,
            contentColor      = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}
```

**Impacto:** elimina código duplicado, corrige el estado disabled, y añade ripple accesible.

---

### 1.5 Añadir `NavigationBar` de M3 + `Scaffold` en `MainActivity`

**Archivo:** `MainActivity.kt`

Actualmente no existe barra de navegación: el usuario navega entre Descargar/Player/Perfil solo a través de botones dispersos en cada pantalla. La arquitectura de navegación es frágil y no cumple ningún patrón estándar de Material.

**Mejora:** añadir una `NavigationBar` persistente en el `Scaffold` principal:

```kotlin
@Composable
fun AppShell(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("analyze", "player", "profile")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentRoute == "analyze",
                        onClick  = { navController.navigate("analyze") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Filled.Download, contentDescription = "Descargar") },
                        label    = { Text("Descargar") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "player",
                        onClick  = { navController.navigate("player") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Filled.MusicNote, contentDescription = "Reproductor") },
                        label    = { Text("Reproductor") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "profile",
                        onClick  = { navController.navigate("profile") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Filled.Person, contentDescription = "Perfil") },
                        label    = { Text("Perfil") },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(it) },
    ) { innerPadding -> /* NavHost */ }
}
```

**Impacto:** UX estándar de apps de música, navegación predecible, elimina botones de navegación hardcodeados en cada pantalla.

---

### 1.6 Reemplazar `Toast` con `Snackbar` de M3

**Archivos:** `PlayerScreen.kt`, `AnalyzeScreen.kt`

Hay al menos 8 llamadas a `Toast.makeText()` para comunicar errores y estados al usuario. Los `Toast` son opacas, no se pueden personalizar con el tema, y en Android 12+ pueden quedar enterradas detrás de otras notificaciones del sistema.

**Mejora:** usar `SnackbarHostState` desde el `Scaffold` y emitir `Snackbar` con acción cuando sea necesario:

```kotlin
// En lugar de:
Toast.makeText(context, "Descarga iniciada en segundo plano.", Toast.LENGTH_SHORT).show()

// Usar:
scope.launch { snackbarHostState.showSnackbar("Descarga iniciada en segundo plano.") }
```

**Impacto:** mensajes coherentes con el tema, con posibilidad de añadir botón de acción (p.ej. "Abrir reproductor").

---

### 1.7 Implementar edge-to-edge + control de barras del sistema

**Archivo:** `MainActivity.kt`

La app no llama a `enableEdgeToEdge()` ni maneja `WindowInsets`. El contenido queda recortado por la barra de estado y la barra de navegación del sistema, especialmente en el reproductor expandido.

**Mejora:**

```kotlin
// En MainActivity.onCreate():
enableEdgeToEdge()

// En los Scaffold/Surface de cada pantalla:
Modifier.windowInsetsPadding(WindowInsets.systemBars)
// o en el Scaffold principal:
contentWindowInsets = WindowInsets.systemBars
```

Para el reproductor expandido (full-screen), usar `WindowInsets.statusBars` para solapar la status bar con el artwork, efecto que usan Spotify y Apple Music.

**Impacto:** experiencia visual inmersiva, especialmente en el reproductor.

---

### 1.8 Reemplazar los tabs de `AnalyzeScreen` con `PrimaryTabRow`

**Archivo:** `AnalyzeScreen.kt` (líneas 140–154)

Los tabs Playlist/Canción usan chips horizontales con `ModeChip`. M3 tiene `PrimaryTabRow` + `Tab` con indicador animado, que es el patrón correcto para cambiar entre modos de pantalla.

```kotlin
val tabs = listOf("Playlist", "Canción")
val selectedIndex = if (mode == "playlist") 0 else 1

PrimaryTabRow(selectedTabIndex = selectedIndex) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = selectedIndex == index,
            onClick  = { mode = if (index == 0) "playlist" else "song" },
            text     = { Text(title) },
        )
    }
}
```

**Impacto:** indicador de selección animado, semántica de accesibilidad correcta, patrón estándar de M3.

---

### 1.9 Mejorar `ProfileScreen` con `ListItem` de M3

**Archivo:** `ProfileScreen.kt` (función `ProfileOption`)

Las opciones del perfil son `Box + clickable + Column`. M3 tiene `ListItem` con soporte de `leadingContent`, `trailingContent`, `overlineContent` y ripple integrado.

```kotlin
@Composable
private fun ProfileOption(label: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent  = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        supportingContent= subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent   = icon?.let { { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
        trailingContent  = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier         = Modifier.clickable { onClick() },
    )
}
```

**Impacto:** look profesional inmediato, touch target correcto, iconos de sección claros.

---

### 1.10 Usar `OutlinedTextField` con `supportingText` e `isError`

**Archivo:** `AnalyzeScreen.kt`

Los errores de validación (`playlistInputError`) se muestran como `Text` suelto debajo del campo. M3 `OutlinedTextField` tiene `supportingText` e `isError` para esto de forma integrada y accesible.

```kotlin
OutlinedTextField(
    value        = playlistUrl,
    onValueChange= { playlistUrl = it; playlistInputError = null },
    label        = { Text(stringResource(R.string.analyze_playlist_url_label)) },
    isError      = playlistInputError != null,
    supportingText = playlistInputError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
    leadingIcon  = { Icon(Icons.Filled.Link, contentDescription = null) },
    singleLine   = true,
    modifier     = Modifier.fillMaxWidth(),
)
```

**Impacto:** validación visual integrada, mejor accesibilidad (TalkBack anuncia el error), menos código.

---

## Fase 2 — Experiencia visual (Coil + Palette API)

### 2.1 Integrar Coil para carga de imágenes

**Dependencia a añadir:**

```toml
# gradle/libs.versions.toml
coil = "2.7.0"
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
```

**Archivos afectados:** `AnalyzeScreen.kt` (SuggestionThumbnail), `PlayerScreen.kt` (artwork)

#### 2.1a `SuggestionThumbnail` en `AnalyzeScreen`

El componente `SuggestionThumbnail` carga imágenes con un `OkHttpClient` manual, sin cache en disco, sin placeholder, sin manejo de errores visuales y sin crossfade. Además crea una nueva instancia de `OkHttpClient` en cada composición.

```kotlin
// Antes: ~40 líneas con OkHttpClient + LaunchedEffect + Bitmap + remember
@Composable
private fun SuggestionThumbnail(url: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale       = ContentScale.Crop,
        placeholder        = painterResource(R.drawable.ic_music_placeholder),
        error              = painterResource(R.drawable.ic_music_placeholder),
        modifier           = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    )
}
```

**Impacto:** cache en memoria y disco automático, crossfade, placeholder, ~40 líneas eliminadas.

#### 2.1b Artwork en `PlayerScreen`

El artwork se carga con `MediaMetadataRetriever` o descargando el thumbnail de YouTube con OkHttp. Coil soporta `file://` y URLs sin configuración extra, con el mismo cache unificado.

```kotlin
AsyncImage(
    model            = artworkUri ?: R.drawable.ic_music_placeholder,
    contentDescription = "Portada de ${currentTrack?.title}",
    contentScale     = ContentScale.Crop,
    placeholder      = painterResource(R.drawable.ic_music_placeholder),
    modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
)
```

Para el fondo borroso del reproductor expandido, usar `BlurTransformation` de Coil:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(artworkUri)
        .transformations(BlurTransformation(context, radius = 25f, sampling = 4f))
        .build(),
    contentDescription = null,
    contentScale       = ContentScale.Crop,
    alpha              = 0.35f,
    modifier           = Modifier.fillMaxSize(),
)
```

---

### 2.2 Integrar Palette API para tema dinámico por canción

**Dependencia a añadir:**

```toml
# gradle/libs.versions.toml
palette = "1.0.0"
androidx-palette = { module = "androidx.palette:palette-ktx", version.ref = "palette" }
```

**Archivos afectados:** `PlayerScreen.kt` (`ExpandedPlayerOverlay`), `PlayerViewModel.kt`

Cuando el reproductor expandido muestra el artwork, la Palette API puede extraer el color dominante del bitmap para colorear dinámicamente el fondo del overlay, sin modificar el perfil de tema global.

```kotlin
// En PlayerScreen / ExpandedPlayerOverlay
var dominantColor by remember { mutableStateOf(palette.backgroundBottom) }

LaunchedEffect(currentArtwork) {
    val bmp = currentArtwork ?: return@LaunchedEffect
    withContext(Dispatchers.Default) {
        Palette.from(bmp).generate { p ->
            p?.let { palette ->
                val swatch = palette.darkVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                swatch?.rgb?.let { rgb ->
                    dominantColor = Color(rgb).copy(alpha = 0.85f)
                }
            }
        }
    }
}

// Usar dominantColor como fondo animado del overlay:
val animatedBg by animateColorAsState(
    targetValue  = dominantColor,
    animationSpec = tween(durationMillis = 600),
    label        = "player_bg_color",
)
Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
    colors = listOf(animatedBg, palette.backgroundBottom)
)))
```

**Impacto:** efecto "Now Playing" premium similar a Spotify/Apple Music. El fondo del reproductor cambia suavemente con cada canción, usando los colores del artwork pero **sin tocar la paleta global del tema**.

---

## Fase 3 — Polish y detalles

### 3.1 Shimmer para estados de carga

**Dependencia a añadir:**

```toml
# gradle/libs.versions.toml
shimmer = "1.3.1"
compose-shimmer = { module = "com.valentinilk.shimmer:compose-shimmer", version.ref = "shimmer" }
```

#### 3.1a Skeleton de carga en `PlayerScreen` (biblioteca)

Cuando `deviceTracks` está vacío y `hasAudioPermission` es true, se puede mostrar un skeleton en lugar de pantalla en blanco:

```kotlin
@Composable
fun TrackListSkeleton() {
    val shimmerInstance = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shimmer(shimmerInstance)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(0.65f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Box(Modifier.fillMaxWidth(0.40f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}
```

#### 3.1b Skeleton en `AnalyzeScreen` mientras se buscan sugerencias

```kotlin
// Reemplazar el texto "Buscando canciones..." por:
if (searchingSuggestions) {
    SuggestionListSkeleton() // Similar al TrackListSkeleton pero más compacto
}
```

**Impacto:** percepción de velocidad mejorada, carga más elegante y professional.

---

### 3.2 Animaciones nativas de Compose

No se necesita Lottie para las mejoras de animación más impactantes; Compose tiene las herramientas built-in.

#### 3.2a Transiciones de navegación con `AnimatedNavHost`

```kotlin
// Reemplazar NavHost por AnimatedNavHost (accompanist) o
// usar NavHost con enterTransition/exitTransition de Compose Animation:
NavHost(
    navController    = navController,
    startDestination = "splash",
    enterTransition  = { slideInHorizontally { it } + fadeIn() },
    exitTransition   = { slideOutHorizontally { -it } + fadeOut() },
    popEnterTransition  = { slideInHorizontally { -it } + fadeIn() },
    popExitTransition   = { slideOutHorizontally { it } + fadeOut() },
)
```

#### 3.2b `AnimatedVisibility` para la mini barra del reproductor

La `MiniPlayerBar` aparece/desaparece sin animación. 

```kotlin
AnimatedVisibility(
    visible = currentTrack != null,
    enter   = slideInVertically { it } + fadeIn(),
    exit    = slideOutVertically { it } + fadeOut(),
) {
    MiniPlayerBar(/* ... */)
}
```

#### 3.2c `AnimatedContent` para el cambio de sección en `PlayerScreen`

Cuando el usuario cambia de Songs → Favorites → Playlist, el contenido aparece de golpe.

```kotlin
AnimatedContent(
    targetState   = selectedSection,
    transitionSpec = { fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally() },
    label         = "section_content",
) { section ->
    // LazyColumn de la sección correspondiente
}
```

#### 3.2d Crossfade de artwork en el reproductor

```kotlin
Crossfade(
    targetState   = currentArtwork,
    animationSpec = tween(durationMillis = 400),
    label         = "artwork_crossfade",
) { artwork ->
    if (artwork != null) {
        Image(bitmap = artwork.asImageBitmap(), /* ... */)
    } else {
        ArtworkPlaceholder()
    }
}
```

#### 3.2e Animación del botón Play/Pause

```kotlin
val playScale by animateFloatAsState(
    targetValue   = if (isPlaying) 0.92f else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label         = "play_button_scale",
)
IconButton(
    onClick   = { /* toggle play */ },
    modifier  = Modifier.scale(playScale),
) { /* icono */ }
```

---

### 3.3 Pantalla de estado vacío en `PlayerScreen`

Cuando no hay pistas y se tiene permiso, la pantalla muestra nada. Añadir un estado vacío ilustrado:

```kotlin
if (displayedTracks.isEmpty() && !isLoading) {
    Column(
        modifier              = Modifier.fillMaxSize(),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector       = Icons.Filled.LibraryMusic,
            contentDescription= null,
            modifier          = Modifier.size(72.dp),
            tint              = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Tu biblioteca está vacía",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Descarga música desde la pestaña Descargar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onOpenDownloader) {
            Text("Ir a Descargar")
        }
    }
}
```

---

### 3.4 Mejoras de accesibilidad

**Archivos:** todos los screens

| Problema | Mejora |
|---|---|
| Botones icon-only sin `contentDescription` (play, pause, shuffle, repeat, skip) | Añadir `contentDescription` con string resource en todos los `IconButton` |
| `Slider` del reproductor sin semántica de rango | `Modifier.semantics { contentDescription = "Posición: $currentTime de $totalTime" }` |
| Touch targets menores de 48dp en chips y botones pequeños | `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` |
| `ModeChip` y botones de acción usan `clickable` sin `role` | Añadir `Modifier.semantics { role = Role.Button }` |
| `SuggestionThumbnail` sin descripción | Pasar título de la canción como `contentDescription` |

---

### 3.5 Lottie Compose (opcional, mayor impacto visual)

**Dependencia:**

```toml
lottie-compose = { module = "com.airbnb.android:lottie-compose", version = "6.4.0" }
```

Casos de uso prioritarios para BeatMyBeat:

| Animación | Pantalla | Recurso sugerido (LottieFiles) |
|---|---|---|
| Visualizador de ecualizador (barras pulsando) | `MiniPlayerBar` + `ExpandedPlayerOverlay` | `music-equalizer` |
| Estado vacío biblioteca | `PlayerScreen` empty state | `empty-music-library` |
| Descarga en progreso | `AnalyzeScreen` | `downloading-file` |
| Éxito al completar descarga | `AnalyzeScreen` | `download-complete` |

```kotlin
// Ejemplo: ecualizador en MiniPlayerBar cuando isPlaying
val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.equalizer))
val progress    by animateLottieCompositionAsState(
    composition = composition,
    isPlaying   = isPlaying,
    iterations  = LottieConstants.IterateForever,
    speed       = 1.2f,
)
LottieAnimation(
    composition = composition,
    progress    = { progress },
    modifier    = Modifier.size(24.dp),
)
```

---

### 3.6 Accompanist — permisos y control de barras del sistema

**Dependencias:**

```toml
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version = "0.36.0" }
```

#### 3.6a Manejo declarativo del permiso de audio en `PlayerScreen`

Actualmente `PlayerScreen` gestiona `rememberLauncherForActivityResult` manualmente (~20 líneas). Con Accompanist:

```kotlin
val audioPermissionState = rememberPermissionState(
    permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                 else Manifest.permission.READ_EXTERNAL_STORAGE
)

when {
    audioPermissionState.status.isGranted -> { /* mostrar biblioteca */ }
    audioPermissionState.status.shouldShowRationale -> {
        PermissionRationaleCard(onRequest = { audioPermissionState.launchPermissionRequest() })
    }
    else -> { audioPermissionState.launchPermissionRequest() }
}
```

---

## Resumen priorizado

| # | Mejora | Librería | Complejidad | Impacto visual |
|---|---|---|---|---|
| 1 | Completar esquema de color M3 | Material 3 | Baja | Alto |
| 2 | Tipografía completa + fuente custom | Material 3 | Baja | Alto |
| 3 | `FilterChip` en lugar de `ModeChip` | Material 3 | Baja | Medio |
| 4 | `Button` M3 en lugar de `PrimaryButton` custom | Material 3 | Baja | Medio |
| 5 | `NavigationBar` + `Scaffold` principal | Material 3 | Media | Muy alto |
| 6 | `Snackbar` en lugar de `Toast` | Material 3 | Baja | Medio |
| 7 | Edge-to-edge + WindowInsets | Compose | Baja | Alto |
| 8 | `PrimaryTabRow` en AnalyzeScreen | Material 3 | Baja | Medio |
| 9 | `ListItem` en ProfileScreen | Material 3 | Baja | Medio |
| 10 | `OutlinedTextField` con `supportingText` | Material 3 | Baja | Bajo |
| 11 | Coil para artwork y thumbnails | Coil | Media | Alto |
| 12 | Palette API para fondo dinámico del player | Palette API | Media | Muy alto |
| 13 | Shimmer skeletons en lista y búsqueda | Shimmer | Media | Alto |
| 14 | Transiciones de navegación | Compose Animation | Baja | Alto |
| 15 | `AnimatedVisibility` mini player | Compose Animation | Baja | Medio |
| 16 | Crossfade artwork | Compose Animation | Baja | Medio |
| 17 | Animación botón play (spring) | Compose Animation | Baja | Medio |
| 18 | Pantalla estado vacío biblioteca | Compose | Media | Alto |
| 19 | Accesibilidad (`contentDescription`, roles) | Compose | Baja | Crítico |
| 20 | Lottie (ecualizador, estados) | Lottie | Media | Alto |
| 21 | Accompanist permisos declarativo | Accompanist | Media | Bajo |

---

*Documento generado para BeatMyBeat — Mayo 2026*
