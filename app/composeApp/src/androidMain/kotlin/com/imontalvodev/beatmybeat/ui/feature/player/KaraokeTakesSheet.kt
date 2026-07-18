package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tomas guardadas de una canción: escuchar y borrar.
 *
 * Sin esto el índice servía de poco — se sabía cuántas tomas había, pero solo se podían oír desde
 * el explorador de archivos.
 *
 * Aquí la toma suena **sola**, no sobre la canción: al revisar recién grabada tiene sentido oírla
 * con la pista para juzgar el resultado, pero al volver días después lo que se quiere es escuchar
 * lo que uno cantó.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KaraokeTakesSheet(
    trackId: Long,
    trackTitle: String,
    onDismiss: () -> Unit,
    onTakesChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    var takes by remember { mutableStateOf<List<TakeRow>>(emptyList()) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    fun stopPlayback() {
        player?.let { active ->
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        player = null
        playingName = null
    }

    LaunchedEffect(trackId, reloadToken) {
        takes = withContext(Dispatchers.IO) { loadTakes(context, trackId) }
        onTakesChanged(takes.size)
    }

    // Salir del sheet con una toma sonando dejaria el MediaPlayer vivo.
    DisposableEffect(Unit) {
        onDispose {
            player?.let { active ->
                runCatching { active.stop() }
                runCatching { active.release() }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            stopPlayback()
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
        ) {
            Text(
                text = stringResource(R.string.karaoke_takes_title),
                style = AppText.sectionHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = trackTitle,
                style = AppText.trackArtist,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(takes, key = { it.saved.displayName }) { row ->
                    val isPlaying = playingName == row.saved.displayName
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    stopPlayback()
                                } else {
                                    stopPlayback()
                                    player = startTake(context, row.saved.uri) { stopPlayback() }
                                    if (player != null) playingName = row.saved.displayName
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.karaoke_take_stop
                                    else R.string.karaoke_take_play,
                                ),
                                tint = if (isPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.label,
                                style = AppText.trackTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatBytes(row.saved.sizeBytes),
                                style = AppText.meta,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isPlaying) stopPlayback()
                                val ok = KaraokeRecordings.deleteSaved(context, row.saved)
                                if (ok) reloadToken++
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.karaoke_take_delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una toma con la etiqueta ya resuelta para pintarla. */
internal data class TakeRow(
    val saved: KaraokeRecordings.Saved,
    val label: String,
)

/**
 * Cruza el índice (qué tomas son de esta canción) con los archivos que existen de verdad. Una
 * entrada sin archivo no se muestra: `KaraokeRecordingIndex.forTrack` ya la habrá descartado.
 */
private fun loadTakes(context: Context, trackId: Long): List<TakeRow> {
    val entries = KaraokeRecordingIndex.forTrack(context, trackId)
    val savedByName = KaraokeRecordings.listSaved(context).associateBy { it.displayName }
    return entries.mapNotNull { entry ->
        val saved = savedByName[entry.fileName] ?: return@mapNotNull null
        TakeRow(saved = saved, label = takeLabel(entry.fileName))
    }
}

/**
 * Etiqueta legible a partir del nombre `REC-AAAA-MM-DD-HH-MM-SS.m4a` → `18/07/2026 11:09`.
 * Si el nombre no sigue el formato se usa tal cual, que es mejor que no mostrar nada.
 */
internal fun takeLabel(fileName: String): String {
    val stamp = fileName.removePrefix(KaraokeRecordings.PREFIX).substringBeforeLast('.')
    val parts = stamp.split('-')
    if (parts.size != 6) return fileName
    val (year, month, day, hour, minute, _) = parts
    return "$day/$month/$year $hour:$minute"
}

private operator fun <T> List<T>.component6(): T = this[5]

private fun startTake(context: Context, uri: Uri, onComplete: () -> Unit): MediaPlayer? =
    runCatching {
        MediaPlayer().apply {
            setDataSource(context, uri)
            setOnCompletionListener { onComplete() }
            prepare()
            start()
        }
    }.onFailure { Logger.e("KaraokeTakes", "No se pudo reproducir la toma", it) }
        .getOrNull()
