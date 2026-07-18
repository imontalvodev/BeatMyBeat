package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaraokeRecordingIndexTest {

    private fun entry(
        fileName: String = "REC-2026-07-18-11-09-57.m4a",
        trackId: Long = 42L,
        title: String = "Du Hast",
        artist: String = "Rammstein",
        recordedAtMs: Long = 1_700_000_000_000L,
    ) = KaraokeRecordingIndex.Entry(fileName, trackId, title, artist, recordedAtMs)

    @Test
    fun `una entrada sobrevive al viaje de ida y vuelta`() {
        val original = listOf(entry())
        val restored = KaraokeRecordingIndex.fromJson(KaraokeRecordingIndex.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `se conservan titulo y artista, no solo el id`() {
        // El id de MediaStore no es estable: si el usuario borra y vuelve a descargar la cancion,
        // cambia. Con titulo y artista la toma sigue siendo identificable.
        val restored = KaraokeRecordingIndex.fromJson(KaraokeRecordingIndex.toJson(listOf(entry())))
        assertEquals("Du Hast", restored.single().trackTitle)
        assertEquals("Rammstein", restored.single().trackArtist)
    }

    @Test
    fun `un indice corrupto no revienta, devuelve lo que se pueda`() {
        assertEquals(emptyList(), KaraokeRecordingIndex.fromJson("{no es json"))
        assertEquals(emptyList(), KaraokeRecordingIndex.fromJson(""))
        assertEquals(emptyList(), KaraokeRecordingIndex.fromJson(null))
    }

    @Test
    fun `las entradas sin nombre de archivo se descartan`() {
        // El nombre es la clave: sin el, la entrada no sirve para nada.
        val raw = """[{"trackId":1},{"fileName":"REC-2026-07-18-11-09-57.m4a","trackId":2}]"""
        val restored = KaraokeRecordingIndex.fromJson(raw)
        assertEquals(1, restored.size)
        assertEquals(2L, restored.single().trackId)
    }

    @Test
    fun `los campos que faltan caen a valores por defecto`() {
        val raw = """[{"fileName":"REC-2026-07-18-11-09-57.m4a"}]"""
        val restored = KaraokeRecordingIndex.fromJson(raw).single()
        assertEquals(0L, restored.trackId)
        assertEquals("", restored.trackTitle)
        assertEquals(0L, restored.recordedAtMs)
    }

    @Test
    fun `reconciliar quita las entradas cuyo archivo ya no existe`() {
        // El usuario puede borrar las grabaciones desde su explorador de archivos, y el indice no
        // se entera: sin esto acumularia entradas fantasma para siempre.
        val entries = listOf(
            entry(fileName = "REC-2026-07-18-11-09-57.m4a"),
            entry(fileName = "REC-2026-07-19-12-00-00.m4a"),
        )
        val alive = KaraokeRecordingIndex.reconcile(
            entries,
            existingFileNames = setOf("REC-2026-07-18-11-09-57.m4a"),
        )
        assertEquals(1, alive.size)
        assertEquals("REC-2026-07-18-11-09-57.m4a", alive.single().fileName)
    }

    @Test
    fun `reconciliar sin archivos deja el indice vacio`() {
        val alive = KaraokeRecordingIndex.reconcile(listOf(entry()), emptySet())
        assertTrue(alive.isEmpty())
    }
}
