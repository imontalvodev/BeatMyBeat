package com.imontalvodev.beatmybeat.ui.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imontalvodev.beatmybeat.ui.network.LrcLine
import com.imontalvodev.beatmybeat.ui.network.LrcParser

/**
 * Letras sincronizadas con la posición de reproducción (LRC en local).
 * La línea activa se centra con padding vertical = mitad del viewport + scroll suave.
 */
@Composable
fun SyncedLyricsView(
    lines: List<LrcLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
    /** Compensación fina audio/letra (ms). Positivo = la letra va un poco antes. */
    syncOffsetMs: Long = 0L,
    onLineClick: ((Long) -> Unit)? = null,
) {
    if (lines.isEmpty()) return

    val adjustedMs = (positionMs + syncOffsetMs).coerceAtLeast(0L)
    val activeIndex = LrcParser.lineAtPosition(lines, adjustedMs)

    val listState = rememberLazyListState()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(activeIndex, lines.size) {
        if (activeIndex < 0) return@LaunchedEffect
        val target = activeIndex.coerceIn(0, lines.lastIndex)
        listState.animateScrollToItem(target)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerPad = maxHeight / 2

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = centerPad,
                bottom = centerPad,
                start = 16.dp,
                end = 16.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${index}_${line.startMs}_${line.text.hashCode()}" },
            ) { index, line ->
                val isActive = index == activeIndex
                val isPast = activeIndex >= 0 && index < activeIndex
                val color by animateColorAsState(
                    targetValue = when {
                        isActive -> primary
                        isPast -> onSurface.copy(alpha = 0.45f)
                        else -> onSurface.copy(alpha = 0.65f)
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "lyric_line_color",
                )
                val fontSize = if (isActive) 18.sp else 15.sp
                val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .then(
                            if (onLineClick != null) {
                                Modifier.clickable { onLineClick(line.startMs) }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isActive && line.words.isNotEmpty()) {
                        // Solo se resalta palabra a palabra cuando la fuente trae timestamps
                        // reales; ver LrcLine.words. Sin eso no hay forma de saber qué se está
                        // cantando en cada instante sin adivinar (y adivinar se desincroniza).
                        val highlightLen = LrcParser.karaokeHighlightLength(line, adjustedMs)
                        val sungColor = primary
                        val upcomingColor = primary.copy(alpha = 0.45f)
                        val annotated = buildAnnotatedString {
                            withStyle(SpanStyle(color = sungColor)) {
                                append(line.text.substring(0, highlightLen))
                            }
                            withStyle(SpanStyle(color = upcomingColor)) {
                                append(line.text.substring(highlightLen))
                            }
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeight = 22.sp,
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeight = 22.sp,
                            ),
                            color = color,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
