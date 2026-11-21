package com.osuchat.app.util

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Immutable
data class DiffspikeBucket(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val hitObjectCount: Int,
    val objectsPerSecond: Double,
    val normalizedIntensity: Float,
    val isPeakDiffspike: Boolean
)

@Immutable
data class BeatmapDiffspikeProfile(
    val buckets: List<DiffspikeBucket>,
    val peakObjectsPerSecond: Double,
    val averageObjectsPerSecond: Double,
    val totalObjects: Int,
    val drainDurationSeconds: Int,
    val clockRate: Double
)

object OsuDiffspikeParser {

    const val DEFAULT_BUCKET_COUNT = 54
    private const val MIN_BUCKETS = 48
    private const val MAX_BUCKETS = 60

    /**
     * Parses `[HitObjects]` timestamps (`x,y,time,...`) from a `.osu` beatmap file and computes
     * objects-per-second density across 48–60 equal time buckets, flagging the top 15% intensity
     * intervals as peak diffspikes.
     */
    fun parse(
        osuText: String,
        clockRate: Double = 1.0,
        bucketCount: Int = DEFAULT_BUCKET_COUNT
    ): BeatmapDiffspikeProfile? {
        if (osuText.isBlank()) return null

        val safeClockRate = if (clockRate.isFinite() && clockRate > 0.0) {
            clockRate.coerceIn(0.5, 2.0)
        } else {
            1.0
        }
        val safeBucketCount = bucketCount.coerceIn(MIN_BUCKETS, MAX_BUCKETS)

        val timestamps = parseHitObjectTimestamps(osuText)
        if (timestamps.size < 2) return null

        val minTimeMs = timestamps.first()
        val maxTimeMs = timestamps.last()
        val rawSpanMs = (maxTimeMs - minTimeMs).coerceAtLeast(1_000L)
        val bucketSpanMs = rawSpanMs.toDouble() / safeBucketCount.toDouble()
        val bucketSecondsScaled = (bucketSpanMs / 1000.0) / safeClockRate

        val counts = IntArray(safeBucketCount)
        for (ts in timestamps) {
            val idx = (((ts - minTimeMs).toDouble() / rawSpanMs.toDouble()) * safeBucketCount.toDouble())
                .toInt()
                .coerceIn(0, safeBucketCount - 1)
            counts[idx]++
        }

        val densities = DoubleArray(safeBucketCount) { idx ->
            if (bucketSecondsScaled > 0.0) counts[idx].toDouble() / bucketSecondsScaled else 0.0
        }
        val peakDensity = densities.maxOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        if (peakDensity <= 0.0) return null

        val activeIndices = densities.indices.filter { counts[it] > 0 }
        val spikeCount = ceil(activeIndices.size * 0.15).toInt().coerceAtLeast(1)
        val peakIndices = activeIndices
            .sortedByDescending { densities[it] }
            .take(spikeCount)
            .toSet()

        val buckets = List(safeBucketCount) { idx ->
            val startMs = minTimeMs + (idx * bucketSpanMs).roundToLong()
            val endMs = minTimeMs + ((idx + 1) * bucketSpanMs).roundToLong()
            val density = densities[idx]
            DiffspikeBucket(
                index = idx,
                startMs = startMs,
                endMs = endMs,
                hitObjectCount = counts[idx],
                objectsPerSecond = density,
                normalizedIntensity = (density / peakDensity).toFloat().coerceIn(0f, 1f),
                isPeakDiffspike = idx in peakIndices
            )
        }

        val scaledDrainSec = ((rawSpanMs / 1000.0) / safeClockRate).roundToInt().coerceAtLeast(1)
        val avgDensity = if (scaledDrainSec > 0) timestamps.size.toDouble() / scaledDrainSec.toDouble() else 0.0

        return BeatmapDiffspikeProfile(
            buckets = buckets,
            peakObjectsPerSecond = peakDensity,
            averageObjectsPerSecond = avgDensity,
            totalObjects = timestamps.size,
            drainDurationSeconds = scaledDrainSec,
            clockRate = safeClockRate
        )
    }

    internal fun parseHitObjectTimestamps(osuText: String): List<Long> {
        val timestamps = ArrayList<Long>(256)
        var inHitObjects = false
        for (rawLine in osuText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                if (inHitObjects) break
                inHitObjects = line.equals("[HitObjects]", ignoreCase = true)
                continue
            }
            if (!inHitObjects) continue
            val firstComma = line.indexOf(',')
            if (firstComma <= 0) continue
            val secondComma = line.indexOf(',', firstComma + 1)
            if (secondComma <= firstComma + 1) continue
            val thirdComma = line.indexOf(',', secondComma + 1)
            val timeToken = if (thirdComma > secondComma) {
                line.substring(secondComma + 1, thirdComma).trim()
            } else {
                line.substring(secondComma + 1).trim()
            }
            val parsedTime = timeToken.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 && it <= 86_400_000.0 }
                ?.roundToLong() ?: continue
            timestamps.add(parsedTime)
        }
        timestamps.sort()
        return timestamps
    }
}