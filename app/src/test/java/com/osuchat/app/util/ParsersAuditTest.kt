package com.osuchat.app.util

import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.BeatmapRecommendationPreset
import com.osuchat.app.domain.model.BeatmapSetSearchResult
import com.osuchat.app.domain.model.ChatInlineEmbed
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.MatchAnalysisGame
import com.osuchat.app.domain.model.MatchAnalysisPlayerScore
import com.osuchat.app.domain.model.MatchAnalysisReport
import com.osuchat.app.domain.model.MpSlotStatus
import com.osuchat.app.domain.model.MpTeam
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.domain.model.PlayerRecommendationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersAuditTest {

    private fun botMsg(id: String, content: String) = ChatMessage(
        id = id,
        channelId = "#mp_123456",
        senderName = "BanchoBot",
        content = content,
        timestamp = 1L,
        isOutgoing = false
    )

    @Test
    fun mpCommandBuildersHandleSpacesBracketsAndIdsSafely() {
        assertEquals("!mp make My [Tourney] #1", MpLobbyParser.buildMakeCommand("My\n[Tourney]\r\n#1"))
        assertNull(MpLobbyParser.buildMakeCommand("   \n\t  "))

        assertEquals("!mp map 123456 0", MpLobbyParser.buildMapCommand(123456L, 0))
        assertNull(MpLobbyParser.buildMapCommand(0L, 0))

        assertEquals("!mp mods HD HR", MpLobbyParser.buildModsCommand(listOf("HD", "HR", "hd", "None")))
        assertEquals("!mp mods None", MpLobbyParser.buildModsCommand(emptyList()))

        // When userId is known, prefer #userId so spaces/brackets/special chars never split args
        assertEquals("!mp move #998877 5", MpLobbyParser.buildMoveCommand("[Tournament] Player Name", 5, 998877L))
        assertEquals("!mp team #998877 red", MpLobbyParser.buildTeamCommand("[Tournament] Player Name", "RED", 998877L))
        assertEquals("!mp host #998877", MpLobbyParser.buildHostCommand("[Tournament] Player Name", 998877L))
        assertEquals("!mp kick #998877", MpLobbyParser.buildKickCommand("[Tournament] Player Name", 998877L))

        // Without userId, convert spaces to underscores and preserve #id / brackets / hyphens
        assertEquals("!mp move [Tournament]_Player-1 16", MpLobbyParser.buildMoveCommand("[Tournament] Player-1", 99))
        assertEquals("!mp team [Tournament]_Player-1 blue", MpLobbyParser.buildTeamCommand("[Tournament] Player-1", "Blue"))
        assertEquals("!mp invite #4242", MpLobbyParser.buildInviteCommand("#4242"))
        assertEquals("!mp addref Ref_User", MpLobbyParser.buildAddRefCommand("Ref\nUser"))
        assertEquals("!mp removeref Ref_User", MpLobbyParser.buildRemoveRefCommand("Ref User"))
        assertNull(MpLobbyParser.buildTeamCommand("Player", "green"))
        assertEquals("!mp password", MpLobbyParser.buildPasswordCommand("  \r\n "))
        assertEquals("!mp password secret", MpLobbyParser.buildPasswordCommand(" secret\n "))
    }

    @Test
    fun extractMatchIdSupportsBareUrlsWithoutScheme() {
        assertEquals(115241345L, MpLobbyParser.extractMatchId("115241345"))
        assertEquals(115241345L, MpLobbyParser.extractMatchId("#mp_115241345"))
        assertEquals(115241345L, MpLobbyParser.extractMatchId("osu.ppy.sh/mp/115241345"))
        assertEquals(115241345L, MpLobbyParser.extractMatchId("https://old.ppy.sh/community/matches/115241345"))
    }

    @Test
    fun parseLobbyStatePreservesBracketsAndSpacesInUsernames() {
        val dump = """
            Room name: A, B, Tourney #1, History: https://osu.ppy.sh/mp/123456
            Beatmap: https://osu.ppy.sh/b/789012 Artist - Title [Diff]
            Team mode: TeamVs, Win condition: ScoreV2
            Active mods: Freemod
            Players: 4
            Slot 1  Not Ready https://osu.ppy.sh/u/1001 [Tournament] Player One [Host / Team Red]
            Slot 2  Ready     https://osu.ppy.sh/u/1002 Player [1] [Team Blue / Hidden, HardRock]
            Slot 3  No Map    https://osu.ppy.sh/u/1003 Player [Test]
            Slot 4  Ready     https://osu.ppy.sh/u/1004 [A] Hyphen-Name [B]
        """.trimIndent()

        val state = MpLobbyParser.parseLobbyState(
            channelId = "#mp_123456",
            channelName = "#mp_123456",
            messages = listOf(botMsg("1", dump))
        )

        assertEquals("A, B, Tourney #1", state.roomName)
        assertEquals(789012L, state.beatmapId)
        assertEquals("Artist - Title [Diff]", state.beatmapTitle)
        assertTrue(state.isFreeMod)

        val s1 = state.slots[0]
        assertEquals("[Tournament] Player One", s1.username)
        assertEquals(1001L, s1.userId)
        assertTrue(s1.isHost)
        assertEquals(MpTeam.RED, s1.team)
        assertEquals("", s1.mods)
        assertEquals(MpSlotStatus.NOT_READY, s1.status)

        val s2 = state.slots[1]
        assertEquals("Player [1]", s2.username)
        assertEquals(1002L, s2.userId)
        assertFalse(s2.isHost)
        assertEquals(MpTeam.BLUE, s2.team)
        assertEquals("Hidden, HardRock", s2.mods)
        assertEquals(MpSlotStatus.READY, s2.status)

        val s3 = state.slots[2]
        assertEquals("Player [Test]", s3.username)
        assertEquals(MpTeam.NONE, s3.team)
        assertEquals("", s3.mods)
        assertEquals(MpSlotStatus.NO_MAP, s3.status)

        val s4 = state.slots[3]
        assertEquals("[A] Hyphen-Name [B]", s4.username)
        assertEquals(MpTeam.NONE, s4.team)
        assertEquals("", s4.mods)
    }

    @Test
    fun parseLobbyStateHandlesIncrementalBotEventsAndModChanges() {
        val messages = listOf(
            botMsg(
                "1",
                """
                Room name: Lobby, History: https://osu.ppy.sh/mp/123456
                Beatmap: https://osu.ppy.sh/b/111 Old Artist - Old Map [Easy]
                Active mods: None
                Players: 1
                Slot 1  Not Ready https://osu.ppy.sh/u/1001 [Clan] Alpha-Beta [Host]
                """.trimIndent()
            ),
            botMsg("2", "[Clan] Alpha-Beta moved to slot 3."),
            botMsg("3", "[Clan] Gamma Delta joined in slot 2 for team blue."),
            botMsg("4", "[Clan] Alpha-Beta changed to Red."),
            botMsg("5", "Changed beatmap to: https://osu.ppy.sh/b/222"),
            botMsg("6", "Enabled Hidden, HardRock, disabled FreeMod"),
            botMsg("7", "Disabled all mods, enabled FreeMod"),
            botMsg("8", "Changed match host to [Clan] Gamma Delta")
        )

        val state = MpLobbyParser.parseLobbyState("#mp_123456", "#mp_123456", messages)
        assertNull(state.slots[0].username)
        assertEquals("[Clan] Alpha-Beta", state.slots[2].username)
        assertEquals(MpTeam.RED, state.slots[2].team)
        assertFalse(state.slots[2].isHost)

        assertEquals("[Clan] Gamma Delta", state.slots[1].username)
        assertEquals(MpTeam.BLUE, state.slots[1].team)
        assertTrue(state.slots[1].isHost)
        assertEquals("[Clan] Gamma Delta", state.hostUsername)

        assertEquals(222L, state.beatmapId)
        assertNull(state.beatmapTitle)
        assertTrue(state.isFreeMod)
        assertEquals("Freemod", state.activeMods)
    }

    @Test
    fun beatmapUrlParserHandlesIrcLinksPunctuationAndLeftToRightOrder() {
        val text = "Look [https://osu.ppy.sh/u/42 Alice], play [https://osu.ppy.sh/beatmapsets/100#osu/200 Song [Hard]], " +
            "compare (https://osu.ppy.sh/users/99/mania) and osu.ppy.sh/beatmaps/300. Ignore notosu.ppy.sh/b/999!"

        val embeds = BeatmapUrlParser.extractOrderedEmbeds(text)
        assertEquals(4, embeds.size)

        val first = embeds[0] as ChatInlineEmbed.User
        assertEquals(42L, first.ref.userId)
        assertEquals("https://osu.ppy.sh/u/42", first.ref.url)

        val second = embeds[1] as ChatInlineEmbed.Beatmap
        assertEquals(100L, second.ref.beatmapsetId)
        assertEquals(200L, second.ref.beatmapId)
        assertEquals("https://osu.ppy.sh/beatmapsets/100#osu/200", second.ref.url)

        val third = embeds[2] as ChatInlineEmbed.User
        assertEquals(99L, third.ref.userId)
        assertEquals("https://osu.ppy.sh/users/99/mania", third.ref.url)

        val fourth = embeds[3] as ChatInlineEmbed.Beatmap
        assertEquals(300L, fourth.ref.beatmapId)
        assertNull(fourth.ref.beatmapsetId)
        assertEquals("https://osu.ppy.sh/beatmaps/300", fourth.ref.url)
    }

    @Test
    fun parseLobbyStateDetectsMatchClosedFromBotAndUserCommand() {
        val openState = MpLobbyParser.parseLobbyState(
            channelId = "#mp_123456",
            channelName = "#mp_123456",
            messages = listOf(botMsg("1", "Room name: Open Room, History: https://osu.ppy.sh/mp/123456"))
        )
        assertFalse(openState.isClosed)

        val closedByBot = MpLobbyParser.parseLobbyState(
            channelId = "#mp_123456",
            channelName = "#mp_123456",
            messages = listOf(
                botMsg("1", "The match has started!"),
                botMsg("2", "Closed the match")
            )
        )
        assertTrue(closedByBot.isClosed)
        assertFalse(closedByBot.isMatchInProgress)

        val closedByUserCommand = MpLobbyParser.parseLobbyState(
            channelId = "#mp_123456",
            channelName = "#mp_123456",
            messages = listOf(
                ChatMessage(
                    id = "3",
                    channelId = "#mp_123456",
                    senderName = "HostPlayer",
                    content = "!mp close",
                    timestamp = 2L,
                    isOutgoing = true
                )
            )
        )
        assertTrue(closedByUserCommand.isClosed)
    }

    @Test
    fun calculatorsGuardAgainstNaNInfinityAndNegativeCounts() {
        val validDiff = BeatmapMetadata(
            beatmapId = 2001L,
            beatmapsetId = 1001L,
            title = "Track",
            artist = "Artist",
            creator = "Mapper",
            version = "Insane",
            starRating = 5.2,
            bpm = Double.NaN,
            totalLengthSeconds = 180,
            ar = 9.0,
            cs = 4.0,
            od = 8.0,
            coverUrl = "",
            previewAudioUrl = ""
        )
        val nonFiniteDiff = validDiff.copy(beatmapId = 2002L, starRating = Double.NaN)
        val recSet = BeatmapSetSearchResult(
            beatmapsetId = 1001L,
            primaryBeatmapId = 2001L,
            title = "Track",
            artist = "Artist",
            creator = "Mapper",
            status = "ranked",
            bpm = Double.POSITIVE_INFINITY,
            playCount = -500,
            favouriteCount = -10,
            coverUrl = "",
            previewAudioUrl = "",
            difficulties = listOf(nonFiniteDiff, validDiff)
        )
        val recs = BeatmapRecommendationEngine.selectRecommendations(
            sets = listOf(recSet),
            profile = PlayerRecommendationProfile(avgStarRating = Double.NaN),
            preset = BeatmapRecommendationPreset.STREAM_SPEED,
            topScores = emptyList(),
            savedBeatmaps = emptyList()
        )
        assertEquals(1, recs.size)
        assertEquals(2001L, recs[0].recommendedDiff.beatmapId)
        assertTrue(recs[0].matchScore.isFinite())

        val matchReport = MatchAnalysisReport(
            matchId = 99L,
            roomName = "Test",
            startTime = null,
            endTime = null,
            isLive = false,
            games = listOf(
                MatchAnalysisGame(
                    gameId = 1L,
                    gameIndex = 1,
                    beatmapId = 2001L,
                    beatmapsetId = 1001L,
                    beatmapTitle = "Track",
                    beatmapArtist = "Artist",
                    beatmapVersion = "Insane",
                    starRating = 5.2,
                    coverUrl = "",
                    mode = "osu",
                    scoringType = "scorev2",
                    teamType = "head-to-head",
                    mods = emptyList(),
                    startTime = null,
                    endTime = null,
                    scores = listOf(
                        MatchAnalysisPlayerScore(
                            userId = 10L,
                            username = "PlayerA",
                            avatarUrl = "",
                            countryCode = "US",
                            slot = 0,
                            team = MpTeam.NONE,
                            score = 500_000L,
                            accuracy = Double.NaN,
                            maxCombo = 400,
                            mods = emptyList(),
                            passed = true,
                            perfect = false,
                            rank = "A"
                        )
                    )
                )
            )
        )
        val analytics = MatchAnalyzerCalculator.compute(matchReport, emptySet())
        assertEquals(0.0, analytics.playerStandings.first().averageAccuracy, 1e-6)
        assertTrue(analytics.playerStandings.first().matchCost.isFinite())

        val nanScore = OsuScoreItem(
            id = 1L,
            userId = 1L,
            username = "U",
            rank = "F",
            pp = null,
            accuracy = Double.NaN,
            maxCombo = 10,
            score = 100L,
            passed = false,
            beatmapsetId = 1L,
            beatmapTitle = "A",
            beatmapArtist = "B",
            beatmapVersion = "C"
        )
        val validScore = nanScore.copy(id = 2L, rank = "S", accuracy = 98.5, score = 500_000L, passed = false)
        val session = SessionTrackerCalculator.computeSummary(
            mode = "osu",
            scores24h = listOf(nanScore, validScore),
            currentProfile = null,
            historyPoints = emptyList()
        )
        assertEquals(2L, session.highestPpPlay?.id)
    }
}
