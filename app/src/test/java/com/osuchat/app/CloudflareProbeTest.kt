package com.osuchat.app

import com.osuchat.app.data.network.CloudflareChallengeManager
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class CloudflareProbeTest {

    private fun buildProbeClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(
                CloudflareChallengeManager.createCompliantHeadersInterceptor { "probe-token" }
            )
            .build()
    }

    @Test
    fun `oauth token and api v2 requests strip spoofed browser headers and avoid 403`() {
        val client = buildProbeClient()

        val tokenBody = FormBody.Builder()
            .add("client_id", "75323")
            .add("grant_type", "authorization_code")
            .add("code", "probe-invalid-code")
            .add("redirect_uri", "http://127.0.0.1:42069/callback")
            .build()

        val tokenRequest = Request.Builder()
            .url("https://osu.ppy.sh/oauth/token")
            .header("Origin", "https://osu.ppy.sh")
            .header("Referer", "https://osu.ppy.sh/home")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("Cookie", "cf_clearance=stale; __cf_bm=stale")
            .post(tokenBody)
            .build()

        client.newCall(tokenRequest).execute().use { response ->
            val sent = response.request
            println("PROBE /oauth/token -> HTTP ${response.code} ua=${sent.header("User-Agent")} cf=${response.header("cf-mitigated")}")
            assertEquals(CloudflareChallengeManager.USER_AGENT, sent.header("User-Agent"))
            assertEquals("application/json", sent.header("Accept"))
            assertNull(sent.header("Origin"))
            assertNull(sent.header("Referer"))
            assertNull(sent.header("Sec-Fetch-Site"))
            assertNull(sent.header("Cookie"))
            assertNotEquals("POST /oauth/token was blocked with 403", 403, response.code)
        }

        val apiEndpoints = listOf(
            "https://osu.ppy.sh/api/v2/beatmapsets/search?q=freedom+dive",
            "https://osu.ppy.sh/api/v2/me"
        )
        for (url in apiEndpoints) {
            val apiRequest = Request.Builder()
                .url(url)
                .header("Origin", "https://osu.ppy.sh")
                .header("Referer", "https://osu.ppy.sh/home")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Cookie", "cf_clearance=stale; __cf_bm=stale")
                .get()
                .build()

            client.newCall(apiRequest).execute().use { response ->
                val sent = response.request
                println("PROBE $url -> HTTP ${response.code} ua=${sent.header("User-Agent")} auth=${sent.header("Authorization")} cf=${response.header("cf-mitigated")}")
                assertEquals(CloudflareChallengeManager.USER_AGENT, sent.header("User-Agent"))
                assertEquals("application/json", sent.header("Accept"))
                assertEquals("Bearer probe-token", sent.header("Authorization"))
                assertNull(sent.header("Origin"))
                assertNull(sent.header("Referer"))
                assertNull(sent.header("Sec-Fetch-Site"))
                assertNull(sent.header("Cookie"))
                assertNotEquals("GET $url was blocked with 403", 403, response.code)
            }
        }
    }
}
