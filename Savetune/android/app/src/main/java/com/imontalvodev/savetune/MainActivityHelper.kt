package com.imontalvodev.savetune

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File

object MainActivityHelper {

    private const val TAG = "SaveTuneHelper"

    fun loadDownloadedSongs(context: Context): List<Song> {
        val result = mutableListOf<Song>()

        // Primero intentar con MediaStore
        result.addAll(loadFromMediaStore(context))

        // Si no encontramos nada, buscar directamente en carpetas conocidas
        if (result.isEmpty()) {
            Log.w(TAG, "MediaStore vacío, buscando directamente en sistema de archivos...")
            result.addAll(loadFromFileSystem())
        }

        val distinct = result.distinctBy { song ->
            song.mediaStoreId
                ?: song.file?.let { f -> "${f.name.lowercase()}-${f.length()}" }
                ?: "${song.title.lowercase()}-${song.durationSeconds}"
        }

        Log.d(TAG, "Total de canciones cargadas (sin duplicados): ${distinct.size}")
        return distinct
    }

    private fun loadFromFileSystem(): List<Song> {
        val result = mutableListOf<Song>()

        val musicFolders = listOf(
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/sdcard/Music",
            "/sdcard/Download"
        )

        val audioExtensions = setOf("mp3", "m4a", "webm", "ogg", "wav", "flac", "aac")

        for (folderPath in musicFolders) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                Log.d(TAG, "Carpeta no existe: $folderPath")
                continue
            }

            Log.d(TAG, "Escaneando carpeta: $folderPath")

            folder.listFiles()?.forEach { file ->
                if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                    try {
                        val song = Song(
                            title = file.nameWithoutExtension,
                            artist = "Unknown Artist",
                            album = "",
                            durationSeconds = 0,
                            file = file
                        )
                        result.add(song)
                        Log.d(TAG, "Archivo encontrado: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando ${file.name}", e)
                    }
                }
            }
        }

        return result
    }

    private fun loadFromMediaStore(context: Context): List<Song> {
        val result = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                Log.d(TAG, "Escaneando MediaStore... Total filas: ${it.count}")

                while (it.moveToNext()) {
                    try {
                        val id = it.getLong(idIdx)
                        val displayName = it.getString(nameIdx) ?: ""
                        var title = it.getString(titleIdx)?.takeIf { t -> t.isNotBlank() }
                            ?: displayName.substringBeforeLast('.')
                        var artist = it.getString(artistIdx)?.takeIf { a -> a.isNotBlank() && a != "<unknown>" }
                            ?: "Unknown Artist"
                        val album = it.getString(albumIdx) ?: ""
                        val durationMs = it.getLong(durationIdx)
                        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else 0
                        val fullPath = it.getString(dataIdx) ?: ""

                        val file = try {
                            if (fullPath.isNotBlank()) File(fullPath) else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando File para: $fullPath", e)
                            null
                        }

                        if (artist == "Unknown Artist") {
                            val source = title.ifBlank { displayName.substringBeforeLast('.') }
                            if (source.contains(" - ")) {
                                val parts = source.split(" - ")
                                if (parts.size >= 2) {
                                    val maybeArtist = parts.last().trim()
                                    val maybeTitle = parts.dropLast(1).joinToString(" - ").trim()
                                    if (maybeArtist.length in 2..40) {
                                        artist = maybeArtist
                                        if (maybeTitle.isNotBlank()) {
                                            title = maybeTitle
                                        }
                                    }
                                }
                            }
                        }

                        val song = Song(
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSec,
                            file = file,
                            mediaStoreId = id
                        )

                        result.add(song)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando fila", e)
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al acceder a MediaStore", e)
            Toast.makeText(context, "Error de permisos al acceder a la música", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error general al cargar canciones", e)
            Toast.makeText(context, "Error al cargar canciones: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return result
    }
}

