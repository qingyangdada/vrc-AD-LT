package com.vrc.friendtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FriendEntity::class, HistoryEventEntity::class, WorldEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun friendDao(): FriendDao
    abstract fun historyDao(): HistoryDao
    abstract fun worldDao(): WorldDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: add the model-avatar fallback column used when the VRC+ picture fails. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN avatarFallbackUrl TEXT")
            }
        }
        /** v2 -> v3: add the pinned-to-top flag for the swipe-to-pin feature. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** v3 -> v4: world cache gains favorite-detail columns (capacity, release, tags). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE worlds ADD COLUMN capacity INTEGER")
                db.execSQL("ALTER TABLE worlds ADD COLUMN releaseStatus TEXT")
                db.execSQL("ALTER TABLE worlds ADD COLUMN tags TEXT")
            }
        }
        /** v4 -> v5: friends gain the private note (备注) column shown in the bio line. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN note TEXT")
            }
        }
        /** v5 -> v6: friends gain the per-friend background-notification flag. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN notify INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** v6 -> v7: friends gain pronouns (人称代词) plus the timestamp of the
         *  last successful public-profile fetch (bio / pronouns backfill). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN pronouns TEXT")
                db.execSQL("ALTER TABLE friends ADD COLUMN profileCheckedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vrc_friend_tracker.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
