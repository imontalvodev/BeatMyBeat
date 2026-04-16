package com.imontalvodev.savetune.ui.feature.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.imontalvodev.savetune.ui.theme.PrimaryButton
import com.imontalvodev.savetune.ui.theme.SavetuneThemeProfile
import java.util.UUID

@Composable
fun ThemeCustomizerScreen(
    profiles: List<SavetuneThemeProfile>,
    activeProfileId: String,
    onApplyProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveProfile: (SavetuneThemeProfile) -> Unit,
) {
    var name by remember { mutableStateOf("Mi tema") }
    var backgroundTop by remember { mutableStateOf("#050816") }
    var backgroundBottom by remember { mutableStateOf("#0B1120") }
    var primary by remember { mutableStateOf("#22C55E") }
    var primaryVariant by remember { mutableStateOf("#16A34A") }
    var secondary by remember { mutableStateOf("#14B8A6") }
    var surface by remember { mutableStateOf("#020617") }
    var onSurface by remember { mutableStateOf("#F9FAFB") }
    var onSurfaceMuted by remember { mutableStateOf("#9CA3AF") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun parseHexColor(hex: String): Color? {
        val raw = hex.trim().removePrefix("#")
        if (raw.length != 6 && raw.length != 8) return null
        val argb = if (raw.length == 6) "FF$raw" else raw
        return runCatching { Color(argb.toLong(16)) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Personalizar colores", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Crea perfiles y cámbialos desde el menú lateral.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Perfiles guardados", style = MaterialTheme.typography.titleMedium)
                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${if (profile.id == activeProfileId) "● " else ""}${profile.name}",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row {
                            TextButton(onClick = { onApplyProfile(profile.id) }) { Text("Aplicar") }
                            if (!profile.id.startsWith("builtin-")) {
                                TextButton(onClick = { onDeleteProfile(profile.id) }) { Text("Borrar") }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre del perfil") })
        ColorField("Background top", backgroundTop) { backgroundTop = it }
        ColorField("Background bottom", backgroundBottom) { backgroundBottom = it }
        ColorField("Primary", primary) { primary = it }
        ColorField("Primary variant", primaryVariant) { primaryVariant = it }
        ColorField("Secondary", secondary) { secondary = it }
        ColorField("Surface", surface) { surface = it }
        ColorField("On surface", onSurface) { onSurface = it }
        ColorField("On surface muted", onSurfaceMuted) { onSurfaceMuted = it }

        validationError?.let {
            Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
        }

        PrimaryButton(
            text = "Guardar perfil",
            onClick = {
                val profile = SavetuneThemeProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Tema custom" },
                    backgroundTop = parseHexColor(backgroundTop) ?: run { validationError = "Color inválido: Background top"; return@PrimaryButton },
                    backgroundBottom = parseHexColor(backgroundBottom) ?: run { validationError = "Color inválido: Background bottom"; return@PrimaryButton },
                    primary = parseHexColor(primary) ?: run { validationError = "Color inválido: Primary"; return@PrimaryButton },
                    primaryVariant = parseHexColor(primaryVariant) ?: run { validationError = "Color inválido: Primary variant"; return@PrimaryButton },
                    secondary = parseHexColor(secondary) ?: run { validationError = "Color inválido: Secondary"; return@PrimaryButton },
                    surface = parseHexColor(surface) ?: run { validationError = "Color inválido: Surface"; return@PrimaryButton },
                    onSurface = parseHexColor(onSurface) ?: run { validationError = "Color inválido: On surface"; return@PrimaryButton },
                    onSurfaceMuted = parseHexColor(onSurfaceMuted) ?: run { validationError = "Color inválido: On surface muted"; return@PrimaryButton },
                )
                validationError = null
                onSaveProfile(profile)
                onApplyProfile(profile.id)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("#RRGGBB") },
            singleLine = true,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Preview:", style = MaterialTheme.typography.bodySmall)
            BoxColorPreview(value)
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun BoxColorPreview(value: String) {
    val color = runCatching {
        val raw = value.trim().removePrefix("#")
        val argb = if (raw.length == 6) "FF$raw" else raw
        if (argb.length != 8) return@runCatching null
        Color(argb.toLong(16))
    }.getOrNull() ?: Color(0xFF1F2937)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(18.dp)
            .background(color = color, shape = RoundedCornerShape(4.dp)),
    )
}

