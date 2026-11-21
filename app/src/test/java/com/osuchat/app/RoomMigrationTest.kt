package com.osuchat.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.osuchat.app.data.local.OsuChatDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class RoomMigrationTest {

    private data class IndexSpec(
        val tableName: String,
        val indexName: String,
        val columns: List<String>
    )

    private fun captureExecutedSql(block: (SupportSQLiteDatabase) -> Unit): List<String> {
        val executed = mutableListOf<String>()
        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args?.firstOrNull() as? String
                    if (sql != null) executed.add(sql)
                    Unit
                }
                "isOpen" -> true
                "inTransaction" -> false
                "getVersion" -> 5
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Double::class.javaPrimitiveType -> 0.0
                    Float::class.javaPrimitiveType -> 0f
                    else -> null
                }
            }
        } as SupportSQLiteDatabase
        block(proxy)
        return executed
    }

    private fun parseCreateIndexStatements(sqlStatements: List<String>): Set<IndexSpec> {
        val regex = Regex(
            """^CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS\s+`([^`]+)`\s+ON\s+`([^`]+)`\s*\(([^)]+)\)$""",
            RegexOption.IGNORE_CASE
        )
        return sqlStatements.map { sql ->
            val match = regex.matchEntire(sql.trim())
            assertNotNull("Unexpected SQL statement in MIGRATION_5_6: $sql", match)
            val indexName = match!!.groupValues[1]
            val tableName = match.groupValues[2]
            val columns = match.groupValues[3]
                .split(",")
                .map { it.trim().removeSurrounding("`") }
            IndexSpec(tableName = tableName, indexName = indexName, columns = columns)
        }.toSet()
    }

    private fun resolveLocalSourceFile(fileName: String): File? {
        val relative = "src/main/java/com/osuchat/app/data/local/$fileName"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
    }

    private fun parseEntityIndicesFromSource(entitiesSource: String): Set<IndexSpec> {
        val entityRegex = Regex(
            """@Entity\s*\(([\s\S]*?)\)\s*data\s+class\s+\w+""",
            RegexOption.MULTILINE
        )
        val tableNameRegex = Regex("""tableName\s*=\s*"([^"]+)"""")
        val indexBlockRegex = Regex("""Index\s*\(([\s\S]*?)\)""")
        val indexValueRegex = Regex("""value\s*=\s*\[([^\]]+)]""")
        val indexNameRegex = Regex("""name\s*=\s*"([^"]+)"""")
        val quotedStringRegex = Regex(""""([^"]+)"""")

        val specs = mutableSetOf<IndexSpec>()
        for (entityMatch in entityRegex.findAll(entitiesSource)) {
            val block = entityMatch.groupValues[1]
            val tableName = tableNameRegex.find(block)?.groupValues?.get(1) ?: continue
            for (idxMatch in indexBlockRegex.findAll(block)) {
                val idxBody = idxMatch.groupValues[1]
                val colsRaw = indexValueRegex.find(idxBody)?.groupValues?.get(1) ?: continue
                val cols = quotedStringRegex.findAll(colsRaw).map { it.groupValues[1] }.toList()
                val explicitName = indexNameRegex.find(idxBody)?.groupValues?.get(1)
                val name = explicitName ?: "index_${tableName}_${cols.joinToString("_")}"
                specs.add(IndexSpec(tableName = tableName, indexName = name, columns = cols))
            }
        }
        return specs
    }

    @Test
    fun databaseSource_isVersion9WithAllExpectedEntities() {
        val dbFile = resolveLocalSourceFile("OsuChatDatabase.kt")
        assertNotNull("Expected OsuChatDatabase.kt source file in workspace", dbFile)
        val dbSource = dbFile!!.readText()

        val versionMatch = Regex("""@Database\s*\([\s\S]*?version\s*=\s*(\d+)""").find(dbSource)
        assertNotNull(versionMatch)
        assertEquals(9, versionMatch!!.groupValues[1].toInt())

        val expectedEntityClasses = listOf(
            "ChannelEntity::class",
            "MessageEntity::class",
            "UserAliasEntity::class",
            "BeatmapCacheEntity::class",
            "PlayerProfileCacheEntity::class",
            "TrackedFriendEntity::class",
            "SavedBeatmapEntity::class",
            "SelfProfileCacheEntity::class",
            "MappoolEntity::class",
            "MappoolSlotEntity::class",
            "BookmarkedMessageEntity::class",
            "FriendScoreEventEntity::class",
            "ProfileHistoryEntity::class"
        )
        for (entityRef in expectedEntityClasses) {
            assertTrue("Missing $entityRef in @Database entities", dbSource.contains(entityRef))
        }
    }

    @Test
    fun migration5To6_createsAllNewIndexesMatchingEntityAnnotations() {
        assertEquals(5, OsuChatDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, OsuChatDatabase.MIGRATION_5_6.endVersion)

        val executedSql = captureExecutedSql { db ->
            OsuChatDatabase.MIGRATION_5_6.migrate(db)
        }
        assertEquals(12, executedSql.size)

        val migratedIndexes = parseCreateIndexStatements(executedSql)
        val expectedNewIndexes = setOf(
            IndexSpec("channels", "index_channels_type_isJoined", listOf("type", "isJoined")),
            IndexSpec("user_aliases", "index_user_aliases_customNickname", listOf("customNickname")),
            IndexSpec("user_aliases", "index_user_aliases_userId", listOf("userId")),
            IndexSpec("beatmap_cache", "index_beatmap_cache_beatmapId", listOf("beatmapId")),
            IndexSpec("beatmap_cache", "index_beatmap_cache_beatmapsetId", listOf("beatmapsetId")),
            IndexSpec("beatmap_cache", "index_beatmap_cache_cachedAt", listOf("cachedAt")),
            IndexSpec("player_profile_cache", "index_player_profile_cache_isOnline_username", listOf("isOnline", "username")),
            IndexSpec("player_profile_cache", "index_player_profile_cache_cachedAt", listOf("cachedAt")),
            IndexSpec("tracked_friends", "index_tracked_friends_username", listOf("username")),
            IndexSpec("tracked_friends", "index_tracked_friends_userId", listOf("userId")),
            IndexSpec("saved_beatmaps", "index_saved_beatmaps_beatmapsetId", listOf("beatmapsetId")),
            IndexSpec("self_profile_cache", "index_self_profile_cache_userId", listOf("userId"))
        )
        assertEquals(expectedNewIndexes, migratedIndexes)

        val preExistingV5Indexes = setOf(
            IndexSpec("channels", "index_channels_isJoined_isPinned_lastMessageTimestamp", listOf("isJoined", "isPinned", "lastMessageTimestamp")),
            IndexSpec("messages", "index_messages_channelId_timestamp", listOf("channelId", "timestamp")),
            IndexSpec("messages", "index_messages_timestamp", listOf("timestamp")),
            IndexSpec("player_profile_cache", "index_player_profile_cache_userId", listOf("userId")),
            IndexSpec("saved_beatmaps", "index_saved_beatmaps_savedAt", listOf("savedAt"))
        )

        val v7MappoolIndexes = parseCreateIndexStatements(
            captureExecutedSql { db ->
                OsuChatDatabase.MIGRATION_6_7.migrate(db)
            }.filter { it.trim().startsWith("CREATE INDEX", ignoreCase = true) }
        )

        val entitiesFile = resolveLocalSourceFile("Entities.kt")
        assertNotNull("Expected Entities.kt source file in workspace", entitiesFile)
        val sourceEntityIndexes = parseEntityIndicesFromSource(entitiesFile!!.readText())
        assertEquals(sourceEntityIndexes, preExistingV5Indexes + migratedIndexes + v7MappoolIndexes)
    }

    @Test
    fun migration6To7_createsMappoolTablesAndIndexesWithForeignKeyCascade() {
        assertEquals(6, OsuChatDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, OsuChatDatabase.MIGRATION_6_7.endVersion)

        val executedSql = captureExecutedSql { db ->
            OsuChatDatabase.MIGRATION_6_7.migrate(db)
        }

        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `mappools`") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_mappools_createdAt`") })
        assertTrue(
            executedSql.any {
                it.contains("CREATE TABLE IF NOT EXISTS `mappool_slots`") &&
                    it.contains("FOREIGN KEY(`poolId`) REFERENCES `mappools`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE")
            }
        )
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_mappool_slots_poolId_orderIndex`") })
    }

    @Test
    fun migration4To5_updatesChannelsAndCreatesSelfProfileCache() {
        assertEquals(4, OsuChatDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, OsuChatDatabase.MIGRATION_4_5.endVersion)

        val executedSql = captureExecutedSql { db ->
            OsuChatDatabase.MIGRATION_4_5.migrate(db)
        }

        assertTrue(executedSql.any { it.contains("ALTER TABLE `channels` ADD COLUMN `notificationMode`") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `channels` ADD COLUMN `isPinned`") })
        assertTrue(executedSql.any { it.contains("index_channels_isJoined_isPinned_lastMessageTimestamp") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `self_profile_cache`") })
    }

    @Test
    fun migration7To8_addsTagsAndDurationColumnsToSavedBeatmaps() {
        assertEquals(7, OsuChatDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, OsuChatDatabase.MIGRATION_7_8.endVersion)

        val executedSql = captureExecutedSql { db ->
            OsuChatDatabase.MIGRATION_7_8.migrate(db)
        }

        assertTrue(
            executedSql.any {
                it.contains("ALTER TABLE saved_beatmaps ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
            }
        )
        assertTrue(
            executedSql.any {
                it.contains("ALTER TABLE saved_beatmaps ADD COLUMN totalLengthSeconds INTEGER NOT NULL DEFAULT 0")
            }
        )
    }

    @Test
    fun migration8To9_addsNoteNotifyOnlineAndCreatesNewTables() {
        assertEquals(8, OsuChatDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, OsuChatDatabase.MIGRATION_8_9.endVersion)

        val executedSql = captureExecutedSql { db ->
            OsuChatDatabase.MIGRATION_8_9.migrate(db)
        }

        assertTrue(executedSql.any { it.contains("ALTER TABLE `user_aliases` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `tracked_friends` ADD COLUMN `notifyOnline` INTEGER NOT NULL DEFAULT 0") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `bookmarked_messages`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `friend_score_events`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `profile_history`") })
    }
}
