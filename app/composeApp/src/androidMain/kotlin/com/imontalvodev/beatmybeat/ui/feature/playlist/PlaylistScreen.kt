package com.imontalvodev.beatmybeat.ui.feature.playlist

import android.graphics.Bitmap
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.network.RemoteArtworkCache
import com.imontalvodev.beatmybeat.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.beatmybeat.ui.network.MiddlewareApi
import com.imontalvodev.beatmybeat.download.DownloadProgressBus
import com.imontalvodev.beatmybeat.service.SongDownloadService
import com.imontalvodev.beatmybeat.ui.network.PlaylistResponse
import com.imontalvodev.beatmybeat.ui.network.PlaylistSong
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.PrimaryButton
import com.imontalvodev.beatmybeat.ui.network.AppHttpClient
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
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
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<PlaylistSong>>(emptyList()) }

    val context = LocalContext.current
    val resources = LocalResources.current
    val activeDownload by DownloadProgressBus.state.collectAsState()
    val downloadInProgress = activeDownload != null

    LaunchedEffect(playlistUrl) {
        if (playlistUrl.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val res = try {
            withContext(Dispatchers.IO) {
                MiddlewareApi.fetchPlaylistWithFallback(MIDDLEWARE_BASE_URL, playlistUrl)
            }
        } catch (_: Exception) {
            PlaylistResponse(
                success = false,
                playlist = null,
                songs = emptyList(),
                error = "NetworkError",
                message = resources.getString(R.string.playlist_error_cannot_connect),
            )
        }
        loading = false
        if (!res.success) {
            error = res.message ?: res.error ?: resources.getString(R.string.playlist_error_cannot_load)
            title = resources.getString(R.string.playlist_default_title)
            tracks = emptyList()
        } else {
            title = res.playlist?.name?.ifBlank { resources.getString(R.string.playlist_default_title) }
                ?: resources.getString(R.string.playlist_default_title)
            tracks = res.songs
        }
    }

    val palette = currentBeatMyBeatThemeProfile()
    val bgBrush = Brush.verticalGradient(
        colors = listOf(palette.backgroundTop, palette.backgroundBottom),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            PlaylistHeader(
                title = title,
                subtitle = when {
                    loading -> stringResource(R.string.playlist_loading)
                    error != null -> error!!
                    else -> "${tracks.size} ${stringResource(R.string.playlist_tracks_found)}"
                },
                downloadEnabled = !downloadInProgress,
                onPrimaryClick = {
                    if (tracks.isEmpty() || downloadInProgress) return@PlaylistHeader
                    val videoIds = tracks.map { it.id.trim() }.filter { it.length == 11 }
                    if (videoIds.isEmpty()) return@PlaylistHeader
                    SongDownloadService.enqueuePlaylistDownload(
                        context = context,
                        videoIds = videoIds,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tracks) { track ->
                    TrackRow(
                        track = track,
                        onPlay = {
                            if (downloadInProgress) return@TrackRow
                            SongDownloadService.enqueueDownload(
                                context = context,
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                videoId = track.id,
                                thumbnailUrl = track.imageUrl,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    subtitle: String,
    downloadEnabled: Boolean,
    onPrimaryClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                text = stringResource(R.string.playlist_download_all),
                onClick = onPrimaryClick,
                enabled = downloadEnabled,
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
            contentColor = MaterialTheme.colorScheme.onSurface,
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
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(RemoteArtworkCache.get(url)) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        if (bitmap != null) return@LaunchedEffect

        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                AppHttpClient.instance.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val bytes = res.body?.bytes() ?: return@use null
                    BitmapDecoding.decodeSampled(bytes, 160)
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

