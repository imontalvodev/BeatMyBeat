package com.imontalvodev.beatmybeat.ui.feature.analyze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.beatmybeat.ui.network.AudioDownloader
import com.imontalvodev.beatmybeat.ui.network.RemoteArtworkCache
import com.imontalvodev.beatmybeat.ui.network.SongSuggestion
import com.imontalvodev.beatmybeat.ui.network.YouTubeSearchClient
import com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.ModeChip
import com.imontalvodev.beatmybeat.ui.theme.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import com.imontalvodev.beatmybeat.service.BeatMyBeatForegroundService

@Composable
fun AnalyzeScreen(
    themeName: String,
    onOpenPlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = currentBeatMyBeatThemeProfile()
    val backgroundBrush = remember(palette.id) {
        Brush.verticalGradient(
            colors = listOf(palette.backgroundTop, palette.backgroundBottom),
        )
    }

    var mode by remember { mutableStateOf("song") }
    var playlistUrl by remember { mutableStateOf("") }
    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var songAlbum by remember { mutableStateOf("") }
    var searchingSuggestions by remember { mutableStateOf(false) }
    var suggestionError by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<SongSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<SongSuggestion?>(null) }
    var downloadingSuggestion by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var playlistInputError by remember { mutableStateOf<String?>(null) }
    var playlistDownloadRunning by remember { mutableStateOf(false) }
    var playlistDownloadTotal by remember { mutableStateOf(0) }
    var playlistDownloadDone by remember { mutableStateOf(0) }
    var playlistDownloadFailed by remember { mutableStateOf(0) }
    var playlistCurrentTitle by remember { mutableStateOf("") }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Ir al reproductor",
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { onOpenPlayer() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs Playlist / Song
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

            Spacer(modifier = Modifier.height(24.dp))

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

                    Spacer(modifier = Modifier.height(16.dp))

                    if (mode == "playlist") {
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = {
                                playlistUrl = it
                                playlistInputError = null
                            },
                            label = { Text("URL de playlist/album/canción (YouTube)") },
                            placeholder = { Text("https://youtube.com/... o https://music.youtube.com/...") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = songTitle,
                            onValueChange = { songTitle = it },
                            placeholder = { Text("Song title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songArtist,
                            onValueChange = { songArtist = it },
                            placeholder = { Text("Artist (recommended)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songAlbum,
                            onValueChange = { songAlbum = it },
                            placeholder = { Text("Album (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    PrimaryButton(
                        text = when {
                            mode == "song" -> "ANALYZE SONG"
                            playlistDownloadRunning -> "DESCARGANDO..."
                            else -> "ANALYZE PLAYLIST"
                        },
                        enabled = !playlistDownloadRunning,
                        onClick = {
                            if (mode == "song") {
                                if (songTitle.isBlank() && songArtist.isBlank() && songAlbum.isBlank()) {
                                    // Mantener feedback por pantalla (y no notificación) en validaciones rápidas.
                                    // En caso de descarga real, usamos notificaciones.
                                } else {
                                    scope.launch {
                                        searchingSuggestions = true
                                        suggestionError = null
                                        suggestions = emptyList()
                                        try {
                                            val searchQuery = listOf(songTitle, songArtist, songAlbum)
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() }
                                                .joinToString(" ")
                                            val results = withContext(Dispatchers.IO) {
                                                YouTubeSearchClient.search(searchQuery, limit = 10)
                                            }
                            if (results.isNotEmpty()) {
                                suggestions = results.map { r ->
                                    // Muchos vídeos tienen "Artista - Título" en el título
                                    val (parsedTitle, parsedArtist) = parseYouTubeTitle(r.title, r.channel)
                                    SongSuggestion(
                                        title = parsedTitle,
                                        artist = parsedArtist,
                                        videoId = r.videoId,
                                        thumbnailUrl = r.thumbnailUrl,
                                        durationText = r.durationText,
                                    )
                                }
                                            } else {
                                                suggestionError = "No se encontraron resultados para esa búsqueda."
                                            }
                                        } catch (e: Exception) {
                                            suggestionError =
                                                "Error de conexión. Comprueba tu internet e inténtalo de nuevo."
                                        } finally {
                                            searchingSuggestions = false
                                        }
                                    }
                                }
                            } else {
                                val normalizedUrl = playlistUrl.trim()
                                if (normalizedUrl.isBlank()) {
                                    playlistInputError = "Introduce una URL de YouTube o YouTube Music."
                                } else {
                                    when (val parsed = parseYouTubeInput(normalizedUrl)) {
                                        is ParsedYouTubeInput.Invalid -> {
                                            playlistInputError = parsed.reason
                                        }
                                        is ParsedYouTubeInput.PlaylistOrAlbum -> {
                                            scope.launch {
                                                try {
                                                    val videoIds = withContext(Dispatchers.IO) {
                                                        YouTubeSearchClient.fetchPlaylistVideoIds(parsed.listId, limit = 200)
                                                    }
                                                    if (videoIds.isEmpty()) {
                                                        playlistInputError = "No se pudieron resolver canciones de la playlist/álbum."
                                                        return@launch
                                                    }

                                                    playlistDownloadRunning = true
                                                    playlistDownloadTotal = videoIds.size
                                                    playlistDownloadDone = 0
                                                    playlistDownloadFailed = 0
                                                    playlistCurrentTitle = "Preparando descargas..."
                                                    BeatMyBeatForegroundService.startDownload(
                                                        context = context,
                                                        title = "Descargando playlist",
                                                        subtitle = "0/${videoIds.size}",
                                                    )

                                                    var downloaded = 0
                                                    var failed = 0
                                                    videoIds.forEach { videoId ->
                                                        val metadata = withContext(Dispatchers.IO) {
                                                            fetchYouTubeSongMetadata(videoId)
                                                        }
                                                        playlistCurrentTitle = metadata.title
                                                        BeatMyBeatForegroundService.startDownload(
                                                            context = context,
                                                            title = "Descargando playlist",
                                                            subtitle = "${downloaded + failed}/${videoIds.size} · ${metadata.title.take(40)}",
                                                        )
                                                        val single = AudioDownloader.downloadAutoToAppMusic(
                                                            context = context,
                                                            middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                                            title = metadata.title,
                                                            artist = metadata.artist,
                                                            album = "",
                                                            videoId = videoId,
                                                            thumbnailUrl = metadata.thumbnailUrl,
                                                        )
                                                        if (single.success) downloaded++ else failed++
                                                        playlistDownloadDone = downloaded
                                                        playlistDownloadFailed = failed
                                                    }
                                                    if (downloaded <= 0) {
                                                        playlistInputError = "No se pudo descargar ninguna canción de esa playlist."
                                                    } else if (failed > 0) {
                                                        playlistInputError = "Descargadas $downloaded de ${videoIds.size}. Fallidas: $failed."
                                                    }
                                                } catch (_: Exception) {
                                                    playlistInputError =
                                                        "No se pudo resolver la playlist directamente desde YouTube. Inténtalo más tarde."
                                                } finally {
                                                    playlistDownloadRunning = false
                                                    playlistCurrentTitle = ""
                                                    BeatMyBeatForegroundService.stopDownload(context)
                                                }
                                            }
                                        }
                                        is ParsedYouTubeInput.SingleSong -> {
                                            scope.launch {
                                                val metadata = withContext(Dispatchers.IO) {
                                                    fetchYouTubeSongMetadata(parsed.videoId)
                                                }
                                                val result = AudioDownloader.downloadAutoToAppMusic(
                                                    context = context,
                                                    middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                                    title = metadata.title,
                                                    artist = metadata.artist,
                                                    album = "",
                                                    videoId = parsed.videoId,
                                                    thumbnailUrl = metadata.thumbnailUrl,
                                                )
                                                if (!result.success) {
                                                    playlistInputError =
                                                        result.error ?: "No se pudo descargar la canción desde esa URL."
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (mode == "playlist" && playlistInputError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playlistInputError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (mode == "playlist" && playlistDownloadRunning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress =
                            if (playlistDownloadTotal > 0) {
                                playlistDownloadDone.toFloat() / playlistDownloadTotal.toFloat()
                            } else {
                                0f
                            }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Descargando ${playlistDownloadDone}/${playlistDownloadTotal} · Fallidas: $playlistDownloadFailed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                        if (playlistCurrentTitle.isNotBlank()) {
                            Text(
                                text = "Actual: ${playlistCurrentTitle.take(60)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            text = "Puedes dejar la app en segundo plano mientras termina.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }

                    if (mode == "song") {
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            searchingSuggestions -> {
                                Text(
                                    text = "Buscando canciones...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                )
                            }

                            suggestionError != null -> {
                                Text(
                                    text = suggestionError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            suggestions.isNotEmpty() -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Resultados",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    suggestions.forEach { suggestion ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !downloadingSuggestion) {
                                                    selectedSuggestion = suggestion
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            ),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    SuggestionThumbnail(url = suggestion.thumbnailUrl)
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                                    ) {
                                                        Text(
                                                            text = suggestion.title.ifBlank { "Sin título" },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Text(
                                                            text = suggestion.artist.ifBlank { "Artista desconocido" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            }
        }
    }

    if (selectedSuggestion != null) {
        val suggestion = selectedSuggestion!!
        AlertDialog(
            onDismissRequest = {
                if (!downloadingSuggestion) selectedSuggestion = null
            },
            title = { Text("Descargar canción") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("¿Quieres descargar \"${suggestion.title}\" de ${suggestion.artist}?")
                    if (downloadError != null) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = downloadError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloadingSuggestion,
                    onClick = {
                        scope.launch {
                            downloadingSuggestion = true
                            downloadError = null
                            try {
                                val result = AudioDownloader.downloadAutoToAppMusic(
                                    context = context,
                                    middlewareBaseUrl = MIDDLEWARE_BASE_URL,
                                    title = suggestion.title,
                                    artist = suggestion.artist,
                                    album = "",
                                    videoId = suggestion.videoId,
                                    thumbnailUrl = suggestion.thumbnailUrl,
                                )
                                if (!result.success) {
                                    downloadError = result.error ?: "Error desconocido"
                                } else {
                                    selectedSuggestion = null
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AnalyzeScreen", "Download crash: ${e.javaClass.simpleName}: ${e.message}", e)
                                downloadError = "Error: ${e.javaClass.simpleName}"
                            } finally {
                                downloadingSuggestion = false
                            }
                        }
                    },
                ) {
                    Text(if (downloadingSuggestion) "Descargando..." else "Sí, descargar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloadingSuggestion,
                    onClick = { selectedSuggestion = null },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private sealed interface ParsedYouTubeInput {
    data class PlaylistOrAlbum(val url: String, val listId: String) : ParsedYouTubeInput
    data class SingleSong(val videoId: String) : ParsedYouTubeInput
    data class Invalid(val reason: String) : ParsedYouTubeInput
}

private fun parseYouTubeInput(raw: String): ParsedYouTubeInput {
    val url = raw.toHttpUrlOrNull()
        ?: return ParsedYouTubeInput.Invalid(
            "Formato inválido. Usa una URL completa de YouTube o YouTube Music.",
        )

    val host = url.host.lowercase().removePrefix("www.")
    val allowedHosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    if (host !in allowedHosts) {
        return ParsedYouTubeInput.Invalid(
            "Solo se admiten enlaces de YouTube o YouTube Music.",
        )
    }

    val videoId = extractYouTubeVideoId(url)
    if (videoId != null) {
        return ParsedYouTubeInput.SingleSong(videoId)
    }

    val listId = url.queryParameter("list")?.trim().orEmpty()
    if (listId.isNotBlank()) {
        val normalized = buildCanonicalPlaylistUrl(url, listId)
        return ParsedYouTubeInput.PlaylistOrAlbum(normalized, listId)
    }

    return ParsedYouTubeInput.Invalid(
        "URL no válida. Introduce un enlace de playlist/álbum o de canción de YouTube/YT Music.",
    )
}

private fun buildCanonicalPlaylistUrl(url: HttpUrl, listId: String): String {
    val builder = HttpUrl.Builder()
        .scheme("https")
        .host("www.youtube.com")
        .addPathSegment("playlist")
        .addQueryParameter("list", listId)

    // Para casos como youtu.be/<videoId>?list=... preservamos v para mejorar compatibilidad backend.
    val v = extractYouTubeVideoId(url)
    if (!v.isNullOrBlank()) builder.addQueryParameter("v", v)
    return builder.build().toString()
}

private fun extractYouTubeVideoId(url: HttpUrl): String? {
    val host = url.host.lowercase().removePrefix("www.")
    if (host == "youtu.be") {
        val shortId = url.pathSegments.firstOrNull().orEmpty()
        return shortId.takeIf { it.length == 11 }
    }

    val path = url.encodedPath.lowercase()
    val fromQuery = url.queryParameter("v")?.trim().orEmpty()
    if (path == "/watch" && fromQuery.length == 11) {
        return fromQuery
    }

    val segments = url.pathSegments
    if (segments.size >= 2 && (segments[0] == "shorts" || segments[0] == "live")) {
        return segments[1].takeIf { it.length == 11 }
    }
    return null
}

private data class SongUrlMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
)

@Composable
private fun SuggestionThumbnail(url: String) {
    val client = remember { OkHttpClient() }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(RemoteArtworkCache.get(url)) }

    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
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
                .size(54.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                ),
        )
    } else {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                ),
        )
    }
}

private fun fetchYouTubeSongMetadata(videoId: String): SongUrlMetadata {
    val fallback = SongUrlMetadata(
        title = "YouTube Track $videoId",
        artist = "Unknown artist",
        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
    )

    return runCatching {
        val targetUrl = "https://www.youtube.com/watch?v=$videoId"
        val oEmbed = "https://www.youtube.com/oembed?url=$targetUrl&format=json"
        val req = Request.Builder().url(oEmbed).get().build()
        OkHttpClient().newCall(req).execute().use { res ->
            if (!res.isSuccessful) return fallback
            val body = res.body?.string().orEmpty()
            val json = org.json.JSONObject(body)
            SongUrlMetadata(
                title = json.optString("title", fallback.title).ifBlank { fallback.title },
                artist = json.optString("author_name", fallback.artist).ifBlank { fallback.artist },
                thumbnailUrl = json.optString("thumbnail_url", fallback.thumbnailUrl).ifBlank { fallback.thumbnailUrl },
            )
        }
    }.getOrElse { fallback }
}

/**
 * Intenta separar "Artista - Título" del nombre del vídeo de YouTube.
 * Si no hay separador claro, usa el nombre del canal como artista y el título tal cual.
 * También elimina sufijos comunes como "(Official Audio)", "[Lyrics]", etc.
 */
private fun parseYouTubeTitle(rawTitle: String, channel: String): Pair<String, String> {
    val cleanSuffixRegex = Regex(
        """\s*[\(\[](official\s*(audio|video|music\s*video|lyric\s*video)?|lyrics?|audio|hd|4k|explicit|ft\.?[^)\]]*|feat\.?[^)\]]*)[\)\]]\s*""",
        RegexOption.IGNORE_CASE,
    )
    val cleaned = rawTitle.replace(cleanSuffixRegex, "").trim()

    // Separadores comunes: " - ", " – ", " — "
    val separators = listOf(" - ", " – ", " — ")
    for (sep in separators) {
        val idx = cleaned.indexOf(sep)
        if (idx > 0) {
            val artistRaw = cleaned.substring(0, idx).trim()
            val title = cleaned.substring(idx + sep.length).trim()
            if (artistRaw.isNotBlank() && title.isNotBlank()) {
                return Pair(title, cleanArtistForLyrics(artistRaw))
            }
        }
    }
    // Sin separador: usar canal como artista y título limpio
    return Pair(cleaned.ifBlank { rawTitle }, cleanArtistForLyrics(channel))
}

