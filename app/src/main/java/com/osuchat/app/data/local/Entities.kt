package com.osuchat.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(
            value = ["isJoined", "isPinned", "lastMessageTimestamp"],
            name = "index_channels_isJoined_isPinned_lastMessageTimestamp"
        ),
        Index(
            value = ["type", "isJoined"],
            name = "index_channels_type_isJoined"
        )
    ]
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val type: String, // PUBLIC or DIRECT
    val unreadCount: Int = 0,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val lastSenderName: String? = null,
    val targetUserId: Long? = null,
    val avatarUrl: String? = null,
    val isJoined: Boolean = true,
    val notificationMode: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [
        Index(
            value = ["channelId", "timestamp"],
            name = "index_messages_channelId_timestamp"
        ),
        Index(
            value = ["timestamp"],
            name = "index_messages_timestamp"
        )
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val channelId: String,
    val senderId: Long,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val content: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isAction: Boolean = false,
    val isHighlighted: Boolean = false,
    val deliveryStatus: String = "SENT"
)

@Entity(
    tableName = "user_aliases",
    indices = [
        Index(
            value = ["customNickname"],
            name = "index_user_aliases_customNickname"
        ),
        Index(
            value = ["userId"],
            name = "index_user_aliases_userId"
        )
    ]
)
data class UserAliasEntity(
    @PrimaryKey
    val normalizedUsername: String,
    val originalUsername: String,
    val userId: Long? = null,
    val customNickname: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "")
    val note: String = ""
)

@Entity(
    tableName = "beatmap_cache",
    indices = [
        Index(
            value = ["beatmapId"],
            name = "index_beatmap_cache_beatmapId"
        ),
        Index(
            value = ["beatmapsetId"],
            name = "index_beatmap_cache_beatmapsetId"
        ),
        Index(
            value = ["cachedAt"],
            name = "index_beatmap_cache_cachedAt"
        )
    ]
)
data class BeatmapCacheEntity(
    @PrimaryKey
    val cacheKey: String, // "b:<id>" or "s:<id>"
    val beatmapId: Long,
    val beatmapsetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val version: String,
    val starRating: Double,
    val bpm: Double,
    val totalLengthSeconds: Int,
    val ar: Double,
    val cs: Double,
    val od: Double,
    val coverUrl: String,
    val previewAudioUrl: String,
    val mode: String,
    val cachedAt: Long
)

@Entity(
    tableName = "player_profile_cache",
    indices = [
        Index(
            value = ["userId"],
            name = "index_player_profile_cache_userId"
        ),
        Index(
            value = ["isOnline", "username"],
            name = "index_player_profile_cache_isOnline_username"
        ),
        Index(
            value = ["cachedAt"],
            name = "index_player_profile_cache_cachedAt"
        )
    ]
)
data class PlayerProfileCacheEntity(
    @PrimaryKey
    val normalizedUsername: String,
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val coverUrl: String?,
    val countryCode: String,
    val countryName: String?,
    val globalRank: Int?,
    val countryRank: Int?,
    val pp: Double?,
    val accuracy: Double?,
    val playCount: Int?,
    val isOnline: Boolean,
    val currentAction: String?,
    val cachedAt: Long
)

@Entity(
    tableName = "tracked_friends",
    indices = [
        Index(
            value = ["username"],
            name = "index_tracked_friends_username"
        ),
        Index(
            value = ["userId"],
            name = "index_tracked_friends_userId"
        )
    ]
)
data class TrackedFriendEntity(
    @PrimaryKey
    val normalizedUsername: String,
    val userId: Long,
    val username: String,
    val avatarUrl: String = "",
    val topScoreIdsCsv: String = "",
    val lastCheckedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val notifyOnline: Boolean = false
)

@Entity(
    tableName = "saved_beatmaps",
    indices = [
        Index(
            value = ["savedAt"],
            name = "index_saved_beatmaps_savedAt"
        ),
        Index(
            value = ["beatmapsetId"],
            name = "index_saved_beatmaps_beatmapsetId"
        )
    ]
)
data class SavedBeatmapEntity(
    @PrimaryKey
    val beatmapId: Long,
    val beatmapsetId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val version: String,
    val starRating: Double,
    val bpm: Double,
    val mode: String,
    val coverUrl: String,
    val previewAudioUrl: String,
    val savedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "")
    val tagsCsv: String = "",
    @ColumnInfo(defaultValue = "0")
    val totalLengthSeconds: Int = 0
)

@Entity(
    tableName = "self_profile_cache",
    indices = [
        Index(
            value = ["userId"],
            name = "index_self_profile_cache_userId"
        )
    ]
)
data class SelfProfileCacheEntity(
    @PrimaryKey
    val cacheKey: String = "self",
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val coverUrl: String?,
    val countryCode: String,
    val countryName: String?,
    val globalRank: Int?,
    val countryRank: Int?,
    val pp: Double?,
    val accuracy: Double?,
    val playCount: Int?,
    val isOnline: Boolean,
    val currentAction: String?,
    val mode: String = "osu",
    val cachedAt: Long
)

@Entity(
    tableName = "mappools",
    indices = [
        Index(
            value = ["createdAt"],
            name = "index_mappools_createdAt"
        )
    ]
)
data class MappoolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mappool_slots",
    foreignKeys = [
        ForeignKey(
            entity = MappoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["poolId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["poolId", "orderIndex"],
            name = "index_mappool_slots_poolId_orderIndex"
        )
    ]
)
data class MappoolSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val poolId: Long,
    val slotTag: String,
    val beatmapId: Long,
    val mods: String,
    val orderIndex: Int
)

@Entity(tableName = "bookmarked_messages")
data class BookmarkedMessageEntity(
    @PrimaryKey
    val messageId: String,
    val channelId: String,
    val channelName: String,
    val senderName: String,
    val content: String,
    val messageTimestamp: Long,
    val bookmarkedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "friend_score_events")
data class FriendScoreEventEntity(
    @PrimaryKey
    val scoreId: Long,
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val beatmapId: Long,
    val beatmapTitle: String,
    val beatmapVersion: String,
    val starRating: Double,
    val rank: String,
    val pp: Double,
    val accuracy: Double,
    val modsCsv: String,
    val createdAtIso: String,
    val recordedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "profile_history",
    primaryKeys = ["dayKey", "mode"]
)
data class ProfileHistoryEntity(
    val dayKey: String,
    val mode: String,
    val globalRank: Int,
    val countryRank: Int,
    val pp: Double,
    val accuracy: Double,
    val playCount: Int,
    val recordedAt: Long = System.currentTimeMillis()
)
