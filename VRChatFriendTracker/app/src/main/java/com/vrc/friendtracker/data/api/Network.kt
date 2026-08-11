package com.vrc.friendtracker.data.api

import com.vrc.friendtracker.data.security.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {
    const val BASE_URL = "https://api.vrchat.cloud/api/1/"
    const val USER_AGENT = "VRCFriendTracker/1.3.1 (personal friend tracker)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun createApi(tokenStore: TokenStore): VrChatApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val isLoginAttempt = original.header("Authorization") != null
                val isVerify2fa = original.url.encodedPath.contains("twofactorauth/")
                val request = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .apply {
                        // Login/verify use the pending 2FA cookie; regular calls use the real session.
                        val authCookie = when {
                            isLoginAttempt || isVerify2fa -> tokenStore.pendingAuthToken
                            else -> tokenStore.authToken
                        }
                        val cookies = buildList {
                            if (authCookie != null) add("auth=$authCookie")
                            val twoFactor = tokenStore.twoFactorToken
                            if (twoFactor != null && (isLoginAttempt || isVerify2fa)) add("twoFactorAuth=$twoFactor")
                        }
                        if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString("; "))
                    }
                    .build()
                val response = chain.proceed(request)
                captureCookies(response, tokenStore, request)
                logResponse(request, response)
                response
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()
            .create(VrChatApiService::class.java)
    }

    /**
     * Persists auth / twoFactorAuth cookies received via Set-Cookie headers.
     * Cookies are captured from every response on purpose: the 2FA-challenge
     * response carries the "pending" auth cookie needed to verify the code.
     */
    private fun captureCookies(response: Response, tokenStore: TokenStore, request: Request) {
        try {
            val isLoginAttempt = request.header("Authorization") != null
            for (cookie in response.headers("Set-Cookie")) {
                val nameValue = cookie.substringBefore(';').trim()
                val split = nameValue.split('=', limit = 2)
                if (split.size != 2) continue
                val name = split[0]
                val value = split[1]
                if (value.isBlank()) continue
                when (name) {
                    "auth" -> {
                        if (isLoginAttempt && response.isSuccessful) {
                            // Real session established; drop pending/2FA state.
                            tokenStore.authToken = value
                            tokenStore.pendingAuthToken = null
                            tokenStore.twoFactorToken = null
                        } else {
                            // 2FA challenge (401/200) or verify stage -> pending cookie.
                            tokenStore.pendingAuthToken = value
                        }
                        ApiDiagnostics.log("Set-Cookie auth=…${value.takeLast(8)}")
                    }
                    "twoFactorAuth" -> {
                        tokenStore.twoFactorToken = value
                        ApiDiagnostics.log("Set-Cookie twoFactorAuth=…${value.takeLast(8)}")
                    }
                }
            }
        } catch (_: Exception) {
            // Never let cookie handling break the request flow.
        }
    }

    private fun logResponse(request: Request, response: Response) {
        try {
            val path = request.url.encodedPath
            val relevant = path.endsWith("/auth/user") ||
                path.contains("twofactor") ||
                path.contains("friends") ||
                path.contains("/users/") ||
                path.contains("/worlds/")
            if (response.isSuccessful) {
                if (relevant && (path.endsWith("/auth/user") || path.contains("twofactor"))) {
                    ApiDiagnostics.log("HTTP ${response.code} ${request.method} ${path} ✓")
                }
            } else {
                val body = try {
                    response.peekBody(300).string().replace('\n', ' ').take(200)
                } catch (_: Exception) {
                    ""
                }
                ApiDiagnostics.log("HTTP ${response.code} ${request.method} ${path} ← $body")
            }
        } catch (_: Exception) {
            // logging must never break requests
        }
    }
}