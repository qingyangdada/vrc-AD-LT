package com.vrc.friendtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.ui.detail.FriendDetailViewModel
import com.vrc.friendtracker.ui.friends.FriendsViewModel
import com.vrc.friendtracker.ui.history.HistoryViewModel
import com.vrc.friendtracker.ui.login.LoginViewModel
import com.vrc.friendtracker.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val app: VrApp,
    private val friendId: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) -> LoginViewModel(app)
        modelClass.isAssignableFrom(FriendsViewModel::class.java) -> FriendsViewModel(app)
        modelClass.isAssignableFrom(FriendDetailViewModel::class.java) ->
            FriendDetailViewModel(app, friendId ?: error("friendId required"))
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app)
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(app)
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    } as T
}