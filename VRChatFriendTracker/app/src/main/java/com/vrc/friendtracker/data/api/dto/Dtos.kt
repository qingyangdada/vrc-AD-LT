package com.vrc.friendtracker.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** User object as returned by the friends list endpoint (limited, tags always empty). */
@Serializable
data class LimitedUserDto(
    val id: String = "",
    @SerialName("displayName") val displayName: String = "",
    val bio: String? = null,
    @SerialName("bioLinks") val bioLinks: List<String>? = null,
    @SerialName("currentAvatarImageUrl") val currentAvatarImageUrl: String? = null,
    @SerialName("currentAvatarThumbnailImageUrl") val currentAvatarThumbnailImageUrl: String? = null,
    @SerialName("profilePicOverride") val profilePicOverride: String? = null,
    @SerialName("profilePicOverrideThumbnail") val profilePicOverrideThumbnail: String? = null,
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("last_activity") val lastActivity: String? = null,
    @SerialName("last_platform") val lastPlatform: String? = null,
    val location: String? = null,
    val status: String? = null,
    @SerialName("statusDescription") val statusDescription: String? = null,
    val tags: List<String>? = null,
    @SerialName("developerType") val developerType: String? = null,
    @SerialName("isFriend") val isFriend: Boolean? = null,
    @SerialName("friendKey") val friendKey: String? = null,
)

/** Full user object as returned by GET /users/{userId} (contains real tags). */
@Serializable
data class UserDto(
    val id: String = "",
    @SerialName("displayName") val displayName: String = "",
    val username: String? = null,
    val bio: String? = null,
    @SerialName("bioLinks") val bioLinks: List<String>? = null,
    @SerialName("currentAvatarImageUrl") val currentAvatarImageUrl: String? = null,
    @SerialName("currentAvatarThumbnailImageUrl") val currentAvatarThumbnailImageUrl: String? = null,
    @SerialName("profilePicOverride") val profilePicOverride: String? = null,
    @SerialName("profilePicOverrideThumbnail") val profilePicOverrideThumbnail: String? = null,
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("last_activity") val lastActivity: String? = null,
    @SerialName("last_platform") val lastPlatform: String? = null,
    val location: String? = null,
    val status: String? = null,
    @SerialName("statusDescription") val statusDescription: String? = null,
    val tags: List<String>? = null,
    @SerialName("developerType") val developerType: String? = null,
    @SerialName("isFriend") val isFriend: Boolean? = null,
    val state: String? = null,
    @SerialName("requiresTwoFactorAuth") val requiresTwoFactorAuth: List<String>? = null,
)

@Serializable
data class TwoFactorAuthCode(
    val code: String,
)

@Serializable
data class Verify2FAResult(
    val verified: Boolean? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("requiresTwoFactorAuth") val requiresTwoFactorAuth: List<String>? = null,
)

@Serializable
data class WorldDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("thumbnailImageUrl") val thumbnailImageUrl: String? = null,
    @SerialName("authorName") val authorName: String? = null,
    val capacity: Int? = null,
    @SerialName("releaseStatus") val releaseStatus: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class InstanceDto(
    val id: String = "",
    val name: String? = null,
    val type: String? = null,
    @SerialName("worldId") val worldId: String? = null,
    @SerialName("instanceId") val instanceId: String? = null,
    @SerialName("n_users") val nUsers: Int? = null,
    val userCount: Int? = null,
    val capacity: Int? = null,
    val region: String? = null,
    val full: Boolean? = null,
    @SerialName("ownerId") val ownerId: String? = null,
)