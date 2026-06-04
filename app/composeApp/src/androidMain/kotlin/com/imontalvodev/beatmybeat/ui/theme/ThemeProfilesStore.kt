package com.imontalvodev.beatmybeat.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private fun linearSrgb(channel: Float): Double {
    val v = channel.toDouble().coerceIn(0.0, 1.0)
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun relativeLuminance(c: Color): Double {
    val r = linearSrgb(c.red)
    val g = linearSrgb(c.green)
    val b = linearSrgb(c.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun contrastRatio(c1: Color, c2: Color): Double {
    val l1 = relativeLuminance(c1) + 0.05
    val l2 = relativeLuminance(c2) + 0.05
    return max(l1, l2) / min(l1, l2)
}

/** Perfiles guardados corruptos o con poco contraste rompen toda la UI (texto ≈ fondo). */

/** ARGB en 32 bits como Long sin signo (evita -1 en JSON y colores corruptos al recargar). */
private fun colorToStoredLong(c: Color): Long = c.toArgb().toLong() and 0xFFFF_FFFFL

private fun storedLongToColor(raw: Long): Color {
    val argb = (raw.toULong() and 0xFFFF_FFFFu).toInt()
    return Color(argb)
}

/**
 * Solo detecta datos claramente rotos (transparencias o texto casi igual al fondo).
 * Antes también se exigía contraste del primario con negro: cualquier acento claro
 * fallaba al reiniciar y el perfil entero se sustituía por el tema por defecto.
 */
private fun BeatMyBeatThemeProfile.hasMinimallyReadableColors(): Boolean {
    if (surface.alpha < 0.2f || onSurface.alpha < 0.2f) return false
    if (backgroundBottom.alpha < 0.2f) return false
    if (primary.alpha < 0.2f) return false
    if (contrastRatio(surface, onSurface) < 1.12) return false
    if (contrastRatio(backgroundBottom, onSurface) < 1.12) return false
    return true
}

private fun JSONObject.optArgbLong(key: String, default: Long): Long {
    if (!has(key) || isNull(key)) return default
    return when (val v = get(key)) {
        is Long -> v
        is Int -> v.toLong() and 0xFFFF_FFFFL
        is Double -> v.toLong() and 0xFFFF_FFFFL
        else -> default
    }
}

class ThemeProfilesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("beatmybeat_theme_profiles", Context.MODE_PRIVATE)

    fun loadProfiles(): List<BeatMyBeatThemeProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) return defaultProfiles()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return defaultProfiles()
        val out = mutableListOf<BeatMyBeatThemeProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var profile = BeatMyBeatThemeProfile(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Custom"),
                backgroundTop = storedLongToColor(
                    o.optArgbLong("backgroundTop", colorToStoredLong(NeonMintProfile.backgroundTop)),
                ),
                backgroundBottom = storedLongToColor(
                    o.optArgbLong("backgroundBottom", colorToStoredLong(NeonMintProfile.backgroundBottom)),
                ),
                primary = storedLongToColor(o.optArgbLong("primary", colorToStoredLong(NeonMintProfile.primary))),
                primaryVariant = storedLongToColor(
                    o.optArgbLong("primaryVariant", colorToStoredLong(NeonMintProfile.primaryVariant)),
                ),
                secondary = storedLongToColor(o.optArgbLong("secondary", colorToStoredLong(NeonMintProfile.secondary))),
                surface = storedLongToColor(o.optArgbLong("surface", colorToStoredLong(NeonMintProfile.surface))),
                onSurface = storedLongToColor(o.optArgbLong("onSurface", colorToStoredLong(NeonMintProfile.onSurface))),
                onSurfaceMuted = storedLongToColor(
                    o.optArgbLong("onSurfaceMuted", colorToStoredLong(NeonMintProfile.onSurfaceMuted)),
                ),
            )
            val isBuiltin = profile.id == NeonMintProfile.id || profile.id == CherryProfile.id
            if (!isBuiltin && !profile.hasMinimallyReadableColors()) {
                profile = NeonMintProfile.copy(id = profile.id, name = profile.name)
            }
            out += profile
        }
        if (out.isEmpty()) return defaultProfiles()
        val sanitized = out.map { profile ->
            // Si es un perfil built-in, priorizamos la versión actual del código
            // para reflejar cambios globales de paleta al instante.
            when (profile.id) {
                NeonMintProfile.id -> NeonMintProfile
                CherryProfile.id -> CherryProfile
                else -> profile
            }
        }
        return if (sanitized.isEmpty()) defaultProfiles() else sanitized
    }

    fun saveProfiles(profiles: List<BeatMyBeatThemeProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("backgroundTop", colorToStoredLong(p.backgroundTop))
                    put("backgroundBottom", colorToStoredLong(p.backgroundBottom))
                    put("primary", colorToStoredLong(p.primary))
                    put("primaryVariant", colorToStoredLong(p.primaryVariant))
                    put("secondary", colorToStoredLong(p.secondary))
                    put("surface", colorToStoredLong(p.surface))
                    put("onSurface", colorToStoredLong(p.onSurface))
                    put("onSurfaceMuted", colorToStoredLong(p.onSurfaceMuted))
                },
            )
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).commit()
    }

    fun loadActiveProfileId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun saveActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).commit()
    }

    /**
     * Corrige ids guardados que ya no existen (p. ej. migraciones) para que el tema activo
     * coincida con la lista persistida.
     */
    fun coerceActiveProfileId(validIds: Collection<String>, fallbackId: String): String {
        val saved = loadActiveProfileId()
        val resolved = saved?.takeIf { it in validIds } ?: fallbackId
        if (resolved != saved) saveActiveProfileId(resolved)
        return resolved
    }

    fun defaultProfiles(): List<BeatMyBeatThemeProfile> = listOf(NeonMintProfile)

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
    }
}

