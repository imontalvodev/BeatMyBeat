package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSessionQueueHelpersTest {

    @Test
    fun appendTracksToSessionAddsAtEnd() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 1)
        val updated = appendTracksToSession(state, listOf("d", "e"))
        assertEquals(listOf("a", "b", "c", "d", "e"), updated.orderUris)
        assertEquals(1, updated.currentIndex)
        assertEquals(listOf("c", "d", "e"), pendingUrisFromSession(updated))
    }

    @Test
    fun insertTracksPlayNextInSessionInsertsAfterCurrent() {
        val state = SessionQueueState(listOf("a", "b", "c", "d"), currentIndex = 2)
        val updated = insertTracksPlayNextInSession(state, listOf("x", "y"))
        assertEquals(listOf("a", "b", "c", "x", "y", "d"), updated.orderUris)
        assertEquals(listOf("x", "y", "d"), pendingUrisFromSession(updated))
    }

    @Test
    fun insertTracksPlayNextRespectsPreviousBlockFifo() {
        val state = SessionQueueState(listOf("a", "b", "c", "d"), currentIndex = 0)
        val first = insertTracksPlayNextInSession(state, listOf("x"))
        val playNext = listOf("x")
        val offset = playNextBlockSize(pendingUrisFromSession(first), playNext)
        assertEquals(1, offset)
        val second = insertTracksPlayNextInSession(first, listOf("y"), blockOffset = offset)
        // x (primero en añadirse) suena antes que y; ambos antes de lo que ya había.
        assertEquals(listOf("a", "x", "y", "b", "c", "d"), second.orderUris)
    }

    @Test
    fun playNextBlockSizeShrinksWhenHeadWasConsumedOrReordered() {
        val playNext = listOf("x", "y")
        assertEquals(2, playNextBlockSize(listOf("x", "y", "b"), playNext))
        // x ya sonó: y sigue siendo el resto del bloque.
        assertEquals(1, playNextBlockSize(listOf("y", "b"), playNext))
        // Reordenado o quitado: la racha se corta.
        assertEquals(1, playNextBlockSize(listOf("x", "b", "y"), playNext))
        assertEquals(0, playNextBlockSize(listOf("b", "c"), playNext))
        assertEquals(0, playNextBlockSize(emptyList(), playNext))
        assertEquals(0, playNextBlockSize(listOf("x"), emptyList()))
    }

    @Test
    fun replacePendingInSessionKeepsPlayedAndCurrent() {
        val state = SessionQueueState(listOf("a", "b", "c", "d", "e"), currentIndex = 1)
        val updated = replacePendingInSession(state, listOf("e", "c"))
        assertEquals(listOf("a", "b", "e", "c"), updated.orderUris)
        assertEquals(1, updated.currentIndex)
        assertEquals(listOf("e", "c"), pendingUrisFromSession(updated))
    }

    @Test
    fun replacePendingInSessionWithEmptyLeavesOnlyHistory() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 0)
        assertEquals(listOf("a"), replacePendingInSession(state, emptyList()).orderUris)
    }

    @Test
    fun ensureSessionQueueBuildsFromCurrentAndPending() {
        val state = ensureSessionQueue(
            currentTrackUri = "b",
            pendingQueueUris = listOf("c", "d"),
            existing = null,
        )
        assertEquals(SessionQueueState(listOf("b", "c", "d"), 0), state)
    }

    @Test
    fun advanceSessionToTrackUpdatesCurrentIndex() {
        val state = SessionQueueState(listOf("a", "b", "c", "d"), currentIndex = 0)
        val updated = advanceSessionToTrack(state, "c")
        assertEquals(2, updated?.currentIndex)
        // Pendientes = lo que viene después de la pista actual (no la incluye).
        assertEquals(listOf("d"), pendingUrisFromSession(updated!!))
    }

    @Test
    fun advanceSessionToLastTrackLeavesNothingPending() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 0)
        val updated = advanceSessionToTrack(state, "c")!!
        assertEquals(emptyList(), pendingUrisFromSession(updated))
    }

    @Test
    fun removeTrackFromSessionAdjustsCurrentIndex() {
        val state = SessionQueueState(listOf("a", "b", "c"), currentIndex = 2)
        val updated = removeTrackFromSession(state, "b")
        assertEquals(listOf("a", "c"), updated.orderUris)
        assertEquals(1, updated.currentIndex)
    }

    @Test
    fun ensureSessionQueueReturnsExistingWhenPresent() {
        val existing = SessionQueueState(listOf("a", "b"), 1)
        assertEquals(existing, ensureSessionQueue("z", listOf("q"), existing))
    }

    @Test
    fun ensureSessionQueueReturnsNullWithoutCurrentTrack() {
        assertNull(ensureSessionQueue(null, listOf("a"), null))
    }
}
