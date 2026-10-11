package com.vrc.friendtracker.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vrc.friendtracker.MainActivity
import com.vrc.friendtracker.R
import com.vrc.friendtracker.data.db.FriendDao
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.HistoryDao
import com.vrc.friendtracker.data.db.HistoryType
import com.vrc.friendtracker.data.db.WorldDao
import com.vrc.friendtracker.data.repo.SettingsRepo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns friend state changes into Android notifications, but only for friends
 * whose per-friend reminder is enabled (bell icon on the friend detail page).
 *
 * A persisted history-event cursor guarantees every change is delivered at most
 * once, whether it is detected by the foreground poll loop or by the periodic
 * background worker. A pass-level mutex stops the UI poll loop, the periodic
 * worker and the real-time monitor service from ever claiming the same batch
 * twice, and a short per-friend dedupe window drops identical repeated alerts
 * caused by API flapping.
 */
class FriendNotifier(
    context: Context,
    private val friendDao: FriendDao,
    private val historyDao: HistoryDao,
    private val worldDao: WorldDao,
    private val settingsRepo: SettingsRepo,
) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val lastBodyByFriend = HashMap<String, Pair<String, Long>>()

    /** Sends notifications for new watched-friend changes since the last pass.
     *  Best-effort: it never throws into the caller's sync path. */
    suspend fun notifyWatchedChanges() {
        try {
            mutex.withLock {
                val cursor = settingsRepo.getNotifyCursor()
                val events = historyDao.getEventsAfterId(cursor, 60)
                if (events.isEmpty()) return
                val maxId = events.maxOf { it.id }
                // Advance the cursor even when nothing is shown so past events never
                // get re-delivered after the user enables a reminder later.
                settingsRepo.setNotifyCursor(maxId)
                if (!notificationsAllowed()) return
                val watched = friendDao.getWatched().associateBy { it.id }
                if (watched.isEmpty()) return
                ensureChannel()
                val worldIds = events
                    .flatMap { listOfNotNull(it.oldValue, it.newValue) }
                    .filter { it.isNotBlank() }
                    .distinct()
                val worlds = worldDao.getByIds(worldIds).associateBy { it.id }
                for (event in events) {
                    val friend = watched[event.friendId] ?: continue
                    val body = eventBody(event, worlds) ?: continue
                    if (alreadyShownRecently(friend.id, body)) continue
                    rememberShown(friend.id, body)
                    postNotification(friend, body)
                }
            }
        } catch (_: Exception) {
            // Notifications are best-effort and must never crash the sync.
        }
    }

    /** Skips the exact same alert for the same friend repeated within a short
     *  window (the cursor plus mutex already prevent double delivery of one
     *  event; this additionally absorbs API blips that re-emit one change). */
    private fun alreadyShownRecently(friendId: String, body: String): Boolean {
        val (lastBody, lastAt) = lastBodyByFriend[friendId] ?: return false
        return body == lastBody &&
            System.currentTimeMillis() - lastAt < DEDUPE_WINDOW_MS
    }

    private fun rememberShown(friendId: String, body: String) {
        lastBodyByFriend[friendId] = body to System.currentTimeMillis()
    }

    private fun postNotification(friend: FriendEntity, body: String) {
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = shownName(friend)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title $body"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // Re-posting the same friend change must not buzz again.
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(app).notify(friend.id.hashCode() xor body.hashCode(), notification)
    }

    /** "上线了 / 下线了 / 位置变化：进入「…」/ 离开「…」" */
    private fun eventBody(
        event: com.vrc.friendtracker.data.db.HistoryEventEntity,
        worlds: Map<String, com.vrc.friendtracker.data.db.WorldEntity>,
    ): String? = when (event.type) {
        HistoryType.STATUS -> if (event.newValue == "在线") "上线了" else "下线了"
        HistoryType.ROOM -> {
            val oldName = event.oldValue?.takeIf { it.isNotBlank() }?.let { worlds[it]?.name ?: it }
            val newName = event.newValue?.takeIf { it.isNotBlank() }?.let { worlds[it]?.name ?: it }
            when {
                oldName == null && newName == null -> null
                oldName == null -> "位置变化：进入「$newName」"
                newName == null -> "位置变化：离开「$oldName」"
                else -> "位置变化：「$oldName」→「$newName」"
            }
        }
        else -> null
    }

    private fun shownName(friend: FriendEntity): String =
        friend.note?.trim()?.takeIf { it.isNotEmpty() } ?: friend.displayName

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "好友动态提醒", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "friend_changes"
        private const val DEDUPE_WINDOW_MS = 90_000L
    }
}
