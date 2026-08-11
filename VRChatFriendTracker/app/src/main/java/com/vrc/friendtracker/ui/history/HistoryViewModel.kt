package com.vrc.friendtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.db.WorldEntity
import com.vrc.friendtracker.util.formatChange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(private val app: VrApp) : ViewModel() {

    private val db = app.database

    val friends = db.friendDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val worlds: StateFlow<Map<String, WorldEntity>> =
        db.worldDao().observeAll()
            .map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    data class Record(
        val id: Long,
        val friendName: String,
        val avatarUrl: String?,
        val text: String,
        val timestamp: Long,
    )

    /** All history events, newest first, joined with friend and world names. */
    val records: StateFlow<List<Record>> =
        combine(db.historyDao().observeAll(), friends, worlds) { events, friendList, worldMap ->
            val friendsById = friendList.associateBy { it.id }
            events.mapNotNull { e ->
                val friend = friendsById[e.friendId] ?: return@mapNotNull null
                Record(
                    id = e.id,
                    friendName = friend.displayName,
                    avatarUrl = friend.avatarUrl,
                    text = formatChange(e, worldMap),
                    timestamp = e.timestamp,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}