package com.imontalvodev.beatmybeat.ui.feature.player

import android.content.Context
import com.imontalvodev.beatmybeat.core.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Relación grabación → canción, para poder listar las tomas de una pista.
 *
 * **Por qué un índice aparte y no metadatos del archivo:** el nombre acordado
 * (`REC-AAAA-MM-DD-HH-MM-SS`) no lleva id de canción, y colgar la relación de los tags de MediaStore
 * no vale — `StorageSettings.saveToCustomTree` ignora título/artista/álbum, así que para quien tenga
 * carpeta personalizada configurada no se guardaría nada. Un índice propio funciona en los dos casos.
 *
 * Se guarda el título y el artista además del id porque el id de MediaStore **no es estable**: si el
 * usuario borra y vuelve a descargar la canción, cambia. Con título y artista la toma sigue siendo
 * identificable aunque el id ya no case con nada.
 *
 * **Límite conocido:** si se borran los datos de la app, el índice se pierde y las grabaciones quedan
 * como archivos sueltos en la carpeta del usuario. Siguen siendo suyas y reproducibles; solo se
 * pierde a qué canción pertenecían.
 */
object KaraokeRecordingIndex {

    private const val PREFS = "karaoke_recordings_index"
    private const val KEY_ENTRIES = "entries"
    private const val LOG_TAG = "KaraokeRecordingIndex"

    data class Entry(
        /** Nombre del archivo publicado, que es la clave: `REC-AAAA-MM-DD-HH-MM-SS.m4a`. */
        val fileName: String,
        val trackId: Long,
        val trackTitle: String,
        val trackArtist: String,
        val recordedAtMs: Long,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Serialización (pura, testeable) ─────────────────────────────────────

    fun toJson(entries: List<Entry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("fileName", entry.fileName)
                    put("trackId", entry.trackId)
                    put("trackTitle", entry.trackTitle)
                    put("trackArtist", entry.trackArtist)
                    put("recordedAtMs", entry.recordedAtMs)
                },
            )
        }
        return array.toString()
    }

    /** Tolera JSON corrupto o entradas a medias: devuelve lo que se pueda leer, nunca lanza. */
    fun fromJson(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val fileName = obj.optString("fileName")
                if (fileName.isBlank()) continue
                add(
                    Entry(
                        fileName = fileName,
                        trackId = obj.optLong("trackId", 0L),
                        trackTitle = obj.optString("trackTitle"),
                        trackArtist = obj.optString("trackArtist"),
                        recordedAtMs = obj.optLong("recordedAtMs", 0L),
                    ),
                )
            }
        }
    }

    /**
     * Quita del índice las entradas cuyo archivo ya no existe. El usuario puede borrar las
     * grabaciones desde su explorador de archivos, y el índice no se entera.
     */
    fun reconcile(entries: List<Entry>, existingFileNames: Set<String>): List<Entry> =
        entries.filter { it.fileName in existingFileNames }

    // ── Persistencia ────────────────────────────────────────────────────────

    fun all(context: Context): List<Entry> =
        fromJson(prefs(context).getString(KEY_ENTRIES, null))

    private fun save(context: Context, entries: List<Entry>) {
        runCatching {
            prefs(context).edit().putString(KEY_ENTRIES, toJson(entries)).apply()
        }.onFailure { Logger.e(LOG_TAG, "No se pudo guardar el índice de grabaciones", it) }
    }

    fun add(context: Context, entry: Entry) {
        // Reemplaza si ya existía ese nombre: publicar sobreescribe el archivo del mismo nombre.
        val updated = all(context).filterNot { it.fileName == entry.fileName } + entry
        save(context, updated)
    }

    fun remove(context: Context, fileName: String) {
        save(context, all(context).filterNot { it.fileName == fileName })
    }

    fun clear(context: Context) = save(context, emptyList())

    /**
     * Tomas de una canción, de más reciente a más antigua, ya reconciliadas con los archivos que
     * siguen existiendo. Si el índice tenía huérfanas, se limpian de paso.
     */
    fun forTrack(context: Context, trackId: Long): List<Entry> {
        val existing = KaraokeRecordings.listSaved(context).map { it.displayName }.toSet()
        val all = all(context)
        val alive = reconcile(all, existing)
        if (alive.size != all.size) save(context, alive)
        return alive.filter { it.trackId == trackId }.sortedByDescending { it.recordedAtMs }
    }
}
