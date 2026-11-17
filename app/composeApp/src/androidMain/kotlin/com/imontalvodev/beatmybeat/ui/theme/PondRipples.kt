package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Motivo de «Charca»: anillos concéntricos que se expanden y se desvanecen detrás del elemento.
 *
 * Se pintan fuera de los límites del nodo (sin recortar), así que sirve para la carátula del
 * reproductor. Con [active] en falso (pausa) o con las animaciones del sistema desactivadas se
 * quedan quietos y tenues.
 */
@Composable
fun Modifier.pondRipples(
    color: Color,
    active: Boolean,
    rings: Int = 3,
    periodMs: Int = 4000,
): Modifier {
    val reduced = rememberReducedMotionSetting()
    val transition = rememberInfiniteTransition(label = "pond")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "pond_phase",
    )
    val running = active && !reduced
    return drawBehind {
        val baseRadius = size.minDimension * 1.087f / 2f
        val stroke = Stroke(width = 1.5.dp.toPx())
        for (i in 0 until rings) {
            if (running) {
                val p = (phase + i / rings.toFloat()) % 1f
                drawCircle(
                    color = color.copy(alpha = 0.5f * (1f - p)),
                    radius = baseRadius * (0.82f + 0.63f * p),
                    center = center,
                    style = stroke,
                )
            } else {
                drawCircle(
                    color = color.copy(alpha = 0.2f - i * 0.06f),
                    radius = baseRadius * (0.95f + i * 0.2f),
                    center = center,
                    style = stroke,
                )
            }
        }
    }
}
