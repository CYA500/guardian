package com.guardian.app.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guardian.app.R
import com.guardian.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the lock alive even if the user swipes away
 * the app.  Ticks every second, updates the persistent notification with the
 * countdown, and releases the lock when the timer expires.
 */
@AndroidEntryPoint
class LockForegroundService : Service() {

    @Inject lateinit var lockEngine: LockEngine
    @Inject lateinit var alertEngine: com.guardian.app.engine.AlertEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null

    companion object {
        const val EXTRA_LEVEL   = "level"
        const val EXTRA_END_MS  = "end_ms"
        const val EXTRA_MESSAGE = "message"

        private const val CHANNEL_ID   = "guardian_lock_channel"
        private const val NOTIF_ID     = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val level   = intent?.getIntExtra(EXTRA_LEVEL, 2) ?: 2
        val endMs   = intent?.getLongExtra(EXTRA_END_MS, 0L) ?: 0L
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: ""

        startForeground(NOTIF_ID, buildNotification(endMs))

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val remaining = endMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    lockEngine.releaseLock()
                    stopSelf()
                    break
                }
                updateNotification(remaining)
                delay(1_000L)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الحارس — قفل نشط",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يُعلِمك بالوقت المتبقي على انتهاء القفل"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(endMs: Long): Notification {
        val remaining = maxOf(0L, endMs - System.currentTimeMillis())
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guardian_shield)
            .setContentTitle("الحارس — قفل نشط")
            .setContentText("الوقت المتبقي: ${formatDuration(remaining)}")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(remainingMs: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guardian_shield)
            .setContentTitle("الحارس — قفل نشط")
            .setContentText("الوقت المتبقي: ${formatDuration(remainingMs)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
