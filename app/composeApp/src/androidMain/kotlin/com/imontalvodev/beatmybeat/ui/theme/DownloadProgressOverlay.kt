package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.download.ActiveDownloadUiState
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import androidx.compose.ui.platform.LocalContext

@Composable
fun ActiveDownloadProgressSection(
    download: ActiveDownloadUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showBackgroundHint: Boolean = true,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (download.isBatch) {
            PlaylistDownloadProgressCard(
                done = download.batchDone ?: 0,
                total = download.batchTotal ?: 0,
                failed = download.batchFailed,
                currentTitle = download.title,
                phase = download.phase.ifBlank { stringResource(R.string.download_processing) },
                currentFileFraction = download.fileFraction,
            )
        } else {
            SingleDownloadProgressCard(
                title = download.title,
                artist = download.subtitle,
                phase = download.phase,
                fileFraction = download.fileFraction,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.download_cancel))
        }
        if (showBackgroundHint) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.download_progress_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            if (!BeatMyBeatNotification.canPostNotifications(context)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.download_progress_no_notification_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}
