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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.network.isLyricsNetworkFailure
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.LyricsFetchCoordinator
import com.imontalvodev.beatmybeat.ui.network.LyricsFetcher
import com.imontalvodev.beatmybeat.ui.network.LrcLine
import com.imontalvodev.beatmybeat.ui.network.LrcParser
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.Motion
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.TrackListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.playback.LocalPlaybackService
import com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
import com.imontalvodev.beatmybeat.service.PlaybackService
import com.imontalvodev.beatmybeat.service.shouldShowPlaybackError
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private fun Context.sendPlaybackForegroundAction(action: String) {
    val intent = Intent(this, PlaybackService::class.java).setAction(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    modifier: Modifier = Modifier,
    onNavigateToDownloader: () -> Unit = {},
) {
    val palette = currentBeatMyBeatThemeProfile()
    val viewModel: PlayerViewModel = viewModel()
    val deviceTracks = viewModel.tracks.collectAsState().value
    val librarySyncing by viewModel.librarySyncing.collectAsState()
    val favoriteIds = viewModel.favoriteIds.collectAsState().value
    val playlists = viewModel.playlists.collectAsState().value
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiScope = rememberCoroutineScope()
    fun showToast(message: String, long: Boolean = false) {
        Toast.makeText(
            context.applicationContext,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasAudioPermission = granted
            if (granted) {
                viewModel.syncLibrary(auto = true)
            } else {
                showToast(
                    resources.getString(R.string.player_audio_permission_denied_snack),
                    long = true,
                )
                viewModel.syncLibrary(auto = true)
            }
        },
    )

    val queue = remember { mutableStateListOf<DeviceTrack>() }

    var currentTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }

    // PlaybackService ligado en MainActivity: no se pierde al cambiar de pestaña ni en segundo plano.
    val boundService = LocalPlaybackService.current

    // Cola pendiente si el usuario pulsa play antes de que el servicio esté ligado.
    data class PendingPlay(
        val queueJson: String,
        val index: Int,
        val startPositionMs: Long = 0L,
        val autoPlay: Boolean = true,
        val shuffleEnabled: Boolean = false,
    )
    var pendingPlay by remember { mutableStateOf<PendingPlay?>(null) }

    data class PendingRestore(
        val queueJson: String,
        val startIndex: Int,
        val positionMs: Long,
        val shuffleOn: Boolean,
        val autoPlay: Boolean,
    )
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    LaunchedEffect(boundService, pendingPlay) {
        val svc = boundService ?: return@LaunchedEffect
        val pending = pendingPlay ?: return@LaunchedEffect
        svc.loadQueue(
            queueJson = pending.queueJson,
            startIndex = pending.index,
            startPositionMs = pending.startPositionMs,
            autoPlay = pending.autoPlay,
            shuffleEnabled = pending.shuffleEnabled,
        )
        pendingPlay = null
    }

    LaunchedEffect(boundService, pendingRestore) {
        val svc = boundService ?: return@LaunchedEffect
        val restore = pendingRestore ?: return@LaunchedEffect
        svc.loadQueue(
            queueJson = restore.queueJson,
            startIndex = restore.startIndex,
            startPositionMs = restore.positionMs,
            autoPlay = restore.autoPlay,
            shuffleEnabled = restore.shuffleOn,
        )
        pendingRestore = null
    }

    // ── Playback state: única fuente de verdad ──────────────────────────────
    // StateFlow del servicio: reactivo, sin polling, sin variables @Volatile.
    val playbackState by PlaybackService.state.collectAsState()
    val isPlaying = playbackState.isPlaying
    val playbackPositionMs = playbackState.positionMs
    val playbackDurationMs = playbackState.durationMs
    val playbackMediaId = playbackState.currentMediaId

    // Errores de reproducción/cola del servicio: evento consumible (id único) para no repetir
    // el mismo toast en recomposiciones sucesivas.
    val playbackError by PlaybackService.playbackError.collectAsState()
    var lastShownErrorId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playbackError) {
        val error = playbackError ?: return@LaunchedEffect
        if (!shouldShowPlaybackError(error.id, lastShownErrorId)) return@LaunchedEffect
        lastShownErrorId = error.id
        showToast(error.message, long = true)
    }

    // posición normalizada [0,1] que ve el Slider: mientras el usuario arrastra
    // usamos sliderDragPos; en cuanto suelta, el StateFlow vuelve a mandar.
    var sliderDragPos by remember { mutableStateOf<Float?>(null) }
    val sliderPosition: Float = sliderDragPos
        ?: if (playbackDurationMs > 0) playbackPositionMs.toFloat() / playbackDurationMs else 0f

    var currentArtwork by remember { mutableStateOf<Bitmap?>(null) }

    var query by remember { mutableStateOf("") }
    var selectedSection by remember {
        mutableStateOf(
            when (viewModel.loadSection()) {
                "favorites" -> PlayerSection.Favorites
                "playlist"  -> PlayerSection.Playlist
                else        -> PlayerSection.Songs
            }
        )
    }
    var isExpanded by remember { mutableStateOf(false) }
    var lyricsState by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Idle) }
    // Mantener scroll independiente por pestaña para no perder posición al alternar.
    val songsListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val playlistListState = rememberLazyListState()

    val shuffleOn by viewModel.shuffleEnabled.collectAsState()
    val karaokeMode by viewModel.karaokeMode.collectAsState()
    val karaokePitchSemitones by viewModel.karaokePitchSemitones.collectAsState()
    val karaokeSpeed by viewModel.karaokeSpeed.collectAsState()
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    // Repetición de la cola (cuando Shuffle está OFF y repeatMode == LIST)
    var queueRepeatSnapshot by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var queueRepeatIndex by remember { mutableStateOf(-1) }
    var shuffleOrder by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var shuffleIndex by remember { mutableStateOf(-1) }
    /** Cola de sesión: lista COMPLETA de la sesión y posición absoluta de la canción actual. */
    var sessionOrderUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var sessionOrderIndex by remember { mutableStateOf(0) }
    val queueSnapshotHydrated by viewModel.queueUiHydrated.collectAsState()

    fun currentSessionQueueState(): SessionQueueState? {
        if (sessionOrderUris.isEmpty()) return null
        return SessionQueueState(sessionOrderUris, sessionOrderIndex)
    }

    fun applySessionQueueState(state: SessionQueueState?) {
        if (state == null || state.orderUris.isEmpty()) {
            sessionOrderUris = emptyList()
            sessionOrderIndex = 0
            return
        }
        sessionOrderUris = state.orderUris
        sessionOrderIndex = state.currentIndex.coerceIn(0, state.orderUris.lastIndex)
    }

    fun rebuildPendingQueueFromSession() {
        val state = currentSessionQueueState() ?: return
        val byUri = deviceTracks.associateBy { it.uri }
        queue.clear()
        resolvePendingTracks(pendingUrisFromSession(state), byUri).forEach { queue.add(it) }
    }

    fun ensureSessionQueueInitialized() {
        val ensured = ensureSessionQueue(
            currentTrackUri = currentTrack?.uri,
            pendingQueueUris = queue.map { it.uri },
            existing = currentSessionQueueState(),
        ) ?: return
        applySessionQueueState(ensured)
        rebuildPendingQueueFromSession()
    }

    fun updateQueueRepeatSnapshotForAppend(tracks: List<DeviceTrack>) {
        if (shuffleOn || repeatMode != RepeatMode.LIST) return
        val ct = currentTrack ?: return
        if (tracks.isEmpty()) return
        if (queueRepeatSnapshot.isEmpty() || queueRepeatIndex < 0) {
            queueRepeatSnapshot = listOf(ct) + queue.toList()
            queueRepeatIndex = 0
        } else {
            queueRepeatSnapshot = queueRepeatSnapshot + tracks
        }
    }

    fun updateQueueRepeatSnapshotForPlayNext(tracks: List<DeviceTrack>) {
        if (shuffleOn || repeatMode != RepeatMode.LIST) return
        val ct = currentTrack ?: return
        if (tracks.isEmpty()) return
        if (queueRepeatSnapshot.isEmpty() || queueRepeatIndex < 0) {
            queueRepeatSnapshot = listOf(ct) + queue.toList()
            queueRepeatIndex = 0
        } else {
            val mutable = queueRepeatSnapshot.toMutableList()
            var insertAt = (queueRepeatIndex + 1).coerceAtMost(mutable.size)
            tracks.forEach { track ->
                mutable.add(insertAt, track)
                insertAt++
            }
            queueRepeatSnapshot = mutable
        }
    }

    fun syncQueueToServiceOnly() {
        val svc = boundService ?: return
        val shuffleActive = viewModel.shuffleEnabled.value
        val nextTracks = if (shuffleActive && shuffleOrder.isNotEmpty()) {
            shuffleOrder.drop((shuffleIndex + 1).coerceAtLeast(0))
        } else {
            queue.toList()
        }
        val arr = JSONArray()
        nextTracks.forEach { t ->
            val o = JSONObject()
            o.put("id", t.uri)
            o.put("uri", t.uri)
            o.put("title", t.title)
            o.put("artist", t.artist)
            arr.put(o)
        }
        svc.syncNextItems(arr.toString())
    }

    var pendingDeleteTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var deleteConfirmTracks by remember { mutableStateOf<List<DeviceTrack>?>(null) }

    // Si el usuario está viendo el overlay expandido (letra),
    // el botón Atrás debe cerrar el overlay en vez de navegar fuera del player.
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    // Mantener el repeat del servicio (reproducción real) en sincronía con la UI.
    LaunchedEffect(boundService, repeatMode) {
        val player = boundService?.player ?: return@LaunchedEffect
        player.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.LIST -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    /**
     * Aplica tono/velocidad al player. Fuera del Modo Karaoke se fuerzan los valores neutros:
     * el ajuste sigue guardado en el ViewModel, pero no debe teñir la escucha normal.
     * Depende de [boundService] para reaplicarse si el servicio muere y se vuelve a bindar.
     */
    LaunchedEffect(boundService, karaokeMode, karaokePitchSemitones, karaokeSpeed) {
        val svc = boundService ?: return@LaunchedEffect
        if (karaokeMode) {
            svc.setPlaybackTuning(
                speed = karaokeSpeed,
                pitch = KaraokeTuning.pitchRatio(karaokePitchSemitones),
            )
        } else {
            svc.setPlaybackTuning(
                speed = KaraokeTuning.NEUTRAL_SPEED,
                pitch = KaraokeTuning.NEUTRAL_PITCH,
            )
        }
    }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var playlistDetailOpen by remember { mutableStateOf(false) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    /** URI (única por fichero); evita seleccionar dos filas con el mismo MediaStore id. */
    var selectedTrackUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = isSelectionModeActive
    var sortOption by remember {
        mutableStateOf(
            when (viewModel.loadSortOption()) {
                "name_desc"    -> SortOption.NAME_DESC
                "newest_first" -> SortOption.NEWEST_FIRST
                "oldest_first" -> SortOption.OLDEST_FIRST
                else           -> SortOption.NAME_ASC
            }
        )
    }

    fun exitSelectionMode() {
        isSelectionModeActive = false
        selectedTrackUris = emptySet()
    }

    fun enterSelectionMode(trackUri: String) {
        isSelectionModeActive = true
        selectedTrackUris = setOf(trackUri)
    }

    fun toggleTrackSelection(trackUri: String) {
        if (!isSelectionModeActive) return
        selectedTrackUris = if (selectedTrackUris.contains(trackUri)) {
            selectedTrackUris - trackUri
        } else {
            selectedTrackUris + trackUri
        }
        if (selectedTrackUris.isEmpty()) {
            exitSelectionMode()
        }
    }

    fun clearTrackSelection() {
        exitSelectionMode()
    }

    LaunchedEffect(selectedTrackUris) {
        if (selectedTrackUris.isEmpty() && isSelectionModeActive) {
            isSelectionModeActive = false
        }
    }

    LaunchedEffect(selectedSection, query, selectedPlaylistId) {
        clearTrackSelection()
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != PlayerSection.Playlist) {
            playlistDetailOpen = false
        }
    }

    BackHandler(enabled = playlistDetailOpen && !isExpanded && !isSelectionModeActive) {
        playlistDetailOpen = false
    }

    BackHandler(enabled = isSelectionModeActive && !isExpanded) {
        clearTrackSelection()
    }

    LaunchedEffect(playlists) {
        if (playlists.isEmpty()) {
            selectedPlaylistId = null
            return@LaunchedEffect
        }
        val stillExists = selectedPlaylistId != null && playlists.any { it.id == selectedPlaylistId }
        if (!stillExists) {
            selectedPlaylistId = playlists.first().id
        }
    }

    // Persistir sección activa cada vez que cambia
    LaunchedEffect(selectedSection) {
        viewModel.saveSection(
            when (selectedSection) {
                PlayerSection.Songs     -> "songs"
                PlayerSection.Favorites -> "favorites"
                PlayerSection.Playlist  -> "playlist"
            }
        )
    }

    // Persistir opción de ordenación cada vez que cambia
    LaunchedEffect(sortOption) {
        viewModel.saveSortOption(
            when (sortOption) {
                SortOption.NAME_ASC    -> "name_asc"
                SortOption.NAME_DESC   -> "name_desc"
                SortOption.NEWEST_FIRST -> "newest_first"
                SortOption.OLDEST_FIRST -> "oldest_first"
            }
        )
    }

    var queueSheetOpen by remember { mutableStateOf(false) }
    var addToPlaylistDialogOpen by remember { mutableStateOf(false) }
    var addToPlaylistTracks by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var addToPlaylistExistingId by remember { mutableStateOf<Long?>(null) }
    var addToPlaylistNewName by remember { mutableStateOf("") }
    var addToPlaylistPickerExpanded by remember { mutableStateOf(false) }

    data class DuplicateConfirmState(
        val trackIds: List<Long>,
        val playlistId: Long,
    )

    var duplicateDialog by remember { mutableStateOf<DuplicateConfirmState?>(null) }

    var playlistDeleteDialogId by remember { mutableStateOf<Long?>(null) }
    var playlistRenameDialogId by remember { mutableStateOf<Long?>(null) }
    var playlistRenameNewName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = granted
        if (!granted) {
            permissionLauncher.launch(audioPermission)
        } else {
            viewModel.syncLibrary(auto = true)
        }
    }

    if (deviceTracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.backgroundTop, palette.backgroundBottom),
                    ),
                ),
        ) {
            when {
                !hasAudioPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.player_audio_permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.player_audio_permission_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TextButton(onClick = { permissionLauncher.launch(audioPermission) }) {
                            Text(stringResource(R.string.player_audio_permission_grant))
                        }
                    }
                }

                librarySyncing -> {
                    TrackListSkeleton(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    LibraryEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        onOpenDownloader = onNavigateToDownloader,
                    )
                }
            }
        }
        return
    }

    // Cargar carátula: primero tags embebidos, luego .meta.json con artworkBase64
    LaunchedEffect(currentTrack?.uri) {
        val track = currentTrack
        if (track == null) {
            currentArtwork = null
            return@LaunchedEffect
        }
        currentArtwork = null

        val cached = ArtworkCache.getUri(track.uri)
        if (cached != null && !cached.isRecycled) {
            currentArtwork = cached
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            val bytes = com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
                .resolveArtworkBytes(context, track.uri)
            if (bytes != null && bytes.isNotEmpty()) {
                return@withContext BitmapDecoding.decodeSampled(bytes, PLAYER_ARTWORK_MAX_PX)
            }
            null
        }

        if (currentTrack?.uri != track.uri) return@LaunchedEffect
        currentArtwork = loaded
        if (loaded != null) ArtworkCache.putUri(track.uri, loaded)
    }

    // Letras: solo caché local (offline-first). Se rellenan al descargar.
    /** URIs de pistas con descarga de letra en curso (independiente de la pista actual). */
    var lyricsDownloadingUris by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(currentTrack?.id, lyricsDownloadingUris) {
        val t = currentTrack ?: run {
            lyricsState = LyricsUiState.Empty(
                resources.getString(R.string.player_lyrics_select_song),
            )
            return@LaunchedEffect
        }

        // Leer título y artista reales desde .meta.json si existen
        val (title, artist) = withContext(Dispatchers.IO) {
            resolveTrackMeta(t)
        }

        fun isUnknown(s: String): Boolean =
            s.equals("unknown", ignoreCase = true) ||
                s.equals("unknown artist", ignoreCase = true) ||
                s.isBlank()

        if (isUnknown(title) || isUnknown(artist)) {
            lyricsState = LyricsUiState.Empty(
                resources.getString(R.string.player_lyrics_unavailable),
            )
            return@LaunchedEffect
        }

        val cachedEntry = withContext(Dispatchers.IO) {
            LyricsCache.getEntry(context, title, artist)
        }
        if (cachedEntry != null && cachedEntry.hasAnyLyrics()) {
            lyricsState = LyricsUiState.Ready(
                lyrics = cachedEntry.displayPlain(),
                syncedLrc = cachedEntry.syncedLrc,
            )
            return@LaunchedEffect
        }
        lyricsState = if (t.uri in lyricsDownloadingUris) {
            LyricsUiState.Loading
        } else {
            LyricsUiState.Empty(
                resources.getString(R.string.player_lyrics_tap_download),
            )
        }
    }

    fun sanitizeTitle(input: String): String {
        return input
            .replace(Regex("\\s*[\\(\\[].*?[\\)\\]]\\s*"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), " ")
            .replace(Regex("(?i)\\b(remastered|remaster|official|audio|video|videolyrics|lyrics|live|prod\\.?|produced)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun titleFromUri(uri: String): String {
        val rawName = runCatching {
            val parsed = Uri.parse(uri)
            val path = parsed.lastPathSegment ?: return@runCatching ""
            URLDecoder.decode(path, "UTF-8")
        }.getOrElse { "" }
        if (rawName.isBlank()) return ""
        return rawName
            .replace(Regex("\\.[A-Za-z0-9]{2,5}$"), "")
            .replace(Regex("_"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isUnknown(s: String): Boolean =
        s.equals("unknown", ignoreCase = true) ||
            s.equals("unknown artist", ignoreCase = true) ||
            s.isBlank()

    fun lyricsSearchKeys(
        track: DeviceTrack,
        meta: TrackLyricsMetadata,
    ): Pair<List<String>, List<String>> {
        val uriTitle = titleFromUri(track.uri)
        val metaPair = resolveTrackMeta(track)
        val titles = listOf(
            meta.title.trim(),
            sanitizeTitle(meta.title),
            metaPair.first.trim(),
            uriTitle,
            sanitizeTitle(uriTitle),
            track.title.trim(),
        ).distinct().filter { it.isNotBlank() }
        val artists = com.imontalvodev.beatmybeat.ui.network.buildLyricsArtistCandidates(
            meta.artist,
            listOf(track.artist.trim(), metaPair.second.trim()),
        )
        return titles to artists
    }

    suspend fun clearLyricsCacheForTrack(track: DeviceTrack) {
        val meta = resolveTrackMetadata(track)
        val (titles, artists) = lyricsSearchKeys(track, meta)
        LyricsCache.removeAll(context, titles, artists)
    }

    fun downloadLyricsIfNeeded(track: DeviceTrack, forceRefresh: Boolean = false) {
        if (track.uri in lyricsDownloadingUris) return

        lyricsDownloadingUris = lyricsDownloadingUris + track.uri
        if (currentTrack?.uri == track.uri) {
            lyricsState = LyricsUiState.Loading
        }

        uiScope.launch {
            try {
                val meta = withContext(Dispatchers.IO) { resolveTrackMetadata(track) }

                if (isUnknown(meta.title) || isUnknown(meta.artist)) {
                    if (currentTrack?.uri == track.uri) {
                        lyricsState = LyricsUiState.Empty(
                            resources.getString(R.string.player_lyrics_unavailable),
                        )
                    }
                    return@launch
                }

                val uriTitle = titleFromUri(track.uri)
                val titleCandidates = listOf(
                    meta.title.trim(),
                    sanitizeTitle(meta.title),
                    uriTitle,
                    sanitizeTitle(uriTitle),
                ).distinct().filter { it.isNotBlank() }
                val artistCandidates = listOf(
                    track.artist.trim(),
                    meta.artist.trim(),
                ).distinct().filter { it.isNotBlank() }

                val effectiveDurationMs = when {
                    meta.durationMs > 0L -> meta.durationMs
                    currentTrack?.uri == track.uri && playbackDurationMs > 0L -> playbackDurationMs
                    else -> 0L
                }

                if (forceRefresh) {
                    withContext(Dispatchers.IO) { clearLyricsCacheForTrack(track) }
                }
                val res = LyricsFetchCoordinator.fetch(
                    context,
                    LyricsFetcher.Request(
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        durationMs = effectiveDurationMs,
                        titleCandidates = titleCandidates,
                        artistCandidates = artistCandidates,
                    ),
                    skipCache = forceRefresh,
                )

                if (currentTrack?.uri == track.uri) {
                    if (res.success && res.lyrics.isNotBlank()) {
                        lyricsState = LyricsUiState.Ready(
                            lyrics = res.lyrics,
                            syncedLrc = res.syncedLrc,
                        )
                    } else {
                        // Un fallo de red no es lo mismo que "esta canción no tiene letra":
                        // en el primer caso reintentar sirve de algo, en el segundo no.
                        val message = if (isLyricsNetworkFailure(res.error)) {
                            R.string.player_lyrics_network_error
                        } else {
                            R.string.player_lyrics_unavailable
                        }
                        lyricsState = LyricsUiState.Empty(resources.getString(message))
                    }
                }
            } finally {
                lyricsDownloadingUris = lyricsDownloadingUris - track.uri
            }
        }
    }

    fun deleteLyricsForTrack(track: DeviceTrack) {
        if (track.uri in lyricsDownloadingUris) return
        uiScope.launch {
            withContext(Dispatchers.IO) { clearLyricsCacheForTrack(track) }
            lyricsState = LyricsUiState.Empty(
                resources.getString(R.string.player_lyrics_tap_download),
            )
            showToast(resources.getString(R.string.player_lyrics_deleted))
        }
    }

    // (Helper eliminado: ya no se usa en el fallback de letras bajo demanda)

    val bgBrush = Brush.verticalGradient(colors = listOf(palette.backgroundTop, palette.backgroundBottom))
    val cannotPlayFileText = stringResource(R.string.player_error_cannot_play_file)
    val songDeletedText = stringResource(R.string.player_song_deleted)
    val deleteCancelledText = stringResource(R.string.player_delete_cancelled)
    val selectionModeEnabledText = stringResource(R.string.player_selection_mode_enabled)
    val queueAddedText = stringResource(R.string.player_added_to_queue_end)
    val playNextAddedText = stringResource(R.string.player_play_next_added)

    val miniDurationMs = playbackDurationMs.toInt().takeIf { it > 0 } ?: 0
    val miniCurrentMs = ((sliderDragPos ?: sliderPosition).coerceIn(0f, 1f) * miniDurationMs).toInt()
    val miniSliderA11y = stringResource(
        R.string.player_slider_a11y,
        formatMs(miniCurrentMs),
        formatMs(miniDurationMs),
    )

    val visibleTracks = remember(
        deviceTracks,
        favoriteIds,
        playlists,
        selectedPlaylistId,
        query,
        selectedSection,
        sortOption,
    ) {
        buildVisibleTracksForSection(
            deviceTracks,
            favoriteIds,
            playlists,
            selectedPlaylistId,
            query,
            selectedSection,
            sortOption,
        )
    }

    /**
     * Pool de reproducción según el contexto actual: pestaña (canciones / favoritos / playlist),
     * búsqueda y ordenación. Una entrada por URI.
     */
    val playbackPoolTracks = remember(visibleTracks) {
        visibleTracks.distinctBy { it.uri }
    }

    fun playbackQueueTotalCount(): Int {
        if (shuffleOn && shuffleOrder.isNotEmpty()) {
            // Derivar la posición actual desde currentTrack para que el conteo sea siempre
            // reactivo: cuántas canciones quedan (incluyendo la actual) en el orden aleatorio.
            val ct = currentTrack
            val pos = if (ct != null) {
                shuffleOrder.indexOfFirst { it.uri == ct.uri }.takeIf { it >= 0 } ?: shuffleIndex
            } else shuffleIndex
            return (shuffleOrder.size - pos.coerceAtLeast(0)).coerceAtLeast(if (ct != null) 1 else 0)
        }
        return (if (currentTrack != null) 1 else 0) + queue.size
    }

    /**
     * Pool de shuffle = [playbackPoolTracks] (lista visible con filtros activos).
     * [shuffleOrder] es una permutación única de ese pool; [queue] espeja siempre
     * lo pendiente: shuffleOrder.drop(shuffleIndex + 1).
     */
    fun refreshShuffleQueueMirror() {
        if (!viewModel.shuffleEnabled.value || shuffleOrder.isEmpty()) return
        queue.clear()
        queue.addAll(shuffleOrder.drop((shuffleIndex + 1).coerceAtLeast(0)))
    }

    /**
     * Nueva permutación aleatoria del pool [playbackPoolTracks].
     * Si hay [playbackAnchor] o [currentTrack], se coloca en el índice 0 para que
     * ExoPlayer y la UI reciban TODAS las canciones pendientes (no solo las posteriores
     * a una posición aleatoria en el orden barajado).
     */
    fun rebuildShuffleOrderFromPool(playbackAnchor: DeviceTrack? = null) {
        if (!viewModel.shuffleEnabled.value) return
        val base = playbackPoolTracks.toMutableList()
        val anchor = playbackAnchor ?: currentTrack
        anchor?.let { a ->
            if (base.none { it.uri == a.uri }) base.add(a)
        }
        if (base.isEmpty()) {
            shuffleOrder = emptyList()
            shuffleIndex = -1
            queue.clear()
            syncQueueToServiceOnly()
            return
        }
        var order = base.shuffled(Random(System.currentTimeMillis()))
        if (anchor != null) {
            val anchorIdx = order.indexOfFirst { it.uri == anchor.uri }
            if (anchorIdx > 0) {
                order = listOf(order[anchorIdx]) + order.drop(anchorIdx + 1) + order.take(anchorIdx)
            }
        }
        shuffleOrder = order
        shuffleIndex = if (shuffleOrder.isEmpty()) -1 else 0
        refreshShuffleQueueMirror()
        syncQueueToServiceOnly()
    }

    fun buildPlaybackSnapshot(positionMs: Long = playbackPositionMs): PlaybackQueueSnapshot? {
        val shuffleActive = viewModel.shuffleEnabled.value
        val orderUris: List<String>
        val currentIndex: Int
        if (shuffleActive && shuffleOrder.isNotEmpty()) {
            orderUris = shuffleOrder.map { it.uri }
            currentIndex = shuffleIndex.coerceIn(0, shuffleOrder.lastIndex)
        } else if (sessionOrderUris.isNotEmpty()) {
            // Índice absoluto en la lista completa de sesión: al restaurar se reanuda
            // exactamente aquí y continúa hacia los siguientes (sessionOrderIndex+1, +2…).
            orderUris = sessionOrderUris
            currentIndex = sessionOrderIndex.coerceIn(0, sessionOrderUris.lastIndex)
        } else {
            val current = currentTrack
            if (current == null && queue.isEmpty()) return null
            orderUris = buildList {
                current?.let { add(it.uri) }
                addAll(queue.map { it.uri })
            }
            currentIndex = 0
        }
        if (orderUris.isEmpty()) return null
        return PlaybackQueueSnapshot(
            orderUris = orderUris,
            currentIndex = currentIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            shuffleOn = shuffleActive,
        )
    }

    fun persistPlaybackSnapshot(positionMs: Long = playbackPositionMs) {
        if (!queueSnapshotHydrated) return
        // Estado inconsistente (p. ej. remember recién reiniciado): no pisar el JSON completo.
        if (viewModel.shuffleEnabled.value && shuffleOrder.isEmpty() && queue.isEmpty()) return
        val snapshot = buildPlaybackSnapshot(positionMs)
        if (snapshot == null) {
            viewModel.clearPlaybackQueueSnapshot()
        } else {
            viewModel.savePlaybackQueueSnapshot(snapshot)
        }
    }

    fun syncQueueToService(persist: Boolean = true) {
        syncQueueToServiceOnly()
        if (persist) persistPlaybackSnapshot()
    }

    /**
     * Inserta tras la pista actual (índice current+1 en el fichero de cola).
     * Con shuffle: muta [shuffleOrder]; si no: cabeza de [queue] (espejo de lo pendiente).
     */
    fun insertTracksPlayNext(tracks: List<DeviceTrack>) {
        if (tracks.isEmpty()) return
        val shuffleActive = viewModel.shuffleEnabled.value
        if (shuffleActive && shuffleOrder.isNotEmpty() && shuffleIndex >= 0) {
            val mutable = shuffleOrder.toMutableList()
            var insertAt = (shuffleIndex + 1).coerceAtMost(mutable.size)
            tracks.forEach { t ->
                mutable.add(insertAt, t)
                insertAt++
            }
            shuffleOrder = mutable
            refreshShuffleQueueMirror()
        } else if (currentTrack != null) {
            ensureSessionQueueInitialized()
            val updated = insertTracksPlayNextInSession(
                state = currentSessionQueueState() ?: return,
                trackUris = tracks.map { it.uri },
            )
            applySessionQueueState(updated)
            rebuildPendingQueueFromSession()
        } else {
            tracks.reversed().forEach { queue.add(0, it) }
        }
        updateQueueRepeatSnapshotForPlayNext(tracks)
        syncQueueToService()
    }

    fun appendTracksToQueue(tracks: List<DeviceTrack>) {
        if (tracks.isEmpty()) return
        val shuffleActive = viewModel.shuffleEnabled.value
        if (shuffleActive && shuffleOrder.isNotEmpty() && shuffleIndex >= 0) {
            shuffleOrder = shuffleOrder + tracks
            refreshShuffleQueueMirror()
        } else if (currentTrack != null) {
            ensureSessionQueueInitialized()
            val updated = appendTracksToSession(
                state = currentSessionQueueState() ?: return,
                trackUris = tracks.map { it.uri },
            )
            applySessionQueueState(updated)
            rebuildPendingQueueFromSession()
        } else {
            queue.addAll(tracks)
        }
        updateQueueRepeatSnapshotForAppend(tracks)
        syncQueueToService()
    }

    fun applyResolvedPlaybackQueue(resolved: ResolvedPlaybackQueue) {
        viewModel.setShuffleEnabled(resolved.shuffleOn)
        if (resolved.shuffleOn) {
            shuffleOrder = resolved.tracks
            shuffleIndex = resolved.currentIndex
            sessionOrderUris = emptyList()
            sessionOrderIndex = 0
            refreshShuffleQueueMirror()
        } else {
            shuffleOrder = emptyList()
            shuffleIndex = -1
            // Restaurar la lista completa y el índice absoluto tal como se guardaron.
            sessionOrderUris = resolved.tracks.map { it.uri }
            sessionOrderIndex = resolved.currentIndex
            queue.clear()
            queue.addAll(resolved.tracks.drop(resolved.currentIndex + 1))
        }
        currentTrack = resolved.tracks.getOrNull(resolved.currentIndex)
        currentIndex = currentTrack?.let { t ->
            deviceTracks.indexOfFirst { it.uri == t.uri }.takeIf { it >= 0 }
                ?: deviceTracks.indexOfFirst { it.id == t.id }
        } ?: -1
    }

    fun clearPlaybackQueueState(keepCurrentInShuffle: Boolean = false) {
        if (keepCurrentInShuffle && shuffleOn && shuffleIndex in shuffleOrder.indices) {
            shuffleOrder = listOf(shuffleOrder[shuffleIndex])
            shuffleIndex = 0
            refreshShuffleQueueMirror()
        } else {
            queue.clear()
            shuffleOrder = emptyList()
            shuffleIndex = -1
        }
        sessionOrderUris = emptyList()
        sessionOrderIndex = 0
        queueRepeatSnapshot = emptyList()
        queueRepeatIndex = -1
        syncQueueToService(persist = false)
        persistPlaybackSnapshot()
    }

    val persistOnPause by rememberUpdatedState {
        {
            if (queueSnapshotHydrated) {
                persistPlaybackSnapshot(PlaybackService.state.value.positionMs)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                persistOnPause()
                clearTrackSelection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clearTrackSelection()
        }
    }

    // Restaurar cola al reentrar en el reproductor sin interrumpir lo que ya suena en el servicio.
    LaunchedEffect(deviceTracks, librarySyncing, boundService) {
        val svc = boundService ?: return@LaunchedEffect
        if (deviceTracks.isEmpty()) return@LaunchedEffect

        val player = svc.player
        val serviceMediaId = player.currentMediaItem?.mediaId
            ?.takeIf { it.isNotBlank() }
            ?: PlaybackService.state.value.currentMediaId
        val serviceHasQueue = player.mediaItemCount > 0 && serviceMediaId.isNotBlank()
        val uiQueueEmpty = shuffleOrder.isEmpty() && queue.isEmpty()

        fun hydrateUiFromSnapshot(): Boolean {
            val snapshot = viewModel.loadPlaybackQueueSnapshot() ?: return false
            val resolved = resolvePlaybackQueueSnapshot(
                snapshot,
                deviceTracks.associateBy { it.uri },
            ) ?: return false
            applyResolvedPlaybackQueue(resolved)
            syncQueueToService(persist = false)
            return true
        }

        // Reentrar en la pestaña: remember se reinicia pero el servicio sigue sonando.
        if (queueSnapshotHydrated && serviceHasQueue && uiQueueEmpty) {
            if (!hydrateUiFromSnapshot()) {
                resolveTrackFromPlaybackMediaId(serviceMediaId, deviceTracks)?.let { track ->
                    currentTrack = track
                    currentIndex = deviceTracks.indexOfFirst { it.uri == track.uri }.takeIf { it >= 0 }
                        ?: deviceTracks.indexOfFirst { it.id == track.id }
                }
            }
            return@LaunchedEffect
        }

        if (queueSnapshotHydrated) return@LaunchedEffect
        if (!uiQueueEmpty) {
            viewModel.setQueueUiHydrated(true)
            return@LaunchedEffect
        }

        if (serviceHasQueue) {
            if (!hydrateUiFromSnapshot()) {
                resolveTrackFromPlaybackMediaId(serviceMediaId, deviceTracks)?.let { track ->
                    currentTrack = track
                    currentIndex = deviceTracks.indexOfFirst { it.uri == track.uri }.takeIf { it >= 0 }
                        ?: deviceTracks.indexOfFirst { it.id == track.id }
                }
            }
            viewModel.setQueueUiHydrated(true)
            return@LaunchedEffect
        }

        val snapshot = viewModel.loadPlaybackQueueSnapshot()
        if (snapshot == null) {
            viewModel.setQueueUiHydrated(true)
            return@LaunchedEffect
        }

        val resolved = resolvePlaybackQueueSnapshot(
            snapshot,
            deviceTracks.associateBy { it.uri },
        )
        if (resolved == null) {
            if (librarySyncing) return@LaunchedEffect
            viewModel.clearPlaybackQueueSnapshot()
            viewModel.setQueueUiHydrated(true)
            return@LaunchedEffect
        }

        applyResolvedPlaybackQueue(resolved)
        syncQueueToService(persist = false)

        val current = resolved.tracks.getOrNull(resolved.currentIndex)
        if (current != null) {
            val shouldAutoPlay = player.playWhenReady || PlaybackService.state.value.isPlaying
            pendingRestore = PendingRestore(
                queueJson = resolved.tracks.toQueueJsonArray().toString(),
                startIndex = resolved.currentIndex,
                positionMs = resolved.positionMs,
                shuffleOn = resolved.shuffleOn,
                autoPlay = shouldAutoPlay,
            )
        }
        viewModel.setQueueUiHydrated(true)
    }

    val playbackPersistenceKey = buildString {
        append(shuffleOn)
        append('|')
        if (shuffleOn && shuffleOrder.isNotEmpty()) {
            append(shuffleOrder.joinToString("\u0001") { it.uri })
            append('|')
            append(shuffleIndex)
        } else if (sessionOrderUris.isNotEmpty()) {
            // Sólo necesitamos que cambie cuando avanza la posición en la lista.
            append(sessionOrderIndex)
            append('|')
            append(sessionOrderUris.joinToString("\u0001"))
            append('|')
            append(currentTrack?.uri ?: "")
        } else {
            append(currentTrack?.uri ?: "")
            append('|')
            append(queue.joinToString("\u0001") { it.uri })
        }
    }
    LaunchedEffect(playbackPersistenceKey, queueSnapshotHydrated) {
        if (!queueSnapshotHydrated) return@LaunchedEffect
        persistPlaybackSnapshot()
    }

    fun onToggleShuffle() {
        val next = !viewModel.shuffleEnabled.value
        viewModel.setShuffleEnabled(next)
        viewModel.clearPlaybackQueueSnapshot()
        queueRepeatSnapshot = emptyList()
        queueRepeatIndex = -1
        sessionOrderUris = emptyList()
        sessionOrderIndex = 0
        if (next) {
            rebuildShuffleOrderFromPool()
            syncQueueToService()
        } else {
            shuffleOrder = emptyList()
            shuffleIndex = -1
            queue.clear()
            syncQueueToService()
        }
    }

    fun onCycleRepeatMode() {
        val next = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.LIST
            RepeatMode.LIST -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatMode = next

        // Iniciar/limpiar snapshot de repetición de cola si aplica.
        if (next != RepeatMode.LIST || shuffleOn || currentTrack == null || queue.isEmpty()) {
            queueRepeatSnapshot = emptyList()
            queueRepeatIndex = -1
        } else {
            // Snapshot = [cancion actual] + cola restante
            queueRepeatSnapshot = listOf(currentTrack!!) + queue.toList()
            queueRepeatIndex = 0
        }
    }

    // Cambio de sección/playlist: solo afecta la lista visible, no lo que ya suena.
    // Si hay reproducción activa, conservamos cola + snapshot; nueva cola al pulsar Play en otra sección.
    var previousSectionPlaylistKey by remember { mutableStateOf<Pair<PlayerSection, Long?>?>(null) }
    LaunchedEffect(selectedSection, selectedPlaylistId) {
        val key = selectedSection to selectedPlaylistId
        val prev = previousSectionPlaylistKey
        previousSectionPlaylistKey = key
        if (prev == null) return@LaunchedEffect
        if (prev == key) return@LaunchedEffect
        if (prev.first == PlayerSection.Playlist && key.first == PlayerSection.Playlist &&
            prev.second == null && key.second != null
        ) {
            return@LaunchedEffect
        }
        val playbackActive = currentTrack != null ||
            playbackState.currentMediaId.isNotBlank() ||
            (boundService?.player?.mediaItemCount ?: 0) > 0
        if (!playbackActive) {
            viewModel.clearPlaybackQueueSnapshot()
            queue.clear()
            sessionOrderUris = emptyList()
            sessionOrderIndex = 0
            if (shuffleOn) rebuildShuffleOrderFromPool()
            else syncQueueToService()
            queueRepeatSnapshot = emptyList()
            queueRepeatIndex = -1
        }
        clearTrackSelection()
    }

    // Sincronizar UI con ExoPlayer (notificación / lock screen): misma estructura shuffle o consumo de cola.
    LaunchedEffect(playbackMediaId, deviceTracks) {
        val track = resolveTrackFromPlaybackMediaId(playbackMediaId, deviceTracks) ?: return@LaunchedEffect
        if (currentTrack?.uri != track.uri) {
            if (shuffleOn && shuffleOrder.isNotEmpty()) {
                val idx = shuffleOrder.indexOfFirst { it.uri == track.uri }
                if (idx >= 0) {
                    shuffleIndex = idx
                    refreshShuffleQueueMirror()
                    syncQueueToService()
                }
            } else {
                val sessionState = currentSessionQueueState()
                if (sessionState != null) {
                    val advanced = advanceSessionToTrack(sessionState, track.uri)
                    if (advanced != null) {
                        applySessionQueueState(advanced)
                        rebuildPendingQueueFromSession()
                    }
                } else if (queue.firstOrNull()?.uri == track.uri) {
                    queue.removeAt(0)
                }
            }
        }
        currentTrack = track
        currentIndex = deviceTracks.indexOfFirst { it.uri == track.uri }.takeIf { it >= 0 }
            ?: deviceTracks.indexOfFirst { it.id == track.id }
    }

    fun playTrack(track: DeviceTrack, clearQueue: Boolean = false) {
        try {
            if (clearQueue) queue.clear()
            if (clearQueue) {
                queueRepeatSnapshot = emptyList()
                queueRepeatIndex = -1
            }
            // Cargamos en el servicio SOLO [track actual] + [cola manual].
            // Antes cargábamos visibleTracks entero (hasta cientos de canciones),
            // lo que forzaba después un syncNextItems() con N removes sobre el hilo
            // principal → causa directa del ANR con listas grandes.
            fun DeviceTrack.toJsonObject() = JSONObject().also { o ->
                o.put("id", uri)
                o.put("uri", uri)
                o.put("title", title)
                o.put("artist", artist)
            }
            // Shuffle: reorganizamos shuffleOrder para que el track seleccionado quede en
            // la posición 0, garantizando así que ExoPlayer recibe TODAS las canciones.
            val nextItems: List<DeviceTrack>
            if (shuffleOn) {
                var idx = shuffleOrder.indexOfFirst { it.uri == track.uri }
                if (idx < 0) {
                    rebuildShuffleOrderFromPool(playbackAnchor = track)
                    idx = shuffleOrder.indexOfFirst { it.uri == track.uri }
                }
                if (idx > 0 && idx < shuffleOrder.size) {
                    // Rotar: poner el track en cabeza y conservar el orden del resto
                    shuffleOrder = listOf(shuffleOrder[idx]) +
                        shuffleOrder.drop(idx + 1) +
                        shuffleOrder.take(idx)
                    shuffleIndex = 0
                    refreshShuffleQueueMirror()
                }
                nextItems = if (shuffleOrder.isNotEmpty()) shuffleOrder.drop(1) else emptyList()
            } else {
                nextItems = queue.toList()
            }
            val arr = JSONArray()
            arr.put(track.toJsonObject())
            nextItems.forEach { t -> arr.put(t.toJsonObject()) }

            val queueJson = arr.toString()
            val svc = boundService
            if (svc != null) {
                svc.loadQueue(
                    queueJson = queueJson,
                    startIndex = 0,
                    shuffleEnabled = shuffleOn,
                )
            } else {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, PlaybackService::class.java),
                )
                pendingPlay = PendingPlay(
                    queueJson = queueJson,
                    index = 0,
                    shuffleEnabled = shuffleOn,
                )
            }
            currentTrack = track
            currentIndex = deviceTracks.indexOfFirst { it.uri == track.uri }.takeIf { it >= 0 }
                ?: deviceTracks.indexOfFirst { it.id == track.id }

            if (shuffleOn && shuffleOrder.isNotEmpty()) {
                val si = shuffleOrder.indexOfFirst { it.uri == track.uri }
                if (si >= 0) {
                    shuffleIndex = si
                    refreshShuffleQueueMirror()
                }
            } else {
                val sessionState = currentSessionQueueState()
                if (sessionState != null) {
                    val advanced = advanceSessionToTrack(sessionState, track.uri)
                    if (advanced != null) {
                        applySessionQueueState(advanced)
                        rebuildPendingQueueFromSession()
                    }
                }
            }
            persistPlaybackSnapshot()
        } catch (e: Exception) {
            showToast(cannotPlayFileText)
        }
    }

    fun startPlaybackFromCollection(startTrack: DeviceTrack) {
        val pool = playbackPoolTracks
        if (pool.isEmpty()) return
        queueRepeatSnapshot = emptyList()
        queueRepeatIndex = -1
        if (shuffleOn) {
            // Colocamos startTrack en la posición 0 para garantizar que ExoPlayer
            // recibe TODAS las canciones del pool (no solo las posteriores al índice).
            val rest = pool.filterNot { it.uri == startTrack.uri }
                .shuffled(Random(System.currentTimeMillis()))
            shuffleOrder = listOf(startTrack) + rest
            shuffleIndex = 0
            refreshShuffleQueueMirror()
            syncQueueToService()
            playTrack(shuffleOrder[0], clearQueue = false)
            return
        }

        val startIndex = pool.indexOfFirst { it.uri == startTrack.uri }.coerceAtLeast(0)
        // Guardar la lista completa del pool y el índice absoluto de inicio.
        sessionOrderUris = pool.map { it.uri }
        sessionOrderIndex = startIndex
        queue.clear()
        queue.addAll(pool.drop(startIndex + 1))
        playTrack(pool[startIndex], clearQueue = false)
        if (repeatMode == RepeatMode.LIST) {
            queueRepeatSnapshot = listOf(pool[startIndex]) + queue.toList()
            queueRepeatIndex = 0
        }
    }

    fun playPrev() {
        if (visibleTracks.isEmpty()) return
        if (repeatMode == RepeatMode.ONE && currentTrack != null) {
            playTrack(currentTrack!!, clearQueue = false)
            return
        }
        if (!shuffleOn && repeatMode == RepeatMode.LIST &&
            queueRepeatSnapshot.size > 1 && queueRepeatIndex >= 0
        ) {
            val prevIndex = if (queueRepeatIndex - 1 >= 0) queueRepeatIndex - 1 else queueRepeatSnapshot.lastIndex
            val nextTrack = queueRepeatSnapshot[prevIndex]
            // Si el tema se borró del teléfono, invalidamos la repetición de cola.
            if (deviceTracks.none { it.uri == nextTrack.uri }) return
            playTrack(nextTrack, clearQueue = false)
            queueRepeatIndex = prevIndex
            queue.clear()
            queue.addAll(queueRepeatSnapshot.subList(queueRepeatIndex + 1, queueRepeatSnapshot.size))
            return
        }
        if (shuffleOn && shuffleOrder.isNotEmpty() && shuffleIndex >= 0) {
            val prevIndex = shuffleIndex - 1
            if (prevIndex >= 0) {
                playTrack(shuffleOrder[prevIndex], clearQueue = false)
            } else if (repeatMode == RepeatMode.LIST && shuffleOrder.isNotEmpty()) {
                playTrack(shuffleOrder.last(), clearQueue = false)
            }
            return
        }

        val curUri = currentTrack?.uri ?: return
        val idx = visibleTracks.indexOfFirst { it.uri == curUri }
        val prevIndex = when {
            idx < 0 -> 0
            idx <= 0 -> visibleTracks.lastIndex
            else -> idx - 1
        }
        if (idx <= 0 && repeatMode != RepeatMode.LIST) return
        visibleTracks.getOrNull(prevIndex)?.let { track ->
            playTrack(track, clearQueue = false)
        }
    }

    fun playNext() {
        if (repeatMode == RepeatMode.ONE && currentTrack != null) {
            playTrack(currentTrack!!, clearQueue = false)
            return
        }
        if (!shuffleOn && repeatMode == RepeatMode.LIST &&
            queueRepeatSnapshot.size > 1 && queueRepeatIndex >= 0
        ) {
            val nextIndex = queueRepeatIndex + 1
            val wrappedIndex = if (nextIndex < queueRepeatSnapshot.size) nextIndex else 0
            val nextTrack = queueRepeatSnapshot[wrappedIndex]
            // Si el tema se borró del teléfono, invalidamos la repetición de cola.
            if (deviceTracks.none { it.uri == nextTrack.uri }) return
            playTrack(nextTrack, clearQueue = false)
            queueRepeatIndex = wrappedIndex
            queue.clear()
            queue.addAll(queueRepeatSnapshot.subList(queueRepeatIndex + 1, queueRepeatSnapshot.size))
            return
        }
        // Si Shuffle está activo, siempre usamos el orden aleatorio.
        if (shuffleOn && shuffleOrder.isNotEmpty() && shuffleIndex >= 0) {
            val nextIndex = shuffleIndex + 1
            if (nextIndex < shuffleOrder.size) {
                playTrack(shuffleOrder[nextIndex], clearQueue = false)
            } else if (repeatMode == RepeatMode.LIST && shuffleOrder.isNotEmpty()) {
                val lastUri = shuffleOrder.last().uri
                rebuildShuffleOrderFromPool()
                var order = shuffleOrder
                if (order.size > 1 && order.first().uri == lastUri) {
                    order = order.drop(1) + order.take(1)
                    shuffleOrder = order
                }
                shuffleIndex = 0
                refreshShuffleQueueMirror()
                syncQueueToService()
                playTrack(shuffleOrder.first(), clearQueue = false)
            }
            return
        }

        // 1) Si hay cola, consumimos lo primero encolado
        if (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            playTrack(next, clearQueue = false)
            return
        }

        // 2) Si no hay cola y Shuffle está apagado, navegamos dentro de la lista actual (según filtro/playlist)
        if (visibleTracks.isEmpty()) return
        val curUri = currentTrack?.uri ?: return
        val idx = visibleTracks.indexOfFirst { it.uri == curUri }
        val canAdvance = idx >= 0 && idx < visibleTracks.lastIndex
        if (!canAdvance) {
            if (repeatMode == RepeatMode.LIST) {
                visibleTracks.firstOrNull()?.let { playTrack(it, clearQueue = false) }
            }
            return
        }
        val nextIndex = idx + 1
        visibleTracks.getOrNull(nextIndex)?.let { track ->
            playTrack(track, clearQueue = false)
        }
    }

    fun finishDeletion(track: DeviceTrack, showToast: Boolean = true) {
        queue.removeAll { it.uri == track.uri }
        currentSessionQueueState()?.let { state ->
            applySessionQueueState(removeTrackFromSession(state, track.uri))
            rebuildPendingQueueFromSession()
        }
        if (shuffleOn) {
            val curUri = currentTrack?.uri
            val wasCurrent = curUri == track.uri
            shuffleOrder = shuffleOrder.filter { it.uri != track.uri }
            if (shuffleOrder.isEmpty() || wasCurrent) {
                shuffleIndex = -1
                queue.clear()
            } else if (curUri != null) {
                shuffleIndex = shuffleOrder.indexOfFirst { it.uri == curUri }.takeIf { it >= 0 } ?: 0
                refreshShuffleQueueMirror()
            }
        }
        syncQueueToService()
        if (currentTrack?.uri == track.uri) {
            currentTrack = null
            currentArtwork = null
            lyricsState = LyricsUiState.Empty(
                resources.getString(R.string.player_lyrics_select_song),
            )
            context.sendPlaybackForegroundAction(PlaybackService.ACTION_STOP)
        }
        viewModel.syncLibrary(auto = true)
        if (showToast) showToast(songDeletedText)
    }

    val deletionApprovalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            val track = pendingDeleteTrack ?: return@rememberLauncherForActivityResult
            if (result.resultCode == Activity.RESULT_OK) {
                finishDeletion(track)
            } else {
                showToast(deleteCancelledText)
            }
            pendingDeleteTrack = null
        },
    )

    fun isSafUri(uri: Uri): Boolean {
        val auth = uri.authority ?: return false
        return auth.contains("externalstorage") ||
            auth.contains("downloads") ||
            auth.contains("document") ||
            uri.pathSegments.firstOrNull() == "tree" ||
            uri.pathSegments.firstOrNull() == "document"
    }

    fun requestDeleteFromDevice(tracks: List<DeviceTrack>) {
        if (tracks.isEmpty()) return
        deleteConfirmTracks = tracks
    }

    fun deleteTrackFromDevice(
        track: DeviceTrack,
        syncAfter: Boolean = true,
        showToast: Boolean = true,
    ) {
        val uri = Uri.parse(track.uri)
        when (uri.scheme) {
            "file" -> {
                val path = uri.path
                val deleted = if (path.isNullOrBlank()) false else File(path).delete()
                if (deleted) finishDeletion(track, showToast)
                else if (showToast) showToast(resources.getString(R.string.player_delete_failed))
            }
            "content" -> {
                if (isSafUri(uri)) {
                    // URI de carpeta SAF: borrar via DocumentFile con permiso de árbol concedido
                    val deleted = runCatching {
                        DocumentFile.fromSingleUri(context, uri)?.delete() == true
                    }.getOrDefault(false)
                    if (deleted) finishDeletion(track, showToast)
                    else if (showToast) showToast(resources.getString(R.string.player_delete_failed_saf))
                } else {
                    // URI de MediaStore: intentar borrado directo y si falla pedir permiso al usuario
                    val directResult = runCatching {
                        context.contentResolver.delete(uri, null, null) > 0
                    }
                    if (directResult.getOrDefault(false)) {
                        finishDeletion(track, showToast)
                        return
                    }
                    val ex = directResult.exceptionOrNull()
                    // Pedir aprobación del sistema con el launcher correcto
                    try {
                        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ex is RecoverableSecurityException) {
                            ex.userAction.actionIntent.intentSender
                        } else {
                            if (showToast) showToast(resources.getString(R.string.player_delete_failed))
                            return
                        }
                        pendingDeleteTrack = track
                        deletionApprovalLauncher.launch(
                            IntentSenderRequest.Builder(sender).build()
                        )
                        if (showToast) showToast(resources.getString(R.string.player_delete_confirm_system))
                    } catch (_: Exception) {
                        if (showToast) showToast(resources.getString(R.string.player_delete_permission_error))
                    }
                }
            }
            else -> {
                val path = uri.path
                val deleted = if (path.isNullOrBlank()) false else File(path).delete()
                if (deleted) finishDeletion(track, showToast)
                else if (showToast) showToast(resources.getString(R.string.player_delete_failed))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
                Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                AppMiniBrand()
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Center,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false,
                        ),
                    ),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.player_search_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.player_search_clear_cd),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))

                LibraryFiltersMenu(
                    selectedSection = selectedSection,
                    selectedSort = sortOption,
                    onSelectSection = { selectedSection = it },
                    onSelectSort = { sortOption = it },
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (selectedSection == PlayerSection.Playlist) {
                    if (playlistDetailOpen && selectedPlaylistId != null) {
                        val openPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
                        PlaylistDetailHeader(
                            playlistName = openPlaylist?.name ?: "",
                            trackCount = openPlaylist?.songIds?.size ?: 0,
                            onBack = { playlistDetailOpen = false },
                            onRequestRename = { id ->
                                playlistRenameDialogId = id
                                val currentName = playlists.firstOrNull { it.id == id }?.name ?: ""
                                playlistRenameNewName = currentName
                            },
                            onRequestDelete = { id ->
                                playlistDeleteDialogId = id
                            },
                            playlistId = selectedPlaylistId!!,
                        )
                    } else {
                        PlaylistPickerBar(
                            playlists = playlists,
                            selectedPlaylistId = selectedPlaylistId,
                            onSelect = { id ->
                                selectedPlaylistId = id
                                playlistDetailOpen = true
                            },
                            onCreateEmpty = {
                                val res = viewModel.createPlaylist(
                                    resources.getString(R.string.player_default_playlist_name),
                                )
                                selectedPlaylistId = when (res) {
                                    is PlayerViewModel.CreatePlaylistResult.Created -> res.id
                                    is PlayerViewModel.CreatePlaylistResult.AlreadyExists -> res.id
                                }
                            },
                            onRequestDelete = { id ->
                                playlistDeleteDialogId = id
                            },
                            onRequestRename = { id ->
                                playlistRenameDialogId = id
                                val currentName = playlists.firstOrNull { it.id == id }?.name ?: ""
                                playlistRenameNewName = currentName
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                val showTracksArea = selectedSection != PlayerSection.Playlist || playlistDetailOpen

                val selectedTracksOrdered = visibleTracks.filter { selectedTrackUris.contains(it.uri) }
                if (selectionMode && showTracksArea) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.player_selection_count,
                                selectedTracksOrdered.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    selectedTrackUris = visibleTracks.map { it.uri }.toSet()
                                },
                                enabled = visibleTracks.isNotEmpty() &&
                                    selectedTracksOrdered.size < visibleTracks.size,
                            ) {
                                Text(stringResource(R.string.player_select_all))
                            }
                            TextButton(onClick = { clearTrackSelection() }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (showTracksArea) {
                    PrimaryPillButton(
                        text = stringResource(R.string.player_play_all_tracks),
                        onClick = {
                            if (visibleTracks.isEmpty()) return@PrimaryPillButton
                            val startTrack = if (shuffleOn) {
                                visibleTracks.shuffled(Random(System.currentTimeMillis())).first()
                            } else {
                                visibleTracks.first()
                            }
                            startPlaybackFromCollection(startTrack)
                        },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (showTracksArea) {
                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(Motion.STANDARD)) + slideInHorizontally { it / 6 }) togetherWith
                            (fadeOut(animationSpec = tween(Motion.QUICK)) + slideOutHorizontally { -it / 6 })
                    },
                    label = "player_section_content",
                ) { section ->
                    val tracksInSection = remember(
                        deviceTracks,
                        favoriteIds,
                        playlists,
                        selectedPlaylistId,
                        query,
                        section,
                        sortOption,
                    ) {
                        buildVisibleTracksForSection(
                            deviceTracks,
                            favoriteIds,
                            playlists,
                            selectedPlaylistId,
                            query,
                            section,
                            sortOption,
                        )
                    }
                    val activeListState = when (section) {
                        PlayerSection.Songs -> songsListState
                        PlayerSection.Favorites -> favoritesListState
                        PlayerSection.Playlist -> playlistListState
                    }
                    // Rail A-Z (U3). Solo tiene sentido con la lista ordenada por nombre: con
                    // cualquier otro orden las iniciales no serian monotonas y saltar a una
                    // letra dejaria al usuario en un sitio arbitrario.
                    val alphabetIndex = remember(tracksInSection, sortOption) {
                        if (sortOption != SortOption.NAME_ASC && sortOption != SortOption.NAME_DESC) {
                            emptyMap()
                        } else {
                            buildAlphabetIndex(tracksInSection.map { it.title })
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = activeListState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(
                            items = tracksInSection,
                            key = { it.uri },
                        ) { track ->
                        val currentPlaylist =
                            selectedPlaylistId?.let { pid -> playlists.firstOrNull { it.id == pid } }
                        val showRemoveFromPlaylist =
                            section == PlayerSection.Playlist &&
                                selectedPlaylistId != null &&
                                currentPlaylist?.songIds?.contains(track.id) == true
                        val isFavorite = favoriteIds.contains(track.id)
                        TrackRow(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isSelected = selectedTrackUris.contains(track.uri),
                            selectionMode = selectionMode,
                            showOverflowMenu = !selectionMode,
                            showSelectedActionsMenu = selectionMode && selectedTrackUris.contains(track.uri),
                            onEnterSelectionMode = {
                                enterSelectionMode(track.uri)
                                showToast(selectionModeEnabledText)
                            },
                            onToggleSelection = { toggleTrackSelection(track.uri) },
                            onPlayTrack = { startPlaybackFromCollection(track) },
                            onQueue = {
                                appendTracksToQueue(listOf(track))
                                showToast(queueAddedText)
                            },
                            onPlayNext = {
                                insertTracksPlayNext(listOf(track))
                                showToast(playNextAddedText)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            onAddToPlaylist = {
                                addToPlaylistDialogOpen = true
                                addToPlaylistTracks = listOf(track)
                                addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                addToPlaylistNewName = ""
                            },
                            onDeleteFromDevice = { requestDeleteFromDevice(listOf(track)) },
                            onBulkQueue = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                appendTracksToQueue(selectedTracksOrdered)
                                showToast(
                                    if (selectedTracksOrdered.size == 1) {
                                        queueAddedText
                                    } else {
                                        resources.getString(
                                            R.string.player_bulk_queue_added,
                                            selectedTracksOrdered.size,
                                        )
                                    },
                                )
                                clearTrackSelection()
                            },
                            onBulkPlayNext = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                insertTracksPlayNext(selectedTracksOrdered)
                                showToast(
                                    if (selectedTracksOrdered.size == 1) {
                                        playNextAddedText
                                    } else {
                                        resources.getString(
                                            R.string.player_bulk_play_next_added,
                                            selectedTracksOrdered.size,
                                        )
                                    },
                                )
                                clearTrackSelection()
                            },
                            onBulkToggleFavorite = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                selectedTracksOrdered.forEach { viewModel.toggleFavorite(it) }
                                clearTrackSelection()
                            },
                            onBulkAddToPlaylist = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                addToPlaylistDialogOpen = true
                                addToPlaylistTracks = selectedTracksOrdered
                                addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                addToPlaylistNewName = ""
                                addToPlaylistPickerExpanded = false
                            },
                            onBulkDeleteFromDevice = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                requestDeleteFromDevice(selectedTracksOrdered)
                            },
                            bulkSelectionCount = selectedTracksOrdered.size,
                            showBulkRemoveFromPlaylist = section == PlayerSection.Playlist && selectedPlaylistId != null,
                            onBulkRemoveFromPlaylist = {
                                val pid = selectedPlaylistId ?: return@TrackRow
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                selectedTracksOrdered.forEach { tr ->
                                    viewModel.removeSongFromPlaylist(
                                        trackId = tr.id,
                                        playlistId = pid,
                                        removeAllOccurrences = true,
                                    )
                                }
                                showToast(
                                    if (selectedTracksOrdered.size == 1) {
                                        resources.getString(R.string.player_playlist_removed_one)
                                    } else {
                                        resources.getString(R.string.player_playlist_removed_many)
                                    },
                                )
                                clearTrackSelection()
                            },
                            isFavorite = isFavorite,
                            showRemoveFromPlaylist = showRemoveFromPlaylist,
                            onRemoveFromPlaylist = {
                                selectedPlaylistId?.let { pid ->
                                    viewModel.removeSongFromPlaylist(
                                        trackId = track.id,
                                        playlistId = pid,
                                        removeAllOccurrences = true,
                                    )
                                }
                            },
                        )
                    }
                }
                    if (alphabetIndex.size >= 2) {
                        AlphabetFastScroller(
                            letters = alphabetIndex.keys.toList(),
                            onLetterSelected = { letter ->
                                alphabetIndex[letter]?.let { target ->
                                    uiScope.launch { activeListState.scrollToItem(target) }
                                }
                            },
                        )
                    }
                }
                }
                }
            }

            AnimatedVisibility(
                visible = currentTrack != null,
                modifier = Modifier.fillMaxWidth(),
                enter = slideInVertically { it } + fadeIn(animationSpec = tween(Motion.STANDARD)),
                exit = slideOutVertically { it } + fadeOut(animationSpec = tween(Motion.QUICK)),
            ) {
                MiniPlayerBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                track = currentTrack,
                isPlaying = isPlaying,
                position = sliderPosition,
                artwork = currentArtwork,
                queueSize = playbackQueueTotalCount(),
                sliderAccessibilityLabel = miniSliderA11y,
                onTogglePlay = {
                    currentTrack ?: return@MiniPlayerBar
                    context.sendPlaybackForegroundAction(
                        if (isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY,
                    )
                },
                onPrev = {
                    context.sendPlaybackForegroundAction(PlaybackService.ACTION_PREV)
                },
                onNext = {
                    context.sendPlaybackForegroundAction(PlaybackService.ACTION_NEXT)
                },
                onSeekPreview = { newPos -> sliderDragPos = newPos },
                onSeekCommit = { finalPos ->
                    if (playbackDurationMs > 0) {
                        boundService?.seekTo((playbackDurationMs * finalPos).toLong())
                    }
                    sliderDragPos = null
                },
                onOpenExpanded = { isExpanded = true },
                onOpenQueue = { queueSheetOpen = true },
                )
            }
        }

            // OVERLAY EXPANDIDO (boceto 2)
            if (isExpanded) {
                val lyricsLoadingForCurrent = currentTrack?.uri in lyricsDownloadingUris
                val canRefreshLyrics =
                    !lyricsLoadingForCurrent &&
                        currentTrack != null &&
                        lyricsState !is LyricsUiState.Loading &&
                        lyricsState !is LyricsUiState.Idle
                val canDeleteLyrics =
                    !lyricsLoadingForCurrent && lyricsState is LyricsUiState.Ready
                val lyricsPositionMs = remember(sliderDragPos, playbackPositionMs, playbackDurationMs) {
                    sliderDragPos?.let { (it.coerceIn(0f, 1f) * playbackDurationMs).toLong() }
                        ?: playbackPositionMs
                }
                ExpandedPlayerOverlay(
                    modifier = Modifier.fillMaxSize(),
                    track = currentTrack,
                    artwork = currentArtwork,
                    lyricsState = lyricsState,
                    lyricsPositionMs = lyricsPositionMs,
                    isPlaying = isPlaying,
                    position = sliderPosition,
                    onClose = { isExpanded = false },
                    onTogglePlay = {
                        currentTrack ?: return@ExpandedPlayerOverlay
                        context.sendPlaybackForegroundAction(
                            if (isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY,
                        )
                    },
                    onPrev = {
                        context.sendPlaybackForegroundAction(PlaybackService.ACTION_PREV)
                    },
                    onNext = {
                        context.sendPlaybackForegroundAction(PlaybackService.ACTION_NEXT)
                    },
                    onSeekPreview = { newPos -> sliderDragPos = newPos },
                    onSeekCommit = { finalPos ->
                        if (playbackDurationMs > 0) {
                            boundService?.seekTo((playbackDurationMs * finalPos).toLong())
                        }
                        sliderDragPos = null
                    },
                    onSeekToLyricsPosition = { ms ->
                        boundService?.seekTo(ms)
                        sliderDragPos = null
                    },
                    shuffleOn = shuffleOn,
                    repeatMode = repeatMode,
                    onToggleShuffle = { onToggleShuffle() },
                    onToggleRepeat = { onCycleRepeatMode() },
                    canRefreshLyrics = canRefreshLyrics,
                    canDeleteLyrics = canDeleteLyrics,
                    onRefreshLyrics = {
                        currentTrack?.let { downloadLyricsIfNeeded(it, forceRefresh = true) }
                    },
                    onDeleteLyrics = {
                        currentTrack?.let { deleteLyricsForTrack(it) }
                    },
                    karaokeMode = karaokeMode,
                    onKaraokeModeChange = { viewModel.setKaraokeMode(it) },
                    karaokePitchSemitones = karaokePitchSemitones,
                    karaokeSpeed = karaokeSpeed,
                    onKaraokePitchChange = { viewModel.setKaraokePitchSemitones(it) },
                    onKaraokeSpeedChange = { viewModel.setKaraokeSpeed(it) },
                    onResetKaraokeTuning = { viewModel.resetKaraokeTuning() },
                )
            }

            // SHEET: Cola de reproducción en tiempo real
            if (queueSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = { queueSheetOpen = false },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.player_queue_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.player_queue_total,
                                        playbackQueueTotalCount(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                            // displayQueue: en shuffle muestra el resto del orden aleatorio
                            // derivado de la posición real de currentTrack (más reactivo que shuffleIndex).
                            val displayQueue = if (shuffleOn && shuffleOrder.isNotEmpty()) {
                                val ct = currentTrack
                                val pos = if (ct != null) {
                                    shuffleOrder.indexOfFirst { it.uri == ct.uri }
                                        .takeIf { it >= 0 } ?: shuffleIndex
                                } else shuffleIndex
                                shuffleOrder.drop((pos + 1).coerceAtLeast(0))
                            } else {
                                queue.toList()
                            }

                            if (displayQueue.isNotEmpty()) {
                                TextButton(onClick = {
                                    clearPlaybackQueueState(keepCurrentInShuffle = shuffleOn)
                                }) {
                                    Text(stringResource(R.string.player_clear_queue))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val displayQueue = if (shuffleOn && shuffleOrder.isNotEmpty()) {
                            val ct = currentTrack
                            val pos = if (ct != null) {
                                shuffleOrder.indexOfFirst { it.uri == ct.uri }
                                    .takeIf { it >= 0 } ?: shuffleIndex
                            } else shuffleIndex
                            shuffleOrder.drop((pos + 1).coerceAtLeast(0))
                        } else {
                            queue.toList()
                        }

                        if (currentTrack != null) {
                            Text(
                                text = stringResource(R.string.player_now_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.sm)),
                                    ) {
                                        ArtworkThumbnail(track = currentTrack!!, sizeDp = 36)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentTrack!!.title.toTitleCaseSimple(),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = currentTrack!!.artist.toDisplayArtist(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (displayQueue.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.player_queue_empty_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.player_queue_up_next, displayQueue.size) +
                                    if (shuffleOn) " • ${stringResource(R.string.player_shuffle_suffix)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(0.6f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(displayQueue.size, key = { idx -> displayQueue[idx].id.toString() + idx }) { idx ->
                                    val t = displayQueue[idx]
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                modifier = Modifier.width(20.dp),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.sm)),
                                            ) {
                                                ArtworkThumbnail(track = t, sizeDp = 34)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = t.title.toTitleCaseSimple(),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = t.artist.toDisplayArtist(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            // Saltar directamente a esta canción
                                            IconButton(
                                                onClick = {
                                                    if (!shuffleOn) {
                                                        val remaining = queue.drop(idx + 1)
                                                        repeat(queue.size) { queue.removeAt(0) }
                                                        queue.addAll(remaining)
                                                    }
                                                    playTrack(t, clearQueue = false)
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = stringResource(R.string.player_play_now_cd),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            // Quitar de la cola (solo en modo no-shuffle)
                                            if (!shuffleOn) {
                                                IconButton(
                                                    onClick = {
                                                        queue.removeAt(idx)
                                                        syncQueueToService()
                                                    },
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.player_remove_from_queue_cd),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DIALOG: Añadir a playlist (crear o seleccionar)
            if (addToPlaylistDialogOpen && addToPlaylistTracks.isNotEmpty()) {
                val tracksToAdd = addToPlaylistTracks
                val currentSelectedId = addToPlaylistExistingId
                    ?: selectedPlaylistId
                    ?: playlists.firstOrNull()?.id

                ModalBottomSheet(
                    onDismissRequest = {
                        addToPlaylistDialogOpen = false
                        addToPlaylistPickerExpanded = false
                        addToPlaylistTracks = emptyList()
                        duplicateDialog = null
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_add_to_playlist_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (playlists.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.player_playlist_target),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                playlists.forEach { p ->
                                    val selected = p.id == (addToPlaylistExistingId ?: currentSelectedId)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { addToPlaylistExistingId = p.id },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainer
                                            },
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.player_playlist_song_count,
                                                    p.name,
                                                    p.songIds.size,
                                                ),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (selected) {
                                                Text(
                                                    text = stringResource(R.string.player_playlist_selected),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = addToPlaylistNewName,
                            onValueChange = { addToPlaylistNewName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.player_new_playlist_optional)) },
                            placeholder = { Text(stringResource(R.string.player_new_playlist_placeholder)) },
                        )

                        Text(
                            text = stringResource(R.string.player_new_playlist_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    addToPlaylistDialogOpen = false
                                    addToPlaylistTracks = emptyList()
                                    duplicateDialog = null
                                },
                            ) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            TextButton(
                                onClick = {
                                val newName = addToPlaylistNewName.trim()
                                val chosenId =
                                    addToPlaylistExistingId
                                        ?: selectedPlaylistId
                                        ?: playlists.firstOrNull()?.id

                                val targetPlaylistId = if (newName.isNotBlank()) {
                                    val res = viewModel.createPlaylist(newName)
                                    when (res) {
                                        is PlayerViewModel.CreatePlaylistResult.Created -> {
                                            selectedPlaylistId = res.id
                                            showToast(resources.getString(R.string.player_playlist_created, newName))
                                            res.id
                                        }

                                        is PlayerViewModel.CreatePlaylistResult.AlreadyExists -> {
                                            showToast(resources.getString(R.string.player_playlist_name_exists))
                                            selectedPlaylistId = res.id
                                            res.id
                                        }
                                    }
                                } else {
                                    val existingId = chosenId
                                    if (existingId == null) {
                                        // UX: si no hay playlist todavía, crear una por defecto y continuar.
                                        when (val created = viewModel.createPlaylist(
                                            resources.getString(R.string.player_default_playlist_name),
                                        )) {
                                            is PlayerViewModel.CreatePlaylistResult.Created -> {
                                                selectedPlaylistId = created.id
                                                showToast(resources.getString(R.string.player_playlist_default_created))
                                                created.id
                                            }
                                            is PlayerViewModel.CreatePlaylistResult.AlreadyExists -> {
                                                selectedPlaylistId = created.id
                                                created.id
                                            }
                                        }
                                    } else {
                                        existingId
                                    }
                                }

                                val duplicateIds = mutableListOf<Long>()
                                var anyAdded = false

                                tracksToAdd.forEach { track ->
                                    val addRes = viewModel.addToPlaylist(
                                        track = track,
                                        playlistId = targetPlaylistId,
                                        allowDuplicate = false,
                                    )
                                    when (addRes) {
                                        is PlayerViewModel.AddToPlaylistResult.Added -> {
                                            anyAdded = true
                                        }

                                        is PlayerViewModel.AddToPlaylistResult.AlreadyExists -> {
                                            duplicateIds.add(track.id)
                                        }
                                    }
                                }

                                selectedPlaylistId = targetPlaylistId
                                addToPlaylistDialogOpen = false
                                addToPlaylistTracks = emptyList()

                                if (duplicateIds.isNotEmpty()) {
                                    duplicateDialog = DuplicateConfirmState(
                                        trackIds = duplicateIds.distinct(),
                                        playlistId = targetPlaylistId,
                                    )
                                } else if (anyAdded) {
                                    showToast(
                                        if (tracksToAdd.size == 1) {
                                            resources.getString(R.string.player_track_added_playlist)
                                        } else {
                                            resources.getString(R.string.player_tracks_added_playlist)
                                        },
                                    )
                                    clearTrackSelection()
                                }
                                },
                            ) {
                                Text(stringResource(R.string.player_add_button))
                            }
                        }
                    }
                }
            }

            // DIALOG: confirmar eliminación de canción(es) del dispositivo
            deleteConfirmTracks?.let { tracksToDelete ->
                val count = tracksToDelete.size
                val firstTitle = tracksToDelete.first().title
                AlertDialog(
                    onDismissRequest = { deleteConfirmTracks = null },
                    title = { Text(stringResource(R.string.player_delete_confirm_title)) },
                    text = {
                        Text(
                            if (count == 1) {
                                stringResource(R.string.player_delete_confirm_message, firstTitle)
                            } else {
                                stringResource(R.string.player_delete_confirm_message_plural, count)
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val batch = deleteConfirmTracks ?: return@TextButton
                                deleteConfirmTracks = null
                                batch.forEach { track ->
                                    deleteTrackFromDevice(track, syncAfter = false, showToast = false)
                                }
                                viewModel.syncLibrary(auto = true)
                                showToast(
                                    if (batch.size == 1) {
                                        songDeletedText
                                    } else {
                                        resources.getString(
                                            R.string.player_bulk_deleted,
                                            batch.size,
                                        )
                                    },
                                )
                                clearTrackSelection()
                            },
                        ) {
                            Text(stringResource(R.string.player_delete_confirm_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmTracks = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            // DIALOG: confirmación duplicado en playlist
            if (duplicateDialog != null) {
                val d = duplicateDialog!!
                val count = d.trackIds.distinct().size
                AlertDialog(
                    onDismissRequest = { duplicateDialog = null },
                    title = { Text(stringResource(R.string.player_duplicates_title)) },
                    text = {
                        Text(
                            stringResource(R.string.player_duplicates_message, count)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                d.trackIds.distinct().forEach { tid ->
                                    val track = deviceTracks.firstOrNull { it.id == tid } ?: return@forEach
                                    viewModel.addToPlaylist(
                                        track = track,
                                        playlistId = d.playlistId,
                                        allowDuplicate = true,
                                    )
                                }
                                showToast(resources.getString(R.string.player_duplicates_added))
                                duplicateDialog = null
                                clearTrackSelection()
                            },
                        ) {
                            Text(stringResource(R.string.player_duplicate_action))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                duplicateDialog = null
                                showToast(resources.getString(R.string.player_skip_duplicates_snack))
                                clearTrackSelection()
                            },
                        ) {
                            Text(stringResource(R.string.player_skip_duplicates))
                        }
                    },
                )
            }

            // DIALOG: eliminar playlist
            if (playlistDeleteDialogId != null) {
                val id = playlistDeleteDialogId!!
                val name = playlists.firstOrNull { it.id == id }?.name ?: "Playlist"
                AlertDialog(
                    onDismissRequest = { playlistDeleteDialogId = null },
                    title = { Text(stringResource(R.string.player_delete_playlist_title)) },
                    text = { Text(stringResource(R.string.player_delete_playlist_message, name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val ok = viewModel.deletePlaylist(id)
                                playlistDeleteDialogId = null
                                if (ok && id == selectedPlaylistId) {
                                    playlistDetailOpen = false
                                    selectedPlaylistId = playlists.firstOrNull { it.id != id }?.id
                                }
                                if (!ok) {
                                    showToast(resources.getString(R.string.player_delete_playlist_failed))
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistDeleteDialogId = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            // DIALOG: renombrar playlist
            if (playlistRenameDialogId != null) {
                val id = playlistRenameDialogId!!
                val currentName = playlists.firstOrNull { it.id == id }?.name ?: ""
                AlertDialog(
                    onDismissRequest = { playlistRenameDialogId = null },
                    title = { Text(stringResource(R.string.player_edit_playlist_title)) },
                    text = {
                        OutlinedTextField(
                            value = playlistRenameNewName,
                            onValueChange = { playlistRenameNewName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.player_edit_playlist_name)) },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val newName = playlistRenameNewName.trim()
                                if (newName.isBlank()) {
                                    showToast(resources.getString(R.string.player_edit_playlist_invalid_name))
                                    return@TextButton
                                }

                                when (val res = viewModel.renamePlaylist(id, newName)) {
                                    is PlayerViewModel.RenamePlaylistResult.Renamed -> {
                                        playlistRenameDialogId = null
                                    }

                                    is PlayerViewModel.RenamePlaylistResult.AlreadyExists -> {
                                        showToast(resources.getString(R.string.player_playlist_name_exists))
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.player_save_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistRenameDialogId = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }
    }
}

