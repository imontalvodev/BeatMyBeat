package com.imontalvodev.savetune.ui.playlists

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.data.LibraryStore

class PlaylistsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)

        val listView = findViewById<ListView>(R.id.listPlaylists)
        val playlists = LibraryStore.getPlaylists(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            playlists
        )

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val name = playlists[position]
            val intent = Intent(this, PlaylistSongsActivity::class.java)
            intent.putExtra(PlaylistSongsActivity.EXTRA_PLAYLIST_NAME, name)
            startActivity(intent)
        }
    }
}

