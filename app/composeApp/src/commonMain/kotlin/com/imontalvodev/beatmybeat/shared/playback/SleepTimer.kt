package com.imontalvodev.beatmybeat.shared.playback

/**
 * Temporizador de apagado. Los instantes son de un reloj monótono (en Android,
 * `SystemClock.elapsedRealtime()`), así que no les afecta cambiar la hora del sistema.
 */
sealed interface SleepTimer {
    /** Para la música en [endsAtMs], con un fundido de [FADE_OUT_MS] justo antes. */
    data class AtTime(val endsAtMs: Long) : SleepTimer {
        fun remainingMs(nowMs: Long): Long = (endsAtMs - nowMs).coerceAtLeast(0)
    }

    /** Para la música al acabar la canción que suena. */
    data object EndOfTrack : SleepTimer

    companion object {
        /** Duraciones que ofrece la UI, en minutos. */
        val PRESET_MINUTES = listOf(5, 15, 30, 45, 60, 90)

        const val FADE_OUT_MS = 10_000L
        const val FADE_STEPS = 20

        fun startingAt(nowMs: Long, minutes: Int): AtTime = AtTime(nowMs + minutes * 60_000L)

        /**
         * Volumen (0..1) del fundido final: 1 hasta que quedan [FADE_OUT_MS] y luego baja en
         * línea recta hasta 0. Si alguien pausa a mitad, el servicio restaura el volumen.
         */
        fun fadeVolume(remainingMs: Long): Float = when {
            remainingMs >= FADE_OUT_MS -> 1f
            remainingMs <= 0 -> 0f
            else -> remainingMs.toFloat() / FADE_OUT_MS
        }

        /** "mm:ss", o "h:mm:ss" desde una hora. Redondea hacia arriba para no mostrar 0:00 antes de tiempo. */
        fun formatRemaining(remainingMs: Long): String {
            val totalSeconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val ss = seconds.toString().padStart(2, '0')
            return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
        }
    }
}
