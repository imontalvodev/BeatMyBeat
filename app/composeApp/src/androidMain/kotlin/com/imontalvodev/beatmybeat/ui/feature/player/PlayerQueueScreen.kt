package com.imontalvodev.beatmybeat.ui.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.SectionLabel
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Cola de reproducción a pantalla completa. Tocar una fila la reproduce; el asa reordena
 * (solo fuera de aleatorio, donde el orden visible es el orden real de la cola).
 */
@Composable
internal fun PlayerQueueScreen(
    currentTrack: DeviceTrack?,
    upNext: List<DeviceTrack>,
    totalCount: Int,
    shuffleOn: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onPlayItem: (index: Int, track: DeviceTrack) -> Unit,
    onRemoveItem: (index: Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onMoveFinished: () -> Unit,
) {
    val canReorder = !shuffleOn
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // Claves estables aunque la misma canción esté dos veces en la cola: uri + número de aparición.
    val keys = buildList {
        val seen = HashMap<String, Int>()
        upNext.forEach { t ->
            val n = seen.getOrDefault(t.uri, 0)
            seen[t.uri] = n + 1
            add("${t.uri}#$n")
        }
    }
    val currentKeys by rememberUpdatedState(keys)
    // La lista lleva cabeceras delante, así que se traduce por clave y no por índice de la lista.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = currentKeys.indexOf(from.key)
        val toIdx = currentKeys.indexOf(to.key)
        if (fromIdx >= 0 && toIdx >= 0) {
            onMoveItem(fromIdx, toIdx)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_queue_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.library_track_count, totalCount, totalCount),
                    style = AppText.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (upNext.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.player_clear_queue))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (currentTrack != null) {
                item(key = "now_playing_label") {
                    SectionLabel(
                        text = stringResource(R.string.player_now_playing),
                        modifier = Modifier.padding(top = 0.dp),
                    )
                }
                item(key = "now_playing") {
                    QueueRow(
                        track = currentTrack,
                        highlighted = true,
                        onClick = null,
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = Spacing.md),
                            )
                        },
                    )
                }
            }

            if (upNext.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.player_queue_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                item(key = "up_next_label") {
                    SectionLabel(
                        text = stringResource(R.string.player_queue_up_next, upNext.size) +
                            if (shuffleOn) " · ${stringResource(R.string.player_shuffle_suffix)}" else "",
                    )
                }
            }

            itemsIndexed(items = upNext, key = { idx, _ -> keys[idx] }) { idx, track ->
                ReorderableItem(state = reorderState, key = keys[idx], enabled = canReorder) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "queue_drag")
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                        shadowElevation = elevation,
                    ) {
                        QueueRow(
                            track = track,
                            highlighted = false,
                            onClick = { onPlayItem(idx, track) },
                            trailing = {
                                if (!shuffleOn) {
                                    IconButton(onClick = { onRemoveItem(idx) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.player_remove_from_queue_cd),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                if (canReorder) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = onMoveFinished,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DragHandle,
                                            contentDescription = stringResource(R.string.player_reorder_cd),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: DeviceTrack,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ArtworkThumbnail(track = track, sizeDp = 48)
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.toTitleCaseSimple(),
                style = AppText.trackTitle,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.toDisplayArtist(),
                style = AppText.trackArtist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
