package com.vrc.friendtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    @Query("SELECT * FROM friends ORDER BY isOnline DESC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE id = :id")
    fun observeById(id: String): Flow<FriendEntity?>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun getById(id: String): FriendEntity?

    @Query("SELECT * FROM friends")
    suspend fun getAll(): List<FriendEntity>

    @Query("SELECT * FROM friends WHERE isOnline = 1")
    suspend fun getOnline(): List<FriendEntity>

    @Upsert
    suspend fun upsert(friend: FriendEntity)

    @Upsert
    suspend fun upsertAll(friends: List<FriendEntity>)

    /** Friends whose full profile (tags) hasn't been refreshed for the longest time. */
    @Query("SELECT * FROM friends ORDER BY tagsCheckedAt ASC LIMIT :limit")
    suspend fun getStaleForProfileCheck(limit: Int): List<FriendEntity>

    /** Friends that still have no avatar URL (fetched first so avatars appear quickly). */
    @Query("SELECT * FROM friends WHERE avatarUrl IS NULL OR avatarUrl = '' ORDER BY tagsCheckedAt ASC LIMIT :limit")
    suspend fun getMissingAvatarProfiles(limit: Int): List<FriendEntity>

    /** Friends whose public profile (bio / pronouns) has never been fetched;
     *  online friends come first so the visible list fills in first. */
    @Query("SELECT * FROM friends WHERE profileCheckedAt = 0 ORDER BY isOnline DESC, lastSeenAt DESC LIMIT :limit")
    suspend fun getNeverProfiled(limit: Int): List<FriendEntity>



    @Query("UPDATE friends SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE friends SET notify = :notify WHERE id = :id")
    suspend fun updateNotify(id: String, notify: Boolean)

    /** Friends with background notifications enabled. */
    @Query("SELECT * FROM friends WHERE notify = 1")
    suspend fun getWatched(): List<FriendEntity>

    /** Updates only the private note (备注) synced from VRChat user notes. */
    @Query("UPDATE friends SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String?)
    
    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM friends")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_events WHERE friendId = :friendId ORDER BY timestamp DESC")
    fun observeForFriend(friendId: String): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEventEntity>>

    @Insert
    suspend fun insert(event: HistoryEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<HistoryEventEntity>)

    @Query("DELETE FROM history_events WHERE friendId = :friendId")
    suspend fun deleteForFriend(friendId: String)

    @Query("DELETE FROM history_events")
    suspend fun clearAll()

    /** Events newer than [afterId], oldest first (used by the notification pass). */
    @Query("SELECT * FROM history_events WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getEventsAfterId(afterId: Long, limit: Int): List<HistoryEventEntity>

    /** Most recent event of one type for a friend; used to suppress exact repeats. */
    @Query("SELECT * FROM history_events WHERE friendId = :friendId AND type = :type ORDER BY id DESC LIMIT 1")
    suspend fun getLatestFor(friendId: String, type: String): HistoryEventEntity?

    /** Highest stored event id (0 when empty); keeps old events from re-notifying. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM history_events")
    suspend fun maxId(): Long
}

@Dao
interface WorldDao {

    @Query("SELECT * FROM worlds WHERE id = :id")
    suspend fun getById(id: String): WorldEntity?

    @Query("SELECT * FROM worlds WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WorldEntity>

    @Query("SELECT * FROM worlds")
    fun observeAll(): Flow<List<WorldEntity>>

    @Upsert
    suspend fun upsertAll(worlds: List<WorldEntity>)

    @Upsert
    suspend fun upsert(world: WorldEntity)
}
