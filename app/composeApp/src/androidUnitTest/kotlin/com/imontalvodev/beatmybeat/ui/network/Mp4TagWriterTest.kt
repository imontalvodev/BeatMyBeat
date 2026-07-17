package com.imontalvodev.beatmybeat.ui.network

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Mp4TagWriterTest {

    private fun box(name: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val buf = ByteBuffer.allocate(size)
        buf.putInt(size)
        buf.put(name.toByteArray(Charsets.ISO_8859_1), 0, 4)
        buf.put(payload)
        return buf.array()
    }

    /** MP4 mínimo: ftyp + moov(mvhd[+udta]) + mdat. */
    private fun buildMinimalMp4(includeUdta: Boolean = false): ByteArray {
        val ftyp = box("ftyp", "isomiso2mp41".toByteArray(Charsets.ISO_8859_1))
        val mvhd = box("mvhd", ByteArray(4))
        val existingUdta = box("udta", box("meta", byteArrayOf(0, 0, 0, 0) + box("ilst", ByteArray(0))))
        val moovChildren = if (includeUdta) mvhd + existingUdta else mvhd
        val moov = box("moov", moovChildren)
        val mdat = box("mdat", "audio-bytes-placeholder".toByteArray(Charsets.UTF_8))
        return ftyp + moov + mdat
    }

    private data class SimpleAtom(val name: String, val offset: Int, val size: Int)

    /** Parser de verificación independiente del de producción (test de caja negra). */
    private fun readAtomsAt(data: ByteArray, start: Int, end: Int): List<SimpleAtom> {
        val atoms = mutableListOf<SimpleAtom>()
        var pos = start
        while (pos + 8 <= end) {
            val size = ByteBuffer.wrap(data, pos, 4).int
            val name = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            if (size < 8) break
            atoms.add(SimpleAtom(name, pos, size))
            pos += size
        }
        return atoms
    }

    @Test
    fun write_insertsUdtaAsChildOfMoov_notTopLevel() {
        val src = File.createTempFile("mp4writer_in", ".m4a").apply { writeBytes(buildMinimalMp4()) }
        val dst = File.createTempFile("mp4writer_out", ".m4a")
        try {
            Mp4TagWriter.write(src, dst, title = "T", artist = "A", album = "Al", artworkJpeg = null)

            val result = dst.readBytes()
            val topAtoms = readAtomsAt(result, 0, result.size)
            assertTrue(topAtoms.none { it.name == "udta" }, "udta no debe quedar a nivel raíz")

            val moov = topAtoms.first { it.name == "moov" }
            val moovChildren = readAtomsAt(result, moov.offset + 8, moov.offset + moov.size)
            assertTrue(moovChildren.any { it.name == "udta" }, "udta debe ser hijo de moov")

            // El tamaño declarado de moov debe cuadrar exactamente con sus hijos reales.
            val lastChild = moovChildren.last()
            assertEquals(moov.offset + moov.size, lastChild.offset + lastChild.size)
        } finally {
            src.delete()
            dst.delete()
        }
    }

    @Test
    fun write_replacesExistingUdtaInPlace_noDuplicates() {
        val src = File.createTempFile("mp4writer_in", ".m4a").apply { writeBytes(buildMinimalMp4(includeUdta = true)) }
        val dst = File.createTempFile("mp4writer_out", ".m4a")
        try {
            Mp4TagWriter.write(src, dst, title = "New Title", artist = "New Artist", album = "New Album", artworkJpeg = null)

            val result = dst.readBytes()
            val topAtoms = readAtomsAt(result, 0, result.size)
            val moov = topAtoms.first { it.name == "moov" }
            val moovChildren = readAtomsAt(result, moov.offset + 8, moov.offset + moov.size)
            assertEquals(1, moovChildren.count { it.name == "udta" }, "no debe duplicar udta al reemplazar")
        } finally {
            src.delete()
            dst.delete()
        }
    }

    @Test
    fun write_throwsWhenNoMoovAtom() {
        val src = File.createTempFile("mp4writer_in", ".m4a").apply {
            writeBytes(box("ftyp", ByteArray(4)) + box("mdat", ByteArray(16)))
        }
        val dst = File.createTempFile("mp4writer_out", ".m4a")
        try {
            assertFailsWith<Exception> {
                Mp4TagWriter.write(src, dst, title = "T", artist = "A", album = "Al", artworkJpeg = null)
            }
        } finally {
            src.delete()
            dst.delete()
        }
    }
}
