package com.vrc.friendtracker.data.api

import com.vrc.friendtracker.data.api.dto.InstanceDto
import com.vrc.friendtracker.data.api.dto.LimitedUserDto
import com.vrc.friendtracker.data.api.dto.TwoFactorAuthCode
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.api.dto.Verify2FAResult
import com.vrc.friendtracker.data.api.dto.WorldDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VrChatApiService {

    /** Logs in with Basic auth, or returns current user if already authenticated. */
    @GET("auth/user")
    suspend fun login(@Header("Authorization") basic: String): UserDto

    /** Current logged-in user info. */
    @GET("auth/user")
    suspend fun getCurrentUser(): UserDto

    /** TOTP (authenticator app) 2FA code verification. */
    @POST("auth/twofactorauth/totp/verify")
    suspend fun verifyTotp(@Body body: TwoFactorAuthCode): Verify2FAResult

    /** OTP recovery code verification. */
    @POST("auth/twofactorauth/otp/verify")
    suspend fun verifyOtp(@Body body: TwoFactorAuthCode): Verify2FAResult

    /** Email OTP verification. */
    @POST("auth/twofactorauth/emailotp/verify")
    suspend fun verifyEmailOtp(@Body body: TwoFactorAuthCode): Verify2FAResult

    /** Friends list. offline=false -> online+active friends; offline=true -> offline friends. */
    @GET("auth/user/friends")
    suspend fun getFriends(
        @Query("offline") offline: Boolean,
        @Query("offset") offset: Int = 0,
        @Query("number") number: Int = 100,
    ): List<LimitedUserDto>

    /** Full user profile (contains real tags used for trust rank). */
    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserDto

    /** World details. */
    @GET("worlds/{worldId}")
    suspend fun getWorld(@Path("worldId") worldId: String): WorldDto

    /** Instance details. */
    @GET("instances/{worldInstance}")
    suspend fun getInstance(@Path(value = "worldInstance", encoded = true) worldInstance: String): InstanceDto

    @POST("logout")
    suspend fun logout(): Unit
}