package com.imontalvodev.beatmybeat.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackErrorHelpersTest {

    @Test
    fun corruptQueue_nonBlankJsonWithZeroParsedItems_isCorrupt() {
        assertTrue(isQueueJsonCorrupt(queueJson = "[{\"broken\":true}]", parsedItemCount = 0))
    }

    @Test
    fun corruptQueue_blankJson_isNotCorrupt() {
        // Cola legítimamente vacía (nunca se cargó nada): no es un error, es el estado inicial.
        assertFalse(isQueueJsonCorrupt(queueJson = "", parsedItemCount = 0))
        assertFalse(isQueueJsonCorrupt(queueJson = "   ", parsedItemCount = 0))
    }

    @Test
    fun corruptQueue_nonBlankJsonWithParsedItems_isNotCorrupt() {
        assertFalse(isQueueJsonCorrupt(queueJson = "[{\"uri\":\"a\"}]", parsedItemCount = 1))
    }

    @Test
    fun showPlaybackError_firstEvent_showsIt() {
        assertTrue(shouldShowPlaybackError(newErrorId = 100L, lastShownErrorId = null))
    }

    @Test
    fun showPlaybackError_sameIdAsLastShown_doesNotRepeat() {
        assertFalse(shouldShowPlaybackError(newErrorId = 100L, lastShownErrorId = 100L))
    }

    @Test
    fun showPlaybackError_newIdDifferentFromLastShown_showsIt() {
        assertTrue(shouldShowPlaybackError(newErrorId = 200L, lastShownErrorId = 100L))
    }
}
