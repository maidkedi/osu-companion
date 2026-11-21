package com.osuchat.app

import com.osuchat.app.data.irc.IrcEvent
import com.osuchat.app.data.irc.IrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrcParserTest {

    @Test
    fun parseLine_handlesBlankAndMalformedLinesSafely() {
        assertNull(IrcParser.parseLine(""))
        assertNull(IrcParser.parseLine("   \r\n"))
        assertNull(IrcParser.parseLine("@time=2026-01-01T00:00:00Z"))
        assertNull(IrcParser.parseLine("@tag=1   "))
        assertNull(IrcParser.parseLine(":cho.ppy.sh"))
        assertNull(IrcParser.parseLine(":cho.ppy.sh   "))
        assertNull(IrcParser.parseLine("@tag=1 :cho.ppy.sh"))
    }

    @Test
    fun parseLine_parsesIrcv3TagsPrefixNickCommandParamsAndTrailing() {
        val tagged = IrcParser.parseLine("@time=123;account=peppy :peppy!cho@ppy.sh privmsg #osu :hello :world\r\n")
        requireNotNull(tagged)
        assertEquals("peppy!cho@ppy.sh", tagged.prefix)
        assertEquals("peppy", tagged.nick)
        assertEquals("PRIVMSG", tagged.command)
        assertEquals(listOf("#osu"), tagged.params)
        assertEquals("hello :world", tagged.trailing)

        val serverOnly = IrcParser.parseLine(":cho.ppy.sh   001   my_nick  :Welcome")
        requireNotNull(serverOnly)
        assertEquals("cho.ppy.sh", serverOnly.prefix)
        assertEquals("cho.ppy.sh", serverOnly.nick)
        assertEquals("001", serverOnly.command)
        assertEquals(listOf("my_nick"), serverOnly.params)
        assertEquals("Welcome", serverOnly.trailing)

        val noPrefix = IrcParser.parseLine("ping :cho.ppy.sh")
        requireNotNull(noPrefix)
        assertNull(noPrefix.prefix)
        assertNull(noPrefix.nick)
        assertEquals("PING", noPrefix.command)
        assertEquals("cho.ppy.sh", noPrefix.trailing)

        val nickWithAtOnly = IrcParser.parseLine(":bot@ppy.sh NOTICE #osu :hi")
        requireNotNull(nickWithAtOnly)
        assertEquals("bot", nickWithAtOnly.nick)
    }

    @Test
    fun toIrcEvent_parsesPrivMsgDirectMessageAndAction() {
        val channelMsg = IrcParser.toIrcEvent(":BanchoBot!cho@ppy.sh PRIVMSG #osu :Match history ready") as IrcEvent.PrivMsg
        assertEquals("BanchoBot", channelMsg.sender)
        assertEquals("#osu", channelMsg.target)
        assertEquals("Match history ready", channelMsg.text)
        assertFalse(channelMsg.isAction)

        val dmMsg = IrcParser.toIrcEvent(":Friend_1!cho@ppy.sh PRIVMSG MyNick :want to play?") as IrcEvent.PrivMsg
        assertEquals("Friend_1", dmMsg.sender)
        assertEquals("MyNick", dmMsg.target)
        assertEquals("want to play?", dmMsg.text)
        assertFalse(dmMsg.isAction)

        // Multi-word PRIVMSG without leading colon on trailing
        val noColonPrivMsg = IrcParser.toIrcEvent(":User1!cho@ppy.sh PRIVMSG #lobby hello there") as IrcEvent.PrivMsg
        assertEquals("hello there", noColonPrivMsg.text)
        assertFalse(noColonPrivMsg.isAction)

        val action = IrcParser.toIrcEvent(":cookiezi!cho@ppy.sh PRIVMSG #osu :\u0001ACTION is playing [osu!]\u0001") as IrcEvent.PrivMsg
        assertTrue(action.isAction)
        assertEquals("is playing [osu!]", action.text)

        // Case-insensitive ACTION and missing closing \u0001
        val unterminatedAction = IrcParser.toIrcEvent(":User2!cho@ppy.sh PRIVMSG #osu :\u0001action waves") as IrcEvent.PrivMsg
        assertTrue(unterminatedAction.isAction)
        assertEquals("waves", unterminatedAction.text)

        val bareAction = IrcParser.toIrcEvent(":User2!cho@ppy.sh PRIVMSG #osu :\u0001ACTION\u0001") as IrcEvent.PrivMsg
        assertTrue(bareAction.isAction)
        assertEquals("", bareAction.text)

        // Malformed PRIVMSG missing nick or target
        assertNull(IrcParser.toIrcEvent("PRIVMSG #osu :missing prefix"))
        assertNull(IrcParser.toIrcEvent(":User1!cho@ppy.sh PRIVMSG :missing target"))
    }

    @Test
    fun toIrcEvent_parsesCtcpAndMalformedCtcpFallback() {
        val version = IrcParser.toIrcEvent(":Alice!cho@ppy.sh PRIVMSG MyNick :\u0001VERSION\u0001") as IrcEvent.Ctcp
        assertEquals("Alice", version.sender)
        assertEquals("MyNick", version.target)
        assertEquals("VERSION", version.command)
        assertNull(version.args)

        val pingCtcp = IrcParser.toIrcEvent(":Alice!cho@ppy.sh PRIVMSG MyNick :\u0001ping 1700000000\u0001") as IrcEvent.Ctcp
        assertEquals("PING", pingCtcp.command)
        assertEquals("1700000000", pingCtcp.args)

        // Blank CTCP payload falls back to normal PrivMsg without crashing
        val emptyCtcp = IrcParser.toIrcEvent(":Alice!cho@ppy.sh PRIVMSG #osu :\u0001   \u0001") as IrcEvent.PrivMsg
        assertFalse(emptyCtcp.isAction)
        assertEquals("\u0001   \u0001", emptyCtcp.text)
    }

    @Test
    fun toIrcEvent_parsesNoticeErrorAndAuthNumerics() {
        val welcome = IrcParser.toIrcEvent(":cho.ppy.sh 001 mynick :Welcome to the osu!Bancho IRC") as IrcEvent.Connected
        assertEquals("cho.ppy.sh", welcome.serverHost)

        val welcomeNoPrefix = IrcParser.toIrcEvent("001 mynick :Welcome") as IrcEvent.Connected
        assertEquals("irc.ppy.sh", welcomeNoPrefix.serverHost)

        val errPasswd = IrcParser.toIrcEvent(":cho.ppy.sh 464 mynick :Bad authentication token.") as IrcEvent.AuthFailed
        assertEquals("Bad authentication token.", errPasswd.reason)

        val errPasswdDefault = IrcParser.toIrcEvent(":cho.ppy.sh 464 mynick") as IrcEvent.AuthFailed
        assertEquals("Invalid Bancho IRC credentials", errPasswdDefault.reason)

        val banned = IrcParser.toIrcEvent(":cho.ppy.sh 465 mynick :You are banned") as IrcEvent.AuthFailed
        assertEquals("You are banned", banned.reason)

        val notice = IrcParser.toIrcEvent(":cho.ppy.sh NOTICE * :Server restarting soon") as IrcEvent.ServerNotice
        assertEquals("Server restarting soon", notice.text)

        val badAuthNotice = IrcParser.toIrcEvent(":cho.ppy.sh NOTICE AUTH :Bad authentication token.") as IrcEvent.AuthFailed
        assertEquals("Bad authentication token.", badAuthNotice.reason)

        val authFailedNotice = IrcParser.toIrcEvent(":cho.ppy.sh NOTICE * :Authentication failed for user") as IrcEvent.AuthFailed
        assertEquals("Authentication failed for user", authFailedNotice.reason)

        val errPasswdNotice = IrcParser.toIrcEvent("ERROR :ERR_PASSWDMISMATCH") as IrcEvent.AuthFailed
        assertEquals("ERR_PASSWDMISMATCH", errPasswdNotice.reason)

        val errorLine = IrcParser.toIrcEvent("ERROR :Closing Link: timeout") as IrcEvent.ServerNotice
        assertEquals("Closing Link: timeout", errorLine.text)

        val errorNoTrailing = IrcParser.toIrcEvent("ERROR Closing Link") as IrcEvent.ServerNotice
        assertEquals("Closing Link", errorNoTrailing.text)

        assertNull(IrcParser.toIrcEvent(":cho.ppy.sh NOTICE *"))
    }

    @Test
    fun toIrcEvent_parsesNamesReplyAndNamesEnd() {
        val namReply = IrcParser.toIrcEvent(
            ":cho.ppy.sh 353 mynick = #osu :alice @mod_user +voiced %halfop ~owner &admin"
        ) as IrcEvent.NamesChunk
        assertEquals("#osu", namReply.channel)
        assertEquals(listOf("alice", "mod_user", "voiced", "halfop", "owner", "admin"), namReply.nicks)

        val emptyNames = IrcParser.toIrcEvent(":cho.ppy.sh 353 mynick @ &local :   ") as IrcEvent.NamesChunk
        assertEquals("&local", emptyNames.channel)
        assertTrue(emptyNames.nicks.isEmpty())

        assertNull(IrcParser.toIrcEvent(":cho.ppy.sh 353 mynick = :alice"))

        val namesEnd = IrcParser.toIrcEvent(":cho.ppy.sh 366 mynick #osu :End of /NAMES list.") as IrcEvent.NamesEnd
        assertEquals("#osu", namesEnd.channel)
        assertNull(IrcParser.toIrcEvent(":cho.ppy.sh 366 mynick :End of /NAMES list."))
    }

    @Test
    fun toIrcEvent_parsesJoinPartQuitNickPingAnd401() {
        val joinTrailing = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh JOIN :#turkish") as IrcEvent.UserJoined
        assertEquals("Player_1", joinTrailing.nick)
        assertEquals("#turkish", joinTrailing.channel)

        val joinParam = IrcParser.toIrcEvent(":Player_2!cho@ppy.sh JOIN #lobby") as IrcEvent.UserJoined
        assertEquals("Player_2", joinParam.nick)
        assertEquals("#lobby", joinParam.channel)
        assertNull(IrcParser.toIrcEvent(":Player_2!cho@ppy.sh JOIN"))

        val partWithReason = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh PART #osu :bye all") as IrcEvent.UserParted
        assertEquals("Player_1", partWithReason.nick)
        assertEquals("#osu", partWithReason.channel)
        assertEquals("bye all", partWithReason.reason)

        val partNoReason = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh PART #osu") as IrcEvent.UserParted
        assertEquals("#osu", partNoReason.channel)
        assertNull(partNoReason.reason)

        val partTrailingChannel = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh PART :#osu") as IrcEvent.UserParted
        assertEquals("#osu", partTrailingChannel.channel)
        assertNull(partTrailingChannel.reason)
        assertNull(IrcParser.toIrcEvent(":Player_1!cho@ppy.sh PART :not_a_channel"))

        val quitWithReason = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh QUIT :Quit: Leaving") as IrcEvent.UserQuit
        assertEquals("Player_1", quitWithReason.nick)
        assertEquals("Quit: Leaving", quitWithReason.reason)

        val quitNoReason = IrcParser.toIrcEvent(":Player_1!cho@ppy.sh QUIT") as IrcEvent.UserQuit
        assertNull(quitNoReason.reason)

        val nickTrailing = IrcParser.toIrcEvent(":OldNick!cho@ppy.sh NICK :NewNick") as IrcEvent.NickChanged
        assertEquals("OldNick", nickTrailing.oldNick)
        assertEquals("NewNick", nickTrailing.newNick)

        val nickParam = IrcParser.toIrcEvent(":OldNick!cho@ppy.sh NICK NewNick2") as IrcEvent.NickChanged
        assertEquals("NewNick2", nickParam.newNick)
        assertNull(IrcParser.toIrcEvent(":OldNick!cho@ppy.sh NICK"))

        val pingTrailing = IrcParser.toIrcEvent("PING :cho.ppy.sh") as IrcEvent.Ping
        assertEquals("cho.ppy.sh", pingTrailing.token)

        val pingParam = IrcParser.toIrcEvent("PING keepalive_1") as IrcEvent.Ping
        assertEquals("keepalive_1", pingParam.token)

        val noSuchNick = IrcParser.toIrcEvent(":cho.ppy.sh 401 mynick GhostUser :No such nick/channel") as IrcEvent.NoSuchNick
        assertEquals("GhostUser", noSuchNick.target)
        assertEquals("No such nick/channel", noSuchNick.reason)

        val noSuchNickDefaultReason = IrcParser.toIrcEvent(":cho.ppy.sh 401 mynick GhostUser :") as IrcEvent.NoSuchNick
        assertEquals("No such nick/channel", noSuchNickDefaultReason.reason)
        assertNull(IrcParser.toIrcEvent(":cho.ppy.sh 401"))

        // Unhandled numeric returns null cleanly
        assertNull(IrcParser.toIrcEvent(":cho.ppy.sh 376 mynick :End of /MOTD command."))
    }
}
