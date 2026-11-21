package com.osuchat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChannelEntity::class,
        MessageEntity::class,
        UserAliasEntity::class,
        BeatmapCacheEntity::class,
        PlayerProfileCacheEntity::class,
        TrackedFriendEntity::class,
        SavedBeatmapEntity::class,
        SelfProfileCacheEntity::class,
        MappoolEntity::class,
        MappoolSlotEntity::class,
        BookmarkedMessageEntity::class,
        FriendScoreEventEntity::class,
        ProfileHistoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class OsuChatDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun userAliasDao(): UserAliasDao
    abstract fun beatmapCacheDao(): BeatmapCacheDao
    abstract fun playerProfileCacheDao(): PlayerProfileCacheDao
    abstract fun trackedFriendDao(): TrackedFriendDao
    abstract fun savedBeatmapDao(): SavedBeatmapDao
    abstract fun selfProfileCacheDao(): SelfProfileCacheDao
    abstract fun mappoolDao(): MappoolDao
    abstract fun bookmarkedMessageDao(): BookmarkedMessageDao
    abstract fun friendScoreEventDao(): FriendScoreEventDao
    abstract fun profileHistoryDao(): ProfileHistoryDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `notificationMode` TEXT")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_channels_isJoined_lastMessageTimestamp`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channels_isJoined_isPinned_lastMessageTimestamp` ON `channels` (`isJoined`, `isPinned`, `lastMessageTimestamp`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `self_profile_cache` (
                        `cacheKey` TEXT NOT NULL PRIMARY KEY,
                        `userId` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `avatarUrl` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `countryCode` TEXT NOT NULL,
                        `countryName` TEXT,
                        `globalRank` INTEGER,
                        `countryRank` INTEGER,
                        `pp` REAL,
                        `accuracy` REAL,
                        `playCount` INTEGER,
                        `isOnline` INTEGER NOT NULL,
                        `currentAction` TEXT,
                        `mode` TEXT NOT NULL,
                        `cachedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channels_type_isJoined` ON `channels` (`type`, `isJoined`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_aliases_customNickname` ON `user_aliases` (`customNickname`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_aliases_userId` ON `user_aliases` (`userId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_beatmap_cache_beatmapId` ON `beatmap_cache` (`beatmapId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_beatmap_cache_beatmapsetId` ON `beatmap_cache` (`beatmapsetId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_beatmap_cache_cachedAt` ON `beatmap_cache` (`cachedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_player_profile_cache_isOnline_username` ON `player_profile_cache` (`isOnline`, `username`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_player_profile_cache_cachedAt` ON `player_profile_cache` (`cachedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracked_friends_username` ON `tracked_friends` (`username`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracked_friends_userId` ON `tracked_friends` (`userId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_beatmaps_beatmapsetId` ON `saved_beatmaps` (`beatmapsetId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_self_profile_cache_userId` ON `self_profile_cache` (`userId`)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mappools` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_mappools_createdAt` ON `mappools` (`createdAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mappool_slots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `poolId` INTEGER NOT NULL,
                        `slotTag` TEXT NOT NULL,
                        `beatmapId` INTEGER NOT NULL,
                        `mods` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`poolId`) REFERENCES `mappools`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_mappool_slots_poolId_orderIndex` ON `mappool_slots` (`poolId`, `orderIndex`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_beatmaps ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_beatmaps ADD COLUMN totalLengthSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_aliases` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tracked_friends` ADD COLUMN `notifyOnline` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarked_messages` (
                        `messageId` TEXT NOT NULL PRIMARY KEY,
                        `channelId` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `messageTimestamp` INTEGER NOT NULL,
                        `bookmarkedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `friend_score_events` (
                        `scoreId` INTEGER NOT NULL PRIMARY KEY,
                        `userId` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `avatarUrl` TEXT NOT NULL,
                        `beatmapId` INTEGER NOT NULL,
                        `beatmapTitle` TEXT NOT NULL,
                        `beatmapVersion` TEXT NOT NULL,
                        `starRating` REAL NOT NULL,
                        `rank` TEXT NOT NULL,
                        `pp` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `modsCsv` TEXT NOT NULL,
                        `createdAtIso` TEXT NOT NULL,
                        `recordedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_history` (
                        `dayKey` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `globalRank` INTEGER NOT NULL,
                        `countryRank` INTEGER NOT NULL,
                        `pp` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dayKey`, `mode`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
