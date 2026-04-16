package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
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
    name = "Neon",
    backgroundTop = NeonBackgroundTop,
    backgroundBottom = NeonBackgroundBottom,
    primary = NeonPrimary,
    primaryVariant = NeonPrimaryVariant,
    secondary = NeonSecondary,
    surface = NeonSurface,
    onSurface = NeonOnSurface,
    onSurfaceMuted = NeonOnSurfaceMuted,
)

val CherryPulseProfile = BeatMyBeatThemeProfile(
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

val LocalBeatMyBeatThemeProfile = staticCompositionLocalOf { NeonMintProfile }

@Composable fun currentBeatMyBeatThemeProfile(): BeatMyBeatThemeProfile = LocalBeatMyBeatThemeProfile.current

@Composable
fun BeatMyBeatTheme(
    themeProfile: BeatMyBeatThemeProfile,
    content: @Composable () -> Unit,
) {
    val baseScheme = darkColorScheme(
        primary = themeProfile.primary,
        onPrimary = Color.Black,
        secondary = themeProfile.secondary,
        background = themeProfile.backgroundBottom,
        surface = themeProfile.surface,
        onSurface = themeProfile.onSurface,
    )

    CompositionLocalProvider(LocalBeatMyBeatThemeProfile provides themeProfile) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = Typography,
            content = content,
        )
    }
}