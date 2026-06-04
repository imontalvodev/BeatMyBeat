package com.imontalvodev.beatmybeat.ui.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

object StorageSettings {
    private const val PREFS = "beatmybeat_storage_settings"
    private const val KEY_CUSTOM_TREE_URI = "custom_tree_uri"
    private const val DEFAULT_RELATIVE_PATH = "Music/BeatMyBeat/"

    fun getCustomTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_TREE_URI, null)
            ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun setCustomTreeUri(context: Context, treeUri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_TREE_URI, treeUri?.toString())
            .apply()
    }

    fun getLocationLabel(context: Context): String {
        val custom = getCustomTreeUri(context)
        return if (custom != null) {
            val tree = DocumentFile.fromTreeUri(context, custom)
            val folderName = tree?.name?.takeIf { it.isNotBlank() }
            if (folderName != null) {
                "Carpeta: $folderName"
            } else {
                "Carpeta personalizada"
            }
        } else {
            "Music/BeatMyBeat/"
        }
    }

    fun saveAudioFromFile(
        context: Context,
        source: File,
        displayName: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): String? {
        val ext = source.extension.lowercase()
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
        FileInputStream(source).use { input ->
            return saveStream(
                context = context,
                input = input,
                displayName = displayName,
                mimeType = mime,
                isAudio = true,
                title = title,
                artist = artist,
                album = album,
            )
        }
    }

    fun saveTextSidecar(
        context: Context,
        fileName: String,
        text: String,
    ): Boolean {
        val ok = saveStream(
            context = context,
            input = text.byteInputStream(),
            displayName = fileName,
            mimeType = "application/json",
            isAudio = false,
        )
        return ok != null
    }

    fun saveRawAudioFromStream(
        context: Context,
        input: InputStream,
        displayName: String,
        mimeType: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): Boolean {
        return saveStream(
            context = context,
            input = input,
            displayName = displayName,
            mimeType = mimeType,
            isAudio = true,
            title = title,
            artist = artist,
            album = album,
        ) != null
    }

    private fun saveStream(
        context: Context,
        input: InputStream,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): String? {
        val customTree = getCustomTreeUri(context)
        return if (customTree != null) {
            saveToCustomTree(context, customTree, input, displayName, mimeType)
        } else {
            saveToDefaultPublicFolder(
                context = context,
                input = input,
                displayName = displayName,
                mimeType = mimeType,
                isAudio = isAudio,
                title = title,
                artist = artist,
                album = album,
            )
        }
    }

    private fun saveToCustomTree(
        context: Context,
        treeUri: Uri,
        input: InputStream,
        displayName: String,
        mimeType: String,
    ): String? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!tree.canWrite()) return null

        tree.findFile(displayName)?.delete()
        val doc = tree.createFile(mimeType, displayName) ?: return null
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            input.copyTo(out)
        } ?: return null
        return displayName
    }

    private fun saveToDefaultPublicFolder(
        context: Context,
        input: InputStream,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        title: String?,
        artist: String?,
        album: String?,
    ): String? {
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (isAudio) {
                title?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
                artist?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            }
        }

        val resolver = context.contentResolver

        // Eliminar entrada previa con el mismo nombre para evitar duplicados en MediaStore
        runCatching {
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(displayName, DEFAULT_RELATIVE_PATH),
            )
        }

        val uri = resolver.insert(collection, values) ?: return null
        var completed = false
        try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                input.copyTo(out)
            } ?: return null
            completed = true
            return displayName
        } finally {
            val finalizeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, finalizeValues, null, null)
            if (!completed) resolver.delete(uri, null, null)
        }
    }

    fun listCustomAudioDocs(context: Context): List<DocumentFile> {
        val customTree = getCustomTreeUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, customTree) ?: return emptyList()
        return collectAudioFilesRecursive(root)
    }

    private fun collectAudioFilesRecursive(dir: DocumentFile): List<DocumentFile> {
        val out = mutableListOf<DocumentFile>()
        dir.listFiles().forEach { entry ->
            when {
                entry.isFile && isAudioExtension(entry.name.orEmpty()) -> out.add(entry)
                entry.isDirectory -> out.addAll(collectAudioFilesRecursive(entry))
            }
        }
        return out
    }

    fun listCustomAudioUris(context: Context): List<Uri> =
        listCustomAudioDocs(context).map { it.uri }

    private fun isAudioExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "flac", "webm", "opus", "wma", "mp4")
    }
}

