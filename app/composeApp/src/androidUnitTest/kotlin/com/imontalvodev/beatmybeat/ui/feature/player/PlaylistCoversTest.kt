package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PlaylistCoversTest {

    @Test
    fun squareCropCentersOnTheShortSide() {
        assertEquals(Triple(500, 0, 3000), PlaylistCovers.squareCrop(4000, 3000))
        assertEquals(Triple(0, 250, 1000), PlaylistCovers.squareCrop(1000, 1500))
        assertEquals(Triple(0, 0, 512), PlaylistCovers.squareCrop(512, 512))
    }

    @Test
    fun sampleSizeKeepsBothSidesAboveTarget() {
        // 4000x3000 -> el lado corto (3000) admite /4 (750) pero no /8 (375 < 512).
        assertEquals(4, PlaylistCovers.sampleSizeFor(4000, 3000, 512))
        assertEquals(1, PlaylistCovers.sampleSizeFor(600, 600, 512))
        assertEquals(1, PlaylistCovers.sampleSizeFor(0, 0, 512))
    }

    @Test
    fun movedItemReordersAndIgnoresInvalidIndices() {
        val list = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("c", "a", "b", "d", "e"), list.movedItem(2, 0))
        assertEquals(listOf("a", "b", "d", "e", "c"), list.movedItem(2, 4))
        assertSame(list, list.movedItem(1, 1))
        assertSame(list, list.movedItem(-1, 2))
        assertSame(list, list.movedItem(1, 9))
    }
}
