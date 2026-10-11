package com.vrc.friendtracker.ui.friends

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.api.dto.AvatarDto
import com.vrc.friendtracker.data.api.dto.FavoriteDto
import com.vrc.friendtracker.data.api.dto.PrintDto
import com.vrc.friendtracker.data.api.dto.UpdateAvatarBody
import com.vrc.friendtracker.data.api.dto.WorldDto
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.sync.SyncOutcome
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.Status
import com.vrc.friendtracker.util.formatChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.HashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class FriendsViewModel(private val app: VrApp) : ViewModel() {

    private val db = app.database

    val friends: StateFlow<List<FriendEntity>> =
        db.friendDao().observeAll()
            .map { list ->
                runCatching {
                    list.sortedWith(friendComparator(roomCountsOf(list), groupPrioritiesOf(list)))
                }.getOrDefault(list)
            }
            // Keep sorting/grouping off the main thread so a 5s poll never
            // steals frames from an active scroll.
            .flowOn(Dispatchers.Default)
            // Compare only what the UI shows: the full sync rewrites rows every
            // minute to advance internal timestamps (updatedAt/lastSeenAt/
            // tagsCheckedAt), and a plain equals would re-emit the whole list
            // (and recompose every card) even when nothing visible changed.
            .distinctUntilChanged { a, b -> sameVisibleList(a, b) }
            // The comparison above also runs off the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val worlds: StateFlow<Map<String, WorldEntity>> =
        db.worldDao().observeAll()
            .map { list -> list.associateBy { it.id } }
            // Same idea: world rows carry an updatedAt that changes on every
            // cache refresh; only re-emit when the visible world info changed.
            .distinctUntilChanged { a, b -> sameWorlds(a, b) }
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

    private val _ownAvatarUrl = MutableStateFlow<String?>(null)
    val ownAvatarUrl: StateFlow<String?> = _ownAvatarUrl.asStateFlow()

    sealed interface OwnAvatarsState {
        data object Loading : OwnAvatarsState
        data class Loaded(val avatars: List<AvatarDto>) : OwnAvatarsState
        data class Error(val message: String) : OwnAvatarsState
    }

    private val _ownAvatars = MutableStateFlow<OwnAvatarsState>(OwnAvatarsState.Loading)
    val ownAvatars: StateFlow<OwnAvatarsState> = _ownAvatars.asStateFlow()

    /** One VRChat favorite folder (分类) with the worlds saved inside it. */
    data class WorldFavoriteGroup(
        val key: String,
        val name: String,
        val worlds: List<WorldDto>,
    )

    sealed interface WorldFavoritesState {
        data object Loading : WorldFavoritesState
        data class Loaded(val groups: List<WorldFavoriteGroup>) : WorldFavoritesState
        data class Error(val message: String) : WorldFavoritesState
    }

    private val _worldFavorites = MutableStateFlow<WorldFavoritesState>(WorldFavoritesState.Loading)
    val worldFavorites: StateFlow<WorldFavoritesState> = _worldFavorites.asStateFlow()
    private var worldFavoritesLoadedAt = 0L

    sealed interface PrintsState {
        data object Loading : PrintsState
        data class Loaded(val prints: List<PrintDto>) : PrintsState
        data class Error(val message: String) : PrintsState
    }

    private val _prints = MutableStateFlow<PrintsState>(PrintsState.Loading)
    val prints: StateFlow<PrintsState> = _prints.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    /** One-shot upload/download/delete/rename results shown as toasts. */
    private val _printMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val printMessages: SharedFlow<String> = _printMessages.asSharedFlow()

    private var ownUserId: String? = null

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** True only for manual refreshes. Background polls never spin the
     *  top-bar indicator: while it animates, the UI redraws every frame,
     *  which steals frames from an active scroll. */
    private val _showSpinner = MutableStateFlow(false)
    val showSpinner: StateFlow<Boolean> = _showSpinner.asStateFlow()

    /** Baseline state used to detect friend location/status changes between cycles. */
    private var previousState: Map<String, FriendEntity>? = null
    /** Per-friend dedupe so a presence flap cannot spam identical notices. */
    private val lastNoticeByFriend = HashMap<String, Pair<String, Long>>()

    init {
        viewModelScope.launch { loadOwnProfile() }
        viewModelScope.launch {
            while (true) {
                try {
                val poll = MIN_POLL_MS
                // Run the whole cycle off the main thread so 5s polling never
                // steals frames while the user is scrolling the list. The auto
                // poll stays a light sync every ~5s; the complete sync (full
                // profiles, trust ranks, notes, offline friends) only happens
                // on a manual refresh.
                val outcome = withContext(Dispatchers.Default) { runSync(light = true) }
                when (outcome) {
                    SyncOutcome.AuthExpired -> {
                        app.onLogout()
                        break
                    }
                    is SyncOutcome.Failed -> {
                        // Back off after rate limiting / repeated errors.
                        delay(BACKOFF_MS)
                    }
                    is SyncOutcome.Done -> Unit
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

    /** Serializes sync cycles. A manual refresh used to be dropped outright
     *  when the 5s poll happened to be in flight, which is why the list only
     *  filled in after several taps; now it queues and always runs. */
    private val syncLock = Mutex()
    private val refreshQueued = AtomicBoolean(false)

    fun refresh() {
        // Coalesce repeated taps, but never drop the request just because a
        // light poll is currently running.
        if (!refreshQueued.compareAndSet(false, true)) return
        viewModelScope.launch {
            _showSpinner.value = true
            try {
                val outcome = withContext(Dispatchers.Default) { runSync(light = false, forceOffline = true) }
                when (outcome) {
                    SyncOutcome.AuthExpired -> app.onLogout()
                    else -> Unit
                }
            } finally {
                refreshQueued.set(false)
                _showSpinner.value = false
            }
        }
    }

    /** Runs one sync cycle, serialized so cycles can never overlap. */
    private suspend fun runSync(light: Boolean, forceOffline: Boolean = false): SyncOutcome =
        syncLock.withLock {
            _syncing.value = true
            try {
                val outcome = app.syncManager.syncOnce(light = light, forceOffline = forceOffline)
                if (outcome is SyncOutcome.Done) {
                    diffAndNotify()
                    // Deliver system notifications for reminder-enabled friends.
                    app.notifier.notifyWatchedChanges()
                }
                // Tell the background monitor that the in-app loop is alive so it
                // does not duplicate this network tick while the screen is open.
                app.touchUiPoll()
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
                if (noticeFresh(friend.id, "下线了")) {
                    notices += ChangeNotice(friend.displayName, "下线了")
                }
            } else if (!old.isOnline && friend.isOnline) {
                if (noticeFresh(friend.id, "上线了")) {
                    notices += ChangeNotice(friend.displayName, "上线了")
                }
            } else if (old.isOnline && friend.isOnline && old.worldId != friend.worldId) {
                val newName = friend.worldId?.let { worldNames[it] } ?: friend.worldId ?: "私密房间"
                if (noticeFresh(friend.id, "进入 $newName")) {
                    notices += ChangeNotice(friend.displayName, "进入 $newName")
                }
            }
        }
        previousState = current.associateBy { it.id }
        notices.take(8).forEach { _notices.tryEmit(it) }
    }

    private fun noticeFresh(friendId: String, text: String): Boolean {
        val (lastText, lastAt) = lastNoticeByFriend[friendId] ?: return true
        val fresh = !(text == lastText && System.currentTimeMillis() - lastAt < NOTICE_DEDUPE_MS)
        if (fresh) lastNoticeByFriend[friendId] = text to System.currentTimeMillis()
        return fresh
    }

    /** Number of online friends per world (used to sort bigger rooms first). */
    private fun roomCountsOf(list: List<FriendEntity>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (f in list) {
            val key = InstanceUtil.roomGroupKey(f.isOnline, f.worldId, f.instanceId) ?: continue
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    /** Best (greenest) status light of every group, so a room containing a
     *  green (active) friend stays above rooms whose members are all yellow. */
    private fun groupPrioritiesOf(list: List<FriendEntity>): Map<String, Int> {
        val map = HashMap<String, Int>()
        for (f in list) {
            val key = groupKey(f)
            val priority = Status.priority(f.status, f.isOnline)
            val current = map[key]
            if (current == null || priority < current) map[key] = priority
        }
        return map
    }

    private fun roomKey(friend: FriendEntity): String =
        InstanceUtil.roomGroupKey(friend.isOnline, friend.worldId, friend.instanceId) ?: ""

    private fun groupKey(friend: FriendEntity): String =
        InstanceUtil.friendGroupKey(friend.id, friend.isOnline, friend.worldId, friend.instanceId)

    /** Pins/unpins a friend; the list re-sorts so pinned friends float to the top. */
    fun setPinned(friendId: String, pinned: Boolean) {
        viewModelScope.launch { db.friendDao().updatePinned(friendId, pinned) }
    }
    
    /** List order: pinned friends first, then friends who are in a room
     *  together (the room with the most friends first, public rooms before
     *  private ones on equal size), rooms ranked by their best status light
     *  (Join Me -> Online -> Ask Me -> Busy), then online friends without a
     *  room and offline friends at the bottom. */
    private fun friendComparator(
        roomCounts: Map<String, Int>,
        groupPriority: Map<String, Int>,
    ): Comparator<FriendEntity> =
        Comparator { a, b ->
            if (a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                val ca = listCategory(a)
                val cb = listCategory(b)
                val inRoomA = ca <= 1
                val inRoomB = cb <= 1
                when {
                    // Friends who are in a room (grouped) come before the rest.
                    inRoomA != inRoomB -> if (inRoomA) -1 else 1
                    inRoomA -> {
                        // The room with the most friends stays on top, so a lone
                        // friend in a room can never outrank a populated group
                        // (not even with a blue status light).
                        val na = roomCounts[roomKey(a)] ?: 0
                        val nb = roomCounts[roomKey(b)] ?: 0
                        if (na != nb) nb - na
                        // Equal size: public rooms before private ones.
                        else if (ca != cb) ca - cb
                        else {
                            val pa = groupPriority[groupKey(a)] ?: 90
                            val pb = groupPriority[groupKey(b)] ?: 90
                            if (pa != pb) pa - pb
                            else {
                                val wa = a.worldId ?: ""
                                val wb = b.worldId ?: ""
                                if (wa != wb) wa.compareTo(wb)
                                else {
                                    // Same world: cluster by instance so friends in
                                    // the same room stay adjacent, then sort the room
                                    // members by their own status light and name.
                                    val ia = a.instanceId ?: ""
                                    val ib = b.instanceId ?: ""
                                    if (ia != ib) ia.compareTo(ib)
                                    else {
                                        val sa = Status.priority(a.status, a.isOnline)
                                        val sb = Status.priority(b.status, b.isOnline)
                                        if (sa != sb) sa - sb
                                        else a.displayName.compareTo(b.displayName, ignoreCase = true)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // Online friends without a room follow the status-light
                        // order (blue, green, yellow, red); ties go by name.
                        val sa = Status.priority(a.status, a.isOnline)
                        val sb = Status.priority(b.status, b.isOnline)
                        if (sa != sb) sa - sb
                        else a.displayName.compareTo(b.displayName, ignoreCase = true)
                    }
                }
            }
        }

    private fun listCategory(friend: FriendEntity): Int = when {
        !friend.isOnline -> 3
        friend.worldId.isNullOrBlank() -> 2
        InstanceUtil.isPrivateAccess(friend.instanceId) -> 1
        else -> 0
    }

    /** True when two friend lists show the same content (ignores internal
     *  bookkeeping timestamps that the full sync advances every minute). */
    private fun sameVisibleList(a: List<FriendEntity>, b: List<FriendEntity>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!a[i].sameVisibleAs(b[i])) return false
        }
        return true
    }

    private fun FriendEntity.sameVisibleAs(other: FriendEntity): Boolean =
        id == other.id &&
        displayName == other.displayName &&
        note == other.note &&
        avatarUrl == other.avatarUrl &&
        avatarFallbackUrl == other.avatarFallbackUrl &&
        bio == other.bio &&
        bioLinksJson == other.bioLinksJson &&
        pronouns == other.pronouns &&
        tagsJson == other.tagsJson &&
        trustRank == other.trustRank &&
        isTroll == other.isTroll &&
        isModerator == other.isModerator &&
        status == other.status &&
        statusDescription == other.statusDescription &&
        location == other.location &&
        worldId == other.worldId &&
        instanceId == other.instanceId &&
        isOnline == other.isOnline &&
        lastLogin == other.lastLogin &&
        // last_activity ticks with every heartbeat for online friends, which
        // would otherwise re-emit the whole list (recomposing every card)
        // every 5s poll. Only compare it for offline friends, where it drives
        // the "离线 x 分钟前" text.
        (isOnline || lastActivity == other.lastActivity) &&
        lastPlatform == other.lastPlatform &&
        pinned == other.pinned

    /** True when two world maps show the same content (ignores updatedAt). */
    private fun sameWorlds(a: Map<String, WorldEntity>, b: Map<String, WorldEntity>): Boolean {
        if (a.size != b.size) return false
        for ((id, wa) in a) {
            val wb = b[id] ?: return false
            if (wa.name != wb.name ||
                wa.thumbnailUrl != wb.thumbnailUrl ||
                wa.imageUrl != wb.imageUrl ||
                wa.authorName != wb.authorName ||
                wa.description != wb.description ||
                wa.capacity != wb.capacity ||
                wa.releaseStatus != wb.releaseStatus ||
                wa.tags != wb.tags
            ) return false
        }
        return true
    }



    /** Fetches the logged-in user profile once (feeds the drawer avatar). */
    private suspend fun loadOwnProfile() {
        try {
            val me = app.api.getCurrentUser()
            _ownAvatarUrl.value = me.profilePicOverrideThumbnail?.takeIf { it.isNotBlank() }
                ?: me.profilePicOverride?.takeIf { it.isNotBlank() }
                ?: me.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                ?: me.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
            if (!me.displayName.isNullOrBlank()) {
                app.settingsRepo.setOwnDisplayName(me.displayName)
            }
        } catch (_: Exception) {
            // best-effort: the drawer falls back to the initial-letter avatar
        }
    }

    /** Fetches avatars owned by the logged-in user (including private/hidden). */
    fun loadOwnAvatars() {
        if (_ownAvatars.value is OwnAvatarsState.Loaded) return
        viewModelScope.launch {
            _ownAvatars.value = OwnAvatarsState.Loading
            try {
                val all = mutableListOf<AvatarDto>()
                var offset = 0
                while (true) {
                    val page = app.api.getOwnAvatars(offset = offset, number = 100)
                    all += page
                    if (page.size < 100 || offset >= 900) break
                    offset += page.size
                }
                _ownAvatars.value = OwnAvatarsState.Loaded(all)
            } catch (e: Exception) {
                _ownAvatars.value = OwnAvatarsState.Error("获取失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** Fetches the logged-in user's favorited worlds (地图收藏) for the 收藏 tab,
     *  split by favorite folder so each VRChat 分类 shows as its own section.
     *  Favorite entries only carry world ids, so each world's details (name,
     *  thumbnail) are fetched individually with limited concurrency. */
    fun loadWorldFavorites(force: Boolean = false) {
        val current = _worldFavorites.value
        val fresh = current is WorldFavoritesState.Loaded &&
            System.currentTimeMillis() - worldFavoritesLoadedAt < 120_000
        if (fresh && !force) return
        viewModelScope.launch {
            _worldFavorites.value = WorldFavoritesState.Loading
            try {
                val groupKeyById = LinkedHashMap<String, String>()
                for (favorite in fetchAllFavorites("world")) {
                    val id = favorite.favoriteId
                    if (id.isNotBlank()) groupKeyById[id] = favoriteGroupKey(favorite)
                }
                val ids = groupKeyById.keys.toList()
                if (ids.isEmpty()) {
                    worldFavoritesLoadedAt = System.currentTimeMillis()
                    _worldFavorites.value = WorldFavoritesState.Loaded(emptyList())
                    return@launch
                }
                val groupNames = loadFavoriteGroupNames()
                runCatching {
                    val counts = groupKeyById.values.groupingBy { it }.eachCount()
                    app.settingsRepo.logDiagnostic(
                        "FAV",
                        "地图收藏: " + groupKeyById.size + " 个, 分组 [" +
                            counts.entries.joinToString(", ") { it.key + "x" + it.value } +
                            "], 名称 " + groupNames.size + " 个",
                    )
                }
                // Serve the grid from the local world cache first so it is
                // never blank while the network refresh runs.
                val cachedById = db.worldDao().getByIds(ids).associateBy { it.id }
                val seed = HashMap<String, WorldDto>()
                cachedById.forEach { (id, entity) -> seed[id] = entity.toWorldDto() }
                if (seed.isNotEmpty()) {
                    _worldFavorites.value = WorldFavoritesState.Loaded(
                        buildFavoriteGroups(ids, groupKeyById, groupNames, seed)
                    )
                }
                // Refresh details progressively (10 at a time) and cache each
                // world so the next visit renders instantly.
                val freshById = HashMap<String, WorldDto>()
                ids.chunked(10).forEach { batch ->
                    batch.map { id -> async { id to runCatching { app.api.getWorld(id) } } }
                        .forEach { deferred ->
                            val (id, result) = deferred.await()
                            result.onSuccess { dto ->
                                freshById[id] = dto
                                db.worldDao().upsert(toWorldEntity(dto, System.currentTimeMillis()))
                            }
                        }
                    if (freshById.isNotEmpty()) {
                        _worldFavorites.value = WorldFavoritesState.Loaded(
                            buildFavoriteGroups(ids, groupKeyById, groupNames, freshById)
                        )
                    }
                }
                worldFavoritesLoadedAt = System.currentTimeMillis()
                if (freshById.isEmpty() && seed.isEmpty()) {
                    _worldFavorites.value = WorldFavoritesState.Error("地图信息获取失败，请重试")
                }
            } catch (e: Exception) {
                _worldFavorites.value = WorldFavoritesState.Error("获取失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** All favorites of one type; paged because accounts can exceed one page. */
    private suspend fun fetchAllFavorites(type: String): List<FavoriteDto> {
        val all = mutableListOf<FavoriteDto>()
        var offset = 0
        while (all.size < MAX_FAVORITES) {
            val page = app.api.getFavorites(type = type, offset = offset, number = 100)
            all += page
            if (page.size < 100) break
            offset += page.size
        }
        return all
    }

    /** Folder key of a favorite: newer API field first, then the tag, else "未分类". */
    private fun favoriteGroupKey(favorite: FavoriteDto): String =
        favorite.favoriteGroupName?.takeIf { it.isNotBlank() }
            ?: favorite.tags?.firstOrNull { it.isNotBlank() }
            ?: UNGROUPED_FAVORITE_KEY

    /** Folder display names, keyed by group key; best effort (endpoint is optional). */
    private suspend fun loadFavoriteGroupNames(): Map<String, String> = runCatching {
        app.api.getFavoriteGroups("world")
            .mapNotNull { (key, group) ->
                // Only the real display name is used; when VRChat omits it the
                // caller falls back to a readable "地图收藏 N" label instead of
                // showing the raw key like "worlds1".
                group.displayName?.takeIf { it.isNotBlank() }?.let { key to it }
            }
            .toMap()
    }.getOrDefault(emptyMap())

    /** Buckets the available world details into folders, keeping favorite order. */
    private fun buildFavoriteGroups(
        ids: List<String>,
        groupKeyById: Map<String, String>,
        groupNames: Map<String, String>,
        byId: Map<String, WorldDto>,
    ): List<WorldFavoriteGroup> {
        val buckets = LinkedHashMap<String, MutableList<WorldDto>>()
        for (id in ids) {
            val world = byId[id] ?: continue
            val key = groupKeyById[id] ?: UNGROUPED_FAVORITE_KEY
            buckets.getOrPut(key) { mutableListOf() }.add(world)
        }
        return buckets.entries
            .sortedWith(compareBy({ favoriteGroupSortIndex(it.key) }, { it.key }))
            .map { (key, worlds) ->
                WorldFavoriteGroup(key, groupNames[key] ?: defaultFavoriteGroupLabel(key), worlds)
            }
    }

    /** worlds1/2/3… keep their natural order; everything else sorts last. */
    private fun favoriteGroupSortIndex(key: String): Int =
        key.removePrefix("worlds").takeIf { it != key }?.toIntOrNull() ?: Int.MAX_VALUE

    private fun defaultFavoriteGroupLabel(key: String): String {
        if (key == UNGROUPED_FAVORITE_KEY) return "未分类"
        val suffix = key.removePrefix("worlds")
        return if (suffix != key && suffix.isNotBlank()) "地图收藏 $suffix" else key
    }

    /** Fetches the logged-in user's 拍立得照片 (prints). */
    fun loadPrints(force: Boolean = false) {
        val current = _prints.value
        if (current is PrintsState.Loaded && !force) return
        viewModelScope.launch {
            _prints.value = PrintsState.Loading
            try {
                val me = ownUserId ?: app.api.getCurrentUser().id.also { ownUserId = it }
                if (me.isBlank()) {
                    _prints.value = PrintsState.Error("无法获取用户信息，请重新登录")
                    return@launch
                }
                // The prints endpoint paginates, but its parameter name is not
                // documented: older VRChat list endpoints use `number`, newer
                // ones use `n`. Probe both (two pages each), keep whichever
                // returns more distinct prints, then page through the rest.
                suspend fun probe(useN: Boolean): List<PrintDto> {
                    val p1 = if (useN) app.api.getUserPrintsN(me, 0, 100)
                    else app.api.getUserPrints(me, 0, 100)
                    val p2 = if (useN) app.api.getUserPrintsN(me, 10, 100)
                    else app.api.getUserPrints(me, 10, 100)
                    return (p1 + p2).distinctBy { it.id }
                }
                val probedNumber = probe(false)
                val probedN = probe(true)
                val useN = probedN.size > probedNumber.size
                val all = (if (useN) probedN else probedNumber).toMutableList()
                val seen = all.mapTo(HashSet()) { it.id }
                var offset = 10
                var emptyPages = 0
                while (offset < 2000 && emptyPages < 2) {
                    val page = if (useN) app.api.getUserPrintsN(me, offset, 100)
                    else app.api.getUserPrints(me, offset, 100)
                    val fresh = page.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) {
                        emptyPages++
                    } else {
                        emptyPages = 0
                        all += fresh
                    }
                    offset += page.size.coerceAtLeast(1)
                }
                val imageMissing = all.count { it.files?.image.isNullOrBlank() }
                app.settingsRepo.logDiagnostic("PRINTS", "获取 ${all.size} 张 (n=$useN, image 空 $imageMissing)")
                _prints.value = PrintsState.Loaded(all)
            } catch (e: Exception) {
                _prints.value = PrintsState.Error("获取失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** Uploads a print image (bytes from the picked photo) to the user's 拍立得相册. */
    fun uploadPrint(bytes: ByteArray, mimeType: String) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            try {
                val me = ownUserId ?: app.api.getCurrentUser().id.also { ownUserId = it }
                val ext = when (mimeType) {
                    "image/jpeg" -> "jpg"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "png"
                }
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    "print.$ext",
                    bytes.toRequestBody(mimeType.ifBlank { "image/png" }.toMediaType()),
                )
                val timestamp = java.time.Instant.now().toString()
                    .toRequestBody("text/plain".toMediaType())
                app.api.uploadPrint(
                    image = imagePart,
                    timestamp = timestamp,
                    note = null,
                    worldId = null,
                    worldName = null,
                )
                _printMessages.tryEmit("上传成功")
                loadPrints(force = true)
            } catch (e: Exception) {
                app.settingsRepo.logDiagnostic("PRINTS", "上传失败: ${e.message}")
                _printMessages.tryEmit("上传失败：" + (e.message ?: "网络错误"))
            } finally {
                _uploading.value = false
            }
        }
    }

    /** Downloads a print image into the user-chosen content Uri. */
    fun downloadPrint(print: PrintDto, targetUri: Uri) {
        viewModelScope.launch {
            try {
                val url = print.files?.image ?: throw IllegalStateException("无图片地址")
                val host = runCatching { Uri.parse(url).host }.getOrNull()
                val cookie = app.tokenStore.authToken
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
                    )
                    .apply {
                        if (cookie != null && host?.endsWith(".vrchat.cloud") == true) {
                            header("Cookie", "auth=$cookie")
                        }
                    }
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body?.bytes() ?: throw IllegalStateException("无内容")
                    app.contentResolver.openOutputStream(targetUri)?.use { it.write(body) }
                        ?: throw IllegalStateException("无法写入文件")
                }
                _printMessages.tryEmit("已保存")
            } catch (e: Exception) {
                app.settingsRepo.logDiagnostic("PRINTS", "下载失败: ${e.message}")
                _printMessages.tryEmit("下载失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** Deletes one print. */
    fun deletePrint(printId: String) {
        viewModelScope.launch {
            try {
                app.api.deletePrint(printId)
                _printMessages.tryEmit("已删除")
                loadPrints(force = true)
            } catch (e: Exception) {
                app.settingsRepo.logDiagnostic("PRINTS", "删除失败: ${e.message}")
                _printMessages.tryEmit("删除失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** Renames one of the logged-in user's avatars. */
    fun renameAvatar(avatarId: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name.length > 64) {
            _printMessages.tryEmit("名称不能为空且不能超过 64 个字符")
            return
        }
        viewModelScope.launch {
            try {
                val updated = app.api.updateAvatar(avatarId, UpdateAvatarBody(name = name))
                val current = _ownAvatars.value
                if (current is OwnAvatarsState.Loaded) {
                    _ownAvatars.value = OwnAvatarsState.Loaded(
                        current.avatars.map { if (it.id == avatarId) updated else it }
                    )
                }
                _printMessages.tryEmit("已重命名")
            } catch (e: Exception) {
                app.settingsRepo.logDiagnostic("AVATAR", "重命名失败: ${e.message}")
                _printMessages.tryEmit("重命名失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    private fun toWorldEntity(dto: WorldDto, now: Long) = WorldEntity(
        id = dto.id,
        name = dto.name.ifBlank { dto.id },
        thumbnailUrl = dto.thumbnailImageUrl ?: dto.imageUrl,
        imageUrl = dto.imageUrl,
        authorName = dto.authorName,
        description = dto.description,
        updatedAt = now,
        capacity = dto.capacity,
        releaseStatus = dto.releaseStatus,
        tags = dto.tags?.takeIf { it.isNotEmpty() }?.joinToString("\u0001"),
    )

    private fun WorldEntity.toWorldDto(): WorldDto = WorldDto(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        thumbnailImageUrl = thumbnailUrl,
        authorName = authorName,
        capacity = capacity,
        releaseStatus = releaseStatus,
        tags = tags?.split('\u0001')?.filter { it.isNotEmpty() },
    )

    companion object {
        private const val MIN_POLL_MS = 5_000L
        private const val BACKOFF_MS = 30_000L
        private const val NOTICE_DEDUPE_MS = 90_000L
        private const val MAX_FAVORITES = 300
        private const val UNGROUPED_FAVORITE_KEY = "ungrouped"
    }
}
