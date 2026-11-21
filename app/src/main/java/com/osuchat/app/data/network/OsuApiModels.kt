package com.osuchat.app.data.network

import com.google.gson.annotations.SerializedName

data class OAuthTokenResponse(
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null
)

data class OsuOAuthErrorDto(
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("hint") val hint: String? = null
)

data class OsuUserDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("cover") val cover: OsuUserCoverDto? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("country") val country: OsuCountryDto? = null,
    @SerializedName("is_online") val isOnline: Boolean? = null,
    @SerializedName("is_mutual") val isMutual: Boolean? = null,
    @SerializedName("last_visit") val lastVisit: String? = null,
    @SerializedName("playmode") val playmode: String? = null,
    @SerializedName("profile_colour") val profileColour: String? = null,
    @SerializedName("profile_hue") val profileHue: Int? = null,
    @SerializedName("is_supporter") val isSupporter: Boolean = false,
    @SerializedName("statistics") val statistics: OsuUserStatsDto? = null,
    @SerializedName("user_achievements") val userAchievements: List<OsuUserAchievementDto>? = null,
    @SerializedName("rank_history") val rankHistory: OsuRankHistoryDto? = null
)

data class OsuUserAchievementDto(
    @SerializedName("achieved_at") val achievedAt: String? = null,
    @SerializedName("achievement_id") val achievementId: Int = 0
)

data class OsuRankHistoryDto(
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("data") val data: List<Int>? = null
)

data class OsuUserCoverDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("custom_url") val customUrl: String? = null
)

data class OsuCountryDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("name") val name: String? = null
)

data class OsuUserStatsDto(
    @SerializedName("global_rank") val globalRank: Int? = null,
    @SerializedName("country_rank") val countryRank: Int? = null,
    @SerializedName("pp") val pp: Double? = null,
    @SerializedName("hit_accuracy") val hitAccuracy: Double? = null,
    @SerializedName("play_count") val playCount: Int? = null,
    @SerializedName("rank") val rank: OsuRankFallbackDto? = null
)

data class OsuRankFallbackDto(
    @SerializedName("country") val country: Int? = null
)

data class ChatChannelDto(
    @SerializedName("channel_id") val channelId: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String? = null, // PUBLIC, PRIVATE, PM, ANNOUNCE, etc.
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("last_message_id") val lastMessageId: Long? = null,
    @SerializedName("last_read_id") val lastReadId: Long? = null,
    @SerializedName("users") val users: List<Long>? = null,
    @SerializedName("current_user_attributes") val currentUserAttributes: CurrentUserAttributesDto? = null,
    @SerializedName("recent_messages") val recentMessages: List<ChatMessageDto>? = null
)

data class OsuCreateChannelRequestDto(
    @SerializedName("type") val type: String = "PM",
    @SerializedName("target_id") val targetId: Long = 0L
)

data class CurrentUserAttributesDto(
    @SerializedName("can_message") val canMessage: Boolean? = null,
    @SerializedName("last_read_id") val lastReadId: Long? = null
)

data class ChatMessageDto(
    @SerializedName("message_id") val messageId: Long = 0L,
    @SerializedName("sender_id") val senderId: Long = 0L,
    @SerializedName("channel_id") val channelId: Long = 0L,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("is_action") val isAction: Boolean = false,
    @SerializedName("sender") val sender: ChatSenderDto? = null
)

data class ChatSenderDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("is_online") val isOnline: Boolean? = null,
    @SerializedName("profile_colour") val profileColour: String? = null,
    @SerializedName("profile_hue") val profileHue: Int? = null,
    @SerializedName("is_supporter") val isSupporter: Boolean = false
)

data class ChatUpdatesResponse(
    @SerializedName("presence") val presence: List<ChatChannelDto>? = null,
    @SerializedName("messages") val messages: List<ChatMessageDto>? = null
)

data class SendChannelMessageRequest(
    @SerializedName("message") val message: String = "",
    @SerializedName("is_action") val isAction: Boolean = false
)

data class CreatePmRequest(
    @SerializedName("target_id") val targetId: Long = 0L,
    @SerializedName("message") val message: String = "",
    @SerializedName("is_action") val isAction: Boolean = false
)

data class CreatePmResponse(
    @SerializedName("channel") val channel: ChatChannelDto? = null,
    @SerializedName("message") val message: ChatMessageDto? = null,
    @SerializedName("new_channel_id") val newChannelId: Long? = null
)

data class OsuBeatmapDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("beatmapset_id") val beatmapsetId: Long = 0L,
    @SerializedName("difficulty_rating") val difficultyRating: Double = 0.0,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("total_length") val totalLength: Int = 0,
    @SerializedName("hit_length") val hitLength: Int = 0,
    @SerializedName("version") val version: String? = null,
    @SerializedName("ar") val ar: Double = 0.0,
    @SerializedName("cs") val cs: Double = 0.0,
    @SerializedName("accuracy") val od: Double = 0.0,
    @SerializedName("drain") val hp: Double = 0.0,
    @SerializedName("bpm") val bpm: Double? = null,
    @SerializedName("max_combo") val maxCombo: Int? = null,
    @SerializedName("playcount") val playCount: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("beatmapset") val beatmapset: OsuBeatmapsetSummaryDto? = null
)

data class OsuBeatmapsetSummaryDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("bpm") val bpm: Double = 0.0,
    @SerializedName("play_count") val playCount: Int? = null,
    @SerializedName("favourite_count") val favouriteCount: Int? = null,
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("covers") val covers: OsuBeatmapCoversDto? = null
)

data class OsuBeatmapsetDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("bpm") val bpm: Double = 0.0,
    @SerializedName("play_count") val playCount: Int? = null,
    @SerializedName("favourite_count") val favouriteCount: Int? = null,
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("covers") val covers: OsuBeatmapCoversDto? = null,
    @SerializedName("beatmaps") val beatmaps: List<OsuBeatmapDto>? = null
)

data class OsuBeatmapCoversDto(
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("cover@2x") val cover2x: String? = null,
    @SerializedName("card") val card: String? = null,
    @SerializedName("list") val list: String? = null,
    @SerializedName("slimcover") val slimcover: String? = null
)

data class OsuScoreStatisticsDto(
    @SerializedName("count_300") val count300: Int = 0,
    @SerializedName("count_100") val count100: Int = 0,
    @SerializedName("count_50") val count50: Int = 0,
    @SerializedName("count_miss") val countMiss: Int = 0,
    @SerializedName("count_geki") val countGeki: Int = 0,
    @SerializedName("count_katu") val countKatu: Int = 0
)

data class OsuScoreDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("best_id") val bestId: Long? = null,
    @SerializedName("user_id") val userId: Long = 0L,
    @SerializedName("accuracy") val accuracy: Double = 0.0,
    @SerializedName("mods") val mods: List<String>? = null,
    @SerializedName("score") val score: Long = 0L,
    @SerializedName("total_score") val totalScore: Long? = null,
    @SerializedName("max_combo") val maxCombo: Int = 0,
    @SerializedName("passed") val passed: Boolean = true,
    @SerializedName("pp") val pp: Double? = null,
    @SerializedName("rank") val rank: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("statistics") val statistics: OsuScoreStatisticsDto? = null,
    @SerializedName("beatmap") val beatmap: OsuBeatmapDto? = null,
    @SerializedName("beatmapset") val beatmapset: OsuBeatmapsetSummaryDto? = null,
    @SerializedName("user") val user: OsuUserDto? = null
)

data class OsuBeatmapScoresResponseDto(
    @SerializedName("scores") val scores: List<OsuScoreDto>? = null
)

data class OsuUserBeatmapScoresResponseDto(
    @SerializedName("scores") val scores: List<OsuScoreDto>? = null
)

data class OsuBeatmapsetSearchResponseDto(
    @SerializedName("beatmapsets") val beatmapsets: List<OsuBeatmapsetDto>? = null
)

data class OsuRankingsResponseDto(
    @SerializedName("ranking") val ranking: List<OsuRankingEntryDto>? = null
)

data class OsuRankingEntryDto(
    @SerializedName("global_rank") val globalRank: Int? = null,
    @SerializedName("pp") val pp: Double? = null,
    @SerializedName("hit_accuracy") val hitAccuracy: Double? = null,
    @SerializedName("play_count") val playCount: Int? = null,
    @SerializedName("user") val user: OsuUserDto? = null
)

data class OsuSearchResponseDto(
    @SerializedName("user") val user: OsuSearchUserSectionDto? = null
)

data class OsuSearchUserSectionDto(
    @SerializedName("data") val data: List<OsuUserDto>? = null
)

data class OsuMatchResponseDto(
    @SerializedName("match") val match: OsuMatchHeaderDto? = null,
    @SerializedName("events") val events: List<OsuMatchEventDto>? = null,
    @SerializedName("users") val users: List<OsuUserDto>? = null,
    @SerializedName("first_event_id") val firstEventId: Long? = null,
    @SerializedName("latest_event_id") val latestEventId: Long? = null,
    @SerializedName("current_game_id") val currentGameId: Long? = null
)

data class OsuMatchHeaderDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("name") val name: String? = null
)

typealias OsuMatchInfoDto = OsuMatchHeaderDto

data class OsuMatchEventDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("detail") val detail: OsuMatchEventDetailDto? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("game") val game: OsuMatchGameDto? = null
)

data class OsuMatchEventDetailDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null
)

data class OsuMatchGameDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("beatmap_id") val beatmapId: Long? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("mode_int") val modeInt: Int? = null,
    @SerializedName("scoring_type") val scoringType: String? = null,
    @SerializedName("team_type") val teamType: String? = null,
    @SerializedName("mods") val mods: List<String>? = null,
    @SerializedName("beatmap") val beatmap: OsuBeatmapDto? = null,
    @SerializedName("scores") val scores: List<OsuMatchGameScoreDto>? = null
)

data class OsuMatchGameScoreDto(
    @SerializedName("user_id") val userId: Long = 0L,
    @SerializedName("accuracy") val accuracy: Double = 0.0,
    @SerializedName("mods") val mods: List<String>? = null,
    @SerializedName("score") val score: Long = 0L,
    @SerializedName("max_combo") val maxCombo: Int = 0,
    @SerializedName("passed") val passed: Boolean = true,
    @SerializedName("perfect") val perfect: Int? = null,
    @SerializedName("pp") val pp: Double? = null,
    @SerializedName("rank") val rank: String? = null,
    @SerializedName("statistics") val statistics: OsuScoreStatisticsDto? = null,
    @SerializedName("match") val match: OsuMatchSlotMetaDto? = null
)

typealias OsuMatchScoreDto = OsuMatchGameScoreDto

data class OsuMatchSlotMetaDto(
    @SerializedName("slot") val slot: Int? = null,
    @SerializedName("team") val team: String? = null,
    @SerializedName("pass") val pass: Boolean? = null
)

typealias OsuMatchScoreSlotDto = OsuMatchSlotMetaDto

data class OsuNewsResponseDto(
    @SerializedName("news_posts") val newsPosts: List<OsuNewsPostDto>? = null
)

data class OsuNewsPostDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("first_image") val firstImage: String? = null,
    @SerializedName("preview") val preview: String? = null
)

data class OsuSpotlightsResponseDto(
    @SerializedName("spotlights") val spotlights: List<OsuSpotlightDto>? = null
)

data class OsuSpotlightDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("participant_count") val participantCount: Int? = null,
    @SerializedName("mode_specific") val modeSpecific: Boolean? = null
)

data class OsuSeasonalBackgroundsResponse(
    @SerializedName("ends_at") val endsAt: String? = null,
    @SerializedName("backgrounds") val backgrounds: List<OsuSeasonalBackgroundDto>? = null
)

data class OsuSeasonalBackgroundDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("user") val user: OsuUserDto? = null
)
