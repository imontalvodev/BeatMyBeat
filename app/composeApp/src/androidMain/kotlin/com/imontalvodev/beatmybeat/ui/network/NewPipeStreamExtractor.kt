package com.imontalvodev.beatmybeat.ui.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
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
            NewPipe.init(OkHttpDownloader.instance)
            initialized = true
        }
    }

    fun extractBestAudioStream(videoId: String): AudioStreamInfo {
        init()
        val url = "https://www.youtube.com/watch?v=$videoId"
        android.util.Log.d("NewPipeStream", "Extracting: $url")

        val extractor: StreamExtractor = try {
            ServiceList.YouTube.getStreamExtractor(url)
        } catch (e: Exception) {
            throw Exception("No se pudo crear el extractor: ${e.message}", e)
        }

        try {
            extractor.fetchPage()
        } catch (e: Exception) {
            throw Exception("Error al obtener la página del vídeo: ${e.message}", e)
        }

        android.util.Log.d("NewPipeStream", "Page fetched, getting audio streams")

        val audioStreams: List<AudioStream> = try {
            extractor.audioStreams
        } catch (e: Exception) {
            throw Exception("Error al obtener streams de audio: ${e.message}", e)
        }

        android.util.Log.d("NewPipeStream", "Audio streams found: ${audioStreams.size}")
        if (audioStreams.isEmpty()) throw Exception("No se encontraron streams de audio para $videoId")

        val best = audioStreams
            .filter { it.content.isNotBlank() }
            .maxWithOrNull(
                compareBy(
                    { if (it.format == MediaFormat.M4A || it.format == MediaFormat.MPEG_4) 1 else 0 },
                    { it.averageBitrate }
                )
            ) ?: audioStreams.first()

        val mimeType = when (best.format) {
            MediaFormat.M4A, MediaFormat.MPEG_4 -> "audio/mp4"
            MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> "audio/webm"
            MediaFormat.MP3 -> "audio/mpeg"
            else -> "audio/mp4"
        }

        android.util.Log.d("NewPipeStream", "Best stream: format=${best.format} bitrate=${best.averageBitrate} mime=$mimeType")

        return AudioStreamInfo(
            url = best.content,
            mimeType = mimeType,
            averageBitrate = best.averageBitrate,
        )
    }
}

object OkHttpDownloader : Downloader() {

    val instance: OkHttpDownloader = this

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val okRequest = Request.Builder().apply {
            url(request.url())
            request.headers().forEach { (key, values) ->
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
