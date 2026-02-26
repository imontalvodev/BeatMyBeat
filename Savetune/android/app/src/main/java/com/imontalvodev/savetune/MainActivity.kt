package com.imontalvodev.savetune

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class Song(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val file: File? = null
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
    private var songs: List<Song> = emptyList()

    private val PERMISSION_REQUEST_CODE = 1001
    private val TAG = "SaveTune"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        checkPermissionsAndLoad()
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

    private fun checkPermissionsAndLoad() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 y anteriores
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Solicitando permisos: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "Permisos ya otorgados")
            loadSongsAndSetupList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permisos otorgados")
                loadSongsAndSetupList()
            } else {
                Log.e(TAG, "Permisos denegados")
                songs = emptyList()
                txtPlaylistName.text = "No Permissions"
                txtPlaylistSubtitle.text = "Grant storage permission to see your music"
                Toast.makeText(this, "Se necesitan permisos de almacenamiento", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSongsAndSetupList() {
        songs = loadDownloadedSongs()
        Log.d(TAG, "Canciones encontradas: ${songs.size}")

        txtPlaylistName.text = "My Music"
        txtPlaylistSubtitle.text = "${songs.size} Track${if (songs.size != 1) "s" else ""}"

        if (songs.isEmpty()) {
            Toast.makeText(this, "No se encontraron canciones en el dispositivo", Toast.LENGTH_LONG).show()
        }

        setupList()
    }

    private fun loadDownloadedSongs(): List<Song> {
        val result = mutableListOf<Song>()

        // Primero intentar con MediaStore
        result.addAll(loadFromMediaStore())

        // Si no encontramos nada, buscar directamente en carpetas conocidas
        if (result.isEmpty()) {
            Log.w(TAG, "MediaStore vacío, buscando directamente en sistema de archivos...")
            result.addAll(loadFromFileSystem())
        }

        Log.d(TAG, "Total de canciones cargadas: ${result.size}")
        return result
    }

    private fun loadFromFileSystem(): List<Song> {
        val result = mutableListOf<Song>()

        // Carpetas comunes donde se guardan canciones
        val musicFolders = listOf(
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/sdcard/Music",
            "/sdcard/Download"
        )

        val audioExtensions = setOf("mp3", "m4a", "webm", "ogg", "wav", "flac", "aac")

        for (folderPath in musicFolders) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                Log.d(TAG, "Carpeta no existe: $folderPath")
                continue
            }

            Log.d(TAG, "Escaneando carpeta: $folderPath")

            folder.listFiles()?.forEach { file ->
                if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                    try {
                        val song = Song(
                            title = file.nameWithoutExtension,
                            artist = "Unknown Artist",
                            album = "",
                            durationSeconds = 0,
                            file = file
                        )
                        result.add(song)
                        Log.d(TAG, "Archivo encontrado: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando ${file.name}", e)
                    }
                }
            }
        }

        return result
    }

    private fun loadFromMediaStore(): List<Song> {
        val result = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // Selección mejorada: solo archivos de música (incluye webm, m4a, etc.)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                Log.d(TAG, "Escaneando MediaStore... Total filas: ${it.count}")

                while (it.moveToNext()) {
                    try {
                        val id = it.getLong(idIdx)
                        val displayName = it.getString(nameIdx) ?: ""
                        val title = it.getString(titleIdx)?.takeIf { t -> t.isNotBlank() }
                            ?: displayName.substringBeforeLast('.')
                        val artist = it.getString(artistIdx)?.takeIf { a -> a.isNotBlank() && a != "<unknown>" }
                            ?: "Unknown Artist"
                        val album = it.getString(albumIdx) ?: ""
                        val durationMs = it.getLong(durationIdx)
                        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else 0
                        val fullPath = it.getString(dataIdx) ?: ""

                        val file = try {
                            if (fullPath.isNotBlank()) File(fullPath) else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creando File para: $fullPath", e)
                            null
                        }

                        val song = Song(
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSec,
                            file = file
                        )

                        result.add(song)

                        Log.d(TAG, "Canción encontrada: $title - $artist (${formatDuration(durationSec)})")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando fila", e)
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al acceder a MediaStore", e)
            Toast.makeText(this, "Error de permisos al acceder a la música", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error general al cargar canciones", e)
            Toast.makeText(this, "Error al cargar canciones: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return result
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "--:--"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }

    private fun setupList() {
        if (songs.isEmpty()) {
            Log.d(TAG, "Lista de canciones vacía")
        }

        val adapter = object : ArrayAdapter<Song>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            songs
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
                val view = super.getView(position, convertView, parent)
                val titleView = view.findViewById<TextView>(android.R.id.text1)
                val subtitleView = view.findViewById<TextView>(android.R.id.text2)

                val song = getItem(position)
                if (song != null) {
                    titleView.text = song.title
                    val subtitle = buildString {
                        append(song.artist)
                        if (song.album.isNotBlank()) {
                            append(" • ${song.album}")
                        }
                        if (song.durationSeconds > 0) {
                            append(" • ${formatDuration(song.durationSeconds)}")
                        }
                    }
                    subtitleView.text = subtitle
                }
                return view
            }
        }

        listSongs.adapter = adapter

        listSongs.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            updateCurrentTrack(song)
            Log.d(TAG, "Canción seleccionada: ${song.title}")
            // TODO: Aquí implementar reproducción real
        }

        btnPlayAll.setOnClickListener {
            if (songs.isNotEmpty()) {
                updateCurrentTrack(songs.first())
                Log.d(TAG, "Play All presionado")
            } else {
                Toast.makeText(this, "No hay canciones para reproducir", Toast.LENGTH_SHORT).show()
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
            Log.d(TAG, "Play/Pause - isPlaying: $isPlaying")
        }
    }

    private fun updateCurrentTrack(song: Song) {
        txtCurrentTitle.text = song.title
        txtCurrentArtist.text = song.artist
        isPlaying = true
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

        // TODO: Cargar carátula del álbum si está disponible
        // imgCurrentArt.setImageBitmap(...)
    }
}