package com.osuchat.app

import com.google.gson.Gson
import com.osuchat.app.data.repository.ApkDownloadProgress
import com.osuchat.app.data.repository.AppUpdateRepository
import com.osuchat.app.data.repository.GitHubAssetDto
import com.osuchat.app.data.repository.UpdateCheckResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isNewerVersion compares numeric SemVer segments properly`() {
        assertTrue(AppUpdateRepository.isNewerVersion("1.2.10", "1.2.9"))
        assertTrue(AppUpdateRepository.isNewerVersion("v1.2.10", "1.2.9"))
        assertTrue(AppUpdateRepository.isNewerVersion("V2.0.0", "1.9.9"))
        assertTrue(AppUpdateRepository.isNewerVersion("v2.0", "1.9.9"))
        assertTrue(AppUpdateRepository.isNewerVersion("1.0.0.1", "1.0.0"))
        assertTrue(AppUpdateRepository.isNewerVersion("v1.0.1+build.42", "1.0.0"))

        assertFalse(AppUpdateRepository.isNewerVersion("v1.2.9", "1.2.10"))
        assertFalse(AppUpdateRepository.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("1.0", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("1.0.0+1", "1.0.0+2"))
        assertFalse(AppUpdateRepository.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `isNewerVersion handles prereleases and invalid inputs`() {
        assertTrue(AppUpdateRepository.isNewerVersion("v1.0.0", "1.0.0-rc.1"))
        assertTrue(AppUpdateRepository.isNewerVersion("v1.0.0-rc.2", "1.0.0-rc.1"))
        assertTrue(AppUpdateRepository.isNewerVersion("v1.0.1-beta.1", "1.0.0"))

        assertFalse(AppUpdateRepository.isNewerVersion("v1.0.0-rc.1", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("v1.0.0-rc.1", "1.0.0-rc.1"))
        assertFalse(AppUpdateRepository.isNewerVersion("", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("v", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("latest", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("1..0", "1.0.0"))
        assertFalse(AppUpdateRepository.isNewerVersion("1.2.x", "1.0.0"))
    }

    @Test
    fun `selectApkAsset prefers release apk over non-release and ignores non-apk`() {
        val selected = AppUpdateRepository.selectApkAsset(
            listOf(
                GitHubAssetDto(name = "app-universal.aab", browserDownloadUrl = "https://example.com/app.aab", size = 100),
                GitHubAssetDto(name = "OsuChat-v1.1.0-debug.apk", browserDownloadUrl = "https://example.com/debug.apk", size = 200),
                GitHubAssetDto(name = "OsuChat-v1.1.0-release.apk", browserDownloadUrl = "https://example.com/release.apk", size = 300)
            )
        )
        assertNotNull(selected)
        assertEquals("OsuChat-v1.1.0-release.apk", selected?.name)

        val fallback = AppUpdateRepository.selectApkAsset(
            listOf(
                GitHubAssetDto(name = "OsuChat-v1.1.0.apk", browserDownloadUrl = "https://example.com/app.apk", size = 250)
            )
        )
        assertEquals("OsuChat-v1.1.0.apk", fallback?.name)

        val none = AppUpdateRepository.selectApkAsset(
            listOf(
                GitHubAssetDto(name = "checksums.txt", browserDownloadUrl = "https://example.com/checksums.txt", size = 10)
            )
        )
        assertNull(none)
    }

    @Test
    fun `checkForUpdate respects throttle and skip unless forced, and downloads apk`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val apkPayload = ByteArray(16 * 1024) { (it % 127).toByte() }
            val releaseJson = """
                {
                  "tag_name": "v1.1.0",
                  "name": "OsuChat v1.1.0",
                  "body": "Updater improvements",
                  "html_url": "https://github.com/example/osu-companion/releases/tag/v1.1.0",
                  "draft": false,
                  "prerelease": false,
                  "published_at": "2026-11-21T00:00:00Z",
                  "assets": [
                    {
                      "name": "OsuChat-v1.1.0-release.apk",
                      "browser_download_url": "${server.url("/download/OsuChat-v1.1.0-release.apk")}",
                      "size": ${apkPayload.size}
                    }
                  ]
                }
            """.trimIndent()

            var nowMs = 10_000_000L
            var lastCheck = 0L
            var skipped = ""

            val repo = AppUpdateRepository(
                cacheRootDir = tempFolder.newFolder("cache"),
                gson = Gson(),
                client = AppUpdateRepository.buildStandaloneClient(),
                latestReleaseUrl = server.url("/repos/test/osu-companion/releases/latest").toString(),
                currentVersionName = "1.0.0",
                userAgent = "OsuChat-Test/1.0.0",
                clockMs = { nowMs },
                readLastCheckAt = { lastCheck },
                writeLastCheckAt = { lastCheck = it },
                readSkippedVersion = { skipped },
                writeSkippedVersion = { skipped = it }
            )

            server.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))
            val first = repo.checkForUpdate(force = false)
            assertTrue(first is UpdateCheckResult.UpdateAvailable)
            assertEquals("1.1.0", (first as UpdateCheckResult.UpdateAvailable).update.versionName)
            assertEquals(nowMs, lastCheck)

            // Throttled within 6 hours when force = false
            nowMs += 60_000L
            val throttled = repo.checkForUpdate(force = false)
            assertEquals(UpdateCheckResult.SkippedOrThrottled, throttled)

            // Skipped version suppressed when force = false even after 6h
            nowMs += AppUpdateRepository.CHECK_THROTTLE_MS + 1000L
            skipped = "v1.1.0"
            server.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))
            val skippedRes = repo.checkForUpdate(force = false)
            assertEquals(UpdateCheckResult.SkippedOrThrottled, skippedRes)

            // Manual force = true bypasses both throttle and skip
            server.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))
            val forced = repo.checkForUpdate(force = true)
            assertTrue(forced is UpdateCheckResult.UpdateAvailable)

            // Download APK stream
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(apkPayload))
            )
            val events = repo.downloadApk(first.update.apkDownloadUrl).toList()
            val completed = events.last()
            assertTrue(completed is ApkDownloadProgress.Completed)
            val file = (completed as ApkDownloadProgress.Completed).apkFile
            assertTrue(file.exists())
            assertEquals(apkPayload.size.toLong(), file.length())
        } finally {
            server.shutdown()
        }
    }
}
