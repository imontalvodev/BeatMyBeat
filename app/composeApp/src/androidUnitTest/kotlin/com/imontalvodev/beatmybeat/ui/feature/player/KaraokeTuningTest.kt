package com.imontalvodev.beatmybeat.ui.feature.player

import com.imontalvodev.beatmybeat.service.PlaybackService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaraokeTuningTest {

    @Test
    fun `cero semitonos deja el tono intacto`() {
        assertEquals(1f, KaraokeTuning.pitchRatio(0f), 0.0001f)
    }

    @Test
    fun `doce semitonos serian una octava pero el rango los recorta a seis`() {
        // El slider no llega a 12; si algo llama con ese valor debe quedarse en el máximo
        // aceptado por PlaybackService, no propagar un ratio fuera de rango.
        assertEquals(PlaybackService.MAX_PLAYBACK_PITCH, KaraokeTuning.pitchRatio(12f), 0.0001f)
        assertEquals(PlaybackService.MIN_PLAYBACK_PITCH, KaraokeTuning.pitchRatio(-12f), 0.0001f)
    }

    @Test
    fun `un semitono arriba es la raiz doceava de dos`() {
        assertEquals(1.05946f, KaraokeTuning.pitchRatio(1f), 0.0001f)
    }

    @Test
    fun `subir y bajar el mismo intervalo son reciprocos`() {
        val up = KaraokeTuning.pitchRatio(5f)
        val down = KaraokeTuning.pitchRatio(-5f)
        assertEquals(1f, up * down, 0.0001f)
    }

    @Test
    fun `el ratio siempre cae dentro del rango que acepta el servicio`() {
        var st = KaraokeTuning.MIN_SEMITONES
        while (st <= KaraokeTuning.MAX_SEMITONES) {
            val ratio = KaraokeTuning.pitchRatio(st)
            assertTrue(
                ratio in PlaybackService.MIN_PLAYBACK_PITCH..PlaybackService.MAX_PLAYBACK_PITCH,
                "ratio $ratio fuera de rango para $st semitonos",
            )
            st += 0.5f
        }
    }

    @Test
    fun `la etiqueta de semitonos lleva signo solo al subir`() {
        assertEquals("+2", KaraokeTuning.semitoneLabel(2f))
        assertEquals("0", KaraokeTuning.semitoneLabel(0f))
        assertEquals("-3", KaraokeTuning.semitoneLabel(-3f))
    }

    @Test
    fun `la etiqueta de velocidad se redondea a dos decimales`() {
        assertEquals("1.0x", KaraokeTuning.speedLabel(1f))
        assertEquals("0.85x", KaraokeTuning.speedLabel(0.8499f))
    }

    @Test
    fun `isNeutral solo es cierto sin transporte ni cambio de velocidad`() {
        assertTrue(KaraokeTuning.isNeutral(0f, 1f))
        assertFalse(KaraokeTuning.isNeutral(1f, 1f))
        assertFalse(KaraokeTuning.isNeutral(0f, 1.25f))
    }
}
