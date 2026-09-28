package com.imontalvodev.beatmybeat.ui.feature.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
import com.imontalvodev.beatmybeat.service.PlaybackService
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Petición de abrir el reproductor expandido desde fuera de Biblioteca. El reproductor vive en
 * [PlayerScreen] (cola, letras, karaoke); las demás pestañas solo navegan y dejan la petición aquí.
 */
internal object PlayerExpandRequest {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}

/** Hay algo cargado en el reproductor (el mediaId es la URI de la pista). */
@Composable
fun rememberHasActivePlayback(): Boolean {
    val state by PlaybackService.state.collectAsState()
    return state.currentMediaId.isNotBlank()
}

/**
 * Mini reproductor para Descargar y Ajustes. Lee directamente el estado del servicio: no necesita
 * la biblioteca cargada. Tocarlo abre el reproductor completo en Biblioteca.
 */
@Composable
fun GlobalMiniPlayer(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by PlaybackService.state.collectAsState()
    val mediaId = state.currentMediaId

    var artwork by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(mediaId) {
        if (mediaId.isBlank()) {
            artwork = null
            return@LaunchedEffect
        }
        artwork = ArtworkCache.get(mediaId, PLAYER_ARTWORK_MAX_PX)?.takeUnless { it.isRecycled }
        if (artwork != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            PlaybackArtworkHelper.resolveArtworkBytes(context, mediaId)
                ?.takeIf { it.isNotEmpty() }
                ?.let { BitmapDecoding.decodeSampled(it, PLAYER_ARTWORK_MAX_PX) }
        }
        artwork = loaded
        if (loaded != null) ArtworkCache.put(mediaId, PLAYER_ARTWORK_MAX_PX, loaded)
    }

    val durationMs = state.durationMs.coerceAtLeast(0L)
    val position = if (durationMs > 0) state.positionMs.toFloat() / durationMs else 0f
    MiniPlayerBar(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        title = state.currentTitle.ifBlank { null },
        artist = state.currentArtist,
        isPlaying = state.isPlaying,
        position = position,
        artwork = artwork,
        progressAccessibilityLabel = stringResource(
            R.string.player_slider_a11y,
            formatMs(state.positionMs.toInt()),
            formatMs(durationMs.toInt()),
        ),
        onTogglePlay = {
            context.sendPlaybackForegroundAction(
                if (state.isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY,
            )
        },
        onNext = { context.sendPlaybackForegroundAction(PlaybackService.ACTION_NEXT) },
        onOpenExpanded = {
            PlayerExpandRequest.request()
            onOpenPlayer()
        },
    )
}
