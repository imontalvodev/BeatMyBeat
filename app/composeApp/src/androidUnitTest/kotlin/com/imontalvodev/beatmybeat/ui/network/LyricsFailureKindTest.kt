package com.imontalvodev.beatmybeat.ui.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsFailureKindTest {

    @Test
    fun `los fallos de red se distinguen del resto`() {
        assertTrue(isLyricsNetworkFailure(ERROR_UNREACHABLE))
        assertTrue(isLyricsNetworkFailure(ERROR_TIMEOUT))
    }

    @Test
    fun `no encontrado no es un fallo de red`() {
        // Es la distincion que importa: con NotFound reintentar no sirve de nada, asi que la UI
        // no debe invitar a ello.
        assertFalse(isLyricsNetworkFailure("NotFound"))
        assertFalse(isLyricsNetworkFailure("MissingFields"))
    }

    @Test
    fun `sin error no es fallo de red`() {
        assertFalse(isLyricsNetworkFailure(null))
        assertFalse(isLyricsNetworkFailure(""))
    }
}
