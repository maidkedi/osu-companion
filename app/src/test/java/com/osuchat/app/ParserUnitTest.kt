package com.osuchat.app

import com.osuchat.app.data.irc.IrcEvent
import com.osuchat.app.data.irc.IrcParser
import com.osuchat.app.data.network.OAuthConfig
import com.osuchat.app.data.network.OAuthPkceManager
import com.osuchat.app.util.BeatmapUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUnitTest {

    @Test
    fun parsesBeatmapUrlsCorrectly() {
        val text = "check out https://osu.ppy.sh/beatmapsets/123456#osu/789012 and old.ppy.sh/b/55555 or https://osu.ppy.sh/s/9999"
        val links = BeatmapUrlParser.extractBeatmapLinks(text)
        assertEquals(3, links.size)
        assertEquals(789012L, links[0].beatmapId)
        assertEquals(123456L, links[0].beatmapsetId)
        assertEquals(55555L, links[1].beatmapId)
        assertEquals(9999L, links[2].beatmapsetId)
    }

    @Test
    fun parsesIrcPrivMsgAndAction() {
        val normal = IrcParser.toIrcEvent(":BanchoBot!cho@ppy.sh PRIVMSG #osu :Welcome to osu!")
        assertTrue(normal is IrcEvent.PrivMsg)
        normal as IrcEvent.PrivMsg
        assertEquals("BanchoBot", normal.sender)
        assertEquals("#osu", normal.target)
        assertEquals("Welcome to osu!", normal.text)
        assertEquals(false, normal.isAction)

        val action = IrcParser.toIrcEvent(":cookiezi!cho@ppy.sh PRIVMSG #osu :\u0001ACTION is listening to Freedom Dive\u0001")
        assertTrue(action is IrcEvent.PrivMsg)
        action as IrcEvent.PrivMsg
        assertEquals(true, action.isAction)
        assertEquals("is listening to Freedom Dive", action.text)
    }

    @Test
    fun buildsLoopbackOAuthAuthorizeUrlWithoutPkce() {
        assertEquals(
            listOf("public", "identify", "friends.read", "chat.write"),
            OAuthConfig.SCOPES
        )
        assertFalse(OAuthConfig.SUPPORTS_CHAT_READ_HISTORY)

        val explicitStateUrl = OAuthPkceManager().buildAuthorizeUrl("12345", state = "test-state")
        assertEquals(
            "https://osu.ppy.sh/oauth/authorize?client_id=12345&redirect_uri=http%3A%2F%2F127.0.0.1%3A42069%2Fcallback&response_type=code&scope=public+identify+friends.read+chat.write&state=test-state",
            explicitStateUrl
        )

        val manager = OAuthPkceManager()
        val defaultStateUrl = manager.buildAuthorizeUrl("12345")
        val prefix =
            "https://osu.ppy.sh/oauth/authorize?client_id=12345&redirect_uri=http%3A%2F%2F127.0.0.1%3A42069%2Fcallback&response_type=code&scope=public+identify+friends.read+chat.write&state="
        assertTrue(defaultStateUrl.startsWith(prefix))
        val generatedState = defaultStateUrl.removePrefix(prefix)
        assertTrue(generatedState.isNotBlank())
        assertTrue(manager.isExpectedStateValid(generatedState))
    }
}
