package com.imontalvodev.savetune.data

import android.content.Context
import com.imontalvodev.savetune.model.Song
import org.json.JSONObject

object LibraryStore {
    private const val PREFS_NAME = "savetune_library"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_PLAYLISTS = "playlists"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun songKey(song: Song): String {
        return song.mediaStoreId?.toString()
            ?: song.file?.absolutePath
            ?: "${song.title}-${song.artist}"
    }

    fun isFavorite(context: Context, song: Song): Boolean {
        val set = prefs(context).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return songKey(song) in set
    }

    fun toggleFavorite(context: Context, song: Song): Boolean {
        val p = prefs(context)
        val set = (p.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()).toMutableSet()
        val key = songKey(song)
        val isNowFavorite: Boolean
        if (set.contains(key)) {
            set.remove(key)
            isNowFavorite = false
        } else {
            set.add(key)
            isNowFavorite = true
        }
        p.edit().putStringSet(KEY_FAVORITES, set).apply()
        return isNowFavorite
    }

    fun addSongToPlaylist(context: Context, playlistName: String, song: Song) {
        val p = prefs(context)
        val key = songKey(song)
        val raw = p.getString(KEY_PLAYLISTS, "{}") ?: "{}"
        val json = JSONObject(raw)
        val arr = if (json.has(playlistName)) {
            json.getJSONArray(playlistName)
        } else {
            org.json.JSONArray().also { json.put(playlistName, it) }
        }
        var exists = false
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == key) {
                exists = true
                break
            }
        }
        if (!exists) {
            arr.put(key)
        }
        p.edit().putString(KEY_PLAYLISTS, json.toString()).apply()
    }

    fun getPlaylists(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_PLAYLISTS, "{}") ?: "{}"
        val json = JSONObject(raw)
        return json.keys().asSequence().toList()
    }

    fun getFavoriteSongs(context: Context, allSongs: List<Song>): List<Song> {
        val set = prefs(context).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        if (set.isEmpty()) return emptyList()
        val byKey = allSongs.associateBy { songKey(it) }
        return set.mapNotNull { byKey[it] }
    }

    fun getPlaylistSongs(context: Context, playlistName: String, allSongs: List<Song>): List<Song> {
        val raw = prefs(context).getString(KEY_PLAYLISTS, "{}") ?: "{}"
        val json = JSONObject(raw)
        if (!json.has(playlistName)) return emptyList()
        val arr = json.getJSONArray(playlistName)
        val byKey = allSongs.associateBy { songKey(it) }
        val result = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            val key = arr.getString(i)
            val song = byKey[key]
            if (song != null) result.add(song)
        }
        return result
    }
}

