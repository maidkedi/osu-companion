package com.osuchat.app

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.osuchat.app.data.network.ChatMessageDto
import com.osuchat.app.data.network.CreatePmRequest
import com.osuchat.app.data.network.CreatePmResponse
import com.osuchat.app.data.network.SendChannelMessageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendDtoTest {

    private val gson = Gson()

    @Test
    fun `chat send request DTOs serialize expected snake_case keys`() {
        val sendReqJson = JsonParser.parseString(
            gson.toJson(SendChannelMessageRequest(message = "hello", isAction = true))
        ).asJsonObject
        assertEquals("hello", sendReqJson.get("message").asString)
        assertTrue(sendReqJson.get("is_action").asBoolean)

        val pmReqJson = JsonParser.parseString(
            gson.toJson(CreatePmRequest(targetId = 12345L, message = "hi", isAction = false))
        ).asJsonObject
        assertEquals(12345L, pmReqJson.get("target_id").asLong)
        assertEquals("hi", pmReqJson.get("message").asString)
        assertFalse(pmReqJson.get("is_action").asBoolean)

        val defaultSendReq = gson.fromJson("{}", SendChannelMessageRequest::class.java)
        assertEquals("", defaultSendReq.message)
        assertFalse(defaultSendReq.isAction)

        val defaultPmReq = gson.fromJson("{}", CreatePmRequest::class.java)
        assertEquals(0L, defaultPmReq.targetId)
        assertEquals("", defaultPmReq.message)
        assertFalse(defaultPmReq.isAction)
    }

    @Test
    fun `chat send response DTOs tolerate empty nulls and full osu v2 payloads`() {
        val emptyPmRes = gson.fromJson("{}", CreatePmResponse::class.java)
        assertNull(emptyPmRes.channel)
        assertNull(emptyPmRes.message)
        assertNull(emptyPmRes.newChannelId)

        val emptyMsgRes = gson.fromJson("{}", ChatMessageDto::class.java)
        assertEquals(0L, emptyMsgRes.messageId)
        assertEquals(0L, emptyMsgRes.senderId)
        assertEquals(0L, emptyMsgRes.channelId)
        assertNull(emptyMsgRes.content)
        assertNull(emptyMsgRes.sender)
        assertFalse(emptyMsgRes.isAction)

        val nullFieldsPmRes = gson.fromJson(
            """{"channel":null,"message":null,"new_channel_id":null}""",
            CreatePmResponse::class.java
        )
        assertNull(nullFieldsPmRes.channel)
        assertNull(nullFieldsPmRes.message)
        assertNull(nullFieldsPmRes.newChannelId)

        val fullPmRes = gson.fromJson(
            """
            {
              "new_channel_id": 9988,
              "channel": {
                "channel_id": 9988,
                "name": "FriendName",
                "type": "PM",
                "users": [101, 202]
              },
              "message": {
                "message_id": 555666,
                "sender_id": 101,
                "channel_id": 9988,
                "timestamp": "2026-11-20T10:00:00Z",
                "content": "ping",
                "is_action": false,
                "sender": {
                  "id": 101,
                  "username": "Me"
                }
              }
            }
            """.trimIndent(),
            CreatePmResponse::class.java
        )
        assertEquals(9988L, fullPmRes.newChannelId)
        assertEquals(9988L, fullPmRes.channel?.channelId)
        assertEquals("PM", fullPmRes.channel?.type)
        assertEquals(555666L, fullPmRes.message?.messageId)
        assertEquals("ping", fullPmRes.message?.content)
        assertEquals("Me", fullPmRes.message?.sender?.username)
    }
}
