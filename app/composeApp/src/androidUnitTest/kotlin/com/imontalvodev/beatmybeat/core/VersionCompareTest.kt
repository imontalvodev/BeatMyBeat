package com.imontalvodev.beatmybeat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {

    @Test
    fun isNewer_patch() {
        assertTrue(VersionCompare.isNewer("1.0.1", "1.0.0"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun isNewer_withVPrefix() {
        assertTrue(VersionCompare.isNewer("v1.0.2", "1.0.1"))
    }

    @Test
    fun isNewer_multiDigitSegments() {
        assertTrue(VersionCompare.isNewer("1.0.10", "1.0.9"))
        assertFalse(VersionCompare.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun compare_equalVersions() {
        assertEquals(0, VersionCompare.compare("1.0.2", "v1.0.2"))
    }

    @Test
    fun parseSegments_ignoresSuffix() {
        assertEquals(listOf(1, 0, 1), VersionCompare.parseSegments("1.0.1-beta"))
    }

    @Test
    fun parseSegments_nonNumericSegmentInTheMiddleCountsAsZero_doesNotDisappear() {
        // Regresión: antes "1.a.2" perdía el segmento "a" por completo (mapNotNull) y quedaba
        // como [1, 2], desalineado frente a versiones de 3 segmentos.
        assertEquals(listOf(1, 0, 2), VersionCompare.parseSegments("1.a.2"))
    }

    @Test
    fun isNewer_nonNumericMiddleSegment_comparesInCorrectDirection() {
        // Antes: compare("1.a.2", "1.0.3") comparaba [1,2] vs [1,0,3] y decía que 1.a.2 era más
        // nueva. Con el segmento alineado a 0, debe ser correctamente más antigua.
        assertFalse(VersionCompare.isNewer("1.a.2", "1.0.3"))
        assertTrue(VersionCompare.isNewer("1.0.3", "1.a.2"))
    }
}
