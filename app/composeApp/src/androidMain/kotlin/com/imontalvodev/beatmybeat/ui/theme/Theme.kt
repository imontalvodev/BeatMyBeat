package com.imontalvodev.beatmybeat.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class BeatMyBeatThemeProfile(
    val id: String,
    val name: String,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
)

val NeonMintProfile = BeatMyBeatThemeProfile(
    id = "builtin-neon",
    name = "Bluewave",
    backgroundTop = NeonBackgroundTop,
    backgroundBottom = NeonBackgroundBottom,
    primary = NeonPrimary,
    primaryVariant = NeonPrimaryVariant,
    secondary = NeonSecondary,
    surface = NeonSurface,
    onSurface = NeonOnSurface,
    onSurfaceMuted = NeonOnSurfaceMuted,
)

val CherryProfile = BeatMyBeatThemeProfile(
    id = "builtin-cherry",
    name = "Cherry",
    backgroundTop = CherryBackgroundTop,
    backgroundBottom = CherryBackgroundBottom,
    primary = CherryPrimary,
    primaryVariant = CherryPrimaryVariant,
    secondary = CherrySecondary,
    surface = CherrySurface,
    onSurface = CherryOnSurface,
    onSurfaceMuted = CherryOnSurfaceMuted,
)

val EstanqueProfile = BeatMyBeatThemeProfile(
    id = "builtin-estanque",
    name = "Estanque",
    backgroundTop = EstanqueBackgroundTop,
    backgroundBottom = EstanqueBackgroundBottom,
    primary = EstanquePrimary,
    primaryVariant = EstanquePrimaryVariant,
    secondary = EstanquePrimary,
    surface = EstanqueSurface,
    onSurface = EstanqueOnSurface,
    onSurfaceMuted = EstanqueOnSurfaceMuted,
)

val BrasaProfile = BeatMyBeatThemeProfile(
    id = "builtin-brasa",
    name = "Brasa",
    backgroundTop = BrasaBackgroundTop,
    backgroundBottom = BrasaBackgroundBottom,
    primary = BrasaPrimary,
    primaryVariant = BrasaPrimaryVariant,
    secondary = BrasaPrimary,
    surface = BrasaSurface,
    onSurface = BrasaOnSurface,
    onSurfaceMuted = BrasaOnSurfaceMuted,
)

val NocheProfile = BeatMyBeatThemeProfile(
    id = "builtin-noche",
    name = "Noche",
    backgroundTop = NocheBackgroundTop,
    backgroundBottom = NocheBackgroundBottom,
    primary = NochePrimary,
    primaryVariant = NochePrimaryVariant,
    secondary = NochePrimary,
    surface = NocheSurface,
    onSurface = NocheOnSurface,
    onSurfaceMuted = NocheOnSurfaceMuted,
)

/** Perfiles que vienen con la app, en el orden en que se ofrecen. */
val BuiltinProfiles = listOf(NeonMintProfile, EstanqueProfile, BrasaProfile, NocheProfile)

val LocalBeatMyBeatThemeProfile = staticCompositionLocalOf { NeonMintProfile }

@Composable
fun currentBeatMyBeatThemeProfile(): BeatMyBeatThemeProfile = LocalBeatMyBeatThemeProfile.current

private fun Color.bumpChannels(delta: Float): Color = Color(
    red = (red + delta).coerceIn(0f, 1f),
    green = (green + delta).coerceIn(0f, 1f),
    blue = (blue + delta).coerceIn(0f, 1f),
    alpha = alpha,
)

/** Tinta oscura de «Charca» para el texto que va sobre un relleno de acento claro. */
private val AccentInk = Color(0xFF031316)

/** El texto sobre [fill] es la tinta oscura o blanco, según cuál contraste más. */
private fun onAccent(fill: Color): Color =
    if (fill.luminance() > 0.3f) AccentInk else Color.White

/** Perfil con fondo claro (Estanque o uno personalizado): cambia esquema y barras del sistema. */
val BeatMyBeatThemeProfile.isLight: Boolean
    get() = backgroundBottom.luminance() > 0.5f

@Composable
fun BeatMyBeatTheme(
    themeProfile: BeatMyBeatThemeProfile,
    content: @Composable () -> Unit,
) {
    val isLight = themeProfile.isLight
    // En claro las capas se oscurecen respecto a la superficie; en oscuro se aclaran.
    fun Color.layer(delta: Float) = bumpChannels(if (isLight) -delta else delta)
    val surfaceHigh = themeProfile.surface.layer(0.05f)
    val accentFill = themeProfile.primary
    val baseScheme = (if (isLight) lightColorScheme() else darkColorScheme()).copy(
        primary = themeProfile.primary,
        onPrimary = onAccent(accentFill),
        primaryContainer = themeProfile.primaryVariant.copy(alpha = 0.25f),
        onPrimaryContainer = themeProfile.primary,
        secondary = themeProfile.secondary,
        onSecondary = onAccent(themeProfile.secondary),
        secondaryContainer = themeProfile.secondary.copy(alpha = 0.18f),
        onSecondaryContainer = themeProfile.secondary,
        tertiary = themeProfile.primaryVariant,
        onTertiary = onAccent(themeProfile.primaryVariant),
        tertiaryContainer = themeProfile.secondary.copy(alpha = 0.12f),
        onTertiaryContainer = themeProfile.onSurface,
        error = Color(0xFFCF6679),
        onError = Color.Black,
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD4),
        background = themeProfile.backgroundBottom,
        onBackground = themeProfile.onSurface,
        surface = themeProfile.surface,
        onSurface = themeProfile.onSurface,
        surfaceVariant = themeProfile.surface.layer(0.06f),
        onSurfaceVariant = themeProfile.onSurfaceMuted,
        surfaceContainerLowest = themeProfile.surface,
        surfaceContainerLow = themeProfile.surface.layer(0.02f),
        surfaceContainer = themeProfile.surface.layer(0.03f),
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = themeProfile.surface.layer(0.08f),
        outline = themeProfile.primary.copy(alpha = 0.40f),
        outlineVariant = themeProfile.onSurfaceMuted.copy(alpha = 0.20f),
        scrim = Color.Black,
        inverseSurface = themeProfile.onSurface,
        inverseOnSurface = themeProfile.surface,
        inversePrimary = themeProfile.primary,
        // Sin tinte: con Surface(color = Transparent) + fondo en modifier, un surfaceTint fuerte
        // en M3 1.10 puede aclarar capas y dejar texto/iconos casi iguales al fondo.
        surfaceTint = Color.Transparent,
    )

    // Barras del sistema: iconos oscuros sobre fondos claros (enableEdgeToEdge solo sigue el modo
    // del sistema, no el perfil elegido).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
        }
    }

    CompositionLocalProvider(LocalBeatMyBeatThemeProfile provides themeProfile) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = Typography,
            content = content,
        )
    }
}
