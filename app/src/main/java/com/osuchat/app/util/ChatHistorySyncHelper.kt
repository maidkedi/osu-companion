package com.osuchat.app.util

import com.osuchat.app.data.local.MessageEntity
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

/**
 * Pure helpers for osu! API v2 chat history timestamp parsing and zero-duplicate Room ingestion.
 */
object ChatHistorySyncHelper {
    const val HISTORY_PAGE_SIZE = 50
    const val DEDUP_WINDOW_MS = 15_000L
    const val API_MESSAGE_ID_PREFIX = "api_"

    fun apiMessageId(messageId: Long): String = "$API_MESSAGE_ID_PREFIX$messageId"

    fun parseIso8601TimestampMillis(
        raw: String?,
        fallbackMillis: Long = System.currentTimeMillis()
    ): Long {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallbackMillis
        return runCatching {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                Instant.parse(trimmed).toEpochMilli()
            }.getOrElse { fallbackMillis }
        }
    }

    fun filterNewApiMessages(
        candidates: List<MessageEntity>,
        existingInRange: List<MessageEntity>,
        dedupWindowMs: Long = DEDUP_WINDOW_MS
    ): List<MessageEntity> {
        if (candidates.isEmpty()) return emptyList()

        val seenIds = existingInRange.mapTo(HashSet(existingInRange.size + candidates.size)) { it.id }
        val nonApiRows = existingInRange.filterNot { it.id.startsWith(API_MESSAGE_ID_PREFIX) }
        val consumedNonApi = BooleanArray(nonApiRows.size)

        val result = ArrayList<MessageEntity>(candidates.size)
        for (candidate in candidates) {
            val legacyNumericId = candidate.id.removePrefix(API_MESSAGE_ID_PREFIX)
            if (candidate.id in seenIds || (legacyNumericId != candidate.id && legacyNumericId in seenIds)) {
                continue
            }

            val candSenderNorm = BeatmapUrlParser.normalizeOsuNick(candidate.senderName)
            val candContent = candidate.content.trim()

            var matchedIndex = -1
            var bestDelta = Long.MAX_VALUE
            for (i in nonApiRows.indices) {
                if (consumedNonApi[i]) continue
                val existing = nonApiRows[i]
                if (existing.content.trim() != candContent) continue

                val existingSenderNorm = BeatmapUrlParser.normalizeOsuNick(existing.senderName)
                val senderMatches = existingSenderNorm == candSenderNorm ||
                    (existing.isOutgoing && candidate.isOutgoing && existing.senderName.equals("Me", ignoreCase = true))
                if (!senderMatches) continue

                val delta = abs(candidate.timestamp - existing.timestamp)
                if (delta <= dedupWindowMs && delta < bestDelta) {
                    bestDelta = delta
                    matchedIndex = i
                }
            }

            seenIds.add(candidate.id)
            if (matchedIndex >= 0) {
                consumedNonApi[matchedIndex] = true
            } else {
                result.add(candidate)
            }
        }
        return result
    }
}
