package com.imontalvodev.beatmybeat.ui.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val averageBitrate: Int,
    val totalBytes: Long = -1L,
)

object NewPipeStreamExtractor {

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(
                OkHttpDownloader.instance,
                Localization.DEFAULT,
                ContentCountry.DEFAULT,
            )
            initialized = true
        }
    }

    fun extractBestAudioStream(videoId: String): AudioStreamInfo {
        init()
        val url = "https://www.youtube.com/watch?v=$videoId"
        android.util.Log.d("NewPipeStream", "Extracting: $url")

        val streamInfo = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: Exception) {
            throw Exception("No se pudo obtener el vídeo: ${e.message}", e)
        }

        val audioStreams = streamInfo.audioStreams.orEmpty()
        val videoStreams = streamInfo.videoStreams.orEmpty()
        val videoOnlyStreams = streamInfo.videoOnlyStreams.orEmpty()

        android.util.Log.d(
            "NewPipeStream",
            "Streams: audio=${audioStreams.size} muxedVideo=${videoStreams.count { !it.isVideoOnly }} " +
                "videoOnly=${videoOnlyStreams.size}",
        )

        pickFromAudioStreams(audioStreams)?.let { return it }

        pickFromMuxedVideo(videoStreams)?.let {
            android.util.Log.d("NewPipeStream", "Using muxed progressive stream (audio+video)")
            return it
        }

        // Algunos vídeos solo exponen audio en la lista de "video" sin flag videoOnly
        pickFromMuxedVideo(videoStreams.filter { it.content.isNotBlank() })?.let {
            android.util.Log.d("NewPipeStream", "Using fallback video stream")
            return it
        }

        throw Exception(
            "No se encontraron streams de audio para $videoId " +
                "(audio=0, video=${videoStreams.size}, videoOnly=${videoOnlyStreams.size})",
        )
    }

    private fun pickFromAudioStreams(streams: List<AudioStream>): AudioStreamInfo? {
        if (streams.isEmpty()) return null
        val best = streams
            .filter { it.content.isNotBlank() }
            .maxWithOrNull(
                compareBy<AudioStream>(
                    { if (it.format == MediaFormat.M4A || it.format == MediaFormat.MPEG_4) 1 else 0 },
                    { it.averageBitrate },
                ),
            ) ?: streams.firstOrNull { it.content.isNotBlank() } ?: return null

        android.util.Log.d(
            "NewPipeStream",
            "Best audio stream: format=${best.format} bitrate=${best.averageBitrate}",
        )
        return AudioStreamInfo(
            url = best.content,
            mimeType = mimeFromFormat(best.format, audio = true),
            averageBitrate = best.averageBitrate,
        )
    }

    /** Vídeo progresivo con pista de audio embebida (FFmpeg extrae el audio después). */
    private fun pickFromMuxedVideo(streams: List<VideoStream>): AudioStreamInfo? {
        val muxed = streams.filter { !it.isVideoOnly && it.content.isNotBlank() }
        if (muxed.isEmpty()) return null

        val best = muxed.maxWithOrNull(
            compareBy<VideoStream>(
                { if (it.format == MediaFormat.M4A || it.format == MediaFormat.MPEG_4) 1 else 0 },
                { it.bitrate },
            ),
        ) ?: return null

        android.util.Log.d(
            "NewPipeStream",
            "Best muxed stream: format=${best.format} bitrate=${best.bitrate} res=${best.resolution}",
        )
        return AudioStreamInfo(
            url = best.content,
            mimeType = mimeFromFormat(best.format, audio = false),
            averageBitrate = best.bitrate,
        )
    }

    private fun mimeFromFormat(format: MediaFormat?, audio: Boolean): String = when (format) {
        null -> if (audio) "audio/mp4" else "video/mp4"
        MediaFormat.M4A, MediaFormat.MPEG_4 ->
            if (audio) "audio/mp4" else "video/mp4"
        MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS ->
            if (audio) "audio/webm" else "video/webm"
        MediaFormat.MP3 -> "audio/mpeg"
        else -> if (audio) "audio/mp4" else "video/mp4"
    }
}

object OkHttpDownloader : org.schabi.newpipe.extractor.downloader.Downloader() {

    val instance: OkHttpDownloader = this

    /** Same strategy as NewPipe app — YouTube rejects custom/bot user agents on HTML pages. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val okRequest = Request.Builder().apply {
            url(request.url())
            addHeader("User-Agent", USER_AGENT)
            request.headers().forEach { (key, values) ->
                removeHeader(key)
                values.forEach { value -> addHeader(key, value) }
            }
            when (request.httpMethod()) {
                "POST" -> {
                    val body = request.dataToSend() ?: ByteArray(0)
                    post(body.toRequestBody(null))
                }
                "HEAD" -> head()
                else -> get()
            }
        }.build()

        val okResponse: Response = client.newCall(okRequest).execute()
        val responseBody = okResponse.body?.string() ?: ""
        if (okResponse.code == 429) {
            android.util.Log.w("NewPipeStream", "YouTube rate limit (429) for ${request.url()}")
        }
        val headers = mutableMapOf<String, List<String>>()
        okResponse.headers.names().forEach { name ->
            headers[name] = okResponse.headers.values(name)
        }

        return org.schabi.newpipe.extractor.downloader.Response(
            okResponse.code,
            okResponse.message,
            headers,
            responseBody,
            okResponse.request.url.toString(),
        )
    }
}
