package com.vrc.friendtracker.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.vrc.friendtracker.VrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the "is there a newer build on GitHub" state for the whole app: the main
 * screen shows a prompt when a newer version exists, and the settings screen
 * shows the outcome of a manual check.
 */
class UpdateCenter(private val app: VrApp) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val versionName: String) : State
        data class Available(val info: UpdateInfo) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Non-null while the "new version available" dialog should be shown. */
    private val _prompt = MutableStateFlow<UpdateInfo?>(null)
    val prompt: StateFlow<UpdateInfo?> = _prompt.asStateFlow()

    private var lastSilentCheckAt = 0L

    val currentVersionName: String
    val currentVersionCode: Long

    init {
        var name = "?"
        var code = 0L
        runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            name = info.versionName ?: "?"
            code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }
        currentVersionName = name
        currentVersionCode = code
    }

    /** At most one silent check per hour; only surfaces a real new version. */
    fun checkSilently() {
        val now = System.currentTimeMillis()
        if (now - lastSilentCheckAt < SILENT_INTERVAL_MS) return
        lastSilentCheckAt = now
        scope.launch {
            val result = runCatching {
                UpdateChecker.check(currentVersionName, currentVersionCode)
            }.getOrNull()
            if (result is CheckResult.Newer) {
                _state.value = State.Available(result.info)
                if (result.info.versionName != app.settingsRepo.getIgnoredUpdateVersion()) {
                    _prompt.value = result.info
                }
            }
        }
    }

    /** Manual check from settings; always reports the outcome. */
    fun checkManually() {
        _state.value = State.Checking
        scope.launch {
            val result = runCatching {
                UpdateChecker.check(currentVersionName, currentVersionCode)
            }.getOrElse { CheckResult.Error(it.message ?: "网络错误") }
            when (result) {
                is CheckResult.Newer -> {
                    _state.value = State.Available(result.info)
                    _prompt.value = result.info
                }
                is CheckResult.UpToDate -> {
                    _state.value = State.UpToDate(currentVersionName)
                    _prompt.value = null
                }
                is CheckResult.Error -> _state.value = State.Failed(result.message)
            }
        }
    }

    /** Opens the release / APK download page in the browser. */
    fun openDownload(info: UpdateInfo) {
        val url = info.downloadUrl.ifBlank { info.pageUrl }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { app.startActivity(intent) }
    }

    /** "稍后": remember this version so the prompt stops nagging. */
    fun dismissPrompt() {
        _prompt.value?.let { app.settingsRepo.setIgnoredUpdateVersion(it.versionName) }
        _prompt.value = null
    }

    companion object {
        private const val SILENT_INTERVAL_MS = 60 * 60 * 1000L
    }
}
