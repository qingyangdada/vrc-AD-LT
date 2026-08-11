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
    val avatarUrl: String?,
    /** Model-avatar URL to fall back to when the VRC+ picture fails to load. */
    val avatarFallbackUrl: String?,
    val bio: String?,
    val bioLinksJson: String?,
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
    /** When this friend was last seen in any sync. */
    val lastSeenAt: Long,
    val updatedAt: Long,
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
)