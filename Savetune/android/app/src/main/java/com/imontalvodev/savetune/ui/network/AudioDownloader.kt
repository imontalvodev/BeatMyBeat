package com.imontalvodev.savetune.ui.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.imontalvodev.savetune.notifications.SavetuneNotification
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

object AudioDownloader {
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
        imageUrl: String = "",
    ): DownloadResult = withContext(Dispatchers.IO) {
        val safeTitle = title.trim()
        val safeArtist = artist.trim()
        val notificationTitle = if (safeTitle.isNotBlank()) "Descargando: $safeTitle" else "Descargando canción"
        SavetuneNotification.showDownloadInProgress(
            context = context,
            title = notificationTitle,
            subtitle = safeArtist,
        )
        try {
            val base = middlewareBaseUrl.trimEnd('/')
            val builder = "$base/api/download-auto".toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext DownloadResult(false, null, "BadUrl")

            if (title.isNotBlank()) builder.addQueryParameter("title", title)
            if (artist.isNotBlank()) builder.addQueryParameter("artist", artist)
            if (album.isNotBlank()) builder.addQueryParameter("album", album)
            if (imageUrl.isNotBlank()) builder.addQueryParameter("imageUrl", imageUrl)

            val url = builder.build()

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .callTimeout(7, TimeUnit.MINUTES)
                .build()

            val request = Request.Builder().url(url).get().build()

            try {
                client.newCall(request).execute().use { res: Response ->
                    val saved = handleResponse(context, res, title)
                    if (saved.success) {
                        // Pre-descargar letras para uso offline (si hay metadatos válidos).
                        val t = title.trim()
                        val a = artist.trim()
                        val isUnknown = { s: String ->
                            s.isBlank() ||
                                s.equals("unknown", ignoreCase = true) ||
                                s.equals("unknown artist", ignoreCase = true)
                        }
                        if (!isUnknown(t) && !isUnknown(a)) {
                            runCatching {
                                val lyr = MiddlewareApi.fetchLyrics(middlewareBaseUrl, t, a)
                                if (lyr.success && lyr.lyrics.isNotBlank()) {
                                    LyricsCache.put(context, t, a, lyr.lyrics)
                                }
                            }
                        }

                        SavetuneNotification.showDownloadCompleted(
                            context = context,
                            title = "Descarga completada",
                            subtitle = saved.fileName ?: safeTitle,
                        )
                    } else {
                        SavetuneNotification.showDownloadFailed(
                            context = context,
                            title = "Error en la descarga",
                            subtitle = saved.error ?: "Reintenta más tarde",
                        )
                    }
                    return@withContext saved
                }
            } catch (e: Exception) {
                SavetuneNotification.showDownloadFailed(
                    context = context,
                    title = "Error en la descarga",
                    subtitle = e.message ?: "Reintenta más tarde",
                )
                return@withContext DownloadResult(false, null, e.message)
            }
        } finally {
            // no-op: dejamos que la notificación en curso sea reemplazada por completada/error.
        }
    }

    suspend fun downloadYoutubeAlbumZipToAppMusic(
        context: Context,
        middlewareBaseUrl: String,
        playlistUrl: String,
    ): ZipDownloadResult = withContext(Dispatchers.IO) {
        val notifTitle = "Descargando playlist de YouTube"
        SavetuneNotification.showDownloadInProgress(
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

            val url = buildUrl(middlewareBaseUrl)
                ?: return@withContext ZipDownloadResult(false, 0, "BadUrl")
            val fallbackBackendUrl = buildUrl(guessPythonBackendBaseUrl(middlewareBaseUrl))

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

                val musicDir = File(context.filesDir, ".music")
                if (!musicDir.exists()) musicDir.mkdirs()

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
                                val outFile = File(musicDir, safeName)
                                FileOutputStream(outFile).use { output ->
                                    var read = zis.read(buffer)
                                    while (read > 0) {
                                        output.write(buffer, 0, read)
                                        read = zis.read(buffer)
                                    }
                                    output.flush()
                                }
                                extracted++
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

            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val out = extractZipFromResponse(res)
                        if (out.success) {
                            SavetuneNotification.showDownloadCompleted(
                                context = context,
                                title = "Playlist descargada",
                                subtitle = "${out.extractedFiles} pistas",
                            )
                        } else {
                            SavetuneNotification.showDownloadFailed(
                                context = context,
                                title = "Error en la descarga",
                                subtitle = out.error ?: "Reintenta más tarde",
                            )
                        }
                        return@withContext out
                    }

                    val firstContentType = (res.header("Content-Type") ?: "").lowercase()
                    val firstBody = res.body?.string().orEmpty()
                    val isHtml404 = res.code == 404 && firstContentType.contains("text/html")

                    // Fallback pragmático: middleware sin ruta nueva o no reiniciado.
                    if (isHtml404 && fallbackBackendUrl != null) {
                        client.newCall(Request.Builder().url(fallbackBackendUrl).get().build()).execute().use { res2 ->
                            if (!res2.isSuccessful) {
                                val body2 = res2.body?.string().orEmpty()
                                val msg2 = if (body2.isNotBlank()) "HTTP_${res2.code}: $body2" else "HTTP_${res2.code}"
                                SavetuneNotification.showDownloadFailed(
                                    context = context,
                                    title = "Error en la descarga",
                                    subtitle = msg2,
                                )
                                return@withContext ZipDownloadResult(false, 0, msg2)
                            }
                            val out = extractZipFromResponse(res2)
                            if (out.success) {
                                SavetuneNotification.showDownloadCompleted(
                                    context = context,
                                    title = "Playlist descargada",
                                    subtitle = "${out.extractedFiles} pistas",
                                )
                            } else {
                                SavetuneNotification.showDownloadFailed(
                                    context = context,
                                    title = "Error en la descarga",
                                    subtitle = out.error ?: "Reintenta más tarde",
                                )
                            }
                            return@withContext out
                        }
                    }

                    val msg = if (firstBody.isNotBlank()) "HTTP_${res.code}: $firstBody" else "HTTP_${res.code}"
                    SavetuneNotification.showDownloadFailed(
                        context = context,
                        title = "Error en la descarga",
                        subtitle = msg,
                    )
                    return@withContext ZipDownloadResult(false, 0, msg)
                }
            } catch (e: Exception) {
                SavetuneNotification.showDownloadFailed(
                    context = context,
                    title = "Error en la descarga",
                    subtitle = e.message ?: "Reintenta más tarde",
                )
                return@withContext ZipDownloadResult(false, 0, e.message)
            }
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

        // Carpeta interna privada: no aparece en MediaStore y se elimina al desinstalar.
        val dir = File(context.filesDir, ".music")
        if (!dir.exists()) dir.mkdirs()

        val fileNameFromHeader =
            res.header("Content-Disposition")
                ?.substringAfter("filename=\"")
                ?.substringBeforeLast("\"")
        val safeName = fileNameFromHeader?.takeIf { it.isNotBlank() }
            ?: (title.ifBlank { "track" } + ".mp3")

        val outFile = File(dir, safeName)
        FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                if (bytes > 0) output.write(buffer, 0, bytes)
                bytes = inputStream.read(buffer)
            }
            output.flush()
        }

        // Si el usuario descarga otro álbum/canciones, las IDs pueden repetirse
        // y ArtworkCache puede devolver una carátula vieja.
        ArtworkCache.clear()
        return DownloadResult(true, outFile.name, null)
    }
}

