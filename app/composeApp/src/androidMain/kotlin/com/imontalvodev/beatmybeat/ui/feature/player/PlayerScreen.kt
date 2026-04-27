package com.imontalvodev.beatmybeat.ui.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.provider.MediaStore
import androidx.media3.common.Player
import java.io.File
import java.net.URLDecoder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.MiddlewareApi
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.service.PlaybackService
import com.imontalvodev.beatmybeat.service.BeatMyBeatForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerScreen(
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {},
    onOpenDownloader: () -> Unit = {},
) {
    val palette = currentBeatMyBeatThemeProfile()
    val viewModel: PlayerViewModel = viewModel()
    val deviceTracks = viewModel.tracks.collectAsState().value
    val favoriteIds = viewModel.favoriteIds.collectAsState().value
    val playlists = viewModel.playlists.collectAsState().value
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
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
                Toast.makeText(
                    context,
                    "Permiso de música denegado. Solo verás descargas de BeatMyBeat.",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.syncLibrary(auto = true)
            }
        },
    )

    val queue = remember { mutableStateListOf<DeviceTrack>() }

    var currentTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }

    // ── Bound service: referencia directa al PlaybackService ────────────────
    // Con bindService obtenemos una referencia al objeto PlaybackService en memoria.
    // Esto permite llamar player.seekTo(ms) sin ningún intent de por medio.
    var boundService by remember { mutableStateOf<PlaybackService?>(null) }

    // Cola pendiente si el usuario pulsa play antes de que el servicio esté ligado.
    data class PendingPlay(val queueJson: String, val index: Int, val shuffleEnabled: Boolean)
    var pendingPlay by remember { mutableStateOf<PendingPlay?>(null) }

    DisposableEffect(context) {
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, b: IBinder?) {
                val svc = (b as? PlaybackService.LocalBinder)?.service
                boundService = svc
                // Si había una reproducción pendiente, la ejecutamos ahora.
                val pending = pendingPlay
                if (svc != null && pending != null) {
                    svc.loadQueue(
                        queueJson = pending.queueJson,
                        startIndex = pending.index,
                        shuffleEnabled = pending.shuffleEnabled,
                    )
                    pendingPlay = null
                }
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                boundService = null
            }
        }
        val intent = Intent(context, PlaybackService::class.java)
        context.bindService(intent, conn, android.content.Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(conn) }
    }

    // ── Playback state: única fuente de verdad ──────────────────────────────
    // StateFlow del servicio: reactivo, sin polling, sin variables @Volatile.
    val playbackState by PlaybackService.state.collectAsState()
    val isPlaying = playbackState.isPlaying
    val playbackPositionMs = playbackState.positionMs
    val playbackDurationMs = playbackState.durationMs
    val playbackMediaId = playbackState.currentMediaId

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

    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    // Repetición de la cola (cuando Shuffle está OFF y repeatMode == LIST)
    var queueRepeatSnapshot by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var queueRepeatIndex by remember { mutableStateOf(-1) }
    var shuffleOrder by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var shuffleIndex by remember { mutableStateOf(-1) }

    // Serializa la cola de "próximas canciones" y la envía al servicio para que
    // la notificación (Next/Prev) navegue por los mismos ítems que ve la UI.
    // En modo shuffle usa el resto de shuffleOrder; en modo normal, la cola manual.
    fun syncQueueToService() {
        val svc = boundService ?: return
        val nextTracks = if (shuffleOn && shuffleOrder.isNotEmpty()) {
            shuffleOrder.drop((shuffleIndex + 1).coerceAtLeast(0))
        } else {
            queue.toList()
        }
        val arr = JSONArray()
        nextTracks.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("uri", t.uri)
            o.put("title", t.title)
            o.put("artist", t.artist)
            arr.put(o)
        }
        svc.syncNextItems(arr.toString())
    }

    var pendingDeleteTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    val deletionApprovalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            val track = pendingDeleteTrack ?: return@rememberLauncherForActivityResult
            if (result.resultCode == Activity.RESULT_OK) {
                queue.removeAll { it.id == track.id }
                syncQueueToService()
                if (currentTrack?.id == track.id) {
                    currentTrack = null
                    currentArtwork = null
                    lyricsState = LyricsUiState.Empty("Selecciona una canción")
                    BeatMyBeatForegroundService.stopPlayback(context)
                }
                viewModel.syncLibrary(auto = true)
                Toast.makeText(context, "Canción eliminada.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Eliminación cancelada.", Toast.LENGTH_SHORT).show()
            }
            pendingDeleteTrack = null
        },
    )

    // Si el usuario está viendo el overlay expandido (letra),
    // el botón Atrás debe cerrar el overlay en vez de navegar fuera del player.
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    // Mantener el shuffle del servicio (notificación) en sincronía con la UI.
    LaunchedEffect(boundService, shuffleOn) {
        boundService?.player?.shuffleModeEnabled = shuffleOn
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

    // Sincronizar canción mostrada con la canción real del servicio/notificación.
    // Cuando la notificación avanza (Next/Prev), el servicio emite un nuevo mediaId;
    // si ese id coincide con el primer ítem de la cola local, lo consumimos para
    // que la UI refleje correctamente cuántas canciones quedan.
    LaunchedEffect(playbackMediaId, deviceTracks) {
        val id = playbackMediaId.toLongOrNull() ?: return@LaunchedEffect
        val track = deviceTracks.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (currentTrack?.id != id) {
            // El servicio avanzó externamente (notificación); consumir de la cola local
            // solo si el nuevo ítem era el siguiente previsto.
            if (queue.firstOrNull()?.id == id) {
                queue.removeAt(0)
                // No llamamos syncQueueToService aquí porque ExoPlayer ya avanzó
                // internamente; solo actualizamos la representación local.
            }
        }
        currentTrack = track
        currentIndex = deviceTracks.indexOfFirst { it.id == id }
    }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedTrackIds.isNotEmpty()
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

    fun toggleTrackSelection(trackId: Long) {
        selectedTrackIds = if (selectedTrackIds.contains(trackId)) {
            selectedTrackIds - trackId
        } else {
            selectedTrackIds + trackId
        }
    }

    fun clearTrackSelection() {
        selectedTrackIds = emptySet()
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

    // Al cambiar de sección/playlist limpiamos la cola para evitar saltos raros
    LaunchedEffect(selectedSection, selectedPlaylistId) {
        queue.clear()
        syncQueueToService()
        queueRepeatSnapshot = emptyList()
        queueRepeatIndex = -1
        selectedTrackIds = emptySet()
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
        Surface(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.backgroundTop, palette.backgroundBottom),
                    ),
                ),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No se encontraron canciones en el dispositivo.",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    // Cargar carátula: primero tags embebidos, luego .meta.json con artworkBase64
    LaunchedEffect(currentTrack?.id) {
        if (currentTrack == null) {
            currentArtwork = null
            return@LaunchedEffect
        }

        val cached = ArtworkCache.get(currentTrack!!.id)
        if (cached != null) {
            currentArtwork = cached
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            // 1) Tags embebidos en el archivo
            val embedded = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(currentTrack!!.uri))
                    val data = retriever.embeddedPicture
                    if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
                } finally { retriever.release() }
            }.getOrNull()

            if (embedded != null) return@withContext embedded

            // 2) Fallback: artworkBase64 en el .meta.json del .music privado
            runCatching {
                val uri = Uri.parse(currentTrack!!.uri)
                val audioFile = java.io.File(uri.path ?: return@runCatching null)
                val metaFile = java.io.File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.meta.json")
                if (!metaFile.exists()) return@runCatching null
                val b64 = org.json.JSONObject(metaFile.readText()).optString("artworkBase64")
                if (b64.isBlank()) return@runCatching null
                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        currentArtwork = loaded
        if (loaded != null) ArtworkCache.put(currentTrack!!.id, loaded)
    }

    // Letras: solo caché local (offline-first). Se rellenan al descargar.
    var lyricsDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(currentTrack?.id) {
        val t = currentTrack ?: run {
            lyricsState = LyricsUiState.Empty("Selecciona una canción")
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
            lyricsState = LyricsUiState.Empty("No hay letra disponible para esta canción")
            return@LaunchedEffect
        }

        // Intentar caché local (offline-friendly)
        val cached = withContext(Dispatchers.IO) {
            LyricsCache.get(context, title, artist)
        }
        if (!cached.isNullOrBlank()) {
            lyricsState = LyricsUiState.Ready(cached)
            return@LaunchedEffect
        }
        lyricsState = LyricsUiState.Empty("Toca para descargar la letra (necesitas internet).")
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

    fun downloadLyricsIfNeeded(track: DeviceTrack) {
        if (lyricsDownloading) return

        lyricsDownloading = true
        lyricsState = LyricsUiState.Loading

        uiScope.launch {
            try {
                // Leer metadatos reales desde .meta.json si existen
                val (title, artist) = withContext(Dispatchers.IO) { resolveTrackMeta(track) }

                if (isUnknown(title) || isUnknown(artist)) {
                    lyricsState = LyricsUiState.Empty("No hay letra disponible para esta canción")
                    return@launch
                }

                // 1) Caché local (offline-first)
                val cached = withContext(Dispatchers.IO) { LyricsCache.get(context, title, artist) }
                if (!cached.isNullOrBlank()) {
                    lyricsState = LyricsUiState.Ready(cached)
                    return@launch
                }

                // 2) Varios candidatos de título limpio
                val uriTitle = titleFromUri(track.uri)
                val attempts = listOf(title, sanitizeTitle(title), uriTitle, sanitizeTitle(uriTitle))
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct()

                // 3) lyrics.ovh directo (sin servidor)
                for (candidate in attempts) {
                    val res = runCatching {
                        withContext(Dispatchers.IO) {
                            MiddlewareApi.fetchLyricsDirect(title = candidate, artist = artist)
                        }
                    }.getOrNull()

                    if (res != null && res.success && res.lyrics.isNotBlank()) {
                        withContext(Dispatchers.IO) { LyricsCache.put(context, title, artist, res.lyrics) }
                        lyricsState = LyricsUiState.Ready(res.lyrics)
                        return@launch
                    }
                }

                lyricsState = LyricsUiState.Empty("No hay letra disponible para esta canción")
            } finally {
                lyricsDownloading = false
            }
        }
    }

    // (Helper eliminado: ya no se usa en el fallback de letras bajo demanda)

    val bgBrush = Brush.verticalGradient(colors = listOf(palette.backgroundTop, palette.backgroundBottom))

    val visibleTracks = remember(deviceTracks, favoriteIds, playlists, selectedPlaylistId, query, selectedSection) {
        val trackById = deviceTracks.associateBy { it.id }
        val base = when (selectedSection) {
            PlayerSection.Songs -> deviceTracks
            PlayerSection.Favorites -> deviceTracks.filter { favoriteIds.contains(it.id) }
            PlayerSection.Playlist -> {
                val playlist = playlists.firstOrNull { it.id == selectedPlaylistId }
                if (playlist == null) emptyList()
                else playlist.songIds.mapNotNull { trackById[it] }
            }
        }
        if (query.isBlank()) base
        else {
            val q = query.trim().lowercase()
            base.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    (it.album ?: "").lowercase().contains(q)
            }
        }
    }.let { tracks ->
        when (sortOption) {
            SortOption.NAME_ASC -> tracks.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> tracks.sortedByDescending { it.title.lowercase() }
            SortOption.NEWEST_FIRST -> tracks.sortedByDescending { it.dateAddedMs }
            SortOption.OLDEST_FIRST -> tracks.sortedBy { it.dateAddedMs }
        }
    }

    fun onToggleShuffle() {
        val next = !shuffleOn
        shuffleOn = next
        // Sincronizar con ExoPlayer (servicio/notificación) para que Next/Prev
        // en la barra del sistema respete el mismo modo aleatorio que la UI.
        boundService?.player?.shuffleModeEnabled = next
        if (shuffleOn) {
            queueRepeatSnapshot = emptyList()
            queueRepeatIndex = -1
            val base = visibleTracks.toMutableList()
            currentTrack?.let { ct ->
                if (base.none { it.id == ct.id }) base.add(ct)
            }
            shuffleOrder = base.shuffled(Random(System.currentTimeMillis()))
            shuffleIndex =
                currentTrack?.id?.let { id -> shuffleOrder.indexOfFirst { it.id == id } } ?: -1
            if (shuffleIndex < 0 && shuffleOrder.isNotEmpty()) shuffleIndex = 0
            // Sincronizar el nuevo orden con el servicio para que la notificación
            // pueda avanzar por las canciones en el mismo orden aleatorio que la UI.
            syncQueueToService()
        } else {
            shuffleOrder = emptyList()
            shuffleIndex = -1
            // Al salir de random, limpiamos estados derivados para volver
            // a navegación normal por lista desde la canción actual.
            queue.clear()
            syncQueueToService()
            queueRepeatSnapshot = emptyList()
            queueRepeatIndex = -1
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

    // Si cambia la lista visible (filtros/búsqueda/playlist) y Shuffle está ON,
    // regeneramos el orden aleatorio sin tocar el track actual.
    LaunchedEffect(visibleTracks, shuffleOn) {
        if (!shuffleOn) return@LaunchedEffect
        val base = visibleTracks.toMutableList()
        currentTrack?.let { ct ->
            if (base.none { it.id == ct.id }) base.add(ct)
        }
        shuffleOrder = base.shuffled(Random(System.currentTimeMillis()))
        val id = currentTrack?.id
        shuffleIndex = if (id == null) -1 else shuffleOrder.indexOfFirst { it.id == id }
        if (shuffleIndex < 0 && shuffleOrder.isNotEmpty()) shuffleIndex = 0
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
                o.put("id", id)
                o.put("uri", uri)
                o.put("title", title)
                o.put("artist", artist)
            }
            // En shuffle: cargamos [track] + resto de shuffleOrder para que la
            // notificación pueda avanzar sin repeticiones.
            // En modo normal: [track] + cola manual.
            val pendingShuffleIdx = if (shuffleOn) shuffleOrder.indexOfFirst { it.id == track.id } else -1
            val nextItems = when {
                shuffleOn && pendingShuffleIdx >= 0 ->
                    shuffleOrder.drop(pendingShuffleIdx + 1)
                shuffleOn && shuffleOrder.isNotEmpty() ->
                    shuffleOrder  // fallback: toda la lista si el track no está en shuffleOrder
                else -> queue.toList()
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
            currentIndex = deviceTracks.indexOfFirst { it.id == track.id }
            // isPlaying se actualiza automáticamente vía PlaybackService.state

            // Si Shuffle está activado, ajustamos el índice sin rebarajar para mantener bidireccionalidad.
            if (shuffleOn) {
                shuffleIndex = shuffleOrder.indexOfFirst { it.id == track.id }
                if (shuffleIndex < 0) {
                    val base = visibleTracks.toMutableList()
                    if (currentTrack != null && base.none { it.id == currentTrack!!.id }) {
                        base.add(currentTrack!!)
                    }
                    shuffleOrder = base.shuffled(Random(System.currentTimeMillis()))
                    shuffleIndex = shuffleOrder.indexOfFirst { it.id == track.id }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo reproducir este archivo.", Toast.LENGTH_SHORT).show()
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
            if (deviceTracks.none { it.id == nextTrack.id }) return
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

        val curId = currentTrack?.id ?: return
        val idx = visibleTracks.indexOfFirst { it.id == curId }
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
            if (deviceTracks.none { it.id == nextTrack.id }) return
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
                // Final de la lista aleatoria: re-barajar para que la nueva pasada
                // no empiece por el mismo tema que terminó la anterior.
                val lastId = shuffleOrder.last().id
                val base = visibleTracks.toMutableList()
                currentTrack?.let { ct -> if (base.none { it.id == ct.id }) base.add(ct) }
                var newOrder = base.shuffled(Random(System.currentTimeMillis()))
                if (newOrder.firstOrNull()?.id == lastId && newOrder.size > 1) {
                    newOrder = newOrder.drop(1) + newOrder.first()
                }
                shuffleOrder = newOrder
                shuffleIndex = 0
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
        val curId = currentTrack?.id ?: return
        val idx = visibleTracks.indexOfFirst { it.id == curId }
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
        queue.removeAll { it.id == track.id }
        syncQueueToService()
        if (currentTrack?.id == track.id) {
            currentTrack = null
            currentArtwork = null
            lyricsState = LyricsUiState.Empty("Selecciona una canción")
            BeatMyBeatForegroundService.stopPlayback(context)
        }
        viewModel.syncLibrary(auto = true)
        if (showToast) Toast.makeText(context, "Canción eliminada.", Toast.LENGTH_SHORT).show()
    }

    fun isSafUri(uri: Uri): Boolean {
        val auth = uri.authority ?: return false
        return auth.contains("externalstorage") ||
            auth.contains("downloads") ||
            auth.contains("document") ||
            uri.pathSegments.firstOrNull() == "tree" ||
            uri.pathSegments.firstOrNull() == "document"
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
                else if (showToast) Toast.makeText(context, "No se pudo eliminar la canción.", Toast.LENGTH_SHORT).show()
            }
            "content" -> {
                if (isSafUri(uri)) {
                    // URI de carpeta SAF: borrar via DocumentFile con permiso de árbol concedido
                    val deleted = runCatching {
                        DocumentFile.fromSingleUri(context, uri)?.delete() == true
                    }.getOrDefault(false)
                    if (deleted) finishDeletion(track, showToast)
                    else if (showToast) Toast.makeText(context, "No se pudo eliminar (SAF).", Toast.LENGTH_SHORT).show()
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
                            if (showToast) Toast.makeText(context, "No se pudo eliminar la canción.", Toast.LENGTH_SHORT).show()
                            return
                        }
                        pendingDeleteTrack = track
                        deletionApprovalLauncher.launch(
                            IntentSenderRequest.Builder(sender).build()
                        )
                        if (showToast) Toast.makeText(context, "Confirma la eliminación en el diálogo del sistema.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        if (showToast) Toast.makeText(context, "Error solicitando permiso de eliminación.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                val path = uri.path
                val deleted = if (path.isNullOrBlank()) false else File(path).delete()
                if (deleted) finishDeletion(track, showToast)
                else if (showToast) Toast.makeText(context, "No se pudo eliminar la canción.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // LISTA (boceto 1)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                AppMiniBrand()
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = when (selectedSection) {
                                PlayerSection.Songs -> "LISTA CANCIONES"
                                PlayerSection.Favorites -> "FAVORITOS"
                                PlayerSection.Playlist -> {
                                    val name = playlists.firstOrNull { it.id == selectedPlaylistId }?.name
                                    name ?: "PLAYLIST"
                                }
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Downloader",
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable { onOpenDownloader() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { onOpenProfile() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Perfil de usuario",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Buscador") },
                    shape = RoundedCornerShape(18.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                SortOptionsBar(
                    selected = sortOption,
                    onSelect = { sortOption = it },
                )

                Spacer(modifier = Modifier.height(12.dp))

                val selectedTracksOrdered = visibleTracks.filter { selectedTrackIds.contains(it.id) }
                if (selectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${selectedTracksOrdered.size} seleccionada(s)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                        TextButton(onClick = { clearTrackSelection() }) {
                            Text("Cancelar")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                PrimaryPillButton(
                    text = "PLAY ALL TRACKS",
                    onClick = {
                        if (visibleTracks.isEmpty()) return@PrimaryPillButton
                        val first = visibleTracks.first()
                        // Añadimos a la cola ANTES de playTrack para que loadQueue
                        // ya incluya todos los temas en su primera llamada al servicio.
                        queue.addAll(visibleTracks.drop(1))
                        playTrack(first, clearQueue = false)
                        if (!shuffleOn && repeatMode == RepeatMode.LIST) {
                            // Cola completa = [cancion actual] + resto
                            queueRepeatSnapshot = listOf(first) + queue.toList()
                            queueRepeatIndex = 0
                        }
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                SectionChips(
                    selected = selectedSection,
                    onSelect = { selectedSection = it },
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedSection == PlayerSection.Playlist) {
                    PlaylistPickerBar(
                        playlists = playlists,
                        selectedPlaylistId = selectedPlaylistId,
                        onSelect = { id ->
                            selectedPlaylistId = id
                        },
                        onCreateEmpty = {
                            val res = viewModel.createPlaylist("Mi playlist")
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
                    Spacer(modifier = Modifier.height(14.dp))
                }

                LazyColumn(
                    state = when (selectedSection) {
                        PlayerSection.Songs -> songsListState
                        PlayerSection.Favorites -> favoritesListState
                        PlayerSection.Playlist -> playlistListState
                    },
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // El mini-player está anclado abajo en un `Box`, así que reservamos
                    // espacio para que el listado no se solape y los taps funcionen bien.
                    contentPadding = PaddingValues(bottom = 190.dp),
                ) {
                    items(visibleTracks) { track ->
                        val currentPlaylist =
                            selectedPlaylistId?.let { pid -> playlists.firstOrNull { it.id == pid } }
                        val showRemoveFromPlaylist =
                            selectedSection == PlayerSection.Playlist &&
                                selectedPlaylistId != null &&
                                currentPlaylist?.songIds?.contains(track.id) == true
                        val isFavorite = favoriteIds.contains(track.id)
                        TrackRow(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isSelected = selectedTrackIds.contains(track.id),
                            showOverflowMenu = !selectionMode,
                            showSelectedActionsMenu = selectionMode && selectedTrackIds.contains(track.id),
                            onLongPress = {
                                val wasSelectionMode = selectionMode
                                toggleTrackSelection(track.id)
                                if (!wasSelectionMode) {
                                    Toast.makeText(
                                        context,
                                        "Modo selección activado.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onClick = {
                                if (selectionMode) {
                                    toggleTrackSelection(track.id)
                                } else {
                                    playTrack(track, clearQueue = true)
                                }
                            },
                            onQueue = {
                                queue.add(track)
                                syncQueueToService()
                                if (!shuffleOn && repeatMode == RepeatMode.LIST) {
                                    val ct = currentTrack
                                    if (ct != null) {
                                        if (queueRepeatSnapshot.isEmpty() || queueRepeatIndex < 0) {
                                            queueRepeatSnapshot = listOf(ct) + queue.toList()
                                            queueRepeatIndex = 0
                                        } else {
                                            queueRepeatSnapshot = queueRepeatSnapshot + track
                                        }
                                    }
                                }
                                Toast.makeText(context, "Añadido al final de la cola.", Toast.LENGTH_SHORT).show()
                            },
                            onPlayNext = {
                                queue.add(0, track)
                                syncQueueToService()
                                if (!shuffleOn && repeatMode == RepeatMode.LIST) {
                                    val ct = currentTrack
                                    if (ct != null) {
                                        if (queueRepeatSnapshot.isEmpty() || queueRepeatIndex < 0) {
                                            queueRepeatSnapshot = listOf(ct) + queue.toList()
                                            queueRepeatIndex = 0
                                        } else {
                                            val insertAt = (queueRepeatIndex + 1).coerceAtMost(queueRepeatSnapshot.size)
                                            val mutable = queueRepeatSnapshot.toMutableList()
                                            mutable.add(insertAt, track)
                                            queueRepeatSnapshot = mutable
                                        }
                                    }
                                }
                                Toast.makeText(context, "Reproducirá a continuación.", Toast.LENGTH_SHORT).show()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            onAddToPlaylist = {
                                addToPlaylistDialogOpen = true
                                addToPlaylistTracks = listOf(track)
                                addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                addToPlaylistNewName = ""
                            },
                            onDeleteFromDevice = { deleteTrackFromDevice(track) },
                            onBulkQueue = {
                                if (selectedTracksOrdered.isEmpty()) return@TrackRow
                                queue.addAll(selectedTracksOrdered)
                                syncQueueToService()
                                Toast.makeText(
                                    context,
                                    if (selectedTracksOrdered.size == 1) "Canción añadida a la cola." else "Canciones añadidas a la cola.",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                                selectedTracksOrdered.forEach {
                                    deleteTrackFromDevice(it, syncAfter = false, showToast = false)
                                }
                                viewModel.syncLibrary(auto = true)
                                Toast.makeText(
                                    context,
                                    "Eliminadas ${selectedTracksOrdered.size} canciones del teléfono.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                clearTrackSelection()
                            },
                            showBulkRemoveFromPlaylist = selectedSection == PlayerSection.Playlist && selectedPlaylistId != null,
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
                                Toast.makeText(
                                    context,
                                    if (selectedTracksOrdered.size == 1) "Canción quitada de la playlist." else "Canciones quitadas de la playlist.",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
            }

            // MINI PLAYER (parte inferior boceto 1)
            MiniPlayerBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                track = currentTrack,
                isPlaying = isPlaying,
                position = sliderPosition,
                artwork = currentArtwork,
                shuffleOn = shuffleOn,
                repeatMode = repeatMode,
                queueSize = if (shuffleOn && shuffleOrder.isNotEmpty())
                    (shuffleOrder.size - (shuffleIndex + 1).coerceAtLeast(0)).coerceAtLeast(0)
                else queue.size,
                onTogglePlay = {
                    currentTrack ?: return@MiniPlayerBar
                    context.startService(
                        Intent(context, PlaybackService::class.java).setAction(
                            if (isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY,
                        ),
                    )
                },
                onPrev = {
                    context.startService(
                        Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_PREV),
                    )
                },
                onNext = {
                    context.startService(
                        Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_NEXT),
                    )
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
                onToggleShuffle = { onToggleShuffle() },
                onToggleRepeat = { onCycleRepeatMode() },
            )

            // OVERLAY EXPANDIDO (boceto 2)
            if (isExpanded) {
                val canDownloadLyrics =
                    !lyricsDownloading &&
                        currentTrack != null &&
                        lyricsState is LyricsUiState.Empty
                ExpandedPlayerOverlay(
                    modifier = Modifier.fillMaxSize(),
                    bgBrush = bgBrush,
                    track = currentTrack,
                    artwork = currentArtwork,
                    lyricsState = lyricsState,
                    isPlaying = isPlaying,
                    position = sliderPosition,
                    onClose = { isExpanded = false },
                    onTogglePlay = {
                        currentTrack ?: return@ExpandedPlayerOverlay
                        context.startService(
                            Intent(context, PlaybackService::class.java).setAction(
                                if (isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY,
                            ),
                        )
                    },
                    onPrev = {
                        context.startService(
                            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_PREV),
                        )
                    },
                    onNext = {
                        context.startService(
                            Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_NEXT),
                        )
                    },
                    onSeekPreview = { newPos -> sliderDragPos = newPos },
                    onSeekCommit = { finalPos ->
                        if (playbackDurationMs > 0) {
                            boundService?.seekTo((playbackDurationMs * finalPos).toLong())
                        }
                        sliderDragPos = null
                    },
                    shuffleOn = shuffleOn,
                    repeatMode = repeatMode,
                    onToggleShuffle = { onToggleShuffle() },
                    onToggleRepeat = { onCycleRepeatMode() },
                    canDownloadLyrics = canDownloadLyrics,
                    onRequestLyricsDownload = {
                        currentTrack?.let { downloadLyricsIfNeeded(it) }
                    },
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
                            Text(
                                text = "Cola de reproducción",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            // displayQueue: en shuffle muestra el resto del orden aleatorio;
                            // en modo normal muestra la cola manual.
                            val displayQueue = if (shuffleOn && shuffleOrder.isNotEmpty())
                                shuffleOrder.drop((shuffleIndex + 1).coerceAtLeast(0))
                            else
                                queue.toList()

                            if (displayQueue.isNotEmpty()) {
                                TextButton(onClick = {
                                    queue.clear()
                                    if (shuffleOn) {
                                        // En shuffle, "limpiar" solo deja la canción actual
                                        // y reconstruye shuffleOrder vacío tras el índice actual
                                        shuffleOrder = if (shuffleIndex >= 0 && shuffleIndex < shuffleOrder.size)
                                            listOf(shuffleOrder[shuffleIndex])
                                        else emptyList()
                                        shuffleIndex = 0
                                    }
                                    syncQueueToService()
                                }) {
                                    Text("Limpiar")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val displayQueue = if (shuffleOn && shuffleOrder.isNotEmpty())
                            shuffleOrder.drop((shuffleIndex + 1).coerceAtLeast(0))
                        else
                            queue.toList()

                        if (currentTrack != null) {
                            Text(
                                text = "Sonando ahora",
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
                                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
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
                                            text = currentTrack!!.artist.toTitleCaseSimple(),
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
                                    text = "La cola está vacía.\nUsa los 3 puntos de una canción para añadirla.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text(
                                text = "A continuación (${displayQueue.size})" +
                                    if (shuffleOn) " • Aleatorio" else "",
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
                                            containerColor = Color.Black.copy(alpha = 0.28f),
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
                                                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(7.dp)),
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
                                                    text = t.artist.toTitleCaseSimple(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            // Saltar directamente a esta canción
                                            IconButton(
                                                onClick = {
                                                    if (shuffleOn) {
                                                        // En shuffle: ajustar shuffleIndex al ítem pulsado
                                                        val absIdx = shuffleOrder.indexOfFirst { it.id == t.id }
                                                        if (absIdx >= 0) shuffleIndex = absIdx - 1
                                                    } else {
                                                        val remaining = queue.drop(idx + 1)
                                                        repeat(queue.size) { queue.removeAt(0) }
                                                        queue.addAll(remaining)
                                                    }
                                                    playTrack(t, clearQueue = false)
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Reproducir ya",
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
                                                        contentDescription = "Quitar de cola",
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
                            text = "Añadir a playlist",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (playlists.isNotEmpty()) {
                            Text(
                                text = "Playlist objetivo",
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
                                                Color.Black.copy(alpha = 0.25f)
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
                                                text = "${p.name} (Canciones: ${p.songIds.size})",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (selected) {
                                                Text(
                                                    text = "Seleccionada",
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
                            label = { Text("Nueva playlist (opcional)") },
                            placeholder = { Text("Ej: Mis favoritos Chill") },
                        )

                        Text(
                            text = "Si no pones nombre nuevo, se añade a la playlist seleccionada.",
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
                                Text("Cancelar")
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
                                            Toast.makeText(
                                                context,
                                                "Playlist \"$newName\" creada.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            res.id
                                        }

                                        is PlayerViewModel.CreatePlaylistResult.AlreadyExists -> {
                                            Toast.makeText(
                                                context,
                                                "Ya existe una playlist con ese nombre.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            selectedPlaylistId = res.id
                                            res.id
                                        }
                                    }
                                } else {
                                    val existingId = chosenId
                                    if (existingId == null) {
                                        // UX: si no hay playlist todavía, crear una por defecto y continuar.
                                        when (val created = viewModel.createPlaylist("Mi playlist")) {
                                            is PlayerViewModel.CreatePlaylistResult.Created -> {
                                                selectedPlaylistId = created.id
                                                Toast.makeText(
                                                    context,
                                                    "Playlist \"Mi playlist\" creada.",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
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
                                    Toast.makeText(
                                        context,
                                        if (tracksToAdd.size == 1) "Canción añadida a la playlist."
                                        else "Canciones añadidas a la playlist.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    clearTrackSelection()
                                }
                                },
                            ) {
                                Text("Añadir")
                            }
                        }
                    }
                }
            }

            // DIALOG: confirmación duplicado en playlist
            if (duplicateDialog != null) {
                val d = duplicateDialog!!
                val count = d.trackIds.distinct().size
                AlertDialog(
                    onDismissRequest = { duplicateDialog = null },
                    title = { Text("Duplicados detectados") },
                    text = {
                        Text(
                            "Hay $count canción(es) ya existentes en la playlist. ¿Quieres duplicarlas todas?"
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
                                Toast.makeText(
                                    context,
                                    "Duplicados añadidos a la playlist.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                duplicateDialog = null
                                clearTrackSelection()
                            },
                        ) {
                            Text("Duplicar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                duplicateDialog = null
                                Toast.makeText(
                                    context,
                                    "Se han omitido las canciones duplicadas.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                clearTrackSelection()
                            },
                        ) {
                            Text("No duplicar")
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
                    title = { Text("Eliminar playlist") },
                    text = { Text("¿Seguro que quieres eliminar \"${name}\"?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val ok = viewModel.deletePlaylist(id)
                                playlistDeleteDialogId = null
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        "No se pudo eliminar la playlist.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        ) {
                            Text("Eliminar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistDeleteDialogId = null }) {
                            Text("Cancelar")
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
                    title = { Text("Editar nombre") },
                    text = {
                        OutlinedTextField(
                            value = playlistRenameNewName,
                            onValueChange = { playlistRenameNewName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nombre de la playlist") },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val newName = playlistRenameNewName.trim()
                                if (newName.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Introduce un nombre válido.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@TextButton
                                }

                                when (val res = viewModel.renamePlaylist(id, newName)) {
                                    is PlayerViewModel.RenamePlaylistResult.Renamed -> {
                                        playlistRenameDialogId = null
                                    }

                                    is PlayerViewModel.RenamePlaylistResult.AlreadyExists -> {
                                        Toast.makeText(
                                            context,
                                            "Ya existe una playlist con ese nombre.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        ) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistRenameDialogId = null }) {
                            Text("Cancelar")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: DeviceTrack,
    isCurrent: Boolean,
    isSelected: Boolean,
    showOverflowMenu: Boolean,
    showSelectedActionsMenu: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onBulkQueue: () -> Unit,
    onBulkToggleFavorite: () -> Unit,
    onBulkAddToPlaylist: () -> Unit,
    onBulkDeleteFromDevice: () -> Unit,
    showBulkRemoveFromPlaylist: Boolean,
    onBulkRemoveFromPlaylist: () -> Unit,
    isFavorite: Boolean,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else -> Color.Black.copy(alpha = 0.35f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = { onClick() },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            ) {
                ArtworkThumbnail(track = track, sizeDp = 36)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Seleccionada",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.toTitleCaseSimple(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist.toTitleCaseSimple(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showOverflowMenu) {
                TrackOverflowMenu(
                    onQueue = onQueue,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onHide = { /* TODO */ },
                    onDeleteFromDevice = onDeleteFromDevice,
                    isFavorite = isFavorite,
                    showRemoveFromPlaylist = showRemoveFromPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                )
            } else if (showSelectedActionsMenu) {
                TrackSelectionOverflowMenu(
                    onQueue = onBulkQueue,
                    onToggleFavorite = onBulkToggleFavorite,
                    onAddToPlaylist = onBulkAddToPlaylist,
                    onDeleteFromDevice = onBulkDeleteFromDevice,
                    showRemoveFromPlaylist = showBulkRemoveFromPlaylist,
                    onRemoveFromPlaylist = onBulkRemoveFromPlaylist,
                )
            }
        }
    }
}

@Composable
private fun TrackSelectionOverflowMenu(
    onQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Acciones selección",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Añadir a cola") },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = { Text("Favoritos") },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            if (showRemoveFromPlaylist) {
                DropdownMenuItem(
                    text = { Text("Quitar de playlist") },
                    onClick = { expanded = false; onRemoveFromPlaylist() },
                )
            }
            DropdownMenuItem(
                text = { Text("Eliminar del teléfono") },
                onClick = { expanded = false; onDeleteFromDevice() },
            )
        }
    }
}

@Composable
private fun ArtworkThumbnail(
    track: DeviceTrack,
    sizeDp: Int,
    isPlaceholderOnly: Boolean = false,
) {
    val context = LocalContext.current
    var bitmap by remember(track.id) { mutableStateOf<Bitmap?>(ArtworkCache.get(track.id)) }

    LaunchedEffect(track.id) {
        if (bitmap != null) return@LaunchedEffect

        val loaded = withContext(Dispatchers.IO) {
            // 1) Tags embebidos
            val embedded = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(track.uri))
                    val data = retriever.embeddedPicture
                    if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
                } finally { retriever.release() }
            }.getOrNull()

            if (embedded != null) return@withContext embedded

            // 2) Fallback: .meta.json artworkBase64
            runCatching {
                val uri = Uri.parse(track.uri)
                val audioFile = java.io.File(uri.path ?: return@runCatching null)
                val metaFile = java.io.File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.meta.json")
                if (!metaFile.exists()) return@runCatching null
                val b64 = org.json.JSONObject(metaFile.readText()).optString("artworkBase64")
                if (b64.isBlank()) return@runCatching null
                val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        bitmap = loaded
        if (loaded != null) ArtworkCache.put(track.id, loaded)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .border(0.dp, Color.Transparent),
            contentScale = ContentScale.Crop,
        )
    } else if (!isPlaceholderOnly) {
        // Si no hay carátula embebida, mostramos icono genérico.
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .border(0.dp, Color.Transparent),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun TrackOverflowMenu(
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHide: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    isFavorite: Boolean,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Más opciones",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Poner en cola") },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = { Text("Reproducir a continuación") },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = {
                    Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
                },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            DropdownMenuItem(
                text = { Text("Ocultar") },
                onClick = { expanded = false; onHide() },
            )
            DropdownMenuItem(
                text = { Text("Eliminar del teléfono") },
                onClick = {
                    expanded = false
                    onDeleteFromDevice()
                },
            )

            if (showRemoveFromPlaylist) {
                DropdownMenuItem(
                    text = { Text("Quitar de la playlist") },
                    onClick = {
                        expanded = false
                        onRemoveFromPlaylist()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionChips(
    selected: PlayerSection,
    onSelect: (PlayerSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionChip(
            text = "Canciones",
            selected = selected == PlayerSection.Songs,
            onClick = { onSelect(PlayerSection.Songs) },
        )
        SectionChip(
            text = "Favoritos",
            selected = selected == PlayerSection.Favorites,
            onClick = { onSelect(PlayerSection.Favorites) },
        )
        SectionChip(
            text = "Playlist",
            selected = selected == PlayerSection.Playlist,
            onClick = { onSelect(PlayerSection.Playlist) },
        )
    }
}

@Composable
private fun RowScope.SectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    else Color.Black.copy(alpha = 0.18f)
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(
        modifier = Modifier
            .weight(1f)
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistPickerBar(
    playlists: List<PlayerViewModel.PlaylistEntity>,
    selectedPlaylistId: Long?,
    onSelect: (Long) -> Unit,
    onCreateEmpty: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestRename: (Long) -> Unit,
) {
    if (playlists.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
            ) {
                Text(
                    text = "Aún no tienes playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryPillButton(
                    text = "Crear una playlist",
                    onClick = onCreateEmpty,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Mis playlists",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onCreateEmpty) {
                Text("Crear")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        playlists.forEach { p ->
            val selected = p.id == selectedPlaylistId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(p.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    } else {
                        Color.Black.copy(alpha = 0.28f)
                    },
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${p.songIds.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Text(
                                    text = "Activa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Opciones de playlist",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar nombre") },
                                onClick = {
                                    menuExpanded = false
                                    onRequestRename(p.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar playlist") },
                                onClick = {
                                    menuExpanded = false
                                    onRequestDelete(p.id)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(36.dp)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    modifier: Modifier,
    track: DeviceTrack?,
    isPlaying: Boolean,
    position: Float,
    artwork: Bitmap?,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    queueSize: Int,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onOpenExpanded: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    val durationMs = track?.durationMs?.toInt()?.takeIf { it > 0 } ?: 0
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    ) {
                        if (artwork != null) {
                            Image(
                                bitmap = artwork.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "${(track?.title ?: "Sin canción").toTitleCaseSimple()} | ${(track?.artist ?: "").toTitleCaseSimple()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = onOpenQueue) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Cola",
                                tint = if (queueSize > 0) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (queueSize > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (queueSize > 99) "99+" else queueSize.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenExpanded) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Ver letra",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            val currentMs = (position.coerceIn(0f, 1f) * durationMs).toInt()
            var localDrag by remember { mutableStateOf<Float?>(null) }
            val sliderValue = localDrag ?: position
            Slider(
                value = sliderValue,
                onValueChange = { v ->
                    localDrag = v
                    onSeekPreview(v)
                },
                onValueChangeFinished = {
                    // localDrag es null cuando el usuario toca sin arrastrar:
                    // en ese caso usamos sliderValue (que Compose ya actualizó).
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    text = formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Opciones",
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Siguiente",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = Icons.Outlined.Loop,
                        contentDescription = "Repetir",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedPlayerOverlay(
    modifier: Modifier,
    bgBrush: Brush,
    track: DeviceTrack?,
    artwork: Bitmap?,
    lyricsState: LyricsUiState,
    isPlaying: Boolean,
    position: Float,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    canDownloadLyrics: Boolean,
    onRequestLyricsDownload: () -> Unit,
) {
    val durationMs = track?.durationMs?.toInt()?.takeIf { it > 0 } ?: 0
    Surface(
        modifier = modifier.background(bgBrush),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = (track?.title ?: "").toTitleCaseSimple(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = (track?.artist ?: "").toTitleCaseSimple(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clickable(
                            enabled = canDownloadLyrics,
                            onClick = onRequestLyricsDownload,
                        ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.30f)),
                ) {
                    val bodyText = when (lyricsState) {
                        LyricsUiState.Idle -> "Pulsa para cargar letra"
                        LyricsUiState.Loading -> "Cargando letra..."
                        is LyricsUiState.Ready -> lyricsState.lyrics
                        is LyricsUiState.Empty -> lyricsState.message
                    }
                    Text(
                        text = bodyText,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    val currentMs = (position.coerceIn(0f, 1f) * durationMs).toInt()
                    var localDrag by remember { mutableStateOf<Float?>(null) }
                    val sliderValue = localDrag ?: position
                    Slider(
                        value = sliderValue,
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        Text(
                            text = formatMs(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPrev) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = onTogglePlay) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = onNext) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(onClick = onToggleRepeat) {
                            Icon(
                                imageVector = Icons.Outlined.Loop,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class RepeatMode { OFF, LIST, ONE }

private enum class PlayerSection { Songs, Favorites, Playlist }

private enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST,
}

@Composable
private fun SortOptionsBar(
    selected: SortOption,
    onSelect: (SortOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortChip(
            text = "Nombre A-Z",
            selected = selected == SortOption.NAME_ASC,
            onClick = { onSelect(SortOption.NAME_ASC) },
        )
        SortChip(
            text = "Nombre Z-A",
            selected = selected == SortOption.NAME_DESC,
            onClick = { onSelect(SortOption.NAME_DESC) },
        )
        SortChip(
            text = "Recientes",
            selected = selected == SortOption.NEWEST_FIRST,
            onClick = { onSelect(SortOption.NEWEST_FIRST) },
        )
        SortChip(
            text = "Antiguas",
            selected = selected == SortOption.OLDEST_FIRST,
            onClick = { onSelect(SortOption.OLDEST_FIRST) },
        )
    }
}

@Composable
private fun SortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    else Color.Black.copy(alpha = 0.18f)
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lyrics: String) : LyricsUiState
    data class Empty(val message: String) : LyricsUiState
}

/**
 * Devuelve (title, artist) leyendo primero el sidecar .meta.json del archivo
 * descargado. Si no existe o no tiene los campos, usa los valores de DeviceTrack.
 */
private fun resolveTrackMeta(track: com.imontalvodev.beatmybeat.ui.data.DeviceTrack): Pair<String, String> {
    runCatching {
        val uri = android.net.Uri.parse(track.uri)
        val path = uri.path ?: return@runCatching null
        val audioFile = java.io.File(path)
        val metaFile = java.io.File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.meta.json")
        if (!metaFile.exists()) return@runCatching null
        val json = org.json.JSONObject(metaFile.readText())
        val t = json.optString("title").takeIf { it.isNotBlank() } ?: track.title
        val rawArtist = json.optString("artist").takeIf {
            it.isNotBlank() && !it.equals("unknown artist", ignoreCase = true)
        } ?: track.artist
        // Limpiar sufijos de canal YouTube antes de buscar letras
        val a = com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics(rawArtist)
        Pair(t, a)
    }.getOrNull()?.let { return it }
    return Pair(track.title, com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics(track.artist))
}

private fun String.toTitleCaseSimple(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return trimmed
    return trimmed
        .split(Regex("\\s+"))
        .joinToString(" ") { word ->
            val lower = word.lowercase()
            lower.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

