package com.imontalvodev.savetune.ui.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import java.io.File

data class DeviceTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

class MediaStoreScanner(private val context: Context) {

    suspend fun scanAudio(): List<DeviceTrack> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val tracks = mutableListOf<DeviceTrack>()

        // 1) Intentar leer desde MediaStore (biblioteca general del dispositivo)
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val album = cursor.getString(albumCol)
                    val duration = cursor.getLong(durationCol)
                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        .buildUpon()
                        .appendPath(id.toString())
                        .build()
                        .toString()

                    tracks += DeviceTrack(
                        id = id,
                        uri = uri,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Sin permisos de lectura, seguimos con la carpeta privada de la app.
        }

        // 2) Incluir siempre los ficheros en almacenamiento interno privado (.music)
        val appMusicDir = File(context.filesDir, ".music")
        if (appMusicDir.exists()) {
            val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac")
            appMusicDir.listFiles()?.forEachIndexed { index, file ->
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in audioExtensions) {
                        val uriString = file.toURI().toString()
                        val already = tracks.any { it.uri == uriString }
                        if (!already) {
                            val retriever = MediaMetadataRetriever()
                            var title = file.nameWithoutExtension
                            var artist = "Unknown artist"
                            var album: String? = null
                            var durationMs = 0L
                            try {
                                retriever.setDataSource(file.absolutePath)
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                    ?.takeIf { it.isNotBlank() }?.let { album = it }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()?.let { durationMs = it }
                            } catch (_: Exception) {
                                // Si falla, usamos los valores por defecto definidos arriba.
                            } finally {
                                retriever.release()
                            }

                            tracks += DeviceTrack(
                                id = Int.MAX_VALUE.toLong() - index,
                                uri = uriString,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                            )
                        }
                    }
                }
            }
        }

        return tracks
    }
}

