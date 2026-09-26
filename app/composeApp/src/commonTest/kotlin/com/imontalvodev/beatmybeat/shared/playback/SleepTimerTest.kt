package com.imontalvodev.beatmybeat.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class SleepTimerTest {

    @Test
    fun startingAtAddsMinutes() {
        val timer = SleepTimer.startingAt(nowMs = 1_000, minutes = 15)
        assertEquals(1_000 + 15 * 60_000L, timer.endsAtMs)
        assertEquals(15 * 60_000L, timer.remainingMs(1_000))
        assertEquals(0, timer.remainingMs(timer.endsAtMs + 5_000))
    }

    @Test
    fun fadeOnlyInTheLastSeconds() {
        assertEquals(1f, SleepTimer.fadeVolume(60_000))
        assertEquals(1f, SleepTimer.fadeVolume(SleepTimer.FADE_OUT_MS))
        assertEquals(0.5f, SleepTimer.fadeVolume(SleepTimer.FADE_OUT_MS / 2))
        assertEquals(0f, SleepTimer.fadeVolume(0))
        assertEquals(0f, SleepTimer.fadeVolume(-10))
    }

    @Test
    fun remainingTimeFormatting() {
        assertEquals("15:00", SleepTimer.formatRemaining(15 * 60_000L))
        assertEquals("0:01", SleepTimer.formatRemaining(1))
        assertEquals("0:00", SleepTimer.formatRemaining(0))
        assertEquals("1:30:00", SleepTimer.formatRemaining(90 * 60_000L))
    }
}
