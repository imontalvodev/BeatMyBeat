package com.imontalvodev.savetune.ui.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.ui.data.DeviceTrack
import com.imontalvodev.savetune.ui.theme.NeonBackgroundBottom
import com.imontalvodev.savetune.ui.theme.NeonBackgroundTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.net.toUri

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: PlayerViewModel = viewModel()
    val deviceTracksState = viewModel.tracks.collectAsState()
    val deviceTracks = deviceTracksState.value
    val context = LocalContext.current

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.reset()
            mediaPlayer.release()
        }
    }

    var currentTrack by remember { mutableStateOf<DeviceTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatOn by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0f) }
    var currentArtwork by remember { mutableStateOf<Bitmap?>(null) }

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

    // Cargar carátula embebida de la pista actual, si existe
    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            currentArtwork = null
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, currentTrack!!.uri.toUri())
                val data = retriever.embeddedPicture
                currentArtwork = if (data != null) {
                    BitmapFactory.decodeByteArray(data, 0, data.size)
                } else {
                    null
                }
            } catch (_: Exception) {
                currentArtwork = null
            } finally {
                retriever.release()
            }
        }
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(NeonBackgroundTop, NeonBackgroundBottom),
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Header
            Text(
                text = "Savetune Player",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${deviceTracks.size} tracks found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Vista grande de la canción actual
            CurrentTrackLargeCard(currentTrack = currentTrack, artwork = currentArtwork)

            Spacer(modifier = Modifier.height(16.dp))

            // Botón para sync manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(onClick = { viewModel.syncLibrary(auto = false) }) {
                    Text("Actualizar biblioteca")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Track list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(deviceTracks) { track ->
                    PlayerTrackRow(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        onClick = {
                            try {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(context, Uri.parse(track.uri))
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                                currentTrack = track
                                isPlaying = true
                            } catch (_: Exception) {
                                // TODO: mostrar error al usuario
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BottomPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                shuffleOn = shuffleOn,
                repeatOn = repeatOn,
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
                onToggleShuffle = { shuffleOn = !shuffleOn },
                onToggleRepeat = { repeatOn = !repeatOn },
                onSeek = { newPos ->
                    position = newPos
                    val dur = mediaPlayer.duration
                    if (dur > 0) {
                        mediaPlayer.seekTo((dur * newPos).toInt())
                    }
                },
            )
        }
    }
}

@Composable
private fun PlayerTrackRow(
    track: DeviceTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(0.18f),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = track.album ?: track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun BottomPlayerBar(
    track: DeviceTrack?,
    isPlaying: Boolean,
    shuffleOn: Boolean,
    repeatOn: Boolean,
    position: Float,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (track != null) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = track.album ?: track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    text = "Sin canción seleccionada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = position,
                onValueChange = onSeek,
            )

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
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { /* prev */ }) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { /* next */ }) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = Icons.Outlined.Loop,
                        contentDescription = "Repeat",
                        tint = if (repeatOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentTrackLargeCard(
    currentTrack: DeviceTrack?,
    artwork: Bitmap?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
    ) {
        if (currentTrack == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Selecciona una canción para reproducirla",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(160.dp)
                        .fillMaxWidth(0.45f),
                ) {
                    if (artwork != null) {
                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currentTrack.album ?: currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

