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