package com.imontalvodev.beatmybeat.ui.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleCaseTest {

    @Test
    fun mixedCase_isKeptAsIs() {
        assertEquals("Pharrell Williams y Nile Rodgers", "Pharrell Williams y Nile Rodgers".toTitleCaseSimple())
        assertEquals("The xx", "The xx".toTitleCaseSimple())
    }

    @Test
    fun singleUppercaseWord_isKeptAsAcronym() {
        assertEquals("AC/DC", "AC/DC".toTitleCaseSimple())
        assertEquals("MGMT", "MGMT".toTitleCaseSimple())
    }

    @Test
    fun allUpperOrLower_isNormalizedWithMinorWords() {
        assertEquals("Daft Punk", "DAFT PUNK".toTitleCaseSimple())
        assertEquals("A Day in the Life", "a day in the life".toTitleCaseSimple())
        assertEquals("Rosalía y Bad Bunny", "ROSALÍA Y BAD BUNNY".toTitleCaseSimple())
    }

    @Test
    fun blank_returnsEmpty() {
        assertEquals("", "   ".toTitleCaseSimple())
    }
}
