package com.osuchat.app

import com.osuchat.app.data.repository.ImageUploadRepository
import com.osuchat.app.util.BeatmapUrlParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageUploadAndParserTest {

    @Test
    fun extractImageUrls_detectsExtensionsAndTemporaryHostsDeduplicatingOrder() {
        val text = listOf(
            "Check this out https://litter.catbox.moe/abc123.jpg!",
            "(http://0x0.st/XyZ9)",
            "https://example.com/screen.PNG?size=large",
            "litter.catbox.moe/abc123.jpg",
            "https://cdn.example.org/clip.webp",
            "https://cdn.example.org/anim.gif",
            "https://osu.ppy.sh/b/12345",
            "https://0x0.st/archive.zip"
        ).joinToString(" ")

        val urls = BeatmapUrlParser.extractImageUrls(text)
        assertEquals(
            listOf(
                "https://litter.catbox.moe/abc123.jpg",
                "http://0x0.st/XyZ9",
                "https://example.com/screen.PNG?size=large",
                "https://cdn.example.org/clip.webp",
                "https://cdn.example.org/anim.gif"
            ),
            urls
        )
    }

    @Test
    fun extractImageUrls_returnsEmptyForBlankOrNonImageText() {
        assertTrue(BeatmapUrlParser.extractImageUrls("").isEmpty())
        assertTrue(BeatmapUrlParser.extractImageUrls("   ").isEmpty())
        assertTrue(BeatmapUrlParser.extractImageUrls("https://osu.ppy.sh/beatmapsets/123#osu/456").isEmpty())
        assertTrue(BeatmapUrlParser.extractImageUrls("https://litter.catbox.moe/").isEmpty())
        assertTrue(BeatmapUrlParser.extractImageUrls("https://tmpfiles.org/12345/shot.jpg").isEmpty())
    }

    @Test
    fun extractImageUrls_recognizesAllSupportedUploadHostsAndDirectTmpfilesLinks() {
        val text = listOf(
            "https://litter.catbox.moe/abc123",
            "https://files.catbox.moe/def456",
            "https://h.uguu.se/ghi789",
            "https://uguu.se/jkl012",
            "https://tmpfiles.org/dl/12345/shot",
            "https://0x0.st/XyZ9"
        ).joinToString(" ")

        assertEquals(
            listOf(
                "https://litter.catbox.moe/abc123",
                "https://files.catbox.moe/def456",
                "https://h.uguu.se/ghi789",
                "https://uguu.se/jkl012",
                "https://tmpfiles.org/dl/12345/shot",
                "https://0x0.st/XyZ9"
            ),
            BeatmapUrlParser.extractImageUrls(text)
        )
    }

    @Test
    fun extractImageUrls_handlesIrcLinksUppercaseExtensionsAndLineBreaks() {
        val text = "[https://litter.catbox.moe/abc123.JPG\r\n Label] \"https://files.catbox.moe/photo.PNG?v=1\""
        assertEquals(
            listOf(
                "https://litter.catbox.moe/abc123.JPG",
                "https://files.catbox.moe/photo.PNG?v=1"
            ),
            BeatmapUrlParser.extractImageUrls(text)
        )
        assertTrue(
            BeatmapUrlParser.isSelfOsuSender(
                senderName = "Test User",
                currentUsername = "test_user"
            )
        )
        assertTrue(
            BeatmapUrlParser.isSelfOsuSender(
                senderName = "alt_irc_nick",
                currentUsername = "main_nick",
                currentIrcUsername = "Alt IRC Nick"
            )
        )
    }

    @Test
    fun uploadCompressedFile_usesPrimaryLitterboxFirst() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("https://litter.catbox.moe/sample72h.jpg\n"))
        server.start()

        val tempFile = File.createTempFile("upload_test_", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        try {
            val repo = ImageUploadRepository(
                context = null,
                okHttpClient = OkHttpClient(),
                primaryUrl = server.url("/resources/internals/api.php").toString(),
                fallbackUrl = server.url("/fallback").toString()
            )
            val result = repo.uploadCompressedFile(tempFile)
            assertTrue(result.isSuccess)
            assertEquals("https://litter.catbox.moe/sample72h.jpg", result.getOrNull())
            assertEquals(1, server.requestCount)

            val req = server.takeRequest()
            assertEquals("/resources/internals/api.php", req.path)
            val body = req.body.readUtf8()
            assertTrue(body.contains("name=\"reqtype\""))
            assertTrue(body.contains("fileupload"))
            assertTrue(body.contains("name=\"time\""))
            assertTrue(body.contains("72h"))
            assertTrue(body.contains("name=\"fileToUpload\""))
        } finally {
            tempFile.delete()
            server.shutdown()
        }
    }

    @Test
    fun uploadCompressedFile_fallsBackTo0x0StWhenPrimaryFails() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service unavailable"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("https://0x0.st/test123.jpg\n"))
        server.start()

        val tempFile = File.createTempFile("upload_fallback_", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        try {
            val repo = ImageUploadRepository(
                context = null,
                okHttpClient = OkHttpClient(),
                primaryUrl = server.url("/resources/internals/api.php").toString(),
                fallbackUrl = server.url("/0x0").toString()
            )
            val result = repo.uploadCompressedFile(tempFile)
            assertTrue(result.isSuccess)
            assertEquals("https://0x0.st/test123.jpg", result.getOrNull())
            assertEquals(2, server.requestCount)

            val first = server.takeRequest()
            assertEquals("/resources/internals/api.php", first.path)
            val second = server.takeRequest()
            assertEquals("/0x0", second.path)
            val secondBody = second.body.readUtf8()
            assertTrue(secondBody.contains("name=\"file\""))
        } finally {
            tempFile.delete()
            server.shutdown()
        }
    }

    @Test
    fun uploadCompressedFile_fallsThroughFiveHostsAndRewritesTmpfilesUrl() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad Gateway"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>blocked</html>"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("Down"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","data":{"url":"http://tmpfiles.org/9876/upload.jpg"}}""")
        )
        server.start()

        val tempFile = File.createTempFile("upload_5host_", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        try {
            val repo = ImageUploadRepository(
                context = null,
                okHttpClient = OkHttpClient(),
                litterboxUrl = server.url("/litterbox").toString(),
                catboxUrl = server.url("/catbox").toString(),
                uguuUrl = server.url("/uguu").toString(),
                tmpfilesUrl = server.url("/tmpfiles").toString(),
                fallbackUrl = server.url("/0x0").toString()
            )
            val result = repo.uploadCompressedFile(tempFile)
            assertTrue(result.isSuccess)
            assertEquals("https://tmpfiles.org/dl/9876/upload.jpg", result.getOrNull())
            assertEquals(4, server.requestCount)
            assertEquals(ImageUploadRepository.UPLOAD_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
        } finally {
            tempFile.delete()
            server.shutdown()
        }
    }

    @Test
    fun uploadCompressedFile_reportsAggregatedErrorsWhenAllFiveHostsFail() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(502).setBody("err"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<!DOCTYPE html><html>cf</html>"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("err"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("err"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("err"))
        server.start()

        val tempFile = File.createTempFile("upload_all_fail_", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        try {
            val repo = ImageUploadRepository(
                context = null,
                okHttpClient = OkHttpClient(),
                litterboxUrl = server.url("/litterbox").toString(),
                catboxUrl = server.url("/catbox").toString(),
                uguuUrl = server.url("/uguu").toString(),
                tmpfilesUrl = server.url("/tmpfiles").toString(),
                fallbackUrl = server.url("/0x0").toString()
            )
            val result = repo.uploadCompressedFile(tempFile)
            assertTrue(result.isFailure)
            assertEquals(
                "Upload failed — litterbox: 502, catbox: invalid, uguu: 503, tmpfiles: 403, 0x0.st: 503",
                result.exceptionOrNull()?.message
            )
        } finally {
            tempFile.delete()
            server.shutdown()
        }
    }
}
