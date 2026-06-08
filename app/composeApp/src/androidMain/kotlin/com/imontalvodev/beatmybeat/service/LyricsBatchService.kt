package com.imontalvodev.beatmybeat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imontalvodev.beatmybeat.R
import com.imontalvodev.beatmybeat.download.LyricsProgressBus
import com.imontalvodev.beatmybeat.notifications.BeatMyBeatNotification
import com.imontalvodev.beatmybeat.ui.data.DeviceTrack
import com.imontalvodev.beatmybeat.ui.data.MediaStoreScanner
import com.imontalvodev.beatmybeat.ui.feature.player.resolveTrackMetadata
import com.imontalvodev.beatmybeat.ui.network.LyricsCache
import com.imontalvodev.beatmybeat.ui.network.LyricsFetchCoordinator
import com.imontalvodev.beatmybeat.ui.network.LyricsFetcher
import com.imontalvodev.beatmybeat.ui.network.buildLyricsArtistCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Descarga letras en lote para todas las canciones detectadas en el dispositivo.
 */
class LyricsBatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null
    private var lastProgressNotifyElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelActiveWork(startId, showToast = true)
            ACTION_START_BATCH -> handleBatch(startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleBatch(startId: Int) {
        startBatchForeground()
        launchBatch(startId) {
            val tracks = MediaStoreScanner(this@LyricsBatchService)
                .scanAudio()
                .distinctBy { it.uri }
            val eligible = tracks.filter { isEligibleForLyrics(it) }
            val total = eligible.size

            if (total == 0) {
                finishBatch(startId, getString(R.string.lyrics_batch_none))
                return@launchBatch
            }

            var done = 0
            var found = 0
            var notFound = 0
            var skipped = 0

            reportProgress(done, total, found, notFound, skipped, "", getString(R.string.lyrics_batch_preparing))

            for (track in eligible) {
                if (batchJob?.isActive != true) break
                val meta = resolveTrackMetadata(track)
                val title = meta.title.trim()
                val artist = meta.artist.trim()

                if (LyricsCache.getEntry(this@LyricsBatchService, title, artist)?.hasAnyLyrics() == true) {
                    skipped++
                    done++
                    reportProgress(
                        done = done,
                        total = total,
                        found = found,
                        notFound = notFound,
                        skipped = skipped,
                        currentTitle = title,
                        phase = getString(R.string.lyrics_batch_skipped_track),
                    )
                    continue
                }

                reportProgress(
                    done = done,
                    total = total,
                    found = found,
                    notFound = notFound,
                    skipped = skipped,
                    currentTitle = title,
                    phase = getString(R.string.lyrics_batch_fetching),
                )

                val res = LyricsFetchCoordinator.fetch(
                    context = this@LyricsBatchService,
                    request = LyricsFetcher.Request(
                        title = title,
                        artist = artist,
                        album = meta.album,
                        durationMs = meta.durationMs,
                        titleCandidates = listOf(track.title.trim()).filter { it.isNotBlank() && it != title },
                        artistCandidates = buildLyricsArtistCandidates(
                            artist,
                            listOf(track.artist.trim()),
                        ).filter { it != artist },
                    ),
                )

                if (res.success && res.lyrics.isNotBlank()) {
                    found++
                } else {
                    notFound++
                }
                done++
                reportProgress(
                    done = done,
                    total = total,
                    found = found,
                    notFound = notFound,
                    skipped = skipped,
                    currentTitle = title,
                    phase = getString(R.string.lyrics_batch_track_done),
                )
            }

            val toastMessage = when {
                found <= 0 && skipped <= 0 -> getString(R.string.lyrics_batch_none)
                notFound > 0 -> getString(R.string.lyrics_batch_partial, found, total, notFound, skipped)
                else -> getString(R.string.lyrics_batch_complete, found, skipped)
            }
            finishBatch(startId, toastMessage)
        }
    }

    private fun isEligibleForLyrics(track: DeviceTrack): Boolean {
        val meta = resolveTrackMetadata(track)
        fun isUnknown(s: String): Boolean =
            s.equals("unknown", ignoreCase = true) ||
                s.equals("unknown artist", ignoreCase = true) ||
                s.isBlank()
        return !isUnknown(meta.title) && !isUnknown(meta.artist)
    }

    private fun reportProgress(
        done: Int,
        total: Int,
        found: Int,
        notFound: Int,
        skipped: Int,
        currentTitle: String,
        phase: String,
    ) {
        LyricsProgressBus.update(done, total, found, notFound, skipped, currentTitle, phase)
        if (!shouldPostProgressNotification()) return
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                BeatMyBeatNotification.LYRICS_BATCH_NOTIFICATION_ID,
                BeatMyBeatNotification.buildDownloadInProgressNotification(
                    context = this,
                    title = getString(R.string.lyrics_batch_notification_title),
                    subtitle = "$done/$total · ${currentTitle.take(40)}",
                ),
            )
        }
    }

    private fun shouldPostProgressNotification(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgressNotifyElapsed < NOTIFY_THROTTLE_MS) return false
        lastProgressNotifyElapsed = now
        return true
    }

    private fun startBatchForeground() {
        lastProgressNotifyElapsed = 0L
        if (!BeatMyBeatNotification.canPostNotifications(this)) return
        runCatching {
            startForeground(
                BeatMyBeatNotification.LYRICS_BATCH_NOTIFICATION_ID,
                BeatMyBeatNotification.buildDownloadInProgressNotification(
                    context = this,
                    title = getString(R.string.lyrics_batch_notification_title),
                    subtitle = getString(R.string.lyrics_batch_preparing),
                ),
            )
        }
    }

    private fun launchBatch(startId: Int, block: suspend () -> Unit) {
        batchJob?.cancel()
        val job = scope.launch {
            try {
                block()
            } catch (_: CancellationException) {
                // Cancelado por el usuario.
            } finally {
                if (batchJob === coroutineContext[Job]) {
                    batchJob = null
                }
            }
        }
        batchJob = job
    }

    private fun cancelActiveWork(startId: Int, showToast: Boolean) {
        batchJob?.cancel()
        batchJob = null
        LyricsProgressBus.clear()
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        if (showToast) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, getString(R.string.lyrics_batch_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
        stopSelf(startId)
    }

    private fun finishBatch(startId: Int, toastMessage: String) {
        LyricsProgressBus.clear()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
        }
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        stopSelf(startId)
    }

    override fun onDestroy() {
        batchJob?.cancel()
        batchJob = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START_BATCH =
            "com.imontalvodev.beatmybeat.action.LYRICS_BATCH_START"
        private const val ACTION_CANCEL =
            "com.imontalvodev.beatmybeat.action.LYRICS_BATCH_CANCEL"
        private const val NOTIFY_THROTTLE_MS = 500L

        fun enqueueBatch(context: Context) {
            val intent = Intent(context, LyricsBatchService::class.java).apply {
                action = ACTION_START_BATCH
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun cancelBatch(context: Context) {
            LyricsProgressBus.clear()
            val intent = Intent(context, LyricsBatchService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
