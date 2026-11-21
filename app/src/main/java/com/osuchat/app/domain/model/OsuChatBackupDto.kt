package com.osuchat.app.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class OsuChatBackupDto(
    @SerializedName("schemaVersion") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerializedName("exportedAt") val exportedAt: Long = 0L,
    @SerializedName("appVersionName") val appVersionName: String = "",
    @SerializedName("mappools") val mappools: List<BackupMappoolDto> = emptyList(),
    @SerializedName("savedBeatmaps") val savedBeatmaps: List<BackupSavedBeatmapDto> = emptyList(),
    @SerializedName("userAliases") val userAliases: List<BackupUserAliasDto> = emptyList(),
    @SerializedName("trackedFriends") val trackedFriends: List<BackupTrackedFriendDto> = emptyList(),
    @SerializedName("bookmarkedMessages") val bookmarkedMessages: List<BackupBookmarkedMessageDto> = emptyList(),
    @SerializedName("profileHistory") val profileHistory: List<BackupProfileHistoryDto> = emptyList(),
    @SerializedName("preferences") val preferences: BackupPreferencesDto = BackupPreferencesDto()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Immutable
data class BackupMappoolDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("createdAt") val createdAt: Long = 0L,
    @SerializedName("slots") val slots: List<BackupMappoolSlotDto> = emptyList()
)

@Immutable
data class BackupMappoolSlotDto(
    @SerializedName("slotTag") val slotTag: String = "",
    @SerializedName("beatmapId") val beatmapId: Long = 0L,
    @SerializedName("mods") val mods: String = "NF",
    @SerializedName("orderIndex") val orderIndex: Int = 0
)

@Immutable
data class BackupSavedBeatmapDto(
    @SerializedName("beatmapId") val beatmapId: Long = 0L,
    @SerializedName("beatmapsetId") val beatmapsetId: Long = 0L,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("creator") val creator: String = "",
    @SerializedName("version") val version: String = "",
    @SerializedName("starRating") val starRating: Double = 0.0,
    @SerializedName("bpm") val bpm: Double = 0.0,
    @SerializedName("mode") val mode: String = "osu",
    @SerializedName("coverUrl") val coverUrl: String = "",
    @SerializedName("previewAudioUrl") val previewAudioUrl: String = "",
    @SerializedName("savedAt") val savedAt: Long = 0L,
    @SerializedName("tagsCsv") val tagsCsv: String = "",
    @SerializedName("totalLengthSeconds") val totalLengthSeconds: Int = 0
)

@Immutable
data class BackupUserAliasDto(
    @SerializedName("normalizedUsername") val normalizedUsername: String = "",
    @SerializedName("originalUsername") val originalUsername: String = "",
    @SerializedName("userId") val userId: Long? = null,
    @SerializedName("customNickname") val customNickname: String = "",
    @SerializedName("updatedAt") val updatedAt: Long = 0L,
    @SerializedName("note") val note: String = ""
)

@Immutable
data class BackupTrackedFriendDto(
    @SerializedName("normalizedUsername") val normalizedUsername: String = "",
    @SerializedName("userId") val userId: Long = 0L,
    @SerializedName("username") val username: String = "",
    @SerializedName("avatarUrl") val avatarUrl: String = "",
    @SerializedName("topScoreIdsCsv") val topScoreIdsCsv: String = "",
    @SerializedName("lastCheckedAt") val lastCheckedAt: Long = 0L,
    @SerializedName("addedAt") val addedAt: Long = 0L,
    @SerializedName("notifyOnline") val notifyOnline: Boolean = false
)

@Immutable
data class BackupBookmarkedMessageDto(
    @SerializedName("messageId") val messageId: String = "",
    @SerializedName("channelId") val channelId: String = "",
    @SerializedName("channelName") val channelName: String = "",
    @SerializedName("senderName") val senderName: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("messageTimestamp") val messageTimestamp: Long = 0L,
    @SerializedName("bookmarkedAt") val bookmarkedAt: Long = 0L
)

@Immutable
data class BackupProfileHistoryDto(
    @SerializedName("dayKey") val dayKey: String = "",
    @SerializedName("mode") val mode: String = "osu",
    @SerializedName("globalRank") val globalRank: Int = 0,
    @SerializedName("countryRank") val countryRank: Int = 0,
    @SerializedName("pp") val pp: Double = 0.0,
    @SerializedName("accuracy") val accuracy: Double = 0.0,
    @SerializedName("playCount") val playCount: Int = 0,
    @SerializedName("recordedAt") val recordedAt: Long = 0L
)

@Immutable
data class BackupPreferencesDto(
    @SerializedName("autoJoinEnabled") val autoJoinEnabled: Boolean = true,
    @SerializedName("autoJoinChannels") val autoJoinChannels: List<String> = AppSettings.DEFAULT_AUTO_JOIN_CHANNELS,
    @SerializedName("mutedChannels") val mutedChannels: List<String> = emptyList(),
    @SerializedName("highlightKeywords") val highlightKeywords: List<String> = emptyList(),
    @SerializedName("notifyDm") val notifyDm: Boolean = true,
    @SerializedName("notifyKeywords") val notifyKeywords: Boolean = true,
    @SerializedName("keepAliveService") val keepAliveService: Boolean = true,
    @SerializedName("autoConnectBoot") val autoConnectBoot: Boolean = false,
    @SerializedName("themeMode") val themeMode: String = ThemeMode.SYSTEM.name,
    @SerializedName("widgetTheme") val widgetTheme: String = WidgetTheme.NOTHING_OS.name,
    @SerializedName("widgetTranslucent") val widgetTranslucent: Boolean = false,
    @SerializedName("refereeMacros") val refereeMacros: List<RefereeMacro> = RefereeMacro.DEFAULT_MACROS,
    @SerializedName("ignoredUsers") val ignoredUsers: List<String>? = null,
    @SerializedName("ignoreDisplayMode") val ignoreDisplayMode: String? = null,
    @SerializedName("dotOnlyChannels") val dotOnlyChannels: List<String>? = null,
    @SerializedName("matchSetupTemplates") val matchSetupTemplates: List<MatchSetupTemplate>? = null,
    @SerializedName("wallpaperPreset") val wallpaperPreset: Int? = null,
    @SerializedName("wallpaperDim") val wallpaperDim: Float? = null,
    @SerializedName("wallpaperBlur") val wallpaperBlur: Float? = null,
    @SerializedName("wallpaperScale") val wallpaperScale: Float? = null,
    @SerializedName("wallpaperBiasX") val wallpaperBiasX: Float? = null,
    @SerializedName("wallpaperBiasY") val wallpaperBiasY: Float? = null
)

@Immutable
data class BackupSummary(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersionName: String,
    val mappoolsCount: Int,
    val savedBeatmapsCount: Int,
    val userAliasesCount: Int,
    val trackedFriendsCount: Int,
    val bookmarkedMessagesCount: Int,
    val profileHistoryCount: Int,
    val refereeMacrosCount: Int,
    val matchSetupTemplatesCount: Int = 0,
    val ignoredUsersCount: Int = 0
)

@Immutable
data class ParsedBackup(
    val dto: OsuChatBackupDto,
    val summary: BackupSummary
)
