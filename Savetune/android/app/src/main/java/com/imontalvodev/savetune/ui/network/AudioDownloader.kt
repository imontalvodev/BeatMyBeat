package com.imontalvodev.savetune.ui.network

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AudioDownloader {
    data class DownloadResult(
        val success: Boolean,
        val fileName: String?,
        val error: String? = null,
    )

    suspend fun downloadAutoToAppMusic(
        context: Context,
        middlewareBaseUrl: String,
        title: String,
        artist: String,
        album: String,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val base = middlewareBaseUrl.trimEnd('/')
        val builder = "$base/api/download-auto".toHttpUrlOrNull()?.newBuilder()
            ?: return@withContext DownloadResult(false, null, "BadUrl")

        if (title.isNotBlank()) builder.addQueryParameter("title", title)
        if (artist.isNotBlank()) builder.addQueryParameter("artist", artist)
        if (album.isNotBlank()) builder.addQueryParameter("album", album)

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
                return@withContext handleResponse(context, res, title)
            }
        } catch (e: Exception) {
            return@withContext DownloadResult(false, null, e.message)
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

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
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

        // Actualizar índice para que el reproductor lo detecte
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outFile.absolutePath),
            null,
            null,
        )

        return DownloadResult(true, outFile.name, null)
    }
}

