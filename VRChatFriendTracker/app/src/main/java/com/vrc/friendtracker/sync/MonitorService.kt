package com.vrc.friendtracker.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vrc.friendtracker.MainActivity
import com.vrc.friendtracker.R
import com.vrc.friendtracker.VrApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app resident in the background so friend
 * online / offline / room changes can be checked every few seconds and pushed
 * as local notifications - the closest Android allows to WeChat-style real-time
 * reminders without a vendor messaging SDK.
 *
 * While the main friends screen exists its 5s ViewModel loop already detects
 * changes and notifies, so this service only stays resident (it never double
 * polls). When the screen is closed the service takes over the polling. It
 * stays idle when no per-friend bell reminder is enabled and stops itself when
 * the session expires or the master switch is turned off.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var loopJob: Job? = null
    private var lastPostedBody: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must run quickly after startForegroundService().
        try {
            startForeground(NOTIFICATION_ID, buildNotification(0))
        } catch (e: Exception) {
            // Some OEM ROMs / Android versions reject the foreground state
            // (missing permission, background start, etc.). Log it and stop
            // instead of crashing the whole app.
            runCatching {
                VrApp.INSTANCE.settingsRepo.logDiagnostic(
                    "MON", "前台服务启动失败: " + (e.message ?: e.javaClass.simpleName)
                )
            }
            runCatching { VrApp.INSTANCE.setMonitorRunning(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching { VrApp.INSTANCE.setMonitorRunning(true) }
        if (loopJob == null) {
            loopJob = scope.launch { monitorLoop() }
            runCatching {
                VrApp.INSTANCE.settingsRepo.logDiagnostic("MON", "实时监控服务已启动")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        runCatching { VrApp.INSTANCE.setMonitorRunning(false) }
        super.onDestroy()
    }

    /** Keeps the CPU awake while the monitor is enabled so the poll loop really
     *  runs with the screen off (otherwise Android deep sleep pauses it and no
     *  updates/alerts arrive until the phone wakes up). */
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VRCFD::monitor").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            runCatching {
                VrApp.INSTANCE.settingsRepo.logDiagnostic(
                    "MON", "唤醒锁获取失败: " + (e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    private suspend fun monitorLoop() {
        var consecutiveFailures = 0
        try {
            while (scope.isActive) {
                val app = VrApp.INSTANCE
                if (app.tokenStore.authToken == null ||
                    !app.sessionActive.value ||
                    !app.settingsRepo.monitorEnabled.value
                ) {
                    stopSelf()
                    return
                }
                val watched = try {
                    app.database.friendDao().getWatched()
                } catch (_: Exception) {
                    emptyList()
                }
                if (watched.isEmpty()) {
                    // Nothing to alert about yet: stay resident (keeps the process
                    // warm) without burning API requests.
                    consecutiveFailures = 0
                    postForeground(0)
                    delay(TICK_MS)
                    continue
                }
                // The in-app 5s poll loop handles checks + notifications while the
                // friends screen is alive; only take over when it is gone so the
                // background service never doubles the API request rate.
                val uiPollerFresh = System.currentTimeMillis() - app.lastUiPollAt < UI_POLL_GRACE_MS
                if (uiPollerFresh) {
                    postForeground(watched.size)
                    delay(TICK_MS)
                    continue
                }
                val outcome = try {
                    app.syncManager.syncMonitorTick()
                } catch (e: Exception) {
                    SyncOutcome.Failed(e.message ?: e.javaClass.simpleName)
                }
                when (outcome) {
                    is SyncOutcome.AuthExpired -> {
                        app.settingsRepo.logDiagnostic("MON", "会话失效，停止实时监控")
                        app.onLogout()
                        stopSelf()
                        return
                    }
                    is SyncOutcome.Done -> {
                        consecutiveFailures = 0
                        try {
                            app.notifier.notifyWatchedChanges()
                        } catch (_: Exception) {
                            // Notifications are best-effort; never break the loop.
                        }
                        postForeground(watched.size)
                        delay(TICK_MS)
                    }
                    is SyncOutcome.Failed -> {
                        consecutiveFailures++
                        // Transient network / rate-limit errors: back off, then retry.
                        delay(if (consecutiveFailures >= 3) LONG_BACKOFF_MS else SHORT_BACKOFF_MS)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching {
                VrApp.INSTANCE.settingsRepo.logDiagnostic(
                    "MON", "监控循环异常: " + (e.message ?: e.javaClass.simpleName)
                )
            }
            stopSelf()
        }
    }

    private fun postForeground(watchedCount: Int) {
        val body = if (watchedCount > 0)
            "正在实时监控 $watchedCount 位好友的上下线与位置变化"
        else
            "尚未开启好友提醒：打开好友详情页，点亮右上角铃铛"
        if (body == lastPostedBody) return
        lastPostedBody = body
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(watchedCount))
        }
    }

    private fun buildNotification(watchedCount: Int): Notification {
        val text = if (watchedCount > 0)
            "正在实时监控 $watchedCount 位好友的上下线与位置变化"
        else
            "尚未开启好友提醒：打开好友详情页，点亮右上角铃铛"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("实时监控运行中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "实时监控", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持后台运行，及时提醒好友动态"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "monitor_service"
        private const val NOTIFICATION_ID = 1001
        /** Poll cadence once the in-app loop is no longer running. */
        private const val TICK_MS = 5_000L
        /** The friends list polls every ~5s; skip while that timestamp is fresh. */
        private const val UI_POLL_GRACE_MS = 20_000L
        private const val SHORT_BACKOFF_MS = 60_000L
        private const val LONG_BACKOFF_MS = 5 * 60_000L
    }
}
