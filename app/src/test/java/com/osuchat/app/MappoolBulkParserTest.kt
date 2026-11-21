package com.osuchat.app

import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.util.MappoolBulkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappoolBulkParserTest {

    @Test
    fun parseBulkText_autoNumbersRawIdsAndUrlsWithDefaultNf() {
        val raw = """
            2637362
            https://osu.ppy.sh/b/2637363
            https://osu.ppy.sh/beatmapsets/1200000#osu/2637364
            old.ppy.sh/beatmaps/2637365
            
            not a valid map line
        """.trimIndent()

        val parsed = MappoolBulkParser.parseBulkText(raw)
        assertEquals(4, parsed.size)

        assertEquals("MAP1", parsed[0].slotTag)
        assertEquals(2637362L, parsed[0].beatmapId)
        assertEquals("NF", parsed[0].mods)
        assertEquals(0, parsed[0].orderIndex)

        assertEquals("MAP2", parsed[1].slotTag)
        assertEquals(2637363L, parsed[1].beatmapId)
        assertEquals("NF", parsed[1].mods)

        assertEquals("MAP3", parsed[2].slotTag)
        assertEquals(2637364L, parsed[2].beatmapId)
        assertEquals("NF", parsed[2].mods)

        assertEquals("MAP4", parsed[3].slotTag)
        assertEquals(2637365L, parsed[3].beatmapId)
        assertEquals("NF", parsed[3].mods)
        assertEquals(3, parsed[3].orderIndex)
    }

    @Test
    fun parseBulkText_parsesSlotPairsWithSpaceTabColonAndMapsTournamentMods() {
        val raw = """
            NM1 2637362
            HD2${'\t'}2637363
            HR1: 2637364
            dt1 https://osu.ppy.sh/beatmapsets/111111#osu/2637365
            FM1 - https://osu.ppy.sh/b/2637366
            TB | 2637367
        """.trimIndent()

        val parsed = MappoolBulkParser.parseBulkText(raw)
        assertEquals(6, parsed.size)

        assertEquals("NM1", parsed[0].slotTag)
        assertEquals(2637362L, parsed[0].beatmapId)
        assertEquals("NF", parsed[0].mods)

        assertEquals("HD2", parsed[1].slotTag)
        assertEquals(2637363L, parsed[1].beatmapId)
        assertEquals("NF HD", parsed[1].mods)

        assertEquals("HR1", parsed[2].slotTag)
        assertEquals(2637364L, parsed[2].beatmapId)
        assertEquals("NF HR", parsed[2].mods)

        assertEquals("DT1", parsed[3].slotTag)
        assertEquals(2637365L, parsed[3].beatmapId)
        assertEquals("NF DT", parsed[3].mods)

        assertEquals("FM1", parsed[4].slotTag)
        assertEquals(2637366L, parsed[4].beatmapId)
        assertEquals("NF Freemod", parsed[4].mods)

        assertEquals("TB", parsed[5].slotTag)
        assertEquals(2637367L, parsed[5].beatmapId)
        assertEquals("NF Freemod", parsed[5].mods)
    }

    @Test
    fun defaultModsForSlotTag_returnsStandardMpModsStrings() {
        assertEquals("NF", MappoolBulkParser.defaultModsForSlotTag("NM1"))
        assertEquals("NF HD", MappoolBulkParser.defaultModsForSlotTag("hd3"))
        assertEquals("NF HR", MappoolBulkParser.defaultModsForSlotTag("HR2"))
        assertEquals("NF DT", MappoolBulkParser.defaultModsForSlotTag("DT1"))
        assertEquals("NF Freemod", MappoolBulkParser.defaultModsForSlotTag("FM2"))
        assertEquals("NF Freemod", MappoolBulkParser.defaultModsForSlotTag("TB"))
        assertEquals("NF", MappoolBulkParser.defaultModsForSlotTag("MAP1"))
        assertTrue(MappoolBulkParser.parseBulkText("   ").isEmpty())
    }

    @Test
    fun exportAndImportJsonAndSheetText_roundTripExactPoolAndMods() {
        val pool = Mappool(
            id = 42L,
            name = "OWC Grand Finals",
            slots = listOf(
                MappoolSlot(id = 1L, poolId = 42L, slotTag = "NM1", beatmapId = 2637362L, mods = "NF", orderIndex = 0),
                MappoolSlot(id = 2L, poolId = 42L, slotTag = "HD2", beatmapId = 2637363L, mods = "NF HD", orderIndex = 1),
                MappoolSlot(id = 3L, poolId = 42L, slotTag = "FM1", beatmapId = 2637364L, mods = "NF Freemod", orderIndex = 2)
            )
        )

        val json = MappoolBulkParser.exportToJson(pool)
        val parsedDto = MappoolBulkParser.parseTransferJsonOrNull(json)
        assertNotNull(parsedDto)
        assertEquals(1, parsedDto!!.version)
        assertEquals("OWC Grand Finals", parsedDto.name)
        assertEquals(3, parsedDto.slots.size)
        assertEquals("HD2", parsedDto.slots[1].slotTag)
        assertEquals(2637363L, parsedDto.slots[1].beatmapId)
        assertEquals("NF HD", parsedDto.slots[1].mods)

        val sheet = MappoolBulkParser.exportToSheetText(pool)
        assertEquals("NM1\t2637362\tNF\nHD2\t2637363\tNF HD\nFM1\t2637364\tNF Freemod", sheet)

        val parsedSheet = MappoolBulkParser.parseBulkText(sheet)
        assertEquals(3, parsedSheet.size)
        assertEquals("NF HD", parsedSheet[1].mods)
        assertEquals("NF Freemod", parsedSheet[2].mods)

        assertNull(MappoolBulkParser.parseTransferJsonOrNull("NM1 2637362"))
        assertNull(MappoolBulkParser.parseTransferJsonOrNull("{invalid json"))
    }

    @Test
    fun parseTransferJsonOrNull_returnsNullSafelyOnExplicitNullJsonFields() {
        assertNull(MappoolBulkParser.parseTransferJsonOrNull("""{"slots":null}"""))
        assertNull(MappoolBulkParser.parseTransferJsonOrNull("""{"slots":[null]}"""))
        assertNull(
            MappoolBulkParser.parseTransferJsonOrNull(
                """{"name":null,"slots":[{"beatmapId":123456,"slotTag":null,"mods":null}]}"""
            )
        )
        val partialValid = MappoolBulkParser.parseTransferJsonOrNull(
            """{"name":null,"slots":[{"beatmapId":123456,"slotTag":"NM1","mods":null}]}"""
        )
        assertNotNull(partialValid)
        assertEquals("", partialValid!!.name)
        assertEquals(1, partialValid.slots.size)
        assertEquals("NF", partialValid.slots[0].mods)
    }
}
