package com.osuchat.app.util

import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapRecommendationPreset
import com.osuchat.app.domain.model.BeatmapSearchFilter
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.PlayerRecommendationProfile
import com.osuchat.app.domain.model.RecommendedBeatmapItem
import com.osuchat.app.domain.model.SavedBeatmapItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

object BeatmapRecommendationEngine {

    private data class PresetSpec(
        val srMinOffset: Double,
        val srMaxOffset: Double,
        val srTargetOffset: Double,
        val minLengthSec: Int? = null,
        val maxLengthSec: Int? = null,
        val minBpm: Int? = null,
        val sort: String = "favourites"
    )

    private fun specFor(preset: BeatmapRecommendationPreset, avgLength: Int): PresetSpec = when (preset) {
        BeatmapRecommendationPreset.FOR_YOU -> PresetSpec(
            srMinOffset = -0.45,
            srMaxOffset = 0.45,
            srTargetOffset = 0.0,
            minLengthSec = (avgLength - 45).coerceAtLeast(45),
            maxLengthSec = (avgLength + 60).coerceIn(120, 420),
            sort = "favourites"
        )
        BeatmapRecommendationPreset.FARM_PP -> PresetSpec(
            srMinOffset = 0.15,
            srMaxOffset = 0.65,
            srTargetOffset = 0.35,
            maxLengthSec = 150,
            sort = "plays"
        )
        BeatmapRecommendationPreset.STREAM_SPEED -> PresetSpec(
            srMinOffset = -0.10,
            srMaxOffset = 0.45,
            srTargetOffset = 0.15,
            minLengthSec = 120,
            minBpm = 175,
            sort = "favourites"
        )
        BeatmapRecommendationPreset.CONSISTENCY -> PresetSpec(
            srMinOffset = -0.35,
            srMaxOffset = 0.25,
            srTargetOffset = 0.0,
            minLengthSec = 210,
            sort = "favourites"
        )
    }

    fun modeNameToInt(mode: String?): Int = when (mode?.trim()?.lowercase()) {
        "taiko" -> 1
        "fruits", "catch" -> 2
        "mania" -> 3
        else -> 0
    }

    fun normalizeModeName(mode: String?): String = when (mode?.trim()?.lowercase()) {
        "taiko" -> "taiko"
        "fruits", "catch" -> "fruits"
        "mania" -> "mania"
        else -> "osu"
    }

    fun buildPlayerProfile(
        bestScores: List<OsuScoreItem>,
        fallbackMode: String = "osu"
    ): PlayerRecommendationProfile {
        val normMode = normalizeModeName(bestScores.firstOrNull()?.mode ?: fallbackMode)
        val analytics = RankPpMath.computeTopPlayAnalytics(bestScores)
        val avgSr = analytics?.averageStarRating?.takeIf { it.isFinite() && it > 0.0 }
        if (analytics == null || avgSr == null) {
            return PlayerRecommendationProfile(
                avgStarRating = 5.0,
                avgLengthSeconds = 135,
                primaryModBucket = "NM",
                modeInt = modeNameToInt(normMode),
                modeName = normMode,
                isFallback = true
            )
        }
        return PlayerRecommendationProfile(
            avgStarRating = avgSr.coerceIn(1.5, 11.0),
            avgLengthSeconds = (analytics.averageLengthSeconds ?: 135).coerceIn(45, 360),
            primaryModBucket = analytics.modDistribution.firstOrNull()?.label ?: "NM",
            modeInt = modeNameToInt(normMode),
            modeName = normMode,
            isFallback = false
        )
    }

    fun buildSearchFilter(
        profile: PlayerRecommendationProfile,
        preset: BeatmapRecommendationPreset,
        relaxed: Boolean = false
    ): BeatmapSearchFilter {
        val spec = specFor(preset, profile.avgLengthSeconds)
        val pad = if (relaxed) 0.30 else 0.0
        val minSr = (profile.avgStarRating + spec.srMinOffset - pad).coerceAtLeast(1.0)
        val maxSr = (profile.avgStarRating + spec.srMaxOffset + pad).coerceAtLeast(minSr + 0.35)

        val terms = mutableListOf(
            String.format(Locale.US, "stars>=%.2f", minSr),
            String.format(Locale.US, "stars<=%.2f", maxSr)
        )
        if (!relaxed) {
            spec.minLengthSec?.let { terms += "length>=$it" }
            spec.maxLengthSec?.let { terms += "length<=$it" }
            spec.minBpm?.let { terms += "bpm>=$it" }
        } else {
            // Keep core identity constraint on fallback so Farm stays shorter / Stream stays brisk
            when (preset) {
                BeatmapRecommendationPreset.FARM_PP -> terms += "length<=180"
                BeatmapRecommendationPreset.STREAM_SPEED -> terms += "bpm>=165"
                BeatmapRecommendationPreset.CONSISTENCY -> terms += "length>=180"
                BeatmapRecommendationPreset.FOR_YOU -> Unit
            }
        }

        return BeatmapSearchFilter(
            query = terms.joinToString(" "),
            mode = profile.modeInt,
            status = "ranked",
            sort = spec.sort,
            sortDescending = true
        )
    }

    fun selectRecommendations(
        sets: List<BeatmapSetSearchResult>,
        profile: PlayerRecommendationProfile,
        preset: BeatmapRecommendationPreset,
        topScores: List<OsuScoreItem>,
        savedBeatmaps: List<SavedBeatmapItem>,
        limit: Int = 30
    ): List<RecommendedBeatmapItem> {
        if (sets.isEmpty()) return emptyList()

        val excludedBeatmapIds = HashSet<Long>(topScores.size + savedBeatmaps.size)
        val excludedSetIds = HashSet<Long>(topScores.size + savedBeatmaps.size)
        topScores.forEach { score ->
            score.beatmapId?.takeIf { it > 0L }?.let(excludedBeatmapIds::add)
            score.beatmapsetId?.takeIf { it > 0L }?.let(excludedSetIds::add)
        }
        savedBeatmaps.forEach { saved ->
            if (saved.beatmapId > 0L) excludedBeatmapIds.add(saved.beatmapId)
            if (saved.beatmapsetId > 0L) excludedSetIds.add(saved.beatmapsetId)
        }

        val safeAvgSr = profile.avgStarRating.takeIf { it.isFinite() && it > 0.0 } ?: 5.0
        val safeAvgLength = profile.avgLengthSeconds.coerceIn(1, 3600)
        val spec = specFor(preset, safeAvgLength)
        val targetSr = (safeAvgSr + spec.srTargetOffset).coerceIn(1.0, 11.5)
        val minAllowedSr = (safeAvgSr + spec.srMinOffset - 0.35).coerceAtLeast(0.8)
        val maxAllowedSr = (safeAvgSr + spec.srMaxOffset + 0.35).coerceAtLeast(minAllowedSr + 0.5)

        val candidates = ArrayList<RecommendedBeatmapItem>(sets.size)
        for (set in sets) {
            if (set.beatmapsetId in excludedSetIds) continue
            val finiteDiffs = set.difficulties.withIndex()
                .filter { (_, d) ->
                    d.beatmapId !in excludedBeatmapIds &&
                        d.starRating.isFinite() &&
                        d.starRating >= 0.0
                }
            val modeDiffs = finiteDiffs
                .filter { (_, d) -> d.mode.equals(profile.modeName, ignoreCase = true) }
                .ifEmpty { finiteDiffs }
            if (modeDiffs.isEmpty()) continue

            val bestEntry = modeDiffs.minByOrNull { (_, diff) ->
                val srPenalty = abs(diff.starRating - targetSr)
                val outOfBandPenalty = when {
                    diff.starRating in minAllowedSr..maxAllowedSr -> 0.0
                    else -> 2.0
                }
                val lengthPenalty = when {
                    preset == BeatmapRecommendationPreset.FARM_PP && diff.totalLengthSeconds > 165 -> 1.2
                    preset == BeatmapRecommendationPreset.CONSISTENCY && diff.totalLengthSeconds in 1..179 -> 1.2
                    else -> 0.0
                }
                srPenalty + outOfBandPenalty + lengthPenalty
            } ?: continue

            val diff = bestEntry.value
            val srDist = abs(diff.starRating - targetSr)
            val lengthSec = diff.totalLengthSeconds
            val lenDistNormalized = if (lengthSec > 0) {
                abs(lengthSec - safeAvgLength) / 180.0
            } else {
                0.25
            }
            val safeFavs = (set.favouriteCount ?: 0).coerceAtLeast(0)
            val safePlays = (set.playCount ?: 0).coerceAtLeast(0)
            val popBoost = ln(safeFavs.toDouble() + (safePlays / 100).toDouble() + 2.0) * 0.08
            val rawScore = popBoost - (srDist * 1.6) - (lenDistNormalized * 0.4)
            val matchScore = if (rawScore.isFinite()) rawScore else 0.0

            candidates += RecommendedBeatmapItem(
                beatmapSet = set,
                recommendedDiff = diff,
                recommendedDiffIndex = bestEntry.index,
                reasonTag = buildReasonTag(preset, profile, set, diff),
                matchScore = matchScore
            )
        }

        return candidates
            .sortedByDescending { it.matchScore }
            .take(limit)
    }

    private fun buildReasonTag(
        preset: BeatmapRecommendationPreset,
        profile: PlayerRecommendationProfile,
        set: BeatmapSetSearchResult,
        diff: BeatmapMetadata
    ): String {
        val safeBpm = diff.bpm.takeIf { it.isFinite() && it > 0.0 }
            ?: set.bpm.takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        val bpm = safeBpm.roundToInt()
        val safeAvgSr = profile.avgStarRating.takeIf { it.isFinite() && it > 0.0 } ?: 5.0
        val safeDiffSr = diff.starRating.takeIf { it.isFinite() && it >= 0.0 } ?: safeAvgSr
        val len = formatMinSec(diff.totalLengthSeconds)
        return when (preset) {
            BeatmapRecommendationPreset.FOR_YOU -> if (profile.isFallback) {
                String.format(Locale.US, "Starter pick · %.2f★", safeDiffSr)
            } else {
                String.format(Locale.US, "Matches %.1f★ avg", safeAvgSr)
            }
            BeatmapRecommendationPreset.FARM_PP -> if (len != null) {
                "Short PP window · $len"
            } else {
                String.format(Locale.US, "Stretch +%.2f★", (safeDiffSr - safeAvgSr).coerceAtLeast(0.0))
            }
            BeatmapRecommendationPreset.STREAM_SPEED -> if (bpm > 0) {
                "$bpm BPM stamina"
            } else {
                "Stream / speed pick"
            }
            BeatmapRecommendationPreset.CONSISTENCY -> if (len != null) {
                "$len consistency"
            } else {
                "Endurance marathon"
            }
        }
    }

    fun formatMinSec(seconds: Int): String? {
        if (seconds <= 0) return null
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }
}
