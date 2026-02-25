package com.imontalvodev.savetune

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

data class Song(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0
)

class MainActivity : AppCompatActivity() {

    private lateinit var txtPlaylistName: TextView
    private lateinit var txtPlaylistSubtitle: TextView
    private lateinit var listSongs: ListView
    private lateinit var btnPlayAll: Button

    private lateinit var imgCurrentArt: ImageView
    private lateinit var txtCurrentTitle: TextView
    private lateinit var txtCurrentArtist: TextView
    private lateinit var btnPlayPause: ImageButton

    private var isPlaying: Boolean = false
    private lateinit var songs: List<Song>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupDummyData()
        setupList()
        setupPlayerBar()
    }

    private fun bindViews() {
        txtPlaylistName = findViewById(R.id.txtPlaylistName)
        txtPlaylistSubtitle = findViewById(R.id.txtPlaylistSubtitle)
        listSongs = findViewById(R.id.listSongs)
        btnPlayAll = findViewById(R.id.btnPlayAll)

        imgCurrentArt = findViewById(R.id.imgCurrentArt)
        txtCurrentTitle = findViewById(R.id.txtCurrentTitle)
        txtCurrentArtist = findViewById(R.id.txtCurrentArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)
    }

    private fun setupDummyData() {
        // TODO: más adelante leeremos realmente las canciones descargadas del dispositivo
        songs = listOf(
            Song("City Lights Chill", "Beatmaker X", durationSeconds = 145),
            Song("Demo Light Chill", "Beatmaker X", durationSeconds = 150),
            Song("Memory Chill", "Beatmaker X", durationSeconds = 160),
            Song("Moonlight Drive", "LoFi Dreams", durationSeconds = 180),
            Song("Night Ride", "LoFi Dreams", durationSeconds = 200),
            Song("Skyline Echoes", "Beatmaker X", durationSeconds = 175)
        )

        txtPlaylistName.text = "Downloaded Tracks"
        txtPlaylistSubtitle.text = "${songs.size} Tracks Found"
    }

    private fun setupList() {
        val adapter = object : ArrayAdapter<Song>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            songs
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val titleView = view.findViewById<TextView>(android.R.id.text1)
                val artistView = view.findViewById<TextView>(android.R.id.text2)

                val song = getItem(position)
                titleView.text = song?.title ?: ""
                artistView.text = song?.artist ?: ""

                return view
            }
        }

        listSongs.adapter = adapter

        listSongs.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            updateCurrentTrack(song)
            // En el futuro aquí arrancaremos la reproducción real
        }

        btnPlayAll.setOnClickListener {
            if (songs.isNotEmpty()) {
                updateCurrentTrack(songs.first())
            }
        }
    }

    private fun setupPlayerBar() {
        btnPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            btnPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
    }

    private fun updateCurrentTrack(song: Song) {
        txtCurrentTitle.text = song.title
        txtCurrentArtist.text = song.artist
        isPlaying = true
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
    }
}
