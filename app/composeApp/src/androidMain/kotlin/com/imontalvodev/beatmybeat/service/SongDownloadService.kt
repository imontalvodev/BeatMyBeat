package com.imontalvodev.beatmybeat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.download.DownloadProgressBus
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.ui.network.AudioDownloader
import com.imontalvodev.beatmybeat.ui.network.fetchYouTubeSongMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Descargas en segundo plano (canción o playlist). Sobrevive a cambios de pantalla
 * porque el trabajo vive en este servicio, no en el [rememberCoroutineScope] de Compose.
 */
class SongDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** @Volatile: leído/escrito desde el hilo que llama a [launchDownload]/[cancelActiveWork] y
     * desde el propio hilo de la corrutina (Dispatchers.IO), garantiza visibilidad entre ambos. */
    @Volatile
    private var downloadJob: Job? = null
    private var lastProgressNotifyElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelActiveWork(startId, showToast = true)
                return START_NOT_STICKY
            }
            ACTION_DOWNLOAD_SINGLE -> handleSingleDownload(intent, startId)
            ACTION_DOWNLOAD_PLAYLIST -> handlePlaylistDownload(intent, startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleSingleDownload(intent: Intent, startId: Int) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val album = intent.getStringExtra(EXTRA_ALBUM).orEmpty()
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL).orEmpty()
        val format = AudioDownloader.DownloadFormat.fromId(intent.getStringExtra(EXTRA_FORMAT))

        startDownloadForeground(title.ifBlank { getString(R.string.download_song_title) })

        launchDownload(startId) {
            DownloadProgressBus.setSingle(
                title = title,
                artist = artist,
                phase = getString(R.string.download_processing),
                fileFraction = null,
            )
            val result = runCatching {
                AudioDownloader.downloadAutoToAppMusic(
                    context = this@SongDownloadService,
                    title = title,
                    artist = artist,
                    album = album,
                    format = format,
                    videoId = videoId,
                    thumbnailUrl = thumbnailUrl,
                    onProgress = { update ->
                        if (downloadJob?.isActive != true) return@downloadAutoToAppMusic
                        reportSingleProgress(title, artist, update.phase, update.fileFraction)
                    },
                )
            }.getOrNull()
            finishDownload(
                startId = startId,
                toastMessage = when {
                    result == null -> getString(R.string.download_song_error)
                    result.success -> getString(
                        R.string.download_song_success,
                        format.label,
                        result.fileName ?: title,
                    )
                    else -> getString(R.string.download_song_failed)
                },
            )
        }
    }

    private fun handlePlaylistDownload(intent: Intent, startId: Int) {
        val videoIds = parseVideoIds(intent.getStringExtra(EXTRA_VIDEO_IDS_JSON))
        if (videoIds.isEmpty()) {
            stopSelf(startId)
            return
        }
        val format = AudioDownloader.DownloadFormat.fromId(intent.getStringExtra(EXTRA_FORMAT))
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME).orEmpty().trim()
        val total = videoIds.size

        startDownloadForeground(getString(R.string.download_progress_playlist_headline))

        launchDownload(startId) {
            var downloaded = 0
            var failed = 0
            val downloadedFileNames = mutableListOf<String>()
            DownloadProgressBus.setBatch(
                done = 0,
                total = total,
                failed = 0,
                currentTitle = "",
                phase = getString(R.string.download_playlist_preparing),
                fileFraction = null,
            )
            updatePlaylistNotification(0, total, "")

            for (videoId in videoIds) {
                if (downloadJob?.isActive != true) break
                val metadata = fetchYouTubeSongMetadata(videoId)
                DownloadProgressBus.setBatch(
                    done = downloaded,
                    total = total,
                    failed = failed,
                    currentTitle = metadata.title,
                    phase = getString(R.string.download_playlist_starting_track),
                    fileFraction = null,
                )
                updatePlaylistNotification(downloaded + failed, total, metadata.title)

                val single = AudioDownloader.downloadAutoToAppMusic(
                    context = this@SongDownloadService,
                    title = metadata.title,
                    artist = metadata.artist,
                    album = "",
                    format = format,
                    videoId = videoId,
                    thumbnailUrl = metadata.thumbnailUrl,
                    onProgress = { update ->
                        if (downloadJob?.isActive != true) return@downloadAutoToAppMusic
                        DownloadProgressBus.setBatch(
                            done = downloaded,
                            total = total,
                            failed = failed,
                            currentTitle = metadata.title,
                            phase = update.phase,
                            fileFraction = update.fileFraction,
                        )
                        updatePlaylistNotification(downloaded + failed, total, metadata.title)
                    },
                )
                if (single.success) {
                    downloaded++
                    single.fileName?.let { downloadedFileNames.add(it) }
                } else {
                    failed++
                }
                DownloadProgressBus.setBatch(
                    done = downloaded,
                    total = total,
                    failed = failed,
                    currentTitle = metadata.title,
                    phase = getString(R.string.download_playlist_track_done),
                    fileFraction = 1f,
                )
            }

            if (playlistName.isNotBlank() && downloadedFileNames.isNotEmpty()) {
                createAutoPlaylist(playlistName, downloadedFileNames)
            }

            val toastMessage = when {
                downloaded <= 0 -> getString(R.string.download_playlist_none)
                failed > 0 -> getString(R.string.download_playlist_partial, downloaded, total, failed)
                else -> getString(R.string.download_playlist_complete, downloaded)
            }
            finishDownload(startId, toastMessage)
        }
    }

    private fun createAutoPlaylist(name: String, fileNames: List<String>) {
        val resolver = contentResolver
        val collection = android.provider.MediaStore.Audio.Media.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val trackIds = mutableListOf<Long>()
        for (fileName in fileNames) {
            val cursor = resolver.query(
                collection,
                arrayOf(android.provider.MediaStore.Audio.Media._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null,
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    trackIds.add(it.getLong(0))
                }
            }
        }
        if (trackIds.isEmpty()) return

        val prefs = getSharedPreferences("beatmybeat_player_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("playlists_json", null).orEmpty()
        val root = if (raw.isNotBlank()) {
            runCatching { org.json.JSONObject(raw) }.getOrElse { org.json.JSONObject() }
        } else {
            org.json.JSONObject()
        }
        val arr = root.optJSONArray("playlists") ?: org.json.JSONArray()

        val normalized = name.lowercase()
        var existingIdx = -1
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("name", "").lowercase() == normalized) {
                existingIdx = i
                break
            }
        }

        if (existingIdx >= 0) {
            val existing = arr.getJSONObject(existingIdx)
            val songIdsArr = existing.optJSONArray("songIds") ?: org.json.JSONArray()
            val existingIds = mutableSetOf<Long>()
            for (j in 0 until songIdsArr.length()) existingIds.add(songIdsArr.optLong(j))
            for (id in trackIds) {
                if (id !in existingIds) songIdsArr.put(id)
            }
            existing.put("songIds", songIdsArr)
        } else {
            val newPlaylist = org.json.JSONObject().apply {
                put("id", System.currentTimeMillis())
                put("name", name)
                put("songIds", org.json.JSONArray().apply { trackIds.forEach { put(it) } })
            }
            arr.put(newPlaylist)
        }
        root.put("playlists", arr)
        prefs.edit().putString("playlists_json", root.toString()).apply()
    }

    private fun launchDownload(startId: Int, block: suspend () -> Unit) {
        downloadJob?.cancel()
        // LAZY: el cuerpo no arranca hasta job.start(), llamado DESPUÉS de asignar el campo.
        // Sin esto, la corrutina podía completarse (y ejecutar su `finally`) en otro hilo antes
        // de que `downloadJob = job` fuera visible en el hilo que llama, dejando el campo
        // apuntando a un job obsoleto en vez de a null tras un fallo muy rápido.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (_: CancellationException) {
                // Cancelación por el usuario (ACTION_CANCEL) o por una descarga nueva.
            } finally {
                if (downloadJob === this.coroutineContext[Job]) {
                    downloadJob = null
                }
            }
        }
        downloadJob = job
        job.start()
    }

    private fun cancelActiveWork(startId: Int, showToast: Boolean) {
        downloadJob?.cancel()
        downloadJob = null
        DownloadProgressBus.clear()
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, getString(R.string.download_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
        stopSelf(startId)
    }

    private fun reportSingleProgress(title: String, artist: String, phase: String, fraction: Float?) {
        DownloadProgressBus.setSingle(title, artist, phase, fraction)
        if (!shouldPostProgressNotification()) return
        if (BeatMyBeatNotification.canPostNotifications(this)) {
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    BeatMyBeatNotification.DOWNLOAD_NOTIFICATION_ID,
                    BeatMyBeatNotification.buildDownloadInProgressNotification(
                        context = this,
                        title = title.ifBlank { getString(R.string.download_song_title) },
                        subtitle = phase,
                    ),
                )
            }
        }
    }

    private fun shouldPostProgressNotification(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressNotifyElapsed < NOTIFY_THROTTLE_MS) return false
        lastProgressNotifyElapsed = now
        return true
    }

    private fun updatePlaylistNotification(processed: Int, total: Int, currentTitle: String) {
        if (!shouldPostProgressNotification()) return
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                BeatMyBeatNotification.DOWNLOAD_NOTIFICATION_ID,
                BeatMyBeatNotification.buildDownloadInProgressNotification(
                    context = this,
                    title = getString(R.string.download_progress_playlist_headline),
                    subtitle = "$processed/$total · ${currentTitle.take(40)}",
                ),
            )
        }
    }

    private fun startDownloadForeground(title: String) {
        lastProgressNotifyElapsed = 0L
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            startForeground(
                BeatMyBeatNotification.DOWNLOAD_NOTIFICATION_ID,
                BeatMyBeatNotification.buildDownloadInProgressNotification(
                    context = this,
                    title = title,
                    subtitle = getString(R.string.download_processing),
                ),
            )
        }
    }

    private fun finishDownload(startId: Int, toastMessage: String) {
        DownloadProgressBus.clear()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        }
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        stopSelf(startId)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        downloadJob = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_DOWNLOAD_SINGLE =
            "com.imontalvodev.beatmybeat.action.DOWNLOAD_SINGLE"
        private const val ACTION_DOWNLOAD_PLAYLIST =
            "com.imontalvodev.beatmybeat.action.DOWNLOAD_PLAYLIST"
        private const val ACTION_CANCEL = "com.imontalvodev.beatmybeat.action.CANCEL_DOWNLOAD"
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private const val EXTRA_ALBUM = "extra_album"
        private const val EXTRA_VIDEO_ID = "extra_video_id"
        private const val EXTRA_THUMBNAIL_URL = "extra_thumbnail_url"
        private const val EXTRA_FORMAT = "extra_format"
        private const val EXTRA_VIDEO_IDS_JSON = "extra_video_ids_json"
        private const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"

        fun enqueueDownload(
            context: Context,
            title: String,
            artist: String,
            album: String = "",
            videoId: String = "",
            thumbnailUrl: String = "",
            format: AudioDownloader.DownloadFormat = AudioDownloader.DownloadFormat.MP3,
        ) {
            val intent = Intent(context, SongDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_SINGLE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ALBUM, album)
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_THUMBNAIL_URL, thumbnailUrl)
                putExtra(EXTRA_FORMAT, format.id)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun enqueuePlaylistDownload(
            context: Context,
            videoIds: List<String>,
            format: AudioDownloader.DownloadFormat = AudioDownloader.DownloadFormat.MP3,
            playlistName: String = "",
        ) {
            if (videoIds.isEmpty()) return
            val arr = JSONArray()
            videoIds.forEach { id -> if (id.isNotBlank()) arr.put(id) }
            if (arr.length() == 0) return
            val intent = Intent(context, SongDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_PLAYLIST
                putExtra(EXTRA_VIDEO_IDS_JSON, arr.toString())
                putExtra(EXTRA_FORMAT, format.id)
                if (playlistName.isNotBlank()) putExtra(EXTRA_PLAYLIST_NAME, playlistName)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun cancelDownload(context: Context) {
            DownloadProgressBus.clear()
            val intent = Intent(context, SongDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        private fun parseVideoIds(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i).trim()
                        if (id.isNotBlank()) add(id)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
