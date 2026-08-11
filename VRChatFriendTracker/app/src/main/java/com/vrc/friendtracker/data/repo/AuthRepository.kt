package com.vrc.friendtracker.data.repo

import android.util.Base64
import com.vrc.friendtracker.data.api.Network
import com.vrc.friendtracker.data.api.VrChatApiService
import com.vrc.friendtracker.data.api.dto.ApiErrorBody
import com.vrc.friendtracker.data.api.dto.TwoFactorAuthCode
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.security.TokenStore
import retrofit2.HttpException
import java.net.URLEncoder

sealed class LoginResult {
    data class Success(val user: UserDto) : LoginResult()
    data class NeedsTwoFactor(val methods: List<String>) : LoginResult()
    data class Failed(val message: String) : LoginResult()
}

class AuthRepository(
    private val api: VrChatApiService,
    private val tokenStore: TokenStore,
    private val settingsRepo: SettingsRepo,
) {

    /** In-memory only; never persisted. Used to finish the login after 2FA. */
    @Volatile
    private var pendingBasicAuth: String? = null

    @Volatile
    private var pendingMethod: String? = null

    suspend fun login(username: String, password: String): LoginResult {
        val basic = buildBasicAuth(username, password)
        // Fresh attempt: drop any stale/pending session state.
        tokenStore.authToken = null
        tokenStore.pendingAuthToken = null
        tokenStore.twoFactorToken = null
        tokenStore.username = username
        settingsRepo.logDiagnostic("LOGIN", "尝试登录账号 $username")
        return try {
            val user = api.login(basic)
            // 2FA accounts get HTTP 200 with requiresTwoFactorAuth in the body.
            val methods = user.requiresTwoFactorAuth.orEmpty().filter { it in SUPPORTED_METHODS }
            if (methods.isNotEmpty()) {
                // The interceptor treats a 2xx login as a real session, so the challenge
                // cookie landed in authToken - move it to the pending slot used by verify.
                val pending = tokenStore.authToken
                tokenStore.authToken = null
                tokenStore.pendingAuthToken = pending
                pendingBasicAuth = basic
                pendingMethod = methods.first()
                settingsRepo.logDiagnostic("LOGIN", "需要两步验证(200 响应): $methods")
                return LoginResult.NeedsTwoFactor(methods)
            }
            pendingBasicAuth = null
            pendingMethod = null
            settingsRepo.setOwnDisplayName(user.displayName)
            settingsRepo.logDiagnostic("LOGIN", "登录成功, displayName=${user.displayName}")
            LoginResult.Success(user)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            settingsRepo.logDiagnostic("LOGIN", "HTTP ${e.code()} body=${body?.take(200)}")
            if (e.code() == 401) {
                val methods = parseRequiresTwoFactor(body)
                if (methods.isNotEmpty()) {
                    pendingBasicAuth = basic
                    pendingMethod = methods.firstOrNull { it in SUPPORTED_METHODS } ?: "totp"
                    settingsRepo.logDiagnostic("LOGIN", "需要两步验证: $methods")
                    return LoginResult.NeedsTwoFactor(methods)
                }
                LoginResult.Failed("用户名或密码错误")
            } else if (e.code() == 429) {
                LoginResult.Failed("请求过于频繁，请稍后再试")
            } else {
                LoginResult.Failed("登录失败 (HTTP ${e.code()})")
            }
        } catch (e: Exception) {
            settingsRepo.logDiagnostic("LOGIN", "异常: ${e.message ?: e.javaClass.simpleName}")
            LoginResult.Failed("网络错误：${e.message ?: "未知"}")
        }
    }

    /** Verifies a 2FA code, then finishes login by authenticating again with credentials. */
    suspend fun verifyTwoFactor(code: String, method: String? = null): LoginResult {
        val basic = pendingBasicAuth ?: return LoginResult.Failed("登录状态已失效，请重新输入账号密码")
        val effectiveMethod = method?.takeIf { it in SUPPORTED_METHODS } ?: pendingMethod ?: "totp"
        pendingMethod = effectiveMethod
        return try {
            val body = TwoFactorAuthCode(code.trim())
            when (effectiveMethod) {
                "otp" -> api.verifyOtp(body)
                "emailOtp" -> api.verifyEmailOtp(body)
                else -> api.verifyTotp(body)
            }
            settingsRepo.logDiagnostic("LOGIN", "2FA($effectiveMethod) 验证通过, 重新登录…")
            // 2FA verified: the twoFactorAuth cookie was captured by the interceptor.
            // Finish the login sequence with credentials + twoFactorAuth cookie.
            pendingBasicAuth = null
            pendingMethod = null
            finishLogin(basic)
        } catch (e: HttpException) {
            val errBody = e.response()?.errorBody()?.string()
            settingsRepo.logDiagnostic("LOGIN", "2FA($effectiveMethod) 验证 HTTP ${e.code()} body=${errBody?.take(160)}")
            if (e.code() == 401) {
                val msg = parseErrorMessage(errBody)
                if (msg.contains("Missing Credentials")) LoginResult.Failed("登录会话已失效，请返回重新输入账号密码")
                else if (msg.contains("Invalid")) LoginResult.Failed("验证码错误或已过期，请确认使用的验证方式")
                else LoginResult.Failed("验证码验证失败 (HTTP 401)")
            } else if (e.code() == 429) {
                LoginResult.Failed("请求过于频繁，请稍后再试")
            } else {
                LoginResult.Failed("验证失败 (HTTP ${e.code()})")
            }
        } catch (e: Exception) {
            LoginResult.Failed("网络错误：${e.message ?: "未知"}")
        }
    }

    suspend fun logout() {
        settingsRepo.logDiagnostic("AUTH", "退出登录")
        try {
            api.logout()
        } catch (_: Exception) {
            // Ignore network errors; the local session is cleared regardless.
        }
        tokenStore.clear()
        settingsRepo.clear()
        pendingBasicAuth = null
        pendingMethod = null
    }

    private suspend fun finishLogin(basic: String): LoginResult {
        return try {
            val user = api.login(basic)
            // VRChat only issues the auth cookie once during the handshake; the
            // challenge cookie from the first login becomes the real session cookie
            // after 2FA. If the server did not re-issue one, promote the pending one.
            if (tokenStore.authToken == null) {
                val pending = tokenStore.pendingAuthToken
                if (pending != null) {
                    tokenStore.authToken = pending
                    settingsRepo.logDiagnostic("LOGIN", "沿用 2FA 挑战 cookie 作为会话令牌")
                }
            }
            tokenStore.pendingAuthToken = null
            tokenStore.twoFactorToken = null
            settingsRepo.setOwnDisplayName(user.displayName)
            settingsRepo.logDiagnostic("LOGIN", "2FA 后登录成功, displayName=${user.displayName}")
            LoginResult.Success(user)
        } catch (e: HttpException) {
            settingsRepo.logDiagnostic("LOGIN", "2FA 后登录 HTTP ${e.code()}")
            if (e.code() == 401) LoginResult.Failed("登录失败，请重试")
            else LoginResult.Failed("登录失败 (HTTP ${e.code()})")
        } catch (e: Exception) {
            LoginResult.Failed("网络错误：${e.message ?: "未知"}")
        }
    }

    private fun parseErrorMessage(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return try {
            Network.json.decodeFromString<ApiErrorBody>(body).error?.message ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseRequiresTwoFactor(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            Network.json.decodeFromString<ApiErrorBody>(body).error?.requiresTwoFactorAuth ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildBasicAuth(username: String, password: String): String {
        val raw = "${URLEncoder.encode(username, "UTF-8")}:${URLEncoder.encode(password, "UTF-8")}"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    companion object {
        private val SUPPORTED_METHODS = setOf("totp", "otp", "emailOtp")
    }
}