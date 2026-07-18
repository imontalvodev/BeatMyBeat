package com.imontalvodev.beatmybeat.ui.feature.player

import com.imontalvodev.beatmybeat.service.PlaybackService
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Conversión entre lo que ve el usuario (semitonos) y lo que quiere ExoPlayer (ratio de tono).
 *
 * El slider habla en semitonos porque es la unidad que usa quien canta ("súbelo dos tonos"),
 * mientras que `PlaybackParameters.pitch` es un multiplicador de frecuencia. Un semitono es
 * 2^(1/12), así que N semitonos son 2^(N/12).
 */
object KaraokeTuning {

    const val MIN_SEMITONES = -6f
    const val MAX_SEMITONES = 6f

    /** Valores neutros: ni transporte ni cambio de velocidad. */
    const val NEUTRAL_SEMITONES = 0f
    const val NEUTRAL_SPEED = 1f
    const val NEUTRAL_PITCH = 1f

    /** Ratio de tono para [semitones], acotado al rango que acepta [PlaybackService]. */
    fun pitchRatio(semitones: Float): Float {
        val clamped = semitones.coerceIn(MIN_SEMITONES, MAX_SEMITONES)
        return 2f.pow(clamped / 12f)
            .coerceIn(PlaybackService.MIN_PLAYBACK_PITCH, PlaybackService.MAX_PLAYBACK_PITCH)
    }

    /** Etiqueta del slider de tono: "0", "+2", "-3" (semitonos enteros). */
    fun semitoneLabel(semitones: Float): String {
        val rounded = semitones.roundToInt()
        return if (rounded > 0) "+$rounded" else rounded.toString()
    }

    /** Etiqueta del slider de velocidad: "1.0x", "0.85x". */
    fun speedLabel(speed: Float): String {
        val rounded = (speed * 100f).roundToInt() / 100f
        return "${rounded}x"
    }

    fun isNeutral(semitones: Float, speed: Float): Boolean =
        semitones.roundToInt() == 0 && (speed * 100f).roundToInt() == 100
}
