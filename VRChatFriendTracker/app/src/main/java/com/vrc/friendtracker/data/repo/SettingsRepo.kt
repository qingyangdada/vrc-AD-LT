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

    /** Appearance: paint the friend's current world cover on friend cards. */
    private val _friendCoverEnabled = MutableStateFlow(prefs.getBoolean(KEY_FRIEND_COVER, true))
    val friendCoverEnabled: StateFlow<Boolean> = _friendCoverEnabled.asStateFlow()

    /** Master switch for the WeChat-style real-time monitor (foreground service). */
    private val _monitorEnabled = MutableStateFlow(prefs.getBoolean(KEY_MONITOR_ENABLED, false))
    val monitorEnabled: StateFlow<Boolean> = _monitorEnabled.asStateFlow()


    /** Highest history-event id already turned into notifications. */
    fun getNotifyCursor(): Long = prefs.getLong(KEY_NOTIFY_CURSOR, 0L)

    fun setNotifyCursor(value: Long) {
        prefs.edit().putLong(KEY_NOTIFY_CURSOR, value).apply()
    }

    /** Version the user chose to skip so the update prompt does not nag. */
    fun getIgnoredUpdateVersion(): String? = prefs.getString(KEY_IGNORED_UPDATE, null)

    fun setIgnoredUpdateVersion(value: String?) {
        if (value == null) prefs.edit().remove(KEY_IGNORED_UPDATE).apply()
        else prefs.edit().putString(KEY_IGNORED_UPDATE, value).apply()
    }

    fun setFriendCoverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FRIEND_COVER, enabled).apply()
        _friendCoverEnabled.value = enabled
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _darkMode.value = enabled
    }
    fun setMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITOR_ENABLED, enabled).apply()
        _monitorEnabled.value = enabled
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
        _friendCoverEnabled.value = true
        _monitorEnabled.value = false
        _lastSyncAt.value = 0L
        _lastSyncError.value = null
        _ownDisplayName.value = null
        _diagnostics.value = emptyList()
        _failedImageHosts.value = emptySet()
    }

    companion object {

        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_FRIEND_COVER = "friend_cover_enabled"
        const val KEY_MONITOR_ENABLED = "monitor_enabled"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        const val KEY_OWN_DISPLAY_NAME = "own_display_name"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_FAILED_IMAGE_HOSTS = "failed_image_hosts_v2"
        const val KEY_CRASH_INFO = "crash_info"
        const val KEY_NOTIFY_CURSOR = "notify_cursor"
        const val KEY_IGNORED_UPDATE = "ignored_update_version"

    }
}
