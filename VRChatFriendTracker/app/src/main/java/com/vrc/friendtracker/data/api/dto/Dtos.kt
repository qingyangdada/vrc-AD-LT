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
    /** Pronouns (人称代词), when the endpoint includes them. */
    val pronouns: String? = null,
    /** The logged-in user's private note (备注) about this user, when the API includes it. */
    val note: String? = null,
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
    /** The logged-in user's private note (备注) about this user, when the API includes it. */
    val note: String? = null,
    val tags: List<String>? = null,
    @SerialName("developerType") val developerType: String? = null,
    @SerialName("isFriend") val isFriend: Boolean? = null,
    val state: String? = null,
    val pronouns: String? = null,
    @SerialName("requiresTwoFactorAuth") val requiresTwoFactorAuth: List<String>? = null,
)

/** A note (备注) the logged-in user wrote about another user. */
@Serializable
data class UserNoteDto(
    val id: String = "",
    val note: String? = null,
    @SerialName("targetUserId") val targetUserId: String? = null,
    @SerialName("userId") val userId: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
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

/** Body for PUT /users/{userId}: status light, status text and pronouns.
 *  The bio is NOT accepted here — see UpdateProfileBody. */
@Serializable
data class UpdateUserBody(
    val status: String? = null,
    val statusDescription: String? = null,
    val pronouns: String? = null,
)

/**
 * Public profile from GET /profile/{userId}. The bio / bioLinks / pronouns
 * only exist on this object: the User returned by /users/{id} and the
 * CurrentUser returned by /auth/user have no bio field at all.
 */
@Serializable
data class PublicProfileDto(
    val id: String = "",
    @SerialName("displayName") val displayName: String = "",
    val bio: String? = null,
    @SerialName("bioLinks") val bioLinks: List<String>? = null,
    val pronouns: String? = null,
    val status: String? = null,
    @SerialName("statusDescription") val statusDescription: String? = null,
    @SerialName("trustTags") val trustTags: List<String>? = null,
    @SerialName("currentAvatarImageUrl") val currentAvatarImageUrl: String? = null,
    @SerialName("currentAvatarThumbnailImageUrl") val currentAvatarThumbnailImageUrl: String? = null,
    @SerialName("userIcon") val userIcon: String? = null,
    @SerialName("profilePicOverride") val profilePicOverride: String? = null,
    val tags: List<String>? = null,
)

/** Body for PUT /profile/{userId}; this is the endpoint that writes the bio. */
@Serializable
data class UpdateProfileBody(
    val bio: String? = null,
    @SerialName("bioLinks") val bioLinks: List<String>? = null,
)

/** A VRChat "print" (拍立得照片) taken with the in-game camera. */
@Serializable
data class PrintDto(
    val id: String = "",
    @SerialName("authorId") val authorId: String? = null,
    @SerialName("authorName") val authorName: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    val note: String? = null,
    @SerialName("ownerId") val ownerId: String? = null,
    val timestamp: String? = null,
    @SerialName("worldId") val worldId: String? = null,
    @SerialName("worldName") val worldName: String? = null,
    val files: PrintFilesDto? = null,
)

@Serializable
data class PrintFilesDto(
    @SerialName("fileId") val fileId: String? = null,
    val image: String? = null,
)

/** Body for renaming/updating an avatar. */
@Serializable
data class UpdateAvatarBody(
    val name: String? = null,
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
data class FavoriteDto(
    val id: String = "",
    val type: String = "",
    @SerialName("favoriteId") val favoriteId: String = "",
    val tags: List<String>? = null,
    @SerialName("favoriteGroupName") val favoriteGroupName: String? = null,
)

@Serializable
data class FavoriteGroupDto(
    val name: String = "",
    @SerialName("displayName") val displayName: String? = null,
    val type: String = "",
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
data class AvatarDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    @SerialName("thumbnailImageUrl") val thumbnailImageUrl: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("authorId") val authorId: String? = null,
    @SerialName("authorName") val authorName: String? = null,
    @SerialName("releaseStatus") val releaseStatus: String? = null,
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
