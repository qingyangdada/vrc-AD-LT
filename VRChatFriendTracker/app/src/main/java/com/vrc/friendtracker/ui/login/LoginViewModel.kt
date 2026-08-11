package com.vrc.friendtracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrc.friendtracker.VrApp
import com.vrc.friendtracker.data.repo.LoginResult
import com.vrc.friendtracker.sync.SyncOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val app: VrApp) : ViewModel() {

    data class UiState(
        val username: String = "",
        val password: String = "",
        val code: String = "",
        val loading: Boolean = false,
        val twoFactorRequired: Boolean = false,
        val twoFactorMethods: List<String> = emptyList(),
        val selectedMethod: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value, error = null) }
    fun onMethodChange(method: String) = _uiState.update { it.copy(selectedMethod = method, error = null) }

    fun login() {
        val s = _uiState.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入账号和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val result = app.authRepository.login(s.username.trim(), s.password)) {
                is LoginResult.Success -> verifySessionAndProceed()
                is LoginResult.NeedsTwoFactor -> _uiState.update {
                    it.copy(
                        loading = false,
                        twoFactorRequired = true,
                        twoFactorMethods = result.methods,
                        selectedMethod = defaultMethod(result.methods),
                        error = null,
                    )
                }
                is LoginResult.Failed -> _uiState.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun verifyCode() {
        val s = _uiState.value
        if (s.code.isBlank()) {
            _uiState.update { it.copy(error = "请输入验证码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            when (val result = app.authRepository.verifyTwoFactor(s.code, s.selectedMethod)) {
                is LoginResult.Success -> verifySessionAndProceed()
                is LoginResult.Failed -> _uiState.update {
                    val expired = result.message.contains("会话已失效")
                    it.copy(
                        loading = false,
                        error = result.message,
                        twoFactorRequired = it.twoFactorRequired && !expired,
                        selectedMethod = if (expired) null else it.selectedMethod,
                        code = if (expired) "" else it.code,
                    )
                }
                is LoginResult.NeedsTwoFactor -> _uiState.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * After a successful login, immediately probe the API with the new token.
     * Only activate the session (and navigate) when the token actually works,
     * so a bad session shows an error here instead of bouncing back to login.
     */
    private suspend fun verifySessionAndProceed() {
        when (val check = app.syncManager.syncOnce(light = true)) {
            SyncOutcome.AuthExpired -> {
                val http = com.vrc.friendtracker.data.api.ApiDiagnostics.lines.value.takeLast(8)
                val appLog = app.settingsRepo.diagnostics.value.takeLast(8)
                val diag = (http + appLog).distinct().joinToString("\n")
                app.onLogout()
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "登录成功但会话立即被接口拒绝。诊断信息：\n$diag",
                    )
                }
            }
            else -> {
                app.onLoginSuccess()
                _uiState.update { it.copy(loading = false, error = null) }
            }
        }
    }

    /** Official order (matches VRChat client / VRCX): email OTP first, then TOTP, then recovery. */
    private fun defaultMethod(methods: List<String>): String? = when {
        "emailOtp" in methods -> "emailOtp"
        "totp" in methods -> "totp"
        "otp" in methods -> "otp"
        else -> methods.firstOrNull()
    }
}