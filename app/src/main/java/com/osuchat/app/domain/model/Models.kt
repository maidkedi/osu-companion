package com.osuchat.app.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import java.util.Locale

enum class AuthMode {
    NONE,
    OAUTH_V2,
    IRC
}

enum class ConnectionStatus {
    OFFLINE,
    CONNECTING,
    ONLINE,
    RECONNECTING,
    ERROR
}

enum class ChannelType {
    PUBLIC,
    DIRECT
}

enum class DeliveryStatus {
    QUEUED,
    SENDING,
    SENT,
    READ,
    FAILED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

enum class WidgetTheme(val label: String) {
    NOTHING_OS("Nothing OS"),
    MATERIAL_YOU("Material You"),
    OSU_DARK("osu! Dark")
}

@Immutable
enum class ChannelNotificationMode(val label: String) {
    ALL("All Messages"),
    MENTIONS_ONLY("Mentions Only"),
    MUTED("Muted")
}

@Immutable
data class BeatmapLinkRef(
    val url: String,
    val beatmapId: Long? = null,
    val beatmapsetId: Long? = null,
    val startIndex: Int = 0
)

@Immutable
data class UserLinkRef(
    val url: String,
    val userId: Long,
    val startIndex: Int = 0
)

@Immutable
data class MpLinkRef(
    val url: String,
    val matchId: Long,
    val startIndex: Int = 0
)

@Immutable
sealed interface ChatInlineEmbed {
    val startIndex: Int

    @Immutable
    data class Beatmap(val ref: BeatmapLinkRef) : ChatInlineEmbed {
        override val startIndex: Int get() = ref.startIndex
    }

    @Immutable
    data class User(val ref: UserLinkRef) : ChatInlineEmbed {
        override val startIndex: Int get() = ref.startIndex
    }

    @Immutable
    data class MpMatch(val ref: MpLinkRef) : ChatInlineEmbed {
        override val startIndex: Int get() = ref.startIndex
    }
}

@Immutable
data class ChatChannel(
    val id: String,
    val name: String,
    val description: String = "",
    val type: ChannelType,
    val unreadCount: Int = 0,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val lastSenderName: String? = null,
    val targetUserId: Long? = null,
    val avatarUrl: String? = null,
    val isJoined: Boolean = true,
    val customAlias: String? = null,
    val notificationMode: ChannelNotificationMode = if (
        type == ChannelType.DIRECT ||
        OfficialOsuChannels.isMultiplayerRoom(name) ||
        OfficialOsuChannels.isMultiplayerRoom(id)
    ) {
        ChannelNotificationMode.ALL
    } else {
        ChannelNotificationMode.MENTIONS_ONLY
    },
    val isPinned: Boolean = false
) {
    val displayTitle: String
        get() = if (type == ChannelType.DIRECT && !customAlias.isNullOrBlank()) {
            "$customAlias ($name)"
        } else {
            name
        }
}

fun ChatChannel.matchesNormalizedChannelSet(normalizedSet: Set<String>): Boolean {
    if (normalizedSet.isEmpty()) return false
    val normId = id.trim().lowercase(Locale.ROOT)
    val normName = name.trim().lowercase(Locale.ROOT)
    if (normId in normalizedSet || normName in normalizedSet) return true
    if (type == ChannelType.PUBLIC) {
        val bareName = normName.removePrefix("#")
        val bareId = normId.removePrefix("#")
        if (bareName.isNotEmpty() && (bareName in normalizedSet || "#$bareName" in normalizedSet)) return true
        if (bareId.isNotEmpty() && (bareId in normalizedSet || "#$bareId" in normalizedSet)) return true
    }
    return false
}

@Immutable
data class ChatMessage(
    val id: String,
    val channelId: String,
    val senderId: Long = 0L,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val senderAlias: String? = null,
    val content: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isAction: Boolean = false,
    val isHighlighted: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val beatmapUrls: List<BeatmapLinkRef> = emptyList(),
    val userUrls: List<UserLinkRef> = emptyList(),
    val inlineEmbeds: List<ChatInlineEmbed> = emptyList(),
    val imageUrls: List<String> = emptyList()
) {
    val senderDisplayName: String
        get() = if (!senderAlias.isNullOrBlank()) {
            "$senderAlias ($senderName)"
        } else {
            senderName
        }
}

@Immutable
data class BeatmapMetadata(
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
    val hp: Double = 0.0,
    val drainLengthSeconds: Int = 0,
    val maxCombo: Int? = null,
    val status: String = "ranked",
    val playCount: Int? = null,
    val favouriteCount: Int? = null,
    val coverUrl: String,
    val previewAudioUrl: String,
    val mode: String = "osu"
)

@Immutable
enum class FriendRelationStatus {
    NONE,
    FRIEND,
    MUTUAL
}

@Immutable
data class PlayerProfile(
    val userId: Long,
    val username: String,
    val customAlias: String? = null,
    val customNote: String? = null,
    val avatarUrl: String,
    val coverUrl: String? = null,
    val countryCode: String = "XX",
    val countryName: String? = null,
    val globalRank: Int? = null,
    val countryRank: Int? = null,
    val pp: Double? = null,
    val accuracy: Double? = null,
    val playCount: Int? = null,
    val isOnline: Boolean = false,
    val currentAction: String? = null,
    val isTrackedForPp: Boolean = false,
    val notifyOnline: Boolean = false,
    val friendStatus: FriendRelationStatus = FriendRelationStatus.NONE,
    val mode: String = "osu",
    val userAchievements: List<OsuUserMedalUnlock> = emptyList(),
    val rankHistory: List<Int> = emptyList()
) {
    val displayName: String
        get() = if (!customAlias.isNullOrBlank()) {
            "$customAlias ($username)"
        } else {
            username
        }
}

typealias SelfProfile = PlayerProfile

@Immutable
data class UserAlias(
    val normalizedUsername: String,
    val originalUsername: String,
    val userId: Long? = null,
    val customNickname: String,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
data class WallpaperConfig(
    val presetId: Int = 0,
    val customImageUri: String? = null,
    val dimAmount: Float = 0.35f,
    val blurRadiusDp: Float = 0f,
    val scale: Float = 1.0f,
    val biasX: Float = 0.0f,
    val biasY: Float = 0.0f
) {
    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
        const val MIN_BIAS = -1.0f
        const val MAX_BIAS = 1.0f
    }
}

@Immutable
data class ChatUserColorInfo(
    val profileColourHex: String? = null,
    val profileHue: Int? = null,
    val isSupporter: Boolean = false
)

@Immutable
data class AppSettings(
    val authMode: AuthMode = AuthMode.NONE,
    val username: String = "",
    val ircUsername: String = "",
    val userId: Long = 0L,
    val avatarUrl: String? = null,
    val hasIrcPassword: Boolean = false,
    val customClientId: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val globalWallpaper: WallpaperConfig = WallpaperConfig(),
    val highlightKeywords: List<String> = emptyList(),
    val keepAliveService: Boolean = true,
    val autoConnectOnBoot: Boolean = false,
    val notifyDirectMessages: Boolean = true,
    val notifyKeywordMentions: Boolean = true,
    val widgetTheme: WidgetTheme = WidgetTheme.NOTHING_OS,
    val widgetTranslucent: Boolean = false,
    val autoJoinEnabled: Boolean = true,
    val autoJoinChannels: List<String> = DEFAULT_AUTO_JOIN_CHANNELS,
    val isExplicitlyDisconnected: Boolean = false,
    val ignoredUsers: Set<String> = emptySet(),
    val ignoreDisplayMode: String = "HIDE"
) {
    companion object {
        val DEFAULT_AUTO_JOIN_CHANNELS: List<String> = listOf("#turkish", "#osu", "#lobby", "#announce")
    }
}

@Immutable
data class AudioQueueTrack(
    val beatmapId: Long = 0L,
    val beatmapsetId: Long,
    val title: String = "",
    val artist: String = "",
    val version: String = "",
    val creator: String = "",
    val starRating: Double = 0.0,
    val bpm: Double = 0.0,
    val totalLengthSeconds: Int = 0,
    val coverUrl: String = "",
    val previewAudioUrl: String = "",
    val mode: String = "osu"
)

@Immutable
data class AudioPlaybackState(
    val beatmapsetId: Long? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val currentTrack: AudioQueueTrack? = null,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val autoAdvance: Boolean = true
)

@Immutable
data class FriendContact(
    val userId: Long,
    val username: String,
    val customAlias: String? = null,
    val avatarUrl: String,
    val isOnline: Boolean = false,
    val isMutual: Boolean = false,
    val statusText: String = "Offline",
    val lastVisit: String? = null,
    val pp: Double? = null,
    val globalRank: Int? = null,
    val isTrackedForPp: Boolean = false,
    val notifyOnline: Boolean = false,
    val countryCode: String = "XX",
    val accuracy: Double? = null,
    val playCount: Int? = null,
    val mode: String = "osu"
) {
    val displayName: String
        get() = if (!customAlias.isNullOrBlank()) "$customAlias ($username)" else username

    val shortName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: username
}

@Immutable
data class OsuScoreItem(
    val id: Long,
    val userId: Long,
    val username: String,
    val rank: String,
    val pp: Double?,
    val accuracy: Double,
    val maxCombo: Int,
    val mods: List<String> = emptyList(),
    val score: Long = 0L,
    val passed: Boolean = true,
    val createdAt: String? = null,
    val beatmapId: Long? = null,
    val beatmapsetId: Long? = null,
    val beatmapTitle: String = "",
    val beatmapArtist: String = "",
    val beatmapVersion: String = "",
    val starRating: Double = 0.0,
    val coverUrl: String = "",
    val mode: String = "osu",
    val userAvatarUrl: String? = null,
    val userCountryCode: String = "XX",
    val countMiss: Int = 0,
    val beatmapMaxCombo: Int? = null,
    val beatmapLengthSeconds: Int = 0,
    val count300: Int = 0,
    val count100: Int = 0,
    val count50: Int = 0,
    val bpm: Double = 0.0
) {
    val gradeLabel: String
        get() = when (rank.uppercase()) {
            "XH", "X" -> "SS"
            "SH" -> "S"
            else -> rank.uppercase()
        }

    val modsLabel: String
        get() = if (mods.isEmpty()) "" else "+${mods.joinToString("")}"
}

@Immutable
data class BeatmapSetSearchResult(
    val beatmapsetId: Long,
    val primaryBeatmapId: Long,
    val title: String,
    val artist: String,
    val creator: String,
    val status: String,
    val bpm: Double,
    val playCount: Int? = null,
    val favouriteCount: Int? = null,
    val coverUrl: String,
    val previewAudioUrl: String,
    val difficulties: List<BeatmapMetadata>
)

@Immutable
data class BeatmapSearchFilter(
    val query: String = "",
    val mode: Int? = null,
    val status: String? = null,
    val sort: String = "relevance",
    val sortDescending: Boolean = true,
    val genreId: Int? = null,
    val languageId: Int? = null,
    val hasVideo: Boolean = false,
    val hasStoryboard: Boolean = false,
    val recommendedDifficulty: Boolean = false
) {
    val apiSortParam: String?
        get() {
            val cleanSort = sort.trim().lowercase().ifBlank { "relevance" }
            if (cleanSort == "relevance" && sortDescending) return null
            val dir = if (sortDescending) "desc" else "asc"
            return "${cleanSort}_$dir"
        }

    val apiExtrasParam: String?
        get() = when {
            hasVideo && hasStoryboard -> "video.storyboard"
            hasVideo -> "video"
            hasStoryboard -> "storyboard"
            else -> null
        }

    val apiGeneralParam: String?
        get() = if (recommendedDifficulty) "recommended" else null

    val activeFilterCount: Int
        get() = listOfNotNull(
            mode,
            status?.takeIf { it.isNotBlank() && !it.equals("any", ignoreCase = true) },
            sort.takeIf { it != "relevance" || !sortDescending },
            genreId,
            languageId,
            true.takeIf { hasVideo },
            true.takeIf { hasStoryboard },
            true.takeIf { recommendedDifficulty }
        ).size
}

enum class MpSlotStatus {
    EMPTY,
    NOT_READY,
    READY,
    NO_MAP,
    PLAYING
}

enum class MpTeam {
    NONE,
    BLUE,
    RED
}

@Immutable
data class MpSlot(
    val slotNumber: Int,
    val username: String? = null,
    val userId: Long? = null,
    val status: MpSlotStatus = MpSlotStatus.EMPTY,
    val team: MpTeam = MpTeam.NONE,
    val isHost: Boolean = false,
    val mods: String = ""
)

@Immutable
data class MpGameScoreSummary(
    val username: String,
    val userId: Long,
    val score: Long,
    val accuracy: Double,
    val maxCombo: Int,
    val mods: List<String>,
    val team: String?,
    val passed: Boolean,
    val rank: String?
)

@Immutable
data class MpCompletedGame(
    val gameId: Long,
    val beatmapTitle: String,
    val beatmapId: Long?,
    val mode: String,
    val mods: List<String>,
    val redTeamTotal: Long,
    val blueTeamTotal: Long,
    val scores: List<MpGameScoreSummary>
)

@Immutable
data class MpMatchHistory(
    val matchId: Long,
    val roomName: String,
    val games: List<MpCompletedGame>
)

@Immutable
data class MpFinishedRoundEvent(
    val finishKey: String,
    val beatmapId: Long?,
    val beatmapTitle: String?,
    val teamMode: String,
    val redTotalScore: Long,
    val blueTotalScore: Long
)

@Immutable
data class MpLobbyState(
    val channelId: String,
    val matchId: Long?,
    val roomName: String,
    val historyUrl: String?,
    val beatmapId: Long? = null,
    val beatmapTitle: String? = null,
    val beatmapUrl: String? = null,
    val teamMode: String = "HeadToHead",
    val winCondition: String = "Score",
    val activeMods: String = "None",
    val isFreeMod: Boolean = false,
    val isMatchInProgress: Boolean = false,
    val isLocked: Boolean = false,
    val isClosed: Boolean = false,
    val roomSize: Int = 16,
    val hostUsername: String? = null,
    val slots: List<MpSlot> = (1..16).map { MpSlot(it) },
    val finishedRounds: List<MpFinishedRoundEvent> = emptyList()
)

object OfficialOsuChannels {
    val PRIORITY: List<String> = listOf(
        "#osu",
        "#announce",
        "#help",
        "#mapping",
        "#modreqs",
        "#osumania",
        "#taiko",
        "#lobby"
    )

    val REMAINING_ALPHABETICAL: List<String> = listOf(
        "#arabic",
        "#bulgarian",
        "#cantonese",
        "#chinese",
        "#ctb",
        "#czechoslovak",
        "#dutch",
        "#english",
        "#estonian",
        "#filipino",
        "#finnish",
        "#german",
        "#greek",
        "#hebrew",
        "#hungarian",
        "#indonesian",
        "#italian",
        "#japanese",
        "#korean",
        "#latvian",
        "#lazer",
        "#malaysian",
        "#polish",
        "#portuguese",
        "#romanian",
        "#russian",
        "#skandinavian",
        "#spanish",
        "#taiwanese",
        "#thai",
        "#turkish",
        "#ukrainian",
        "#uzbek",
        "#videogames",
        "#vietnamese"
    )

    val ALL_ORDERED: List<String> = PRIORITY + REMAINING_ALPHABETICAL
    val ALL_OFFICIAL_SET: Set<String> = ALL_ORDERED.toSet()

    fun isOfficial(channelName: String): Boolean {
        return channelName.trim().lowercase() in ALL_OFFICIAL_SET
    }

    fun isMultiplayerRoom(channelName: String): Boolean {
        val norm = channelName.trim().lowercase()
        return norm.startsWith("#mp_") && norm.removePrefix("#mp_").toLongOrNull() != null
    }
}

@Immutable
data class SavedBeatmapItem(
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
    val tagsCsv: String = "",
    val totalLengthSeconds: Int = 0
) {
    val tags: List<String>
        get() = parseTagsCsv(tagsCsv)

    companion object {
        val PRESET_TAGS: List<String> = listOf(
            "Stream",
            "Aim",
            "Tech",
            "Tourney",
            "Farm",
            "Alt",
            "Speed"
        )

        fun normalizeTag(raw: String): String {
            val cleaned = raw.replace(",", " ")
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(24)
            if (cleaned.isEmpty()) return ""
            val presetMatch = PRESET_TAGS.firstOrNull { it.equals(cleaned, ignoreCase = true) }
            return presetMatch ?: cleaned
        }

        fun parseTagsCsv(csv: String): List<String> {
            if (csv.isBlank()) return emptyList()
            val seen = HashSet<String>()
            val result = mutableListOf<String>()
            for (part in csv.split(',')) {
                val normalized = normalizeTag(part)
                if (normalized.isNotEmpty() && seen.add(normalized.lowercase(java.util.Locale.US))) {
                    result.add(normalized)
                }
            }
            return result
        }

        fun formatTagsCsv(tags: Iterable<String>): String {
            val seen = HashSet<String>()
            val normalizedList = mutableListOf<String>()
            for (tag in tags) {
                val normalized = normalizeTag(tag)
                if (normalized.isNotEmpty() && seen.add(normalized.lowercase(java.util.Locale.US))) {
                    normalizedList.add(normalized)
                }
            }
            return normalizedList.joinToString(",")
        }
    }
}

enum class PlayLaterSortOption(val label: String) {
    SAVED_DATE("Saved Date"),
    STAR_RATING("Star Rating (SR)"),
    BPM("BPM"),
    DURATION("Duration")
}

@Immutable
sealed interface OszDownloadState {
    data object Idle : OszDownloadState
    data class Downloading(val progressPercent: Int? = null) : OszDownloadState
    data class Downloaded(val uri: String, val fileName: String) : OszDownloadState
    data class Error(val message: String) : OszDownloadState
}

@Immutable
data class RankPpConversionResult(
    val mode: String,
    val targetRank: Int,
    val estimatedPp: Double,
    val nearestPlayer: PlayerProfile? = null,
    val isExactLeaderboardMatch: Boolean = false
)

@Immutable
data class OsuNewsItem(
    val id: Long,
    val title: String,
    val author: String,
    val publishedAt: String,
    val preview: String,
    val imageUrl: String?,
    val url: String
)

@Immutable
data class OsuSpotlightItem(
    val id: Long,
    val name: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val participantCount: Int?,
    val modeSpecific: Boolean,
    val url: String
)

@Immutable
data class NewsAndSpotlightsFeed(
    val news: List<OsuNewsItem> = emptyList(),
    val spotlights: List<OsuSpotlightItem> = emptyList()
)

enum class MpPoolSlotState {
    AVAILABLE,
    PLAYED,
    BANNED,
    PICKED_RED,
    PICKED_BLUE,
    BANNED_RED,
    BANNED_BLUE,
    PLAYED_TB
}

enum class MpPickBanMode(val label: String) {
    RED_PICK("Red Pick"),
    BLUE_PICK("Blue Pick"),
    RED_BAN("Red Ban"),
    BLUE_BAN("Blue Ban"),
    TB_NEUTRAL("TB / Neutral")
}

@Immutable
data class SeriesRoundRecord(
    val slotTag: String = "",
    val beatmapLabel: String = "",
    val pickedBy: MpTeam = MpTeam.NONE,
    val winner: MpTeam = MpTeam.NONE,
    val redTotalScore: Long = 0L,
    val blueTotalScore: Long = 0L,
    val isWarmup: Boolean = false,
    val roundKey: String = ""
)

@Immutable
data class MatchSeriesState(
    val channelId: String = "",
    val bestOf: Int = 9,
    val redTeamName: String = "Red Team",
    val blueTeamName: String = "Blue Team",
    val isCustomTeamNames: Boolean = false,
    val redScore: Int = 0,
    val blueScore: Int = 0,
    val rounds: List<SeriesRoundRecord> = emptyList(),
    val autoScoreTeamVs: Boolean = true,
    val ignoreWarmups: Boolean = true,
    val isWarmupActive: Boolean = false,
    val selectedPoolId: Long? = null,
    val pickBanMode: MpPickBanMode = MpPickBanMode.RED_PICK,
    val slotStates: Map<Long, MpPoolSlotState> = emptyMap(),
    val activePickedSlotId: Long? = null,
    val activePickedSlotTag: String? = null,
    val activePickedBeatmapId: Long? = null,
    val activePickedBy: MpTeam = MpTeam.NONE,
    val processedRoundKeys: Set<String> = emptySet()
) {
    val firstTo: Int
        get() = (bestOf / 2) + 1

    val winnerTeam: MpTeam
        get() = when {
            redScore >= firstTo && redScore > blueScore -> MpTeam.RED
            blueScore >= firstTo && blueScore > redScore -> MpTeam.BLUE
            else -> MpTeam.NONE
        }

    val isRedMatchPoint: Boolean
        get() = winnerTeam == MpTeam.NONE && redScore == firstTo - 1

    val isBlueMatchPoint: Boolean
        get() = winnerTeam == MpTeam.NONE && blueScore == firstTo - 1

    companion object {
        val BEST_OF_PRESETS: List<Int> = listOf(5, 7, 9, 11, 13)
    }
}

@Immutable
data class RefereeMacro(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("message") val message: String = ""
) {
    companion object {
        val DEFAULT_MACROS: List<RefereeMacro> = listOf(
            RefereeMacro(
                id = "warmup",
                label = "Warmup",
                message = "Welcome to {red} vs {blue}! Please pick your warmup map."
            ),
            RefereeMacro(
                id = "roll",
                label = "Roll",
                message = "Captains of {red} and {blue}, please !roll for pick/ban order."
            ),
            RefereeMacro(
                id = "pick_120s",
                label = "Pick 120s",
                message = "Score: {red} {score} {blue} — you have 120s to lock in your next pick."
            ),
            RefereeMacro(
                id = "ban",
                label = "Ban",
                message = "Please declare your next ban in chat ({red} vs {blue})."
            ),
            RefereeMacro(
                id = "tiebreaker",
                label = "Tiebreaker",
                message = "Tiebreaker time! Series is tied {score} between {red} and {blue}. Good luck!"
            ),
            RefereeMacro(
                id = "gg",
                label = "GG",
                message = "GGWP! Final score: {red} {score} {blue} | MP Link: {mp}"
            )
        )
    }
}

@Immutable
data class MappoolSlot(
    val id: Long = 0L,
    val poolId: Long = 0L,
    val slotTag: String,
    val beatmapId: Long,
    val mods: String,
    val orderIndex: Int = 0
)

@Immutable
data class Mappool(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val slots: List<MappoolSlot> = emptyList()
)

@Immutable
data class MappoolSlotTransferDto(
    @SerializedName("slotTag") val slotTag: String = "",
    @SerializedName("beatmapId") val beatmapId: Long = 0L,
    @SerializedName("mods") val mods: String = "NF"
)

@Immutable
data class MappoolTransferDto(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("name") val name: String = "",
    @SerializedName("slots") val slots: List<MappoolSlotTransferDto> = emptyList()
)

@Immutable
data class SeasonalBackgroundItem(
    val url: String,
    val artistUsername: String
) {
    val localFilePrefix: String
        get() = "seasonal_${url.hashCode().toUInt().toString(16)}"
}

enum class ChatLogExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    PLAIN_TEXT("Plain Text (.txt)", "txt", "text/plain"),
    MARKDOWN("Markdown (.md)", "md", "text/markdown")
}

@Immutable
data class BookmarkedMessageItem(
    val messageId: String,
    val channelId: String,
    val channelName: String,
    val senderName: String,
    val content: String,
    val messageTimestamp: Long,
    val bookmarkedAt: Long
)

@Immutable
data class FriendScoreEventItem(
    val scoreId: Long,
    val userId: Long,
    val username: String,
    val customAlias: String? = null,
    val avatarUrl: String,
    val beatmapId: Long,
    val beatmapTitle: String,
    val beatmapVersion: String,
    val starRating: Double,
    val rank: String,
    val pp: Double,
    val accuracy: Double,
    val mods: List<String> = emptyList(),
    val createdAtIso: String,
    val recordedAt: Long
) {
    val displayName: String
        get() = if (!customAlias.isNullOrBlank()) "$customAlias ($username)" else username
}

@Immutable
enum class ProfileHistorySource {
    LOCAL_SNAPSHOT,
    OSU_TRACK,
    OSU_API_RANK_HISTORY,
    CURRENT_LIVE
}

@Immutable
enum class ProfileHistoryMetric {
    PP,
    RANK
}

@Immutable
data class OsuTrackPoint(
    val timestampMs: Long,
    val pp: Double,
    val globalRank: Int,
    val accuracy: Double = 0.0,
    val playCount: Int = 0
)

@Immutable
data class OsuTrackPeak(
    val bestGlobalRank: Int?,
    val bestRankTimestamp: String? = null,
    val bestAccuracy: Double? = null,
    val bestAccTimestamp: String? = null
)

@Immutable
data class ProfileHistoryPoint(
    val dayKey: String,
    val mode: String,
    val globalRank: Int,
    val countryRank: Int = 0,
    val pp: Double,
    val accuracy: Double = 0.0,
    val playCount: Int = 0,
    val recordedAt: Long,
    val source: ProfileHistorySource = ProfileHistorySource.LOCAL_SNAPSHOT
) {
    val timestampMs: Long get() = recordedAt
}

@Immutable
data class GlobalMessageSearchHit(
    val messageId: String,
    val channelId: String,
    val channelName: String,
    val isDirectChannel: Boolean,
    val senderName: String,
    val content: String,
    val timestamp: Long
)

@Immutable
data class MatchAnalysisPlayerScore(
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val countryCode: String,
    val slot: Int?,
    val team: MpTeam,
    val score: Long,
    val accuracy: Double,
    val maxCombo: Int,
    val mods: List<String>,
    val passed: Boolean,
    val perfect: Boolean,
    val rank: String?
)

@Immutable
data class MatchAnalysisGame(
    val gameId: Long,
    val gameIndex: Int,
    val beatmapId: Long?,
    val beatmapsetId: Long?,
    val beatmapTitle: String,
    val beatmapArtist: String,
    val beatmapVersion: String,
    val beatmapCreator: String = "",
    val starRating: Double,
    val bpm: Double = 0.0,
    val totalLengthSeconds: Int = 0,
    val ar: Double = 0.0,
    val cs: Double = 0.0,
    val od: Double = 0.0,
    val hp: Double = 0.0,
    val coverUrl: String,
    val previewAudioUrl: String = "",
    val mode: String,
    val scoringType: String,
    val teamType: String,
    val mods: List<String>,
    val startTime: String?,
    val endTime: String?,
    val scores: List<MatchAnalysisPlayerScore>
) {
    val isTeamMatch: Boolean
        get() = teamType.equals("team-vs", ignoreCase = true) ||
            teamType.equals("tag-team-vs", ignoreCase = true)

    val formattedMapLabel: String
        get() {
            val base = if (beatmapArtist.isNotBlank()) "$beatmapArtist - $beatmapTitle" else beatmapTitle
            return if (beatmapVersion.isNotBlank()) "$base [$beatmapVersion]" else base
        }

    val scoringTypeLabel: String
        get() = when (scoringType.trim().lowercase()) {
            "scorev2" -> "ScoreV2"
            "accuracy" -> "Accuracy"
            "combo" -> "Combo"
            else -> "Score"
        }
}

@Immutable
data class MatchAnalysisReport(
    val matchId: Long,
    val roomName: String,
    val startTime: String?,
    val endTime: String?,
    val isLive: Boolean,
    val games: List<MatchAnalysisGame>
)

@Immutable
data class MatchAnalyzedGameItem(
    val game: MatchAnalysisGame,
    val isWarmup: Boolean,
    val redTeamTotal: Long,
    val blueTeamTotal: Long,
    val scoreDelta: Long,
    val winnerTeam: MpTeam,
    val winnerUsername: String?,
    val sortedScores: List<MatchAnalysisPlayerScore>
)

@Immutable
data class MatchPlayerStanding(
    val rank: Int,
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val countryCode: String,
    val primaryTeam: MpTeam,
    val mapsPlayed: Int,
    val averageScore: Long,
    val totalScore: Long,
    val highestScore: Long,
    val averageAccuracy: Double,
    val matchCost: Double,
    val isMvp: Boolean
)

@Immutable
data class MatchComputedAnalytics(
    val matchId: Long,
    val roomName: String,
    val startTime: String?,
    val endTime: String?,
    val isLive: Boolean,
    val isTeamSeries: Boolean,
    val redSeriesWins: Int,
    val blueSeriesWins: Int,
    val countedMapCount: Int,
    val warmupMapCount: Int,
    val games: List<MatchAnalyzedGameItem>,
    val playerStandings: List<MatchPlayerStanding>,
    val mvpPlayer: MatchPlayerStanding?
)

enum class BeatmapRecommendationPreset(val label: String, val subtitle: String) {
    FOR_YOU("For You", "Balanced"),
    FARM_PP("Farm / PP", "Short high-PP"),
    STREAM_SPEED("Stream / Speed", "175+ BPM"),
    CONSISTENCY("Consistency", "3:30+ marathon")
}

@Immutable
data class PlayerRecommendationProfile(
    val avgStarRating: Double = 5.0,
    val avgLengthSeconds: Int = 135,
    val primaryModBucket: String = "NM",
    val modeInt: Int = 0,
    val modeName: String = "osu",
    val isFallback: Boolean = true
)

@Immutable
data class RecommendedBeatmapItem(
    val beatmapSet: BeatmapSetSearchResult,
    val recommendedDiff: BeatmapMetadata,
    val recommendedDiffIndex: Int,
    val reasonTag: String,
    val matchScore: Double
)

enum class FriendsRankingSort(val label: String) {
    PP("PP"),
    GLOBAL_RANK("Global Rank"),
    ACCURACY("Accuracy")
}

@Immutable
data class FriendLeaderboardEntry(
    val rankPosition: Int,
    val userId: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val countryCode: String,
    val globalRank: Int?,
    val pp: Double?,
    val accuracy: Double?,
    val isOnline: Boolean,
    val isMutual: Boolean,
    val isSelf: Boolean
)

@Immutable
data class OsuUserMedalUnlock(
    @SerializedName("achievement_id") val achievementId: Int = 0,
    @SerializedName("achieved_at") val achievedAt: String = ""
)

@Immutable
data class OsuMedalCatalogItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("grouping") val grouping: String = "Skill & Dedication",
    @SerializedName("icon_slug") val iconSlug: String = "",
    @SerializedName("instructions") val instructions: String? = null,
    @SerializedName("mode") val mode: String? = null
) {
    val iconUrl: String
        get() = if (iconSlug.isNotBlank()) {
            "https://assets.ppy.sh/medals/web/${iconSlug}@2x.png"
        } else {
            ""
        }
}

@Immutable
data class DailySessionSummary(
    val mode: String = "osu",
    val totalPlays: Int = 0,
    val passCount: Int = 0,
    val failCount: Int = 0,
    val passRatePercent: Double = 0.0,
    val averageAccuracy: Double = 0.0,
    val estimatedPlaytimeSeconds: Int = 0,
    val highestPpPlay: OsuScoreItem? = null,
    val rankDelta: Int? = null,
    val ppDelta: Double? = null,
    val recentSessionPlays: List<OsuScoreItem> = emptyList()
)

@Immutable
data class ChannelHistoryState(
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val oldestApiMessageId: Long? = null,
    val initialSynced: Boolean = false
)
