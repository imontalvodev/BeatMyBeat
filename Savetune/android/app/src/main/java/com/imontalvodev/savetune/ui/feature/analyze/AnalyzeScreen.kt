package com.imontalvodev.savetune.ui.feature.analyze

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imontalvodev.savetune.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.savetune.ui.network.AudioDownloader
import com.imontalvodev.savetune.ui.theme.CherryBackgroundBottom
import com.imontalvodev.savetune.ui.theme.CherryBackgroundTop
import com.imontalvodev.savetune.ui.theme.NeonBackgroundBottom
import com.imontalvodev.savetune.ui.theme.NeonBackgroundTop
import com.imontalvodev.savetune.ui.theme.SavetuneThemeMode
import com.imontalvodev.savetune.ui.theme.ModeChip
import com.imontalvodev.savetune.ui.theme.PrimaryButton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@Composable
fun AnalyzeScreen(
    themeMode: SavetuneThemeMode,
    onToggleTheme: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = remember(themeMode) {
        when (themeMode) {
            SavetuneThemeMode.NeonMint -> Brush.verticalGradient(
                colors = listOf(NeonBackgroundTop, NeonBackgroundBottom),
            )

            SavetuneThemeMode.CherryPulse -> Brush.verticalGradient(
                colors = listOf(CherryBackgroundTop, CherryBackgroundBottom),
            )
        }
    }

    var mode by remember { mutableStateOf("playlist") }
    var playlistUrl by remember { mutableStateOf("") }
    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var songAlbum by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Savetune",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (themeMode == SavetuneThemeMode.NeonMint) "Neon" else "Cherry",
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { onToggleTheme() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ModeChip(
                            text = "Playlist",
                            selected = mode == "playlist",
                            onClick = { mode = "playlist" },
                        )
                        ModeChip(
                            text = "Song",
                            selected = mode == "song",
                            onClick = { mode = "song" },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (mode == "playlist") {
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { playlistUrl = it },
                            label = { Text("Playlist URL (Spotify/YouTube)") },
                            placeholder = { Text("Paste playlist link...") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = songTitle,
                            onValueChange = { songTitle = it },
                            label = { Text("Song title") },
                            placeholder = { Text("Song name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = songArtist,
                            onValueChange = { songArtist = it },
                            label = { Text("Artist (recommended)") },
                            placeholder = { Text("Artist name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = songAlbum,
                            onValueChange = { songAlbum = it },
                            label = { Text("Album (optional)") },
                            placeholder = { Text("Album name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    PrimaryButton(
                        text = if (mode == "playlist") "ANALYZE PLAYLIST" else "ANALYZE SONG",
                        onClick = {
                            if (mode == "song") {
                                if (songTitle.isBlank() && songArtist.isBlank() && songAlbum.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Introduce al menos el título o el artista.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    startAutoDownloadInApp(
                                        context = context,
                                        title = songTitle,
                                        artist = songArtist,
                                        album = songAlbum,
                                    )
                                }
                            } else {
                                if (playlistUrl.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Pega un enlace de playlist.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    val normalizedUrl = playlistUrl.trim()
                                    if (isYoutubePlaylistUrl(normalizedUrl)) {
                                        Toast.makeText(
                                            context,
                                            "Descargando playlist de YouTube...",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        scope.launch {
                                            val result = AudioDownloader.downloadYoutubeAlbumZipToAppMusic(
                                                context = context,
                                                middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                                playlistUrl = normalizedUrl,
                                            )
                                            if (result.success) {
                                                Toast.makeText(
                                                    context,
                                                    "Playlist descargada: ${result.extractedFiles} pistas",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Error playlist YouTube: ${result.error ?: "desconocido"}",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    } else {
                                        onOpenPlaylist(normalizedUrl)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun isYoutubePlaylistUrl(url: String): Boolean {
    val u = url.lowercase()
    return (u.contains("youtube.com") || u.contains("youtu.be") || u.contains("music.youtube.com")) &&
        u.contains("list=")
}

private fun startAutoDownloadInApp(
    context: android.content.Context,
    title: String,
    artist: String,
    album: String,
) {
    // Descarga en segundo plano utilizando OkHttp.
    val base = MIDDLEWARE_BASE_URL.trimEnd('/')
    val httpUrlBuilder = "$base/api/download-auto".toHttpUrlOrNull()?.newBuilder()
        ?: return

    if (title.isNotBlank()) httpUrlBuilder.addQueryParameter("title", title)
    if (artist.isNotBlank()) httpUrlBuilder.addQueryParameter("artist", artist)
    if (album.isNotBlank()) httpUrlBuilder.addQueryParameter("album", album)

    val url = httpUrlBuilder.build()

    val client = okhttp3.OkHttpClient.Builder()
        // Descargas pueden tardar bastante (search + stream)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .callTimeout(7, TimeUnit.MINUTES)
        .build()
    val request = okhttp3.Request.Builder()
        .url(url)
        .get()
        .build()

    Thread {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.os.Handler(context.mainLooper).post {
                        Toast.makeText(
                            context,
                            "Error al descargar: ${response.code}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@use
                }

                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("application/json")) {
                    android.os.Handler(context.mainLooper).post {
                        Toast.makeText(
                            context,
                            "No se pudo descargar la canción.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@use
                }

                val body = response.body ?: return@use
                val inputStream = body.byteStream()

                val dir = java.io.File(context.filesDir, ".music")
                if (!dir.exists()) dir.mkdirs()

                val fileNameFromHeader =
                    response.header("Content-Disposition")
                        ?.substringAfter("filename=\"")
                        ?.substringBeforeLast("\"")
                val safeName = fileNameFromHeader?.takeIf { it.isNotBlank() }
                    ?: (title.ifBlank { "track" } + ".mp3")

                val outFile = java.io.File(dir, safeName)
                java.io.FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        if (bytes > 0) output.write(buffer, 0, bytes)
                        bytes = inputStream.read(buffer)
                    }
                    output.flush()
                }

                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(
                        context,
                        "Descargado en: ${outFile.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } catch (e: Exception) {
            android.os.Handler(context.mainLooper).post {
                Toast.makeText(
                    context,
                    "Error de red: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }.start()
}


