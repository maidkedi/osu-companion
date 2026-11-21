package com.osuchat.app.util

import android.content.Intent
import android.net.Uri

sealed interface ExternalOsuTarget {
    data class BeatmapTarget(
        val beatmapId: Long?,
        val beatmapsetId: Long?
    ) : ExternalOsuTarget

    data class UserTarget(
        val userIdOrName: String
    ) : ExternalOsuTarget

    data class MultiplayerTarget(
        val matchId: Long
    ) : ExternalOsuTarget

    data class UnrecognizedText(
        val text: String
    ) : ExternalOsuTarget
}

object OsuIntentRouter {

    private val MP_URL_REGEX = Regex(
        """(?:^|(?<![A-Za-z0-9.-]))(?:https?://)?(?:osu|old)\.ppy\.sh/(?:mp|community/matches)/(\d+)(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val USER_URL_ANY_REGEX = Regex(
        """(?:^|(?<![A-Za-z0-9.-]))(?:https?://)?(?:osu|old)\.ppy\.sh/(?:users|u)/([^\s/?#<>\]\[)"']+)(?:/(?:osu|taiko|fruits|mania))?""",
        RegexOption.IGNORE_CASE
    )

    fun parseText(rawText: String): ExternalOsuTarget {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return ExternalOsuTarget.UnrecognizedText("")
        }

        val beatmapLinks = BeatmapUrlParser.extractBeatmapLinks(trimmed)
        if (beatmapLinks.isNotEmpty()) {
            val first = beatmapLinks.first()
            return ExternalOsuTarget.BeatmapTarget(
                beatmapId = first.beatmapId,
                beatmapsetId = first.beatmapsetId
            )
        }

        val mpMatch = MP_URL_REGEX.find(trimmed)
        val matchId = mpMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (matchId != null && matchId > 0L) {
            return ExternalOsuTarget.MultiplayerTarget(matchId)
        }

        val userLinks = BeatmapUrlParser.extractUserLinks(trimmed)
        if (userLinks.isNotEmpty()) {
            return ExternalOsuTarget.UserTarget(userLinks.first().userId.toString())
        }

        val userMatch = USER_URL_ANY_REGEX.find(trimmed)
        val rawSegment = userMatch?.groupValues?.getOrNull(1)
        if (!rawSegment.isNullOrBlank()) {
            val decoded = Uri.decode(rawSegment)
                .trim()
                .trimEnd('.', ',', ';', ')', ']')
            if (decoded.isNotEmpty()) {
                return ExternalOsuTarget.UserTarget(decoded)
            }
        }

        return ExternalOsuTarget.UnrecognizedText(trimmed)
    }

    fun parseIntent(intent: Intent?): ExternalOsuTarget? {
        if (intent == null) return null
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return null
        }

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/", ignoreCase = true) != true) {
                    return null
                }
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                parseText(sharedText.orEmpty())
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return null
                val scheme = data.scheme?.lowercase() ?: return null
                val host = data.host?.lowercase() ?: return null
                if ((scheme == "https" || scheme == "http") &&
                    (host == "osu.ppy.sh" || host == "old.ppy.sh")
                ) {
                    parseText(data.toString())
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
