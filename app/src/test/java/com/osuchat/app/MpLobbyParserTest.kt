package com.osuchat.app

import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpSlotStatus
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.SeriesRoundRecord
import com.osuchat.app.util.MpLobbyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpLobbyParserTest {

    private fun botMsg(id: String, content: String) = ChatMessage(
        id = id,
        channelId = "#mp_123456",
        senderName = "BanchoBot",
        content = content,
        timestamp = 1L,
        isOutgoing = false
    )

    private fun userMsg(id: String, sender: String, content: String) = ChatMessage(
        id = id,
        channelId = "#mp_123456",
        senderName = sender,
        content = content,
        timestamp = 2L,
        isOutgoing = true
    )

    @Test
    fun extractMatchIdAndCreatedResponse_handleAllUrlVariants() {
        assertEquals(987654L, MpLobbyParser.extractMatchId("987654"))
        assertEquals(987654L, MpLobbyParser.extractMatchId(" #mp_987654 "))
        assertEquals(987654L, MpLobbyParser.extractMatchId("https://osu.ppy.sh/mp/987654"))
        assertEquals(987654L, MpLobbyParser.extractMatchId("old.ppy.sh/community/matches/987654"))
        assertNull(MpLobbyParser.extractMatchId("#osu"))
        assertNull(MpLobbyParser.extractMatchId(""))

        val created = MpLobbyParser.parseCreatedMatchResponse(
            "Created the tournament match https://osu.ppy.sh/mp/445566 Summer Cup 1v1"
        )
        assertEquals(445566L to "Summer Cup 1v1", created)

        val createdOldUrlNoName = MpLobbyParser.parseCreatedMatchResponse(
            "Created the tournament match http://old.ppy.sh/community/matches/445566"
        )
        assertEquals(445566L to "", createdOldUrlNoName)

        assertNull(MpLobbyParser.parseCreatedMatchResponse("Joined lobby #mp_445566"))
    }

    @Test
    fun commandBuilders_sanitizeAndClampArguments() {
        assertEquals("!mp settings", MpLobbyParser.buildSettingsCommand())
        assertEquals("!mp start", MpLobbyParser.buildStartCommand(null))
        assertEquals("!mp start", MpLobbyParser.buildStartCommand(0))
        assertEquals("!mp start 15", MpLobbyParser.buildStartCommand(15))
        assertEquals("!mp start 300", MpLobbyParser.buildStartCommand(999))
        assertEquals("!mp abort", MpLobbyParser.buildAbortCommand())
        assertEquals("!mp close", MpLobbyParser.buildCloseCommand())
        assertEquals("!mp lock", MpLobbyParser.buildLockCommand(true))
        assertEquals("!mp unlock", MpLobbyParser.buildLockCommand(false))
        assertEquals("!mp size 1", MpLobbyParser.buildSizeCommand(0))
        assertEquals("!mp size 16", MpLobbyParser.buildSizeCommand(32))
        assertEquals("!mp set 2 3", MpLobbyParser.buildSetCommand(2, 3))
        assertEquals("!mp set 3 3 16", MpLobbyParser.buildSetCommand(9, 9, 24))
        assertEquals("!mp map 12345", MpLobbyParser.buildMapCommand(12345L))
        assertEquals("!mp map 12345 3", MpLobbyParser.buildMapCommand(12345L, 8))
        assertNull(MpLobbyParser.buildMapCommand(-1L))
        assertEquals("!mp mods DT HD", MpLobbyParser.buildModsCommand(listOf("DT", "HD", "dt", "None", "  ")))
        assertEquals("!mp mods None", MpLobbyParser.buildModsCommand(listOf("None", "")))
        assertEquals("!mp invite Player_With_Spaces", MpLobbyParser.buildInviteCommand("Player With Spaces"))
        assertEquals("!mp invite #42", MpLobbyParser.buildInviteCommand("Player With Spaces", userId = 42L))
        assertNull(MpLobbyParser.buildInviteCommand("   "))
    }

    @Test
    fun parseLobbyState_parsesSettingsDumpTeamVsScoreV2FreeModAndSpacesInNames() {
        val settingsDump = """
            Room name: OWC Practice, Qualifier #2, History: https://osu.ppy.sh/mp/123456
            Beatmap: https://osu.ppy.sh/beatmapsets/1001#osu/789012 Camellia - Exit This Earth's Atomosphere [Evolution]
            Team mode: TeamVs, Win condition: ScoreV2
            Active mods: Hidden, DoubleTime, Freemod
            Players: 4
            Slot 1  Ready     https://osu.ppy.sh/u/101 Player With Spaces [Host / Team Red]
            Slot 2  Not Ready https://old.ppy.sh/users/102 Second Player [Team Blue / Hidden, HardRock]
            Slot 3  No Map    https://osu.ppy.sh/u/103 [Tourney] Name [2] [Team Red / DoubleTime]
            Slot 4  Ready     https://osu.ppy.sh/u/104 Plain User [1]
        """.trimIndent()

        val state = MpLobbyParser.parseLobbyState(
            channelId = "#mp_123456",
            channelName = "#mp_123456",
            messages = listOf(botMsg("1", settingsDump))
        )

        assertEquals(123456L, state.matchId)
        assertEquals("OWC Practice, Qualifier #2", state.roomName)
        assertEquals("https://osu.ppy.sh/mp/123456", state.historyUrl)
        assertEquals(789012L, state.beatmapId)
        assertEquals("Camellia - Exit This Earth's Atomosphere [Evolution]", state.beatmapTitle)
        assertEquals("https://osu.ppy.sh/beatmapsets/1001#osu/789012", state.beatmapUrl)
        assertEquals("TeamVs", state.teamMode)
        assertEquals("ScoreV2", state.winCondition)
        assertEquals("Hidden, DoubleTime, Freemod", state.activeMods)
        assertTrue(state.isFreeMod)
        assertEquals("Player With Spaces", state.hostUsername)

        val slot1 = state.slots[0]
        assertEquals("Player With Spaces", slot1.username)
        assertEquals(101L, slot1.userId)
        assertEquals(MpSlotStatus.READY, slot1.status)
        assertEquals(MpTeam.RED, slot1.team)
        assertTrue(slot1.isHost)
        assertEquals("", slot1.mods)

        val slot2 = state.slots[1]
        assertEquals("Second Player", slot2.username)
        assertEquals(102L, slot2.userId)
        assertEquals(MpSlotStatus.NOT_READY, slot2.status)
        assertEquals(MpTeam.BLUE, slot2.team)
        assertFalse(slot2.isHost)
        assertEquals("Hidden, HardRock", slot2.mods)

        val slot3 = state.slots[2]
        assertEquals("[Tourney] Name [2]", slot3.username)
        assertEquals(MpSlotStatus.NO_MAP, slot3.status)
        assertEquals(MpTeam.RED, slot3.team)
        assertEquals("DoubleTime", slot3.mods)

        val slot4 = state.slots[3]
        assertEquals("Plain User [1]", slot4.username)
        assertEquals(MpSlotStatus.READY, slot4.status)
        assertEquals(MpTeam.NONE, slot4.team)
        assertEquals("", slot4.mods)
    }

    @Test
    fun parseLobbyState_tracksMpHostVariantsAndHostClearing() {
        val messages = listOf(
            botMsg("1", "Player One joined in slot 1."),
            botMsg("2", "Player Two joined in slot 2."),
            botMsg("3", "Changed match host to Player One"),
            botMsg("4", "Player Two became the host.")
        )

        val state = MpLobbyParser.parseLobbyState("#mp_123456", "#mp_123456", messages)
        assertEquals("Player Two", state.hostUsername)
        assertFalse(state.slots[0].isHost)
        assertTrue(state.slots[1].isHost)

        val cleared = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            messages + botMsg("5", "Cleared match host")
        )
        assertNull(cleared.hostUsername)
        assertFalse(cleared.slots[0].isHost)
        assertFalse(cleared.slots[1].isHost)
    }

    @Test
    fun parseLobbyState_tracksMpModsAndFreeModTransitions() {
        val enableFreeModOnly = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(botMsg("1", "Enabled FreeMod"))
        )
        assertTrue(enableFreeModOnly.isFreeMod)
        assertEquals("Freemod", enableFreeModOnly.activeMods)

        val enableDtHdDisableFreeMod = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(
                botMsg("1", "Enabled FreeMod"),
                botMsg("2", "Enabled Hidden, DoubleTime, disabled FreeMod")
            )
        )
        assertFalse(enableDtHdDisableFreeMod.isFreeMod)
        assertEquals("Hidden, DoubleTime", enableDtHdDisableFreeMod.activeMods)

        val disableAllPreservingFreeMod = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(
                botMsg("1", "Enabled Hidden, enabled FreeMod"),
                botMsg("2", "Disabled all mods")
            )
        )
        assertTrue(disableAllPreservingFreeMod.isFreeMod)
        assertEquals("Freemod", disableAllPreservingFreeMod.activeMods)

        val disableAllAndFreeMod = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(
                botMsg("1", "Enabled FreeMod"),
                botMsg("2", "Disabled all mods, disabled FreeMod")
            )
        )
        assertFalse(disableAllAndFreeMod.isFreeMod)
        assertEquals("None", disableAllAndFreeMod.activeMods)

        val disabledFreeModAlone = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(
                botMsg("1", "Enabled Hidden, enabled FreeMod"),
                botMsg("2", "Disabled FreeMod")
            )
        )
        assertFalse(disabledFreeModAlone.isFreeMod)
        assertEquals("Hidden", disabledFreeModAlone.activeMods)

        val disabledFreeModToNone = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            listOf(
                botMsg("1", "Enabled FreeMod"),
                botMsg("2", "Disabled FreeMod")
            )
        )
        assertFalse(disabledFreeModToNone.isFreeMod)
        assertEquals("None", disabledFreeModToNone.activeMods)
    }

    @Test
    fun parseLobbyState_parsesChangedMatchSettingsBeatmapWithoutColonAndUnderscoreNames() {
        val messages = listOf(
            botMsg("1", "Changed match settings to 8 slots, TeamVs, ScoreV2"),
            botMsg("2", "Changed beatmap to https://osu.ppy.sh/b/667788 Song - Map [Extra]"),
            botMsg("3", "Slot 1  Ready https://osu.ppy.sh/u/501 Player_With_Spaces [Host / Classic]"),
            botMsg("4", "Slot 2  Ready https://osu.ppy.sh/u/502 Another User [NoMod]"),
            botMsg("5", "Player With Spaces moved to slot 4.")
        )

        val state = MpLobbyParser.parseLobbyState("#mp_123456", "#mp_123456", messages)
        assertEquals(8, state.roomSize)
        assertEquals("TeamVs", state.teamMode)
        assertEquals("ScoreV2", state.winCondition)
        assertEquals(667788L, state.beatmapId)
        assertEquals("Song - Map [Extra]", state.beatmapTitle)

        // Underscore/space fallback moves slot 1 -> slot 4 cleanly
        assertNull(state.slots[0].username)
        assertEquals("Player_With_Spaces", state.slots[3].username)
        assertTrue(state.slots[3].isHost)
        assertEquals("Classic", state.slots[3].mods)

        assertEquals("Another User", state.slots[1].username)
        assertEquals("NoMod", state.slots[1].mods)
    }

    @Test
    fun parseLobbyState_tracksMpMapStartFinishAbortLockSizeAndLeave() {
        val messages = listOf(
            botMsg("1", "Room name: Match Room, History: https://osu.ppy.sh/mp/123456"),
            botMsg("2", "Alice Wonderland joined in slot 1 for team blue."),
            botMsg("3", "Bob Ross joined in slot 2 for team red."),
            botMsg("4", "Changed match host to Alice Wonderland"),
            botMsg("5", "Changed beatmap to: https://osu.ppy.sh/b/55555 Artist - Map [Insane]"),
            botMsg("6", "Changed match size to 8"),
            botMsg("7", "Locked the match"),
            botMsg("8", "All players are ready"),
            botMsg("9", "The match has started!")
        )

        val inProgress = MpLobbyParser.parseLobbyState("#mp_123456", "#mp_123456", messages)
        assertEquals(55555L, inProgress.beatmapId)
        assertEquals("Artist - Map [Insane]", inProgress.beatmapTitle)
        assertEquals(8, inProgress.roomSize)
        assertTrue(inProgress.isLocked)
        assertTrue(inProgress.isMatchInProgress)
        assertEquals(MpSlotStatus.PLAYING, inProgress.slots[0].status)
        assertEquals(MpSlotStatus.PLAYING, inProgress.slots[1].status)

        val finished = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            messages + listOf(
                botMsg("10", "The match has finished!"),
                botMsg("11", "Unlocked the match"),
                botMsg("12", "Alice Wonderland left the game.")
            )
        )
        assertFalse(finished.isMatchInProgress)
        assertFalse(finished.isLocked)
        assertNull(finished.slots[0].username)
        assertEquals(MpSlotStatus.EMPTY, finished.slots[0].status)
        assertNull(finished.hostUsername)
        assertEquals("Bob Ross", finished.slots[1].username)
        assertEquals(MpSlotStatus.NOT_READY, finished.slots[1].status)

        val aborted = MpLobbyParser.parseLobbyState(
            "#mp_123456",
            "#mp_123456",
            messages + botMsg("10", "Aborted the match")
        )
        assertFalse(aborted.isMatchInProgress)
        assertEquals(MpSlotStatus.NOT_READY, aborted.slots[0].status)
    }

    @Test
    fun parseLobbyState_ignoresNonBanchoBotChatExceptMpCloseAndResetsOnNewSettingsDump() {
        val messages = listOf(
            userMsg("1", "RandomPlayer", "The match has started!"),
            userMsg("2", "RandomPlayer", "Changed match host to RandomPlayer"),
            botMsg(
                "3",
                """
                Room name: First Dump, History: https://osu.ppy.sh/mp/123456
                Beatmap: https://osu.ppy.sh/b/101 Old [Easy]
                Players: 1
                Slot 1  Ready https://osu.ppy.sh/u/10 OldUser [Host]
                """.trimIndent()
            ),
            botMsg(
                "4",
                """
                Room name: Second Dump, History: https://osu.ppy.sh/mp/123456
                Beatmap: https://osu.ppy.sh/b/202 New [Hard]
                Players: 1
                Slot 2  Not Ready https://osu.ppy.sh/u/20 NewUser
                """.trimIndent()
            )
        )

        val state = MpLobbyParser.parseLobbyState("#mp_123456", "#mp_123456", messages)
        assertFalse(state.isMatchInProgress)
        assertEquals("Second Dump", state.roomName)
        assertEquals(202L, state.beatmapId)
        assertNull(state.hostUsername)
        assertNull(state.slots[0].username)
        assertEquals("NewUser", state.slots[1].username)
    }

    @Test
    fun extractTournamentTeams_andFinishedRoundScores_andDiscordSummary_workCorrectly() {
        assertEquals("Team Alpha" to "Team Beta", MpLobbyParser.extractTournamentTeams("OWC: (Team Alpha) vs (Team Beta)"))
        assertEquals("USA" to "Japan", MpLobbyParser.extractTournamentTeams("QF: (USA) vs. (Japan)"))
        assertNull(MpLobbyParser.extractTournamentTeams("Chill Multi Lobby #1"))

        val messages = listOf(
            botMsg(
                "1",
                """
                Room name: OWC: (Team Red) vs (Team Blue), History: https://osu.ppy.sh/mp/999111
                Beatmap: https://osu.ppy.sh/b/55555 Song - Map [Diff]
                Team mode: TeamVs, Win condition: ScoreV2
                Players: 2
                Slot 1  Ready https://osu.ppy.sh/u/101 RedPlayer [Team Red]
                Slot 2  Ready https://osu.ppy.sh/u/102 BluePlayer [Team Blue]
                """.trimIndent()
            ),
            botMsg("2", "The match has started!"),
            botMsg("3", "RedPlayer finished playing (Score: 1,452,100, PASSED)"),
            botMsg("4", "BluePlayer finished playing (Score: 1,210,400, PASSED)"),
            botMsg("5", "The match has finished!")
        )

        val state = MpLobbyParser.parseLobbyState("#mp_999111", "#mp_999111", messages)
        assertEquals(1, state.finishedRounds.size)
        val round = state.finishedRounds.first()
        assertEquals(1_452_100L, round.redTotalScore)
        assertEquals(1_210_400L, round.blueTotalScore)
        assertEquals("TeamVs", round.teamMode)

        val pool = Mappool(
            id = 1L,
            name = "OWC QF",
            slots = listOf(
                MappoolSlot(id = 10L, poolId = 1L, slotTag = "NM1", beatmapId = 55555L, mods = "NF", orderIndex = 0),
                MappoolSlot(id = 11L, poolId = 1L, slotTag = "HD1", beatmapId = 66666L, mods = "NF HD", orderIndex = 1),
                MappoolSlot(id = 12L, poolId = 1L, slotTag = "DT1", beatmapId = 77777L, mods = "NF DT", orderIndex = 2)
            )
        )
        val series = MatchSeriesState(
            channelId = "#mp_999111",
            bestOf = 9,
            redTeamName = "Team Red",
            blueTeamName = "Team Blue",
            redScore = 5,
            blueScore = 3,
            slotStates = mapOf(
                11L to MpPoolSlotState.BANNED_RED,
                12L to MpPoolSlotState.BANNED_BLUE
            ),
            rounds = listOf(
                SeriesRoundRecord(
                    slotTag = "NM1",
                    beatmapLabel = "Song - Map [Diff]",
                    pickedBy = MpTeam.RED,
                    winner = MpTeam.RED,
                    redTotalScore = 1_452_100L,
                    blueTotalScore = 1_210_400L
                )
            )
        )

        val macro = MpLobbyParser.resolveRefereeMacro(
            "{red} {score} {blue} @ {mp}",
            series,
            state
        )
        assertEquals("Team Red 5 - 3 Team Blue @ https://osu.ppy.sh/mp/999111", macro)

        val summary = MpLobbyParser.formatDiscordMatchSummary(state, series, pool)
        assertTrue(summary.contains("**OWC: (Team Red) vs (Team Blue)** (Bo9)"))
        assertTrue(summary.contains("**Team Red** 5 - 3 **Team Blue**"))
        assertTrue(summary.contains("https://osu.ppy.sh/mp/999111"))
        assertTrue(summary.contains("Team Red Bans: HD1"))
        assertTrue(summary.contains("Team Blue Bans: DT1"))
        assertTrue(summary.contains("1. [Red Pick] NM1 (Song - Map [Diff]) — Team Red wins (1,452,100 vs 1,210,400)"))
    }
}
