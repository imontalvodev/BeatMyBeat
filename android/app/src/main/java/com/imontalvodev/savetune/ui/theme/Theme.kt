package com.imontalvodev.savetune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

enum class SavetuneThemeMode {
    NeonMint,
    CherryPulse,
}

private val NeonMintDarkColorScheme = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = Color.Black,
    secondary = NeonSecondary,
    background = NeonBackgroundBottom,
    surface = NeonSurface,
    onSurface = NeonOnSurface,
)

private val CherryPulseDarkColorScheme = darkColorScheme(
    primary = CherryPrimary,
    onPrimary = Color.Black,
    secondary = CherrySecondary,
    background = CherryBackgroundBottom,
    surface = CherrySurface,
    onSurface = CherryOnSurface,
)

@Composable
fun rememberSavetuneThemeModeState(): MutableState<SavetuneThemeMode> =
    remember { mutableStateOf(SavetuneThemeMode.NeonMint) }

@Composable
fun SavetuneTheme(
    themeMode: SavetuneThemeMode,
    content: @Composable () -> Unit,
) {
    val baseScheme = when (themeMode) {
        SavetuneThemeMode.NeonMint -> NeonMintDarkColorScheme
        SavetuneThemeMode.CherryPulse -> CherryPulseDarkColorScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = Typography,
        content = content,
    )
}