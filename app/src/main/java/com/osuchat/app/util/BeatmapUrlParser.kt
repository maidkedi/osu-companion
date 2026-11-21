package com.osuchat.app.util

import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.ChatInlineEmbed
import com.osuchat.app.domain.model.MpLinkRef
import com.osuchat.app.domain.model.UserLinkRef
import java.util.Locale

object BeatmapUrlParser {
    // Matches:
    // https://osu.ppy.sh/beatmapsets/123456#osu/789012
    // https://osu.ppy.sh/beatmapsets/123456
    // https://osu.ppy.sh/b/789012
    // https://osu.ppy.sh/beatmaps/789012
    // https://osu.ppy.sh/s/123456
    // Also supports old.ppy.sh, missing https://, and [url label] osu! IRC links
    private val URL_REGEX = Regex(
        """(?:^|(?<![A-Za-z0-9.-]))(?:https?://)?(?:osu|old)\.ppy\.sh/(?:beatmapsets/(\d+)(?:#[a-z]+/(\d+))?|(?:b|beatmaps)/(\d+)|s/(\d+))(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val USER_URL_REGEX = Regex(
        """(?:^|(?<![A-Za-z0-9.-]))(?:https?://)?(?:osu|old)\.ppy\.sh/(?:users|u)/(\d+)(?:/(?:osu|taiko|fruits|mania))?(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val MP_URL_REGEX = Regex(
        """(?:^|(?<![A-Za-z0-9.-]))(?:https?://)?(?:osu|old)\.ppy\.sh/(?:mp|community/matches)/(\d+)(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val GENERAL_IMAGE_CANDIDATE_REGEX = Regex(
        """(?:https?://|(?:^|(?<![A-Za-z0-9.-]))(?:(?:litter|files)\.catbox\.moe|(?:[a-z0-9-]+\.)?uguu\.se|tmpfiles\.org/dl|0x0\.st)/)[^\s<>\[\]()'"]+""",
        RegexOption.IGNORE_CASE
    )

    private val IMAGE_EXTENSION_REGEX = Regex(
        """^.+\.(?:jpe?g|png|webp|gif)$""",
        RegexOption.IGNORE_CASE
    )

    private val IMAGE_HOST_ALLOWLIST = setOf(
        "litter.catbox.moe",
        "files.catbox.moe",
        "uguu.se",
        "0x0.st"
    )

    private val IMAGE_UPLOAD_API_PATHS = setOf(
        "/upload",
        "/user/api.php",
        "/resources/internals/api.php"
    )

    private val NON_IMAGE_EXTENSIONS = setOf(
        ".txt", ".log", ".json", ".zip", ".rar", ".7z", ".tar", ".gz",
        ".mp3", ".ogg", ".wav", ".flac", ".mp4", ".webm", ".mov", ".pdf",
        ".osz", ".osk", ".osr", ".osu", ".exe", ".apk"
    )

    fun extractFirst(text: String): BeatmapLinkRef? = extractBeatmapLinks(text).firstOrNull()

    fun extractBeatmapLinks(text: String): List<BeatmapLinkRef> {
        if (text.isBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        val results = mutableListOf<BeatmapLinkRef>()

        for (match in URL_REGEX.findAll(text)) {
            val rawUrl = match.value
            val normalizedUrl = if (rawUrl.startsWith("http", ignoreCase = true)) {
                rawUrl
            } else {
                "https://$rawUrl"
            }

            val setFromNew = match.groupValues[1].toLongOrNull()
            val mapFromNew = match.groupValues[2].toLongOrNull()
            val mapFromB = match.groupValues[3].toLongOrNull()
            val setFromS = match.groupValues[4].toLongOrNull()

            val beatmapId = mapFromNew ?: mapFromB
            val beatmapsetId = setFromNew ?: setFromS

            if (beatmapId != null || beatmapsetId != null) {
                val key = "${beatmapId ?: 0}:${beatmapsetId ?: 0}"
                if (seen.add(key)) {
                    results.add(
                        BeatmapLinkRef(
                            url = normalizedUrl,
                            beatmapId = beatmapId,
                            beatmapsetId = beatmapsetId,
                            startIndex = match.range.first
                        )
                    )
                }
            }
        }
        return results
    }

    fun extractUserLinks(text: String): List<UserLinkRef> {
        if (text.isBlank()) return emptyList()
        val seen = mutableSetOf<Long>()
        val results = mutableListOf<UserLinkRef>()

        for (match in USER_URL_REGEX.findAll(text)) {
            val userId = match.groupValues[1].toLongOrNull() ?: continue
            if (seen.add(userId)) {
                val rawUrl = match.value
                val normalizedUrl = if (rawUrl.startsWith("http", ignoreCase = true)) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
                results.add(
                    UserLinkRef(
                        url = normalizedUrl,
                        userId = userId,
                        startIndex = match.range.first
                    )
                )
            }
        }
        return results
    }

    fun extractMpLinks(text: String): List<MpLinkRef> {
        if (text.isBlank()) return emptyList()
        val seen = mutableSetOf<Long>()
        val results = mutableListOf<MpLinkRef>()
        for (match in MP_URL_REGEX.findAll(text)) {
            val matchId = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: continue
            if (seen.add(matchId)) {
                val rawUrl = match.value
                val normalizedUrl = if (rawUrl.startsWith("http", ignoreCase = true)) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
                results.add(
                    MpLinkRef(
                        url = normalizedUrl,
                        matchId = matchId,
                        startIndex = match.range.first
                    )
                )
            }
        }
        return results
    }

    fun extractOrderedEmbeds(text: String): List<ChatInlineEmbed> {
        if (text.isBlank()) return emptyList()
        return buildList {
            extractBeatmapLinks(text).forEach { add(ChatInlineEmbed.Beatmap(it)) }
            extractUserLinks(text).forEach { add(ChatInlineEmbed.User(it)) }
            extractMpLinks(text).forEach { add(ChatInlineEmbed.MpMatch(it)) }
        }.sortedBy { it.startIndex }
    }

    fun extractImageUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val seen = linkedSetOf<String>()
        val results = mutableListOf<String>()
        val sanitizedText = text.replace("\r", "").replace("\n", " ")

        for (match in GENERAL_IMAGE_CANDIDATE_REGEX.findAll(sanitizedText)) {
            val cleaned = match.value
                .trimStart('[', '(', '<', '"', '\'', ' ')
                .trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>', '"', '\'', ' ')
            if (cleaned.isBlank()) continue
            val normalized = if (cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned
            } else {
                "https://$cleaned"
            }
            if (!isDirectImageUrl(normalized)) continue
            if (seen.add(normalized)) {
                results.add(normalized)
            }
        }
        return results
    }

    private fun isDirectImageUrl(url: String): Boolean {
        val withoutScheme = url.substringAfter("://", "")
        if (withoutScheme.isBlank()) return false
        val slashIdx = withoutScheme.indexOf('/')
        if (slashIdx <= 0) return false
        val host = withoutScheme.substring(0, slashIdx).substringBefore(':').lowercase()
        val pathAndQuery = withoutScheme.substring(slashIdx)
        val path = pathAndQuery.substringBefore('?').substringBefore('#')
        if (path.length <= 1) return false

        val lowerPath = path.lowercase()
        if ((host == "tmpfiles.org" || host == "www.tmpfiles.org") && !lowerPath.startsWith("/dl/")) {
            return false
        }
        if (IMAGE_EXTENSION_REGEX.matches(path)) {
            return true
        }
        if (isAllowlistedImageHost(host, lowerPath)) {
            return path.trim('/').isNotEmpty() && NON_IMAGE_EXTENSIONS.none { lowerPath.endsWith(it) }
        }
        return false
    }

    private fun isAllowlistedImageHost(host: String, lowerPath: String): Boolean {
        if (lowerPath in IMAGE_UPLOAD_API_PATHS) return false
        if (host in IMAGE_HOST_ALLOWLIST || host.endsWith(".uguu.se")) return true
        return (host == "tmpfiles.org" || host == "www.tmpfiles.org") &&
            lowerPath.startsWith("/dl/") &&
            lowerPath.removePrefix("/dl/").trim('/').isNotEmpty()
    }

    fun formatDuration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return "%d:%02d".format(mins, secs)
    }

    fun normalizeOsuNick(nick: String): String {
        return nick.trim().replace(' ', '_').lowercase(Locale.ROOT)
    }

    fun normalizeUsername(username: String): String = normalizeOsuNick(username)

    fun isSelfOsuSender(
        senderName: String,
        senderId: Long = 0L,
        currentUserId: Long = 0L,
        currentUsername: String = "",
        currentIrcUsername: String = ""
    ): Boolean {
        if (senderId > 0L && currentUserId > 0L && senderId == currentUserId) {
            return true
        }
        val normSender = normalizeOsuNick(senderName)
        if (normSender.isEmpty()) return false
        val normCurrent = normalizeOsuNick(currentUsername)
        if (normCurrent.isNotEmpty() && normSender == normCurrent) {
            return true
        }
        val normIrc = normalizeOsuNick(currentIrcUsername)
        return normIrc.isNotEmpty() && normSender == normIrc
    }
}
