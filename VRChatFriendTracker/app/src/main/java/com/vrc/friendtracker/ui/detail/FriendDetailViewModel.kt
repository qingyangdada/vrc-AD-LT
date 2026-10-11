package com.vrc.friendtracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.api.dto.InstanceDto
import com.vrc.friendtracker.data.api.dto.LimitedUserDto
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.HistoryEventEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.util.InstanceUtil
import com.vrc.friendtracker.util.TrustRank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendDetailViewModel(
    private val app: VrApp,
    private val friendId: String,
) : ViewModel() {

    val friend: StateFlow<FriendEntity?> =
        app.database.friendDao().observeById(friendId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<List<HistoryEventEntity>> =
        app.database.historyDao().observeForFriend(friendId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val worlds: StateFlow<Map<String, WorldEntity>> =
        app.database.worldDao().observeAll()
            .map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _instance = MutableStateFlow<InstanceDto?>(null)
    val instance: StateFlow<InstanceDto?> = _instance.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    sealed interface MutualState {
        data object Loading : MutualState
        data class Loaded(val friends: List<FriendEntity>) : MutualState
        data class Error(val message: String) : MutualState
    }

    private val _mutual = MutableStateFlow<MutualState>(MutualState.Loading)
    val mutual: StateFlow<MutualState> = _mutual.asStateFlow()

    init { loadMutualFriends() }

    /** Fetches mutual friends from the official mutual-friends endpoint and
     *  enriches each entry with locally cached profile data (trust rank,
     *  fallback avatar, current room) when available. */
    fun loadMutualFriends() {
        viewModelScope.launch {
            _mutual.value = MutualState.Loading
            val current = app.database.friendDao().getById(friendId)
            if (current == null) {
                _mutual.value = MutualState.Error("好友信息不存在")
                return@launch
            }
            try {
                val dtos = mutableListOf<LimitedUserDto>()
                var offset = 0
                while (true) {
                    val page = app.api.getMutualFriends(current.id, offset = offset, number = 100)
                    dtos += page
                    if (page.size < 100 || offset >= 900) break
                    offset += page.size
                }
                val mutual = dtos
                    .mapNotNull { dto ->
                        toDisplayFriend(dto, app.database.friendDao().getById(dto.id))
                    }
                    .sortedWith(compareByDescending<FriendEntity> { it.isOnline }
                        .thenBy { it.displayName.lowercase() })
                _mutual.value = MutualState.Loaded(mutual)
            } catch (e: Exception) {
                _mutual.value = MutualState.Error("获取失败：" + (e.message ?: "网络错误"))
            }
        }
    }

    /** Builds a display entity for a mutual-friend DTO, preferring locally
     *  cached profile data so trust rank and room info show up correctly. */
    private fun toDisplayFriend(dto: LimitedUserDto, existing: FriendEntity?): FriendEntity {
        val primary = dto.profilePicOverrideThumbnail?.takeIf { it.isNotBlank() }
            ?: dto.profilePicOverride?.takeIf { it.isNotBlank() }
            ?: dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
            ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
        val fallback = if (!dto.profilePicOverrideThumbnail.isNullOrBlank() || !dto.profilePicOverride.isNullOrBlank())
            dto.currentAvatarThumbnailImageUrl?.takeIf { it.isNotBlank() }
                ?: dto.currentAvatarImageUrl?.takeIf { it.isNotBlank() }
        else null
        return FriendEntity(
            id = dto.id,
            displayName = dto.displayName.ifBlank { existing?.displayName ?: dto.id },
            note = existing?.note,
            avatarUrl = primary ?: existing?.avatarUrl,
            avatarFallbackUrl = fallback ?: existing?.avatarFallbackUrl,
            bio = existing?.bio,
            bioLinksJson = existing?.bioLinksJson,
            pronouns = existing?.pronouns,
            tagsJson = existing?.tagsJson,
            trustRank = existing?.trustRank ?: TrustRank.VISITOR.name,
            isTroll = existing?.isTroll ?: false,
            isModerator = existing?.isModerator ?: false,
            status = dto.status ?: existing?.status,
            statusDescription = dto.statusDescription ?: existing?.statusDescription,
            location = existing?.location,
            worldId = existing?.worldId,
            instanceId = existing?.instanceId,
            isOnline = existing?.isOnline ?: false,
            lastLogin = existing?.lastLogin,
            lastActivity = existing?.lastActivity,
            lastPlatform = existing?.lastPlatform,
            tagsCheckedAt = existing?.tagsCheckedAt ?: 0L,
            profileCheckedAt = existing?.profileCheckedAt ?: 0L,
            lastSeenAt = existing?.lastSeenAt ?: 0L,
            updatedAt = existing?.updatedAt ?: 0L,
            pinned = existing?.pinned ?: false,
        )
    }

    /** Turns the per-friend background reminder on/off (bell icon). */
    fun setNotify(enabled: Boolean) {
        viewModelScope.launch { app.database.friendDao().updateNotify(friendId, enabled) }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                app.syncManager.refreshFriend(friendId)
                val current = app.database.friendDao().getById(friendId)
                val worldId = current?.worldId
                val instanceId = current?.instanceId
                if (current != null && worldId != null && instanceId != null) {
                    _instance.value = try {
                        app.api.getInstance("$worldId:$instanceId")
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    _instance.value = null
                }
            } finally {
                _refreshing.value = false
            }
        }
    }
}
