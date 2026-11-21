package com.osuchat.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.osuchat.app.domain.model.OsuTrackPeak
import com.osuchat.app.domain.model.OsuTrackPoint
import com.osuchat.app.util.RankPpMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OsuTrackStatEntryDto(
    @SerializedName("pp_raw") val ppRaw: Double? = null,
    @SerializedName("pp_rank") val ppRank: Int? = null,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("playcount") val playcount: Int? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class OsuTrackPeakDto(
    @SerializedName("best_global_rank") val bestGlobalRank: Int? = null,
    @SerializedName("best_rank_timestamp") val bestRankTimestamp: String? = null,
    @SerializedName("best_accuracy") val bestAccuracy: Double? = null,
    @SerializedName("best_acc_timestamp") val bestAccTimestamp: String? = null
)

@Singleton
class OsuTrackRepository internal constructor(
    private val gson: Gson,
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val todayUtc: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }
) {
    @Inject
    constructor(gson: Gson) : this(
        gson = gson,
        baseUrl = DEFAULT_BASE_URL,
        client = buildStandaloneClient()
    )

    private data class HistoryCacheEntry(
        val points: List<OsuTrackPoint>,
        val requestedDays: Int,
        val fetchedAtMs: Long
    )

    private data class PeakCacheEntry(
        val peak: OsuTrackPeak?,
        val fetchedAtMs: Long
    )

    private data class CacheKey(
        val userId: Long,
        val modeInt: Int
    )

    private val historyCache = ConcurrentHashMap<CacheKey, HistoryCacheEntry>()
    private val peakCache = ConcurrentHashMap<CacheKey, PeakCacheEntry>()
    private val inFlightHistory = ConcurrentHashMap<Pair<CacheKey, Int>, CompletableDeferred<Result<List<OsuTrackPoint>>>>()
    private val inFlightPeak = ConcurrentHashMap<CacheKey, CompletableDeferred<Result<OsuTrackPeak?>>>()

    @Volatile
    private var rateLimitUntilMs: Long = 0L

    suspend fun fetchStatsHistory(
        userId: Long,
        mode: String,
        days: Int = 90
    ): Result<List<OsuTrackPoint>> {
        if (userId <= 0L) return Result.success(emptyList())
        val modeInt = modeToInt(mode)
        val safeDays = days.coerceIn(1, 365)
        val key = CacheKey(userId, modeInt)
        val now = clockMs()

        val cached = historyCache[key]
        if (cached != null && (now - cached.fetchedAtMs) < CACHE_TTL_MS && cached.requestedDays >= safeDays) {
            return Result.success(cached.points)
        }

        if (now < rateLimitUntilMs) {
            return if (cached != null) {
                Result.success(cached.points)
            } else {
                Result.failure(IOException("osu!track rate limited"))
            }
        }

        val flightKey = key to safeDays
        val newDeferred = CompletableDeferred<Result<List<OsuTrackPoint>>>()
        val existingDeferred = inFlightHistory.putIfAbsent(flightKey, newDeferred)
        if (existingDeferred != null) {
            return existingDeferred.await()
        }

        val result = try {
            withContext(Dispatchers.IO) {
                fetchStatsHistoryFromNetwork(
                    userId = userId,
                    modeInt = modeInt,
                    days = safeDays,
                    key = key,
                    staleCache = cached
                )
            }
        } catch (ce: CancellationException) {
            newDeferred.cancel(ce)
            inFlightHistory.remove(flightKey, newDeferred)
            throw ce
        } catch (t: Throwable) {
            val fallback = cached?.let { Result.success(it.points) } ?: Result.failure(t)
            newDeferred.complete(fallback)
            inFlightHistory.remove(flightKey, newDeferred)
            return fallback
        }
        newDeferred.complete(result)
        inFlightHistory.remove(flightKey, newDeferred)
        return result
    }

    private fun fetchStatsHistoryFromNetwork(
        userId: Long,
        modeInt: Int,
        days: Int,
        key: CacheKey,
        staleCache: HistoryCacheEntry?
    ): Result<List<OsuTrackPoint>> {
        val today = todayUtc()
        val fromDate = today.minusDays(days.toLong()).toString()
        val toDate = today.plusDays(1L).toString()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("stats_history")
            .addQueryParameter("user", userId.toString())
            .addQueryParameter("mode", modeInt.toString())
            .addQueryParameter("from", fromDate)
            .addQueryParameter("to", toDate)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val now = clockMs()
                when {
                    response.code == 429 -> {
                        rateLimitUntilMs = now + RATE_LIMIT_COOLDOWN_MS
                        staleCache?.let { Result.success(it.points) }
                            ?: Result.failure(IOException("osu!track HTTP 429"))
                    }
                    response.code == 404 -> {
                        putHistoryCache(key, HistoryCacheEntry(emptyList(), days, now))
                        Result.success(emptyList())
                    }
                    !response.isSuccessful -> {
                        staleCache?.let { Result.success(it.points) }
                            ?: Result.failure(IOException("osu!track HTTP ${response.code}"))
                    }
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = parseStatsHistory(body)
                        putHistoryCache(key, HistoryCacheEntry(parsed, days, now))
                        Result.success(parsed)
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            staleCache?.let { Result.success(it.points) } ?: Result.failure(t)
        }
    }

    suspend fun fetchPeak(userId: Long, mode: String): Result<OsuTrackPeak?> {
        if (userId <= 0L) return Result.success(null)
        val modeInt = modeToInt(mode)
        val key = CacheKey(userId, modeInt)
        val now = clockMs()

        val cached = peakCache[key]
        if (cached != null && (now - cached.fetchedAtMs) < CACHE_TTL_MS) {
            return Result.success(cached.peak)
        }

        if (now < rateLimitUntilMs) {
            return if (cached != null) {
                Result.success(cached.peak)
            } else {
                Result.failure(IOException("osu!track rate limited"))
            }
        }

        val newDeferred = CompletableDeferred<Result<OsuTrackPeak?>>()
        val existingDeferred = inFlightPeak.putIfAbsent(key, newDeferred)
        if (existingDeferred != null) {
            return existingDeferred.await()
        }

        val result = try {
            withContext(Dispatchers.IO) {
                fetchPeakFromNetwork(userId, modeInt, key, cached)
            }
        } catch (ce: CancellationException) {
            newDeferred.cancel(ce)
            inFlightPeak.remove(key, newDeferred)
            throw ce
        } catch (t: Throwable) {
            val fallback = cached?.let { Result.success(it.peak) } ?: Result.failure(t)
            newDeferred.complete(fallback)
            inFlightPeak.remove(key, newDeferred)
            return fallback
        }
        newDeferred.complete(result)
        inFlightPeak.remove(key, newDeferred)
        return result
    }

    private fun fetchPeakFromNetwork(
        userId: Long,
        modeInt: Int,
        key: CacheKey,
        staleCache: PeakCacheEntry?
    ): Result<OsuTrackPeak?> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("peak")
            .addQueryParameter("user", userId.toString())
            .addQueryParameter("mode", modeInt.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val now = clockMs()
                when {
                    response.code == 429 -> {
                        rateLimitUntilMs = now + RATE_LIMIT_COOLDOWN_MS
                        staleCache?.let { Result.success(it.peak) }
                            ?: Result.failure(IOException("osu!track HTTP 429"))
                    }
                    response.code == 404 -> {
                        putPeakCache(key, PeakCacheEntry(null, now))
                        Result.success(null)
                    }
                    !response.isSuccessful -> {
                        staleCache?.let { Result.success(it.peak) }
                            ?: Result.failure(IOException("osu!track HTTP ${response.code}"))
                    }
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val peak = parsePeak(body)
                        putPeakCache(key, PeakCacheEntry(peak, now))
                        Result.success(peak)
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            staleCache?.let { Result.success(it.peak) } ?: Result.failure(t)
        }
    }

    private fun parseStatsHistory(jsonText: String): List<OsuTrackPoint> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return emptyList()
        if (!root.isJsonArray) return emptyList()
        val array = root.asJsonArray
        val points = ArrayList<OsuTrackPoint>(array.size())
        for (elem in array) {
            if (!elem.isJsonObject) continue
            val dto = runCatching { gson.fromJson(elem, OsuTrackStatEntryDto::class.java) }.getOrNull() ?: continue
            val tsMs = parseTimestampMs(dto.timestamp) ?: continue
            val pp = dto.ppRaw?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val rank = RankPpMath.sanitizeRank(dto.ppRank) ?: continue
            val acc = RankPpMath.sanitizeFiniteNonNegative(dto.accuracy) ?: 0.0
            val playcount = (dto.playcount ?: 0).coerceAtLeast(0)
            points += OsuTrackPoint(
                timestampMs = tsMs,
                pp = pp,
                globalRank = rank,
                accuracy = acc,
                playCount = playcount
            )
        }
        return points.sortedBy { it.timestampMs }
    }

    private fun parsePeak(jsonText: String): OsuTrackPeak? {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return null
        val root = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return null
        val obj = when {
            root.isJsonObject -> root.asJsonObject
            root.isJsonArray && root.asJsonArray.size() > 0 && root.asJsonArray[0].isJsonObject ->
                root.asJsonArray[0].asJsonObject
            else -> return null
        }
        val dto = runCatching { gson.fromJson(obj, OsuTrackPeakDto::class.java) }.getOrNull() ?: return null
        val bestRank = RankPpMath.sanitizeRank(dto.bestGlobalRank)
        val bestAcc = RankPpMath.sanitizeFiniteNonNegative(dto.bestAccuracy)
        if (bestRank == null && bestAcc == null) return null
        return OsuTrackPeak(
            bestGlobalRank = bestRank,
            bestRankTimestamp = dto.bestRankTimestamp?.takeIf { it.isNotBlank() },
            bestAccuracy = bestAcc,
            bestAccTimestamp = dto.bestAccTimestamp?.takeIf { it.isNotBlank() }
        )
    }

    private fun putHistoryCache(key: CacheKey, entry: HistoryCacheEntry) {
        if (historyCache.size >= MAX_CACHE_ENTRIES) {
            val now = clockMs()
            historyCache.entries.removeIf { (now - it.value.fetchedAtMs) >= CACHE_TTL_MS }
            if (historyCache.size >= MAX_CACHE_ENTRIES) {
                val oldestKey = historyCache.entries.minByOrNull { it.value.fetchedAtMs }?.key
                if (oldestKey != null) historyCache.remove(oldestKey)
            }
        }
        historyCache[key] = entry
    }

    private fun putPeakCache(key: CacheKey, entry: PeakCacheEntry) {
        if (peakCache.size >= MAX_CACHE_ENTRIES) {
            val now = clockMs()
            peakCache.entries.removeIf { (now - it.value.fetchedAtMs) >= CACHE_TTL_MS }
            if (peakCache.size >= MAX_CACHE_ENTRIES) {
                val oldestKey = peakCache.entries.minByOrNull { it.value.fetchedAtMs }?.key
                if (oldestKey != null) peakCache.remove(oldestKey)
            }
        }
        peakCache[key] = entry
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://osutrack-api.ameo.dev/"
        private const val USER_AGENT = "OsuChat-Android"
        private const val CACHE_TTL_MS = 15L * 60L * 1000L
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        private const val MAX_CACHE_ENTRIES = 128

        fun modeToInt(mode: String?): Int = when (mode?.trim()?.lowercase(Locale.US)) {
            "taiko" -> 1
            "fruits", "catch", "ctb" -> 2
            "mania" -> 3
            else -> 0
        }

        internal fun parseTimestampMs(raw: String?): Long? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching {
                    LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrNull()
                ?: runCatching {
                    LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrNull()
        }

        private fun buildStandaloneClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
