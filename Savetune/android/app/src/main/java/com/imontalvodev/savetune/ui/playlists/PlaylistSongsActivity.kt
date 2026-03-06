package com.imontalvodev.savetune.ui.playlists

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.imontalvodev.savetune.R
import com.imontalvodev.savetune.data.LibraryStore
import com.imontalvodev.savetune.model.Song
import com.imontalvodev.savetune.player.NowPlayingState
import com.imontalvodev.savetune.ui.main.MainActivityHelper
import com.imontalvodev.savetune.ui.nowplaying.NowPlayingActivity

class PlaylistSongsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
    }

    private lateinit var txtPlaylistTitle: TextView
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
        setContentView(R.layout.activity_playlist_songs)

        txtPlaylistTitle = findViewById(R.id.txtPlaylistTitle)
        btnPlayAll = findViewById(R.id.btnPlayAllPlaylist)
        listView = findViewById(R.id.listPlaylistSongs)
        playerBar = findViewById(R.id.playerBar)
        imgCurrentArt = findViewById(R.id.imgCurrentArt)
        txtCurrentTitle = findViewById(R.id.txtCurrentTitle)
        txtCurrentArtist = findViewById(R.id.txtCurrentArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevMain = findViewById(R.id.btnPrevMain)
        btnNextMain = findViewById(R.id.btnNextMain)

        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: ""
        txtPlaylistTitle.text = playlistName

        loadSongs(playlistName)
        setupList()
        setupPlayerBar()
    }

    private fun loadSongs(playlistName: String) {
        val allSongs = MainActivityHelper.loadDownloadedSongs(this)
        songs = LibraryStore.getPlaylistSongs(this, playlistName, allSongs)
    }

    private fun setupList() {
        val adapter = object : ArrayAdapter<Song>(
            this,
            R.layout.item_song,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
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
                        com.bumptech.glide.Glide.with(this@PlaylistSongsActivity)
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
                Toast.makeText(this, "Reproduciendo playlist completa", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No hay canciones en esta playlist", Toast.LENGTH_SHORT).show()
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

