package com.imontalvodev.savetune.ui.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.savetune.ui.network.LyricsCache
import com.imontalvodev.savetune.ui.network.ArtworkCache
import com.imontalvodev.savetune.ui.network.MiddlewareApi
import com.imontalvodev.savetune.ui.theme.NeonBackgroundBottom
import com.imontalvodev.savetune.ui.theme.NeonBackgroundTop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerViewModel = viewModel()
    val deviceTracks = viewModel.tracks.collectAsState().value
    val favoriteIds = viewModel.favoriteIds.collectAsState().value
    val playlists = viewModel.playlists.collectAsState().value
    val context = LocalContext.current

    val mediaPlayer = remember { MediaPlayer() }
    val queue = remember { mutableStateListOf<DeviceTrack>() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.reset()
            mediaPlayer.release()
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

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(playlists) {
        if (selectedPlaylistId == null && playlists.isNotEmpty()) {
            selectedPlaylistId = playlists.first().id
        }
    }

    // Al cambiar de sección/playlist limpiamos la cola para evitar saltos raros
    LaunchedEffect(selectedSection, selectedPlaylistId) {
        queue.clear()
    }

    var addToPlaylistDialogOpen by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var addToPlaylistExistingId by remember { mutableStateOf<Long?>(null) }
    var addToPlaylistNewName by remember { mutableStateOf("") }
    var addToPlaylistPickerExpanded by remember { mutableStateOf(false) }

    data class DuplicateConfirmState(
        val trackId: Long,
        val playlistId: Long,
    )

    var duplicateDialog by remember { mutableStateOf<DuplicateConfirmState?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncLibrary(auto = true)
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

    // Cargar letra cuando se abre el overlay o cambia la canción
    LaunchedEffect(currentTrack?.id) {
        val t = currentTrack ?: run {
            lyricsState = LyricsUiState.Empty("Selecciona una canción")
            return@LaunchedEffect
        }
        if (t.title.isBlank() || t.artist.isBlank()) {
            lyricsState = LyricsUiState.Empty("Faltan metadatos (título/artista)")
            return@LaunchedEffect
        }

        // 1) Intentar caché local primero (offline-friendly)
        val cached = withContext(Dispatchers.IO) {
            LyricsCache.get(context, t.title, t.artist)
        }
        if (!cached.isNullOrBlank()) {
            lyricsState = LyricsUiState.Ready(cached)
            return@LaunchedEffect
        }

        // 2) Si no hay caché, intentar red (puede fallar si no hay internet)
        lyricsState = LyricsUiState.Loading
        val res = runCatching {
            withContext(Dispatchers.IO) {
                MiddlewareApi.fetchLyrics(
                    baseUrl = MIDDLEWARE_BASE_URL,
                    title = t.title,
                    artist = t.artist,
                )
            }
        }.getOrNull()

        if (res != null && res.success && res.lyrics.isNotBlank()) {
            withContext(Dispatchers.IO) {
                LyricsCache.put(context, t.title, t.artist, res.lyrics)
            }
            lyricsState = LyricsUiState.Ready(res.lyrics)
        } else {
            lyricsState = LyricsUiState.Empty(
                res?.message ?: res?.error ?: "Sin conexión o letras no disponibles",
            )
        }
    }

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

    fun playTrack(track: DeviceTrack, clearQueue: Boolean = false) {
        try {
            if (clearQueue) queue.clear()
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, track.uri.toUri())
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentTrack = track
            currentIndex = deviceTracks.indexOfFirst { it.id == track.id }
            isPlaying = true
        } catch (_: Exception) {
        }
    }

    fun playPrev() {
        if (visibleTracks.isEmpty()) return
        val curId = currentTrack?.id ?: return
        val idx = visibleTracks.indexOfFirst { it.id == curId }
        val prevIndex = when {
            idx < 0 -> 0
            idx <= 0 -> visibleTracks.lastIndex
            else -> idx - 1
        }
        visibleTracks.getOrNull(prevIndex)?.let { track ->
            playTrack(track)
        }
    }

    fun playNext() {
        // 1) Si hay cola, consumimos lo primero encolado
        if (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            playTrack(next, clearQueue = false)
            return
        }

        // 2) Si no hay cola, navegamos dentro de la lista actual (según filtro/playlist)
        if (visibleTracks.isEmpty()) return
        val curId = currentTrack?.id ?: return
        val idx = visibleTracks.indexOfFirst { it.id == curId }
        val nextIndex = when {
            idx < 0 || idx >= visibleTracks.lastIndex -> 0
            else -> idx + 1
        }
        visibleTracks.getOrNull(nextIndex)?.let { track ->
            playTrack(track, clearQueue = false)
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

                PrimaryPillButton(
                    text = "PLAY ALL TRACKS",
                    onClick = {
                        if (visibleTracks.isEmpty()) return@PrimaryPillButton
                        val first = visibleTracks.first()
                        playTrack(first, clearQueue = true)
                        queue.addAll(visibleTracks.drop(1))
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
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    items(visibleTracks) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            onClick = { playTrack(track, clearQueue = true) },
                            onQueue = { queue.add(track) },
                            onSaveLibrary = { viewModel.toggleFavorite(track) },
                            onAddToPlaylist = {
                                addToPlaylistDialogOpen = true
                                addToPlaylistTrack = track
                                addToPlaylistExistingId = selectedPlaylistId ?: playlists.firstOrNull()?.id
                                addToPlaylistNewName = " "
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
                onTogglePlay = {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else if (currentTrack != null) {
                        mediaPlayer.start()
                        isPlaying = true
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
            )

            // OVERLAY EXPANDIDO (boceto 2)
            if (isExpanded) {
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
                        } else if (currentTrack != null) {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                    onPrev = { playPrev() },
                    onNext = { playNext() },
                    onSeek = { newPos ->
                        position = newPos
                        val dur = mediaPlayer.duration
                        if (dur > 0) mediaPlayer.seekTo((dur * newPos).toInt())
                    },
                )
            }

            // DIALOG: Añadir a playlist (crear o seleccionar)
            if (addToPlaylistDialogOpen && addToPlaylistTrack != null) {
                val track = addToPlaylistTrack!!
                val currentSelectedId = addToPlaylistExistingId
                    ?: selectedPlaylistId
                    ?: playlists.firstOrNull()?.id

                AlertDialog(
                    onDismissRequest = {
                        addToPlaylistDialogOpen = false
                        addToPlaylistPickerExpanded = false
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

                                val res = viewModel.addToPlaylist(
                                    track = track,
                                    playlistId = targetPlaylistId,
                                    allowDuplicate = false,
                                )
                                when (res) {
                                    is PlayerViewModel.AddToPlaylistResult.Added -> {
                                        selectedPlaylistId = targetPlaylistId
                                        addToPlaylistDialogOpen = false
                                        addToPlaylistTrack = null
                                    }

                                    is PlayerViewModel.AddToPlaylistResult.AlreadyExists -> {
                                        selectedPlaylistId = targetPlaylistId
                                        addToPlaylistDialogOpen = false
                                        addToPlaylistTrack = null
                                        duplicateDialog = DuplicateConfirmState(
                                            trackId = track.id,
                                            playlistId = targetPlaylistId,
                                        )
                                    }
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
                                addToPlaylistTrack = null
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
                val track = deviceTracks.firstOrNull { it.id == d.trackId }
                if (track != null) {
                    AlertDialog(
                        onDismissRequest = { duplicateDialog = null },
                        title = { Text("Duplicado detectado") },
                        text = {
                            Text("La canción ya existe en esta playlist. ¿Quieres duplicarla?")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.addToPlaylist(
                                        track = track,
                                        playlistId = d.playlistId,
                                        allowDuplicate = true,
                                    )
                                    duplicateDialog = null
                                },
                            ) {
                                Text("Duplicar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { duplicateDialog = null }) {
                                Text("No duplicar")
                            }
                        },
                    )
                } else {
                    duplicateDialog = null
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: DeviceTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onSaveLibrary: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else Color.Black.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
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
            TrackOverflowMenu(
                onQueue = onQueue,
                onSaveLibrary = onSaveLibrary,
                onAddToPlaylist = onAddToPlaylist,
                onHide = { /* TODO */ },
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
    onSaveLibrary: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHide: () -> Unit,
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
                text = { Text("Guardar en biblioteca") },
                onClick = { expanded = false; onSaveLibrary() },
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            DropdownMenuItem(
                text = { Text("Ocultar") },
                onClick = { expanded = false; onHide() },
            )
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
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenExpanded: () -> Unit,
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
                    )
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
                IconButton(onClick = { /* TODO volumen */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                IconButton(onClick = { /* TODO repeat */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Loop,
                        contentDescription = "Repetir",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                    .weight(1f),
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
                        IconButton(onClick = { /* TODO shuffle */ }) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = "Shuffle",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                        IconButton(onClick = { /* TODO repeat */ }) {
                            Icon(
                                imageVector = Icons.Outlined.Loop,
                                contentDescription = "Repeat",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

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

