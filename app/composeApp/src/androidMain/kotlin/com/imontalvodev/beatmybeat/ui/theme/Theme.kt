package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

val LocalBeatMyBeatThemeProfile = staticCompositionLocalOf { NeonMintProfile }

@Composable
fun currentBeatMyBeatThemeProfile(): BeatMyBeatThemeProfile = LocalBeatMyBeatThemeProfile.current

private fun Color.bumpChannels(delta: Float): Color = Color(
    red = (red + delta).coerceIn(0f, 1f),
    green = (green + delta).coerceIn(0f, 1f),
    blue = (blue + delta).coerceIn(0f, 1f),
    alpha = alpha,
)

@Composable
fun BeatMyBeatTheme(
    themeProfile: BeatMyBeatThemeProfile,
    content: @Composable () -> Unit,
) {
    val surfaceHigh = themeProfile.surface.bumpChannels(0.05f)
    val baseScheme = darkColorScheme(
        primary = themeProfile.primary,
        onPrimary = Color.Black,
        primaryContainer = themeProfile.primaryVariant.copy(alpha = 0.25f),
        onPrimaryContainer = themeProfile.primary,
        secondary = themeProfile.secondary,
        onSecondary = Color.Black,
        secondaryContainer = themeProfile.secondary.copy(alpha = 0.18f),
        onSecondaryContainer = themeProfile.secondary,
        tertiary = themeProfile.primaryVariant,
        onTertiary = Color.Black,
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
        surfaceVariant = themeProfile.surface.bumpChannels(0.06f),
        onSurfaceVariant = themeProfile.onSurfaceMuted,
        surfaceContainerLowest = themeProfile.surface,
        surfaceContainerLow = themeProfile.surface.bumpChannels(0.02f),
        surfaceContainer = themeProfile.surface.bumpChannels(0.03f),
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = themeProfile.surface.bumpChannels(0.08f),
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

    CompositionLocalProvider(LocalBeatMyBeatThemeProfile provides themeProfile) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = Typography,
            content = content,
        )
    }
}
