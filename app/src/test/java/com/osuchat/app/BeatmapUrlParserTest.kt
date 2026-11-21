package com.osuchat.app

import com.osuchat.app.domain.model.ChatInlineEmbed
import com.osuchat.app.util.BeatmapUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatmapUrlParserTest {

    @Test
    fun extractBeatmapLinks_returnsEmptyForBlankOrUnrelatedText() {
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("   \n\t ").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("hello https://google.com/beatmaps/123").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("https://notosu.ppy.sh/b/12345").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("https://myosu.ppy.sh/beatmapsets/123#osu/456").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("https://osu.ppy.sh/b/").isEmpty())
        assertTrue(BeatmapUrlParser.extractBeatmapLinks("https://osu.ppy.sh/beatmapsets/").isEmpty())
    }

    @Test
    fun extractBeatmapLinks_supportsAllOsuAndOldPpyFormatsAndModes() {
        val text = listOf(
            "https://osu.ppy.sh/beatmapsets/1001#osu/2001",
            "http://osu.ppy.sh/beatmapsets/1002#taiko/2002",
            "https://old.ppy.sh/beatmapsets/1003#fruits/2003",
            "osu.ppy.sh/beatmapsets/1004#mania/2004",
            "https://osu.ppy.sh/beatmapsets/1005",
            "old.ppy.sh/b/2006",
            "http://osu.ppy.sh/beatmaps/2007",
            "https://old.ppy.sh/s/1008"
        ).joinToString(" | ")

        val links = BeatmapUrlParser.extractBeatmapLinks(text)
        assertEquals(8, links.size)

        assertEquals(1001L, links[0].beatmapsetId)
        assertEquals(2001L, links[0].beatmapId)
        assertEquals("https://osu.ppy.sh/beatmapsets/1001#osu/2001", links[0].url)

        assertEquals(1002L, links[1].beatmapsetId)
        assertEquals(2002L, links[1].beatmapId)
        assertEquals("http://osu.ppy.sh/beatmapsets/1002#taiko/2002", links[1].url)

        assertEquals(1003L, links[2].beatmapsetId)
        assertEquals(2003L, links[2].beatmapId)

        assertEquals(1004L, links[3].beatmapsetId)
        assertEquals(2004L, links[3].beatmapId)
        assertEquals("https://osu.ppy.sh/beatmapsets/1004#mania/2004", links[3].url)

        assertEquals(1005L, links[4].beatmapsetId)
        assertNull(links[4].beatmapId)

        assertNull(links[5].beatmapsetId)
        assertEquals(2006L, links[5].beatmapId)
        assertEquals("https://old.ppy.sh/b/2006", links[5].url)

        assertNull(links[6].beatmapsetId)
        assertEquals(2007L, links[6].beatmapId)

        assertEquals(1008L, links[7].beatmapsetId)
        assertNull(links[7].beatmapId)
    }

    @Test
    fun extractBeatmapLinks_isCaseInsensitiveAndHandlesWrappingPunctuation() {
        val text = "Play [HTTPS://OSU.PPY.SH/BeatmapSets/42#OSU/99 Artist - Song], or (osu.ppy.sh/b/123)!"
        val links = BeatmapUrlParser.extractBeatmapLinks(text)
        assertEquals(2, links.size)

        assertEquals(42L, links[0].beatmapsetId)
        assertEquals(99L, links[0].beatmapId)
        assertEquals("HTTPS://OSU.PPY.SH/BeatmapSets/42#OSU/99", links[0].url)

        assertNull(links[1].beatmapsetId)
        assertEquals(123L, links[1].beatmapId)
        assertEquals("https://osu.ppy.sh/b/123", links[1].url)
    }

    @Test
    fun extractBeatmapLinks_deduplicatesIdenticalIdPairsAndRejectsAlphanumericSuffixes() {
        val text = "https://osu.ppy.sh/b/555 and old.ppy.sh/b/555 and osu.ppy.sh/b/556abc and osu.ppy.sh/s/777_x"
        val links = BeatmapUrlParser.extractBeatmapLinks(text)
        assertEquals(1, links.size)
        assertEquals(555L, links[0].beatmapId)
        assertEquals("https://osu.ppy.sh/b/555", links[0].url)

        // Same set ID with vs without specific difficulty ID have distinct (beatmapId, beatmapsetId) keys
        val setAndDiff = BeatmapUrlParser.extractBeatmapLinks(
            "https://osu.ppy.sh/beatmapsets/100 and https://osu.ppy.sh/beatmapsets/100#osu/200"
        )
        assertEquals(2, setAndDiff.size)
        assertNull(setAndDiff[0].beatmapId)
        assertEquals(200L, setAndDiff[1].beatmapId)
    }

    @Test
    fun extractUserLinks_handlesAllFormatsModesDeduplicationAndBoundaries() {
        assertTrue(BeatmapUrlParser.extractUserLinks("").isEmpty())
        assertTrue(BeatmapUrlParser.extractUserLinks("https://osu.ppy.sh/users/peppy").isEmpty())
        assertTrue(BeatmapUrlParser.extractUserLinks("https://fakeosu.ppy.sh/u/123").isEmpty())
        assertTrue(BeatmapUrlParser.extractUserLinks("https://osu.ppy.sh/u/123xyz").isEmpty())

        val text = "https://osu.ppy.sh/users/100/osu " +
            "old.ppy.sh/u/100 " +
            "http://old.ppy.sh/users/200/taiko " +
            "osu.ppy.sh/users/300/fruits " +
            "[https://osu.ppy.sh/users/400/mania player] " +
            "https://osu.ppy.sh/u/500"

        val users = BeatmapUrlParser.extractUserLinks(text)
        assertEquals(5, users.size)

        assertEquals(100L, users[0].userId)
        assertEquals("https://osu.ppy.sh/users/100/osu", users[0].url)

        assertEquals(200L, users[1].userId)
        assertEquals("http://old.ppy.sh/users/200/taiko", users[1].url)

        assertEquals(300L, users[2].userId)
        assertEquals("https://osu.ppy.sh/users/300/fruits", users[2].url)

        assertEquals(400L, users[3].userId)
        assertEquals("https://osu.ppy.sh/users/400/mania", users[3].url)

        assertEquals(500L, users[4].userId)
        assertEquals("https://osu.ppy.sh/u/500", users[4].url)
    }

    @Test
    fun extractOrderedEmbeds_sortsBeatmapsAndUsersByStartIndex() {
        assertTrue(BeatmapUrlParser.extractOrderedEmbeds("   ").isEmpty())

        val text = "User1 https://osu.ppy.sh/u/1 -> Map1 old.ppy.sh/b/2 -> User2 osu.ppy.sh/users/3/osu -> Map2 https://osu.ppy.sh/s/4"
        val embeds = BeatmapUrlParser.extractOrderedEmbeds(text)
        assertEquals(4, embeds.size)

        assertTrue(embeds[0] is ChatInlineEmbed.User)
        assertEquals(1L, (embeds[0] as ChatInlineEmbed.User).ref.userId)

        assertTrue(embeds[1] is ChatInlineEmbed.Beatmap)
        assertEquals(2L, (embeds[1] as ChatInlineEmbed.Beatmap).ref.beatmapId)

        assertTrue(embeds[2] is ChatInlineEmbed.User)
        assertEquals(3L, (embeds[2] as ChatInlineEmbed.User).ref.userId)

        assertTrue(embeds[3] is ChatInlineEmbed.Beatmap)
        assertEquals(4L, (embeds[3] as ChatInlineEmbed.Beatmap).ref.beatmapsetId)

        assertTrue(embeds[0].startIndex < embeds[1].startIndex)
        assertTrue(embeds[1].startIndex < embeds[2].startIndex)
        assertTrue(embeds[2].startIndex < embeds[3].startIndex)
    }

    @Test
    fun formatDurationAndNormalizeUsername_handleEdgeCases() {
        assertEquals("0:00", BeatmapUrlParser.formatDuration(-5))
        assertEquals("0:00", BeatmapUrlParser.formatDuration(0))
        assertEquals("0:09", BeatmapUrlParser.formatDuration(9))
        assertEquals("1:05", BeatmapUrlParser.formatDuration(65))
        assertEquals("60:00", BeatmapUrlParser.formatDuration(3600))

        assertEquals("player_one", BeatmapUrlParser.normalizeUsername("  Player One  "))
        assertEquals("[clan]_alpha_beta", BeatmapUrlParser.normalizeUsername("[Clan] Alpha Beta"))
        assertEquals("", BeatmapUrlParser.normalizeUsername("   "))
    }
}
