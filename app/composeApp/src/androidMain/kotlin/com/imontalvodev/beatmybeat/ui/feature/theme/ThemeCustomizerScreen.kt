package com.imontalvodev.beatmybeat.ui.feature.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.theme.BeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.AppMiniBrand
import com.imontalvodev.beatmybeat.ui.theme.ModeChip
import com.imontalvodev.beatmybeat.ui.theme.PrimaryButton
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ThemeCustomizerSection {
    Background,
    Text,
}

@Composable
fun ThemeCustomizerScreen(
    section: ThemeCustomizerSection,
    profiles: List<BeatMyBeatThemeProfile>,
    activeProfileId: String,
    onApplyProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveProfile: (BeatMyBeatThemeProfile) -> Unit,
) {
    val palette = currentBeatMyBeatThemeProfile()
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    val bgBrush = Brush.verticalGradient(
        colors = listOf(palette.backgroundTop, palette.backgroundBottom),
    )

    var name by remember { mutableStateOf("Mi tema") }
    var backgroundTop by remember { mutableStateOf(Color(0xFF000000)) }
    var backgroundBottom by remember { mutableStateOf(Color(0xFF181818)) }
    var primary by remember { mutableStateOf(Color(0xFF07979C)) }
    var primaryVariant by remember { mutableStateOf(Color(0xFF0AAEB3)) }
    var secondary by remember { mutableStateOf(Color(0xFF27C2C7)) }
    var surface by remember { mutableStateOf(Color(0xFF060A0D)) }
    var onSurface by remember { mutableStateOf(Color(0xFFF5FCFF)) }
    var onSurfaceMuted by remember { mutableStateOf(Color(0xFF9FB5BD)) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    fun loadProfile(profile: BeatMyBeatThemeProfile, keepId: Boolean) {
        name = profile.name
        backgroundTop = profile.backgroundTop
        backgroundBottom = profile.backgroundBottom
        primary = profile.primary
        primaryVariant = profile.primaryVariant
        secondary = profile.secondary
        surface = profile.surface
        onSurface = profile.onSurface
        onSurfaceMuted = profile.onSurfaceMuted
        editingProfileId = if (keepId) profile.id else null
    }

    LaunchedEffect(activeProfileId, profiles) {
        val profile = activeProfile ?: return@LaunchedEffect
        loadProfile(profile, keepId = !profile.id.startsWith("builtin-"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppMiniBrand()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    text = stringResource(R.string.theme_tab_background),
                    selected = section == ThemeCustomizerSection.Background,
                    onClick = {},
                )
                ModeChip(
                    text = stringResource(R.string.theme_tab_text),
                    selected = section == ThemeCustomizerSection.Text,
                    onClick = {},
                )
            }

            Text(
                text = if (section == ThemeCustomizerSection.Background) {
                    stringResource(R.string.theme_customize_background)
                } else {
                    stringResource(R.string.theme_customize_text)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.theme_saved_profiles), style = MaterialTheme.typography.titleMedium)
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${if (profile.id == activeProfileId) "● " else ""}${profile.name}",
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                TextButton(onClick = { onApplyProfile(profile.id) }) { Text(stringResource(R.string.common_apply)) }
                                if (!profile.id.startsWith("builtin-")) {
                                    TextButton(onClick = { onDeleteProfile(profile.id) }) { Text(stringResource(R.string.common_delete)) }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.theme_profile_name)) },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { activeProfile?.let { loadProfile(it, keepId = !it.id.startsWith("builtin-")) } }) {
                    Text(stringResource(R.string.theme_load_active))
                }
                TextButton(onClick = { editingProfileId = null; name = "Mi tema" }) {
                    Text(stringResource(R.string.theme_new_profile))
                }
            }

            if (section == ThemeCustomizerSection.Background) {
                ColorField("Background top", backgroundTop) { backgroundTop = it }
                ColorField("Background bottom", backgroundBottom) { backgroundBottom = it }
                ColorField("Primary", primary) { primary = it }
                ColorField("Primary variant", primaryVariant) { primaryVariant = it }
                ColorField("Secondary", secondary) { secondary = it }
                ColorField("Surface", surface) { surface = it }
            } else {
                ColorField("On surface", onSurface) { onSurface = it }
                ColorField("On surface muted", onSurfaceMuted) { onSurfaceMuted = it }
            }

            PreviewBlock(
                section = section,
                backgroundTop = backgroundTop,
                backgroundBottom = backgroundBottom,
                primary = primary,
                surface = surface,
                onSurface = onSurface,
                onSurfaceMuted = onSurfaceMuted,
            )

            validationError?.let {
                Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
            }

            PrimaryButton(
                text = if (editingProfileId == null) stringResource(R.string.theme_save_as_new) else stringResource(R.string.theme_apply_changes),
                onClick = {
                    val mergeBase = editingProfileId?.let { pid -> profiles.firstOrNull { it.id == pid } }
                        ?: profiles.firstOrNull { it.id == activeProfileId }
                        ?: profiles.first()
                    val profile = when (section) {
                        ThemeCustomizerSection.Background -> BeatMyBeatThemeProfile(
                            id = editingProfileId ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "Tema custom" },
                            backgroundTop = backgroundTop,
                            backgroundBottom = backgroundBottom,
                            primary = primary,
                            primaryVariant = primaryVariant,
                            secondary = secondary,
                            surface = surface,
                            onSurface = mergeBase.onSurface,
                            onSurfaceMuted = mergeBase.onSurfaceMuted,
                        )
                        ThemeCustomizerSection.Text -> BeatMyBeatThemeProfile(
                            id = editingProfileId ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "Tema custom" },
                            backgroundTop = mergeBase.backgroundTop,
                            backgroundBottom = mergeBase.backgroundBottom,
                            primary = mergeBase.primary,
                            primaryVariant = mergeBase.primaryVariant,
                            secondary = mergeBase.secondary,
                            surface = mergeBase.surface,
                            onSurface = onSurface,
                            onSurfaceMuted = onSurfaceMuted,
                        )
                    }
                    validationError = null
                    onSaveProfile(profile)
                    onApplyProfile(profile.id)
                    editingProfileId = profile.id
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorField(
    label: String,
    value: Color,
    onValueChange: (Color) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxColorPreview(value)
                TextButton(onClick = { pickerOpen = true }) {
                    Text(stringResource(R.string.theme_choose))
                }
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(stringResource(R.string.theme_select_color, label)) },
            text = {
                ColorWheelPicker(
                    initialColor = value,
                    onColorChanged = onValueChange,
                )
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.theme_done))
                }
            },
        )
    }
}

@Composable
private fun ColorWheelPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
) {
    var hue by remember(initialColor) { mutableStateOf(colorToHsv(initialColor).first) }
    var saturation by remember(initialColor) { mutableStateOf(colorToHsv(initialColor).second) }
    var value by remember(initialColor) { mutableStateOf(colorToHsv(initialColor).third) }

    val selectedColor = hsvToColor(hue, saturation, value)

    LaunchedEffect(hue, saturation, value) {
        onColorChanged(selectedColor)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(Unit) {
                    fun updateHue(point: Offset, sizePx: Float) {
                        val center = Offset(sizePx / 2f, sizePx / 2f)
                        val angle = Math.toDegrees(
                            atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble()),
                        ).toFloat()
                        hue = (angle + 360f) % 360f
                    }
                    detectTapGestures { point ->
                        updateHue(point, min(size.width, size.height).toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val p = change.position
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = Math.toDegrees(
                            atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble()),
                        ).toFloat()
                        hue = (angle + 360f) % 360f
                    }
                },
        ) {
            val diameter = min(size.width, size.height)
            val stroke = diameter * 0.20f
            val radius = diameter / 2f - stroke / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
                radius = radius,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val angleRad = Math.toRadians(hue.toDouble())
            val cx = size.width / 2f + (radius * cos(angleRad)).toFloat()
            val cy = size.height / 2f + (radius * sin(angleRad)).toFloat()
            drawCircle(
                color = Color.White,
                radius = stroke * 0.22f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = selectedColor,
                radius = stroke * 0.14f,
                center = Offset(cx, cy),
            )
        }

        Text(stringResource(R.string.theme_saturation), style = MaterialTheme.typography.bodySmall)
        Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)

        Text(stringResource(R.string.theme_brightness), style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.theme_current_color), style = MaterialTheme.typography.bodySmall)
            BoxColorPreview(selectedColor)
        }
    }
}

@Composable
private fun BoxColorPreview(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color = color, shape = RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun PreviewBlock(
    section: ThemeCustomizerSection,
    backgroundTop: Color,
    backgroundBottom: Color,
    primary: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceMuted: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(backgroundTop, backgroundBottom)))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (section == ThemeCustomizerSection.Background) "Vista previa de fondo" else "Vista previa de texto",
                color = onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Texto principal de ejemplo",
                color = onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Texto secundario de ejemplo",
                color = onSurfaceMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                modifier = Modifier
                    .background(primary.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.theme_accent), color = primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = floatArrayOf(
        hue.coerceIn(0f, 360f),
        saturation.coerceIn(0f, 1f),
        value.coerceIn(0f, 1f),
    )
    return Color(AndroidColor.HSVToColor(hsv))
}
