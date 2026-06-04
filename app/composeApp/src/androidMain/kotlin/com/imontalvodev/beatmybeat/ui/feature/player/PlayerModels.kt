package com.imontalvodev.beatmybeat.ui.feature.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import android.provider.MediaStore
import androidx.media3.common.Player
import java.io.File
import java.net.URLDecoder
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.LyricsFetcher
import com.imontalvodev.beatmybeat.ui.network.LrcLine
import com.imontalvodev.beatmybeat.ui.network.LrcParser
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.TrackListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.playback.LocalPlaybackService
import com.imontalvodev.beatmybeat.service.PlaybackArtworkHelper
import com.imontalvodev.beatmybeat.service.PlaybackService
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

internal const val LIST_ARTWORK_MAX_PX = 160
internal const val PLAYER_ARTWORK_MAX_PX = 512

internal enum class RepeatMode { OFF, LIST, ONE }

internal enum class PlayerSection { Songs, Favorites, Playlist }

internal enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST,
}

internal fun resolveTrackFromPlaybackMediaId(
    mediaId: String,
    tracks: List<DeviceTrack>,
): DeviceTrack? {
    if (mediaId.isBlank()) return null
    return tracks.firstOrNull { it.uri == mediaId }
        ?: mediaId.toLongOrNull()?.let { id -> tracks.firstOrNull { it.id == id } }
}

internal fun buildVisibleTracksForSection(
    deviceTracks: List<DeviceTrack>,
    favoriteIds: Set<Long>,
    playlists: List<PlayerViewModel.PlaylistEntity>,
    selectedPlaylistId: Long?,
    query: String,
    section: PlayerSection,
    sortOption: SortOption,
): List<DeviceTrack> {
    val trackById = deviceTracks.associateBy { it.id }
    val base = when (section) {
        PlayerSection.Songs -> deviceTracks
        PlayerSection.Favorites -> deviceTracks.filter { favoriteIds.contains(it.id) }
        PlayerSection.Playlist -> {
            val playlist = playlists.firstOrNull { it.id == selectedPlaylistId }
            if (playlist == null) emptyList()
            else playlist.songIds.mapNotNull { trackById[it] }
        }
    }
    val filtered = if (query.isBlank()) {
        base
    } else {
        val q = query.trim().lowercase()
        base.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                (it.album ?: "").lowercase().contains(q)
        }
    }
    return when (sortOption) {
        SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        SortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.dateAddedMs }
        SortOption.OLDEST_FIRST -> filtered.sortedBy { it.dateAddedMs }
    }
}

internal sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(
        val lyrics: String,
        /** LRC guardado para sincronización futura con la posición de reproducción. */
        val syncedLrc: String? = null,
    ) : LyricsUiState
    data class Empty(val message: String) : LyricsUiState
}

internal data class TrackLyricsMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/**
 * Metadatos para búsqueda de letras (LRCLIB usa álbum y duración).
 */
internal fun resolveTrackMetadata(track: com.imontalvodev.beatmybeat.ui.data.DeviceTrack): TrackLyricsMetadata {
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
        val album = json.optString("album").takeIf { it.isNotBlank() }
            ?: track.album.orEmpty()
        val durationMs = json.optString("durationMs").toLongOrNull()
            ?: json.optLong("durationMs", 0L).takeIf { it > 0L }
            ?: track.durationMs
        TrackLyricsMetadata(
            title = t,
            artist = com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics(rawArtist),
            album = album,
            durationMs = durationMs,
        )
    }.getOrNull()?.let { return it }
    return TrackLyricsMetadata(
        title = track.title,
        artist = com.imontalvodev.beatmybeat.ui.network.cleanArtistForLyrics(track.artist),
        album = track.album.orEmpty(),
        durationMs = track.durationMs,
    )
}

/** Devuelve (title, artist) para compatibilidad con llamadas existentes. */
internal fun resolveTrackMeta(track: com.imontalvodev.beatmybeat.ui.data.DeviceTrack): Pair<String, String> {
    val m = resolveTrackMetadata(track)
    return Pair(m.title, m.artist)
}

internal fun String.toTitleCaseSimple(): String {
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

internal fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

