package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import android.util.Base64
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.download.DownloadProgressUpdate
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.shared.download.ChunkResponse
import com.imontalvodev.beatmybeat.shared.download.DownloadFormat
import com.imontalvodev.beatmybeat.shared.download.RangedDownloadResult
import com.imontalvodev.beatmybeat.shared.download.SourceStream
import com.imontalvodev.beatmybeat.shared.download.TrackTags
import com.imontalvodev.beatmybeat.shared.download.buildFfmpegArguments
import com.imontalvodev.beatmybeat.shared.download.chooseSourceStream
import com.imontalvodev.beatmybeat.shared.download.downloadInRanges
import com.imontalvodev.beatmybeat.shared.download.downloadFileBaseName
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

object AudioDownloader {

    private const val TAG = "AudioDownloader"

    /** Los googlevideo de clientes móviles esperan un UA de la app de YouTube. */
    private const val STREAM_USER_AGENT = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"

    data class DownloadResult(
        val success: Boolean,
        val fileName: String?,
        val error: DownloadError? = null,
    )

    private val downloadClient: OkHttpClient by lazy {
        AppHttpClient.withTimeouts(connectSeconds = 20, readSeconds = 30)
    }

    suspend fun downloadAutoToAppMusic(
        context: Context,
        title: String,
        artist: String,
        album: String,
        format: DownloadFormat = DownloadFormat.MP3,
        videoId: String = "",
        thumbnailUrl: String = "",
        onProgress: ((DownloadProgressUpdate) -> Unit)? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        fun report(phase: String, fraction: Float? = null) {
            onProgress?.invoke(DownloadProgressUpdate(phase = phase, fileFraction = fraction))
        }

        val safeTitle = title.trim()
        val safeArtist = artist.trim()
        val safeAlbum = album.trim()
        val workDir = File(context.cacheDir, ".music_tmp").apply { mkdirs() }
        val baseName = downloadFileBaseName(safeTitle, safeArtist)
        val scratch = mutableListOf<File>()

        BeatMyBeatNotification.showDownloadInProgress(
            context,
            if (safeTitle.isNotBlank()) context.getString(R.string.download_notification_downloading, safeTitle)
            else context.getString(R.string.download_song_title),
            safeArtist,
        )

        try {
            report(context.getString(R.string.download_phase_searching))
            val resolvedVideoId = videoId.takeIf { it.length == 11 } ?: findVideoId(safeTitle, safeArtist, safeAlbum)

            report(context.getString(R.string.download_phase_resolving))
            val media = NewPipeStreamExtractor.extract(resolvedVideoId)
            val stream = chooseSourceStream(media.streams, format)
                ?: throw DownloadException(DownloadError.NoAudio)
            Logger.d(TAG, "source codec=${stream.codec} ${stream.bitrateKbps}kbps video=${stream.isVideo} → $format")

            val sourceFile = File(workDir, "$baseName.source.${stream.containerExtension}").also { scratch += it }
            report(context.getString(R.string.download_phase_downloading, 0), 0f)
            downloadStream(stream, sourceFile) { written, total ->
                if (total > 0) {
                    val pct = (written * 100 / total).toInt().coerceIn(0, 99)
                    report(context.getString(R.string.download_phase_downloading, pct), pct / 100f)
                }
            }

            coroutineContext.ensureActive()
            report(context.getString(R.string.download_phase_converting, format.label), 1f)
            val artwork = fetchArtwork(resolvedVideoId, thumbnailUrl)
            val coverFile = artwork?.let { bytes ->
                File(workDir, "$baseName.cover.jpg").also { it.writeBytes(bytes); scratch += it }
            }
            val outFile = File(workDir, "$baseName.${format.extension}").also { scratch += it }
            val tags = TrackTags(
                title = safeTitle.ifBlank { "Track" },
                artist = safeArtist.ifBlank { "Unknown artist" },
                album = safeAlbum.ifBlank { safeTitle.ifBlank { "BeatMyBeat" } },
            )
            convert(sourceFile, coverFile, outFile, stream, format, tags)
            sourceFile.delete()

            val metaFile = writeSidecar(outFile, tags, thumbnailUrlFor(resolvedVideoId), artwork)
                ?.also { scratch += it }

            coroutineContext.ensureActive()
            report(context.getString(R.string.download_phase_saving), 1f)
            val savedName = StorageSettings.saveAudioFromFile(
                context = context,
                source = outFile,
                displayName = outFile.name,
                title = safeTitle,
                artist = safeArtist,
                album = safeAlbum,
            ) ?: throw DownloadException(DownloadError.Save)
            metaFile?.let { runCatching { StorageSettings.saveTextSidecar(context, it.name, it.readText()) } }
            ArtworkCache.clear()

            if (isKnown(safeTitle) && isKnown(safeArtist)) {
                report(context.getString(R.string.download_phase_lyrics), 1f)
                runCatching {
                    LyricsFetchCoordinator.fetch(
                        context,
                        LyricsFetcher.Request(
                            title = safeTitle,
                            artist = safeArtist,
                            album = safeAlbum,
                            // Con la duración LRCLIB puede usar sus endpoints exactos (get/get-cached),
                            // que aciertan mucho más que la búsqueda libre.
                            durationMs = media.durationSeconds * 1000L,
                        ),
                    )
                }.onFailure { if (it is CancellationException) throw it }
            }

            BeatMyBeatNotification.showDownloadCompleted(
                context,
                context.getString(R.string.download_completed_title),
                savedName,
            )
            DownloadResult(success = true, fileName = savedName)
        } catch (e: CancellationException) {
            BeatMyBeatNotification.cancelDownloadNotification(context)
            throw e
        } catch (e: Throwable) {
            val error = (e as? DownloadException)?.error ?: DownloadError.Unknown
            Logger.e(TAG, "download failed: $error", e)
            BeatMyBeatNotification.showDownloadFailed(
                context,
                context.getString(R.string.download_failed_title),
                errorMessage(context, error, format),
            )
            DownloadResult(success = false, fileName = null, error = error)
        } finally {
            scratch.forEach { it.delete() }
        }
    }

    fun errorMessage(context: Context, error: DownloadError, format: DownloadFormat): String = when (error) {
        DownloadError.VideoNotFound -> context.getString(R.string.download_error_video_not_found)
        DownloadError.AgeRestricted -> context.getString(R.string.download_error_age_restricted)
        DownloadError.GeoBlocked -> context.getString(R.string.download_error_geo_blocked)
        DownloadError.Private -> context.getString(R.string.download_error_private)
        DownloadError.PremiumOnly -> context.getString(R.string.download_error_premium)
        DownloadError.BotCheck -> context.getString(R.string.download_error_bot_check)
        DownloadError.Unavailable -> context.getString(R.string.download_error_unavailable)
        DownloadError.NoAudio -> context.getString(R.string.download_error_no_audio)
        DownloadError.Network -> context.getString(R.string.download_error_network)
        DownloadError.Incomplete -> context.getString(R.string.download_error_incomplete)
        DownloadError.Convert -> context.getString(R.string.download_error_convert, format.label)
        DownloadError.Save -> context.getString(R.string.download_error_save)
        DownloadError.Unknown -> context.getString(R.string.download_error_unknown)
    }

    private fun isKnown(s: String) =
        s.isNotBlank() && !s.equals("unknown", ignoreCase = true) && !s.equals("unknown artist", ignoreCase = true)

    private suspend fun findVideoId(title: String, artist: String, album: String): String {
        val query = listOf(title, artist, album).filter { it.isNotBlank() }.joinToString(" ")
        val first = try {
            YouTubeSearchClient.search(query, limit = 1).firstOrNull()
        } catch (e: IOException) {
            throw DownloadException(DownloadError.Network, e)
        }
        return first?.videoId ?: throw DownloadException(DownloadError.VideoNotFound)
    }

    private suspend fun downloadStream(
        stream: SourceStream,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val known = stream.contentLength.takeIf { it > 0 } ?: probeLength(stream.url)
        val result = FileOutputStream(target).use { out ->
            downloadInRanges(
                knownLength = known,
                fetchChunk = { start, end -> fetchChunk(stream.url, start, end) },
                write = out::write,
                onProgress = onProgress,
                backoff = { attempt -> delay(500L * attempt * attempt) },
                beforeChunk = { coroutineContext.ensureActive() },
            )
        }
        when (result) {
            is RangedDownloadResult.Completed -> {
                Logger.d(TAG, "downloaded ${result.bytesWritten}B")
                if (result.bytesWritten == 0L) throw DownloadException(DownloadError.Incomplete)
            }
            is RangedDownloadResult.Incomplete -> {
                Logger.w(TAG, "incomplete ${result.bytesWritten}/${result.expected}: ${result.reason}")
                throw DownloadException(DownloadError.Incomplete)
            }
        }
    }

    private fun streamRequest(url: String) = Request.Builder().url(url)
        .header("User-Agent", STREAM_USER_AGENT)
        .header("Accept-Encoding", "identity")

    private fun probeLength(url: String): Long = runCatching {
        downloadClient.newCall(streamRequest(url).header("Range", "bytes=0-0").get().build())
            .execute().use { r ->
                r.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: -1L
            }
    }.getOrDefault(-1L)

    private fun fetchChunk(url: String, start: Long, end: Long): ChunkResponse = try {
        downloadClient.newCall(streamRequest(url).header("Range", "bytes=$start-$end").get().build())
            .execute().use { r ->
                when {
                    r.code == 206 -> ChunkResponse.Partial(
                        bytes = r.body.bytes(),
                        totalLength = r.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: -1L,
                    )
                    r.code == 200 -> ChunkResponse.Full(r.body.bytes())
                    r.code == 429 || r.code >= 500 -> ChunkResponse.RetryableError("HTTP ${r.code}")
                    else -> ChunkResponse.FatalError("HTTP ${r.code}")
                }
            }
    } catch (e: IOException) {
        ChunkResponse.RetryableError(e.javaClass.simpleName)
    }

    private fun convert(
        source: File,
        cover: File?,
        output: File,
        stream: SourceStream,
        format: DownloadFormat,
        tags: TrackTags,
    ) {
        output.delete()
        val args = buildFfmpegArguments(
            inputPath = source.absolutePath,
            coverPath = cover?.absolutePath,
            outputPath = output.absolutePath,
            source = stream.codec,
            target = format,
            tags = tags,
        )
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() == 0L) {
            Logger.e(TAG, "ffmpeg rc=${session.returnCode?.value}: ${session.failStackTrace ?: session.output?.takeLast(600)}")
            throw DownloadException(DownloadError.Convert)
        }
    }

    private fun thumbnailUrlFor(videoId: String) = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

    /** maxresdefault no existe en todos los vídeos: se baja de resolución hasta encontrar una. */
    private fun fetchArtwork(videoId: String, fallbackUrl: String): ByteArray? {
        val urls = listOf("maxresdefault", "hqdefault", "mqdefault")
            .map { "https://i.ytimg.com/vi/$videoId/$it.jpg" } + listOfNotNull(fallbackUrl.takeIf { it.isNotBlank() })
        return urls.firstNotNullOfOrNull { url ->
            runCatching {
                downloadClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.bytes().takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
        }
    }

    /**
     * Escribe el `.meta.json` que lee el escáner de la biblioteca: título, artista y carátula en
     * base64, fuente de verdad aunque el contenedor no admita etiquetas (AAC, WAV, OGG).
     */
    private fun writeSidecar(file: File, tags: TrackTags, thumbnailUrl: String, artwork: ByteArray?): File? {
        val metaFile = File(file.parentFile, "${file.nameWithoutExtension}.meta.json")
        runCatching {
            JSONObject().apply {
                put("title", tags.title)
                put("artist", tags.artist)
                put("album", tags.album)
                put("thumbnailUrl", thumbnailUrl)
                if (artwork != null) put("artworkBase64", Base64.encodeToString(artwork, Base64.NO_WRAP))
            }.also { metaFile.writeText(it.toString()) }
        }.onFailure { Logger.w(TAG, "meta.json failed: ${it.message}") }

        return metaFile.takeIf { it.exists() }
    }
}
