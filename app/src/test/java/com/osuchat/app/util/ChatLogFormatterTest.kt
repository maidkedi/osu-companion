package com.osuchat.app.util

import com.osuchat.app.domain.model.ChatLogExportFormat
import com.osuchat.app.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ChatLogFormatterTest {

    @Test
    fun formatPlainText_includesMpLinkAndActions() {
        val messages = listOf(
            ChatMessage(
                id = "1",
                channelId = "#mp_115241345",
                senderName = "BanchoBot",
                content = "created the match",
                timestamp = 1_700_000_000_000L,
                isOutgoing = false,
                isAction = true
            ),
            ChatMessage(
                id = "2",
                channelId = "#mp_115241345",
                senderName = "peppy",
                content = "glhf",
                timestamp = 1_700_000_060_000L,
                isOutgoing = true,
                isAction = false
            )
        )

        val output = ChatLogFormatter.format(
            channelName = "#mp_115241345",
            channelId = "#mp_115241345",
            messages = messages,
            format = ChatLogExportFormat.PLAIN_TEXT,
            exportedAtMillis = 1_700_000_120_000L,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(output.contains("Channel: #mp_115241345"))
        assertTrue(output.contains("Messages: 2"))
        assertTrue(output.contains("Match History: https://osu.ppy.sh/mp/115241345"))
        assertTrue(output.contains("[2023-11-14 22:13:20] * BanchoBot created the match"))
        assertTrue(output.contains("[2023-11-14 22:14:20] peppy: glhf"))
    }

    @Test
    fun formatMarkdown_nonMpOmitsMatchHistoryLink() {
        val messages = listOf(
            ChatMessage(
                id = "1",
                channelId = "#osu",
                senderName = "Alice",
                content = "hi!",
                timestamp = 1_700_000_000_000L,
                isOutgoing = false
            )
        )

        val output = ChatLogFormatter.format(
            channelName = "#osu",
            channelId = "#osu",
            messages = messages,
            format = ChatLogExportFormat.MARKDOWN,
            exportedAtMillis = 1_700_000_060_000L,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(output.contains("# Chat Log: #osu"))
        assertTrue(output.contains("- **Messages:** 1"))
        assertFalse(output.contains("Match History"))
        assertTrue(output.contains("- `[2023-11-14 22:13:20]` **Alice**: hi!"))
    }

    @Test
    fun buildDefaultFilename_sanitizesAndFormatsStamp() {
        val name = ChatLogFormatter.buildDefaultFilename(
            channelName = "#mp_998877",
            channelId = "#mp_998877",
            format = ChatLogExportFormat.PLAIN_TEXT,
            timestampMillis = 1_700_000_000_000L,
            zoneId = ZoneOffset.UTC
        )
        assertEquals("osucompanion_mp_998877_20231114_2213.txt", name)
    }
}
