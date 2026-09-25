package com.imontalvodev.beatmybeat.ui.network

import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.shared.download.AudioCodec
import com.imontalvodev.beatmybeat.shared.download.SourceStream
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

/** Motivo de fallo de una descarga, para mostrar al usuario un mensaje que le sirva. */
enum class DownloadError {
    VideoNotFound,
    AgeRestricted,
    GeoBlocked,
    Private,
    PremiumOnly,
    BotCheck,
    Unavailable,
    NoAudio,
    Network,
    Incomplete,
    Convert,
    Save,
    Unknown,
}

class DownloadException(val error: DownloadError, cause: Throwable? = null) :
    Exception(error.name + (cause?.message?.let { ": $it" } ?: ""), cause)

/** Lo que el descargador necesita de un vídeo: duración y streams candidatos. */
data class ExtractedMedia(
    val durationSeconds: Long,
    val streams: List<SourceStream>,
)

object NewPipeStreamExtractor {

    private const val TAG = "NewPipeStream"

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader, Localization.DEFAULT, ContentCountry.DEFAULT)
            initialized = true
        }
    }

    /** @throws DownloadException con el motivo clasificado. */
    fun extract(videoId: String): ExtractedMedia {
        init()
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: Exception) {
            val error = classify(e)
            Logger.w(TAG, "extract($videoId) failed: $error (${e.javaClass.simpleName}: ${e.message})")
            throw DownloadException(error, e)
        }

        val audio = info.audioStreams.orEmpty().map { it.toSource() }
        val muxed = info.videoStreams.orEmpty().filter { !it.isVideoOnly }.map { it.toSource() }
        Logger.d(TAG, "streams audio=${audio.size} muxed=${muxed.size} duration=${info.duration}s")
        if (audio.isEmpty() && muxed.isEmpty()) throw DownloadException(DownloadError.NoAudio)
        return ExtractedMedia(durationSeconds = info.duration, streams = audio + muxed)
    }

    private fun classify(e: Throwable): DownloadError = when (e) {
        is AgeRestrictedContentException -> DownloadError.AgeRestricted
        is GeographicRestrictionException, is UnsupportedContentInCountryException -> DownloadError.GeoBlocked
        is PrivateContentException -> DownloadError.Private
        is PaidContentException, is YoutubeMusicPremiumContentException -> DownloadError.PremiumOnly
        is SignInConfirmNotBotException, is ReCaptchaException -> DownloadError.BotCheck
        is ContentNotAvailableException -> DownloadError.Unavailable
        is IOException -> DownloadError.Network
        else -> e.cause?.let { if (it !== e) classify(it) else null } ?: DownloadError.Unknown
    }

    private fun AudioStream.toSource(): SourceStream {
        val kbps = averageBitrate.takeIf { it > 0 } ?: (bitrate / 1000).coerceAtLeast(0)
        val codec = AudioCodec.fromCodecString(codec).takeIf { it != AudioCodec.UNKNOWN }
            ?: when (format) {
                MediaFormat.M4A, MediaFormat.MPEG_4 -> AudioCodec.AAC
                MediaFormat.WEBMA_OPUS, MediaFormat.OPUS -> AudioCodec.OPUS
                MediaFormat.MP3 -> AudioCodec.MP3
                else -> AudioCodec.UNKNOWN
            }
        return SourceStream(
            url = content,
            codec = codec,
            bitrateKbps = kbps,
            containerExtension = format?.suffix ?: "m4a",
            isVideo = false,
            isOriginalAudio = audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL,
            isDirectDownload = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
            contentLength = itagItem?.contentLength ?: -1L,
        )
    }

    /** Vídeo progresivo con audio: último recurso, ffmpeg extrae la pista de audio. */
    private fun VideoStream.toSource() = SourceStream(
        url = content,
        codec = if (format == MediaFormat.MPEG_4) AudioCodec.AAC else AudioCodec.UNKNOWN,
        bitrateKbps = (bitrate / 1000).coerceAtLeast(0),
        containerExtension = format?.suffix ?: "mp4",
        isVideo = true,
        isDirectDownload = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
        contentLength = itagItem?.contentLength ?: -1L,
    )
}

object OkHttpDownloader : org.schabi.newpipe.extractor.downloader.Downloader() {

    /** Same strategy as NewPipe app — YouTube rejects custom/bot user agents on HTML pages. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private val client = AppHttpClient.withTimeouts(
        connectSeconds = 20,
        readSeconds = 30,
        callSeconds = 45,
        followRedirects = true,
    )

    override fun execute(
        request: org.schabi.newpipe.extractor.downloader.Request,
    ): org.schabi.newpipe.extractor.downloader.Response {
        val okRequest = Request.Builder().apply {
            url(request.url())
            addHeader("User-Agent", USER_AGENT)
            request.headers().forEach { (key, values) ->
                removeHeader(key)
                values.forEach { value -> addHeader(key, value) }
            }
            when (request.httpMethod()) {
                "POST" -> post((request.dataToSend() ?: ByteArray(0)).toRequestBody(null))
                "HEAD" -> head()
                else -> get()
            }
        }.build()

        client.newCall(okRequest).execute().use { okResponse ->
            if (okResponse.code == 429) {
                // Igual que NewPipe: el 429 de YouTube es un captcha, no un fallo de red.
                throw ReCaptchaException("YouTube rate limit (429)", request.url())
            }
            val headers = okResponse.headers.names().associateWith { okResponse.headers.values(it) }
            return org.schabi.newpipe.extractor.downloader.Response(
                okResponse.code,
                okResponse.message,
                headers,
                okResponse.body.string(),
                okResponse.request.url.toString(),
            )
        }
    }
}
