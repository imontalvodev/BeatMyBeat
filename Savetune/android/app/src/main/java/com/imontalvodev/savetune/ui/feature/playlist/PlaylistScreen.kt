package com.imontalvodev.savetune.ui.feature.playlist

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imontalvodev.savetune.ui.network.RemoteArtworkCache
import com.imontalvodev.savetune.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.savetune.ui.network.MiddlewareApi
import com.imontalvodev.savetune.ui.network.AudioDownloader
import com.imontalvodev.savetune.ui.network.PlaylistSong
import com.imontalvodev.savetune.ui.theme.NeonBackgroundBottom
import com.imontalvodev.savetune.ui.theme.NeonBackgroundTop
import com.imontalvodev.savetune.ui.theme.PrimaryButton
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistScreen(
    modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit,
    playlistUrl: String,
) {
    var loading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("Playlist") }
    var tracks by remember { mutableStateOf<List<PlaylistSong>>(emptyList()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistUrl) {
        if (playlistUrl.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val res = withContext(Dispatchers.IO) {
            MiddlewareApi.fetchPlaylist(MIDDLEWARE_BASE_URL, playlistUrl)
        }
        loading = false
        if (!res.success) {
            error = res.message ?: res.error ?: "No se pudo cargar la playlist"
            title = "Playlist"
            tracks = emptyList()
        } else {
            title = res.playlist?.name?.ifBlank { "Playlist" } ?: "Playlist"
            tracks = res.songs
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
            PlaylistHeader(
                title = title,
                subtitle = when {
                    loading -> "Cargando..."
                    error != null -> error!!
                    else -> "${tracks.size} Tracks found"
                },
                onPrimaryClick = {
                    if (tracks.isEmpty() || downloading) return@PlaylistHeader
                    downloading = true
                    Toast.makeText(
                        context,
                        "Descargando ${tracks.size} canciones...",
                        Toast.LENGTH_SHORT,
                    ).show()
                    scope.launch {
                        try {
                            for (t in tracks) {
                                AudioDownloader.downloadAutoToAppMusic(
                                    context = context,
                                    middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                    title = t.title,
                                    artist = t.artist,
                                    album = t.album,
                                    imageUrl = t.imageUrl,
                                )
                            }
                            Toast.makeText(context, "Descargas listas.", Toast.LENGTH_SHORT).show()
                            downloading = false
                            onOpenPlayer()
                        } catch (e: Exception) {
                            downloading = false
                            Toast.makeText(
                                context,
                                "Error al descargar: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tracks) { track ->
                    TrackRow(track = track, onPlay = {
                        if (downloading) return@TrackRow
                        downloading = true
                        Toast.makeText(context, "Descargando: ${track.title}", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            try {
                                AudioDownloader.downloadAutoToAppMusic(
                                    context = context,
                                    middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                    title = track.title,
                                    artist = track.artist,
                                    album = track.album,
                                    imageUrl = track.imageUrl,
                                )
                                downloading = false
                                onOpenPlayer()
                            } catch (e: Exception) {
                                downloading = false
                                Toast.makeText(context, "No se pudo descargar.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    subtitle: String,
    onPrimaryClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.25f),
                ) {
                    // Placeholder cover (el backend da imageUrl por canción, pero no cover global)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    )
                }
                Spacer(modifier = Modifier.height(0.dp))
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(
                text = "DOWNLOAD ALL TRACKS (ZIP)",
                onClick = onPrimaryClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrackRow(track: PlaylistSong, onPlay: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(0.18f),
            ) {
                RemoteArtworkThumbnail(url = track.imageUrl)
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
                    text = track.artist,
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
private fun RemoteArtworkThumbnail(url: String) {
    val client = remember { OkHttpClient() }
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(RemoteArtworkCache.get(url)) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        if (bitmap != null) return@LaunchedEffect

        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val bytes = res.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
        }
        bitmap = loaded
        if (loaded != null) RemoteArtworkCache.put(url, loaded)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
        )
    }
}

