package com.vrc.friendtracker.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App settings stored in SharedPreferences with in-memory StateFlow mirrors. */
class SettingsRepo(context: Context) {

    private val prefs = context.getSharedPreferences("vrc_settings", Context.MODE_PRIVATE)


    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()
    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_AT, 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _lastSyncError = MutableStateFlow(prefs.getString(KEY_LAST_SYNC_ERROR, null))
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _ownDisplayName = MutableStateFlow(prefs.getString(KEY_OWN_DISPLAY_NAME, null))
    val ownDisplayName: StateFlow<String?> = _ownDisplayName.asStateFlow()

    private val _diagnostics = MutableStateFlow(
        prefs.getString(KEY_DIAGNOSTICS, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    /** Image hosts that failed to load before (blocked network). Persisted so
     *  we skip retrying them on every app launch / list scroll. */
    private val _failedImageHosts = MutableStateFlow(
        prefs.getStringSet(KEY_FAILED_IMAGE_HOSTS, emptySet())?.toSet() ?: emptySet()
    )
    val failedImageHosts: StateFlow<Set<String>> = _failedImageHosts.asStateFlow()


    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _darkMode.value = enabled
    }
    fun setLastSyncAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()
        _lastSyncAt.value = value
    }

    fun setLastSyncError(value: String?) {
        prefs.edit().putString(KEY_LAST_SYNC_ERROR, value).apply()
        _lastSyncError.value = value
    }

    fun setOwnDisplayName(value: String?) {
        prefs.edit().putString(KEY_OWN_DISPLAY_NAME, value).apply()
        _ownDisplayName.value = value
    }

    /** Appends a line to the in-memory diagnostics ring buffer (last 60 lines). */
    fun logDiagnostic(tag: String, message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$stamp] $tag: $message"
        val updated = (_diagnostics.value + line).takeLast(60)
        _diagnostics.value = updated
        prefs.edit().putString(KEY_DIAGNOSTICS, updated.joinToString("\n")).apply()
    }

    /** Crash stack trace saved by the global exception handler (if any). */
    fun getCrashInfo(): String? = prefs.getString(KEY_CRASH_INFO, null)

    fun clearCrashInfo() {
        prefs.edit().remove(KEY_CRASH_INFO).apply()
    }
    fun clearDiagnostics() {
        prefs.edit().remove(KEY_DIAGNOSTICS).apply()
        _diagnostics.value = emptyList()
    }

    fun addFailedImageHost(host: String) {
        if (host.isBlank()) return
        val updated = _failedImageHosts.value + host
        _failedImageHosts.value = updated
        prefs.edit().putStringSet(KEY_FAILED_IMAGE_HOSTS, updated).apply()
    }

    fun clearFailedImageHosts() {
        prefs.edit().remove(KEY_FAILED_IMAGE_HOSTS).apply()
        _failedImageHosts.value = emptySet()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _darkMode.value = false
        _lastSyncAt.value = 0L
        _lastSyncError.value = null
        _ownDisplayName.value = null
        _diagnostics.value = emptyList()
        _failedImageHosts.value = emptySet()
    }

    companion object {

        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        const val KEY_OWN_DISPLAY_NAME = "own_display_name"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_FAILED_IMAGE_HOSTS = "failed_image_hosts_v2"
        const val KEY_CRASH_INFO = "crash_info"

    }
}