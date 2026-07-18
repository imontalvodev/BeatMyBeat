package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphabetIndexTest {

    @Test
    fun `apunta a la primera aparicion de cada inicial`() {
        val index = buildAlphabetIndex(listOf("Abba", "Aerosmith", "Blur", "Cake"))
        assertEquals(mapOf('A' to 0, 'B' to 2, 'C' to 3), index)
    }

    @Test
    fun `la inicial no distingue mayusculas`() {
        val index = buildAlphabetIndex(listOf("abba", "Aerosmith"))
        assertEquals(mapOf('A' to 0), index)
    }

    @Test
    fun `lo que no empieza por letra cae en almohadilla`() {
        val index = buildAlphabetIndex(listOf("1979", "50 Cent", "!!!", "Air"))
        assertEquals(mapOf('#' to 0, 'A' to 3), index)
    }

    @Test
    fun `ignora espacios por delante`() {
        val index = buildAlphabetIndex(listOf("   Zeppelin"))
        assertEquals(mapOf('Z' to 0), index)
    }

    @Test
    fun `los titulos vacios o en blanco no generan entrada`() {
        val index = buildAlphabetIndex(listOf("", "   ", "Beck"))
        assertEquals(mapOf('B' to 2), index)
    }

    @Test
    fun `conserva el orden de aparicion, tambien en orden descendente`() {
        // Con SortOption.NAME_DESC la lista llega Z..A; el rail debe salir Z..A también,
        // no reordenado alfabéticamente.
        val index = buildAlphabetIndex(listOf("Zeta", "Motor", "Alfa"))
        assertEquals(listOf('Z', 'M', 'A'), index.keys.toList())
    }

    @Test
    fun `lista vacia da indice vacio`() {
        assertTrue(buildAlphabetIndex(emptyList()).isEmpty())
    }
}
