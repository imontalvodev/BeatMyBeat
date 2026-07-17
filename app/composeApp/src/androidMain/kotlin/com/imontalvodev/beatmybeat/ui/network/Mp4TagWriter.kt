package com.imontalvodev.beatmybeat.ui.network

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Escribe tags iTunes/MP4 (©nam, ©ART, ©alb, covr) en un archivo m4a sin
 * depender de ninguna librería externa. Funciona leyendo el contenedor MP4,
 * localizando o creando el árbol udta→meta→ilst, y reemplazando/insertando
 * los átomos de texto y carátula.
 *
 * Estrategia: si el archivo ya contiene un átomo 'udta/meta/ilst', lo
 * sustituimos completo. Si no, insertamos el bloque justo antes del átomo
 * 'mdat' (datos de audio). En ambos casos reescribimos el archivo en un
 * temporal y después lo renombramos.
 */
object Mp4TagWriter {

    fun write(
        src: File,
        dst: File,
        title: String,
        artist: String,
        album: String,
        artworkJpeg: ByteArray?,
    ) {
        if (!src.exists() || src.length() < 8) throw Exception("Archivo fuente inválido: ${src.name}")
        // Limitar el tamaño de artwork para evitar OOM en dispositivos con poca RAM
        val safeArtwork = if (artworkJpeg != null && artworkJpeg.size > 512 * 1024) {
            artworkJpeg.copyOf(512 * 1024) // truncar a 512KB máximo
        } else artworkJpeg

        val srcBytes = src.readBytes()
        val ilstBytes = buildIlst(title, artist, album, safeArtwork)
        val udtaBytes = wrapInBox("udta", wrapInBox("meta", buildMeta(ilstBytes)))

        // Buscar y reemplazar un 'udta' existente, o insertar antes de 'mdat'
        val result = replaceOrInsertUdta(srcBytes, udtaBytes)
        dst.writeBytes(result)
    }

    // -------------------------------------------------------------------------
    // Construcción de átomos
    // -------------------------------------------------------------------------

    private fun buildIlst(
        title: String,
        artist: String,
        album: String,
        artwork: ByteArray?,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        if (title.isNotBlank()) baos.write(textBox("\u00a9nam", title))
        if (artist.isNotBlank()) baos.write(textBox("\u00a9ART", artist))
        if (album.isNotBlank()) baos.write(textBox("\u00a9alb", album))
        if (artwork != null && artwork.isNotEmpty()) baos.write(covrBox(artwork))
        return wrapInBox("ilst", baos.toByteArray())
    }

    /**
     * Un átomo de texto iTunes tiene la forma:
     *   [©nam]
     *     [data] flags=1 (UTF-8), locale=0, <utf8 bytes>
     */
    private fun textBox(name: String, value: String): ByteArray {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        // data box: 4 bytes size + 4 "data" + 4 flags (1=UTF-8) + 4 locale + payload
        val dataPayload = ByteArray(8 + utf8.size)
        val db = ByteBuffer.wrap(dataPayload)
        db.putInt(1)     // flags = 1 (well-known type: UTF-8)
        db.putInt(0)     // locale
        db.put(utf8)
        val dataBox = wrapInBox("data", dataPayload)
        return wrapInBox(name, dataBox)
    }

    /** covr atom: flags=13 (JPEG) o 14 (PNG). Usamos siempre JPEG. */
    private fun covrBox(jpeg: ByteArray): ByteArray {
        val dataPayload = ByteArray(8 + jpeg.size)
        val db = ByteBuffer.wrap(dataPayload)
        db.putInt(13)    // flags = 13 = JPEG
        db.putInt(0)     // locale
        db.put(jpeg)
        val dataBox = wrapInBox("data", dataPayload)
        return wrapInBox("covr", dataBox)
    }

    /**
     * El átomo 'meta' en iTunes tiene 4 bytes extra (version+flags=0) antes
     * del ilst hijo, a diferencia de los demás átomos.
     */
    private fun buildMeta(ilstBytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0, 0, 0, 0)) // version + flags
        baos.write(ilstBytes)
        return baos.toByteArray()
    }

    /** Envuelve `payload` en un átomo con nombre de 4 caracteres. */
    private fun wrapInBox(name: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val buf = ByteBuffer.allocate(size)
        buf.putInt(size)
        buf.put(name.toByteArray(Charsets.ISO_8859_1), 0, 4)
        buf.put(payload)
        return buf.array()
    }

    // -------------------------------------------------------------------------
    // Localización e inserción en el contenedor MP4
    // -------------------------------------------------------------------------

    /**
     * El spec MP4/iTunes exige que 'udta' (con 'meta/ilst' dentro) sea HIJO de 'moov', no un átomo
     * de nivel raíz — un 'udta' top-level es ignorado por la inmensa mayoría de reproductores/
     * MediaMetadataRetriever. Localizamos 'moov', buscamos 'udta' entre sus hijos (para
     * reemplazarlo) y si no existe lo insertamos como último hijo de 'moov', parcheando el tamaño
     * de 'moov' para reflejar el nuevo contenido.
     */
    private fun replaceOrInsertUdta(src: ByteArray, udtaBytes: ByteArray): ByteArray {
        val topAtoms = parseAtoms(src, 0, src.size)
        val moovAtom = topAtoms.firstOrNull { it.name == "moov" }
            ?: throw Exception("Archivo MP4 sin átomo 'moov': no se pueden escribir tags")

        val moovChildren = parseAtoms(src, moovAtom.offset + moovAtom.headerSize, moovAtom.offset + moovAtom.size)
        val existingUdta = moovChildren.firstOrNull { it.name == "udta" }

        val spliceStart: Int
        val spliceEnd: Int
        if (existingUdta != null) {
            spliceStart = existingUdta.offset
            spliceEnd = existingUdta.offset + existingUdta.size
        } else {
            // Insertar como último hijo, justo antes del cierre de 'moov'.
            val insertAt = moovAtom.offset + moovAtom.size
            spliceStart = insertAt
            spliceEnd = insertAt
        }

        val delta = udtaBytes.size - (spliceEnd - spliceStart)
        val newMoovSize = moovAtom.size + delta
        require(newMoovSize.toLong() <= UInt.MAX_VALUE.toLong()) { "moov excede el tamaño de átomo de 32 bits" }

        val out = ByteArrayOutputStream(src.size + delta)
        out.write(src, 0, spliceStart)
        out.write(udtaBytes)
        out.write(src, spliceEnd, src.size - spliceEnd)
        val result = out.toByteArray()

        // 'moov' usa siempre header de 32 bits en la práctica de esta app (nunca lo escribimos con
        // tamaño extendido); parchear los 4 bytes de tamaño en su offset original, que cae dentro
        // del prefijo sin modificar (moovAtom.offset < spliceStart siempre).
        writeUInt32BigEndian(result, moovAtom.offset, newMoovSize)
        return result
    }

    private fun writeUInt32BigEndian(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value ushr 24) and 0xFF).toByte()
        data[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private data class Atom(val name: String, val offset: Int, val size: Int, val headerSize: Int)

    /**
     * Recorre los átomos dentro de `[rangeStart, rangeEnd)`. Maneja tamaño `0` (el átomo se
     * extiende hasta el final del rango que lo contiene — válido para el último átomo del
     * archivo/contenedor) y tamaño `1` (tamaño real de 64 bits en los 8 bytes siguientes al
     * header de 8 bytes), en vez de tratarlos como inválidos.
     */
    private fun parseAtoms(data: ByteArray, rangeStart: Int, rangeEnd: Int): List<Atom> {
        val atoms = mutableListOf<Atom>()
        var pos = rangeStart
        while (pos + 8 <= rangeEnd) {
            val size32 = ByteBuffer.wrap(data, pos, 4).int
            val name = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            val headerSize: Int
            val size: Long = when {
                size32 == 0 -> {
                    headerSize = 8
                    (rangeEnd - pos).toLong()
                }
                size32 == 1 -> {
                    if (pos + 16 > rangeEnd) break
                    headerSize = 16
                    ByteBuffer.wrap(data, pos + 8, 8).long
                }
                size32 < 8 -> break // átomo realmente inválido
                else -> {
                    headerSize = 8
                    size32.toLong() and 0xFFFFFFFFL
                }
            }
            if (size < headerSize || pos + size > rangeEnd || size > Int.MAX_VALUE) break
            atoms.add(Atom(name, pos, size.toInt(), headerSize))
            pos += size.toInt()
        }
        return atoms
    }
}
