package com.vrc.friendtracker.sync

import com.vrc.friendtracker.data.api.VrChatApiService
import com.vrc.friendtracker.data.api.dto.LimitedUserDto
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.api.dto.UserNoteDto
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
    /** Short in-memory cooldown per friend/transition so VRChat presence
     *  flapping (offline/online/room toggling within seconds) cannot produce
     *  a burst of duplicate history events and duplicate alerts. */
    private val flapCooldowns = HashMap<String, Long>()
    private val EVENT_FLAP_MS = 60_000L

    /** Consecutive light polls a still-"online" friend was missing from the
     *  online list. Two misses are required before flipping them offline so a
     *  single partial/flaky response cannot produce a false "went offline". */
    private val missingOnlineStreak = HashMap<String, Int>()
    private val MISSING_OFFLINE_STRIKES = 2

    /** The 5s poll stays cheap (friends list only), but every
     *  [PROFILE_BACKFILL_EVERY_TICKS] cycles a few profiles (2 requests each)
     *  are fetched in the background, so missing avatars, bios and pronouns of
     *  the whole friend list fill in without a manual refresh. */
    private val PROFILE_BACKFILL_EVERY_TICKS = 12
    private val PROFILE_BACKFILL_BATCH = 5
    private var lightTicks = 0

    suspend fun syncOnce(light: Boolean = false, forceOffline: Boolean = false): SyncOutcome {
        if (tokenStore.authToken == null) {
            settingsRepo.logDiagnostic("SYNC", "无 auth token，判定会话失效")
            return SyncOutcome.AuthExpired
        }
        return runHandled { performSync(light = light, forceOffline = forceOffline) }
    }

    /** Cheap tick for the real-time monitor service: online-friends list plus
     *  a profile confirm for reminder-enabled friends that just left the list
     *  (so their "went offline" change is recorded immediately). */
    suspend fun syncMonitorTick(): SyncOutcome {
        if (tokenStore.authToken == null) {
            settingsRepo.logDiagnostic("SYNC", "无 auth token，判定会话失效")
            return SyncOutcome.AuthExpired
        }
        return runHandled {
            val online = fetchAllFriends(offline = false)
            val onlineIds = HashSet<String>()
            for (dto in online) {
                onlineIds.add(dto.id)
                applySnapshot(Snapshot.fromLimited(dto), tagsAuthoritative = false)
            }
            val watched = friendDao.getWatched()
            for (friend in watched) {
                if (friend.isOnline && friend.id !in onlineIds) {
                    try {
                        fetchAndApplyProfile(friend.id)
                    } catch (e: HttpException) {
                        if (e.code() == 401) throw e
                    } catch (_: Exception) {
                        // transient error; retried on the next tick
                    }
                }
            }
            // Keep world names fresh so background alerts show readable room
            // names instead of raw world/instance ids (no-op when cached).
            refreshWorlds(limit = 5)
            0
        }
    }

    /** Shared result handling for every sync flavour. */
    private suspend fun runHandled(block: suspend () -> Int): SyncOutcome =
        mutex.withLock {
            try {
                block()
                val count = friendDao.count()
                settingsRepo.setLastSyncAt(System.currentTimeMillis())
                settingsRepo.setLastSyncError(null)
                SyncOutcome.Done(count, 0)
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

    /** Refreshes a single friend's full profile (used from the detail screen). */
    suspend fun refreshFriend(friendId: String) {
        if (tokenStore.authToken == null) {
            settingsRepo.logDiagnostic("SYNC", "PROFILE: 会话失效，无法刷新好友资料")
            return
        }
        mutex.withLock {
            try {
                fetchAndApplyProfile(friendId)
            } catch (e: HttpException) {
                if (e.code() == 401) throw e
                settingsRepo.logDiagnostic("SYNC", "PROFILE: 刷新好友资料失败 HTTP " + e.code())
            } catch (e: Exception) {
                // Logged instead of swallowed so "刷新没用" is diagnosable.
                settingsRepo.logDiagnostic("SYNC", "PROFILE: 刷新好友资料失败 " + (e.message ?: e.javaClass.simpleName))
            }
        }
        val refreshed = friendDao.getById(friendId)
        val refreshedBio = refreshed?.bio
        val refreshedNote = refreshed?.note
        val bioState = if (refreshedBio.isNullOrBlank()) "为空" else refreshedBio.length.toString() + " 字"
        val noteState = if (refreshedNote.isNullOrBlank()) "" else "，备注 " + refreshedNote.length.toString() + " 字"
        settingsRepo.logDiagnostic(
            "SYNC",
            "PROFILE: " + (refreshed?.displayName ?: friendId) + " 刷新完成，简介 " + bioState + noteState,
        )
    }

    /**
     * VRChat keeps the bio on the public profile endpoint (/profile/{id}); the
     * user object returned by /users/{id} carries no bio field at all. Every
     * full-profile refresh therefore asks for the profile as well, and a failed
     * lookup simply keeps the cached bio.
     */
    private suspend fun bioSnapshot(user: UserDto): Snapshot {
        val base = Snapshot.fromFull(user)
        val profile = try {
            api.getProfile(user.id)
        } catch (e: HttpException) {
            if (e.code() == 401) throw e
            logProfileFailure(user, "HTTP " + e.code())
            null
        } catch (e: Exception) {
            logProfileFailure(user, e.javaClass.simpleName)
            null
        } ?: return base
        profileFailLogged.remove(user.id)
        return base.copy(
            // VRChat answers /profile with an empty bio for some accounts; an
            // empty string there must never wipe the bio /users/{id} returned,
            // otherwise those friends keep showing "（无简介）" forever.
            bio = profile.bio?.trim()?.takeIf { it.isNotEmpty() } ?: base.bio,
            bioLinks = profile.bioLinks?.takeIf { it.isNotEmpty() } ?: base.bioLinks,
            pronouns = profile.pronouns?.takeIf { it.isNotBlank() } ?: base.pronouns,
            // Only this source may also clear a cached bio (an empty bio there
            // means the friend really has none).
            fromProfile = true,
        )
    }

    /** Remembers the last profile failure per friend so a broken 简介 shows up
     *  once in 诊断信息 instead of on every cycle. */
    private val profileFailLogged = HashMap<String, String>()

    private fun logProfileFailure(user: UserDto, reason: String) {
        if (profileFailLogged[user.id] == reason) return
        profileFailLogged[user.id] = reason
        settingsRepo.logDiagnostic("SYNC", "PROFILE: " + user.displayName + " 简介接口失败 " + reason)
    }

    /** Full-profile fetch for one friend, plus world-name cache fill. */
    private suspend fun fetchAndApplyProfile(friendId: String) {
        val user = api.getUser(friendId)
        applySnapshot(bioSnapshot(user), tagsAuthoritative = true)
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
    }

    /** Keeps reminder-enabled friends fresh on every full sync, so their
     *  online/offline/room changes are detected even after they leave the
     *  friends-online list. */
    private suspend fun refreshWatchedFriends() {
        val watched = friendDao.getWatched()
        if (watched.isEmpty()) return
        for (friend in watched) {
            try {
                fetchAndApplyProfile(friend.id)
            } catch (e: HttpException) {
                if (e.code() == 401) throw e
            } catch (_: Exception) {
                // transient error; retried next cycle
            }
        }
    }

    private suspend fun performSync(light: Boolean, forceOffline: Boolean): Int {
        var changes = 0
        if (!light) {
            // Validate the session early so an expired token is detected fast.
            api.getCurrentUser()
        }
        // Online friends stay real-time. Offline friends are only refreshed on
        // a manual refresh (forceOffline), keeping the auto poll loop lean and
        // the list free of churn.
        val online = fetchAllFriends(offline = false)
        val onlineIds = online.mapTo(HashSet()) { it.id }
        for (dto in online) changes += applySnapshot(Snapshot.fromLimited(dto), tagsAuthoritative = false)
        if (forceOffline) {
            val offline = fetchAllFriends(offline = true)
            for (dto in offline) changes += applySnapshot(Snapshot.fromLimited(dto), tagsAuthoritative = false)
        } else {
            // Absence from the online list is the offline signal. Updating it
            // locally keeps online/offline accurate at the 5s poll cadence
            // without pulling the (much heavier) offline list every cycle.
            changes += markMissingOffline(onlineIds)
        }
        changes += refreshWorlds(limit = 5)
        if (!light) {
            // Manual refresh: a bigger budget so the visible profile details
            // (avatar / trust rank / bio) fill in with far fewer refreshes.
            changes += refreshProfiles(limit = 40)
            refreshWatchedFriends()
            syncUserNotes()
            logAvatarStats()
            logInstanceStats()
        } else {
            lightTicks++
            if (lightTicks % PROFILE_BACKFILL_EVERY_TICKS == 0) {
                changes += refreshProfiles(limit = PROFILE_BACKFILL_BATCH)
            }
        }
        return changes
    }

    /** Flips friends we still store as online to offline once they have been
     *  absent from the online list for [MISSING_OFFLINE_STRIKES] cycles. */
    private suspend fun markMissingOffline(onlineIds: Set<String>): Int {
        var changes = 0
        for (friend in friendDao.getOnline()) {
            if (friend.id in onlineIds) {
                missingOnlineStreak.remove(friend.id)
                continue
            }
            val streak = (missingOnlineStreak[friend.id] ?: 0) + 1
            if (streak < MISSING_OFFLINE_STRIKES) {
                missingOnlineStreak[friend.id] = streak
                continue
            }
            missingOnlineStreak.remove(friend.id)
            changes += applySnapshot(offlineSnapshot(friend), tagsAuthoritative = false)
        }
        return changes
    }

    /** Synthetic snapshot meaning "this friend is no longer online"; keeps all
     *  other cached fields (name, avatar, bio) untouched. */
    private fun offlineSnapshot(friend: FriendEntity) = Snapshot(
        id = friend.id,
        displayName = friend.displayName,
        note = null,
        avatarUrl = null,
        avatarFallbackUrl = null,
        bio = null,
        bioLinks = null,
        location = "offline",
        status = null,
        statusDescription = null,
        lastLogin = null,
        lastActivity = null,
        lastPlatform = null,
        tags = null,
        developerType = null,
    )

    /** Pulls the logged-in user's private notes (备注) about their friends and
     *  writes them onto the matching friend rows. A note that disappeared on
     *  VRChat's side clears the local value. Failures (except an expired
     *  session) are logged and ignored so they never stall the main sync. */
    private suspend fun syncUserNotes() {
        try {
            val notes = mutableListOf<UserNoteDto>()
            var offset = 0
            while (true) {
                val page = api.getUserNotes(offset = offset, number = 100)
                notes += page
                if (page.size < 100 || offset >= 900) break
                offset += page.size
            }
            val byTarget = notes
                .mapNotNull { n -> n.targetUserId?.let { id -> id to n.note?.trim()?.takeIf { it.isNotEmpty() } } }
                .toMap()
            var updated = 0
            for (friend in friendDao.getAll()) {
                val newNote = byTarget[friend.id]
                if (friend.note != newNote) {
                    friendDao.updateNote(friend.id, newNote)
                    updated++
                }
            }
            if (byTarget.size != lastNoteCount || updated > 0) {
                lastNoteCount = byTarget.size
                settingsRepo.logDiagnostic("SYNC", "NOTES: " + byTarget.size + " 条备注, 更新 " + updated)
            }
        } catch (e: HttpException) {
            // Notes are optional decoration: even a 401 must never be treated
            // as a session failure (that would kick the user to the login
            // screen on every sync for an endpoint VRChat may not expose).
            val errBody = try { e.response()?.errorBody()?.string()?.take(200) } catch (_: Exception) { null }
            settingsRepo.logDiagnostic("SYNC", "NOTES: HTTP " + e.code() + (errBody?.takeIf { it.isNotBlank() }?.let { " body=" + it } ?: ""))
        } catch (e: Exception) {
            settingsRepo.logDiagnostic("SYNC", "NOTES: " + (e.message ?: e.javaClass.simpleName))
        }
    }
    private var lastNoteCount = -1
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
        val rest = (limit - noAvatar.size).coerceAtLeast(0)
        val never = if (rest > 0) friendDao.getNeverProfiled(limit = rest) else emptyList()
        var staleBudget = rest - never.size
        // One slot per cycle is reserved for friends whose bio is still empty
        // so a bio that came back as an empty string is retried soon, without
        // starving the trust-tag rotation of the remaining budget.
        val bioBudget = if (staleBudget >= 2) 1 else 0
        staleBudget -= bioBudget
        val blankBio = if (bioBudget > 0) friendDao.getMissingBioProfiles(limit = bioBudget) else emptyList()
        val stale = if (staleBudget > 0) friendDao.getStaleForProfileCheck(limit = staleBudget) else emptyList()
        val targets = (noAvatar + never + blankBio + stale).distinctBy { it.id }
        var changes = 0
        var fetched = 0
        var withAvatar = 0
        for (friend in targets) {
            try {
                val user = api.getUser(friend.id)
                fetched++
                val snap = bioSnapshot(user)
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
        // Location is authoritative: "private"/"traveling"/"local" must CLEAR
        // the cached world id instead of keeping the stale one, otherwise every
        // poll looks like another room change and repeats the same notification.
        val hasLocation = snap.location != null
        val isOnline = if (hasLocation) parts.isOnline else old?.isOnline ?: false
        val newWorldId = if (hasLocation) parts.worldId else old?.worldId
        val newInstanceId = if (hasLocation) parts.instanceId else old?.instanceId

        // Tags are only meaningful when they come from a full profile.
        val newTags = if (tagsAuthoritative && !snap.tags.isNullOrEmpty()) snap.tags else null
        val rank = if (newTags != null) TrustRank.fromTags(newTags) else old?.trustRank?.let(::rankFromName) ?: TrustRank.VISITOR
        val tagsWereKnownBefore = old != null && old.tagsCheckedAt > 0L

        val entity = FriendEntity(
            id = snap.id,
            displayName = snap.displayName.ifBlank { old?.displayName ?: snap.id },
            note = snap.note?.takeIf { it.isNotBlank() } ?: old?.note,
            avatarUrl = snap.avatarUrl ?: old?.avatarUrl,
            avatarFallbackUrl = snap.avatarFallbackUrl ?: old?.avatarFallbackUrl,
            // The lightweight friends list cannot see the real bio; a blank
            // value from it must never wipe the text fetched from the profile.
            bio = if (snap.fromProfile) snap.bio
            else snap.bio?.takeIf { it.isNotBlank() } ?: old?.bio,
            bioLinksJson = snap.bioLinks?.takeIf { it.isNotEmpty() }?.let(::encodeList) ?: old?.bioLinksJson,
            pronouns = snap.pronouns?.takeIf { it.isNotBlank() } ?: old?.pronouns,
            tagsJson = newTags?.let(::encodeList) ?: old?.tagsJson,
            trustRank = rank.name,
            isTroll = if (newTags != null) TrustRank.isTroll(newTags) else old?.isTroll ?: false,
            isModerator = if (newTags != null) TrustRank.isModerator(newTags, snap.developerType)
            else old?.isModerator ?: TrustRank.isModerator(null, snap.developerType),
            status = snap.status ?: old?.status,
            statusDescription = snap.statusDescription ?: old?.statusDescription,
            location = snap.location ?: old?.location,
            worldId = newWorldId,
            instanceId = newInstanceId,
            isOnline = isOnline,
            lastLogin = snap.lastLogin ?: old?.lastLogin,
            lastActivity = snap.lastActivity ?: old?.lastActivity,
            lastPlatform = snap.lastPlatform ?: old?.lastPlatform,
            tagsCheckedAt = if (tagsAuthoritative) now else old?.tagsCheckedAt ?: 0L,
            profileCheckedAt = if (snap.fromProfile) now else old?.profileCheckedAt ?: 0L,
            lastSeenAt = now,
            updatedAt = now,
            // Pin state is user preference; never reset it during sync.
            pinned = old?.pinned ?: false,
            // Per-friend reminder flag is also a user preference; keep it.
            notify = old?.notify ?: false,
        )

        // Light polls must not rewrite rows that did not change; that would
        // re-emit the whole friend list every cycle and churn the UI while scrolling.
        if (!tagsAuthoritative && old != null && entity.sameVisibleAs(old)) return 0

        var changes = 0
        if (old != null) {
            val snapBio = snap.bio
            if (snapBio != null && old.bio != snapBio) {
                if (recordEvent(snap.id, HistoryType.BIO, old.bio, snapBio, now)) changes++
            }
            // Only record rank changes once real tags have been observed before
            // (avoids a fake "Visitor -> X" event on the first tag fetch).
            if (newTags != null && tagsWereKnownBefore && old.trustRank != rank.name) {
                if (recordEvent(snap.id, HistoryType.RANK, old.trustRank, rank.name, now)) changes++
            }
            if (old.isOnline != isOnline) {
                if (recordEvent(snap.id, HistoryType.STATUS, old.isOnline.toStatusZh(), isOnline.toStatusZh(), now)) changes++
            }
            if (old.isOnline && isOnline && old.worldId != newWorldId) {
                if (recordEvent(snap.id, HistoryType.ROOM, old.worldId, newWorldId, now)) changes++
            }
        }
        friendDao.upsert(entity)
        return changes
    }

    /** Inserts a history event unless the same transition (either direction)
     *  for the same friend was recorded within the flap window. */
    private suspend fun recordEvent(
        friendId: String,
        type: String,
        oldValue: String?,
        newValue: String?,
        now: Long,
    ): Boolean {
        // Never store the same transition twice in a row: if the latest event
        // for this friend/type has identical values it is the same change being
        // re-detected (e.g. a stale comparison), not a new one.
        val latest = historyDao.getLatestFor(friendId, type)
        if (latest != null && latest.oldValue == oldValue && latest.newValue == newValue) return false
        val key = friendId + "|" + type + "|" +
            listOf(oldValue.orEmpty(), newValue.orEmpty()).sorted().joinToString("|")
        val last = flapCooldowns[key] ?: 0L
        if (now - last < EVENT_FLAP_MS) return false
        flapCooldowns[key] = now
        historyDao.insert(
            HistoryEventEntity(
                friendId = friendId, type = type,
                oldValue = oldValue, newValue = newValue, timestamp = now
            )
        )
        return true
    }

    private fun toWorldEntity(dto: com.vrc.friendtracker.data.api.dto.WorldDto, now: Long) = WorldEntity(
        id = dto.id,
        name = dto.name.ifBlank { dto.id },
        thumbnailUrl = dto.thumbnailImageUrl ?: dto.imageUrl,
        imageUrl = dto.imageUrl,
        authorName = dto.authorName,
        description = dto.description,
        updatedAt = now,
        capacity = dto.capacity,
        releaseStatus = dto.releaseStatus,
        tags = dto.tags?.takeIf { it.isNotEmpty() }?.let(::encodeList),
    )

    private fun encodeList(items: List<String>): String =
        items.joinToString("\u0001")

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
        lastActivity == other.lastActivity &&
        lastPlatform == other.lastPlatform

    private fun rankFromName(name: String): TrustRank =
        runCatching { TrustRank.valueOf(name) }.getOrDefault(TrustRank.VISITOR)

    private fun Boolean.toStatusZh(): String = if (this) "在线" else "离线"

    /** Normalized view of either the friends-list DTO or the full-profile DTO. */
    private data class Snapshot(
        val id: String,
        val displayName: String,
        val note: String?,
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
        val pronouns: String? = null,
        /** True when this snapshot came from a public-profile fetch, the only
         *  source that can also clear a cached bio. */
        val fromProfile: Boolean = false,
    ) {
        companion object {
            fun fromLimited(dto: LimitedUserDto) = Snapshot(
                id = dto.id,
                displayName = dto.displayName,
                note = dto.note?.takeIf { it.isNotBlank() },
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
                pronouns = dto.pronouns,
            )

            fun fromFull(dto: UserDto) = Snapshot(
                id = dto.id,
                displayName = dto.displayName,
                note = dto.note?.takeIf { it.isNotBlank() },
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
                pronouns = dto.pronouns,
            )
        }
    }
}
