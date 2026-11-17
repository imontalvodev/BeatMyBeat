package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fotos personalizadas de las playlists.
 *
 * La imagen elegida se copia a `filesDir/playlist_covers/`, recortada a cuadrado y reducida: así
 * no depende del permiso del selector (la URI deja de ser válida) ni de que el archivo original
 * se mueva o se borre, y una foto de 12 MP no se decodifica entera cada vez que se pinta la lista.
 */
internal object PlaylistCovers {
    private const val DIR = "playlist_covers"
    private const val SIZE_PX = 512
    private const val JPEG_QUALITY = 88

    /** Potencia de 2 más grande que deja ambos lados por encima de [target]. */
    internal fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Recorte cuadrado centrado: (izquierda, arriba, lado). */
    internal fun squareCrop(width: Int, height: Int): Triple<Int, Int, Int> {
        val side = minOf(width, height)
        return Triple((width - side) / 2, (height - side) / 2, side)
    }

    fun file(path: String?): File? = path?.let { File(it) }?.takeIf { it.isFile }

    fun delete(path: String?) {
        runCatching { path?.let { File(it).delete() } }
    }

    /**
     * Copia [uri] como portada de la playlist [playlistId] y devuelve la ruta nueva, o null si no
     * se pudo leer. El nombre lleva la hora para que Coil no sirva la imagen anterior de la caché.
     */
    suspend fun import(context: Context, playlistId: Long, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, SIZE_PX)
                }
                val decoded = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null

                val rotation = resolver.openInputStream(uri)?.use { stream ->
                    when (
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    ) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val (left, top, side) = squareCrop(decoded.width, decoded.height)
                val matrix = Matrix().apply {
                    if (rotation != 0f) postRotate(rotation)
                    val scale = SIZE_PX.toFloat() / side
                    if (scale < 1f) postScale(scale, scale)
                }
                val square = Bitmap.createBitmap(decoded, left, top, side, side, matrix, true)
                if (square !== decoded) decoded.recycle()

                val dir = File(context.filesDir, DIR).apply { mkdirs() }
                val out = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
                out.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                square.recycle()
                out.absolutePath
            }.getOrNull()
        }
}

/** Mueve el elemento [from] a la posición [to]; índices fuera de rango dejan la lista igual. */
internal fun <T> List<T>.movedItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}
