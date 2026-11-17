package com.imontalvodev.beatmybeat.ui.feature.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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
import com.imontalvodev.beatmybeat.shared.lyrics.LrcLine
import com.imontalvodev.beatmybeat.shared.lyrics.LrcParser
import com.imontalvodev.beatmybeat.ui.network.ArtworkCache
import com.imontalvodev.beatmybeat.ui.network.BitmapDecoding
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.CharcaColors
import com.imontalvodev.beatmybeat.ui.theme.EqualizerBars
import com.imontalvodev.beatmybeat.ui.theme.charcaChipBorder
import com.imontalvodev.beatmybeat.ui.theme.charcaChipColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.BorderStroke
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.TrackListSkeleton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
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
    isPlaying: Boolean = false,
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
            if (isFavorite && !selectionMode) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.player_action_favorite_remove),
                    tint = CharcaColors.amber,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Sonando: barras animadas; el resto muestra la duración.
            if (!selectionMode) {
                if (isCurrent && isPlaying) {
                    EqualizerBars(modifier = Modifier.padding(horizontal = Spacing.sm))
                } else if (track.durationMs > 0) {
                    Text(
                        text = formatMs(track.durationMs.toInt()),
                        style = AppText.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
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
private fun MenuIcon(imageVector: ImageVector, destructive: Boolean = false) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DestructiveMenuText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error)
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
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(Radius.sm),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_queue)
                        else stringResource(R.string.player_action_queue),
                    )
                },
                leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.PlaylistAdd) },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_play_next)
                        else stringResource(R.string.player_action_play_next),
                    )
                },
                leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.PlaylistPlay) },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_favorite)
                        else stringResource(R.string.player_action_favorite),
                    )
                },
                leadingIcon = { MenuIcon(Icons.Outlined.FavoriteBorder) },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (multi) stringResource(R.string.player_bulk_action_playlist)
                        else stringResource(R.string.player_action_playlist),
                    )
                },
                leadingIcon = { MenuIcon(Icons.Outlined.LibraryAdd) },
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
                    leadingIcon = { MenuIcon(Icons.Outlined.RemoveCircleOutline) },
                    onClick = { expanded = false; onRemoveFromPlaylist() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    DestructiveMenuText(
                        if (multi) stringResource(R.string.player_bulk_action_delete)
                        else stringResource(R.string.player_action_delete),
                    )
                },
                leadingIcon = { MenuIcon(Icons.Outlined.Delete, destructive = true) },
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(Radius.sm),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_play_next)) },
                leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.PlaylistPlay) },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_queue)) },
                leadingIcon = { MenuIcon(Icons.AutoMirrored.Filled.PlaylistAdd) },
                onClick = { expanded = false; onQueue() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isFavorite) R.string.player_action_favorite_remove
                            else R.string.player_action_favorite_add,
                        ),
                    )
                },
                leadingIcon = {
                    MenuIcon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder)
                },
                onClick = { expanded = false; onToggleFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_action_playlist)) },
                leadingIcon = { MenuIcon(Icons.Outlined.LibraryAdd) },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            if (showRemoveFromPlaylist) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_action_remove_playlist)) },
                    leadingIcon = { MenuIcon(Icons.Outlined.RemoveCircleOutline) },
                    onClick = { expanded = false; onRemoveFromPlaylist() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { DestructiveMenuText(stringResource(R.string.player_action_delete)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Delete, destructive = true) },
                onClick = { expanded = false; onDeleteFromDevice() },
            )
        }
    }
}

@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(R.string.player_search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.player_search_clear_cd),
                    )
                }
            }
        },
        shape = RoundedCornerShape(Radius.pill),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = scheme.onSurface,
            unfocusedLeadingIconColor = scheme.onSurfaceVariant,
            focusedPlaceholderColor = scheme.onSurfaceVariant,
            unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
internal fun LibrarySectionChips(
    selectedSection: PlayerSection,
    onSelectSection: (PlayerSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        PlayerSection.Songs to R.string.player_section_songs,
        PlayerSection.Favorites to R.string.player_section_favorites,
        PlayerSection.Playlist to R.string.player_section_playlist,
    )
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        sections.forEach { (section, labelRes) ->
            FilterChip(
                selected = section == selectedSection,
                onClick = { onSelectSection(section) },
                label = { Text(stringResource(labelRes)) },
                shape = RoundedCornerShape(Radius.pill),
                colors = charcaChipColors(),
                border = charcaChipBorder(selected = section == selectedSection),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortMenu(
    selectedSort: SortOption,
    onSelectSort: (SortOption) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val options = listOf(
        SortOption.NAME_ASC to R.string.player_sort_name_asc,
        SortOption.NAME_DESC to R.string.player_sort_name_desc,
        SortOption.NEWEST_FIRST to R.string.player_sort_recent,
        SortOption.OLDEST_FIRST to R.string.player_sort_oldest,
    )
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.player_sort_cd),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (open) {
        // Hoja inferior en vez de menú desplegable: el menú se abría encima del buscador y de los
        // chips, con el estilo por defecto de M3 y sin título. Aquí cada opción es una fila grande.
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.player_sort_cd),
                    style = AppText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                options.forEach { (option, labelRes) ->
                    val selected = option == selectedSort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else Color.Transparent,
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelectSort(option)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { open = false }
                                },
                            )
                            .padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayShuffleRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(Radius.pill),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(stringResource(R.string.player_play), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(Radius.pill),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(stringResource(R.string.player_shuffle_play), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Barra contextual de la selección múltiple: sustituye a Reproducir/Aleatorio mientras dura. */
@Composable
internal fun SelectionActionBar(
    selectedCount: Int,
    canSelectAll: Boolean,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.player_selection_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll, enabled = canSelectAll) {
            Text(stringResource(R.string.player_select_all))
        }
        actions()
    }
}

@Composable
internal fun LibraryListEmptyHint(
    section: PlayerSection,
    query: String,
    modifier: Modifier = Modifier,
) {
    val (icon, title, body) = when {
        query.isNotBlank() -> Triple(
            Icons.Filled.Search,
            stringResource(R.string.player_search_no_results, query.trim()),
            null,
        )
        section == PlayerSection.Favorites -> Triple(
            Icons.Outlined.FavoriteBorder,
            stringResource(R.string.player_favorites_empty),
            stringResource(R.string.player_favorites_empty_body),
        )
        else -> Triple(
            Icons.AutoMirrored.Filled.QueueMusic,
            stringResource(R.string.player_playlist_empty),
            stringResource(R.string.player_playlist_empty_body),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (section == PlayerSection.Favorites && query.isBlank()) CharcaColors.amber
        else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .border(1.5.dp, tint.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = tint,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlaylistOptionsMenu(
    playlistId: Long,
    hasCover: Boolean,
    onRequestRename: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestChangeCover: (Long) -> Unit,
    onRequestRemoveCover: (Long) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.player_playlist_options_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(Radius.sm),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_edit_playlist_menu)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Edit) },
                onClick = {
                    menuExpanded = false
                    onRequestRename(playlistId)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_playlist_change_photo)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Image) },
                onClick = {
                    menuExpanded = false
                    onRequestChangeCover(playlistId)
                },
            )
            if (hasCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_playlist_remove_photo)) },
                    leadingIcon = { MenuIcon(Icons.Outlined.HideImage) },
                    onClick = {
                        menuExpanded = false
                        onRequestRemoveCover(playlistId)
                    },
                )
            }
            DropdownMenuItem(
                text = { DestructiveMenuText(stringResource(R.string.player_delete_playlist_menu)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Delete, destructive = true) },
                onClick = {
                    menuExpanded = false
                    onRequestDelete(playlistId)
                },
            )
        }
    }
}

@Composable
private fun PlaylistCover(size: Dp, coverPath: String? = null) {
    val photo = remember(coverPath) { PlaylistCovers.file(coverPath) }
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(Radius.sm)),
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    val amber = CharcaColors.amber
    // Portada «Charca»: degradado del tema con anillos de charca en la esquina y el icono encima.
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                Brush.linearGradient(
                    listOf(scheme.tertiary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.85f), amber.copy(alpha = 0.9f)),
                ),
            )
            .drawBehind {
                val stroke = Stroke(width = (size.toPx() * 0.035f).coerceAtLeast(1.5f))
                val ringColor = Color.White
                for ((i, f) in listOf(0.95f, 0.65f, 0.38f).withIndex()) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.34f - i * 0.07f),
                        radius = this.size.minDimension * f / 2f,
                        center = Offset(this.size.width * 0.12f, this.size.height * 0.95f),
                        style = stroke,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
internal fun PlaylistDetailHeader(
    playlistName: String,
    trackCount: Int,
    playlistId: Long,
    coverPath: String?,
    onBack: () -> Unit,
    onRequestRename: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestChangeCover: (Long) -> Unit,
    onRequestRemoveCover: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        // Tocar la portada abre el selector de foto.
        Box(modifier = Modifier.clip(RoundedCornerShape(Radius.sm)).clickable { onRequestChangeCover(playlistId) }) {
            PlaylistCover(size = 56.dp, coverPath = coverPath)
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.library_track_count, trackCount, trackCount),
                style = AppText.trackArtist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlaylistOptionsMenu(
            playlistId = playlistId,
            hasCover = coverPath != null,
            onRequestRename = onRequestRename,
            onRequestDelete = onRequestDelete,
            onRequestChangeCover = onRequestChangeCover,
            onRequestRemoveCover = onRequestRemoveCover,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistPickerBar(
    playlists: List<PlayerViewModel.PlaylistEntity>,
    selectedPlaylistId: Long?,
    onSelect: (Long) -> Unit,
    onCreateEmpty: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestRename: (Long) -> Unit,
    onRequestChangeCover: (Long) -> Unit,
    onRequestRemoveCover: (Long) -> Unit,
    onMovePlaylist: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaylistCover(size = 72.dp)
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.player_playlists_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.player_playlists_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = onCreateEmpty,
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(Radius.pill),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(R.string.player_playlists_create_first))
            }
        }
        return
    }

    val haptics = LocalHapticFeedback.current
    val reorderCd = stringResource(R.string.player_playlist_reorder_cd)
    val listState = rememberLazyListState()
    val currentIds by rememberUpdatedState(playlists.map { it.id })
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = currentIds.indexOf(from.key)
        val toIdx = currentIds.indexOf(to.key)
        if (fromIdx >= 0 && toIdx >= 0) {
            onMovePlaylist(fromIdx, toIdx)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.player_playlists_header),
                style = AppText.sectionHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(
                onClick = onCreateEmpty,
                shape = RoundedCornerShape(Radius.pill),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.player_create_playlist_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(playlists, key = { it.id }) { p ->
                ReorderableItem(reorderState, key = p.id) { isDragging ->
                    val selected = p.id == selectedPlaylistId
                    val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "playlist_drag")
                    val dragStarted = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    val rowShape = RoundedCornerShape(Radius.md)
                    Surface(
                        shape = rowShape,
                        color = when {
                            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = if (isDragging) 1.5.dp else 1.dp,
                            color = when {
                                isDragging -> MaterialTheme.colorScheme.primary
                                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        shadowElevation = elevation,
                        // Pulsación larga en cualquier punto de la fila, además del asa.
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { dragStarted() },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onSelect(p.id) }
                                .padding(end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 48.dp)
                                    .draggableHandle(onDragStarted = { dragStarted() })
                                    .semantics { contentDescription = reorderCd },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PlaylistCover(size = 48.dp, coverPath = p.coverPath)
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    style = AppText.trackTitle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.library_track_count, p.songIds.size, p.songIds.size),
                                    style = AppText.trackArtist,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PlaylistOptionsMenu(
                                playlistId = p.id,
                                hasCover = p.coverPath != null,
                                onRequestRename = onRequestRename,
                                onRequestDelete = onRequestDelete,
                                onRequestChangeCover = onRequestChangeCover,
                                onRequestRemoveCover = onRequestRemoveCover,
                            )
                        }
                    }
                }
            }
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



/** Cabecera de Favoritos: corazón ámbar y el número de canciones (el ámbar marca favoritos). */
@Composable
internal fun FavoritesBanner(count: Int, modifier: Modifier = Modifier) {
    val amber = CharcaColors.amber
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(amber.copy(alpha = 0.12f))
            .border(1.dp, amber.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = amber,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            Text(
                text = stringResource(R.string.player_section_favorites),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.library_track_count, count, count),
                style = AppText.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
