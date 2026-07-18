package com.imontalvodev.beatmybeat.ui.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.imontalvodev.beatmybeat.download.UpdateDownloadProgressBus
import com.imontalvodev.beatmybeat.download.UpdateDownloadState
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

/**
 * Seguimiento gráfico de la descarga de la actualización + confirmación antes de instalar.
 * Observa [UpdateDownloadProgressBus], alimentado por el polling de [ApkUpdateDownloader] y por
 * el resultado final de [ApkUpdateInstaller]. Móntalo una vez, a nivel de app (ver [MainActivity]).
 */
@Composable
fun UpdateDownloadStatusPrompt() {
    val context = LocalContext.current
    val state by UpdateDownloadProgressBus.state.collectAsState()

    when (val current = state) {
        is UpdateDownloadState.Downloading -> {
            val fraction = if (current.bytesTotal > 0) {
                (current.bytesDownloaded.toFloat() / current.bytesTotal.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { ApkUpdateDownloader.cancelDownload(context) }) {
                        Text(stringResource(R.string.download_cancel))
                    }
                },
            )
        }
        is UpdateDownloadState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { ApkUpdateInstaller.dismissReadyToInstall() },
                title = { Text(stringResource(R.string.update_ready_title)) },
                text = { Text(stringResource(R.string.update_ready_message, current.version)) },
                confirmButton = {
                    TextButton(onClick = { ApkUpdateInstaller.installNow(context, current.apkUri) }) {
                        Text(stringResource(R.string.update_install_apk))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ApkUpdateInstaller.dismissReadyToInstall() }) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }
        is UpdateDownloadState.Failed -> {
            // El toast ya lo muestra ApkUpdateInstaller; aquí solo limpiamos el diálogo de progreso.
            LaunchedEffect(current) { UpdateDownloadProgressBus.clear() }
        }
        null -> Unit
    }
}
