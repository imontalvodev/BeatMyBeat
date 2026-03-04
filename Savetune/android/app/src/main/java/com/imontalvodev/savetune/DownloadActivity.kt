package com.imontalvodev.savetune

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

data class DownloadSong(
    val id: String? = null,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null
)

class DownloadActivity : AppCompatActivity() {

    private lateinit var edtSource: EditText
    private lateinit var btnAnalyze: Button
    private lateinit var txtPlaylistTitle: TextView
    private lateinit var txtPlaylistSubtitle: TextView
    private lateinit var btnDownloadAll: Button
    private lateinit var listDownloadSongs: ListView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressLoading: ProgressBar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navPlayerDownload: TextView
    private lateinit var navDownloadDownload: TextView

    private var songs: List<DownloadSong> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        bindViews()
        setupMenu()
        setupDummyState()
        setupAnalyze()
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.drawerLayoutDownload)
        edtSource = findViewById(R.id.edtSource)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        txtPlaylistTitle = findViewById(R.id.txtPlaylistTitle)
        txtPlaylistSubtitle = findViewById(R.id.txtPlaylistSubtitle)
        btnDownloadAll = findViewById(R.id.btnDownloadAll)
        listDownloadSongs = findViewById(R.id.listDownloadSongs)
        btnMenu = findViewById(R.id.btnMenuDownload)
        navPlayerDownload = findViewById(R.id.navPlayerDownload)
        navDownloadDownload = findViewById(R.id.navDownloadDownload)
        progressLoading = findViewById(R.id.progressLoading)
    }

    private fun setupMenu() {
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navPlayerDownload.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        navDownloadDownload.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Ya estamos en Download
        }
    }

    private fun setupDummyState() {
        // Estado inicial vacío; se rellenará al analizar una playlist real
        txtPlaylistTitle.text = getString(R.string.app_name)
        txtPlaylistSubtitle.text = ""
    }

    private fun setupAnalyze() {
        btnAnalyze.setOnClickListener {
            val text = edtSource.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Pega una URL de Spotify o escribe un título", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Si es URL de playlist de Spotify -> flujo de playlist
            val isSpotifyPlaylist = text.contains("spotify.com/playlist/")

            setLoading(true)

            Thread {
                try {
                    if (isSpotifyPlaylist) {
                        val playlist = ApiClient.fetchPlaylist(text)

                        runOnUiThread {
                            setLoading(false)
                            txtPlaylistTitle.text = playlist.name
                            txtPlaylistSubtitle.text = "${playlist.totalTracks} Tracks Found"
                            songs = playlist.songs
                            updateDownloadList()
                        }
                    } else {
                        // Búsqueda libre de canción (por título / artista / álbum)
                        val video = ApiClient.searchYoutube(text)

                        runOnUiThread {
                            setLoading(false)
                            if (video == null || video.id.isBlank()) {
                                Toast.makeText(
                                    this,
                                    "No se encontró ningún resultado para '$text'",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // Mostrar un pequeño resumen y ofrecer descarga
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Resultado encontrado")
                                    .setMessage(video.title)
                                    .setPositiveButton("Descargar") { _, _ ->
                                        val song = DownloadSong(
                                            id = video.id,
                                            title = video.title,
                                            artist = "",
                                            album = "",
                                            durationSeconds = 0
                                        )
                                        startDownloadTrack(video.id, song)
                                    }
                                    .setNegativeButton("Cancelar", null)
                                    .show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this, "Error al contactar con el backend: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun updateDownloadList() {
        val adapter = object : ArrayAdapter<DownloadSong>(
            this,
            R.layout.item_song_download,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_song_download, parent, false)

                val titleView = view.findViewById<TextView>(R.id.txtSongTitle)
                val artistView = view.findViewById<TextView>(R.id.txtSongArtist)
                val artView = view.findViewById<ImageView>(R.id.imgSongArt)
                val btnDownload = view.findViewById<ImageButton>(R.id.btnDownloadSong)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    artistView.text = song.artist
                    artView.setImageResource(R.mipmap.ic_launcher_round)

                    btnDownload.setOnClickListener {
                        // Buscar el mejor vídeo en YouTube y luego disparar la descarga
                        Toast.makeText(
                            this@DownloadActivity,
                            "Buscando '${song.title}' en YouTube...",
                            Toast.LENGTH_SHORT
                        ).show()

                        Thread {
                            try {
                                val video = ApiClient.searchYoutubeForSong(
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album
                                )

                                if (video == null || video.id.isBlank()) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@DownloadActivity,
                                            "No se encontró vídeo para '${song.title}'",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    return@Thread
                                }

                                runOnUiThread {
                                    startDownloadTrack(video.id, song)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                runOnUiThread {
                                    Toast.makeText(
                                        this@DownloadActivity,
                                        "Error buscando en YouTube: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }.start()
                    }
                }
                return view
            }
        }

        listDownloadSongs.adapter = adapter

        btnDownloadAll.setOnClickListener {
            Toast.makeText(this, "Descargar todos los temas (pendiente de implementación real)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownloadTrack(videoId: String, song: DownloadSong) {
        Toast.makeText(
            this,
            "Descargando '${song.title}'…",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            try {
                val url =
                    "${ApiConfig.BASE_URL}/download?videoId=${java.net.URLEncoder.encode(videoId, "UTF-8")}"

                val client = okhttp3.OkHttpClient.Builder()
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Error ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw IllegalStateException("Respuesta vacía")

                    val fileName = "${song.title.replace("/", "-")}.mp3"

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                            put(
                                MediaStore.Audio.Media.RELATIVE_PATH,
                                Environment.DIRECTORY_MUSIC + "/SaveTune"
                            )
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }

                        val resolver = contentResolver
                        val uri = resolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: throw IllegalStateException("No se pudo crear el archivo en MediaStore")

                        resolver.openOutputStream(uri)?.use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                            }
                        } ?: throw IllegalStateException("No se pudo abrir OutputStream")

                        values.clear()
                        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } else {
                        val musicDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MUSIC + "/SaveTune"
                        )
                        if (!musicDir.exists()) {
                            musicDir.mkdirs()
                        }
                        val outFile = java.io.File(musicDir, fileName)
                        java.io.FileOutputStream(outFile).use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Descarga completada",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Error descargando: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun setLoading(isLoading: Boolean) {
        progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnAnalyze.isEnabled = !isLoading
        btnAnalyze.text = if (isLoading) "LOADING..." else "ANALYZE SOURCE"
    }
}

