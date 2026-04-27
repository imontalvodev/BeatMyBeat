package com.imontalvodev.beatmybeat.ui.network

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

object AudioDownloader {
    enum class DownloadFormat(val id: String, val extension: String, val label: String) {
        MP3("mp3", "mp3", "MP3"),
        M4A("m4a", "m4a", "M4A"),
        AAC("aac", "aac", "AAC"),
        OGG("ogg", "ogg", "OGG"),
        FLAC("flac", "flac", "FLAC"),
        WAV("wav", "wav", "WAV");

        companion object {
            fun fromId(raw: String?): DownloadFormat =
                entries.firstOrNull { it.id.equals(raw.orEmpty(), ignoreCase = true) } ?: MP3
        }
    }

    data class DownloadResult(
        val success: Boolean,
        val fileName: String?,
        val error: String? = null,
    )

    data class ZipDownloadResult(
        val success: Boolean,
        val extractedFiles: Int,
        val error: String? = null,
    )

    suspend fun downloadAutoToAppMusic(
        context: Context,
        middlewareBaseUrl: String,
        title: String,
        artist: String,
        album: String,
        format: DownloadFormat = DownloadFormat.MP3,
        imageUrl: String = "",
        videoId: String = "",
        thumbnailUrl: String = "",
        onPhaseUpdate: ((phase: String) -> Unit)? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val safeTitle = title.trim()
        val safeArtist = artist.trim()
        BeatMyBeatNotification.showDownloadInProgress(
            context,
            if (safeTitle.isNotBlank()) "Descargando: $safeTitle" else "Descargando canción",
            safeArtist,
        )
        try {
            // --- Paso 1: resolver videoId y thumbnail si no se proporcionaron ---
            onPhaseUpdate?.invoke("Buscando vídeo…")
            var resolvedThumbnail = thumbnailUrl
            val resolvedVideoId = if (videoId.length == 11) {
                videoId
            } else {
                val query = listOf(safeTitle, safeArtist, album.trim()).filter { it.isNotBlank() }.joinToString(" ")
                val results = YouTubeSearchClient.search(query, limit = 1)
                val first = results.firstOrNull() ?: run {
                    BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "No se encontró el vídeo en YouTube.")
                    return@withContext DownloadResult(false, null, "VideoNotFound")
                }
                if (resolvedThumbnail.isBlank()) resolvedThumbnail = first.thumbnailUrl
                first.videoId
            }
            // Construir siempre la URL de maxresdefault basada en el videoId real
            resolvedThumbnail = "https://i.ytimg.com/vi/$resolvedVideoId/maxresdefault.jpg"

            android.util.Log.d("NewPipeStream", "title='$safeTitle' artist='$safeArtist' thumbnail='$resolvedThumbnail'")

            // --- Paso 2: extraer URL de stream con NewPipe ---
            onPhaseUpdate?.invoke("Obteniendo enlace de audio…")
            val streamInfo = try {
                NewPipeStreamExtractor.extractBestAudioStream(resolvedVideoId)
            } catch (e: Exception) {
                android.util.Log.e("NewPipeStream", "extractBestAudioStream failed: ${e.javaClass.simpleName}: ${e.message}", e)
                BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "No se pudo completar la descarga. Inténtalo de nuevo.")
                return@withContext DownloadResult(false, null, e.message)
            }

            // --- Paso 3: descargar por rangos para evitar bloqueo con streams chunked ---
            onPhaseUpdate?.invoke("Descargando audio…")
            val sourceExt = when {
                streamInfo.mimeType.contains("mp4") || streamInfo.mimeType.contains("m4a") -> "m4a"
                streamInfo.mimeType.contains("webm") || streamInfo.mimeType.contains("opus") -> "webm"
                else -> "m4a"
            }
            val baseName = safeTitle.ifBlank { "track" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)
            val tempFileName = "${baseName}.source.$sourceExt"
            val fileName = "${baseName}.${format.extension}"
            val dir = File(context.cacheDir, ".music_tmp").also { if (!it.exists()) it.mkdirs() }
            val tempFile = File(dir, tempFileName)
            val outFile = File(dir, fileName)
            val masterMp3 = File(dir, "${baseName}.master.mp3")

            android.util.Log.d("NewPipeStream", "Downloading: ${streamInfo.url.take(100)} mimeType=${streamInfo.mimeType}")

            val downloadClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            // Obtener tamaño total
            val totalBytes = try {
                downloadClient.newCall(
                    Request.Builder().url(streamInfo.url)
                        .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                        .header("Range", "bytes=0-0").get().build()
                ).execute().use { r ->
                    r.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
                        ?: r.body?.contentLength() ?: -1L
                }
            } catch (e: Exception) { -1L }

            android.util.Log.d("NewPipeStream", "Total bytes: $totalBytes, writing to $fileName")

            val chunkSize = 1_048_576L
            var offset = 0L
            var totalWritten = 0L

            FileOutputStream(tempFile).use { out ->
                while (totalBytes < 0 || offset < totalBytes) {
                    val end = if (totalBytes > 0) minOf(offset + chunkSize - 1, totalBytes - 1) else offset + chunkSize - 1
                    val chunkResp = downloadClient.newCall(
                        Request.Builder().url(streamInfo.url)
                            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                            .header("Accept-Encoding", "identity")
                            .header("Range", "bytes=$offset-$end")
                            .get().build()
                    ).execute()

                    val chunkBody = chunkResp.body
                    if (!chunkResp.isSuccessful || chunkBody == null) {
                        android.util.Log.e("NewPipeStream", "Chunk HTTP ${chunkResp.code}")
                        chunkResp.close()
                        break
                    }
                    val bytes = chunkBody.bytes()
                    chunkResp.close()
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                    totalWritten += bytes.size
                    offset += bytes.size
                    if (totalBytes > 0) {
                        val pct = (totalWritten * 100L / totalBytes).toInt().coerceIn(0, 99)
                        onPhaseUpdate?.invoke("Descargando audio… $pct%")
                    }
                    if (bytes.size < chunkSize) break
                }
                out.flush()
            }

            android.util.Log.d("NewPipeStream", "Download complete: ${totalWritten}B")

            if (totalWritten == 0L) {
                tempFile.delete()
                BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "No se pudo completar la descarga. Inténtalo de nuevo.")
                return@withContext DownloadResult(false, null, "ZeroBytes")
            }

            // --- Paso 4: crear MP3 master con metadata/carátula ---
            onPhaseUpdate?.invoke("Procesando metadatos…")
            outFile.delete()
            masterMp3.delete()
            val artworkBytes = fetchArtworkBytes(resolvedThumbnail, downloadClient)
            val artworkFile = artworkBytes?.let {
                File(dir, "${baseName}.cover.jpg").also { f -> f.writeBytes(it) }
            }
            val escapedTitle = ffmpegEscape(safeTitle.ifBlank { "Track" })
            val escapedArtist = ffmpegEscape(safeArtist.ifBlank { "Unknown artist" })
            val escapedAlbum = ffmpegEscape(album.trim().ifBlank { safeTitle.ifBlank { "BeatMyBeat" } })
            val masterCmd = buildMp3MasterCommand(
                inputPath = tempFile.absolutePath,
                outputPath = masterMp3.absolutePath,
                artworkPath = artworkFile?.takeIf { it.exists() }?.absolutePath,
                escapedTitle = escapedTitle,
                escapedArtist = escapedArtist,
                escapedAlbum = escapedAlbum,
            )
            val masterSession = FFmpegKit.execute(masterCmd)
            val masterRc = masterSession.returnCode
            if (!ReturnCode.isSuccess(masterRc) || !masterMp3.exists() || masterMp3.length() <= 0L) {
                tempFile.delete()
                artworkFile?.delete()
                outFile.delete()
                masterMp3.delete()
                BeatMyBeatNotification.showDownloadFailed(
                    context,
                    "Error en la descarga",
                    "No se pudo crear el archivo master de audio.",
                )
                return@withContext DownloadResult(false, null, "FfmpegMasterFailed:${masterRc?.value}")
            }
            // Reutilizamos la lógica existente para sidecar/artwork metadata.
            runCatching {
                embedMetadata(masterMp3, safeTitle, safeArtist, album.trim(), resolvedThumbnail, downloadClient)
            }.onFailure { android.util.Log.w("NewPipeStream", "embedMetadata failed: ${it.message}") }

            // --- Paso 5: si no es MP3, convertir desde master al formato final ---
            onPhaseUpdate?.invoke("Convirtiendo a ${format.label}…")
            if (format == DownloadFormat.MP3) {
                masterMp3.copyTo(outFile, overwrite = true)
            } else {
                val finalCmd = buildFormatFromMasterCommand(
                    masterPath = masterMp3.absolutePath,
                    outputPath = outFile.absolutePath,
                    format = format,
                    artworkPath = artworkFile?.takeIf { it.exists() }?.absolutePath,
                    escapedTitle = escapedTitle,
                    escapedArtist = escapedArtist,
                    escapedAlbum = escapedAlbum,
                )
                val finalSession = FFmpegKit.execute(finalCmd)
                val finalRc = finalSession.returnCode
                if (!ReturnCode.isSuccess(finalRc) || !outFile.exists() || outFile.length() <= 0L) {
                    tempFile.delete()
                    artworkFile?.delete()
                    outFile.delete()
                    masterMp3.delete()
                    BeatMyBeatNotification.showDownloadFailed(
                        context,
                        "Error en la descarga",
                        "No se pudo convertir el audio a ${format.label}.",
                    )
                    return@withContext DownloadResult(false, null, "FfmpegFinalFailed:${finalRc?.value}")
                }
                // Renombrar sidecar del master para que acompañe al archivo final.
                val masterMeta = File(masterMp3.parentFile, "${masterMp3.nameWithoutExtension}.meta.json")
                if (masterMeta.exists()) {
                    val finalMeta = File(outFile.parentFile, "${outFile.nameWithoutExtension}.meta.json")
                    runCatching {
                        if (finalMeta.exists()) finalMeta.delete()
                        masterMeta.copyTo(finalMeta, overwrite = true)
                    }
                }
            }
            tempFile.delete()
            artworkFile?.delete()
            masterMp3.delete()

            val savedName = StorageSettings.saveAudioFromFile(
                context = context,
                source = outFile,
                displayName = outFile.name,
                title = safeTitle,
                artist = safeArtist,
                album = album.trim(),
            )
            if (savedName == null) {
                tempFile.delete()
                outFile.delete()
                BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "No se pudo guardar el archivo en la carpeta configurada.")
                return@withContext DownloadResult(false, null, "SaveFailed")
            }
            val metaFile = File(outFile.parentFile, "${outFile.nameWithoutExtension}.meta.json")
            if (metaFile.exists()) {
                runCatching {
                    StorageSettings.saveTextSidecar(context, metaFile.name, metaFile.readText())
                }
                metaFile.delete()
            }
            outFile.delete()

            ArtworkCache.clear()

            // --- Paso 6: letras desde lyrics.ovh directo ---
            val isUnknown = { s: String -> s.isBlank() || s.equals("unknown", ignoreCase = true) || s.equals("unknown artist", ignoreCase = true) }
            if (!isUnknown(safeTitle) && !isUnknown(safeArtist)) {
                runCatching {
                    val lyr = MiddlewareApi.fetchLyricsDirect(safeTitle, safeArtist)
                    if (lyr.success && lyr.lyrics.isNotBlank()) LyricsCache.put(context, safeTitle, safeArtist, lyr.lyrics)
                }
            }

            BeatMyBeatNotification.showDownloadCompleted(context, "Descarga completada", savedName)
            return@withContext DownloadResult(true, savedName, null)

        } catch (e: Exception) {
            android.util.Log.e("NewPipeStream", "Download exception: ${e.javaClass.simpleName}: ${e.message}", e)
            BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "No se pudo completar la descarga. Inténtalo de nuevo.")
            return@withContext DownloadResult(false, null, "${e.javaClass.simpleName}: ${e.message}")
        } catch (t: Throwable) {
            // Captura también Error (NoSuchMethodError, OutOfMemoryError, etc.)
            android.util.Log.e("NewPipeStream", "Download fatal: ${t.javaClass.simpleName}: ${t.message}", t)
            BeatMyBeatNotification.showDownloadFailed(context, "Error en la descarga", "Error crítico: ${t.javaClass.simpleName}")
            return@withContext DownloadResult(false, null, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    suspend fun downloadYoutubeAlbumZipToAppMusic(
        context: Context,
        middlewareBaseUrl: String,
        playlistUrl: String,
    ): ZipDownloadResult = withContext(Dispatchers.IO) {
        val notifTitle = "Descargando playlist de YouTube"
        BeatMyBeatNotification.showDownloadInProgress(
            context = context,
            title = notifTitle,
            subtitle = playlistUrl.take(40),
        )
        try {
            fun buildUrl(baseUrl: String) =
                "${baseUrl.trimEnd('/')}/api/download-youtube-album"
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.apply { addQueryParameter("playlistUrl", playlistUrl) }
                    ?.build()

            val baseCandidates = getMiddlewareBaseCandidates(middlewareBaseUrl)

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .callTimeout(10, TimeUnit.MINUTES)
                .build()

            fun extractZipFromResponse(res: Response): ZipDownloadResult {
                val contentType = (res.header("Content-Type") ?: "").lowercase()
                if (contentType.contains("application/json") || contentType.contains("text/html")) {
                    val body = res.body?.string().orEmpty()
                    return ZipDownloadResult(false, 0, body.ifBlank { "ServerErrorContentType:$contentType" })
                }

                val body = res.body ?: return ZipDownloadResult(false, 0, "EmptyBody")
                var extracted = 0
                ZipInputStream(body.byteStream()).use { zis ->
                    var entry = zis.nextEntry
                    val buffer = ByteArray(8 * 1024)
                    val allowedExt = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "webm")
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val rawName = File(entry.name).name
                            val ext = rawName.substringAfterLast('.', "").lowercase()
                            if (ext in allowedExt) {
                                val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                val tmp = File.createTempFile("zip_track_", ".$ext", context.cacheDir)
                                FileOutputStream(tmp).use { output ->
                                    var read = zis.read(buffer)
                                    while (read > 0) {
                                        output.write(buffer, 0, read)
                                        read = zis.read(buffer)
                                    }
                                    output.flush()
                                }
                                val saved = StorageSettings.saveAudioFromFile(context, tmp, safeName)
                                tmp.delete()
                                if (saved != null) extracted++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                if (extracted <= 0) return ZipDownloadResult(false, 0, "ZipWithoutAudio")
                // El ZIP puede cambiar las carátulas embebidas de los MP3 existentes
                // (mismos nombres -> misma id -> cache vieja).
                ArtworkCache.clear()
                return ZipDownloadResult(true, extracted, null)
            }

            var lastError: String = "No se pudo completar la descarga. Inténtalo de nuevo."

            for (base in baseCandidates) {
                val url = buildUrl(base) ?: continue
                val fallbackBackendUrl = buildUrl(guessPythonBackendBaseUrl(base))
                try {
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                        if (res.isSuccessful) {
                            val out = extractZipFromResponse(res)
                            if (out.success) {
                                BeatMyBeatNotification.showDownloadCompleted(
                                    context = context,
                                    title = "Playlist descargada",
                                    subtitle = "${out.extractedFiles} pistas",
                                )
                            } else {
                                BeatMyBeatNotification.showDownloadFailed(
                                    context = context,
                                    title = "Error en la descarga",
                                    subtitle = out.error ?: "Reintenta más tarde",
                                )
                            }
                            return@withContext out
                        }

                        val firstContentType = (res.header("Content-Type") ?: "").lowercase()
                        val firstBody = res.body?.string().orEmpty()
                        lastError = if (firstBody.isNotBlank()) "HTTP_${res.code}: $firstBody" else "HTTP_${res.code}"
                        val isHtml404 = res.code == 404 && firstContentType.contains("text/html")

                        // Fallback pragmático: middleware sin ruta nueva o no reiniciado.
                        if (isHtml404 && fallbackBackendUrl != null) {
                            client.newCall(Request.Builder().url(fallbackBackendUrl).get().build()).execute().use { res2 ->
                                if (!res2.isSuccessful) {
                                    val body2 = res2.body?.string().orEmpty()
                                    lastError = if (body2.isNotBlank()) "HTTP_${res2.code}: $body2" else "HTTP_${res2.code}"
                                    return@use
                                }
                                val out = extractZipFromResponse(res2)
                                if (out.success) {
                                    BeatMyBeatNotification.showDownloadCompleted(
                                        context = context,
                                        title = "Playlist descargada",
                                        subtitle = "${out.extractedFiles} pistas",
                                    )
                                } else {
                                    BeatMyBeatNotification.showDownloadFailed(
                                        context = context,
                                        title = "Error en la descarga",
                                        subtitle = out.error ?: "Reintenta más tarde",
                                    )
                                }
                                return@withContext out
                            }
                        }
                    }
                } catch (_: Exception) {
                    lastError = "No se pudo conectar al servidor (${base.removePrefix("http://").removePrefix("https://")})."
                }
            }

            BeatMyBeatNotification.showDownloadFailed(
                context = context,
                title = "Error en la descarga",
                subtitle = lastError,
            )
            return@withContext ZipDownloadResult(false, 0, lastError)
        } finally {
            // no-op: dejamos que la notificación en curso sea reemplazada por completada/error.
        }
    }

    private fun guessPythonBackendBaseUrl(middlewareBaseUrl: String): String {
        val m = middlewareBaseUrl.trimEnd('/')
        return when {
            m.endsWith(":3000") -> m.removeSuffix(":3000") + ":4000"
            else -> "http://10.0.2.2:4000"
        }
    }

    private fun handleResponse(context: Context, res: Response, title: String): DownloadResult {
        if (!res.isSuccessful) {
            return DownloadResult(false, null, "HTTP_${res.code}")
        }

        val contentType = res.header("Content-Type") ?: ""
        if (contentType.contains("application/json")) {
            return DownloadResult(false, null, "ServerErrorJson")
        }

        val body = res.body ?: return DownloadResult(false, null, "EmptyBody")
        val inputStream = body.byteStream()

        val fileNameFromHeader =
            res.header("Content-Disposition")
                ?.substringAfter("filename=\"")
                ?.substringBeforeLast("\"")
        val safeName = fileNameFromHeader?.takeIf { it.isNotBlank() }
            ?: (title.ifBlank { "track" } + ".mp3")
        val saved = StorageSettings.saveRawAudioFromStream(
            context = context,
            input = inputStream,
            displayName = safeName,
            mimeType = "audio/mpeg",
            title = title,
        )
        if (!saved) return DownloadResult(false, null, "SaveFailed")

        // Si el usuario descarga otro álbum/canciones, las IDs pueden repetirse
        // y ArtworkCache puede devolver una carátula vieja.
        ArtworkCache.clear()
        return DownloadResult(true, safeName, null)
    }

    private fun embedMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        thumbnailUrl: String,
        httpClient: OkHttpClient,
    ) {
        android.util.Log.d("NewPipeStream", "embedMetadata: title='$title' artist='$artist' ext=${file.extension}")

        // 1. Descargar la carátula (maxresdefault → hqdefault → mqdefault)
        val artworkBytes: ByteArray? = if (thumbnailUrl.isNotBlank()) {
            runCatching {
                val urls = listOf(
                    thumbnailUrl,
                    thumbnailUrl.replace("maxresdefault", "hqdefault"),
                    thumbnailUrl.replace("maxresdefault", "mqdefault"),
                )
                var bytes: ByteArray? = null
                for (url in urls) {
                    runCatching {
                        val resp = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
                        if (resp.isSuccessful) {
                            val b = resp.body?.bytes()
                            resp.close()
                            if (b != null && b.isNotEmpty()) bytes = b
                        } else { resp.close() }
                    }
                    if (bytes != null) break
                }
                android.util.Log.d("NewPipeStream", "Artwork: ${bytes?.size ?: 0} bytes")
                bytes
            }.getOrNull()
        } else null

        // 2. Guardar SIEMPRE el .meta.json — fuente de verdad para el scanner
        val metaFile = File(file.parentFile, "${file.nameWithoutExtension}.meta.json")
        runCatching {
            org.json.JSONObject().apply {
                put("title", title)
                put("artist", artist)
                put("album", album.ifBlank { title })
                put("thumbnailUrl", thumbnailUrl)
                if (artworkBytes != null) {
                    put("artworkBase64", android.util.Base64.encodeToString(artworkBytes, android.util.Base64.NO_WRAP))
                }
            }.also { metaFile.writeText(it.toString()) }
            android.util.Log.d("NewPipeStream", "meta.json saved: ${metaFile.name}")
        }.onFailure { android.util.Log.w("NewPipeStream", "meta.json failed: ${it.message}") }

        // 3. Intentar también embeber tags MP4 en el contenedor (best-effort)
        if (file.extension.lowercase() == "m4a") {
            val tmp = File(file.parentFile, "${file.nameWithoutExtension}.tmp.m4a")
            runCatching {
                Mp4TagWriter.write(
                    src = file, dst = tmp,
                    title = title, artist = artist,
                    album = album.ifBlank { title },
                    artworkJpeg = artworkBytes,
                )
                if (tmp.exists() && tmp.length() > 0) {
                    file.delete(); tmp.renameTo(file)
                    android.util.Log.d("NewPipeStream", "MP4 tags embedded OK")
                } else { tmp.delete() }
            }.onFailure {
                tmp.delete()
                android.util.Log.w("NewPipeStream", "MP4 tags failed (meta.json fallback active): ${it.message}")
            }
        }
    }

    private fun fetchArtworkBytes(thumbnailUrl: String, httpClient: OkHttpClient): ByteArray? {
        if (thumbnailUrl.isBlank()) return null
        return runCatching {
            val urls = listOf(
                thumbnailUrl,
                thumbnailUrl.replace("maxresdefault", "hqdefault"),
                thumbnailUrl.replace("maxresdefault", "mqdefault"),
            )
            urls.firstNotNullOfOrNull { url ->
                runCatching {
                    httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        resp.body?.bytes()?.takeIf { it.isNotEmpty() }
                    }
                }.getOrNull()
            }
        }.getOrNull()
    }

    private fun ffmpegEscape(raw: String): String = raw.replace("\"", "\\\"")

    private fun buildMp3MasterCommand(
        inputPath: String,
        outputPath: String,
        artworkPath: String?,
        escapedTitle: String,
        escapedArtist: String,
        escapedAlbum: String,
    ): String {
        val metadata = "-metadata title=\"$escapedTitle\" -metadata artist=\"$escapedArtist\" -metadata album=\"$escapedAlbum\""
        return if (!artworkPath.isNullOrBlank()) {
            "-y -i \"$inputPath\" -i \"$artworkPath\" " +
                "-map 0:a -map 1:v -c:a mp3 -b:a 192k -c:v mjpeg -id3v2_version 3 " +
                "$metadata -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\" " +
                "\"$outputPath\""
        } else {
            "-y -i \"$inputPath\" -vn -c:a mp3 -b:a 192k -id3v2_version 3 $metadata \"$outputPath\""
        }
    }

    private fun buildFormatFromMasterCommand(
        masterPath: String,
        outputPath: String,
        format: DownloadFormat,
        artworkPath: String?,
        escapedTitle: String,
        escapedArtist: String,
        escapedAlbum: String,
    ): String {
        val metadata = "-metadata title=\"$escapedTitle\" -metadata artist=\"$escapedArtist\" -metadata album=\"$escapedAlbum\""
        return when (format) {
            DownloadFormat.MP3 ->
                "-y -i \"$masterPath\" -vn -c:a mp3 -b:a 192k -id3v2_version 3 $metadata \"$outputPath\""
            DownloadFormat.M4A -> {
                if (!artworkPath.isNullOrBlank()) {
                    "-y -i \"$masterPath\" -i \"$artworkPath\" " +
                        "-map 0:a -map 1:v -map_metadata 0 -c:a aac -b:a 192k -c:v mjpeg -disposition:v:0 attached_pic " +
                        "$metadata \"$outputPath\""
                } else {
                    "-y -i \"$masterPath\" -vn -map_metadata 0 -c:a aac -b:a 192k $metadata \"$outputPath\""
                }
            }
            DownloadFormat.AAC ->
                "-y -i \"$masterPath\" -vn -map_metadata 0 -c:a aac -b:a 192k -f adts $metadata \"$outputPath\""
            DownloadFormat.OGG ->
                "-y -i \"$masterPath\" -vn -map_metadata 0 -c:a libvorbis -q:a 5 $metadata \"$outputPath\""
            DownloadFormat.FLAC -> {
                if (!artworkPath.isNullOrBlank()) {
                    // FLAC requiere insertar explícitamente la portada en la conversión final.
                    "-y -i \"$masterPath\" -i \"$artworkPath\" " +
                        "-map 0:a -map 1:v -map_metadata 0 -c:a flac -c:v mjpeg -disposition:v:0 attached_pic " +
                        "$metadata \"$outputPath\""
                } else {
                    "-y -i \"$masterPath\" -vn -map_metadata 0 -c:a flac $metadata \"$outputPath\""
                }
            }
            DownloadFormat.WAV ->
                "-y -i \"$masterPath\" -vn -map_metadata 0 -c:a pcm_s16le $metadata \"$outputPath\""
        }
    }

}

