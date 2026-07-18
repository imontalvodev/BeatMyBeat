package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.imontalvodev.beatmybeat.core.Logger
import com.imontalvodev.beatmybeat.ui.storage.StorageSettings
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * Grabaciones de karaoke (Fase F).
 *
 * **Dónde viven:** una toma se graba primero en caché privada (`MediaRecorder` necesita una ruta de
 * archivo real) y **solo se publica en la carpeta pública del usuario al pulsar Guardar**, con
 * [StorageSettings]. Descartar borra el temporal, así que una toma descartada nunca llega a
 * ensuciar la carpeta de música.
 *
 * **Cómo se llaman:** `REC-AAAA-MM-DD-HH-MM-SS.m4a`. El nombre no lleva el id de la canción —
 * consecuencia de usar este formato — así que no se puede agrupar por pista a partir del nombre.
 *
 * **Por qué el prefijo importa:** al estar en la carpeta pública, el MediaScanner las indexa y
 * aparecerían en la biblioteca mezcladas con la música. [isRecordingFileName] es el filtro que lo
 * evita, y se aplica tanto al escanear (`MediaStoreScanner`) como al listar la carpeta
 * personalizada (`StorageSettings`).
 *
 * **Espacio:** AAC mono 64 kbps, ~1,6 MB por toma de 3:30 — menos de la mitad que una canción
 * descargada. La palanca real no es el bitrate sino que nada se guarda solo.
 */
object KaraokeRecordings {

    private const val EXTENSION = "m4a"
    private const val LOG_TAG = "KaraokeRecordings"
    private const val TEMP_DIR = "karaoke_tmp"

    /** `REC-` + fecha y hora. El guion final del prefijo evita chocar con "RECuerdos.mp3". */
    const val PREFIX = "REC-"

    /** Bitrate y canales de la grabación. Mono basta: un micro y un cantante. */
    const val BITRATE_BPS = 64_000
    const val CHANNELS = 1
    const val SAMPLE_RATE_HZ = 44_100

    private val NAME_PATTERN: Pattern =
        Pattern.compile("^REC-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.[A-Za-z0-9]+$")

    /**
     * Nombre de una toma: `REC-AAAA-MM-DD-HH-MM-SS.m4a`, en hora local — es la que el usuario
     * reconoce al ver el archivo en su carpeta.
     */
    fun fileNameFor(startedAtMs: Long): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(java.util.Date(startedAtMs))
        return "$PREFIX$stamp.$EXTENSION"
    }

    /**
     * ¿Es este archivo una grabación de karaoke nuestra?
     *
     * Se exige el formato completo de fecha y no solo el prefijo: un archivo del usuario llamado
     * `REC-ensayo.m4a` debe seguir apareciendo en su biblioteca. Filtrar de más es peor que
     * filtrar de menos — esconderle música propia es un fallo silencioso.
     */
    fun isRecordingFileName(displayName: String): Boolean =
        NAME_PATTERN.matcher(displayName.trim()).matches()

    /** Carpeta temporal privada donde graba `MediaRecorder` antes de publicar. */
    private fun tempDirectory(context: Context): File =
        File(context.cacheDir, TEMP_DIR).apply { mkdirs() }

    fun newTempFile(context: Context, startedAtMs: Long): File =
        File(tempDirectory(context), fileNameFor(startedAtMs))

    /** Borra temporales huérfanos (app matada a media grabación). */
    fun clearTemp(context: Context) {
        tempDirectory(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * Publica una toma en la carpeta pública del usuario. Devuelve `true` si se guardó.
     *
     * No se pasan `title`/`artist`: la toma no es música de nadie, y rellenar esos campos la haría
     * parecer una canción más en cualquier reproductor.
     */
    fun publish(context: Context, tempFile: File): String? {
        if (!tempFile.exists() || tempFile.length() <= 0L) return null
        val saved = runCatching {
            StorageSettings.saveAudioFromFile(
                context = context,
                source = tempFile,
                displayName = tempFile.name,
            )
        }.onFailure { Logger.e(LOG_TAG, "No se pudo publicar la grabación ${tempFile.name}", it) }
            .getOrNull()
        runCatching { tempFile.delete() }
        // El nombre real puede diferir del pedido si el sistema desambigua colisiones.
        return saved?.takeIf { it.isNotBlank() } ?: tempFile.name.takeIf { saved != null }
    }

    /** Una grabación guardada, ya en la carpeta del usuario. */
    data class Saved(val id: Long, val displayName: String, val sizeBytes: Long)

    /**
     * Grabaciones guardadas, vía MediaStore. Se consulta por prefijo y luego se valida el nombre
     * completo con [isRecordingFileName], porque `LIKE 'REC-%'` también casaría `REC-ensayo.m4a`.
     */
    fun listSaved(context: Context): List<Saved> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
        )
        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$PREFIX%"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol).orEmpty()
                        if (!isRecordingFileName(name)) continue
                        add(
                            Saved(
                                id = cursor.getLong(idCol),
                                displayName = name,
                                sizeBytes = cursor.getLong(sizeCol),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.onFailure { Logger.e(LOG_TAG, "No se pudieron listar las grabaciones", it) }
            .getOrDefault(emptyList())
    }

    fun totalBytes(context: Context): Long = listSaved(context).sumOf { it.sizeBytes }

    /** Borra todas las grabaciones guardadas y el índice. Devuelve cuántas se borraron. */
    fun deleteAllSaved(context: Context): Int {
        val deleted = deleteFiles(context)
        KaraokeRecordingIndex.clear(context)
        return deleted
    }

    private fun deleteFiles(context: Context): Int = listSaved(context).count { saved ->
        runCatching {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                saved.id,
            )
            context.contentResolver.delete(uri, null, null) > 0
        }.onFailure { Logger.e(LOG_TAG, "No se pudo borrar ${saved.displayName}", it) }
            .getOrDefault(false)
    }
}

/**
 * Tamaño legible para la UI (Perfil). Se queda en enteros a partir de MB: nadie necesita saber
 * que ocupa 81,37 MB.
 */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L * 1024L -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    // Locale explicita: es una cadena que ve el usuario, asi que el separador decimal debe ser
    // el suyo ("1,6 MB" en espanol, "1.6 MB" en ingles).
    bytes < 10L * 1024L * 1024L ->
        String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> "${bytes / (1024L * 1024L)} MB"
}
