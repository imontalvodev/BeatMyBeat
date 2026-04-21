package com.imontalvodev.beatmybeat.ui.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import java.io.File
import java.util.Locale

data class DeviceTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val dateAddedMs: Long = 0L,
)

class MediaStoreScanner(private val context: Context) {

    suspend fun scanAudio(): List<DeviceTrack> {
        val minMusicDurationMs = 30_000L
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minMusicDurationMs.toString())

        val tracks = mutableListOf<DeviceTrack>()
        // Set de DISPLAY_NAME (en minúsculas) ya añadidos desde MediaStore, para deduplicar SAF
        val knownDisplayNames = mutableSetOf<String>()

        // 1) Intentar leer desde MediaStore (biblioteca general del dispositivo)
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val album = cursor.getString(albumCol)
                    val duration = cursor.getLong(durationCol)
                    val mimeType = cursor.optString(mimeTypeCol).lowercase(Locale.ROOT)
                    val displayName = cursor.optString(displayNameCol).lowercase(Locale.ROOT)
                    val absolutePath = cursor.optString(dataCol).lowercase(Locale.ROOT)
                    val relativePath = cursor.optString(relativePathCol).lowercase(Locale.ROOT)
                    val dateAddedSeconds = cursor.optLong(dateAddedCol)
                    val dateAddedMs = if (dateAddedSeconds > 0L) dateAddedSeconds * 1000L else 0L
                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        .buildUpon()
                        .appendPath(id.toString())
                        .build()
                        .toString()

                    val searchableText = listOf(title, artist, album, displayName, absolutePath, relativePath)
                        .joinToString(" ")
                        .lowercase(Locale.ROOT)

                    if (duration < minMusicDurationMs) continue
                    if (isLikelyNonMusicAudio(searchableText, mimeType)) continue

                    // Guardar el displayName real para deduplicar contra SAF después
                    if (displayName.isNotBlank()) knownDisplayNames.add(displayName)

                    tracks += DeviceTrack(
                        id = id,
                        uri = uri,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        dateAddedMs = dateAddedMs,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Sin permisos de lectura, seguimos con la carpeta privada de la app.
        }

        // 2) Incluir ficheros heredados en almacenamiento interno privado (.music)
        val appMusicDir = File(context.filesDir, ".music")
        if (appMusicDir.exists()) {
            val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "webm")
            appMusicDir.listFiles()?.forEachIndexed { index, file ->
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in audioExtensions) {
                        val uriString = file.toURI().toString()
                        val already = tracks.any { it.uri == uriString }
                        if (!already) {
                            // Intentar leer el sidecar .meta.json primero (escrito por AudioDownloader)
                            val metaFile = File(file.parentFile, "${file.nameWithoutExtension}.meta.json")
                            var title = file.nameWithoutExtension
                            var artist = "Unknown artist"
                            var album: String? = null
                            var durationMs = 0L
                            var metaLoaded = false

                            if (metaFile.exists()) {
                                runCatching {
                                    val json = org.json.JSONObject(metaFile.readText())
                                    json.optString("title").takeIf { it.isNotBlank() }?.let { title = it }
                                    json.optString("artist").takeIf { it.isNotBlank() && !it.equals("unknown artist", ignoreCase = true) }?.let { artist = it }
                                    json.optString("album").takeIf { it.isNotBlank() }?.let { album = it }
                                    metaLoaded = true
                                }
                            }

                            // Si no había meta.json o faltaba duración, leer del archivo
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.absolutePath)
                                if (!metaLoaded) {
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                        ?.takeIf { it.isNotBlank() }?.let { title = it }
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                        ?.takeIf { it.isNotBlank() }?.let { artist = it }
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                        ?.takeIf { it.isNotBlank() }?.let { album = it }
                                }
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull()?.let { durationMs = it }
                            } catch (_: Exception) {
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
                                dateAddedMs = file.lastModified(),
                            )
                        }
                    }
                }
            }
        }

        // 3) Si hay carpeta personalizada (SAF), leer también esas pistas.
        // Solo añadimos archivos cuyo nombre (DocumentFile.name) no está ya en knownDisplayNames,
        // lo que evita duplicados cuando Android MediaStore también indexa esa carpeta.
        val customDocs = StorageSettings.listCustomAudioDocs(context)
        customDocs.forEachIndexed { index, doc ->
            val docName = doc.name?.lowercase(Locale.ROOT).orEmpty()
            // Saltar si MediaStore (o .music) ya tiene este archivo
            if (docName.isNotBlank() && knownDisplayNames.contains(docName)) return@forEachIndexed
            val uriString = doc.uri.toString()
            if (tracks.any { it.uri == uriString }) return@forEachIndexed

            var title = "Unknown"
            var artist = "Unknown artist"
            var album: String? = null
            var durationMs = 0L

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, doc.uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }?.let { album = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { durationMs = it }
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }

            if (durationMs < minMusicDurationMs) return@forEachIndexed
            tracks += DeviceTrack(
                id = Long.MAX_VALUE - index,
                uri = uriString,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                dateAddedMs = doc.lastModified().takeIf { it > 0 } ?: (System.currentTimeMillis() - index),
            )
        }

        return tracks
    }

    private fun isLikelyNonMusicAudio(searchableText: String, mimeType: String): Boolean {
        val nonMusicPathHints = listOf(
            "whatsapp",
            "telegram",
            "instagram",
            "voice notes",
            "voicenotes",
            "recordings",
            "recorder",
            "call",
            "podcast",
            "audiobooks",
            "notifications",
            "ringtones",
            "alarms",
        )

        if (nonMusicPathHints.any { searchableText.contains(it) }) return true
        if (mimeType.contains("audio/3gpp") || mimeType.contains("audio/amr")) return true
        return false
    }

    private fun android.database.Cursor.optString(columnIndex: Int): String {
        if (columnIndex < 0) return ""
        return runCatching { getString(columnIndex) ?: "" }.getOrDefault("")
    }

    private fun android.database.Cursor.optLong(columnIndex: Int): Long {
        if (columnIndex < 0) return 0L
        return runCatching { getLong(columnIndex) }.getOrDefault(0L)
    }
}

