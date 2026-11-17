package com.imontalvodev.beatmybeat.ui.feature.splash

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.imontalvodev.beatmybeat.BuildConfig
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.ui.theme.AppText
import com.imontalvodev.beatmybeat.ui.theme.currentBeatMyBeatThemeProfile
import com.imontalvodev.beatmybeat.ui.theme.Motion
import com.imontalvodev.beatmybeat.ui.theme.Spacing

/** El usuario pidió menos movimiento (escala de animaciones a 0): las ondas se quedan quietas. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Splash «Charca»: ondas de charca que se expanden tras la rana, titular y botón de entrada.
 * Es el momento de marca (el único con [Motion.BRAND]); los colores salen del tema activo.
 */
@Composable
fun SplashScreen(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentBeatMyBeatThemeProfile()
    val accent = MaterialTheme.colorScheme.primary
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "splash")

    val ringProgress = (0 until 3).map { index ->
        if (reduced) {
            null
        } else {
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(RING_PERIOD_MS, easing = LinearEasing),
                    initialStartOffset = StartOffset(index * RING_STAGGER_MS),
                ),
                label = "ring$index",
            )
        }
    }
    val floatOffset = if (reduced) {
        null
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = tween(FLOAT_PERIOD_MS / 2, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logo_float",
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.backgroundTop, palette.backgroundBottom),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.34f)
            val base = 210.dp.toPx()
            for (index in 0 until 3) {
                val t = ringProgress[index]?.value ?: 0.55f
                // Escala .45 → 1.5, opacidad .55 → 0; en reposo, tres anillos tenues y fijos.
                val scale = if (reduced) 0.9f else 0.45f + 1.05f * t
                val alpha = if (reduced) 0.18f else 0.55f * (1f - t)
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = base * scale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
                .offset(y = (floatOffset?.value ?: 0f).dp)
                .size(width = 300.dp, height = 264.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.headlineLarge,
                color = palette.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = onEnter,
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.splash_enter),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME.substringBefore('-')} · GPL-3.0",
                style = AppText.meta,
                color = palette.onSurfaceMuted.copy(alpha = 0.8f),
            )
        }
    }
}

private const val RING_PERIOD_MS = 3600
private const val RING_STAGGER_MS = 1200
private const val FLOAT_PERIOD_MS = 4200
