package com.vrc.friendtracker.sync

import com.vrc.friendtracker.data.api.VrChatApiService
import com.vrc.friendtracker.data.api.dto.LimitedUserDto
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.db.FriendDao
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.HistoryDao
import com.vrc.friendtracker.data.db.HistoryEventEntity
import com.vrc.friendtracker.data.db.HistoryType
import com.vrc.friendtracker.data.db.WorldDao
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.data.repo.SettingsRepo
import com.vrc.friendtracker.data.security.TokenStore
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.TrustRank
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

sealed class SyncOutcome {
    data class Done(val friendCount: Int, val changeCount: Int) : SyncOutcome()
    object AuthExpired : SyncOutcome()
    data class Failed(val message: String) : SyncOutcome()
}

/**
 * Fetches the friend list from VRChat, diffs it against the local database and
 * records history events (bio / trust rank / status / room changes).
 *
 * The sync has two modes:
 *  - light: friends list only (fast polling, ~2 requests per run)
 *  - full: + session validation + world info + rotating full-profile refresh
 *    (the friends-list endpoint always returns empty tags, so trust ranks can
 *    only be derived from full profiles via GET /users/{id})
 */
class SyncManager(
    private val api: VrChatApiService,
    private val friendDao: FriendDao,
    private val historyDao: HistoryDao,
    private val worldDao: WorldDao,
    private val settingsRepo: SettingsRepo,
    private val tokenStore: TokenStore,
) {

    private val mutex = Mutex()

    suspend fun syncOnce(light: Boolean = false): SyncOutcome {
        if (tokenStore.authToken == null) {
            settingsRepo.logDiagnostic("SYNC", "无 auth token，判定会话失效")
            return SyncOutcome.AuthExpired
        }
        return mutex.withLock {
            try {
                val changeCount = performSync(light = light)
                val count = friendDao.count()
                settingsRepo.setLastSyncAt(System.currentTimeMillis())
                settingsRepo.setLastSyncError(null)
                SyncOutcome.Done(count, changeCount)
            } catch (e: HttpException) {
                val url = e.response()?.raw()?.request?.url?.toString() ?: "?"
                settingsRepo.logDiagnostic("SYNC", "HTTP ${e.code()} on $url")
                when (e.code()) {
                    401 -> {
                        settingsRepo.logDiagnostic("SYNC", "会话被接口拒绝 -> 需要重新登录")
                        SyncOutcome.AuthExpired
                    }
                    429 -> {
                        settingsRepo.setLastSyncError("接口限流：请求过于频繁，已自动暂停同步")
                        SyncOutcome.Failed("接口限流")
                    }
                    else -> {
                        settingsRepo.setLastSyncError("同步失败 (HTTP ${e.code()})")
                        SyncOutcome.Failed("同步失败 (HTTP ${e.code()})")
                    }
                }
            } catch (e: Exception) {
                settingsRepo.logDiagnostic("SYNC", "异常: ${e.message ?: e.javaClass.simpleName}")
                settingsRepo.setLastSyncError("网络错误：${e.message ?: "未知"}")
                SyncOutcome.Failed("网络错误：${e.message ?: "未知"}")
            }
        }
    }

    /** Refreshes a single friend's full profile (used from the detail screen). */
    suspend fun refreshFriend(friendId: String) {
        if (tokenStore.authToken == null) return
        mutex.withLock {
            try {
                val user = api.getUser(friendId)
                applySnapshot(Snapshot.fromFull(user), tagsAuthoritative = true)
                val parts = InstanceUtil.parseLocation(user.location)
                if (parts.worldId != null) {
                    val cached = worldDao.getById(parts.worldId)
                    if (cached == null) {
                        try {
                            val world = api.getWorld(parts.worldId)
                            worldDao.upsert(toWorldEntity(world, System.currentTimeMillis()))
                        } catch (_: HttpException) {
                            // world fetch failed; detail screen will show raw id
                        }
                    }
                }
            } catch (_: Exception) {
                // detail screen handles errors via its own state
            }
        }
    }

    private suspend fun performSync(light: Boolean): Int {
        var changes = 0
        if (!light) {
            // Validate the session early so an expired token is detected fast.
            api.getCurrentUser()
        }
        val online = fetchAllFriends(offline = false)
        val offline = fetchAllFriends(offline = true)
        for (dto in online) changes += applySnapshot(Snapshot.fromLimited(dto), tagsAuthoritative = false)
        for (dto in offline) changes += applySnapshot(Snapshot.fromLimited(dto), tagsAuthoritative = false)
        changes += refreshWorlds(limit = 5)
        if (!light) {
            changes += refreshProfiles(limit = 20)
            logAvatarStats()
            logInstanceStats()
        }
        return changes
    }

    private var lastInstanceStats: String? = null

    /** Logs the access-type distribution of online friends' instance ids once
     *  per change, so unknown instance formats are visible in diagnostics. */
    private suspend fun logInstanceStats() {
        val heads = friendDao.getOnline()
            .mapNotNull { it.instanceId }
            .map { id -> id.substringBefore('~').substringBefore('(').trim().lowercase().ifBlank { "?" } }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        val line = "INST: " + heads.entries.joinToString(", ") { "${it.key}x${it.value}" }
        if (line != lastInstanceStats) {
            lastInstanceStats = line
            settingsRepo.logDiagnostic("SYNC", line)
        }
    }

    private var lastAvatarStats: String? = null

    /** Logs how many friends have avatar URLs and which hosts they use, once per change. */
    private suspend fun logAvatarStats() {
        val all = friendDao.getAll()
        val withAvatar = all.count { !it.avatarUrl.isNullOrBlank() }
        val hosts = all.mapNotNull { it.avatarUrl }
            .mapNotNull { u -> runCatching { android.net.Uri.parse(u).host }.getOrNull() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(4)
            .joinToString(", ") { "${it.key}x${it.value}" }
        val line = "头像: $withAvatar/${all.size} 有地址 [$hosts]"
        if (line != lastAvatarStats) {
            lastAvatarStats = line
            settingsRepo.logDiagnostic("SYNC", line)
        }
    }

    private suspend fun fetchAllFriends(offline: Boolean): List<LimitedUserDto> {
        val all = mutableListOf<LimitedUserDto>()
        var offset = 0
        while (true) {
            val page = api.getFriends(offline = offline, offset = offset, number = 100)
            all += page
            if (page.size < 100 || offset >= 900) break
            offset += page.size
        }
        return all
    }

    private suspend fun refreshWorlds(limit: Int): Int {
        val online = friendDao.getOnline()
        val worldIds = online.mapNotNull { it.worldId }.distinct()
        if (worldIds.isEmpty()) return 0
        val cached = worldDao.getByIds(worldIds).map { it.id }.toSet()
        val missing = worldIds.filterNot { it in cached }.take(limit)
        var changes = 0
        val now = System.currentTimeMillis()
        for (id in missing) {
            try {
                val world = api.getWorld(id)
                worldDao.upsert(toWorldEntity(world, now))
                changes++
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // Cache a placeholder so we don't refetch a deleted/private world every cycle.
                    worldDao.upsert(
                        WorldEntity(
                            id = id, name = "未知世界", thumbnailUrl = null,
                            imageUrl = null, authorName = null, description = null,
                            updatedAt = now
                        )
                    )
                }
                // other HTTP errors: skip this world for now
            } catch (_: Exception) {
                // transient network error; will be retried next sync
            }
        }
        return changes
    }

    private val loggedNoAvatarIds: MutableSet<String> = java.util.Collections.synchronizedSet(java.util.HashSet())

    /** Fetches full profiles: friends still missing an avatar URL first (so
     *  avatars fill in fast), then the friends whose trust tags are oldest.
     *  A compact diagnostic line is logged while the backfill is running. */
    private suspend fun refreshProfiles(limit: Int): Int {
        val noAvatar = friendDao.getMissingAvatarProfiles(limit = limit)
        val stale = friendDao.getStaleForProfileCheck(limit = limit - noAvatar.size)
        val targets = (noAvatar + stale).distinctBy { it.id }
        var changes = 0
        var fetched = 0
        var withAvatar = 0
        for (friend in targets) {
            try {
                val user = api.getUser(friend.id)
                fetched++
                val snap = Snapshot.fromFull(user)
                if (!snap.avatarUrl.isNullOrBlank()) withAvatar++
                if (friend.avatarUrl.isNullOrBlank() && snap.avatarUrl.isNullOrBlank()) {
                    // The full profile really has no usable avatar URL; report
                    // it once per friend so the cause is visible in diagnostics.
                    if (loggedNoAvatarIds.add(friend.id)) {
                        settingsRepo.logDiagnostic("SYNC", "PROFILE: " + friend.displayName + " has no avatar URL in full profile")
                    }
                }
                changes += applySnapshot(snap, tagsAuthoritative = true)
            } catch (e: HttpException) {
                if (e.code() == 401) throw e
                if (e.code() == 404) {
                    // Friend no longer exists; remove locally.
                    friendDao.deleteById(friend.id)
                    historyDao.deleteForFriend(friend.id)
                }
                // Other statuses: skip this cycle, retry later.
            } catch (_: Exception) {
                // Transient error; retry next cycle.
            }
        }
        if (fetched > 0 && noAvatar.isNotEmpty()) {
            settingsRepo.logDiagnostic(
                "SYNC", "AVATAR backfill: fetched " + fetched + " profiles (" + withAvatar + " have avatar URL, " + noAvatar.size + " were missing)"
            )
        }
        return changes
    }

    private suspend fun applySnapshot(snap: Snapshot, tagsAuthoritative: Boolean): Int {
        val old = friendDao.getById(snap.id)
        val now = System.currentTimeMillis()
        val parts = InstanceUtil.parseLocation(snap.location)
        val isOnline = parts.isOnline

        // Tags are only meaningful when they come from a full profile.
        val newTags = if (tagsAuthoritative && !snap.tags.isNullOrEmpty()) snap.tags else null
        val rank = if (newTags != null) TrustRank.fromTags(newTags) else old?.trustRank?.let(::rankFromName) ?: TrustRank.VISITOR
        val tagsWereKnownBefore = old != null && old.tagsCheckedAt > 0L

        val entity = FriendEntity(
            id = snap.id,
            displayName = snap.displayName.ifBlank { old?.displayName ?: snap.id },
            avatarUrl = snap.avatarUrl ?: old?.avatarUrl,
            avatarFallbackUrl = snap.avatarFallbackUrl ?: old?.avatarFallbackUrl,
            bio = snap.bio ?: old?.bio,
            bioLinksJson = snap.bioLinks?.takeIf { it.isNotEmpty() }?.let(::encodeList) ?: old?.bioLinksJson,
            tagsJson = newTags?.let(::encodeList) ?: old?.tagsJson,
            trustRank = rank.name,
            isTroll = if (newTags != null) TrustRank.isTroll(newTags) else old?.isTroll ?: false,
            isModerator = if (newTags != null) TrustRank.isModerator(newTags, snap.developerType)
            else old?.isModerator ?: TrustRank.isModerator(null, snap.developerType),
            status = snap.status ?: old?.status,
            statusDescription = snap.statusDescription ?: old?.statusDescription,
            location = snap.location ?: old?.location,
            worldId = parts.worldId ?: old?.worldId,
            instanceId = parts.instanceId ?: old?.instanceId,
            isOnline = isOnline,
            lastLogin = snap.lastLogin ?: old?.lastLogin,
            lastActivity = snap.lastActivity ?: old?.lastActivity,
            lastPlatform = snap.lastPlatform ?: old?.lastPlatform,
            tagsCheckedAt = if (tagsAuthoritative) now else old?.tagsCheckedAt ?: 0L,
            lastSeenAt = now,
            updatedAt = now,
        )

        // Light polls must not rewrite rows that did not change; that would
        // re-emit the whole friend list every cycle and churn the UI while scrolling.
        if (!tagsAuthoritative && old != null && entity.sameVisibleAs(old)) return 0

        var changes = 0
        if (old != null) {
            val snapBio = snap.bio
            if (snapBio != null && old.bio != snapBio) {
                historyDao.insert(
                    HistoryEventEntity(
                        friendId = snap.id, type = HistoryType.BIO,
                        oldValue = old.bio, newValue = snapBio, timestamp = now
                    )
                )
                changes++
            }
            // Only record rank changes once real tags have been observed before
            // (avoids a fake "Visitor -> X" event on the first tag fetch).
            if (newTags != null && tagsWereKnownBefore && old.trustRank != rank.name) {
                historyDao.insert(
                    HistoryEventEntity(
                        friendId = snap.id, type = HistoryType.RANK,
                        oldValue = old.trustRank, newValue = rank.name, timestamp = now
                    )
                )
                changes++
            }
            if (old.isOnline != isOnline) {
                historyDao.insert(
                    HistoryEventEntity(
                        friendId = snap.id, type = HistoryType.STATUS,
                        oldValue = old.isOnline.toStatusZh(), newValue = isOnline.toStatusZh(), timestamp = now
                    )
                )
                changes++
            }
            if (old.isOnline && isOnline && old.worldId != parts.worldId) {
                historyDao.insert(
                    HistoryEventEntity(
                        friendId = snap.id, type = HistoryType.ROOM,
                        oldValue = old.worldId, newValue = parts.worldId, timestamp = now
                    )
                )
                changes++
            }
        }
        friendDao.upsert(entity)
        return changes
    }

    private fun toWorldEntity(dto: com.vrc.friendtracker.data.api.dto.WorldDto, now: Long) = WorldEntity(
        id = dto.id,
        name = dto.name.ifBlank { dto.id },
        thumbnailUrl = dto.thumbnailImageUrl ?: dto.imageUrl,
        imageUrl = dto.imageUrl,
        authorName = dto.authorName,
        description = dto.description,
        updatedAt = now,
    )

    private fun encodeList(items: List<String>): String =
        items.joinToString("\u0001")

    private fun FriendEntity.sameVisibleAs(other: FriendEntity): Boolean =
        id == other.id &&
        displayName == other.displayName &&
        avatarUrl == other.avatarUrl &&
        avatarFallbackUrl == other.avatarFallbackUrl &&
        bio == other.bio &&
        bioLinksJson == other.bioLinksJson &&
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
        lastActivity == other.lastActivity &&
        lastPlatform == other.lastPlatform

    private fun rankFromName(name: String): TrustRank =
        runCatching { TrustRank.valueOf(name) }.getOrDefault(TrustRank.VISITOR)

    private fun Boolean.toStatusZh(): String = if (this) "在线" else "离线"

    /** Normalized view of either the friends-list DTO or the full-profile DTO. */
    private data class Snapshot(
        val id: String,
        val displayName: String,
        val avatarUrl: String?,
        val avatarFallbackUrl: String?,
        val bio: String?,
        val bioLinks: List<String>?,
        val location: String?,
        val status: String?,
        val statusDescription: String?,
        val lastLogin: String?,
        val lastActivity: String?,
        val lastPlatform: String?,
        val tags: List<String>?,
        val developerType: String?,
    ) {
        companion object {
            fun fromLimited(dto: LimitedUserDto) = Snapshot(
                id = dto.id,
                displayName = dto.displayName,
                // VRC+ profile picture (profilePicOverride) is the primary
                // avatar; keep the model avatar (currentAvatar*) as fallback
                // for when the VRC+ picture cannot be loaded.
                avatarUrl = dto.profilePicOverrideThumbnail?.takeIf { it.isNotBlank() }
                    ?: dto.profilePicOverride?.takeIf { it.isNotBlank() }
                    ?: dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                    ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() },
                avatarFallbackUrl = if (!dto.profilePicOverrideThumbnail.isNullOrBlank() || !dto.profilePicOverride.isNullOrBlank())
                    dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                        ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
                else null,
                bio = dto.bio,
                bioLinks = dto.bioLinks,
                location = dto.location,
                status = dto.status,
                statusDescription = dto.statusDescription,
                lastLogin = dto.lastLogin,
                lastActivity = dto.lastActivity,
                lastPlatform = dto.lastPlatform,
                tags = dto.tags,
                developerType = dto.developerType,
            )

            fun fromFull(dto: UserDto) = Snapshot(
                id = dto.id,
                displayName = dto.displayName,
                // VRC+ profile picture (profilePicOverride) is the primary
                // avatar; keep the model avatar (currentAvatar*) as fallback
                // for when the VRC+ picture cannot be loaded.
                avatarUrl = dto.profilePicOverrideThumbnail?.takeIf { it.isNotBlank() }
                    ?: dto.profilePicOverride?.takeIf { it.isNotBlank() }
                    ?: dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                    ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() },
                avatarFallbackUrl = if (!dto.profilePicOverrideThumbnail.isNullOrBlank() || !dto.profilePicOverride.isNullOrBlank())
                    dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                        ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
                else null,
                bio = dto.bio,
                bioLinks = dto.bioLinks,
                location = dto.location,
                status = dto.status,
                statusDescription = dto.statusDescription,
                lastLogin = dto.lastLogin,
                lastActivity = dto.lastActivity,
                lastPlatform = dto.lastPlatform,
                tags = dto.tags,
                developerType = dto.developerType,
            )
        }
    }
}