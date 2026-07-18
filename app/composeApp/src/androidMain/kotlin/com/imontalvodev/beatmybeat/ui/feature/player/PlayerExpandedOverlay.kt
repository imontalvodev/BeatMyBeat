package com.imontalvodev.beatmybeat.ui.feature.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.provider.MediaStore
import androidx.media3.common.Player
import java.io.File
import java.net.URLDecoder
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.LyricsFetcher
import com.imontalvodev.beatmybeat.ui.network.LrcLine
import com.imontalvodev.beatmybeat.ui.network.LrcParser
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.Motion
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import com.imontalvodev.beatmybeat.ui.theme.TrackListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.playback.LocalPlaybackService
import com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
import com.imontalvodev.beatmybeat.service.PlaybackService
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Controles de grabación del Modo Karaoke (Fase F).
 *
 * Tres estados excluyentes: sin grabar (botón de grabar), grabando (tiempo + parar) y revisando
 * (guardar / descartar). La toma en revisión **existe en disco pero no está guardada**: descartar
 * la borra. Ver `PlayerViewModel.KaraokeRecordingState`.
 */
@Composable
private fun KaraokeRecordingControls(
    state: PlayerViewModel.KaraokeRecordingState,
    headphonesConnected: Boolean,
    savedTakeCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is PlayerViewModel.KaraokeRecordingState.Idle -> {
                    // Los auriculares son recomendables, no obligatorios: el aviso informa y se
                    // quita solo al conectarlos, pero nunca impide grabar.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onStart) {
                            Icon(
                                imageVector = Icons.Filled.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = stringResource(R.string.karaoke_record_start),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        AnimatedVisibility(visible = !headphonesConnected) {
                            Text(
                                text = stringResource(R.string.karaoke_headphones_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = Spacing.xs),
                            )
                        }
                        // Tomas ya guardadas de esta canción: la relación no está en el nombre
                        // del archivo sino en KaraokeRecordingIndex.
                        AnimatedVisibility(visible = savedTakeCount > 0) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.karaoke_saved_takes,
                                    savedTakeCount,
                                    savedTakeCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = Spacing.xs),
                            )
                        }
                    }
                }

                is PlayerViewModel.KaraokeRecordingState.Recording -> {
                    // El punto parpadea para que quede claro que el micro está abierto: grabar sin
                    // señal visible es de las cosas que más incomodan en una app.
                    val blink = rememberInfiniteTransition(label = "rec_blink")
                    val dotAlpha by blink.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(Motion.AMBIENT),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                        label = "rec_dot_alpha",
                    )
                    var elapsedMs by remember(state.session.startedAtMs) { mutableStateOf(0L) }
                    LaunchedEffect(state.session.startedAtMs) {
                        while (true) {
                            elapsedMs = System.currentTimeMillis() - state.session.startedAtMs
                            delay(250)
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(dotAlpha),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = formatMs(elapsedMs.toInt()),
                        style = AppText.meta,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    TextButton(onClick = onStop) {
                        Text(
                            text = stringResource(R.string.karaoke_record_stop),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                is PlayerViewModel.KaraokeRecordingState.Review -> {
                    TextButton(onClick = onDiscard) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.karaoke_record_discard),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md))
                    TextButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.karaoke_record_save),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tono y velocidad del Modo Karaoke (Fase E). Plegado por defecto: se abre desde la fila
 * compacta, que ya muestra los valores actuales para no obligar a desplegar solo por mirar.
 */
@Composable
private fun KaraokeTuningControls(
    pitchSemitones: Float,
    speed: Float,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val neutral = KaraokeTuning.isNeutral(pitchSemitones, speed)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (neutral) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.player_karaoke_tuning_summary,
                        KaraokeTuning.semitoneLabel(pitchSemitones),
                        KaraokeTuning.speedLabel(speed),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (neutral) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = !neutral) {
                TextButton(onClick = onReset) {
                    Text(
                        text = stringResource(R.string.player_karaoke_tuning_reset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val pitchA11y = stringResource(
                    R.string.player_karaoke_pitch_a11y,
                    KaraokeTuning.semitoneLabel(pitchSemitones),
                )
                Text(
                    text = stringResource(R.string.player_karaoke_pitch_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Slider(
                    value = pitchSemitones,
                    onValueChange = onPitchChange,
                    valueRange = KaraokeTuning.MIN_SEMITONES..KaraokeTuning.MAX_SEMITONES,
                    // 12 pasos internos = 13 posiciones (-6..+6): el tono siempre cae en un
                    // semitono exacto, no en valores intermedios desafinados.
                    steps = 11,
                    modifier = Modifier.semantics { contentDescription = pitchA11y },
                )

                val speedA11y = stringResource(
                    R.string.player_karaoke_speed_a11y,
                    KaraokeTuning.speedLabel(speed),
                )
                Text(
                    text = stringResource(R.string.player_karaoke_speed_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = PlaybackService.MIN_PLAYBACK_SPEED..PlaybackService.MAX_PLAYBACK_SPEED,
                    modifier = Modifier.semantics { contentDescription = speedA11y },
                )
            }
        }
    }
}

@Composable
internal fun ExpandedPlayerOverlay(
    modifier: Modifier,
    track: DeviceTrack?,
    artwork: Bitmap?,
    lyricsState: LyricsUiState,
    lyricsPositionMs: Long,
    isPlaying: Boolean,
    position: Float,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onSeekToLyricsPosition: (Long) -> Unit,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    canRefreshLyrics: Boolean,
    canDeleteLyrics: Boolean,
    onRefreshLyrics: () -> Unit,
    onDeleteLyrics: () -> Unit,
    karaokeMode: Boolean,
    onKaraokeModeChange: (Boolean) -> Unit,
    karaokePitchSemitones: Float,
    karaokeSpeed: Float,
    onKaraokePitchChange: (Float) -> Unit,
    onKaraokeSpeedChange: (Float) -> Unit,
    onResetKaraokeTuning: () -> Unit,
    karaokeRecording: PlayerViewModel.KaraokeRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    headphonesConnected: Boolean,
    savedTakeCount: Int,
) {
    val durationMs = track?.durationMs?.toInt()?.takeIf { it > 0 } ?: 0
    val expandedPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expanded_play_scale",
    )
    val overlayPalette = currentBeatMyBeatThemeProfile()
    val ctx = LocalContext.current
    var dominantTop by remember { mutableStateOf(overlayPalette.backgroundTop) }

    LaunchedEffect(artwork, track?.id, overlayPalette.id) {
        val bmp = artwork
        if (bmp == null) {
            dominantTop = overlayPalette.backgroundTop
            return@LaunchedEffect
        }
        val sw = withContext(Dispatchers.Default) {
            val pal = Palette.from(bmp).generate()
            pal.darkVibrantSwatch ?: pal.mutedSwatch ?: pal.dominantSwatch
        }
        dominantTop = sw?.let { s ->
            val raw = androidArgbIntToComposeColor(s.rgb).copy(alpha = 1f)
            expandedPlayerOverlayGradientTop(raw, overlayPalette.backgroundTop)
        } ?: overlayPalette.backgroundTop
    }

    val animatedTop by animateColorAsState(
        targetValue = dominantTop,
        animationSpec = tween(Motion.AMBIENT),
        label = "expanded_player_dominant",
    )
    val gradientBrush = remember(animatedTop, overlayPalette.backgroundBottom) {
        Brush.verticalGradient(
            colors = listOf(animatedTop, overlayPalette.backgroundBottom),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            Modifier
                .fillMaxSize()
                .background(overlayPalette.backgroundBottom),
        )
        if (artwork != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(artwork)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp)
                    .alpha(0.38f),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.fillMaxSize().background(gradientBrush))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        ) {
            val plainScroll = rememberScrollState()
            val readySyncedLrc = (lyricsState as? LyricsUiState.Ready)?.syncedLrc
            val syncedLines = remember(readySyncedLrc) {
                readySyncedLrc?.let { LrcParser.parse(it) }.orEmpty()
            }
            val hasSyncedLyrics = syncedLines.isNotEmpty()
            // Sin timestamps por palabra el resaltado avanza línea a línea; se avisa en la UI
            // para que el usuario no lo lea como un fallo de sincronía (ver LrcLine.words).
            val hasWordTimings = remember(syncedLines) { syncedLines.any { it.words.isNotEmpty() } }

            // El modo karaoke vive en el ViewModel y sobrevive al cambio de canción, pero no
            // tiene sentido sin letra sincronizada: en ese caso se apaga solo.
            LaunchedEffect(hasSyncedLyrics, karaokeMode) {
                if (karaokeMode && !hasSyncedLyrics) onKaraokeModeChange(false)
            }
            val karaokeActive = karaokeMode && hasSyncedLyrics

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = hasSyncedLyrics) {
                    IconButton(onClick = { onKaraokeModeChange(!karaokeMode) }) {
                        Icon(
                            imageVector = if (karaokeActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = stringResource(
                                if (karaokeActive) R.string.player_karaoke_exit_cd
                                else R.string.player_karaoke_enter_cd,
                            ),
                            tint = if (karaokeActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Las acciones de letra viven aquí y no bajo el título: ocupaban una fila entera
                // en mitad de la pantalla y dejaban a la letra con sitio para una sola frase.
                if (canRefreshLyrics && !karaokeActive) {
                    IconButton(onClick = onRefreshLyrics) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.player_lyrics_refresh_cd),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
                if (canDeleteLyrics && !karaokeActive) {
                    IconButton(onClick = onDeleteLyrics) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.player_lyrics_delete_cd),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.player_cd_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                // La carátula es cuadrada (aspectRatio 1:1) en vez de rellenar el hueco que
                // sobre: una portada no es un rectángulo arbitrario y recortarla la desfigura.
                // Al no llevar weight, AnimatedVisibility sí puede colapsarla de verdad en
                // karaoke — no hace falta el truco del peso mínimo.
                AnimatedVisibility(
                    visible = !karaokeActive,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enter = fadeIn(tween(Motion.STANDARD)) + expandVertically(tween(Motion.LAYOUT)),
                    exit = fadeOut(tween(Motion.QUICK)) + shrinkVertically(tween(Motion.LAYOUT)),
                ) {
                    Box(
                        modifier = Modifier
                            // 0.74 del ancho, no el ancho entero: a pantalla completa la portada
                            // cuadrada se comía más de la mitad del alto y a la letra le quedaba
                            // sitio para una frase.
                            .fillMaxWidth(0.74f)
                            .padding(vertical = Spacing.sm)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(Radius.xl),
                            )
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artwork != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(artwork)
                                    .crossfade(220)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AppLogo(size = 140.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = (track?.title ?: "").toTitleCaseSimple(),
                    style = if (karaokeActive) AppText.playerTitleCompact else AppText.playerTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = (track?.artist ?: "").toDisplayArtist(),
                    style = AppText.playerArtist,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // La letra vive en la misma capa que el resto: antes era una segunda tarjeta
                // oscura compitiendo con la de la carátula. El fondo (blur + gradiente) ya da
                // contraste suficiente para leerla.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        // Suelo: por debajo de esto no caben ni la frase actual ni la siguiente,
                        // que es justo lo que se quiere ver al arrancar la canción.
                        .heightIn(min = 108.dp)
                        .clip(RoundedCornerShape(Radius.lg))
                        .clickable(
                            enabled = canRefreshLyrics &&
                                lyricsState is LyricsUiState.Empty &&
                                !hasSyncedLyrics,
                            onClick = onRefreshLyrics,
                        ),
                ) {
                    when {
                        hasSyncedLyrics -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // La etiqueta de modo solo se muestra en karaoke. En escucha
                                // normal decía "Letra sincronizada", que es evidente al verla
                                // avanzar, y gastaba una fila que le hace falta a la letra.
                                if (karaokeActive) {
                                    Text(
                                        text = stringResource(
                                            if (hasWordTimings) R.string.player_karaoke_mode
                                            else R.string.player_karaoke_mode_line_sync,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = Spacing.sm),
                                        style = AppText.meta,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                SyncedLyricsView(
                                    lines = syncedLines,
                                    positionMs = lyricsPositionMs,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    activeFontSize = if (karaokeActive) 28.sp else 18.sp,
                                    inactiveFontSize = if (karaokeActive) 20.sp else 15.sp,
                                    onLineClick = onSeekToLyricsPosition,
                                )
                            }
                        }
                        else -> {
                            val bodyText = when (lyricsState) {
                                LyricsUiState.Idle -> stringResource(R.string.player_lyrics_tap_load)
                                LyricsUiState.Loading -> stringResource(R.string.player_lyrics_loading)
                                is LyricsUiState.Ready -> lyricsState.lyrics
                                is LyricsUiState.Empty -> lyricsState.message
                            }
                            Text(
                                text = bodyText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(plainScroll)
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Sin tarjeta: los controles descansan sobre el mismo fondo que el resto. La antigua
            // Card negra con elevación 18dp era la tercera superficie apilada de la pantalla.
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                ) {
                    val currentMs = (position.coerceIn(0f, 1f) * durationMs).toInt()
                    var localDrag by remember { mutableStateOf<Float?>(null) }
                    val sliderValue = localDrag ?: position
                    val expandedSliderA11y = stringResource(
                        R.string.player_slider_a11y,
                        formatMs(((localDrag ?: position).coerceIn(0f, 1f) * durationMs).toInt()),
                        formatMs(durationMs),
                    )
                    Slider(
                        value = sliderValue,
                        modifier = Modifier.semantics {
                            contentDescription = expandedSliderA11y
                        },
                        onValueChange = { v ->
                            localDrag = v
                            onSeekPreview(v)
                        },
                        onValueChangeFinished = {
                            val target = localDrag ?: sliderValue
                            onSeekCommit(target)
                            localDrag = null
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMs(currentMs),
                            style = AppText.meta,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatMs(durationMs),
                            style = AppText.meta,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = stringResource(R.string.player_cd_shuffle),
                                tint = if (shuffleOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            IconButton(
                                onClick = onPrev,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_prev_cd),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            // Play/pausa como botón relleno: es la única acción primaria de la
                            // pantalla y antes pesaba visualmente igual que prev/next.
                            val playPauseCd = stringResource(R.string.player_cd_play_pause)
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .scale(expandedPlayScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(onClick = onTogglePlay)
                                    .semantics { contentDescription = playPauseCd },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.player_next_cd),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleRepeat) {
                            Icon(
                                imageVector = Icons.Outlined.Loop,
                                contentDescription = stringResource(R.string.player_cd_repeat),
                                tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // Fase E: tono y velocidad. Solo en karaoke y plegado por defecto, para no
                    // competir con los controles de transporte.
                    AnimatedVisibility(visible = karaokeActive) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            KaraokeTuningControls(
                                pitchSemitones = karaokePitchSemitones,
                                speed = karaokeSpeed,
                                onPitchChange = onKaraokePitchChange,
                                onSpeedChange = onKaraokeSpeedChange,
                                onReset = onResetKaraokeTuning,
                            )
                            KaraokeRecordingControls(
                                state = karaokeRecording,
                                headphonesConnected = headphonesConnected,
                                savedTakeCount = savedTakeCount,
                                onStart = onStartRecording,
                                onStop = onStopRecording,
                                onSave = onSaveRecording,
                                onDiscard = onDiscardRecording,
                            )
                        }
                    }
                }
            }
        }
    }
}

