package com.imontalvodev.beatmybeat.ui.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextOverflow
import com.imontalvodev.beatmybeat.ui.theme.BeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.CharcaColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Surface
import com.imontalvodev.beatmybeat.ui.theme.Radius
import com.imontalvodev.beatmybeat.ui.theme.ScreenHeader
import com.imontalvodev.beatmybeat.ui.theme.SectionLabel
import com.imontalvodev.beatmybeat.ui.theme.Spacing
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
import androidx.compose.ui.platform.LocalResources
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
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ProfileScreen(
    onChangeLanguage: (languageTag: String) -> Unit = {},
    onCustomizeBackground: () -> Unit = {},
    onCustomizeText: () -> Unit = {},
    profiles: List<BeatMyBeatThemeProfile> = emptyList(),
    activeProfileId: String = "",
    onSelectProfile: (String) -> Unit = {},
    storageLocationLabel: String = "Music/BeatMyBeat/",
    onPickStorageLocation: () -> Unit = {},
    onOpenStorageFolder: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
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

    val currentLanguageName = remember(languageOptions) {
        val tag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore('-')
            .ifBlank { java.util.Locale.getDefault().language }
        languageOptions.firstOrNull { it.first == tag }?.second
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        ProfileHeader(
            title = stringResource(R.string.nav_profile),
            subtitle = stringResource(R.string.profile_about_version, BuildConfig.VERSION_NAME),
        )

        SectionLabel(stringResource(R.string.settings_section_library))
        SettingsGroup {
            ProfileOption(
                label = stringResource(R.string.profile_song_location),
                subtitle = storageLocationLabel,
                icon = Icons.Outlined.Folder,
                onClick = onPickStorageLocation,
            )
            SettingsDivider()
            ProfileOption(
                label = stringResource(R.string.profile_open_song_folder),
                subtitle = stringResource(R.string.profile_open_file_explorer),
                icon = Icons.Outlined.FolderOpen,
                onClick = onOpenStorageFolder,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_appearance))
        if (profiles.isNotEmpty()) {
            ThemePicker(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelect = onSelectProfile,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
        SettingsGroup {
            ProfileOption(
                label = stringResource(R.string.profile_customize_background),
                icon = Icons.Outlined.Palette,
                onClick = onCustomizeBackground,
            )
            SettingsDivider()
            ProfileOption(
                label = stringResource(R.string.profile_customize_text),
                icon = Icons.Outlined.TextFields,
                onClick = onCustomizeText,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_general))
        SettingsGroup {
            ProfileOption(
                label = stringResource(R.string.profile_change_language),
                subtitle = currentLanguageName,
                icon = Icons.Outlined.Language,
                onClick = { showLanguageDialog = true },
            )
            SettingsDivider()
            ProfileOption(
                label = stringResource(R.string.profile_check_updates),
                subtitle = if (checkingUpdates) {
                    stringResource(R.string.profile_check_updates_running)
                } else {
                    stringResource(R.string.profile_check_updates_hint)
                },
                icon = Icons.Outlined.SystemUpdate,
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
                                resources.getString(R.string.update_check_failed),
                            )
                            VersionCompare.isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                                pendingUpdate = release
                            }
                            else -> snackbarHostState.showSnackbar(
                                resources.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                            )
                        }
                    }
                },
            )
        }

        SectionLabel(stringResource(R.string.settings_section_about))
        SettingsGroup {
            ProfileOption(
                label = stringResource(R.string.profile_source_code),
                icon = Icons.Outlined.Code,
                external = true,
                onClick = { context.openUrl("https://github.com/imontalvodev/BeatMyBeat") },
            )
            SettingsDivider()
            ProfileOption(
                label = stringResource(R.string.profile_privacy_policy),
                icon = Icons.Outlined.PrivacyTip,
                external = true,
                onClick = { context.openUrl("https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md") },
            )
            SettingsDivider()
            ProfileOption(
                label = stringResource(R.string.profile_license),
                subtitle = stringResource(R.string.profile_responsible_use),
                icon = Icons.Outlined.Gavel,
                external = true,
                onClick = { context.openUrl("https://github.com/imontalvodev/BeatMyBeat/blob/main/LICENSE") },
            )
        }

        // Hueco para que la última fila no quede bajo el mini reproductor y la barra flotante.
        Spacer(modifier = Modifier.height(Spacing.xxl))
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
                Column {
                    languageOptions.forEach { (tag, name) ->
                        val selected = name == currentLanguageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showLanguageDialog = false
                                        if (!selected) onChangeLanguage(tag)
                                    },
                                )
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(modifier = Modifier.size(Spacing.md))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
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

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
}

@Composable
private fun ProfileOption(
    label: String,
    subtitle: String? = null,
    icon: ImageVector,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = subtitle?.let { sub ->
            {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
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
                imageVector = if (external) Icons.AutoMirrored.Outlined.OpenInNew
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}


/** Cabecera de Perfil: la rana dentro de un círculo con el borde del acento, título y versión. */
@Composable
private fun ProfileHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppLogo(size = 56.dp)
        }
        Spacer(modifier = Modifier.size(Spacing.lg))
        Column {
            Text(
                text = title,
                style = AppText.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Selector rápido de tema: vista previa del perfil activo y una cuadrícula de muestras
 * (diagonal fondo/acento). «Personalizar» abre el editor de colores completo.
 */
@Composable
private fun ThemePicker(
    profiles: List<BeatMyBeatThemeProfile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
) {
    val active = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.theme_preview_kicker, active.name).uppercase(),
                    style = AppText.kicker,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(
                                Brush.linearGradient(
                                    listOf(active.primaryVariant.copy(alpha = 0.55f), active.primaryVariant, CharcaColors.amber),
                                ),
                            ),
                    )
                    Spacer(modifier = Modifier.size(Spacing.md))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Column {
                            Text(
                                text = active.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.38f)
                                    .height(5.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(Spacing.md))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            profiles.forEach { profile ->
                val selected = profile.id == active.id
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 92.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { if (!selected) onSelect(profile.id) },
                        )
                        .padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
                ) {
                    ThemeSwatch(profile = profile, selected = selected, ringColor = active.primary)
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Círculo de 56dp partido en diagonal: fondo del perfil / su acento. */
@Composable
private fun ThemeSwatch(profile: BeatMyBeatThemeProfile, selected: Boolean, ringColor: Color) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.size(56.dp)) {
        val r = size.minDimension / 2f
        val half = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        clipPath(Path().apply { addOval(Rect(center, r - 1.5.dp.toPx())) }) {
            drawRect(color = profile.backgroundTop)
            drawPath(half, color = profile.primary)
        }
        drawCircle(
            color = if (selected) ringColor else outline,
            radius = r - 1.5.dp.toPx(),
            style = Stroke(width = 3.dp.toPx()),
        )
        if (selected) {
            drawCircle(
                color = ringColor.copy(alpha = 0.18f),
                radius = r + 2.dp.toPx(),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }
}
