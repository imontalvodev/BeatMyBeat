package com.imontalvodev.beatmybeat

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/** Snackbar compartido desde el [androidx.compose.material3.Scaffold] de [MainActivity]. */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("LocalSnackbarHostState: envuelve la UI con CompositionLocalProvider(SnackbarHostState).")
}
