package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.download.ActiveDownloadUiState

/**
 * Estado de la descarga en curso, visible desde cualquier pestaña encima de la navegación.
 * Antes solo había una tarjeta a mitad de la pantalla Descargar: al pulsar descargar desde los
 * resultados o desde una playlist larga quedaba fuera de la vista, y al cambiar de pestaña
 * desaparecía.
 */
@Composable
fun DownloadStatusBar(
    download: ActiveDownloadUiState,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overall: Float? = if (download.isBatch) {
        val total = download.batchTotal ?: 0
        if (total > 0) {
            val done = (download.batchDone ?: 0) + download.batchFailed
            ((done + (download.fileFraction ?: 0f)) / total).coerceIn(0f, 1f)
        } else {
            null
        }
    } else {
        download.fileFraction
    }
    val animated by animateFloatAsState(
        targetValue = overall ?: 0f,
        animationSpec = tween(Motion.LAYOUT),
        label = "download_status_progress",
    )
    val title = download.title.ifBlank { stringResource(R.string.download_song_title) }
    val detail = buildString {
        if (download.isBatch && (download.batchTotal ?: 0) > 0) {
            append((download.batchDone ?: 0) + download.batchFailed)
            append('/')
            append(download.batchTotal)
            append(" · ")
        }
        append(download.phase.ifBlank { stringResource(R.string.download_processing) })
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.clickable(onClick = onOpen)) {
            Row(
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (overall != null) {
                        CircularProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    }
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppText.trackTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detail,
                        style = AppText.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.download_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (overall != null) {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .height(2.dp),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .height(2.dp),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    gapSize = 0.dp,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
    }
}
