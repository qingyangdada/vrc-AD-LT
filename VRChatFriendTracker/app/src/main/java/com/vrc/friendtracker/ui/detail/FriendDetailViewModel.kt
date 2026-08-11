package com.vrc.friendtracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.api.dto.InstanceDto
import com.vrc.friendtracker.data.db.FriendEntity
import com.vrc.friendtracker.data.db.HistoryEventEntity
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.util.InstanceUtil
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