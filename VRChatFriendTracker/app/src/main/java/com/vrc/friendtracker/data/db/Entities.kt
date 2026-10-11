package com.vrc.friendtracker.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object HistoryType {
    const val BIO = "bio"
    const val RANK = "rank"
    const val STATUS = "status"
    const val ROOM = "room"
}

@Entity(tableName = "friends")
@Immutable
data class FriendEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    /** The logged-in user's private note (备注) about this friend; the friend card shows it in the bio line instead of the bio when present. */
    val note: String? = null,
    val avatarUrl: String?,
    /** Model-avatar URL to fall back to when the VRC+ picture fails to load. */
    val avatarFallbackUrl: String?,
    val bio: String?,
    val bioLinksJson: String?,
    /** Pronouns (人称代词) from the public profile; the friends-list endpoint
     *  does not carry them, so they are filled in by the profile backfill. */
    val pronouns: String? = null,
    val tagsJson: String?,
    val trustRank: String,
    val isTroll: Boolean,
    val isModerator: Boolean,
    val status: String?,
    val statusDescription: String?,
    val location: String?,
    val worldId: String?,
    val instanceId: String?,
    val isOnline: Boolean,
    val lastLogin: String?,
    val lastActivity: String?,
    val lastPlatform: String?,
    /** When the full profile (with tags) was last fetched for this friend. */
    val tagsCheckedAt: Long,
    /** When the public profile (bio / pronouns) was last fetched for this friend. */
    val profileCheckedAt: Long = 0,
    /** When this friend was last seen in any sync. */
    val lastSeenAt: Long,
    val updatedAt: Long,
    /** User pinned this friend to the top of the list. */
    val pinned: Boolean = false,
    /** User enabled background notifications for this friend's online/offline/room changes. */
    val notify: Boolean = false,
)

@Entity(
    tableName = "history_events",
    indices = [Index("friendId"), Index("timestamp")]
)
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendId: String,
    val type: String,
    val oldValue: String?,
    val newValue: String?,
    val timestamp: Long,
)

@Entity(tableName = "worlds")
data class WorldEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val authorName: String?,
    val description: String?,
    val updatedAt: Long,
    /** Favorite/detail extras so the favorites grid can be served from cache. */
    val capacity: Int? = null,
    val releaseStatus: String? = null,
    val tags: String? = null,
)
