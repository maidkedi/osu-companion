package com.osuchat.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.round

@Immutable
data class TrackedPlayerAlertConfig(
    @SerializedName("custom_pp_targets")
    val customPpTargets: List<Double> = emptyList(),
    @SerializedName("custom_rank_targets")
    val customRankTargets: List<Int> = emptyList(),
    @SerializedName("top_play_limit_override")
    val topPlayLimitOverride: Int? = null,
    @SerializedName("track_top_plays")
    val trackTopPlays: Boolean? = null,
    @SerializedName("track_pp")
    val trackPp: Boolean? = null,
    @SerializedName("track_rank")
    val trackRank: Boolean? = null,
    @SerializedName("baseline_pp")
    val baselinePp: Double? = null,
    @SerializedName("baseline_rank")
    val baselineRank: Int? = null,
    @SerializedName("notified_target_keys")
    val notifiedTargetKeys: Set<String> = emptySet(),
    @SerializedName("last_synced_top_limit")
    val lastSyncedTopLimit: Int = 0,
    @SerializedName("seen_best_score_ids")
    val seenBestScoreIds: Set<Long> = emptySet(),
    @SerializedName("last_sync_epoch_ms")
    val lastSyncEpochMs: Long = 0L
) {
    fun isTopPlaysEnabled(): Boolean = trackTopPlays ?: true

    @Suppress("USELESS_ELVIS")
    fun isPpEnabled(): Boolean = trackPp ?: ((customPpTargets as List<Double>?)?.isNotEmpty() == true)

    @Suppress("USELESS_ELVIS")
    fun isRankEnabled(): Boolean = trackRank ?: ((customRankTargets as List<Int>?)?.isNotEmpty() == true)

    @Suppress("USELESS_ELVIS")
    fun hasAnyTrackingEnabled(): Boolean {
        val safePp = (customPpTargets as List<Double>?) ?: emptyList()
        val safeRank = (customRankTargets as List<Int>?) ?: emptyList()
        return isTopPlaysEnabled() ||
            (isPpEnabled() && safePp.isNotEmpty()) ||
            (isRankEnabled() && safeRank.isNotEmpty())
    }

    @Suppress("USELESS_ELVIS")
    fun sanitized(): TrackedPlayerAlertConfig {
        val safePpTargets = ((customPpTargets as List<Double?>?) ?: emptyList()).filterNotNull()
        val safeRankTargets = ((customRankTargets as List<Int?>?) ?: emptyList()).filterNotNull()
        val safeNotifiedKeys = ((notifiedTargetKeys as Set<String?>?) ?: emptySet()).filterNotNull()
        val safeSeenIds = ((seenBestScoreIds as Set<Long?>?) ?: emptySet()).filterNotNull()
        return TrackedPlayerAlertConfig(
            customPpTargets = normalizePpTargets(safePpTargets),
            customRankTargets = normalizeRankTargets(safeRankTargets),
            topPlayLimitOverride = topPlayLimitOverride?.let(::clampTopPlayLimit),
            trackTopPlays = trackTopPlays,
            trackPp = trackPp,
            trackRank = trackRank,
            baselinePp = baselinePp?.takeIf { it.isFinite() && it >= 0.0 },
            baselineRank = baselineRank?.takeIf { it > 0 },
            notifiedTargetKeys = safeNotifiedKeys.mapNotNull { key ->
                key.trim().takeIf { it.isNotEmpty() }
            }.toSet(),
            lastSyncedTopLimit = lastSyncedTopLimit.coerceIn(0, MAX_TOP_PLAY_LIMIT),
            seenBestScoreIds = safeSeenIds.filter { it > 0L }.take(MAX_SEEN_SCORE_IDS).toSet(),
            lastSyncEpochMs = lastSyncEpochMs.coerceAtLeast(0L)
        )
    }

    companion object {
        const val MIN_TOP_PLAY_LIMIT = 1
        const val MAX_TOP_PLAY_LIMIT = 100
        const val DEFAULT_TOP_PLAY_LIMIT = 5
        const val DEFAULT_INTERVAL_MINUTES = 30
        const val MAX_SEEN_SCORE_IDS = 120
        val INTERVAL_OPTIONS_MINUTES: List<Int> = listOf(15, 30, 60)

        private val VALID_MODES = setOf("osu", "taiko", "fruits", "mania")

        fun normalizeMode(mode: String?): String {
            val clean = mode?.trim()?.lowercase(Locale.US)
            return if (clean != null && clean in VALID_MODES) clean else "osu"
        }

        fun configKey(userId: Long, mode: String?): String =
            "$userId:${normalizeMode(mode)}"

        fun clampTopPlayLimit(limit: Int): Int =
            limit.coerceIn(MIN_TOP_PLAY_LIMIT, MAX_TOP_PLAY_LIMIT)

        fun clampIntervalMinutes(minutes: Int): Int =
            if (minutes in INTERVAL_OPTIONS_MINUTES) minutes else DEFAULT_INTERVAL_MINUTES

        fun normalizePpTarget(value: Double): Double =
            round(value * 100.0) / 100.0

        fun normalizePpTargets(targets: List<Double>): List<Double> =
            targets
                .filter { it.isFinite() && it > 0.0 }
                .map(::normalizePpTarget)
                .distinct()
                .sorted()

        fun normalizeRankTargets(targets: List<Int>): List<Int> =
            targets
                .filter { it > 0 }
                .distinct()
                .sortedDescending()

        fun ppTargetKey(targetPp: Double): String =
            "pp:${normalizePpTarget(targetPp)}"

        fun rankTargetKey(targetRank: Int): String =
            "rank:$targetRank"
    }
}
