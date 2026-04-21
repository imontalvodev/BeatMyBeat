package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AppMiniBrand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(999.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Logo BeatMyBeat",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "BeatMyBeat",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        text = text,
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val start = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    val end = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    }
    Surface(
        modifier = modifier
            .height(56.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        start,
                        end,
                    ),
                ),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

