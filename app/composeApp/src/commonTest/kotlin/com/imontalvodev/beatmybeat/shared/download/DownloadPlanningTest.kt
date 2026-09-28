package com.imontalvodev.beatmybeat.shared.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadPlanningTest {

    private val aac128 = SourceStream("aac128", AudioCodec.AAC, 128, "m4a")
    private val aac48 = SourceStream("aac48", AudioCodec.AAC, 48, "m4a")
    private val opus160 = SourceStream("opus160", AudioCodec.OPUS, 160, "webm")
    private val dubbedOpus = opus160.copy(url = "dubbed", bitrateKbps = 170, isOriginalAudio = false)
    private val dashOpus = opus160.copy(url = "dash", bitrateKbps = 256, isDirectDownload = false)
    private val muxedVideo = SourceStream("video", AudioCodec.AAC, 192, "mp4", isVideo = true)
    private val tags = TrackTags(title = "Say \"Hi\" \\ \$HOME", artist = "Ärtist", album = "Album")

    @Test
    fun m4aTargetPrefersAacSoItCanBeCopied() {
        assertEquals(aac128, chooseSourceStream(listOf(opus160, aac48, aac128), DownloadFormat.M4A))
    }

    @Test
    fun oggTargetPrefersOpus() {
        assertEquals(opus160, chooseSourceStream(listOf(aac128, opus160), DownloadFormat.OGG))
    }

    @Test
    fun transcodedTargetsTakeTheHighestBitrateSource() {
        assertEquals(opus160, chooseSourceStream(listOf(aac128, opus160), DownloadFormat.MP3))
        assertEquals(opus160, chooseSourceStream(listOf(aac128, opus160), DownloadFormat.FLAC))
    }

    @Test
    fun manifestStreamsAreNeverChosen() {
        assertEquals(aac128, chooseSourceStream(listOf(dashOpus, aac128), DownloadFormat.MP3))
        assertNull(chooseSourceStream(listOf(dashOpus), DownloadFormat.MP3))
    }

    @Test
    fun originalAudioBeatsDubbedTrack() {
        assertEquals(opus160, chooseSourceStream(listOf(dubbedOpus, opus160), DownloadFormat.MP3))
        assertEquals(dubbedOpus, chooseSourceStream(listOf(dubbedOpus), DownloadFormat.MP3))
    }

    @Test
    fun audioOnlyBeatsMuxedVideoButVideoIsFallback() {
        assertEquals(aac128, chooseSourceStream(listOf(muxedVideo, aac128), DownloadFormat.M4A))
        assertEquals(muxedVideo, chooseSourceStream(listOf(muxedVideo), DownloadFormat.M4A))
    }

    @Test
    fun codecStringsFromYouTubeAreRecognised() {
        assertEquals(AudioCodec.AAC, AudioCodec.fromCodecString("mp4a.40.2"))
        assertEquals(AudioCodec.OPUS, AudioCodec.fromCodecString("opus"))
        assertEquals(AudioCodec.UNKNOWN, AudioCodec.fromCodecString(null))
    }

    @Test
    fun m4aFromAacIsARemuxWithCover() {
        val args = buildFfmpegArguments("in.m4a", "cover.jpg", "out.m4a", AudioCodec.AAC, DownloadFormat.M4A, tags)
        assertTrue(args.containsInOrder("-c:a", "copy"))
        assertTrue(args.containsInOrder("-map", "1:v:0"))
        assertTrue(args.containsInOrder("-disposition:v:0", "attached_pic"))
        assertEquals("out.m4a", args.last())
    }

    @Test
    fun mp3FromOpusIsTranscodedWithId3() {
        val args = buildFfmpegArguments("in.webm", "cover.jpg", "out.mp3", AudioCodec.OPUS, DownloadFormat.MP3, tags)
        assertTrue(args.containsInOrder("-c:a", "mp3"))
        assertTrue(args.containsInOrder("-id3v2_version", "3"))
        assertFalse(args.containsInOrder("-c:a", "copy"))
    }

    @Test
    fun oggFromOpusIsCopiedWithoutCover() {
        val args = buildFfmpegArguments("in.webm", "cover.jpg", "out.ogg", AudioCodec.OPUS, DownloadFormat.OGG, tags)
        assertTrue(args.containsInOrder("-c:a", "copy"))
        assertFalse("cover.jpg" in args, "el muxer ogg de ffmpeg no admite carátula incrustada")
        assertTrue("-vn" in args)
    }

    @Test
    fun aacTargetUsesAdtsMuxer() {
        val args = buildFfmpegArguments("in.m4a", null, "out.aac", AudioCodec.AAC, DownloadFormat.AAC, tags)
        assertTrue(args.containsInOrder("-f", "adts"))
    }

    @Test
    fun tagsArePassedVerbatimAsSingleArguments() {
        val args = buildFfmpegArguments("in.m4a", null, "out.mp3", AudioCodec.AAC, DownloadFormat.MP3, tags)
        assertTrue("title=Say \"Hi\" \\ \$HOME" in args)
        assertTrue("artist=Ärtist" in args)
    }

    @Test
    fun fileNamesAreSanitised() {
        assertEquals("AC_DC - Back In Black", safeFileBaseName("AC/DC - Back In Black"))
        assertEquals("What_", safeFileBaseName("What?"))
        assertEquals("track", safeFileBaseName("  ...  "))
        assertEquals(150, safeFileBaseName("x".repeat(400)).length)
    }

    @Test
    fun downloadFileNameIncludesArtist() {
        assertEquals("Coldplay - Yellow", downloadFileBaseName("Yellow", "Coldplay"))
        assertEquals("Yellow", downloadFileBaseName("Yellow", "Unknown artist"))
        assertEquals("Coldplay - Yellow (Live)", downloadFileBaseName("Coldplay - Yellow (Live)", "Coldplay"))
    }

    private fun List<String>.containsInOrder(first: String, second: String): Boolean =
        windowed(2).any { it[0] == first && it[1] == second }
}
