package com.osuchat.app

import com.google.gson.GsonBuilder
import com.osuchat.app.data.network.NerinyanApiService
import com.osuchat.app.data.network.OsuApiException
import com.osuchat.app.data.network.OsuApiPoller
import com.osuchat.app.data.network.OsuApiPollerEvent
import com.osuchat.app.data.network.OsuApiService
import com.osuchat.app.data.network.OsuAuthService
import com.osuchat.app.data.network.PublicBeatmapApiService
import com.osuchat.app.data.preferences.CredentialCipher
import com.osuchat.app.data.preferences.OAuthTokenData
import com.osuchat.app.data.preferences.OAuthTokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.KeyGenerator

class OsuApiPollerTest {

    private lateinit var osuServer: MockWebServer
    private lateinit var catboyServer: MockWebServer
    private lateinit var nerinyanServer: MockWebServer
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var authService: OsuAuthService
    private lateinit var apiService: OsuApiService
    private lateinit var publicBeatmapApi: PublicBeatmapApiService
    private lateinit var nerinyanApi: NerinyanApiService
    private lateinit var poller: OsuApiPoller

    private class InMemoryTokenStore(initial: OAuthTokenData? = null) : OAuthTokenStore {
        private val ref = AtomicReference(initial)
        val updateCount = AtomicInteger(0)
        val clearCount = AtomicInteger(0)

        override suspend fun getOAuthTokens(): OAuthTokenData? = ref.get()

        override suspend fun updateOAuthTokens(
            accessToken: String,
            refreshToken: String,
            expiresAtMillis: Long
        ) {
            updateCount.incrementAndGet()
            ref.set(OAuthTokenData(accessToken, refreshToken, expiresAtMillis))
        }

        override suspend fun clearOAuthTokens() {
            clearCount.incrementAndGet()
            ref.set(null)
        }
    }

    @Before
    fun setUp() {
        osuServer = MockWebServer()
        catboyServer = MockWebServer()
        nerinyanServer = MockWebServer()
        osuServer.start()
        catboyServer.start()
        nerinyanServer.start()

        val gson = GsonBuilder().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        val osuRetrofit = Retrofit.Builder()
            .baseUrl(osuServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val catboyRetrofit = Retrofit.Builder()
            .baseUrl(catboyServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val nerinyanRetrofit = Retrofit.Builder()
            .baseUrl(nerinyanServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        authService = osuRetrofit.create(OsuAuthService::class.java)
        apiService = osuRetrofit.create(OsuApiService::class.java)
        publicBeatmapApi = catboyRetrofit.create(PublicBeatmapApiService::class.java)
        nerinyanApi = nerinyanRetrofit.create(NerinyanApiService::class.java)

        tokenStore = InMemoryTokenStore(
            OAuthTokenData(
                accessToken = "stale-access-1",
                refreshToken = "stale-refresh-1",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L
            )
        )
        poller = OsuApiPoller(
            authService = authService,
            apiService = apiService,
            publicBeatmapApi = publicBeatmapApi,
            nerinyanApi = nerinyanApi,
            prefs = tokenStore,
            cloudflareChallengeManagerOrNull = null
        )
    }

    @After
    fun tearDown() {
        poller.stopPolling()
        osuServer.shutdown()
        catboyServer.shutdown()
        nerinyanServer.shutdown()
    }

    @Test
    fun `concurrent 401 responses trigger only one token refresh request`() = runBlocking {
        val refreshCount = AtomicInteger(0)
        osuServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/oauth/token" -> {
                        refreshCount.incrementAndGet()
                        Thread.sleep(120)
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(
                                """{"access_token":"fresh-access-2","refresh_token":"fresh-refresh-2","expires_in":3600}"""
                            )
                    }
                    "/api/v2/me" -> {
                        if (request.getHeader("Authorization") == "Bearer fresh-access-2") {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""{"id":2,"username":"peppy"}""")
                        } else {
                            MockResponse().setResponseCode(401)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val users = coroutineScope {
            (1..5).map {
                async {
                    poller.withBearerRetry { bearer ->
                        apiService.getMe(bearer)
                    }
                }
            }.awaitAll()
        }

        assertEquals(5, users.size)
        assertTrue(users.all { it.username == "peppy" })
        assertEquals(1, refreshCount.get())
        assertEquals(1, tokenStore.updateCount.get())
        val updated = tokenStore.getOAuthTokens()
        assertNotNull(updated)
        assertEquals("fresh-access-2", updated?.accessToken)
        assertEquals("fresh-refresh-2", updated?.refreshToken)
    }

    @Test
    fun `revoked refresh token clears OAuth tokens and emits AuthFailed`() = runBlocking {
        osuServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        val nextEvent = async { poller.events.first() }
        val result = poller.getValidBearerToken(forceRefresh = true, failedBearerToken = "Bearer stale-access-1")

        assertNull(result)
        assertNull(tokenStore.getOAuthTokens())
        assertEquals(1, tokenStore.clearCount.get())
        val event = nextEvent.await()
        assertTrue(event is OsuApiPollerEvent.AuthFailed)
        assertEquals(
            OsuApiPoller.SESSION_EXPIRED_MESSAGE,
            (event as OsuApiPollerEvent.AuthFailed).message
        )
    }

    @Test
    fun `429 with Retry-After honors backoff delay and retries`() = runBlocking {
        val attempts = AtomicInteger(0)
        osuServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/v2/me") {
                    return if (attempts.incrementAndGet() == 1) {
                        MockResponse()
                            .setResponseCode(429)
                            .addHeader("Retry-After", "1")
                    } else {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"id":777,"username":"cookiezi"}""")
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }

        val startMs = System.currentTimeMillis()
        val me = poller.withBearerRetry { bearer ->
            apiService.getMe(bearer)
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals("cookiezi", me.username)
        assertEquals(2, attempts.get())
        assertTrue("Expected >=900ms Retry-After backoff, actual=${elapsedMs}ms", elapsedMs >= 900L)
    }

    @Test
    fun `mirror fallback activates on 403 429 and 5xx`() = runBlocking {
        val codesToTest = listOf(403, 429, 503)
        for (code in codesToTest) {
            poller.resetAuthState()
            osuServer.enqueue(MockResponse().setResponseCode(code))
            catboyServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":75,"beatmapset_id":1,"version":"Normal","difficulty_rating":2.4}""")
            )

            val beatmap = poller.withMirrorFallback(
                primary = {
                    poller.withBearerRetry { bearer ->
                        apiService.getBeatmap(bearer, 75L)
                    }
                },
                fallback = {
                    poller.getBeatmapFromMirrors(75L)
                }
            )

            assertNotNull("Expected mirror beatmap fallback on HTTP $code", beatmap)
            assertEquals(75L, beatmap?.id)
            assertEquals("Normal", beatmap?.version)
        }

        // Verify catboy -> nerinyan search mirror chain when catboy returns 500
        catboyServer.enqueue(MockResponse().setResponseCode(500))
        nerinyanServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":129891,"title":"Freedom Dive","artist":"xi","creator":"Nakagawa-Kanon"}]""")
        )
        val searchResults = poller.searchBeatmapsetsFromMirrors(query = "freedom dive", modeInt = 0, limit = 10)
        assertEquals(1, searchResults.size)
        assertEquals("Freedom Dive", searchResults.first().title)

        assertFalse(poller.shouldFallbackToMirror(OsuApiException(404, "Not found")))
        assertTrue(poller.shouldFallbackToMirror(OsuApiException(500, "Internal error")))
        assertTrue(poller.shouldFallbackToMirror(OsuApiException(503, "Unavailable")))
    }

    @Test
    fun `CredentialCipher AES-GCM encrypt decrypt roundtrip produces unique IV ciphertexts`() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()

        val secret = "super-secret-oauth-token-123"
        val enc1 = CredentialCipher.encrypt(secret, key)
        val enc2 = CredentialCipher.encrypt(secret, key)

        assertTrue(CredentialCipher.isEncrypted(enc1))
        assertTrue(CredentialCipher.isEncrypted(enc2))
        assertNotEquals(secret, enc1)
        assertNotEquals("Fresh random IV must yield unique ciphertexts", enc1, enc2)
        assertEquals(secret, CredentialCipher.decrypt(enc1, key))
        assertEquals(secret, CredentialCipher.decrypt(enc2, key))
        assertEquals("legacy-plaintext-token", CredentialCipher.decrypt("legacy-plaintext-token", key))
    }
}
