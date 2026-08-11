package com.vrc.friendtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: VrApp) : ViewModel() {

    val username: String? = app.tokenStore.username
    val displayName: StateFlow<String?> = app.settingsRepo.ownDisplayName

    val darkMode: StateFlow<Boolean> = app.settingsRepo.darkMode
    val lastSyncAt: StateFlow<Long> = app.settingsRepo.lastSyncAt
    val diagnostics: StateFlow<List<String>> = app.settingsRepo.diagnostics



    fun setDarkMode(enabled: Boolean) = app.settingsRepo.setDarkMode(enabled)
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