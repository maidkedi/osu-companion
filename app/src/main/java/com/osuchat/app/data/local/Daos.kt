package com.osuchat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE isJoined = 1 ORDER BY isPinned DESC, lastMessageTimestamp DESC, name ASC")
    fun observeJoinedChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    fun observeChannel(channelId: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE REPLACE(LOWER(TRIM(name)), ' ', '_') = REPLACE(LOWER(TRIM(:name)), ' ', '_') LIMIT 1")
    suspend fun getChannelByName(name: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE type = :type AND REPLACE(LOWER(TRIM(name)), ' ', '_') = REPLACE(LOWER(TRIM(:name)), ' ', '_') LIMIT 1")
    suspend fun getChannelByNameAndType(name: String, type: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannel(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET unreadCount = 0 WHERE id = :channelId")
    suspend fun clearUnread(channelId: String)

    @Query(
        """
        UPDATE channels
        SET lastMessageText = :text,
            lastMessageTimestamp = MAX(lastMessageTimestamp, :timestamp),
            lastSenderName = :senderName,
            unreadCount = unreadCount + :unreadDelta
        WHERE id = :channelId
        """
    )
    suspend fun updateChannelLastMessage(
        channelId: String,
        text: String,
        timestamp: Long,
        senderName: String?,
        unreadDelta: Int
    )

    @Query(
        """
        UPDATE channels
        SET lastMessageText = :text,
            lastMessageTimestamp = :timestamp,
            lastSenderName = :senderName
        WHERE id = :channelId
          AND :timestamp > lastMessageTimestamp
        """
    )
    suspend fun updateChannelLastMessageIfNewer(
        channelId: String,
        text: String,
        timestamp: Long,
        senderName: String?
    )

    @Query(
        """
        UPDATE channels
        SET isJoined = 1,
            lastMessageText = :text,
            lastMessageTimestamp = MAX(lastMessageTimestamp, :timestamp),
            lastSenderName = :senderName,
            unreadCount = unreadCount + :unreadDelta
        WHERE id = :channelId
        """
    )
    suspend fun updateChannelOnIncomingMessage(
        channelId: String,
        text: String,
        timestamp: Long,
        senderName: String?,
        unreadDelta: Int
    )

    @Query("UPDATE channels SET isJoined = :joined WHERE id = :channelId")
    suspend fun setChannelJoined(channelId: String, joined: Boolean)

    @Query("UPDATE channels SET isPinned = :pinned WHERE id = :channelId")
    suspend fun setChannelPinned(channelId: String, pinned: Boolean)

    @Query("UPDATE channels SET notificationMode = :mode WHERE id = :channelId")
    suspend fun setChannelNotificationMode(channelId: String, mode: String)

    @Query(
        """
        UPDATE channels
        SET avatarUrl = COALESCE(:avatarUrl, avatarUrl),
            targetUserId = COALESCE(:userId, targetUserId)
        WHERE id = :channelId
        """
    )
    suspend fun updateDirectChannelMeta(channelId: String, avatarUrl: String?, userId: Long?)

    @Query("DELETE FROM channels WHERE id = :channelId")
    suspend fun deleteChannelById(channelId: String)

    @Query("DELETE FROM messages WHERE channelId = :channelId")
    suspend fun deleteMessagesForChannelInternal(channelId: String)

    @Transaction
    suspend fun deleteChannelWithMessages(channelId: String) {
        deleteMessagesForChannelInternal(channelId)
        deleteChannelById(channelId)
    }

    @Query(
        """
        SELECT * FROM channels
        WHERE LOWER(TRIM(id)) LIKE 'dm\_%' ESCAPE '\'
           OR (LOWER(TRIM(id)) LIKE 'dm:%' AND id != 'dm:' || REPLACE(LOWER(TRIM(SUBSTR(TRIM(id), 4))), ' ', '_'))
           OR (TRIM(id) LIKE '#%' AND id != LOWER(TRIM(id)))
        """
    )
    suspend fun getNonCanonicalChannels(): List<ChannelEntity>

    @Query(
        """
        UPDATE messages
        SET channelId = 'dm:' || REPLACE(LOWER(TRIM(SUBSTR(TRIM(channelId), 4))), ' ', '_')
        WHERE LOWER(TRIM(channelId)) LIKE 'dm\_%' ESCAPE '\'
           OR (LOWER(TRIM(channelId)) LIKE 'dm:%' AND channelId != 'dm:' || REPLACE(LOWER(TRIM(SUBSTR(TRIM(channelId), 4))), ' ', '_'))
        """
    )
    suspend fun normalizeLegacyDmMessageChannelIdsInternal()

    @Query(
        """
        UPDATE messages
        SET channelId = LOWER(TRIM(channelId))
        WHERE TRIM(channelId) LIKE '#%' AND channelId != LOWER(TRIM(channelId))
        """
    )
    suspend fun normalizePublicMessageChannelIdsInternal()

    /**
     * Atomically canonicalizes legacy `dm_`, mixed-case `DM:`, space-containing DM IDs, and
     * mixed-case `#Channel` IDs across `messages` and `channels`, merging duplicates without loss.
     */
    @Transaction
    suspend fun migrateAndNormalizeChannels() {
        normalizeLegacyDmMessageChannelIdsInternal()
        normalizePublicMessageChannelIdsInternal()
        val nonCanonical = getNonCanonicalChannels()
        for (old in nonCanonical) {
            val trimmed = old.id.trim()
            val canonicalId = when {
                trimmed.startsWith("#") -> trimmed.lowercase()
                trimmed.startsWith("dm:", ignoreCase = true) ||
                    trimmed.startsWith("dm_", ignoreCase = true) ->
                    "dm:" + trimmed.substring(3).trim().lowercase().replace(' ', '_')
                else -> continue
            }
            if (canonicalId == "#" || canonicalId == "dm:") {
                deleteChannelById(old.id)
                continue
            }
            if (canonicalId == old.id) continue
            val existing = getChannelById(canonicalId)
            if (existing == null) {
                upsertChannel(
                    old.copy(
                        id = canonicalId,
                        name = if (old.type == "PUBLIC") old.name.trim().lowercase() else old.name.trim()
                    )
                )
            } else {
                val useOldLast = old.lastMessageTimestamp > existing.lastMessageTimestamp
                upsertChannel(
                    existing.copy(
                        unreadCount = existing.unreadCount + old.unreadCount,
                        lastMessageText = if (useOldLast) old.lastMessageText else existing.lastMessageText,
                        lastMessageTimestamp = maxOf(existing.lastMessageTimestamp, old.lastMessageTimestamp),
                        lastSenderName = if (useOldLast) old.lastSenderName else existing.lastSenderName,
                        targetUserId = existing.targetUserId ?: old.targetUserId,
                        avatarUrl = existing.avatarUrl ?: old.avatarUrl,
                        isJoined = existing.isJoined || old.isJoined,
                        notificationMode = existing.notificationMode ?: old.notificationMode,
                        isPinned = existing.isPinned || old.isPinned
                    )
                )
            }
            deleteChannelById(old.id)
        }
    }

    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE channelId = :channelId
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    fun observeRecentMessagesForChannel(channelId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE channelId = :channelId AND timestamp < :beforeTimestamp
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    suspend fun getMessagesBefore(channelId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesDesc(channelId: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE channelId = :channelId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageId(channelId: String): String?

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY timestamp ASC, rowid ASC")
    suspend fun getAllMessagesForChannel(channelId: String): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE channelId = :channelId
          AND timestamp >= :minTimestamp
          AND timestamp <= :maxTimestamp
        ORDER BY timestamp ASC
        """
    )
    suspend fun getMessagesInRange(
        channelId: String,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessagesIfAbsent(messages: List<MessageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query(
        """
        UPDATE messages
        SET deliveryStatus = :status
        WHERE id = :messageId
          AND deliveryStatus NOT IN ('SENT', 'READ')
        """
    )
    suspend fun updateDeliveryStatusIfNotSent(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE channelId = :channelId")
    suspend fun deleteMessagesForChannel(channelId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query(
        """
        SELECT * FROM messages
        WHERE content LIKE '%' || :query || '%'
           OR senderName LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchAllMessages(query: String, limit: Int = 100): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE deliveryStatus = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getQueuedMessages(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface UserAliasDao {
    @Query("SELECT * FROM user_aliases ORDER BY customNickname ASC")
    fun observeAllAliases(): Flow<List<UserAliasEntity>>

    @Query("SELECT * FROM user_aliases WHERE normalizedUsername = :normalizedUsername LIMIT 1")
    suspend fun getAlias(normalizedUsername: String): UserAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: UserAliasEntity)

    @Query("DELETE FROM user_aliases WHERE normalizedUsername = :normalizedUsername")
    suspend fun deleteAlias(normalizedUsername: String)
}

@Dao
interface BeatmapCacheDao {
    @Query("SELECT * FROM beatmap_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getByKey(key: String): BeatmapCacheEntity?

    @Query("SELECT * FROM beatmap_cache WHERE cacheKey IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<BeatmapCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BeatmapCacheEntity)
}

@Dao
interface PlayerProfileCacheDao {
    @Query("SELECT * FROM player_profile_cache ORDER BY isOnline DESC, username ASC")
    fun observeAllProfiles(): Flow<List<PlayerProfileCacheEntity>>

    @Query("SELECT * FROM player_profile_cache WHERE normalizedUsername = :normalized LIMIT 1")
    suspend fun getByUsername(normalized: String): PlayerProfileCacheEntity?

    @Query("SELECT * FROM player_profile_cache WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: Long): PlayerProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerProfileCacheEntity)
}

@Dao
interface TrackedFriendDao {
    @Query("SELECT * FROM tracked_friends ORDER BY username ASC")
    fun observeTrackedFriends(): Flow<List<TrackedFriendEntity>>

    @Query("SELECT * FROM tracked_friends")
    suspend fun getAllTrackedFriends(): List<TrackedFriendEntity>

    @Query("SELECT * FROM tracked_friends WHERE normalizedUsername = :normalizedUsername LIMIT 1")
    suspend fun getTrackedFriend(normalizedUsername: String): TrackedFriendEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackedFriend(entity: TrackedFriendEntity)

    @Query("DELETE FROM tracked_friends WHERE normalizedUsername = :normalizedUsername")
    suspend fun deleteTrackedFriend(normalizedUsername: String)

    @Query("UPDATE tracked_friends SET notifyOnline = :enabled WHERE normalizedUsername = :normalizedUsername")
    suspend fun updateNotifyOnlineInternal(normalizedUsername: String, enabled: Boolean): Int

    @Transaction
    suspend fun setNotifyOnline(
        normalizedUsername: String,
        enabled: Boolean,
        userId: Long = 0L,
        username: String = normalizedUsername,
        avatarUrl: String = ""
    ) {
        val existing = getTrackedFriend(normalizedUsername)
        if (existing != null) {
            if (!enabled && existing.lastCheckedAt == 0L && existing.topScoreIdsCsv.isEmpty()) {
                deleteTrackedFriend(normalizedUsername)
            } else {
                updateNotifyOnlineInternal(normalizedUsername, enabled)
            }
        } else if (enabled) {
            upsertTrackedFriend(
                TrackedFriendEntity(
                    normalizedUsername = normalizedUsername,
                    userId = userId,
                    username = username,
                    avatarUrl = avatarUrl,
                    topScoreIdsCsv = "",
                    lastCheckedAt = 0L,
                    notifyOnline = true
                )
            )
        }
    }
}

@Dao
interface SavedBeatmapDao {
    @Query("SELECT * FROM saved_beatmaps ORDER BY savedAt DESC")
    fun observeSavedBeatmaps(): Flow<List<SavedBeatmapEntity>>

    @Query("SELECT * FROM saved_beatmaps ORDER BY savedAt DESC")
    suspend fun getAllSavedBeatmaps(): List<SavedBeatmapEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_beatmaps WHERE beatmapId = :beatmapId)")
    suspend fun isSaved(beatmapId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedBeatmapEntity)

    @Query("DELETE FROM saved_beatmaps WHERE beatmapId = :beatmapId")
    suspend fun deleteByBeatmapId(beatmapId: Long)

    @Query("UPDATE saved_beatmaps SET tagsCsv = :tagsCsv WHERE beatmapId = :beatmapId")
    suspend fun updateTags(beatmapId: Long, tagsCsv: String)

    @Transaction
    suspend fun toggleSaved(entity: SavedBeatmapEntity): Boolean {
        return if (isSaved(entity.beatmapId)) {
            deleteByBeatmapId(entity.beatmapId)
            false
        } else {
            upsert(entity)
            true
        }
    }

    @Query("DELETE FROM saved_beatmaps")
    suspend fun clearAll()
}

@Dao
interface SelfProfileCacheDao {
    @Query("SELECT * FROM self_profile_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String = "self"): SelfProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SelfProfileCacheEntity)

    @Query("DELETE FROM self_profile_cache")
    suspend fun clearAll()
}

@Dao
interface MappoolDao {
    @Query("SELECT * FROM mappools ORDER BY createdAt DESC, id DESC")
    fun observeMappools(): Flow<List<MappoolEntity>>

    @Query("SELECT * FROM mappool_slots ORDER BY poolId ASC, orderIndex ASC, id ASC")
    fun observeAllSlots(): Flow<List<MappoolSlotEntity>>

    @Query("SELECT * FROM mappools WHERE id = :poolId LIMIT 1")
    suspend fun getPoolById(poolId: Long): MappoolEntity?

    @Query("SELECT * FROM mappool_slots WHERE poolId = :poolId ORDER BY orderIndex ASC, id ASC")
    suspend fun getSlotsForPool(poolId: Long): List<MappoolSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPool(pool: MappoolEntity): Long

    @Query("UPDATE mappools SET name = :name WHERE id = :poolId")
    suspend fun updatePoolName(poolId: Long, name: String)

    @Query("DELETE FROM mappools WHERE id = :poolId")
    suspend fun deletePoolById(poolId: Long)

    @Query("DELETE FROM mappool_slots WHERE poolId = :poolId")
    suspend fun deleteSlotsForPool(poolId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<MappoolSlotEntity>)

    @Transaction
    suspend fun savePoolWithSlots(
        poolId: Long?,
        name: String,
        slots: List<MappoolSlotEntity>
    ): Long {
        val cleanName = name.trim()
        val existing = if (poolId != null && poolId > 0L) getPoolById(poolId) else null
        val resolvedPoolId = if (existing != null) {
            updatePoolName(existing.id, cleanName)
            deleteSlotsForPool(existing.id)
            existing.id
        } else {
            insertPool(MappoolEntity(name = cleanName))
        }
        val orderedSlots = slots.mapIndexed { idx, slot ->
            slot.copy(id = 0L, poolId = resolvedPoolId, orderIndex = idx)
        }
        if (orderedSlots.isNotEmpty()) {
            insertSlots(orderedSlots)
        }
        return resolvedPoolId
    }
}

@Dao
interface BookmarkedMessageDao {
    @Query("SELECT * FROM bookmarked_messages ORDER BY bookmarkedAt DESC, messageTimestamp DESC")
    fun observeBookmarks(): Flow<List<BookmarkedMessageEntity>>

    @Query("SELECT messageId FROM bookmarked_messages")
    fun observeBookmarkedMessageIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_messages WHERE messageId = :messageId)")
    suspend fun isBookmarked(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(entity: BookmarkedMessageEntity)

    @Query("DELETE FROM bookmarked_messages WHERE messageId = :messageId")
    suspend fun deleteBookmark(messageId: String)

    @Transaction
    suspend fun toggleBookmark(entity: BookmarkedMessageEntity): Boolean {
        return if (isBookmarked(entity.messageId)) {
            deleteBookmark(entity.messageId)
            false
        } else {
            upsertBookmark(entity)
            true
        }
    }
}

@Dao
interface FriendScoreEventDao {
    @Query("SELECT * FROM friend_score_events ORDER BY recordedAt DESC, scoreId DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<FriendScoreEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<FriendScoreEventEntity>)

    @Query("DELETE FROM friend_score_events WHERE scoreId NOT IN (SELECT scoreId FROM friend_score_events ORDER BY recordedAt DESC, scoreId DESC LIMIT :keepCount)")
    suspend fun pruneOldEvents(keepCount: Int = 100)

    @Transaction
    suspend fun upsertAndPrune(events: List<FriendScoreEventEntity>, keepCount: Int = 100) {
        if (events.isEmpty()) return
        upsertEvents(events)
        pruneOldEvents(keepCount)
    }
}

@Dao
interface ProfileHistoryDao {
    @Query("SELECT * FROM profile_history WHERE mode = :mode AND dayKey >= :minDayKey ORDER BY dayKey ASC LIMIT :limitDays")
    fun observeHistoryForMode(mode: String, minDayKey: String, limitDays: Int): Flow<List<ProfileHistoryEntity>>

    @Query("SELECT * FROM (SELECT * FROM profile_history WHERE mode = :mode ORDER BY dayKey DESC LIMIT :limitDays) ORDER BY dayKey ASC")
    fun observeRecentHistoryForMode(mode: String, limitDays: Int): Flow<List<ProfileHistoryEntity>>

    @Query("SELECT * FROM (SELECT * FROM profile_history WHERE mode = :mode ORDER BY dayKey DESC LIMIT :limitDays) ORDER BY dayKey ASC")
    suspend fun getRecentHistoryForMode(mode: String, limitDays: Int): List<ProfileHistoryEntity>

    @Query("SELECT * FROM profile_history ORDER BY dayKey ASC, mode ASC")
    suspend fun getAllSnapshots(): List<ProfileHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(entity: ProfileHistoryEntity)

    @Query("DELETE FROM profile_history WHERE dayKey < :cutoffDayKey")
    suspend fun pruneOlderThan(cutoffDayKey: String)
}
