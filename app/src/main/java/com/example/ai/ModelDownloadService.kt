package com.example.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.ai.ModelCatalog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground Service (`dataSync`) that keeps large offline AI model downloads
 * alive in the background with an ongoing notification displaying progress,
 * transfer speed, ETA, and Pause / Resume / Cancel controls.
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifyTimeMs: Long = 0L
    private var lastObservedStatus: DownloadStatus? = null
    private var lastCompletionFile: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel(this)

        if (intent == null || intent.action == null || intent.action == ACTION_START_OR_SYNC) {
            ModelManager.restorePersistedQueueIfNeeded(this)
        }

        val initialProgress = ModelManager.downloadProgress.value
        val initialGlobal = ModelManager.globalProgress.value
        startForegroundCompat(buildProgressNotification(this, initialProgress, initialGlobal))

        when (intent?.action) {
            ACTION_PAUSE -> {
                ModelManager.pauseDownload()
                releaseWakeLock()
                val pausedNotif = buildProgressNotification(
                    this,
                    ModelManager.downloadProgress.value,
                    ModelManager.globalProgress.value
                )
                notifySafe(NOTIFICATION_ID, pausedNotif)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                acquireWakeLock()
                ModelManager.resumeQueue(this)
            }

            ACTION_CANCEL_ALL -> {
                ModelManager.clearQueue(this)
                releaseWakeLock()
                notificationManager(this)?.cancel(NOTIFICATION_ID)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!ModelManager.hasPendingOrActiveDownloads()) {
                    releaseWakeLock()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                acquireWakeLock()
                ModelManager.ensureWorkerRunning(this)
            }
        }

        startObservingDownloads()
        return START_STICKY
    }

    private fun startObservingDownloads() {
        if (observerJob?.isActive == true) return
        observerJob = serviceScope.launch {
            combine(
                ModelManager.downloadProgress,
                ModelManager.globalProgress
            ) { progress, global -> progress to global }
                .collect { (progress, global) ->
                    val statusChanged = progress.status != lastObservedStatus
                    val now = System.currentTimeMillis()

                    when {
                        global.isDownloading ||
                            progress.status == DownloadStatus.DOWNLOADING ||
                            progress.status == DownloadStatus.PROBING ||
                            progress.status == DownloadStatus.VERIFYING -> {
                            lastObservedStatus = progress.status
                            if (statusChanged || (now - lastNotifyTimeMs) >= NOTIFY_THROTTLE_MS) {
                                lastNotifyTimeMs = now
                                val notif = buildProgressNotification(this@ModelDownloadService, progress, global)
                                notifySafe(NOTIFICATION_ID, notif)
                            }
                        }

                        global.isPaused || progress.status == DownloadStatus.PAUSED -> {
                            lastObservedStatus = progress.status
                            releaseWakeLock()
                            val pausedNotif = buildProgressNotification(this@ModelDownloadService, progress, global)
                            notifySafe(NOTIFICATION_ID, pausedNotif)
                            ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }

                        progress.status == DownloadStatus.ERROR -> {
                            lastObservedStatus = progress.status
                            releaseWakeLock()
                            val errNotif = buildProgressNotification(this@ModelDownloadService, progress, global)
                            notifySafe(NOTIFICATION_ID, errNotif)
                            ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }

                        progress.status == DownloadStatus.COMPLETED && global.totalQueuedCount == 0 -> {
                            lastObservedStatus = progress.status
                            releaseWakeLock()
                            if (progress.fileName.isNotBlank() && progress.fileName != lastCompletionFile) {
                                lastCompletionFile = progress.fileName
                                val doneNotif = buildCompletionNotification(this@ModelDownloadService, progress.fileName)
                                notifySafe(COMPLETION_NOTIFICATION_ID, doneNotif)
                            }
                            ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }

                        global.totalQueuedCount == 0 && progress.status == DownloadStatus.IDLE -> {
                            lastObservedStatus = progress.status
                            releaseWakeLock()
                            ServiceCompat.stopForeground(this@ModelDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ModelManager.persistQueueSnapshot(this)
        super.onTaskRemoved(rootIntent)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout()
    }

    private fun handleForegroundServiceTimeout() {
        ModelManager.pauseDownload()
        releaseWakeLock()
        val pausedNotif = buildProgressNotification(
            this,
            ModelManager.downloadProgress.value,
            ModelManager.globalProgress.value
        )
        notifySafe(NOTIFICATION_ID, pausedNotif)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifySafe(id: Int, notification: Notification) {
        runCatching {
            notificationManager(this)?.notify(id, notification)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val CHANNEL_NAME = "Offline AI Model Downloads"
        const val NOTIFICATION_ID = 4201
        const val COMPLETION_NOTIFICATION_ID = 4202
        private const val NOTIFY_THROTTLE_MS = 500L
        private const val WAKE_LOCK_TAG = "AiAppStudio:ModelDownloadWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L // 4 hours max safety timeout

        const val ACTION_START_OR_SYNC = "com.example.ai.action.START_OR_SYNC"
        const val ACTION_PAUSE = "com.example.ai.action.PAUSE"
        const val ACTION_RESUME = "com.example.ai.action.RESUME"
        const val ACTION_CANCEL_ALL = "com.example.ai.action.CANCEL_ALL"

        fun startOrSync(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                ensureNotificationChannel(appContext)
                val intent = Intent(appContext, ModelDownloadService::class.java).apply {
                    action = ACTION_START_OR_SYNC
                }
                ContextCompat.startForegroundService(appContext, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, ModelDownloadService::class.java))
            }
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = notificationManager(context) ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Progress and controls for downloading offline Gemma & Qwen AI models"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        fun buildProgressNotification(
            context: Context,
            progress: ModelDownloadProgress,
            global: GlobalDownloadProgress
        ): Notification {
            ensureNotificationChannel(context)

            val modelTitle = global.activeModelTitle
                ?: progress.fileName.ifBlank { "Offline AI Model" }
            val queueSuffix = if (global.totalQueuedCount > 1) {
                " (${global.totalQueuedCount} queued)"
            } else {
                ""
            }
            val title = "$modelTitle$queueSuffix"

            val totalBytes = progress.totalBytes ?: global.totalBytesOverall.takeIf { it > 0L }
            val downloadedBytes = progress.bytesDownloaded.coerceAtLeast(0L)
            val pct = if (totalBytes != null && totalBytes > 0L) {
                ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else {
                (global.fraction * 100f).toInt().coerceIn(0, 100)
            }

            val isPaused = global.isPaused || progress.status == DownloadStatus.PAUSED
            val isError = progress.status == DownloadStatus.ERROR
            val isVerifying = progress.status == DownloadStatus.VERIFYING
            val isProbing = progress.status == DownloadStatus.PROBING
            val isIndeterminate = !isPaused && !isError && (isProbing || isVerifying || totalBytes == null || totalBytes <= 0L)

            val speedMb = progress.speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
            val speedPart = if (progress.speedBytesPerSec > 0L) {
                String.format(Locale.US, " • %.1f MB/s", speedMb)
            } else {
                ""
            }
            val etaPart = progress.etaSeconds?.let { sec ->
                if (sec >= 60) " • ETA ${sec / 60}m ${sec % 60}s" else " • ETA ${sec}s"
            }.orEmpty()

            val bodyText = when {
                isPaused -> "Paused at $pct% • ${ModelCatalog.formatBytes(downloadedBytes)} / ${ModelCatalog.formatBytes(totalBytes)}"
                isError -> progress.errorMessage ?: "Download paused due to error — tap Resume to retry"
                isVerifying -> "Verifying downloaded model (${ModelCatalog.formatBytes(downloadedBytes)})..."
                isProbing -> "Connecting & preparing download..."
                else -> "$pct% • ${ModelCatalog.formatBytes(downloadedBytes)} / ${ModelCatalog.formatBytes(totalBytes)}$speedPart$etaPart"
            }

            val openAppIntent = PendingIntent.getActivity(
                context,
                100,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(
                    if (isPaused || isError) android.R.drawable.stat_sys_warning else android.R.drawable.stat_sys_download
                )
                .setContentTitle(title)
                .setContentText(bodyText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                .setContentIntent(openAppIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(!isPaused && !isError)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (!isError) {
                builder.setProgress(100, pct, isIndeterminate)
            } else {
                builder.setProgress(0, 0, false)
            }

            if (isPaused || isError) {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    serviceActionPendingIntent(context, 101, ACTION_RESUME)
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    serviceActionPendingIntent(context, 102, ACTION_PAUSE)
                )
            }

            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                serviceActionPendingIntent(context, 103, ACTION_CANCEL_ALL)
            )

            return builder.build()
        }

        fun buildCompletionNotification(context: Context, fileName: String): Notification {
            ensureNotificationChannel(context)
            val openAppIntent = PendingIntent.getActivity(
                context,
                104,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Offline AI Model Ready")
                .setContentText("$fileName downloaded and ready for offline generation.")
                .setContentIntent(openAppIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun serviceActionPendingIntent(
            context: Context,
            requestCode: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                this.action = action
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, flags)
            } else {
                PendingIntent.getService(context, requestCode, intent, flags)
            }
        }

        private fun notificationManager(context: Context): NotificationManager? {
            return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        }
    }
}
