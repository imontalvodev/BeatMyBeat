package com.imontalvodev.beatmybeat.ui.feature.analyze

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.ui.network.AudioDownloader
import com.imontalvodev.beatmybeat.ui.network.SongSuggestion
import com.imontalvodev.beatmybeat.ui.network.YouTubeSearchClient
import com.imontalvodev.beatmybeat.ui.network.YouTubeSearchSource
import com.imontalvodev.beatmybeat.ui.network.YouTubeSongMetadata
import com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics
import com.imontalvodev.beatmybeat.service.SongDownloadService
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.ModeChip
import com.imontalvodev.beatmybeat.ui.theme.PrimaryButton
import com.imontalvodev.beatmybeat.ui.theme.SuggestionListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.ui.theme.ActiveDownloadProgressSection
import com.imontalvodev.beatmybeat.download.DownloadProgressBus
import com.imontalvodev.beatmybeat.download.LyricsLibraryStats
import com.imontalvodev.beatmybeat.download.LyricsLibraryStatsCalculator
import com.imontalvodev.beatmybeat.download.LyricsProgressBus
import com.imontalvodev.beatmybeat.service.LyricsBatchService
import com.imontalvodev.beatmybeat.ui.feature.player.PlayerViewModel
import com.imontalvodev.beatmybeat.ui.theme.ActiveLyricsBatchProgressSection
import com.imontalvodev.beatmybeat.ui.network.fetchYouTubeSongMetadata
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    var mode by remember { mutableStateOf("url") }
    var urlInput by remember { mutableStateOf("") }
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
    var urlInputError by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf(AudioDownloader.DownloadFormat.MP3) }

    // URL preview state
    var urlResolving by remember { mutableStateOf(false) }
    var urlPreviewTitle by remember { mutableStateOf("") }
    var urlPreviewTracks by remember { mutableStateOf<List<PreviewTrack>>(emptyList()) }
    var urlPreviewError by remember { mutableStateOf<String?>(null) }

    val activeDownload by DownloadProgressBus.state.collectAsState()
    val downloadInProgress = activeDownload != null
    val activeLyricsBatch by LyricsProgressBus.state.collectAsState()
    val lyricsBatchInProgress = activeLyricsBatch != null

    val playerViewModel: PlayerViewModel = viewModel()
    val deviceTracks by playerViewModel.tracks.collectAsState()
    val librarySyncing by playerViewModel.librarySyncing.collectAsState()
    var lyricsStats by remember { mutableStateOf<LyricsLibraryStats?>(null) }
    var lyricsStatsLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    fun showSnack(message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(mode) {
        if (mode == "lyrics") {
            playerViewModel.syncLibrary(auto = true)
        }
    }

    LaunchedEffect(mode, deviceTracks, librarySyncing, activeLyricsBatch) {
        if (mode != "lyrics" || librarySyncing || activeLyricsBatch != null) return@LaunchedEffect
        lyricsStatsLoading = true
        lyricsStats = withContext(Dispatchers.IO) {
            LyricsLibraryStatsCalculator.compute(context.applicationContext, deviceTracks)
        }
        lyricsStatsLoading = false
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

            val tabUrl = stringResource(R.string.analyze_tab_url)
            val tabSong = stringResource(R.string.analyze_tab_song)
            val tabLyrics = stringResource(R.string.analyze_tab_lyrics)
            val selectedTabIndex = when (mode) {
                "url" -> 0
                "song" -> 1
                else -> 2
            }
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = mode == "url",
                    onClick = { mode = "url" },
                    text = { Text(tabUrl) },
                )
                Tab(
                    selected = mode == "song",
                    onClick = { mode = "song" },
                    text = { Text(tabSong) },
                )
                Tab(
                    selected = mode == "lyrics",
                    onClick = { mode = "lyrics" },
                    text = { Text(tabLyrics) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.xl),
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

                    if (mode == "lyrics") {
                        when {
                            librarySyncing || lyricsStatsLoading -> {
                                Text(
                                    text = stringResource(R.string.analyze_lyrics_scanning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SuggestionListSkeleton()
                            }
                            deviceTracks.isEmpty() -> {
                                Text(
                                    text = stringResource(R.string.analyze_lyrics_no_tracks),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            }
                            else -> {
                                val stats = lyricsStats
                                if (stats != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.analyze_lyrics_summary,
                                            stats.eligibleTracks,
                                            stats.cachedTracks,
                                            stats.pendingTracks,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    } else if (mode == "url") {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                urlInputError = null
                            },
                            label = { Text(stringResource(R.string.analyze_url_label)) },
                            placeholder = { Text(stringResource(R.string.analyze_url_placeholder)) },
                            isError = urlInputError != null,
                            supportingText = if (urlInputError != null) {
                                {
                                    Text(
                                        text = urlInputError!!,
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
                            shape = RoundedCornerShape(Radius.md),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songArtist,
                            onValueChange = { songArtist = it },
                            placeholder = { Text(stringResource(R.string.analyze_artist_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.md),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = songAlbum,
                            onValueChange = { songAlbum = it },
                            placeholder = { Text(stringResource(R.string.analyze_album_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.md),
                        )
                    }

                    if (mode != "lyrics") {
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
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    if (mode == "lyrics") {
                        PrimaryButton(
                            text = if (lyricsBatchInProgress) {
                                stringResource(R.string.analyze_lyrics_fetching)
                            } else {
                                stringResource(R.string.analyze_lyrics_cta)
                            },
                            enabled = !lyricsBatchInProgress &&
                                !librarySyncing &&
                                !lyricsStatsLoading &&
                                (lyricsStats?.eligibleTracks ?: 0) > 0,
                            onClick = {
                                LyricsBatchService.enqueueBatch(context)
                                showSnack(resources.getString(R.string.lyrics_batch_started_background))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                    PrimaryButton(
                        text = when {
                            downloadInProgress -> stringResource(R.string.analyze_cta_downloading)
                            mode == "song" -> stringResource(R.string.analyze_cta_song)
                            urlResolving -> stringResource(R.string.analyze_url_resolving)
                            else -> stringResource(R.string.analyze_cta_url)
                        },
                        enabled = !downloadInProgress && !urlResolving,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (mode == "song") {
                                if (songTitle.isBlank() && songArtist.isBlank() && songAlbum.isBlank()) {
                                    showSnack(resources.getString(R.string.analyze_song_input_required))
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
                                                    val (parsedTitle, parsedArtist) = when (r.source) {
                                                        YouTubeSearchSource.YOUTUBE_MUSIC ->
                                                            r.title to r.channel
                                                        YouTubeSearchSource.YOUTUBE ->
                                                            parseYouTubeTitle(r.title, r.channel)
                                                    }
                                                    SongSuggestion(
                                                        title = parsedTitle,
                                                        artist = parsedArtist,
                                                        videoId = r.videoId,
                                                        thumbnailUrl = r.thumbnailUrl,
                                                        durationText = r.durationText,
                                                        source = r.source,
                                                    )
                                                }
                                            } else {
                                                suggestionError =
                                                    resources.getString(R.string.analyze_no_results)
                                            }
                                        } catch (e: Exception) {
                                            suggestionError =
                                                resources.getString(R.string.analyze_connection_error)
                                        } finally {
                                            searchingSuggestions = false
                                        }
                                    }
                                }
                            } else {
                                val normalizedUrl = urlInput.trim()
                                if (normalizedUrl.isBlank()) {
                                    urlInputError =
                                        resources.getString(R.string.analyze_playlist_url_required)
                                    showSnack(resources.getString(R.string.analyze_playlist_url_required))
                                } else {
                                    when (val parsed = parseYouTubeInput(normalizedUrl)) {
                                        is ParsedYouTubeInput.Invalid -> {
                                            urlInputError = resources.getString(parsed.reasonRes)
                                        }
                                        is ParsedYouTubeInput.PlaylistOrAlbum -> {
                                            scope.launch {
                                                urlResolving = true
                                                urlPreviewError = null
                                                urlPreviewTracks = emptyList()
                                                urlPreviewTitle = ""
                                                try {
                                                    val info = withContext(Dispatchers.IO) {
                                                        YouTubeSearchClient.fetchPlaylistInfo(parsed.listId, limit = 200)
                                                    }
                                                    if (info.videoIds.isEmpty()) {
                                                        urlPreviewError =
                                                            resources.getString(R.string.analyze_playlist_resolve_empty)
                                                    } else {
                                                        urlPreviewTitle = info.title.ifBlank { "Playlist" }
                                                        val tracks = withContext(Dispatchers.IO) {
                                                            info.videoIds.map { id ->
                                                                async {
                                                                    val meta = fetchYouTubeSongMetadata(id)
                                                                    PreviewTrack(
                                                                        videoId = id,
                                                                        title = meta.title,
                                                                        artist = meta.artist,
                                                                        thumbnailUrl = meta.thumbnailUrl,
                                                                    )
                                                                }
                                                            }.awaitAll()
                                                        }
                                                        urlPreviewTracks = tracks
                                                    }
                                                } catch (_: Exception) {
                                                    urlPreviewError =
                                                        resources.getString(R.string.analyze_playlist_resolve_failed)
                                                } finally {
                                                    urlResolving = false
                                                }
                                            }
                                        }
                                        is ParsedYouTubeInput.SingleSong -> {
                                            scope.launch {
                                                urlResolving = true
                                                urlPreviewError = null
                                                urlPreviewTracks = emptyList()
                                                urlPreviewTitle = ""
                                                try {
                                                    val meta = withContext(Dispatchers.IO) {
                                                        fetchYouTubeSongMetadata(parsed.videoId)
                                                    }
                                                    urlPreviewTitle = resources.getString(R.string.analyze_url_preview_single)
                                                    urlPreviewTracks = listOf(
                                                        PreviewTrack(
                                                            videoId = parsed.videoId,
                                                            title = meta.title,
                                                            artist = meta.artist,
                                                            thumbnailUrl = meta.thumbnailUrl,
                                                        )
                                                    )
                                                } catch (_: Exception) {
                                                    urlPreviewError =
                                                        resources.getString(R.string.analyze_connection_error)
                                                } finally {
                                                    urlResolving = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    }

                    if (activeLyricsBatch != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ActiveLyricsBatchProgressSection(
                            batch = activeLyricsBatch!!,
                            onCancel = { LyricsBatchService.cancelBatch(context) },
                        )
                    }

                    if (activeDownload != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ActiveDownloadProgressSection(
                            download = activeDownload!!,
                            onCancel = { SongDownloadService.cancelDownload(context) },
                        )
                    } else if (songDownloadInfo != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = songDownloadInfo!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    if (mode == "url") {
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            urlResolving -> {
                                SuggestionListSkeleton()
                            }
                            urlPreviewError != null -> {
                                Text(
                                    text = urlPreviewError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            urlPreviewTracks.isNotEmpty() -> {
                                UrlPreviewSection(
                                    title = urlPreviewTitle,
                                    tracks = urlPreviewTracks,
                                    downloadEnabled = !downloadInProgress,
                                    onDownloadAll = {
                                        val videoIds = urlPreviewTracks.map { it.videoId }
                                        if (urlPreviewTracks.size == 1) {
                                            val t = urlPreviewTracks[0]
                                            SongDownloadService.enqueueDownload(
                                                context = context,
                                                title = t.title,
                                                artist = t.artist,
                                                album = "",
                                                videoId = t.videoId,
                                                thumbnailUrl = t.thumbnailUrl,
                                                format = selectedFormat,
                                            )
                                        } else {
                                            SongDownloadService.enqueuePlaylistDownload(
                                                context = context,
                                                videoIds = videoIds,
                                                format = selectedFormat,
                                                playlistName = urlPreviewTitle,
                                            )
                                        }
                                        showSnack(resources.getString(R.string.download_started_background))
                                    },
                                    onDownloadSingle = { track ->
                                        SongDownloadService.enqueueDownload(
                                            context = context,
                                            title = track.title,
                                            artist = track.artist,
                                            album = "",
                                            videoId = track.videoId,
                                            thumbnailUrl = track.thumbnailUrl,
                                            format = selectedFormat,
                                        )
                                        showSnack(resources.getString(R.string.download_started_background))
                                    },
                                )
                            }
                        }
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
                                            shape = RoundedCornerShape(Radius.sm),
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
                                                            style = AppText.trackTitle,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Text(
                                                            text = suggestion.artist.ifBlank {
                                                                stringResource(R.string.analyze_unknown_artist)
                                                            },
                                                            style = AppText.trackArtist,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                        )
                                                        if (suggestion.durationText.isNotBlank()) {
                                                            Text(
                                                                text = suggestion.durationText,
                                                                style = AppText.meta,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            )
                                                        }
                                                    }
                                                    SearchSourceBadge(source = suggestion.source)
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
                                songDownloadInfo = resources.getString(R.string.download_started_background)
                            } catch (e: Exception) {
                                Logger.e("AnalyzeScreen", "Download crash: ${e.javaClass.simpleName}: ${e.message}", e)
                                downloadError = resources.getString(
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

private data class PreviewTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
)

internal sealed interface ParsedYouTubeInput {
    data class PlaylistOrAlbum(val url: String, val listId: String) : ParsedYouTubeInput
    data class SingleSong(val videoId: String) : ParsedYouTubeInput
    data class Invalid(@StringRes val reasonRes: Int) : ParsedYouTubeInput
}

internal fun parseYouTubeInput(raw: String): ParsedYouTubeInput {
    val url = raw.toHttpUrlOrNull()
        ?: return ParsedYouTubeInput.Invalid(R.string.analyze_url_invalid_format)

    val host = url.host.lowercase().removePrefix("www.")
    val allowedHosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    if (host !in allowedHosts) {
        return ParsedYouTubeInput.Invalid(R.string.analyze_url_host_not_allowed)
    }

    val listId = url.queryParameter("list")?.trim().orEmpty()
    if (listId.isNotBlank()) {
        val normalized = buildCanonicalPlaylistUrl(url, listId)
        return ParsedYouTubeInput.PlaylistOrAlbum(normalized, listId)
    }

    val videoId = extractYouTubeVideoId(url)
    if (videoId != null) {
        return ParsedYouTubeInput.SingleSong(videoId)
    }

    return ParsedYouTubeInput.Invalid(R.string.analyze_url_invalid_generic)
}

private fun buildCanonicalPlaylistUrl(url: HttpUrl, listId: String): String {
    val builder = HttpUrl.Builder()
        .scheme("https")
        .host("www.youtube.com")
        .addPathSegment("playlist")
        .addQueryParameter("list", listId)

    // Para casos como youtu.be/<videoId>?list=... preservamos v para resolver la canción concreta.
    val v = extractYouTubeVideoId(url)
    if (!v.isNullOrBlank()) builder.addQueryParameter("v", v)
    return builder.build().toString()
}

internal fun extractYouTubeVideoId(url: HttpUrl): String? {
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
private fun UrlPreviewSection(
    title: String,
    tracks: List<PreviewTrack>,
    downloadEnabled: Boolean,
    onDownloadAll: () -> Unit,
    onDownloadSingle: (PreviewTrack) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = title,
                    style = AppText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.analyze_url_preview_playlist, tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }

        PrimaryButton(
            text = if (tracks.size == 1) {
                stringResource(R.string.analyze_url_download_single)
            } else {
                stringResource(R.string.analyze_url_download_all, tracks.size)
            },
            enabled = downloadEnabled,
            onClick = onDownloadAll,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        tracks.forEach { track ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = downloadEnabled) { onDownloadSingle(track) },
                shape = RoundedCornerShape(Radius.sm),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SuggestionThumbnail(
                        url = track.thumbnailUrl,
                        contentDescription = track.title,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = track.title,
                            style = AppText.trackTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = track.artist,
                            style = AppText.trackArtist,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSourceBadge(source: YouTubeSearchSource) {
    val label = when (source) {
        YouTubeSearchSource.YOUTUBE_MUSIC -> stringResource(R.string.analyze_search_source_ytmusic)
        YouTubeSearchSource.YOUTUBE -> stringResource(R.string.analyze_search_source_youtube)
    }
    val (backgroundColor, textColor) = when (source) {
        YouTubeSearchSource.YOUTUBE_MUSIC ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) to MaterialTheme.colorScheme.primary
        YouTubeSearchSource.YOUTUBE ->
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f) to
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
    )
}

@Composable
private fun SuggestionThumbnail(url: String, contentDescription: String) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val mod = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(Radius.sm))
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

