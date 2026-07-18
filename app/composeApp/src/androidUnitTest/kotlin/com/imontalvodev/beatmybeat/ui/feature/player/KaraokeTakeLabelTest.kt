package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals

class KaraokeTakeLabelTest {

    @Test
    fun `la etiqueta es fecha y hora legibles`() {
        assertEquals("18/07/2026 11:09", takeLabel("REC-2026-07-18-11-09-57.m4a"))
    }

    @Test
    fun `un nombre con otro formato se muestra tal cual`() {
        // Mejor enseniar el nombre crudo que una fila vacia: si algun dia cambia el formato, el
        // usuario sigue viendo cual es cada toma.
        assertEquals("otra-cosa.m4a", takeLabel("otra-cosa.m4a"))
        assertEquals("REC-2026-07.m4a", takeLabel("REC-2026-07.m4a"))
    }
}
