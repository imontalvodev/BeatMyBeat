package com.imontalvodev.beatmybeat.ui.feature.analyze

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.network.MIDDLEWARE_BASE_URL
import com.imontalvodev.beatmybeat.ui.network.AudioDownloader
import com.imontalvodev.beatmybeat.ui.network.SongSuggestion
import com.imontalvodev.beatmybeat.ui.network.YouTubeSearchClient
import com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics
import com.imontalvodev.beatmybeat.service.SongDownloadService
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.ModeChip
import com.imontalvodev.beatmybeat.ui.theme.PrimaryButton
import com.imontalvodev.beatmybeat.ui.theme.SuggestionListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.ui.theme.ActiveDownloadProgressSection
import com.imontalvodev.beatmybeat.download.DownloadProgressBus
import com.imontalvodev.beatmybeat.ui.network.fetchYouTubeSongMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.imontalvodev.beatmybeat.LocalSnackbarHostState

@Composable
fun AnalyzeScreen(
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
    var songDownloadInfo by remember { mutableStateOf<String?>(null) }
    var playlistInputError by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf(AudioDownloader.DownloadFormat.MP3) }

    val activeDownload by DownloadProgressBus.state.collectAsState()
    val downloadInProgress = activeDownload != null

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showSnack(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            AppMiniBrand()

            Spacer(modifier = Modifier.height(16.dp))

            val tabPlaylist = stringResource(R.string.analyze_tab_playlist)
            val tabSong = stringResource(R.string.analyze_tab_song)
            val selectedTabIndex = if (mode == "playlist") 0 else 1
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = mode == "playlist",
                    onClick = { mode = "playlist" },
                    text = { Text(tabPlaylist) },
                )
                Tab(
                    selected = mode == "song",
                    onClick = { mode = "song" },
                    text = { Text(tabSong) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                            label = { Text(stringResource(R.string.analyze_playlist_url_label)) },
                            placeholder = { Text(stringResource(R.string.analyze_playlist_url_placeholder)) },
                            isError = playlistInputError != null,
                            supportingText = if (playlistInputError != null) {
                                {
                                    Text(
                                        text = playlistInputError!!,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                null
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Link, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = songTitle,
                            onValueChange = { songTitle = it },
                            placeholder = { Text(stringResource(R.string.analyze_song_title_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songArtist,
                            onValueChange = { songArtist = it },
                            placeholder = { Text(stringResource(R.string.analyze_artist_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songAlbum,
                            onValueChange = { songAlbum = it },
                            placeholder = { Text(stringResource(R.string.analyze_album_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.analyze_download_format),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AudioDownloader.DownloadFormat.entries.forEach { format ->
                            ModeChip(
                                text = format.label,
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    PrimaryButton(
                        text = when {
                            downloadInProgress -> stringResource(R.string.analyze_cta_downloading)
                            mode == "song" -> stringResource(R.string.analyze_cta_song)
                            else -> stringResource(R.string.analyze_cta_playlist)
                        },
                        enabled = !downloadInProgress,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
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
                                                suggestionError =
                                                    context.getString(R.string.analyze_no_results)
                                            }
                                        } catch (e: Exception) {
                                            suggestionError =
                                                context.getString(R.string.analyze_connection_error)
                                        } finally {
                                            searchingSuggestions = false
                                        }
                                    }
                                }
                            } else {
                                val normalizedUrl = playlistUrl.trim()
                                if (normalizedUrl.isBlank()) {
                                    playlistInputError =
                                        context.getString(R.string.analyze_playlist_url_required)
                                } else {
                                    when (val parsed = parseYouTubeInput(normalizedUrl)) {
                                        is ParsedYouTubeInput.Invalid -> {
                                            playlistInputError = context.getString(parsed.reasonRes)
                                        }
                                        is ParsedYouTubeInput.PlaylistOrAlbum -> {
                                            scope.launch {
                                                try {
                                                    val videoIds = withContext(Dispatchers.IO) {
                                                        YouTubeSearchClient.fetchPlaylistVideoIds(parsed.listId, limit = 200)
                                                    }
                                                    if (videoIds.isEmpty()) {
                                                        playlistInputError =
                                                            context.getString(R.string.analyze_playlist_resolve_empty)
                                                        return@launch
                                                    }
                                                    SongDownloadService.enqueuePlaylistDownload(
                                                        context = context,
                                                        videoIds = videoIds,
                                                        format = selectedFormat,
                                                    )
                                                    showSnack(context.getString(R.string.download_started_background))
                                                } catch (_: Exception) {
                                                    playlistInputError =
                                                        context.getString(R.string.analyze_playlist_resolve_failed)
                                                }
                                            }
                                        }
                                        is ParsedYouTubeInput.SingleSong -> {
                                            scope.launch {
                                                val metadata = withContext(Dispatchers.IO) {
                                                    fetchYouTubeSongMetadata(parsed.videoId)
                                                }
                                                SongDownloadService.enqueueDownload(
                                                    context = context,
                                                    title = metadata.title,
                                                    artist = metadata.artist,
                                                    album = "",
                                                    videoId = parsed.videoId,
                                                    thumbnailUrl = metadata.thumbnailUrl,
                                                    format = selectedFormat,
                                                )
                                                songDownloadInfo = context.getString(R.string.download_started_background)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (activeDownload != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ActiveDownloadProgressSection(
                            download = activeDownload!!,
                            onCancel = { SongDownloadService.cancelDownload(context) },
                        )
                    } else if (mode == "song" && songDownloadInfo != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = songDownloadInfo!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    if (mode == "song") {
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            searchingSuggestions -> {
                                SuggestionListSkeleton()
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
                                        text = stringResource(R.string.analyze_results_title),
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
                                                contentColor = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    SuggestionThumbnail(
                                                        url = suggestion.thumbnailUrl,
                                                        contentDescription = suggestion.title.ifBlank {
                                                            stringResource(R.string.analyze_song_title_placeholder)
                                                        },
                                                    )
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                                    ) {
                                                        Text(
                                                            text = suggestion.title.ifBlank {
                                                                stringResource(R.string.analyze_no_title)
                                                            },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Text(
                                                            text = suggestion.artist.ifBlank {
                                                                stringResource(R.string.analyze_unknown_artist)
                                                            },
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
            title = { Text(stringResource(R.string.analyze_download_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.analyze_download_dialog_message,
                            suggestion.title,
                            suggestion.artist,
                        ),
                    )
                    activeDownload?.let { progress ->
                        Spacer(modifier = Modifier.height(12.dp))
                        ActiveDownloadProgressSection(
                            download = progress,
                            onCancel = { SongDownloadService.cancelDownload(context) },
                            showBackgroundHint = false,
                        )
                    }
                    if (downloadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
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
                                SongDownloadService.enqueueDownload(
                                    context = context,
                                    title = suggestion.title,
                                    artist = suggestion.artist,
                                    album = "",
                                    videoId = suggestion.videoId,
                                    thumbnailUrl = suggestion.thumbnailUrl,
                                    format = selectedFormat,
                                )
                                selectedSuggestion = null
                                songDownloadInfo = context.getString(R.string.download_started_background)
                            } catch (e: Exception) {
                                android.util.Log.e("AnalyzeScreen", "Download crash: ${e.javaClass.simpleName}: ${e.message}", e)
                                downloadError = context.getString(
                                    R.string.analyze_download_error,
                                    e.javaClass.simpleName,
                                )
                            } finally {
                                downloadingSuggestion = false
                            }
                        }
                    },
                ) {
                    Text(
                        if (downloadingSuggestion) {
                            stringResource(R.string.analyze_downloading_button)
                        } else {
                            stringResource(R.string.analyze_download_confirm)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !downloadingSuggestion,
                    onClick = { selectedSuggestion = null },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private sealed interface ParsedYouTubeInput {
    data class PlaylistOrAlbum(val url: String, val listId: String) : ParsedYouTubeInput
    data class SingleSong(val videoId: String) : ParsedYouTubeInput
    data class Invalid(@StringRes val reasonRes: Int) : ParsedYouTubeInput
}

private fun parseYouTubeInput(raw: String): ParsedYouTubeInput {
    val url = raw.toHttpUrlOrNull()
        ?: return ParsedYouTubeInput.Invalid(R.string.analyze_url_invalid_format)

    val host = url.host.lowercase().removePrefix("www.")
    val allowedHosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    if (host !in allowedHosts) {
        return ParsedYouTubeInput.Invalid(R.string.analyze_url_host_not_allowed)
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

    return ParsedYouTubeInput.Invalid(R.string.analyze_url_invalid_generic)
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

@Composable
private fun SuggestionThumbnail(url: String, contentDescription: String) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val mod = Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(placeholderColor)
    if (url.isBlank()) {
        Box(modifier = mod)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
    }
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

