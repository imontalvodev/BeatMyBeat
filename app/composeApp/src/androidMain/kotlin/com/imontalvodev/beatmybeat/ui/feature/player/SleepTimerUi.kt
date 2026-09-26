package com.imontalvodev.beatmybeat.ui.feature.player

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.playback.LocalPlaybackService
import com.imontalvodev.beatmybeat.service.PlaybackService
import com.imontalvodev.beatmybeat.shared.playback.SleepTimer
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Tiempo restante del temporizador, recalculado cada segundo; `null` si no hay cuenta atrás. */
@Composable
internal fun rememberSleepTimerRemainingMs(timer: SleepTimer?): Long? {
    val atTime = timer as? SleepTimer.AtTime ?: return null
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(atTime) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return atTime.remainingMs(now)
}

/**
 * Botón de la luna en la barra del reproductor. Resaltado mientras hay un temporizador; al
 * pulsarlo se elige duración, "al acabar la canción" o se desactiva.
 */
@Composable
internal fun SleepTimerButton() {
    val service = LocalPlaybackService.current
    val timer by PlaybackService.sleepTimer.collectAsState()
    var dialogOpen by remember { mutableStateOf(false) }
    val active = timer != null

    IconButton(onClick = { dialogOpen = true }, enabled = service != null) {
        Icon(
            imageVector = if (active) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
            contentDescription = stringResource(R.string.sleep_timer_title),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }

    if (dialogOpen && service != null) {
        SleepTimerDialog(
            current = timer,
            onSelectMinutes = { service.setSleepTimer(it); dialogOpen = false },
            onSelectEndOfTrack = { service.setSleepTimerEndOfTrack(); dialogOpen = false },
            onCancelTimer = { service.cancelSleepTimer(); dialogOpen = false },
            onDismiss = { dialogOpen = false },
        )
    }
}

/** Línea bajo el título del reproductor: "Se detendrá en 12:34" / "al acabar esta canción". */
@Composable
internal fun SleepTimerStatus(modifier: Modifier = Modifier) {
    val timer by PlaybackService.sleepTimer.collectAsState()
    val remaining = rememberSleepTimerRemainingMs(timer)
    val text = when {
        timer == SleepTimer.EndOfTrack -> stringResource(R.string.sleep_timer_status_end_of_track)
        remaining != null -> stringResource(R.string.sleep_timer_status_remaining, SleepTimer.formatRemaining(remaining))
        else -> return
    }
    Text(
        text = text,
        style = AppText.meta,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun SleepTimerDialog(
    current: SleepTimer?,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Bedtime, contentDescription = null) },
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text = {
            Column {
                SleepTimer.PRESET_MINUTES.forEach { minutes ->
                    SleepTimerOption(
                        label = pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes),
                        selected = false,
                        onClick = { onSelectMinutes(minutes) },
                    )
                }
                SleepTimerOption(
                    label = stringResource(R.string.sleep_timer_end_of_track),
                    selected = current == SleepTimer.EndOfTrack,
                    onClick = onSelectEndOfTrack,
                )
            }
        },
        confirmButton = {
            if (current != null) {
                TextButton(onClick = onCancelTimer) {
                    Text(stringResource(R.string.sleep_timer_turn_off))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun SleepTimerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(40.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
    }
}
