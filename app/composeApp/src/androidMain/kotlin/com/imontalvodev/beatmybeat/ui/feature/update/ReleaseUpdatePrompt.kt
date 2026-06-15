package com.imontalvodev.beatmybeat.ui.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.network.GitHubReleaseInfo
import com.imontalvodev.beatmybeat.ui.network.UpdateChecker
import com.imontalvodev.beatmybeat.ui.storage.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReleaseUpdatePrompt() {
    val context = LocalContext.current
    var pendingRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val release = withContext(Dispatchers.IO) {
            UpdateChecker.checkForUpdate(context.applicationContext)
        }
        if (release != null) {
            pendingRelease = release
        }
    }

    pendingRelease?.let { release ->
        ReleaseUpdateDialog(
            release = release,
            onDismiss = {
                UpdatePrefs.setDismissedVersion(context.applicationContext, release.version)
                pendingRelease = null
            },
        )
    }
}

@Composable
fun ReleaseUpdateDialog(
    release: GitHubReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.update_available_title,
                    release.version,
                ),
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.update_available_message,
                        BuildConfig.VERSION_NAME,
                        release.version,
                    ),
                )
                if (release.releaseNotesExcerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = release.releaseNotesExcerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ApkUpdateDownloader.startDownload(context, release)
                    UpdatePrefs.setDismissedVersion(context.applicationContext, release.version)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.update_download_apk))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}
