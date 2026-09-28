package com.imontalvodev.beatmybeat.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Resolución de carátula para MediaSession / notificación de sistema.
 * Replica la prioridad de la UI del reproductor: tags embebidos, luego .meta.json (descargas .music).
 */
internal object PlaybackArtworkHelper {

    fun resolveArtworkBytes(context: Context, uriString: String): ByteArray? {
        val embedded = readEmbeddedPicture(context, uriString)
        // Bytes completos: truncarlos corrompe el JPEG/PNG. No se retienen; quien llama
        // decodifica a bitmap reducido (BitmapDecoding.decodeSampled) y los suelta.
        if (embedded != null && embedded.isNotEmpty()) return embedded
        return readSidecarMetaArtwork(uriString)
    }

    private fun readEmbeddedPicture(context: Context, uriString: String): ByteArray? {
        val uri = Uri.parse(uriString)
        // content://: a veces getEmbeddedPicture falla con setDataSource(Context, Uri) (JNI);
        // ParcelFileDescriptor + FileDescriptor suele ser más fiable.
        if (uri.scheme.equals("content", ignoreCase = true)) {
            val viaFd = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(pfd.fileDescriptor)
                        r.embeddedPicture
                    } finally {
                        r.release()
                    }
                }
            }.getOrNull()
            if (viaFd != null && viaFd.isNotEmpty()) return viaFd
        }
        return runCatching {
            val r = MediaMetadataRetriever()
            try {
                when {
                    uri.scheme.equals("file", ignoreCase = true) -> {
                        val path = uri.path ?: return@runCatching null
                        r.setDataSource(path)
                    }
                    else -> r.setDataSource(context, uri)
                }
                r.embeddedPicture
            } finally {
                r.release()
            }
        }.getOrNull()
    }

    private fun readSidecarMetaArtwork(uriString: String): ByteArray? = runCatching {
        val path = Uri.parse(uriString).path ?: return@runCatching null
        val audioFile = File(path)
        if (!audioFile.isFile) return@runCatching null
        val parent = audioFile.parentFile ?: return@runCatching null
        val metaFile = File(parent, "${audioFile.nameWithoutExtension}.meta.json")
        if (!metaFile.exists()) return@runCatching null
        val b64 = JSONObject(metaFile.readText()).optString("artworkBase64")
        if (b64.isBlank()) return@runCatching null
        android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
    }.getOrNull()
}
