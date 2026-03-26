package com.imontalvodev.savetune.ui.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.net.URLDecoder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.savetune.ui.network.LyricsCache
import com.imontalvodev.savetune.ui.network.ArtworkCache
import com.imontalvodev.savetune.ui.network.MiddlewareApi
import com.imontalvodev.savetune.ui.theme.NeonBackgroundBottom
import com.imontalvodev.savetune.ui.theme.NeonBackgroundTop
import com.imontalvodev.savetune.service.SavetuneForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
) {
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
                    "Permiso de música denegado. Solo verás descargas de Savetune.",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.syncLibrary(auto = true)
            }
        },
    )

    val mediaPlayer = remember { MediaPlayer() }
    val queue = remember { mutableStateListOf<DeviceTrack>() }
    DisposableEffect(Unit) {
        mediaPlayer.setOnErrorListener { _, _, _ -> true }
        onDispose {
            mediaPlayer.reset()
            mediaPlayer.release()
            SavetuneForegroundService.stopPlayback(context)
        }
    }

    var currentTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0f) }
    var currentArtwork by remember { mutableStateOf<Bitmap?>(null) }

    var query by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf(PlayerSection.Songs) }
    var isExpanded by remember { mutableStateOf(false) }
    var lyricsState by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Idle) }

    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    // Repetición de la cola (cuando Shuffle está OFF y repeatMode == LIST)
    var queueRepeatSnapshot by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var queueRepeatIndex by remember { mutableStateOf(-1) }
    var shuffleOrder by remember { mutableStateOf<List<DeviceTrack>>(emptyList()) }
    var shuffleIndex by remember { mutableStateOf(-1) }

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedTrackIds.isNotEmpty()

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

    // Al cambiar de sección/playlist limpiamos la cola para evitar saltos raros
    LaunchedEffect(selectedSection, selectedPlaylistId) {
        queue.clear()
        queueRepeatSnapshot = emptyList()
        queueRepeatIndex = -1
        selectedTrackIds = emptySet()
    }

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
                        colors = listOf(NeonBackgroundTop, NeonBackgroundBottom),
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

    // Actualizar progreso mientras se reproduce
    LaunchedEffect(currentTrack, isPlaying) {
        while (isActive && isPlaying && mediaPlayer.isPlaying) {
            val dur = mediaPlayer.duration
            if (dur > 0) {
                position = mediaPlayer.currentPosition.toFloat() / dur.toFloat()
            }
            delay(500)
        }
    }

    // Cargar carátula embebida de la pista actual, si existe (con caché)
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

        val loaded = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(currentTrack!!.uri))
                val data = retriever.embeddedPicture
                if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
            } finally {
                retriever.release()
            }
        }.getOrNull()

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
        val title = t.title.trim()
        val artist = t.artist.trim()

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
            .replace(Regex("(?i)\\b(remastered|remaster|official|audio|video|lyrics|live)\\b"), " ")
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

        val title = track.title.trim()
        val artist = track.artist.trim()
        if (isUnknown(title) || isUnknown(artist)) {
            lyricsState = LyricsUiState.Empty("No hay letra disponible para esta canción")
            return
        }

        lyricsDownloading = true
        lyricsState = LyricsUiState.Loading

        uiScope.launch {
            try {
                // 1) Cache por si ya se bajó en otro momento
                val cached = withContext(Dispatchers.IO) {
                    LyricsCache.get(context, title, artist)
                }
                if (!cached.isNullOrBlank()) {
                    lyricsState = LyricsUiState.Ready(cached)
                    return@launch
                }

                // 2) Fallback de título para mejorar tasa de acierto.
                // Nota: Kotlin no permite "forward reference" fiable para funciones locales,
                // así que calculamos el base URL inline.
                val pythonBaseUrl = run {
                    val m = MIDDLEWARE_BASE_URL.trimEnd('/')
                    when {
                        m.endsWith(":3000") -> m.removeSuffix(":3000") + ":4000"
                        else -> "http://10.0.2.2:4000"
                    }
                }
                val uriTitle = titleFromUri(track.uri)
                val attempts = listOf(
                    title,
                    sanitizeTitle(title),
                    uriTitle,
                    sanitizeTitle(uriTitle),
                )
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                for (candidate in attempts) {
                    val res = runCatching {
                        withContext(Dispatchers.IO) {
                            MiddlewareApi.fetchLyrics(
                                baseUrl = MIDDLEWARE_BASE_URL,
                                title = candidate,
                                artist = artist,
                            )
                        }
                    }.getOrNull()

                    val finalRes = if (res != null && !res.success && res.error == "InvalidJson") {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                MiddlewareApi.fetchLyrics(
                                    baseUrl = pythonBaseUrl,
                                    title = candidate,
                                    artist = artist,
                                )
                            }
                        }.getOrNull() ?: res
                    } else {
                        res
                    }

                    if (finalRes != null && finalRes.success && finalRes.lyrics.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            // Guardamos bajo el título/artista original para que el cache coincida.
                            LyricsCache.put(context, title, artist, finalRes.lyrics)
                        }
                        lyricsState = LyricsUiState.Ready(finalRes.lyrics)
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

    val bgBrush = Brush.verticalGradient(colors = listOf(NeonBackgroundTop, NeonBackgroundBottom))

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
    }

    fun onToggleShuffle() {
        shuffleOn = !shuffleOn
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
        } else {
            shuffleOrder = emptyList()
            shuffleIndex = -1
            // Al salir de random, limpiamos estados derivados para volver
            // a navegación normal por lista desde la canción actual.
            queue.clear()
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
            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mediaPlayer.setDataSource(context, track.uri.toUri())
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setVolume(1f, 1f)
            SavetuneForegroundService.startPlayback(
                context = context,
                title = track.title,
                subtitle = track.artist,
            )
            currentTrack = track
            currentIndex = deviceTracks.indexOfFirst { it.id == track.id }
            isPlaying = true

            // Si Shuffle está activado, ajustamos el índice sin rebarajar para mantener bidireccionalidad.
            if (shuffleOn) {
                shuffleIndex = shuffleOrder.indexOfFirst { it.id == track.id }
                if (shuffleIndex < 0) {
                    // Si por alguna razón la canción no está en la orden actual, reconstruimos con el track incluido.
                    val base = visibleTracks.toMutableList()
                    if (currentTrack != null && base.none { it.id == currentTrack!!.id }) {
                        base.add(currentTrack!!)
                    }
                    shuffleOrder = base.shuffled(Random(System.currentTimeMillis()))
                    shuffleIndex = shuffleOrder.indexOfFirst { it.id == track.id }
                }
            }
        } catch (e: Exception) {
            isPlaying = false
            Toast.makeText(context, "No se pudo reproducir: ${e.message ?: "error"}", Toast.LENGTH_SHORT).show()
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

    fun deleteTrackFromDevice(
        track: DeviceTrack,
        syncAfter: Boolean = true,
        showToast: Boolean = true,
    ) {
        try {
            val uri = Uri.parse(track.uri)
            val deleted = when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> {
                    val path = uri.path
                    if (path.isNullOrBlank()) false else File(path).delete()
                }
                else -> {
                    // Por si viene como `file:///...` pero sin scheme reconocido
                    val path = uri.path
                    if (path.isNullOrBlank()) false else File(path).delete()
                }
            }

            if (!deleted) {
                if (showToast) {
                    Toast.makeText(context, "No se pudo eliminar la canción.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            queue.removeAll { it.id == track.id }
            if (currentTrack?.id == track.id) {
                mediaPlayer.reset()
                isPlaying = false
                currentTrack = null
                currentArtwork = null
                position = 0f
                lyricsState = LyricsUiState.Empty("Selecciona una canción")
                SavetuneForegroundService.stopPlayback(context)
            }

            if (syncAfter) {
                viewModel.syncLibrary(auto = true)
                if (showToast) {
                    Toast.makeText(context, "Eliminado del teléfono.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            if (showToast) {
                Toast.makeText(context, "Error eliminando la canción.", Toast.LENGTH_SHORT).show()
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(20.dp),
                            ),
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

                if (selectionMode) {
                    val selectedTracksOrdered = visibleTracks.filter { selectedTrackIds.contains(it.id) }
                    val count = selectedTracksOrdered.size
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.28f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "$count seleccionada(s)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionPillButton(
                                    text = "Fav",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedTracksOrdered.forEach { viewModel.toggleFavorite(it) }
                                        clearTrackSelection()
                                    },
                                )
                                ActionPillButton(
                                    text = "Eliminar",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedTracksOrdered.forEach {
                                            deleteTrackFromDevice(it, syncAfter = false, showToast = false)
                                        }
                                        viewModel.syncLibrary(auto = true)
                                        Toast.makeText(
                                            context,
                                            "Eliminadas $count canciones del teléfono.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        clearTrackSelection()
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionPillButton(
                                    text = "Cola",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        queue.addAll(selectedTracksOrdered)
                                        clearTrackSelection()
                                    },
                                )
                                if (selectedSection == PlayerSection.Playlist && selectedPlaylistId != null) {
                                    ActionPillButton(
                                        text = "Quitar",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            selectedTracksOrdered.forEach { tr ->
                                                viewModel.removeSongFromPlaylist(
                                                    trackId = tr.id,
                                                    playlistId = selectedPlaylistId!!,
                                                    removeAllOccurrences = true,
                                                )
                                            }
                                            clearTrackSelection()
                                        },
                                    )
                                } else {
                                    ActionPillButton(
                                        text = "Playlist",
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            addToPlaylistDialogOpen = true
                                            addToPlaylistTracks = selectedTracksOrdered
                                            addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                            addToPlaylistNewName = ""
                                            addToPlaylistPickerExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                PrimaryPillButton(
                    text = "PLAY ALL TRACKS",
                    onClick = {
                        if (visibleTracks.isEmpty()) return@PrimaryPillButton
                        val first = visibleTracks.first()
                        playTrack(first, clearQueue = true)
                        queue.addAll(visibleTracks.drop(1))
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
                            onLongPress = { toggleTrackSelection(track.id) },
                            onClick = {
                                if (selectionMode) {
                                    toggleTrackSelection(track.id)
                                } else {
                                    playTrack(track, clearQueue = true)
                                }
                            },
                                            onQueue = {
                                                queue.add(track)
                                                if (!shuffleOn && repeatMode == RepeatMode.LIST) {
                                                    val ct = currentTrack
                                                    if (ct != null) {
                                                        if (queueRepeatSnapshot.isEmpty() || queueRepeatIndex < 0) {
                                                            // Empezamos a repetir la cola desde el inicio.
                                                            queueRepeatSnapshot = listOf(ct) + queue.toList()
                                                            queueRepeatIndex = 0
                                                        } else {
                                                            // La cola es la "parte pendiente"; mantenemos snapshot extendido.
                                                            queueRepeatSnapshot = queueRepeatSnapshot + track
                                                        }
                                                    }
                                                }
                                            },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            onAddToPlaylist = {
                                addToPlaylistDialogOpen = true
                                addToPlaylistTracks = listOf(track)
                                addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                addToPlaylistNewName = " "
                            },
                            onDeleteFromDevice = { deleteTrackFromDevice(track) },
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
                position = position,
                artwork = currentArtwork,
                shuffleOn = shuffleOn,
                repeatMode = repeatMode,
                onTogglePlay = {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                        SavetuneForegroundService.stopPlayback(context)
                    } else if (currentTrack != null) {
                        mediaPlayer.start()
                        isPlaying = true
                        SavetuneForegroundService.startPlayback(
                            context = context,
                            title = currentTrack!!.title,
                            subtitle = currentTrack!!.artist,
                        )
                    }
                },
                onPrev = { playPrev() },
                onNext = { playNext() },
                onSeek = { newPos ->
                    position = newPos
                    val dur = mediaPlayer.duration
                    if (dur > 0) mediaPlayer.seekTo((dur * newPos).toInt())
                },
                onOpenExpanded = { isExpanded = true },
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
                    position = position,
                    onClose = { isExpanded = false },
                    onTogglePlay = {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                            SavetuneForegroundService.stopPlayback(context)
                        } else if (currentTrack != null) {
                            mediaPlayer.start()
                            isPlaying = true
                            SavetuneForegroundService.startPlayback(
                                context = context,
                                title = currentTrack!!.title,
                                subtitle = currentTrack!!.artist,
                            )
                        }
                    },
                    onPrev = { playPrev() },
                    onNext = { playNext() },
                    onSeek = { newPos ->
                        position = newPos
                        val dur = mediaPlayer.duration
                        if (dur > 0) mediaPlayer.seekTo((dur * newPos).toInt())
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

            // DIALOG: Añadir a playlist (crear o seleccionar)
            if (addToPlaylistDialogOpen && addToPlaylistTracks.isNotEmpty()) {
                val tracksToAdd = addToPlaylistTracks
                val currentSelectedId = addToPlaylistExistingId
                    ?: selectedPlaylistId
                    ?: playlists.firstOrNull()?.id

                AlertDialog(
                    onDismissRequest = {
                        addToPlaylistDialogOpen = false
                        addToPlaylistPickerExpanded = false
                        addToPlaylistTracks = emptyList()
                        duplicateDialog = null
                    },
                    title = { Text("Añadir a playlist") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (playlists.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Playlist objetivo: ${
                                            playlists.firstOrNull { it.id == currentSelectedId }?.name
                                                ?: "Ninguna"
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    )
                                    IconButton(
                                        onClick = { addToPlaylistPickerExpanded = !addToPlaylistPickerExpanded },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Elegir playlist",
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (addToPlaylistPickerExpanded) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        playlists.forEach { p ->
                                            val selected = p.id == (addToPlaylistExistingId ?: currentSelectedId)
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        addToPlaylistExistingId = p.id
                                                        addToPlaylistPickerExpanded = false
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                    } else {
                                                        Color.Black.copy(alpha = 0.25f)
                                                    },
                                                ),
                                            ) {
                                                Text(
                                                    text = "${p.name} (Canciones: ${p.songIds.size})",
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
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
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val newName = addToPlaylistNewName.trim()
                                val chosenId =
                                    addToPlaylistExistingId
                                        ?: selectedPlaylistId
                                        ?: playlists.firstOrNull()?.id
                                        ?: return@TextButton

                                val targetPlaylistId = if (newName.isNotBlank()) {
                                    val res = viewModel.createPlaylist(newName)
                                    when (res) {
                                        is PlayerViewModel.CreatePlaylistResult.Created -> {
                                            selectedPlaylistId = res.id
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
                                    chosenId
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
                                    clearTrackSelection()
                                }
                            }
                        ) {
                            Text("Añadir")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                addToPlaylistDialogOpen = false
                                addToPlaylistTracks = emptyList()
                                duplicateDialog = null
                            }
                        ) {
                            Text("Cancelar")
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
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
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
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onHide = { /* TODO */ },
                    onDeleteFromDevice = onDeleteFromDevice,
                    isFavorite = isFavorite,
                    showRemoveFromPlaylist = showRemoveFromPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                )
            }
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
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(track.uri))
                    val data = retriever.embeddedPicture
                    if (data != null) BitmapFactory.decodeByteArray(data, 0, data.size) else null
                } finally {
                    retriever.release()
                }
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
        modifier = Modifier.fillMaxWidth(),
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
        PrimaryPillButton(
            text = "Crear una playlist",
            onClick = onCreateEmpty,
        )

        Spacer(modifier = Modifier.height(14.dp))

        playlists.forEach { p ->
            val selected = p.id == selectedPlaylistId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(p.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Black.copy(alpha = 0.25f)
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 10.dp else 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = p.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${p.songIds.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenExpanded: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
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

                IconButton(onClick = onOpenExpanded) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = "Ver letra",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Slider(value = position, onValueChange = onSeek)

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
    onSeek: (Float) -> Unit,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    canDownloadLyrics: Boolean,
    onRequestLyricsDownload: () -> Unit,
) {
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
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

            val scroll = rememberScrollState()
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
                    .weight(1f)
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
                    Slider(value = position, onValueChange = onSeek)
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

private sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lyrics: String) : LyricsUiState
    data class Empty(val message: String) : LyricsUiState
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

