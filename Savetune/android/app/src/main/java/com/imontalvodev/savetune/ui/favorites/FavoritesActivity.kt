package com.imontalvodev.savetune.ui.favorites

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.data.LibraryStore
import com.imontalvodev.savetune.model.Song
import com.imontalvodev.savetune.player.NowPlayingState
import com.imontalvodev.savetune.ui.main.MainActivityHelper
import com.imontalvodev.savetune.ui.nowplaying.NowPlayingActivity

class FavoritesActivity : AppCompatActivity() {

    private lateinit var btnPlayAll: Button
    private lateinit var listView: ListView
    private lateinit var playerBar: LinearLayout
    private lateinit var imgCurrentArt: ImageView
    private lateinit var txtCurrentTitle: TextView
    private lateinit var txtCurrentArtist: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevMain: ImageButton
    private lateinit var btnNextMain: ImageButton

    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        btnPlayAll = findViewById(R.id.btnPlayAllFavorites)
        listView = findViewById(R.id.listFavorites)
        playerBar = findViewById(R.id.playerBar)
        imgCurrentArt = findViewById(R.id.imgCurrentArt)
        txtCurrentTitle = findViewById(R.id.txtCurrentTitle)
        txtCurrentArtist = findViewById(R.id.txtCurrentArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevMain = findViewById(R.id.btnPrevMain)
        btnNextMain = findViewById(R.id.btnNextMain)

        loadSongs()
        setupList()
        setupPlayerBar()
    }

    private fun loadSongs() {
        val allSongs = MainActivityHelper.loadDownloadedSongs(this)
        songs = LibraryStore.getFavoriteSongs(this, allSongs)
    }

    private fun setupList() {
        val adapter = object : ArrayAdapter<Song>(
            this,
            R.layout.item_song,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_song, parent, false)

                val titleView = view.findViewById<TextView>(R.id.txtSongTitle)
                val artistView = view.findViewById<TextView>(R.id.txtSongArtist)
                val artView = view.findViewById<ImageView>(R.id.imgSongArt)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    artistView.text = song.artist

                    val file = song.file
                    if (file != null && file.exists()) {
                        Glide.with(this@FavoritesActivity)
                            .load(file)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .error(R.mipmap.ic_launcher_round)
                            .into(artView)
                    } else {
                        artView.setImageResource(R.mipmap.ic_launcher_round)
                    }
                }
                return view
            }
        }

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            NowPlayingState.songs = songs
            NowPlayingState.playSong(this, position)
            Toast.makeText(this, "Reproduciendo '${song.title}'", Toast.LENGTH_SHORT).show()
        }

        btnPlayAll.setOnClickListener {
            if (songs.isNotEmpty()) {
                NowPlayingState.songs = songs
                NowPlayingState.playSong(this, 0)
                Toast.makeText(this, "Reproduciendo favoritos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No hay canciones favoritas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlayerBar() {
        playerBar.setOnClickListener {
            if (NowPlayingState.currentSong != null) {
                startActivity(Intent(this, NowPlayingActivity::class.java))
            } else {
                Toast.makeText(this, "No hay ninguna canción reproduciéndose", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlayPause.setOnClickListener {
            NowPlayingState.togglePlayPause(this)
            val mp = NowPlayingState.mediaPlayer
            val isPlayingNow = mp?.isPlaying == true
            btnPlayPause.setImageResource(
                if (isPlayingNow) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }

        btnPrevMain.setOnClickListener {
            if (NowPlayingState.playPrevious(this)) {
                NowPlayingState.currentSong?.let { updateCurrentTrack(it) }
            } else {
                Toast.makeText(this, "No hay pista anterior", Toast.LENGTH_SHORT).show()
            }
        }

        btnNextMain.setOnClickListener {
            if (NowPlayingState.playNext(this)) {
                NowPlayingState.currentSong?.let { updateCurrentTrack(it) }
            } else {
                Toast.makeText(this, "No hay pista siguiente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCurrentTrack(song: Song) {
        txtCurrentTitle.text = song.title
        txtCurrentArtist.text = song.artist
    }
}