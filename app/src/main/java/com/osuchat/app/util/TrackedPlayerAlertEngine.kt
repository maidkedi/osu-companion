package com.osuchat.app.util

import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.domain.model.OsuScoreItem
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface CustomMilestoneHit {
    val targetKey: String

    data class PpTargetHit(
        val targetPp: Double,
        val currentPp: Double,
        override val targetKey: String
    ) : CustomMilestoneHit

    data class RankTargetHit(
        val targetRank: Int,
        val currentRank: Int,
        override val targetKey: String
    ) : CustomMilestoneHit
}

data class MilestoneEvaluationResult(
    val updatedConfig: TrackedPlayerAlertConfig,
    val hits: List<CustomMilestoneHit>
)

data class TopPlayCandidate(
    val scoreId: Long,
    val createdAtEpochMs: Long? = null,
    val beatmapTitle: String = "",
    val beatmapArtist: String = "",
    val beatmapVersion: String = "",
    val mods: List<String> = emptyList(),
    val pp: Double? = null,
    val accuracy: Double = 0.0
)

data class NewTopPlayMatch(
    val rankPosition: Int,
    val candidate: TopPlayCandidate
)

data class TopPlaysEvaluationResult(
    val updatedConfig: TrackedPlayerAlertConfig,
    val newPlays: List<NewTopPlayMatch>
)

/**
 * Pure detection engine for custom tracked-player milestones and configurable Top-N play alerts.
 */
object TrackedPlayerAlertEngine {

    fun effectiveTopPlayLimit(config: TrackedPlayerAlertConfig, defaultLimit: Int): Int {
        val raw = config.topPlayLimitOverride ?: defaultLimit
        return TrackedPlayerAlertConfig.clampTopPlayLimit(raw)
    }

    fun resolveActiveTrackedModes(
        userId: Long,
        defaultPlaymode: String?,
        configsMap: Map<String, TrackedPlayerAlertConfig>
    ): List<Pair<String, TrackedPlayerAlertConfig>> {
        val prefix = "$userId:"
        val explicitEntries = configsMap.entries.filter { it.key.startsWith(prefix) }
        if (explicitEntries.isNotEmpty()) {
            val activeByMode = LinkedHashMap<String, TrackedPlayerAlertConfig>()
            for ((key, rawCfg) in explicitEntries) {
                val cfg = rawCfg.sanitized()
                if (cfg.hasAnyTrackingEnabled()) {
                    val mode = TrackedPlayerAlertConfig.normalizeMode(key.substringAfter(':'))
                    activeByMode[mode] = cfg
                }
            }
            return activeByMode.entries.map { it.key to it.value }
        }
        val fallbackMode = TrackedPlayerAlertConfig.normalizeMode(defaultPlaymode)
        val fallbackConfig = TrackedPlayerAlertConfig()
        return if (fallbackConfig.hasAnyTrackingEnabled()) {
            listOf(fallbackMode to fallbackConfig)
        } else {
            emptyList()
        }
    }

    fun resolveModeProfileMetrics(
        requestedMode: String,
        fetchedPp: Double?,
        fetchedRank: Int?,
        defaultPlaymode: String?,
        fallbackPp: Double?,
        fallbackRank: Int?
    ): Pair<Double?, Int?> {
        val normRequested = TrackedPlayerAlertConfig.normalizeMode(requestedMode)
        val normDefault = TrackedPlayerAlertConfig.normalizeMode(defaultPlaymode)
        val canFallback = normRequested == normDefault
        val resolvedPp = fetchedPp ?: if (canFallback) fallbackPp else null
        val resolvedRank = fetchedRank ?: if (canFallback) fallbackRank else null
        return resolvedPp to resolvedRank
    }

    /**
     * Evaluates ONLY explicit user-configured PP and Global Rank targets.
     * Emits zero automatic milestones. First sync silently records baseline values.
     */
    fun evaluateCustomMilestones(
        config: TrackedPlayerAlertConfig,
        currentPp: Double?,
        currentRank: Int?,
        consumeHits: Boolean = true
    ): MilestoneEvaluationResult {
        val clean = config.sanitized()
        val validPp = currentPp?.takeIf { it.isFinite() && it >= 0.0 }
        val validRank = currentRank?.takeIf { it > 0 }

        val hits = mutableListOf<CustomMilestoneHit>()
        val nextNotified = clean.notifiedTargetKeys.toMutableSet()

        val ppEnabled = clean.isPpEnabled()
        val rankEnabled = clean.isRankEnabled()

        var hasPpHit = false
        val prevPp = clean.baselinePp
        if (ppEnabled && validPp != null && prevPp != null) {
            for (targetPp in clean.customPpTargets) {
                val key = TrackedPlayerAlertConfig.ppTargetKey(targetPp)
                if (key !in nextNotified && prevPp < targetPp && validPp >= targetPp) {
                    hasPpHit = true
                    if (consumeHits) {
                        nextNotified.add(key)
                    }
                    hits.add(
                        CustomMilestoneHit.PpTargetHit(
                            targetPp = targetPp,
                            currentPp = validPp,
                            targetKey = key
                        )
                    )
                }
            }
        }

        var hasRankHit = false
        val prevRank = clean.baselineRank
        if (rankEnabled && validRank != null && prevRank != null && prevRank > 0) {
            for (targetRank in clean.customRankTargets) {
                val key = TrackedPlayerAlertConfig.rankTargetKey(targetRank)
                if (key !in nextNotified && prevRank > targetRank && validRank <= targetRank) {
                    hasRankHit = true
                    if (consumeHits) {
                        nextNotified.add(key)
                    }
                    hits.add(
                        CustomMilestoneHit.RankTargetHit(
                            targetRank = targetRank,
                            currentRank = validRank,
                            targetKey = key
                        )
                    )
                }
            }
        }

        val updatedConfig = clean.copy(
            baselinePp = if (!consumeHits && hasPpHit) clean.baselinePp else (validPp ?: clean.baselinePp),
            baselineRank = if (!consumeHits && hasRankHit) clean.baselineRank else (validRank ?: clean.baselineRank),
            notifiedTargetKeys = if (consumeHits) nextNotified else clean.notifiedTargetKeys
        )
        return MilestoneEvaluationResult(updatedConfig = updatedConfig, hits = hits)
    }

    /**
     * Evaluates new Top-N plays within [effectiveTopPlayLimit].
     * Suppresses retroactive spam on first sync and when the Top-N limit expands.
     */
    fun evaluateNewTopPlays(
        config: TrackedPlayerAlertConfig,
        defaultLimit: Int,
        bestScores: List<TopPlayCandidate>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): TopPlaysEvaluationResult {
        val clean = config.sanitized()
        if (!clean.isTopPlaysEnabled()) {
            return TopPlaysEvaluationResult(
                updatedConfig = clean,
                newPlays = emptyList()
            )
        }
        val effectiveLimit = effectiveTopPlayLimit(clean, defaultLimit)
        val window = bestScores.filter { it.scoreId > 0L }.take(effectiveLimit)
        if (window.isEmpty()) {
            return TopPlaysEvaluationResult(
                updatedConfig = clean.copy(
                    lastSyncedTopLimit = effectiveLimit,
                    lastSyncEpochMs = nowEpochMs.coerceAtLeast(clean.lastSyncEpochMs)
                ),
                newPlays = emptyList()
            )
        }

        val isFirstSync = clean.seenBestScoreIds.isEmpty()
        val isLimitExpanded = !isFirstSync && effectiveLimit > clean.lastSyncedTopLimit
        val newMatches = if (isFirstSync) {
            emptyList()
        } else {
            window.mapIndexedNotNull { index, candidate ->
                if (candidate.scoreId in clean.seenBestScoreIds) {
                    null
                } else {
                    val rankPos = index + 1
                    val allowed = if (isLimitExpanded) {
                        val enteredPreviousSlots =
                            clean.lastSyncedTopLimit > 0 && rankPos <= clean.lastSyncedTopLimit
                        val isGenuinelyNewer =
                            candidate.createdAtEpochMs != null &&
                                clean.lastSyncEpochMs > 0L &&
                                candidate.createdAtEpochMs > clean.lastSyncEpochMs
                        enteredPreviousSlots || isGenuinelyNewer
                    } else {
                        true
                    }
                    if (allowed) NewTopPlayMatch(rankPosition = rankPos, candidate = candidate) else null
                }
            }
        }

        val updatedSeenIds = LinkedHashSet<Long>(TrackedPlayerAlertConfig.MAX_SEEN_SCORE_IDS)
        for (item in window) {
            if (updatedSeenIds.size >= TrackedPlayerAlertConfig.MAX_SEEN_SCORE_IDS) break
            updatedSeenIds.add(item.scoreId)
        }
        for (oldId in clean.seenBestScoreIds) {
            if (updatedSeenIds.size >= TrackedPlayerAlertConfig.MAX_SEEN_SCORE_IDS) break
            updatedSeenIds.add(oldId)
        }

        val updatedConfig = clean.copy(
            lastSyncedTopLimit = effectiveLimit,
            seenBestScoreIds = updatedSeenIds,
            lastSyncEpochMs = nowEpochMs.coerceAtLeast(clean.lastSyncEpochMs)
        )
        return TopPlaysEvaluationResult(updatedConfig = updatedConfig, newPlays = newMatches)
    }

    fun parseIsoEpochMs(isoTimestamp: String?): Long? {
        val raw = isoTimestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    fun formatModeDisplay(mode: String?): String = when (TrackedPlayerAlertConfig.normalizeMode(mode)) {
        "taiko" -> "osu!taiko"
        "fruits" -> "osu!catch"
        "mania" -> "osu!mania"
        else -> "osu!"
    }

    fun formatPpValue(pp: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        val roundedTwo = TrackedPlayerAlertConfig.normalizePpTarget(pp)
        return if (abs(roundedTwo - roundedTwo.roundToInt()) < 0.005) {
            fmt.format(roundedTwo.roundToInt())
        } else {
            fmt.maximumFractionDigits = 2
            fmt.minimumFractionDigits = 0
            fmt.format(roundedTwo)
        }
    }

    fun formatMilestoneNotification(
        username: String,
        mode: String?,
        hits: List<CustomMilestoneHit>,
        currentPp: Double?,
        currentRank: Int?
    ): Pair<String, String> {
        val cleanName = username.trim().ifBlank { "Player" }
        val intFmt = NumberFormat.getIntegerInstance(Locale.US)
        val title = if (hits.size == 1) {
            when (val hit = hits.first()) {
                is CustomMilestoneHit.PpTargetHit ->
                    "$cleanName hit custom target ${formatPpValue(hit.targetPp)} pp"
                is CustomMilestoneHit.RankTargetHit ->
                    "$cleanName reached custom target #${intFmt.format(hit.targetRank)}"
            }
        } else {
            "$cleanName reached ${hits.size} custom targets"
        }

        val details = buildList {
            add(formatModeDisplay(mode))
            currentPp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                add("${intFmt.format(it.roundToInt())} pp")
            }
            currentRank?.takeIf { it > 0 }?.let {
                add("#${intFmt.format(it)}")
            }
        }.joinToString(" · ")
        return title to details
    }

    fun formatTopPlaysNotification(
        username: String,
        newPlays: List<NewTopPlayMatch>
    ): Pair<String, String> {
        val cleanName = username.trim().ifBlank { "Player" }
        if (newPlays.isEmpty()) return cleanName to ""
        if (newPlays.size == 1) {
            val match = newPlays.first()
            val c = match.candidate
            val title = "$cleanName set a new #${match.rankPosition} top play"
            val modsPart = if (c.mods.isNotEmpty()) " +${c.mods.joinToString("")}" else ""
            val mapName = if (c.beatmapArtist.isNotBlank() && c.beatmapTitle.isNotBlank()) {
                "${c.beatmapArtist} - ${c.beatmapTitle}"
            } else {
                c.beatmapTitle.ifBlank { "Beatmap" }
            }
            val diffPart = if (c.beatmapVersion.isNotBlank()) " [${c.beatmapVersion}]" else ""
            val accPercent = if (c.accuracy <= 1.0) c.accuracy * 100.0 else c.accuracy
            val accFormatted = String.format(Locale.US, "%.2f%%", accPercent)
            val ppPart = c.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                "${NumberFormat.getIntegerInstance(Locale.US).format(it.roundToInt())} pp"
            }
            val metrics = listOfNotNull(ppPart, accFormatted).joinToString(" · ")
            val body = "$mapName$diffPart$modsPart · $metrics"
            return title to body
        }

        val sorted = newPlays.sortedBy { it.rankPosition }
        val lead = sorted.first()
        val title = "$cleanName set ${sorted.size} new top plays"
        val leadPp = lead.candidate.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            " · ${NumberFormat.getIntegerInstance(Locale.US).format(it.roundToInt())} pp"
        }.orEmpty()
        val body = "Best: #${lead.rankPosition} ${lead.candidate.beatmapTitle.ifBlank { "Beatmap" }}$leadPp"
        return title to body
    }

    fun buildConfigBadgeLabels(
        config: TrackedPlayerAlertConfig?,
        defaultLimit: Int
    ): List<String> {
        val clean = config?.sanitized()
        val effectiveLimit = clean?.let { effectiveTopPlayLimit(it, defaultLimit) }
            ?: TrackedPlayerAlertConfig.clampTopPlayLimit(defaultLimit)
        val badges = mutableListOf<String>()
        if (clean == null || clean.isTopPlaysEnabled()) {
            badges.add("Top $effectiveLimit")
        }
        val intFmt = NumberFormat.getIntegerInstance(Locale.US)
        if (clean != null && clean.isPpEnabled()) {
            clean.customPpTargets.forEach { targetPp ->
                badges.add("${formatPpValue(targetPp)} pp")
            }
        }
        if (clean != null && clean.isRankEnabled()) {
            clean.customRankTargets.forEach { targetRank ->
                badges.add("#${intFmt.format(targetRank)}")
            }
        }
        return badges
    }

    fun formatTrackingChipLabel(
        config: TrackedPlayerAlertConfig?,
        defaultLimit: Int
    ): String {
        val badges = buildConfigBadgeLabels(config, defaultLimit)
        return when {
            badges.isEmpty() -> "Tracking active"
            badges.size == 1 -> "Tracking ${badges.first()}"
            badges.size == 2 -> "Tracking ${badges[0]} · ${badges[1]}"
            else -> "Tracking ${badges[0]} · ${badges[1]} +${badges.size - 2}"
        }
    }

    fun OsuScoreItem.toTopPlayCandidate(): TopPlayCandidate = TopPlayCandidate(
        scoreId = id,
        createdAtEpochMs = parseIsoEpochMs(createdAt),
        beatmapTitle = beatmapTitle,
        beatmapArtist = beatmapArtist,
        beatmapVersion = beatmapVersion,
        mods = mods,
        pp = pp,
        accuracy = accuracy
    )
}
