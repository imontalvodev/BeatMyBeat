package com.imontalvodev.beatmybeat.ui.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.LocalSnackbarHostState
import com.imontalvodev.beatmybeat.ui.feature.update.ReleaseUpdateDialog
import com.imontalvodev.beatmybeat.core.VersionCompare
import com.imontalvodev.beatmybeat.ui.network.GitHubReleaseInfo
import com.imontalvodev.beatmybeat.ui.network.ReleaseUpdateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.AppLogo
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.feature.player.formatBytes
import com.imontalvodev.beatmybeat.ui.feature.player.KaraokeRecordings
import android.widget.Toast
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ProfileScreen(
    onChangeLanguage: (languageTag: String) -> Unit = {},
    onCustomizeBackground: () -> Unit = {},
    onCustomizeText: () -> Unit = {},
    storageLocationLabel: String = "Music/BeatMyBeat/",
    onPickStorageLocation: () -> Unit = {},
    onOpenStorageFolder: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var pendingUpdate by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    val palette = currentBeatMyBeatThemeProfile()
    val bgBrush = Brush.verticalGradient(
        colors = listOf(palette.backgroundTop, palette.backgroundBottom),
    )

    var showLanguageDialog by remember { mutableStateOf(false) }
    val languageOptions = remember {
        listOf(
            "es" to "Español",
            "en" to "English",
            "pt" to "Português",
            "de" to "Deutsch",
            "hr" to "Hrvatski",
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush),
    ) {
        val layout = profileLayoutFor(maxHeight, maxWidth)
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = layout.horizontalPadding,
                    vertical = layout.verticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            AppMiniBrand(
                modifier = Modifier.align(Alignment.Start),
                logoSize = layout.miniBrandLogoSize,
            )

            Spacer(modifier = Modifier.height(layout.brandToLogoSpacing))

            AppLogo(
                size = layout.profileLogoSize,
                innerPaddingFraction = 0.06f,
            )

            Spacer(modifier = Modifier.height(layout.headerToListSpacing))

            ProfileOption(
                label = stringResource(R.string.profile_change_language),
                icon = Icons.Filled.Language,
                onClick = { showLanguageDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_song_location),
                subtitle = storageLocationLabel,
                icon = Icons.Filled.Folder,
                onClick = onPickStorageLocation,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_open_song_folder),
                subtitle = stringResource(R.string.profile_open_file_explorer),
                icon = Icons.Filled.FolderOpen,
                onClick = onOpenStorageFolder,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            // Grabaciones de karaoke (Fase F): el usuario debe poder ver cuanto ocupan y
            // borrarlas sin salir de la app. Sin esto, el unico modo de recuperar el espacio
            // seria desinstalar.
            val context = LocalContext.current
            var recordingsBytes by remember { mutableStateOf(0L) }
            var deleteRecordingsOpen by remember { mutableStateOf(false) }
            val noRecordingsText = stringResource(R.string.profile_karaoke_none)
            val deletedText = stringResource(R.string.profile_karaoke_deleted)

            LaunchedEffect(Unit) {
                recordingsBytes = withContext(Dispatchers.IO) { KaraokeRecordings.totalBytes(context) }
            }

            ProfileOption(
                label = stringResource(R.string.profile_karaoke_recordings),
                subtitle = if (recordingsBytes > 0L) {
                    stringResource(R.string.profile_karaoke_recordings_size, formatBytes(recordingsBytes))
                } else {
                    noRecordingsText
                },
                icon = Icons.Filled.Mic,
                onClick = { if (recordingsBytes > 0L) deleteRecordingsOpen = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            if (deleteRecordingsOpen) {
                val scope = rememberCoroutineScope()
                AlertDialog(
                    onDismissRequest = { deleteRecordingsOpen = false },
                    title = { Text(stringResource(R.string.profile_karaoke_delete_title)) },
                    text = { Text(stringResource(R.string.profile_karaoke_delete_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteRecordingsOpen = false
                            scope.launch {
                                withContext(Dispatchers.IO) { KaraokeRecordings.deleteAllSaved(context) }
                                recordingsBytes = withContext(Dispatchers.IO) {
                                    KaraokeRecordings.totalBytes(context)
                                }
                                Toast.makeText(context, deletedText, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(stringResource(R.string.profile_karaoke_delete_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteRecordingsOpen = false }) {
                            Text(stringResource(R.string.karaoke_cancel))
                        }
                    },
                )
            }

            ProfileOption(
                label = stringResource(R.string.profile_customize_background),
                icon = Icons.Filled.Palette,
                onClick = onCustomizeBackground,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_customize_text),
                icon = Icons.Filled.TextFields,
                onClick = onCustomizeText,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_check_updates),
                subtitle = if (checkingUpdates) {
                    stringResource(R.string.profile_check_updates_running)
                } else {
                    stringResource(R.string.profile_check_updates_hint)
                },
                icon = Icons.Filled.SystemUpdate,
                onClick = {
                    if (checkingUpdates) return@ProfileOption
                    checkingUpdates = true
                    scope.launch {
                        val release = withContext(Dispatchers.IO) {
                            ReleaseUpdateClient.fetchLatestRelease()
                        }
                        checkingUpdates = false
                        when {
                            release == null -> snackbarHostState.showSnackbar(
                                context.getString(R.string.update_check_failed),
                            )
                            VersionCompare.isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                                pendingUpdate = release
                            }
                            else -> snackbarHostState.showSnackbar(
                                context.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                            )
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_about),
                subtitle = stringResource(R.string.profile_about_version, BuildConfig.VERSION_NAME),
                icon = Icons.Filled.Info,
                onClick = { },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_source_code),
                icon = Icons.Filled.Code,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/imontalvodev/BeatMyBeat")),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_privacy_policy),
                icon = Icons.Filled.PrivacyTip,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md"),
                        ),
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ProfileOption(
                label = stringResource(R.string.profile_license),
                subtitle = stringResource(R.string.profile_responsible_use),
                icon = Icons.Filled.Gavel,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/imontalvodev/BeatMyBeat/blob/main/LICENSE"),
                        ),
                    )
                },
            )

            Spacer(modifier = Modifier.height(layout.bottomScrollPadding))
        }
    }

    pendingUpdate?.let { release ->
        ReleaseUpdateDialog(
            release = release,
            onDismiss = { pendingUpdate = null },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.profile_select_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languageOptions.forEach { (tag, name) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onChangeLanguage(tag)
                                showLanguageDialog = false
                            },
                        ) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private data class ProfileLayout(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val miniBrandLogoSize: Dp,
    val profileLogoSize: Dp,
    val brandToLogoSpacing: Dp,
    val headerToListSpacing: Dp,
    val bottomScrollPadding: Dp,
)

private fun profileLayoutFor(maxHeight: Dp, maxWidth: Dp): ProfileLayout {
    val compactHeight = maxHeight < 640.dp
    val veryCompactHeight = maxHeight < 520.dp
    val narrowWidth = maxWidth < 360.dp

    return ProfileLayout(
        horizontalPadding = when {
            narrowWidth -> 16.dp
            compactHeight -> 24.dp
            else -> 32.dp
        },
        verticalPadding = when {
            veryCompactHeight -> 12.dp
            compactHeight -> 20.dp
            else -> 48.dp
        },
        miniBrandLogoSize = when {
            veryCompactHeight -> 40.dp
            compactHeight -> 44.dp
            else -> 52.dp
        },
        profileLogoSize = when {
            veryCompactHeight -> 88.dp
            compactHeight -> 112.dp
            else -> 156.dp
        },
        brandToLogoSpacing = if (veryCompactHeight) 8.dp else if (compactHeight) 12.dp else 20.dp,
        headerToListSpacing = when {
            veryCompactHeight -> 24.dp
            compactHeight -> 36.dp
            else -> 60.dp
        },
        bottomScrollPadding = 16.dp,
    )
}

@Composable
private fun ProfileOption(
    label: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = AppText.trackTitle,
            )
        },
        supportingContent = subtitle?.let { sub ->
            {
                Text(
                    text = sub,
                    style = AppText.trackArtist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
