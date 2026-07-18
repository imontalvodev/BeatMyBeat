package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.Context
import com.imontalvodev.beatmybeat.core.Logger
import java.io.File
import java.util.Locale

/**
 * Almacén de grabaciones de karaoke (Fase F).
 *
 * Política de espacio acordada:
 *
 * - **Voz sola**, mono AAC a 64 kbps: ~1,6 MB por toma de 3:30. Una canción descargada ocupa
 *   entre 3,5 y 7 MB, así que una grabación cuesta menos de la mitad que una canción.
 * - **Nada se guarda solo.** Al parar se escucha y se decide guardar o descartar; las tomas
 *   descartadas no llegan a ocupar. Es la palanca de espacio que más ahorra, y es gratis.
 * - Se guarda en almacenamiento **privado** de la app (`getExternalFilesDir`), así desinstalar
 *   limpia. Exportar a la carpeta pública es una acción explícita aparte.
 * - La mezcla voz+pista **no se persiste**: se generaría solo al exportar.
 */
object KaraokeRecordings {

    private const val DIR_NAME = "karaoke"
    private const val EXTENSION = "m4a"
    private const val LOG_TAG = "KaraokeRecordings"

    /** Bitrate y canales de la grabación. Mono basta: un micro y un cantante. */
    const val BITRATE_BPS = 64_000
    const val CHANNELS = 1
    const val SAMPLE_RATE_HZ = 44_100

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }

    /**
     * Nombre de archivo para una toma. Lleva el id de la pista para poder listarlas por canción,
     * y el instante para ordenarlas y no colisionar entre tomas de la misma canción.
     */
    fun fileNameFor(trackId: Long, startedAtMs: Long): String = "track${trackId}_$startedAtMs.$EXTENSION"

    /** Id de pista codificado en el nombre, o `null` si el nombre no sigue el formato. */
    fun trackIdFromFileName(name: String): Long? {
        if (!name.startsWith("track") || !name.endsWith(".$EXTENSION")) return null
        return name.removePrefix("track").substringBefore('_').toLongOrNull()
    }

    /** Instante de grabación codificado en el nombre, o `null` si no sigue el formato. */
    fun startedAtFromFileName(name: String): Long? {
        if (!name.startsWith("track") || !name.endsWith(".$EXTENSION")) return null
        return name.substringAfter('_', "").removeSuffix(".$EXTENSION").toLongOrNull()
    }

    fun newFile(context: Context, trackId: Long, startedAtMs: Long): File =
        File(directory(context), fileNameFor(trackId, startedAtMs))

    /** Todas las tomas guardadas, de más reciente a más antigua. */
    fun listAll(context: Context): List<File> =
        directory(context).listFiles()
            ?.filter { it.isFile && it.extension == EXTENSION }
            ?.sortedByDescending { startedAtFromFileName(it.name) ?: it.lastModified() }
            .orEmpty()

    /** Tomas de una canción concreta, de más reciente a más antigua. */
    fun listForTrack(context: Context, trackId: Long): List<File> =
        listAll(context).filter { trackIdFromFileName(it.name) == trackId }

    /** Bytes ocupados por todas las grabaciones. Para mostrarlo en Perfil. */
    fun totalBytes(context: Context): Long = listAll(context).sumOf { it.length() }

    fun delete(file: File): Boolean = runCatching { file.delete() }
        .onFailure { Logger.e(LOG_TAG, "No se pudo borrar la grabación ${file.name}", it) }
        .getOrDefault(false)

    fun deleteAll(context: Context): Int = listAll(context).count { delete(it) }
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
