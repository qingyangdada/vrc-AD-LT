package com.vrc.friendtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: VrApp) : ViewModel() {

    val username: String? = app.tokenStore.username
    val displayName: StateFlow<String?> = app.settingsRepo.ownDisplayName

    val darkMode: StateFlow<Boolean> = app.settingsRepo.darkMode
    val monitorEnabled: StateFlow<Boolean> = app.settingsRepo.monitorEnabled
    val lastSyncAt: StateFlow<Long> = app.settingsRepo.lastSyncAt
    val diagnostics: StateFlow<List<String>> = app.settingsRepo.diagnostics

    private val _watchedFriendCount = MutableStateFlow(0)
    val watchedFriendCount: StateFlow<Int> = _watchedFriendCount.asStateFlow()

    init {
        refreshWatchedCount()
    }

    /** Number of friends with the per-friend bell reminder enabled. */
    fun refreshWatchedCount() {
        viewModelScope.launch {
            _watchedFriendCount.value = app.database.friendDao().getWatched().size
        }
    }

    fun setDarkMode(enabled: Boolean) = app.settingsRepo.setDarkMode(enabled)

    /** Master switch for the real-time monitor foreground service. */
    fun setMonitorEnabled(enabled: Boolean): Boolean {
        app.settingsRepo.setMonitorEnabled(enabled)
        val started = if (enabled) app.startMonitorService() else {
            app.stopMonitorService()
            true
        }
        refreshWatchedCount()
        return started
    }
    fun clearDiagnostics() = app.settingsRepo.clearDiagnostics()

    fun clearFailedImageHosts() = app.settingsRepo.clearFailedImageHosts()

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            app.authRepository.logout()
            app.onLogout()
            onDone()
        }
    }
}
