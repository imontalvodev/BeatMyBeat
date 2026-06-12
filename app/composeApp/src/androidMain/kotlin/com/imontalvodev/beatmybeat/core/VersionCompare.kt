package com.imontalvodev.beatmybeat.core

/**
 * Comparación numérica de versiones tipo semver (major.minor.patch).
 * Acepta prefijos `v` y sufijos no numéricos (`1.0.1-beta` → 1.0.1).
 */
object VersionCompare {

    fun parseSegments(version: String): List<Int> {
        val normalized = version.trim().removePrefix("v").removePrefix("V")
        if (normalized.isBlank()) return listOf(0)
        return normalized.split('.', '-', '_')
            .mapNotNull { segment ->
                segment.takeWhile { it.isDigit() }.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
    }

    /** Negativo si left es menor que right; positivo si es mayor; 0 si son iguales. */
    fun compare(left: String, right: String): Int {
        val leftSegments = parseSegments(left)
        val rightSegments = parseSegments(right)
        val maxSize = maxOf(leftSegments.size, rightSegments.size)
        for (index in 0 until maxSize) {
            val l = leftSegments.getOrElse(index) { 0 }
            val r = rightSegments.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean =
        compare(candidate, installed) > 0
}
