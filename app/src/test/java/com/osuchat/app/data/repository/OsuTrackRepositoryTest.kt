package com.osuchat.app.data.repository

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class OsuTrackRepositoryTest {

    private lateinit var server: MockWebServer
    private var fakeNowMs: Long = 1_750_000_000_000L
    private val fixedToday: LocalDate = LocalDate.of(2026, 6, 15)
    private lateinit var repository: OsuTrackRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        repository = OsuTrackRepository(
            gson = GsonBuilder().create(),
            baseUrl = server.url("/").toString(),
            client = client,
            clockMs = { fakeNowMs },
            todayUtc = { fixedToday }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun modeToInt_mapsAllOsuModesAndFallsBackToStandard() {
        assertEquals(0, OsuTrackRepository.modeToInt("osu"))
        assertEquals(0, OsuTrackRepository.modeToInt("OSU"))
        assertEquals(1, OsuTrackRepository.modeToInt("taiko"))
        assertEquals(2, OsuTrackRepository.modeToInt("fruits"))
        assertEquals(2, OsuTrackRepository.modeToInt("catch"))
        assertEquals(2, OsuTrackRepository.modeToInt("ctb"))
        assertEquals(3, OsuTrackRepository.modeToInt("mania"))
        assertEquals(0, OsuTrackRepository.modeToInt(null))
        assertEquals(0, OsuTrackRepository.modeToInt("unknown"))
    }

    @Test
    fun fetchStatsHistory_parsesFiltersInvalidAndSortsAscending_withRequiredHeaders() = runBlocking {
        val json = """
            [
              {"pp_raw": 5210.5, "pp_rank": 15000, "accuracy": 98.4, "playcount": 12000, "timestamp": "2026-06-10T12:00:00Z"},
              {"pp_raw": 0.0, "pp_rank": 14000, "timestamp": "2026-06-11T12:00:00Z"},
              {"pp_raw": 5100.0, "pp_rank": 0, "timestamp": "2026-06-11T12:00:00Z"},
              {"pp_raw": 5150.25, "pp_rank": 15400, "accuracy": 98.2, "playcount": 11900, "timestamp": "2026-06-01 08:30:00"},
              {"pp_raw": 5300.0, "pp_rank": 14500, "timestamp": "2026-06-14T15:00:00+02:00"},
              {"pp_raw": 5000.0, "pp_rank": 16000, "timestamp": "not-a-date"}
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = repository.fetchStatsHistory(userId = 7562902L, mode = "taiko", days = 90)
        assertTrue(result.isSuccess)
        val points = result.getOrThrow()
        assertEquals(3, points.size)
        // Sorted ascending: June 1 -> June 10 -> June 14
        assertEquals(5150.25, points[0].pp, 1e-6)
        assertEquals(15400, points[0].globalRank)
        assertEquals(5210.5, points[1].pp, 1e-6)
        assertEquals(5300.0, points[2].pp, 1e-6)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("GET", recorded.method)
        assertEquals("OsuChat-Android", recorded.getHeader("User-Agent"))
        assertNull(recorded.getHeader("Authorization"))
        val url = recorded.requestUrl!!
        assertEquals("/stats_history", url.encodedPath)
        assertEquals("7562902", url.queryParameter("user"))
        assertEquals("1", url.queryParameter("mode"))
        assertEquals("2026-03-17", url.queryParameter("from"))
        assertEquals("2026-06-16", url.queryParameter("to"))
    }

    @Test
    fun fetchStatsHistory_cachesFor15MinutesAndExpiresAfterTtl() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"pp_raw": 4000.0, "pp_rank": 30000, "timestamp": "2026-06-10T00:00:00Z"}]"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"pp_raw": 4200.0, "pp_rank": 28000, "timestamp": "2026-06-15T00:00:00Z"}]"""
            )
        )

        val first = repository.fetchStatsHistory(userId = 42L, mode = "osu", days = 90).getOrThrow()
        assertEquals(4000.0, first.single().pp, 1e-6)
        assertEquals(1, server.requestCount)

        // 10 minutes later: cache hit, no extra HTTP call
        fakeNowMs += 10L * 60L * 1000L
        val cached = repository.fetchStatsHistory(userId = 42L, mode = "osu", days = 30).getOrThrow()
        assertEquals(4000.0, cached.single().pp, 1e-6)
        assertEquals(1, server.requestCount)

        // Past 15-minute TTL: refreshes from server
        fakeNowMs += 6L * 60L * 1000L
        val refreshed = repository.fetchStatsHistory(userId = 42L, mode = "osu", days = 90).getOrThrow()
        assertEquals(4200.0, refreshed.single().pp, 1e-6)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun fetchStatsHistory_handles429And404Gracefully() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val rateLimited = repository.fetchStatsHistory(userId = 100L, mode = "fruits", days = 90)
        assertTrue(rateLimited.isFailure)

        // Advance past 60s rate limit cooldown and test 404
        fakeNowMs += 61_000L
        server.enqueue(MockResponse().setResponseCode(404))
        val notFound = repository.fetchStatsHistory(userId = 101L, mode = "mania", days = 90)
        assertTrue(notFound.isSuccess)
        assertTrue(notFound.getOrThrow().isEmpty())
    }

    @Test
    fun fetchPeak_parsesObjectOrArrayResponses() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"best_global_rank": 512, "best_rank_timestamp": "2026-05-01T00:00:00Z", "best_accuracy": 99.12, "best_acc_timestamp": "2026-05-02T00:00:00Z"}]"""
            )
        )
        val peakRes = repository.fetchPeak(userId = 77L, mode = "osu")
        assertTrue(peakRes.isSuccess)
        val peak = peakRes.getOrThrow()
        assertNotNull(peak)
        assertEquals(512, peak!!.bestGlobalRank)
        assertEquals(99.12, peak.bestAccuracy!!, 1e-6)
    }
}
