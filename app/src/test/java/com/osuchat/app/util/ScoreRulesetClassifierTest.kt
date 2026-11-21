package com.osuchat.app.util

import com.google.gson.Gson
import com.osuchat.app.data.network.OsuScoreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreRulesetClassifierTest {

    private val gson = Gson()

    @Test
    fun classify_explicitClassicMod_returnsClassic() {
        assertEquals(
            ScoreRulesetClient.CLASSIC,
            ScoreRulesetClassifier.classify(mods = listOf("hd", "CL"), legacyScoreId = null, buildId = 7890L)
        )
        assertTrue(ScoreRulesetClassifier.isClassic(mods = listOf("CL")))
        assertFalse(ScoreRulesetClassifier.isLazer(mods = listOf("CL")))
    }

    @Test
    fun classify_legacyScoreIdWithoutBuildId_returnsClassic() {
        assertEquals(
            ScoreRulesetClient.CLASSIC,
            ScoreRulesetClassifier.classify(mods = listOf("HD", "HR"), legacyScoreId = 44556677L, buildId = null)
        )
    }

    @Test
    fun classify_nonClassicWithBuildId_returnsLazer() {
        assertEquals(
            ScoreRulesetClient.LAZER,
            ScoreRulesetClassifier.classify(mods = listOf("HD", "DT"), legacyScoreId = null, buildId = 9991L)
        )
        assertEquals(
            ScoreRulesetClient.LAZER,
            ScoreRulesetClassifier.classify(mods = emptyList(), legacyScoreId = 123L, buildId = 9991L)
        )
    }

    @Test
    fun clientBadgeAndGameplayMods_avoidDuplicateClAndNmClClutter() {
        assertEquals("Classic", ScoreRulesetClassifier.clientBadgeLabel(listOf("CL"), isLazer = false))
        assertEquals(emptyList<String>(), ScoreRulesetClassifier.gameplayMods(listOf("CL", "NM")))
        assertEquals("", ScoreRulesetClassifier.gameplayModsLabel(listOf("CL")))

        assertEquals("CL", ScoreRulesetClassifier.clientBadgeLabel(listOf("HD", "DT", "CL"), isLazer = false))
        assertEquals(listOf("HD", "DT"), ScoreRulesetClassifier.gameplayMods(listOf("HD", "DT", "CL", "cl")))
        assertEquals("+HDDT", ScoreRulesetClassifier.gameplayModsLabel(listOf("HD", "DT", "CL")))

        assertEquals("Lazer", ScoreRulesetClassifier.clientBadgeLabel(emptyList(), isLazer = true))
        assertEquals("Lazer", ScoreRulesetClassifier.clientBadgeLabel(listOf("HR"), isLazer = true))
        assertEquals("+HR", ScoreRulesetClassifier.gameplayModsLabel(listOf("HR")))
    }

    @Test
    fun osuScoreDto_deserializesLegacyStringModsAndStructuredApiV2Mods() {
        val legacyJson = """
            {
              "id": 1001,
              "legacy_score_id": 999001,
              "mods": ["HD", "HR"],
              "accuracy": 0.985,
              "statistics": { "count_300": 400, "count_100": 12, "count_50": 1, "count_miss": 2 }
            }
        """.trimIndent()
        val legacy = gson.fromJson(legacyJson, OsuScoreDto::class.java)
        assertEquals(listOf("HD", "HR"), legacy.mods)
        assertEquals(999001L, legacy.legacyScoreId)
        assertEquals(400, legacy.statistics?.resolvedCount300)
        assertEquals(2, legacy.statistics?.resolvedCountMiss)
        assertTrue(ScoreRulesetClassifier.isClassic(legacy.mods.orEmpty(), legacy.legacyScoreId, legacy.buildId))

        val lazerJson = """
            {
              "id": 2002,
              "build_id": 7788,
              "mods": [
                { "acronym": "HD" },
                { "acronym": "DT", "settings": { "speed_change": 1.35 } }
              ],
              "accuracy": 0.991,
              "statistics": { "great": 510, "ok": 4, "miss": 1 }
            }
        """.trimIndent()
        val lazer = gson.fromJson(lazerJson, OsuScoreDto::class.java)
        assertEquals(listOf("HD", "DT"), lazer.mods)
        assertEquals(7788L, lazer.buildId)
        assertEquals(510, lazer.statistics?.resolvedCount300)
        assertEquals(4, lazer.statistics?.resolvedCount100)
        assertEquals(0, lazer.statistics?.resolvedCount50)
        assertEquals(1, lazer.statistics?.resolvedCountMiss)
        assertTrue(ScoreRulesetClassifier.isLazer(lazer.mods.orEmpty(), lazer.legacyScoreId, lazer.buildId))

        val classicV2Json = """
            {
              "id": 3003,
              "legacy_score_id": 888001,
              "mods": [{ "acronym": "CL" }, { "acronym": "HD" }]
            }
        """.trimIndent()
        val classicV2 = gson.fromJson(classicV2Json, OsuScoreDto::class.java)
        assertEquals(listOf("CL", "HD"), classicV2.mods)
        assertTrue(ScoreRulesetClassifier.isClassic(classicV2.mods.orEmpty(), classicV2.legacyScoreId, classicV2.buildId))
    }

    @Test
    fun rosuPpEngine_buildCalculateScript_wiresLazerFlagAndStripsClMod() {
        assertEquals("DTHD", RosuPpEngine.sanitizeModsForJs(setOf("HD", "DT", "CL", "NM")))

        val lazerScript = RosuPpEngine.buildCalculateScript(
            requestId = 7L,
            osuBase64 = "b3N1ZGF0YQ==",
            modeArg = "osu",
            modsArg = "HDDT",
            accuracyPercent = 99.5,
            nMisses = 1,
            combo = 450,
            clockRate = 1.5,
            isLazer = true
        )
        assertTrue(lazerScript.endsWith(", 1.5, true);"))

        val classicScript = RosuPpEngine.buildCalculateScript(
            requestId = 8L,
            osuBase64 = "b3N1ZGF0YQ==",
            modeArg = "osu",
            modBitmask = 72,
            accuracyPercent = 98.0,
            nMisses = 0,
            combo = null,
            clockRate = null,
            isLazer = false
        )
        assertTrue(classicScript.endsWith(", null, false);"))
    }
}
