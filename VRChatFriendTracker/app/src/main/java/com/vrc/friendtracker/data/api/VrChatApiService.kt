package com.vrc.friendtracker.data.api

import com.vrc.friendtracker.data.api.dto.AvatarDto
import com.vrc.friendtracker.data.api.dto.FavoriteDto
import com.vrc.friendtracker.data.api.dto.FavoriteGroupDto
import com.vrc.friendtracker.data.api.dto.InstanceDto
import com.vrc.friendtracker.data.api.dto.LimitedUserDto
import com.vrc.friendtracker.data.api.dto.PrintDto
import com.vrc.friendtracker.data.api.dto.PublicProfileDto
import com.vrc.friendtracker.data.api.dto.TwoFactorAuthCode
import com.vrc.friendtracker.data.api.dto.UpdateAvatarBody
import com.vrc.friendtracker.data.api.dto.UpdateProfileBody
import com.vrc.friendtracker.data.api.dto.UpdateUserBody
import com.vrc.friendtracker.data.api.dto.UserNoteDto
import com.vrc.friendtracker.data.api.dto.UserDto
import com.vrc.friendtracker.data.api.dto.Verify2FAResult
import com.vrc.friendtracker.data.api.dto.WorldDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface VrChatApiService {

    /** Logs in with Basic auth, or returns current user if already authenticated. */
    @GET("auth/user")
    suspend fun login(@Header("Authorization") basic: String): UserDto

    /** Current logged-in user info. */
    @GET("auth/user")
    suspend fun getCurrentUser(): UserDto

    /** Updates the logged-in user's status light and/or custom status text.
     *  VRChat removed the old /users/{id}/status endpoints; status is now set
     *  through the generic user-update endpoint. */
    @PUT("users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body body: UpdateUserBody,
    ): UserDto

    /** The logged-in user's prints (拍立得照片), paginated with `number`. */
    @GET("prints/user/{userId}")
    suspend fun getUserPrints(
        @Path("userId") userId: String,
        @Query("offset") offset: Int = 0,
        @Query("number") number: Int = 100,
    ): List<PrintDto>

    /** Same endpoint, paginated with `n` (newer VRChat list endpoints use `n`). */
    @GET("prints/user/{userId}")
    suspend fun getUserPrintsN(
        @Path("userId") userId: String,
        @Query("offset") offset: Int = 0,
        @Query("n") n: Int = 100,
    ): List<PrintDto>

    /** Uploads a print. The image part is a binary PNG; timestamp is ISO-8601. */
    @Multipart
    @POST("prints")
    suspend fun uploadPrint(
        @Part image: MultipartBody.Part,
        @Part("timestamp") timestamp: RequestBody,
        @Part("note") note: RequestBody?,
        @Part("worldId") worldId: RequestBody?,
        @Part("worldName") worldName: RequestBody?,
    ): PrintDto

    /** Deletes one of the logged-in user's prints. */
    @DELETE("prints/{printId}")
    suspend fun deletePrint(@Path("printId") printId: String)

    /** Renames / updates an avatar. */
    @PUT("avatars/{avatarId}")
    suspend fun updateAvatar(
        @Path("avatarId") avatarId: String,
        @Body body: UpdateAvatarBody,
    ): AvatarDto

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

    /** Notes (备注) the logged-in user wrote about other users; paginated.
     *  Both `n` and `number` are sent because VRChat accepts either spelling
     *  and different SDKs disagree on which one the API uses. */
    @GET("userNotes")
    suspend fun getUserNotes(
        @Query("offset") offset: Int = 0,
        @Query("number") number: Int = 100,
        @Query("n") n: Int = 100,
    ): List<UserNoteDto>

    /** Mutual friends between the logged-in user and the specified user. */
    @GET("users/{userId}/mutuals/friends")
    suspend fun getMutualFriends(
        @Path("userId") userId: String,
        @Query("offset") offset: Int = 0,
        @Query("n") number: Int = 100,
    ): List<LimitedUserDto>
    
    /** Full user profile (contains real tags used for trust rank). */
    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserDto

    /**
     * Public profile — the only place the bio / bioLinks live. Pass
     * `asSelf = true` for the logged-in account so VRChat returns the same
     * fields it shows on your own profile page.
     */
    @GET("profile/{userId}")
    suspend fun getProfile(
        @Path("userId") userId: String,
        @Query("asSelf") asSelf: Boolean? = null,
    ): PublicProfileDto

    /** Updates the logged-in user's bio / bio links. */
    @PUT("profile/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Body body: UpdateProfileBody,
    ): PublicProfileDto

    /** Avatars owned by the logged-in user (including private/hidden ones). */
    @GET("avatars")
    suspend fun getOwnAvatars(
        @Query("user") user: String = "me",
        @Query("releaseStatus") releaseStatus: String = "all",
        @Query("offset") offset: Int = 0,
        @Query("n") number: Int = 100,
    ): List<AvatarDto>
    
    /** The logged-in user's favorites; type=world returns favorited world ids. */
    @GET("favorites")
    suspend fun getFavorites(
        @Query("type") type: String,
        @Query("offset") offset: Int = 0,
        @Query("n") number: Int = 100,
    ): List<FavoriteDto>

    /** Favorite folders (groups) of one type, keyed by group key e.g. "worlds1". */
    @GET("favorite/groups")
    suspend fun getFavoriteGroups(@Query("type") type: String): Map<String, FavoriteGroupDto>

    /** World details. */
    @GET("worlds/{worldId}")
    suspend fun getWorld(@Path("worldId") worldId: String): WorldDto

    /** Instance details. */
    @GET("instances/{worldInstance}")
    suspend fun getInstance(@Path(value = "worldInstance", encoded = true) worldInstance: String): InstanceDto

    @POST("logout")
    suspend fun logout(): Unit
}
