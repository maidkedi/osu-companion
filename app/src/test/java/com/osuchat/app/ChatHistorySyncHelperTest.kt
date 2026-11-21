package com.osuchat.app

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.osuchat.app.data.local.MessageEntity
import com.osuchat.app.data.network.ChatChannelDto
import com.osuchat.app.data.network.ChatMessageDto
import com.osuchat.app.data.network.OsuCreateChannelRequestDto
import com.osuchat.app.util.ChatHistorySyncHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistorySyncHelperTest {

    private val gson = Gson()

    @Test
    fun `parses ISO-8601 timestamps across UTC offsets and falls back safely`() {
        val fallback = 1_700_000_000_000L
        val utcMillis = ChatHistorySyncHelper.parseIso8601TimestampMillis(
            "2026-11-20T12:00:00Z",
            fallbackMillis = fallback
        )
        val offsetZeroMillis = ChatHistorySyncHelper.parseIso8601TimestampMillis(
            "2026-11-20T12:00:00+00:00",
            fallbackMillis = fallback
        )
        val plusThreeMillis = ChatHistorySyncHelper.parseIso8601TimestampMillis(
            "2026-11-20T15:00:00+03:00",
            fallbackMillis = fallback
        )
        val fractionalMillis = ChatHistorySyncHelper.parseIso8601TimestampMillis(
            "2026-11-20T12:00:00.250+00:00",
            fallbackMillis = fallback
        )

        assertEquals(utcMillis, offsetZeroMillis)
        assertEquals(utcMillis, plusThreeMillis)
        assertEquals(utcMillis + 250L, fractionalMillis)
        assertEquals(fallback, ChatHistorySyncHelper.parseIso8601TimestampMillis(null, fallback))
        assertEquals(fallback, ChatHistorySyncHelper.parseIso8601TimestampMillis("  ", fallback))
        assertEquals(fallback, ChatHistorySyncHelper.parseIso8601TimestampMillis("not-a-timestamp", fallback))
    }

    @Test
    fun `deduplicates API messages by deterministic id and 15s IRC window`() {
        val baseTs = 1_700_000_000_000L
        val existing = listOf(
            apiEntity(id = "api_100", sender = "peppy", content = "already stored", ts = baseTs),
            apiEntity(id = "101", sender = "peppy", content = "legacy numeric", ts = baseTs + 500L),
            apiEntity(id = "irc_1", sender = "Cookie_User", content = "hello world", ts = baseTs + 1_000L),
            apiEntity(id = "local_1", sender = "Me", content = "my outgoing msg", ts = baseTs + 2_000L, isOutgoing = true),
            apiEntity(id = "irc_2", sender = "Alice", content = "boundary match", ts = baseTs + 10_000L)
        )

        val candidates = listOf(
            // Duplicate by api_<id>
            apiEntity(id = "api_100", sender = "peppy", content = "already stored", ts = baseTs),
            // Duplicate by legacy numeric id
            apiEntity(id = "api_101", sender = "peppy", content = "legacy numeric", ts = baseTs + 500L),
            // Matches IRC row with normalized nick (space vs underscore, case) and +10s delta <= 15s
            apiEntity(id = "api_102", sender = "cookie user", content = "  hello world  ", ts = baseTs + 11_000L),
            // Matches local outgoing "Me" row within 15s
            apiEntity(id = "api_103", sender = "ActualUsername", content = "my outgoing msg", ts = baseTs + 5_000L, isOutgoing = true),
            // Exact 15_000ms boundary -> skipped
            apiEntity(id = "api_104", sender = "Alice", content = "boundary match", ts = baseTs + 25_000L),
            // 15_001ms delta -> NOT duplicate, kept
            apiEntity(id = "api_105", sender = "Alice", content = "boundary match", ts = baseTs + 25_001L),
            // Duplicate within the same candidate batch
            apiEntity(id = "api_105", sender = "Alice", content = "boundary match", ts = baseTs + 25_001L),
            // Distinct message
            apiEntity(id = "api_106", sender = "Bob", content = "brand new message", ts = baseTs + 30_000L)
        )

        val filtered = ChatHistorySyncHelper.filterNewApiMessages(candidates, existing)
        assertEquals(listOf("api_105", "api_106"), filtered.map { it.id })
    }

    @Test
    fun `performs 1-to-1 IRC matching so repeated identical API messages are preserved`() {
        val baseTs = 1_700_000_000_000L
        val existing = listOf(
            apiEntity(id = "irc_1", sender = "peppy", content = "gg", ts = baseTs)
        )
        val candidates = listOf(
            apiEntity(id = "api_201", sender = "peppy", content = "gg", ts = baseTs + 1_000L),
            apiEntity(id = "api_202", sender = "peppy", content = "gg", ts = baseTs + 2_000L)
        )

        val filtered = ChatHistorySyncHelper.filterNewApiMessages(candidates, existing)
        assertEquals(listOf("api_202"), filtered.map { it.id })
    }

    @Test
    fun `history request and channel DTOs serialize and parse snake_case fields`() {
        val reqJson = JsonParser.parseString(
            gson.toJson(OsuCreateChannelRequestDto(type = "PM", targetId = 777L))
        ).asJsonObject
        assertEquals("PM", reqJson.get("type").asString)
        assertEquals(777L, reqJson.get("target_id").asLong)

        val defaultReq = gson.fromJson("{}", OsuCreateChannelRequestDto::class.java)
        assertEquals("PM", defaultReq.type)
        assertEquals(0L, defaultReq.targetId)

        val channelDto = gson.fromJson(
            """
            {
              "channel_id": 42,
              "name": "#osu",
              "type": "PUBLIC",
              "users": [1, 2],
              "recent_messages": [
                {
                  "message_id": 9001,
                  "sender_id": 2,
                  "channel_id": 42,
                  "timestamp": "2026-11-20T12:00:00+00:00",
                  "content": "hi",
                  "is_action": true,
                  "sender": null
                },
                {
                  "message_id": 9002,
                  "sender_id": 3,
                  "channel_id": 42,
                  "timestamp": "2026-11-20T12:01:00Z",
                  "content": "sup",
                  "is_action": false,
                  "sender": {
                    "id": 3,
                    "username": "SupporterUser",
                    "profile_colour": "#FF66AA",
                    "profile_hue": 333,
                    "is_supporter": true
                  }
                }
              ]
            }
            """.trimIndent(),
            ChatChannelDto::class.java
        )
        assertEquals(42L, channelDto.channelId)
        assertEquals(listOf(1L, 2L), channelDto.users)
        assertEquals(2, channelDto.recentMessages?.size)
        val firstMsg: ChatMessageDto = channelDto.recentMessages!![0]
        assertTrue(firstMsg.isAction)
        assertNull(firstMsg.sender)
        val secondMsg: ChatMessageDto = channelDto.recentMessages!![1]
        assertFalse(secondMsg.isAction)
        assertEquals("#FF66AA", secondMsg.sender?.profileColour)
        assertEquals(333, secondMsg.sender?.profileHue)
        assertTrue(secondMsg.sender?.isSupporter == true)
    }

    private fun apiEntity(
        id: String,
        sender: String,
        content: String,
        ts: Long,
        isOutgoing: Boolean = false
    ): MessageEntity = MessageEntity(
        id = id,
        channelId = "#osu",
        senderId = 1L,
        senderName = sender,
        content = content,
        timestamp = ts,
        isOutgoing = isOutgoing
    )
}
