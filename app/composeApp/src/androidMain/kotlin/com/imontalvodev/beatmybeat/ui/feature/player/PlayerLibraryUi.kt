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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.clip
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
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.Spacing
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackRow(
    track: DeviceTrack,
    isCurrent: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    showOverflowMenu: Boolean,
    showSelectedActionsMenu: Boolean,
    onEnterSelectionMode: () -> Unit,
    onToggleSelection: () -> Unit,
    onPlayTrack: () -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onBulkQueue: () -> Unit,
    onBulkPlayNext: () -> Unit,
    onBulkToggleFavorite: () -> Unit,
    onBulkAddToPlaylist: () -> Unit,
    onBulkDeleteFromDevice: () -> Unit,
    showBulkRemoveFromPlaylist: Boolean,
    onBulkRemoveFromPlaylist: () -> Unit,
    bulkSelectionCount: Int,
    isFavorite: Boolean,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
) {
    val selectedCountLabel = stringResource(R.string.player_selection_actions_cd)
    var suppressClickAfterLongPress by remember { mutableStateOf(false) }
    // Las filas ya no son tarjetas. Una lista de tarjetas apiladas es lo que hacía que la
    // biblioteca se viera densa: cada fila añadía un borde y una superficie más. Ahora la fila
    // solo se tiñe cuando significa algo (seleccionada o sonando).
    val rowBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isCurrent && !selectionMode -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(rowBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = {
                            if (suppressClickAfterLongPress) {
                                suppressClickAfterLongPress = false
                                return@combinedClickable
                            }
                            if (selectionMode) {
                                onToggleSelection()
                            } else {
                                onPlayTrack()
                            }
                        },
                        onLongClick = {
                            suppressClickAfterLongPress = true
                            if (!selectionMode) {
                                onEnterSelectionMode()
                            } else {
                                onToggleSelection()
                            }
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 32dp era demasiado pequeña para que la portada se leyera como portada.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    ArtworkThumbnail(track = track, sizeDp = 48)
                    when {
                        isSelected -> {
                            // Velo derivado de la paleta, no un negro fijo: con un perfil de
                            // fondo claro el negro se comía la miniatura.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.player_track_selected_cd),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        selectionMode -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(Radius.sm),
                                    ),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.toTitleCaseSimple(),
                        // Era bodySmall (12sp) — el texto más importante de la lista y el más
                        // pequeño de la pantalla.
                        style = AppText.trackTitle,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.toDisplayArtist(),
                        style = AppText.trackArtist,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showOverflowMenu) {
                TrackOverflowMenu(
                    onQueue = onQueue,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onDeleteFromDevice = onDeleteFromDevice,
                    isFavorite = isFavorite,
                    showRemoveFromPlaylist = showRemoveFromPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                )
            } else if (showSelectedActionsMenu) {
                TrackSelectionOverflowMenu(
                    selectedCount = bulkSelectionCount,
                    contentDescription = selectedCountLabel,
                    onQueue = onBulkQueue,
                    onPlayNext = onBulkPlayNext,
                    onToggleFavorite = onBulkToggleFavorite,
                    onAddToPlaylist = onBulkAddToPlaylist,
                    onDeleteFromDevice = onBulkDeleteFromDevice,
                    showRemoveFromPlaylist = showBulkRemoveFromPlaylist,
                    onRemoveFromPlaylist = onBulkRemoveFromPlaylist,
                )
            }
        }
    }
}

@Composable
internal fun TrackSelectionOverflowMenu(
    selectedCount: Int,
    contentDescription: String,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    showRemoveFromPlaylist: Boolean,
    onRemoveFromPlaylist: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val multi = selectedCount > 1
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (multi) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.player_bulk_actions_header, selectedCount))
                    },
                    onClick = { },
                    enabled = false,
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_queue)
                        else stringResource(R.string.player_action_queue),
                    )
                },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_play_next)
                        else stringResource(R.string.player_action_play_next),
                    )
                },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_favorite)
                        else stringResource(R.string.player_action_favorite),
                    )
                },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_playlist)
                        else stringResource(R.string.player_action_playlist),
                    )
                },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            if (showRemoveFromPlaylist) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (multi) stringResource(R.string.player_bulk_action_remove_playlist)
                            else stringResource(R.string.player_action_remove_playlist),
                        )
                    },
                    onClick = { expanded = false; onRemoveFromPlaylist() },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_delete)
                        else stringResource(R.string.player_action_delete),
                    )
                },
                onClick = { expanded = false; onDeleteFromDevice() },
            )
        }
    }
}

@Composable
internal fun TrackOverflowMenu(
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
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
                    contentDescription = stringResource(R.string.player_cd_more_options),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_queue)) },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_play_next)) },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = {
                    Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
                },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_playlist)) },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_delete)) },
                onClick = {
                    expanded = false
                    onDeleteFromDevice()
                },
            )

            if (showRemoveFromPlaylist) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_action_remove_playlist)) },
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
internal fun LibraryFiltersMenu(
    selectedSection: PlayerSection,
    selectedSort: SortOption,
    onSelectSection: (PlayerSection) -> Unit,
    onSelectSort: (SortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sectionLabel = when (selectedSection) {
        PlayerSection.Songs -> stringResource(R.string.player_section_songs)
        PlayerSection.Favorites -> stringResource(R.string.player_section_favorites)
        PlayerSection.Playlist -> stringResource(R.string.player_section_playlist)
    }
    val sortLabel = when (selectedSort) {
        SortOption.NAME_ASC -> stringResource(R.string.player_sort_name_asc)
        SortOption.NAME_DESC -> stringResource(R.string.player_sort_name_desc)
        SortOption.NEWEST_FIRST -> stringResource(R.string.player_sort_recent)
        SortOption.OLDEST_FIRST -> stringResource(R.string.player_sort_oldest)
    }
    val summary = "$sectionLabel · $sortLabel"
    val filtersMenuA11y = stringResource(R.string.player_filters_cd)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = filtersMenuA11y },
            shape = RoundedCornerShape(Radius.md),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_section_songs)) },
                onClick = {
                    onSelectSection(PlayerSection.Songs)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSection == PlayerSection.Songs) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_section_favorites)) },
                onClick = {
                    onSelectSection(PlayerSection.Favorites)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSection == PlayerSection.Favorites) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_section_playlist)) },
                onClick = {
                    onSelectSection(PlayerSection.Playlist)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSection == PlayerSection.Playlist) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_sort_name_asc)) },
                onClick = {
                    onSelectSort(SortOption.NAME_ASC)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSort == SortOption.NAME_ASC) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_sort_name_desc)) },
                onClick = {
                    onSelectSort(SortOption.NAME_DESC)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSort == SortOption.NAME_DESC) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_sort_recent)) },
                onClick = {
                    onSelectSort(SortOption.NEWEST_FIRST)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSort == SortOption.NEWEST_FIRST) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_sort_oldest)) },
                onClick = {
                    onSelectSort(SortOption.OLDEST_FIRST)
                    expanded = false
                },
                leadingIcon = {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (selectedSort == SortOption.OLDEST_FIRST) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun PlaylistDetailHeader(
    playlistName: String,
    trackCount: Int,
    playlistId: Long,
    onBack: () -> Unit,
    onRequestRename: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$trackCount canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_edit_playlist_menu)) },
                        onClick = {
                            menuExpanded = false
                            onRequestRename(playlistId)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_delete_playlist_menu)) },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete(playlistId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistPickerBar(
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
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Mis playlists",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onCreateEmpty) {
                Text(stringResource(R.string.player_create_playlist_button))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        playlists.forEach { p ->
            val selected = p.id == selectedPlaylistId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(p.id) },
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${p.songIds.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Text(
                                    text = "Activa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
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
                                text = { Text(stringResource(R.string.player_edit_playlist_menu)) },
                                onClick = {
                                    menuExpanded = false
                                    onRequestRename(p.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_delete_playlist_menu)) },
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
internal fun ActionPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(36.dp)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(Radius.pill),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
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
internal fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.pill),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Índice de la primera pista de cada inicial, para el rail A–Z (U3, tomado de Rhythm).
 *
 * Todo lo que no empiece por letra (números, símbolos) cae en `'#'`. Se conserva el orden de
 * aparición: la lista ya viene ordenada, así que el mapa sale ordenado sin volver a ordenar nada.
 *
 * Función pura para poder testearla — el rail en sí es Compose y aquí no hay Robolectric.
 */
internal fun buildAlphabetIndex(titles: List<String>): Map<Char, Int> {
    val index = LinkedHashMap<Char, Int>()
    titles.forEachIndexed { position, title ->
        val first = title.trim().firstOrNull() ?: return@forEachIndexed
        val key = if (first.isLetter()) first.uppercaseChar() else '#'
        if (!index.containsKey(key)) index[key] = position
    }
    return index
}

/**
 * Rail de iniciales a la derecha de la lista. Se puede tocar o arrastrar: al arrastrar, la letra
 * se deduce de la posición vertical del dedo sobre el rail, no de qué letra concreta se toca —
 * si no, con 27 letras en pantalla habría que acertar una diana de pocos dp.
 */
@Composable
internal fun AlphabetFastScroller(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return

    var activeLetter by remember { mutableStateOf<Char?>(null) }
    val currentLetters by rememberUpdatedState(letters)
    val currentOnSelected by rememberUpdatedState(onLetterSelected)

    fun letterAt(y: Float, height: Int): Char? {
        if (height <= 0) return null
        val slot = height.toFloat() / currentLetters.size
        val idx = (y / slot).toInt().coerceIn(0, currentLetters.lastIndex)
        return currentLetters[idx]
    }

    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null },
                ) { change, _ ->
                    letterAt(change.position.y, size.height)?.let { letter ->
                        if (letter != activeLetter) {
                            activeLetter = letter
                            currentOnSelected(letter)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    letterAt(offset.y, size.height)?.let(currentOnSelected)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (letter == activeLetter) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun LibraryEmptyState(
    modifier: Modifier = Modifier,
    onOpenDownloader: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Text(
            text = stringResource(R.string.player_empty_library_title),
            style = AppText.sectionHeader,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.player_empty_library_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        OutlinedButton(onClick = onOpenDownloader) {
            Text(stringResource(R.string.player_empty_library_cta))
        }
    }
}

