package com.vrc.friendtracker.ui.friends

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.ImageRequest
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.sync.SyncOutcome
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.TimeUtil
import com.vrc.friendtracker.util.formatChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.HashSet

class FriendsViewModel(private val app: VrApp) : ViewModel() {

    private val db = app.database

    val friends: StateFlow<List<FriendEntity>> =
        db.friendDao().observeAll()
            .map { list -> runCatching { list.sortedWith(friendComparator(roomCountsOf(list))) }.getOrDefault(list) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val worlds: StateFlow<Map<String, WorldEntity>> =
        db.worldDao().observeAll()
            .map { list -> list.associateBy { it.id } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    data class RecentChange(
        val id: Long,
        val friendName: String,
        val avatarUrl: String?,
        val avatarFallbackUrl: String?,
        val type: String,
        val text: String,
        val timestamp: Long,
    )

    data class ChangeNotice(val friendName: String, val text: String)

    /** Live feed of the latest recorded changes, joined with friend/world names. */
    val recentChanges: StateFlow<List<RecentChange>> =
        combine(db.historyDao().observeRecent(5), friends, worlds) { events, friendList, worldMap ->
            val friendsById = friendList.associateBy { it.id }
            events.mapNotNull { e ->
                val friend = friendsById[e.friendId] ?: return@mapNotNull null
                RecentChange(e.id, friend.displayName, friend.avatarUrl, friend.avatarFallbackUrl, e.type, formatChange(e, worldMap), e.timestamp)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _notices = MutableSharedFlow<ChangeNotice>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<ChangeNotice> = _notices.asSharedFlow()


    val lastSyncAt: StateFlow<Long> = app.settingsRepo.lastSyncAt
    val lastSyncError: StateFlow<String?> = app.settingsRepo.lastSyncError
    val ownDisplayName: StateFlow<String?> = app.settingsRepo.ownDisplayName

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Baseline state used to detect friend location/status changes between cycles. */
    private var previousState: Map<String, FriendEntity>? = null

    init {
        viewModelScope.launch {
            var lastFullSyncAt = 0L
            while (true) {
                try {
                val poll = MIN_POLL_MS
                val now = System.currentTimeMillis()
                // Fast friend-list polling every cycle; full sync (session check +
                // world info + trust-rank profiles) at most once per minute.
                val full = now - lastFullSyncAt >= FULL_SYNC_EVERY_MS
                if (full) lastFullSyncAt = now
                // Run the whole cycle off the main thread so 5s polling never
                // steals frames while the user is scrolling the list.
                val outcome = withContext(Dispatchers.Default) { runSync(light = !full) }
                when (outcome) {
                    SyncOutcome.AuthExpired -> {
                        app.onLogout()
                        break
                    }
                    is SyncOutcome.Failed -> {
                        // Back off after rate limiting / repeated errors.
                        delay(BACKOFF_MS)
                    }
                    else -> preloadAvatars()
                }
                    delay(poll)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.settingsRepo.logDiagnostic(
                        "SYNC", "LOOP: " + (e.message ?: e.javaClass.simpleName)
                    )
                    delay(BACKOFF_MS)
                }
            }
        }
    }

    fun refresh() {
        if (_syncing.value) return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) { runSync(light = false) }
            when (outcome) {
                SyncOutcome.AuthExpired -> app.onLogout()
                else -> preloadAvatars()
            }
        }
    }

    /** Runs one sync cycle, guarding against overlap and driving the spinner. */
    private suspend fun runSync(light: Boolean): SyncOutcome {
        if (_syncing.value) return SyncOutcome.Failed("busy")
        _syncing.value = true
        return try {
            val outcome = app.syncManager.syncOnce(light = light)
            if (outcome is SyncOutcome.Done) diffAndNotify()
            outcome
        } finally {
            _syncing.value = false
        }
    }

    /** Compares the last observed friend states and emits real-time change notices. */
    private suspend fun diffAndNotify() {
        val current = db.friendDao().getAll()
        val prev = previousState
        if (prev == null) {
            // First sync establishes the baseline; nothing to notify yet.
            previousState = current.associateBy { it.id }
            return
        }
        val worldNames = db.worldDao().getByIds(current.mapNotNull { it.worldId })
            .associate { it.id to it.name }
        val notices = mutableListOf<ChangeNotice>()
        for (friend in current) {
            val old = prev[friend.id] ?: continue
            if (old.isOnline && !friend.isOnline) {
                notices += ChangeNotice(friend.displayName, "下线了")
            } else if (!old.isOnline && friend.isOnline) {
                notices += ChangeNotice(friend.displayName, "上线了")
            } else if (old.isOnline && friend.isOnline && old.worldId != friend.worldId) {
                val newName = friend.worldId?.let { worldNames[it] } ?: friend.worldId ?: "私密房间"
                notices += ChangeNotice(friend.displayName, "进入 $newName")
            }
        }
        previousState = current.associateBy { it.id }
        notices.take(8).forEach { _notices.tryEmit(it) }
    }

    /** Warms Coil's disk cache with online friends' avatars so scrolling the
     *  list never waits on slow network loads. Skips hosts known to be blocked. */
    private suspend fun preloadAvatars() {
        val failedHosts = app.settingsRepo.failedImageHosts.value
        val loader = Coil.imageLoader(app)
        val online = db.friendDao().getOnline()
        online
            .filter { !it.avatarUrl.isNullOrBlank() && it.avatarUrl !in preloadedAvatarUrls }
            .filter { runCatching { Uri.parse(it.avatarUrl).host }.getOrNull() !in failedHosts }
            .sortedByDescending { it.lastActivity?.let(TimeUtil::parseIsoToEpoch) ?: 0L }
            .take(PRELOAD_BATCH)
            .forEach { friend ->
                val url = friend.avatarUrl!!
                preloadedAvatarUrls += url
                loader.enqueue(
                    ImageRequest.Builder(app).data(url).size(128).build()
                )
            }
    }

    private val preloadedAvatarUrls: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Number of online friends per world (used to sort bigger rooms first). */
    private fun roomCountsOf(list: List<FriendEntity>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (f in list) {
            val key = InstanceUtil.roomGroupKey(f.isOnline, f.worldId, f.instanceId) ?: continue
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    /** List order: friends in the same world together (public first, then
     *  private rooms), bigger rooms first, online-without-room next,
     *  offline at the bottom. */
    private fun friendComparator(roomCounts: Map<String, Int>): Comparator<FriendEntity> =
        Comparator { a, b ->
            val ca = listCategory(a)
            val cb = listCategory(b)
            when {
                ca != cb -> ca - cb
                ca <= 1 -> {
                    val na = roomCounts[InstanceUtil.roomGroupKey(a.isOnline, a.worldId, a.instanceId) ?: ""] ?: 0
                    val nb = roomCounts[InstanceUtil.roomGroupKey(b.isOnline, b.worldId, b.instanceId) ?: ""] ?: 0
                    if (na != nb) nb - na
                    else {
                        val wa = a.worldId ?: ""
                        val wb = b.worldId ?: ""
                        if (wa != wb) wa.compareTo(wb)
                        else {
                            // Same world: cluster by instance so friends in the
                            // same room stay adjacent, then by display name.
                            val ia = a.instanceId ?: ""
                            val ib = b.instanceId ?: ""
                            if (ia != ib) ia.compareTo(ib)
                            else a.displayName.compareTo(b.displayName, ignoreCase = true)
                        }
                    }
                }
                else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
            }
        }

    private fun listCategory(friend: FriendEntity): Int = when {
        !friend.isOnline -> 3
        friend.worldId.isNullOrBlank() -> 2
        InstanceUtil.isPrivateAccess(friend.instanceId) -> 1
        else -> 0
    }



    companion object {
        private const val MIN_POLL_MS = 5_000L
        private const val FULL_SYNC_EVERY_MS = 60_000L
        private const val BACKOFF_MS = 30_000L
        private const val PRELOAD_BATCH = 10
    }
}

