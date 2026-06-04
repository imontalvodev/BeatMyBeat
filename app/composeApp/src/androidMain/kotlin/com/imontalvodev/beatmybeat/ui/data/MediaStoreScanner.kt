package com.imontalvodev.beatmybeat.ui.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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

    /**
     * MediaStore suele devolver DURATION = 0 hasta que el indexador termina.
     * Intentamos leer la duración real; si sigue en 0, no descartamos por duración
     * (evita perder pistas válidas que sí aparecen en la biblioteca del sistema).
     */
    private fun resolveDurationMs(mediaUri: Uri, mediaStoreDuration: Long): Long {
        if (mediaStoreDuration > 0L) return mediaStoreDuration
        return runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, mediaUri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                r.release()
            }
        }.getOrDefault(0L)
    }

    private fun isTooShortForLibrary(durationMs: Long, minMusicDurationMs: Long): Boolean =
        durationMs in 1 until minMusicDurationMs

    suspend fun scanAudio(): List<DeviceTrack> {
        val minMusicDurationMs = 20_000L
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
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM,
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        // Sin IS_MUSIC: muchas pistas importadas o anteriores a la app tienen IS_MUSIC = 0.
        val selection = "(${MediaStore.Audio.Media.DURATION} >= ? OR " +
            "${MediaStore.Audio.Media.DURATION} IS NULL OR " +
            "${MediaStore.Audio.Media.DURATION} = 0)"
        val selectionArgs = arrayOf(minMusicDurationMs.toString())

        val tracks = mutableListOf<DeviceTrack>()
        val knownUris = mutableSetOf<String>()
        val knownDisplayNames = mutableSetOf<String>()
        /** Misma canción en Audio.Media y Files → URIs distintas; deduplicamos por ruta o carpeta+nombre. */
        val knownStorageKeys = mutableSetOf<String>()

        // 1) MediaStore Audio en todos los volúmenes visibles
        try {
            for (collection in audioCollectionUris()) {
                scanAudioCollection(
                    collection = collection,
                    projection = projection,
                    selection = selection,
                    selectionArgs = selectionArgs,
                    sortOrder = sortOrder,
                    minMusicDurationMs = minMusicDurationMs,
                    tracks = tracks,
                    knownUris = knownUris,
                    knownDisplayNames = knownDisplayNames,
                    knownStorageKeys = knownStorageKeys,
                )
            }
            // 1b) Respaldo: solo entradas que Audio.Media no indexó (evita duplicar descargas)
            scanFilesAudioCollection(
                minMusicDurationMs = minMusicDurationMs,
                tracks = tracks,
                knownUris = knownUris,
                knownDisplayNames = knownDisplayNames,
                knownStorageKeys = knownStorageKeys,
            )
        } catch (_: SecurityException) {
            // Sin permisos de lectura, seguimos con carpetas de la app y SAF.
        }

        // 2) Ficheros en almacenamiento interno privado (.music)
        scanAppPrivateMusic(tracks, knownUris, knownDisplayNames, knownStorageKeys, minMusicDurationMs)

        // 3) Carpeta personalizada (SAF), incluyendo subcarpetas
        scanCustomStorage(tracks, knownUris, knownDisplayNames, knownStorageKeys, minMusicDurationMs)

        return dedupeTracksPreferringRichMetadata(tracks)
    }

    /** Una entrada por URI; prioriza metadatos de Audio.Media frente a Files (artist Unknown). */
    private fun dedupeTracksPreferringRichMetadata(tracks: List<DeviceTrack>): List<DeviceTrack> =
        tracks
            .groupBy { it.uri }
            .map { (_, group) ->
                group.maxBy { track ->
                    var score = 0
                    val artist = track.artist.lowercase(Locale.ROOT)
                    if (artist.isNotBlank() && artist != "unknown" && artist != "unknown artist") score += 4
                    if (track.durationMs > 0L) score += 1
                    score
                }
            }

    private fun storageKey(relativePath: String, displayName: String): String {
        val folder = relativePath.lowercase(Locale.ROOT).trim().trimEnd('/')
        val name = displayName.lowercase(Locale.ROOT).trim()
        if (folder.isBlank() || name.isBlank()) return ""
        return "$folder/$name"
    }

    private fun canonicalPath(path: String): String {
        if (path.isBlank()) return ""
        return runCatching {
            File(path).canonicalFile.absolutePath.lowercase(Locale.ROOT)
        }.getOrDefault(path.lowercase(Locale.ROOT))
    }

    private fun registerStorageIdentity(
        absolutePath: String,
        relativePath: String,
        displayName: String,
        knownStorageKeys: MutableSet<String>,
    ) {
        val pathKey = canonicalPath(absolutePath)
        if (pathKey.isNotBlank()) knownStorageKeys.add("path:$pathKey")
        val key = storageKey(relativePath, displayName)
        if (key.isNotBlank()) knownStorageKeys.add("key:$key")
    }

    private fun isAlreadyIndexed(
        absolutePath: String,
        relativePath: String,
        displayName: String,
        knownStorageKeys: Set<String>,
    ): Boolean {
        val pathKey = canonicalPath(absolutePath)
        if (pathKey.isNotBlank() && "path:$pathKey" in knownStorageKeys) return true
        val key = storageKey(relativePath, displayName)
        if (key.isNotBlank() && "key:$key" in knownStorageKeys) return true
        return false
    }

    private fun audioCollectionUris(): List<Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = linkedSetOf<Uri>()
            runCatching {
                MediaStore.getExternalVolumeNames(context).forEach { volume ->
                    uris.add(MediaStore.Audio.Media.getContentUri(volume))
                }
            }
            if (uris.isEmpty()) {
                uris.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            }
            uris.add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL))
            return uris.toList()
        }
        return listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    private fun scanAudioCollection(
        collection: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String,
        minMusicDurationMs: Long,
        tracks: MutableList<DeviceTrack>,
        knownUris: MutableSet<String>,
        knownDisplayNames: MutableSet<String>,
        knownStorageKeys: MutableSet<String>,
    ) {
        context.contentResolver.query(
            collection,
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
            val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
            val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val isRingtoneCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
            val isNotificationCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
            val isAlarmCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val uri = contentUri.toString()
                if (!knownUris.add(uri)) continue

                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown"
                val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "Unknown"
                val album = cursor.getString(albumCol)
                val duration = cursor.getLong(durationCol)
                val isMusic = if (isMusicCol >= 0) cursor.getInt(isMusicCol) else 0
                val mimeType = cursor.optString(mimeTypeCol).lowercase(Locale.ROOT)
                val displayName = cursor.optString(displayNameCol)
                val displayNameLower = displayName.lowercase(Locale.ROOT)
                val absolutePath = cursor.optString(dataCol).lowercase(Locale.ROOT)
                val relativePath = cursor.optString(relativePathCol).lowercase(Locale.ROOT)
                val dateAddedSeconds = cursor.optLong(dateAddedCol)
                val dateAddedMs = if (dateAddedSeconds > 0L) dateAddedSeconds * 1000L else 0L

                if (isSystemToneFlags(isRingtoneCol, isNotificationCol, isAlarmCol, cursor)) continue
                if (!isRecognizedMusicFile(isMusic, mimeType, displayNameLower, relativePath, absolutePath)) {
                    continue
                }
                if (isSystemAudioPath(relativePath, absolutePath) && isMusic == 0) continue

                val searchableText = listOf(title, artist, album, displayNameLower, absolutePath, relativePath)
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)

                val durationResolved = resolveDurationMs(contentUri, duration)
                if (isTooShortForLibrary(durationResolved, minMusicDurationMs)) continue
                if (isLikelyNonMusicAudio(searchableText, mimeType, durationResolved)) continue

                if (displayNameLower.isNotBlank()) knownDisplayNames.add(displayNameLower)
                registerStorageIdentity(absolutePath, relativePath, displayName, knownStorageKeys)

                tracks += DeviceTrack(
                    id = id,
                    uri = uri,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationResolved,
                    dateAddedMs = dateAddedMs,
                )
            }
        }
    }

    private fun scanFilesAudioCollection(
        minMusicDurationMs: Long,
        tracks: MutableList<DeviceTrack>,
        knownUris: MutableSet<String>,
        knownDisplayNames: MutableSet<String>,
        knownStorageKeys: MutableSet<String>,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"

        val volumes = runCatching { MediaStore.getExternalVolumeNames(context).toList() }
            .getOrDefault(emptyList())
        if (volumes.isEmpty()) return

        for (volume in volumes) {
            val collection = MediaStore.Files.getContentUri(volume)
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val durationCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                val relativePathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val uri = contentUri.toString()
                    if (!knownUris.add(uri)) continue

                    val displayName = cursor.getString(displayNameCol).orEmpty()
                    val displayNameLower = displayName.lowercase(Locale.ROOT)
                    val mimeType = cursor.optString(mimeTypeCol).lowercase(Locale.ROOT)
                    val relativePath = cursor.optString(relativePathCol).lowercase(Locale.ROOT)
                    val absolutePath = cursor.optString(dataCol).lowercase(Locale.ROOT)
                    val duration = cursor.optLong(durationCol)
                    val dateAddedSeconds = cursor.optLong(dateAddedCol)
                    val dateAddedMs = if (dateAddedSeconds > 0L) dateAddedSeconds * 1000L else 0L

                    if (isSystemAudioPath(relativePath, absolutePath)) continue
                    if (!isRecognizedMusicFile(0, mimeType, displayNameLower, relativePath, absolutePath)) {
                        continue
                    }
                    if (isAlreadyIndexed(absolutePath, relativePath, displayName, knownStorageKeys)) {
                        continue
                    }

                    val title = displayName.substringBeforeLast('.').ifBlank { displayName }
                    val searchableText = listOf(title, displayNameLower, absolutePath, relativePath)
                        .joinToString(" ")
                        .lowercase(Locale.ROOT)

                    val durationResolved = resolveDurationMs(contentUri, duration)
                    if (isTooShortForLibrary(durationResolved, minMusicDurationMs)) continue
                    if (isLikelyNonMusicAudio(searchableText, mimeType, durationResolved)) continue

                    if (displayNameLower.isNotBlank()) knownDisplayNames.add(displayNameLower)
                    registerStorageIdentity(absolutePath, relativePath, displayName, knownStorageKeys)

                    tracks += DeviceTrack(
                        id = id,
                        uri = uri,
                        title = title,
                        artist = "Unknown",
                        album = null,
                        durationMs = durationResolved,
                        dateAddedMs = dateAddedMs,
                    )
                }
            }
        }
    }

    private fun scanAppPrivateMusic(
        tracks: MutableList<DeviceTrack>,
        knownUris: MutableSet<String>,
        knownDisplayNames: MutableSet<String>,
        knownStorageKeys: MutableSet<String>,
        minMusicDurationMs: Long,
    ) {
        val appMusicDir = File(context.filesDir, ".music")
        if (!appMusicDir.exists()) return

        appMusicDir.listFiles()?.forEachIndexed { index, file ->
            if (!file.isFile) return@forEachIndexed
            val ext = file.extension.lowercase(Locale.ROOT)
            if (ext !in MUSIC_EXTENSIONS) return@forEachIndexed

            val uriString = file.toURI().toString()
            if (!knownUris.add(uriString)) return@forEachIndexed
            if (isAlreadyIndexed(file.absolutePath, "", file.name, knownStorageKeys)) {
                return@forEachIndexed
            }

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
                    json.optString("artist").takeIf { it.isNotBlank() && !it.equals("unknown artist", ignoreCase = true) }
                        ?.let { artist = it }
                    json.optString("album").takeIf { it.isNotBlank() }?.let { album = it }
                    metaLoaded = true
                }
            }

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

            if (isTooShortForLibrary(durationMs, minMusicDurationMs)) return@forEachIndexed

            knownDisplayNames.add(file.name.lowercase(Locale.ROOT))
            registerStorageIdentity(file.absolutePath, "", file.name, knownStorageKeys)
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

    private fun scanCustomStorage(
        tracks: MutableList<DeviceTrack>,
        knownUris: MutableSet<String>,
        knownDisplayNames: MutableSet<String>,
        knownStorageKeys: MutableSet<String>,
        minMusicDurationMs: Long,
    ) {
        val customDocs = StorageSettings.listCustomAudioDocs(context)
        customDocs.forEachIndexed { index, doc ->
            val docName = doc.name?.lowercase(Locale.ROOT).orEmpty()
            if (docName.isNotBlank() && knownDisplayNames.contains(docName)) return@forEachIndexed

            val uriString = doc.uri.toString()
            if (!knownUris.add(uriString)) return@forEachIndexed
            if (docName.isNotBlank() && isAlreadyIndexed("", "", doc.name.orEmpty(), knownStorageKeys)) {
                return@forEachIndexed
            }

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

            if (isTooShortForLibrary(durationMs, minMusicDurationMs)) return@forEachIndexed

            registerStorageIdentity("", "", doc.name.orEmpty(), knownStorageKeys)
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
    }

    /**
     * Acepta pistas con IS_MUSIC = 0 si la extensión/MIME/ruta indican música real
     * (común en MP3 importados, Descargas, carpetas legacy).
     */
    private fun isRecognizedMusicFile(
        isMusic: Int,
        mimeType: String,
        displayNameLower: String,
        relativePath: String,
        absolutePath: String,
    ): Boolean {
        if (isMusic != 0) return true

        val ext = displayNameLower.substringAfterLast('.', "")
        if (ext in MUSIC_EXTENSIONS) return true

        if (mimeType.isNotBlank() && MUSIC_MIME_TYPES.any { mime -> mimeType.startsWith(mime) }) {
            return !mimeType.contains("3gpp") && !mimeType.contains("amr")
        }

        val path = "$relativePath $absolutePath".lowercase(Locale.ROOT)
        if (path.contains("/music/") || path.contains("\\music\\")) return ext.isNotEmpty() || mimeType.startsWith("audio/")
        if (path.contains("/download") && ext in MUSIC_EXTENSIONS) return true

        return false
    }

    private fun isSystemToneFlags(
        isRingtoneCol: Int,
        isNotificationCol: Int,
        isAlarmCol: Int,
        cursor: android.database.Cursor,
    ): Boolean {
        if (isRingtoneCol >= 0 && cursor.optInt(isRingtoneCol) != 0) return true
        if (isNotificationCol >= 0 && cursor.optInt(isNotificationCol) != 0) return true
        if (isAlarmCol >= 0 && cursor.optInt(isAlarmCol) != 0) return true
        return false
    }

    private fun isSystemAudioPath(relativePath: String, absolutePath: String): Boolean {
        val p = "$relativePath $absolutePath".lowercase(Locale.ROOT)
        val hints = listOf(
            "/ringtones",
            "/notifications",
            "/alarms",
            "media/ringtones",
            "media/notifications",
            "media/alarms",
            "media/audio/ringtones",
            "media/audio/notifications",
            "media/audio/alarms",
            "sounds/ringtones",
            "sounds/notifications",
            "sounds/alarms",
            "/product/media/audio/ringtones",
            "/product/media/audio/notifications",
            "/product/media/audio/alarms",
            "/system/media/audio/ringtones",
            "/system/media/audio/notifications",
            "/ui/ringtones",
            "/ui/notifications",
        )
        return hints.any { p.contains(it) }
    }

    private fun isLikelyNonMusicAudio(searchableText: String, mimeType: String, durationMs: Long): Boolean {
        if (mimeType.contains("audio/3gpp") || mimeType.contains("audio/amr")) {
            if (durationMs < 60_000L) return true
        }
        if (isSystemAudioPath(searchableText, searchableText)) return true

        val pathHints = listOf(
            "/podcasts/",
            "/audiobooks/",
            "/whatsapp/",
            "whatsapp audio",
            "whatsapp voice",
            "/com.whatsapp/",
            "/telegram/",
            "/org.telegram/",
            "voice notes",
            "voicenotes",
            "voice message",
            "audio message",
            "/instagram/",
            "/com.instagram/",
            "/facebook/",
            "/messenger/",
            "/com.facebook.orca/",
            "/signal/",
            "/org.thoughtcrime/",
            "/snapchat/",
            "/tiktok/",
            "/twitter/",
            "/viber/",
            "/imo/",
            "/wechat/",
            "/tencent/",
            "/discord/",
            "/slack/",
            "/download/voice/",
            "/recordings/",
            "/screenrecord/",
        )
        return pathHints.any { searchableText.contains(it) }
    }

    private fun android.database.Cursor.optInt(columnIndex: Int): Int {
        if (columnIndex < 0) return 0
        return runCatching { getInt(columnIndex) }.getOrDefault(0)
    }

    private fun android.database.Cursor.optString(columnIndex: Int): String {
        if (columnIndex < 0) return ""
        return runCatching { getString(columnIndex) ?: "" }.getOrDefault("")
    }

    private fun android.database.Cursor.optLong(columnIndex: Int): Long {
        if (columnIndex < 0) return 0L
        return runCatching { getLong(columnIndex) }.getOrDefault(0L)
    }

    companion object {
        private val MUSIC_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "flac", "ogg", "opus", "wav", "wma", "webm", "mp4",
        )

        private val MUSIC_MIME_TYPES = setOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/mp4",
            "audio/flac",
            "audio/x-flac",
            "audio/ogg",
            "audio/opus",
            "audio/wav",
            "audio/vnd.wave",
            "audio/aac",
            "application/ogg",
        )
    }
}
