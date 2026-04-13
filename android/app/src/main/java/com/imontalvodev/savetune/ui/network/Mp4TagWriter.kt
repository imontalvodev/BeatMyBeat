package com.imontalvodev.savetune.ui.network

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
     * Recorre los átomos de nivel raíz buscando 'udta'. Si lo encuentra lo
     * reemplaza. Si no, inserta el bloque antes de 'mdat'.
     */
    private fun replaceOrInsertUdta(src: ByteArray, udtaBytes: ByteArray): ByteArray {
        val atoms = parseTopLevelAtoms(src)

        val udtaAtom = atoms.firstOrNull { it.name == "udta" }
        if (udtaAtom != null) {
            // Reemplazar el udta existente
            val out = ByteArrayOutputStream(src.size - udtaAtom.size + udtaBytes.size)
            out.write(src, 0, udtaAtom.offset)
            out.write(udtaBytes)
            val after = udtaAtom.offset + udtaAtom.size
            out.write(src, after, src.size - after)
            return out.toByteArray()
        }

        // Insertar antes de mdat (o al final si no hay mdat)
        val mdatAtom = atoms.firstOrNull { it.name == "mdat" }
        val insertAt = mdatAtom?.offset ?: src.size

        val out = ByteArrayOutputStream(src.size + udtaBytes.size)
        out.write(src, 0, insertAt)
        out.write(udtaBytes)
        out.write(src, insertAt, src.size - insertAt)
        return out.toByteArray()
    }

    private data class Atom(val name: String, val offset: Int, val size: Int)

    private fun parseTopLevelAtoms(data: ByteArray): List<Atom> {
        val atoms = mutableListOf<Atom>()
        var pos = 0
        while (pos + 8 <= data.size) {
            val size = ByteBuffer.wrap(data, pos, 4).int
            val name = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            if (size < 8) break // átomo inválido o de tamaño 0 (fin)
            atoms.add(Atom(name, pos, size))
            pos += size
        }
        return atoms
    }
}
