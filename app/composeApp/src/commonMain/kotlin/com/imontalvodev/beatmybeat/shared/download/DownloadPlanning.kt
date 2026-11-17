package com.imontalvodev.beatmybeat.shared.download

/** Formato de salida que elige el usuario. `id` se persiste en intents: no cambiarlo. */
enum class DownloadFormat(val id: String, val extension: String, val label: String) {
    MP3("mp3", "mp3", "MP3"),
    M4A("m4a", "m4a", "M4A"),
    AAC("aac", "aac", "AAC"),
    OGG("ogg", "ogg", "OGG"),
    FLAC("flac", "flac", "FLAC"),
    WAV("wav", "wav", "WAV");

    companion object {
        fun fromId(raw: String?): DownloadFormat =
            entries.firstOrNull { it.id.equals(raw.orEmpty(), ignoreCase = true) } ?: MP3
    }
}

enum class AudioCodec {
    AAC, OPUS, VORBIS, MP3, UNKNOWN;

    companion object {
        /** Interpreta el códec que publica YouTube ("mp4a.40.2", "opus"…). */
        fun fromCodecString(codec: String?): AudioCodec {
            val c = codec.orEmpty().lowercase()
            return when {
                c.startsWith("mp4a") || c == "aac" -> AAC
                c.startsWith("opus") -> OPUS
                c.startsWith("vorbis") -> VORBIS
                c.startsWith("mp3") || c == "mp4a.40.34" -> MP3
                else -> UNKNOWN
            }
        }
    }
}

/** Stream candidato independiente del extractor (NewPipe en Android). */
data class SourceStream(
    val url: String,
    val codec: AudioCodec,
    val bitrateKbps: Int,
    /** Extensión del contenedor tal como se descarga ("m4a", "webm", "mp4"). */
    val containerExtension: String,
    val isVideo: Boolean = false,
    /** `false` para pistas dobladas, descriptivas o secundarias. */
    val isOriginalAudio: Boolean = true,
    /** `false` para DASH/HLS: el "contenido" es un manifiesto, no el fichero de audio. */
    val isDirectDownload: Boolean = true,
    val contentLength: Long = -1L,
)

/** Sin recodificar: el audio de YouTube ya es con pérdida y recodificarlo solo lo empeora. */
fun canCopyAudio(source: AudioCodec, target: DownloadFormat): Boolean = when (target) {
    DownloadFormat.M4A, DownloadFormat.AAC -> source == AudioCodec.AAC
    DownloadFormat.OGG -> source == AudioCodec.OPUS || source == AudioCodec.VORBIS
    DownloadFormat.MP3 -> source == AudioCodec.MP3
    DownloadFormat.FLAC, DownloadFormat.WAV -> false
}

/** Formatos cuyo contenedor admite carátula incrustada con ffmpeg. */
fun supportsEmbeddedCover(target: DownloadFormat): Boolean =
    target == DownloadFormat.MP3 || target == DownloadFormat.M4A || target == DownloadFormat.FLAC

/**
 * Elige el stream a descargar:
 * 1. Solo descargas directas (nunca un manifiesto DASH/HLS).
 * 2. Audio puro antes que vídeo con audio.
 * 3. Pista original antes que dobladas: YouTube dobla automáticamente cada vez más vídeos.
 * 4. Si el formato pedido admite copiar el códec, se prefiere ese códec (sin recodificar).
 * 5. A igualdad, el de mayor bitrate.
 */
fun chooseSourceStream(candidates: List<SourceStream>, target: DownloadFormat): SourceStream? {
    val direct = candidates.filter { it.isDirectDownload && it.url.isNotBlank() }
    val audioFirst = direct.filter { !it.isVideo }.ifEmpty { direct }
    val originalFirst = audioFirst.filter { it.isOriginalAudio }.ifEmpty { audioFirst }
    val copyable = originalFirst.filter { canCopyAudio(it.codec, target) }
    return copyable.ifEmpty { originalFirst }.maxByOrNull { it.bitrateKbps }
}

data class TrackTags(
    val title: String,
    val artist: String,
    val album: String,
)

/**
 * Argumentos de ffmpeg para pasar del fichero descargado al formato final en **una sola**
 * pasada. Se construyen como lista (para `FFmpegKit.executeWithArguments`), así que títulos con
 * comillas, barras o `$` no necesitan escaparse ni pueden romper el comando.
 */
fun buildFfmpegArguments(
    inputPath: String,
    coverPath: String?,
    outputPath: String,
    source: AudioCodec,
    target: DownloadFormat,
    tags: TrackTags,
): List<String> = buildList {
    val withCover = coverPath != null && supportsEmbeddedCover(target)
    addAll(listOf("-y", "-hide_banner", "-i", inputPath))
    if (withCover) addAll(listOf("-i", coverPath))
    addAll(listOf("-map", "0:a:0"))
    if (withCover) {
        addAll(
            listOf(
                "-map", "1:v:0",
                "-c:v", "mjpeg",
                "-disposition:v:0", "attached_pic",
                "-metadata:s:v", "title=Album cover",
                "-metadata:s:v", "comment=Cover (front)",
            ),
        )
    } else {
        add("-vn")
    }

    if (canCopyAudio(source, target)) {
        addAll(listOf("-c:a", "copy"))
    } else {
        when (target) {
            DownloadFormat.MP3 -> addAll(listOf("-c:a", "mp3", "-b:a", "192k"))
            DownloadFormat.M4A, DownloadFormat.AAC -> addAll(listOf("-c:a", "aac", "-b:a", "192k"))
            DownloadFormat.OGG -> addAll(listOf("-c:a", "libvorbis", "-q:a", "5"))
            DownloadFormat.FLAC -> addAll(listOf("-c:a", "flac"))
            DownloadFormat.WAV -> addAll(listOf("-c:a", "pcm_s16le"))
        }
    }
    when (target) {
        DownloadFormat.MP3 -> addAll(listOf("-id3v2_version", "3"))
        DownloadFormat.AAC -> addAll(listOf("-f", "adts"))
        else -> Unit
    }

    // Etiquetas del contenedor de origen fuera: solo quedan las nuestras.
    addAll(listOf("-map_metadata", "-1"))
    addAll(listOf("-metadata", "title=${tags.title}"))
    addAll(listOf("-metadata", "artist=${tags.artist}"))
    addAll(listOf("-metadata", "album=${tags.album}"))
    add(outputPath)
}

private val FORBIDDEN_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

/** Nombre de fichero seguro en cualquier sistema de archivos (FAT/exFAT de tarjetas SD incluidos). */
fun safeFileBaseName(title: String, fallback: String = "track"): String =
    title.replace(FORBIDDEN_FILENAME_CHARS, "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.')
        .take(150)
        .ifBlank { fallback }

/**
 * Nombre del fichero descargado: "Artista - Título". Solo con el título, dos canciones distintas
 * con el mismo nombre se pisaban (la carpeta SAF borra el fichero existente al guardar).
 */
fun downloadFileBaseName(title: String, artist: String): String {
    val cleanArtist = artist.trim().takeUnless { it.isBlank() || it.equals("unknown artist", ignoreCase = true) }
    val base = if (cleanArtist != null && !title.contains(cleanArtist, ignoreCase = true)) {
        "$cleanArtist - ${title.trim()}"
    } else {
        title
    }
    return safeFileBaseName(base)
}
