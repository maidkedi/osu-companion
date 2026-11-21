package com.osuchat.app.util

import com.google.gson.Gson
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedPlayerAlertEngineTest {

    private fun score(
        id: Long,
        createdAtMs: Long? = 1_000L,
        title: String = "Map $id",
        pp: Double = 700.0,
        acc: Double = 0.9912
    ) = TopPlayCandidate(
        scoreId = id,
        createdAtEpochMs = createdAtMs,
        beatmapTitle = title,
        beatmapArtist = "Artist",
        beatmapVersion = "Diff",
        mods = listOf("DT"),
        pp = pp,
        accuracy = acc
    )

    @Test
    fun evaluateCustomMilestones_emitsZeroAutomaticMilestones_whenNoCustomTargets() {
        val first = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = TrackedPlayerAlertConfig(),
            currentPp = 10_000.0,
            currentRank = 2_000
        )
        assertTrue(first.hits.isEmpty())
        assertEquals(10_000.0, first.updatedConfig.baselinePp!!, 0.01)
        assertEquals(2_000, first.updatedConfig.baselineRank)

        val hugeJump = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = first.updatedConfig,
            currentPp = 15_500.0,
            currentRank = 95
        )
        assertTrue(
            "Empty custom targets must emit ZERO automatic milestones",
            hugeJump.hits.isEmpty()
        )
    }

    @Test
    fun evaluateCustomMilestones_firstSyncSilent_thenFiresOnceAndNeverSpamsOnDecay() {
        val initial = TrackedPlayerAlertConfig(
            customPpTargets = listOf(14_000.0, 15_000.0),
            customRankTargets = listOf(500, 100)
        )

        val firstSync = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = initial,
            currentPp = 13_850.0,
            currentRank = 640
        )
        assertTrue(firstSync.hits.isEmpty())
        assertEquals(13_850.0, firstSync.updatedConfig.baselinePp!!, 0.01)
        assertEquals(640, firstSync.updatedConfig.baselineRank)

        val crossed = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = firstSync.updatedConfig,
            currentPp = 14_042.0,
            currentRank = 480
        )
        assertEquals(2, crossed.hits.size)
        assertTrue(crossed.hits.any { it is CustomMilestoneHit.PpTargetHit && it.targetPp == 14_000.0 })
        assertTrue(crossed.hits.any { it is CustomMilestoneHit.RankTargetHit && it.targetRank == 500 })

        // Simulate rank/PP decay back below target and re-crossing later
        val decayed = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = crossed.updatedConfig,
            currentPp = 13_990.0,
            currentRank = 520
        )
        assertTrue(decayed.hits.isEmpty())

        val recrossed = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = decayed.updatedConfig,
            currentPp = 14_060.0,
            currentRank = 470
        )
        assertTrue(
            "Already-notified targets must NOT re-fire after decay",
            recrossed.hits.isEmpty()
        )

        // Second higher target still fires when crossed
        val secondTarget = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = recrossed.updatedConfig,
            currentPp = 15_010.0,
            currentRank = 98
        )
        assertEquals(2, secondTarget.hits.size)
        assertTrue(secondTarget.hits.any { it is CustomMilestoneHit.PpTargetHit && it.targetPp == 15_000.0 })
        assertTrue(secondTarget.hits.any { it is CustomMilestoneHit.RankTargetHit && it.targetRank == 100 })
    }

    @Test
    fun evaluateNewTopPlays_limitExpansionFrom5To50_suppressesRetroactiveSpam() {
        val top50Initial = (1L..50L).map { idx ->
            score(id = idx, createdAtMs = 1_000L + idx)
        }
        val firstSync = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = TrackedPlayerAlertConfig(),
            defaultLimit = 5,
            bestScores = top50Initial,
            nowEpochMs = 2_000L
        )
        assertTrue(firstSync.newPlays.isEmpty())
        assertEquals(5, firstSync.updatedConfig.lastSyncedTopLimit)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), firstSync.updatedConfig.seenBestScoreIds)

        // Expand limit from 5 to 50 with the exact same older #6..#50 scores plus one genuinely new #14 score
        val expandedScores = top50Initial.toMutableList().apply {
            add(13, score(id = 999L, createdAtMs = 3_000L, title = "Fresh Play"))
        }
        val expandedEval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = firstSync.updatedConfig.copy(topPlayLimitOverride = 50),
            defaultLimit = 5,
            bestScores = expandedScores,
            nowEpochMs = 4_000L
        )

        assertEquals(1, expandedEval.newPlays.size)
        assertEquals(999L, expandedEval.newPlays.first().candidate.scoreId)
        assertEquals(14, expandedEval.newPlays.first().rankPosition)
        assertEquals(50, expandedEval.updatedConfig.lastSyncedTopLimit)
        assertTrue(expandedEval.updatedConfig.seenBestScoreIds.containsAll(listOf(10L, 25L, 49L, 999L)))
    }

    @Test
    fun evaluateNewTopPlays_limit25_triggersRank14_andIgnoresRank26() {
        val baseline = (1L..25L).map { idx -> score(id = idx, createdAtMs = 1_000L) }
        val firstSync = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = TrackedPlayerAlertConfig(topPlayLimitOverride = 25),
            defaultLimit = 5,
            bestScores = baseline,
            nowEpochMs = 2_000L
        )
        assertTrue(firstSync.newPlays.isEmpty())

        // Insert new score at #14 (index 13) and also supply #26 (index 25, outside limit 25)
        val nextScores = baseline.toMutableList().apply {
            add(13, score(id = 2_014L, createdAtMs = 2_500L, title = "Rank 14 Play"))
            add(25, score(id = 2_026L, createdAtMs = 2_600L, title = "Rank 26 Play"))
        }
        val eval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = firstSync.updatedConfig,
            defaultLimit = 5,
            bestScores = nextScores,
            nowEpochMs = 3_000L
        )

        assertEquals(1, eval.newPlays.size)
        assertEquals(14, eval.newPlays.first().rankPosition)
        assertEquals(2_014L, eval.newPlays.first().candidate.scoreId)
        assertFalse(eval.newPlays.any { it.candidate.scoreId == 2_026L })
    }

    @Test
    fun topPlayLimit_clampsBetween1And100() {
        assertEquals(1, TrackedPlayerAlertConfig.clampTopPlayLimit(0))
        assertEquals(1, TrackedPlayerAlertConfig.clampTopPlayLimit(-10))
        assertEquals(100, TrackedPlayerAlertConfig.clampTopPlayLimit(150))
        assertEquals(25, TrackedPlayerAlertEngine.effectiveTopPlayLimit(TrackedPlayerAlertConfig(), 25))
        assertEquals(
            100,
            TrackedPlayerAlertEngine.effectiveTopPlayLimit(
                TrackedPlayerAlertConfig(topPlayLimitOverride = 999),
                5
            )
        )
        assertEquals(
            1,
            TrackedPlayerAlertEngine.effectiveTopPlayLimit(
                TrackedPlayerAlertConfig(topPlayLimitOverride = 0),
                5
            )
        )
    }

    @Test
    fun notificationFormatting_hasCleanCopyWithoutEmojisOrExclamationMarks() {
        val (mTitle, mBody) = TrackedPlayerAlertEngine.formatMilestoneNotification(
            username = "Cookiezi",
            mode = "osu",
            hits = listOf(CustomMilestoneHit.PpTargetHit(14_000.0, 14_042.0, "pp:14000.0")),
            currentPp = 14_042.0,
            currentRank = 812
        )
        assertEquals("Cookiezi hit custom target 14,000 pp", mTitle)
        assertEquals("osu! · 14,042 pp · #812", mBody)
        assertFalse(mTitle.contains("!"))

        val (pTitle, pBody) = TrackedPlayerAlertEngine.formatTopPlaysNotification(
            username = "Cookiezi",
            newPlays = listOf(
                NewTopPlayMatch(
                    rankPosition = 14,
                    candidate = TopPlayCandidate(
                        scoreId = 1L,
                        beatmapTitle = "Freedom Dive",
                        beatmapArtist = "xi",
                        beatmapVersion = "Four Dimensions",
                        mods = listOf("HD", "HR"),
                        pp = 742.4,
                        accuracy = 0.9892
                    )
                )
            )
        )
        assertEquals("Cookiezi set a new #14 top play", pTitle)
        assertEquals("xi - Freedom Dive [Four Dimensions] +HDHR · 742 pp · 98.92%", pBody)
        assertFalse(pTitle.contains("!"))

        val (multiTitle, multiBody) = TrackedPlayerAlertEngine.formatTopPlaysNotification(
            username = "Cookiezi",
            newPlays = listOf(
                NewTopPlayMatch(14, score(101L, title = "Second Best", pp = 680.0)),
                NewTopPlayMatch(6, score(102L, title = "Best Of Sync", pp = 790.0))
            )
        )
        assertEquals("Cookiezi set 2 new top plays", multiTitle)
        assertEquals("Best: #6 Best Of Sync · 790 pp", multiBody)
    }

    @Test
    fun gsonBackwardCompatibility_missingToggleFieldsDefaultSafely() {
        val gson = Gson()
        val legacyWithPpAndRank = """
            {
              "custom_pp_targets": [14000.0],
              "custom_rank_targets": [500],
              "top_play_limit_override": 10
            }
        """.trimIndent()
        val parsedWithTargets = gson.fromJson(legacyWithPpAndRank, TrackedPlayerAlertConfig::class.java).sanitized()
        assertTrue(parsedWithTargets.isTopPlaysEnabled())
        assertTrue(parsedWithTargets.isPpEnabled())
        assertTrue(parsedWithTargets.isRankEnabled())
        assertEquals(listOf("Top 10", "14,000 pp", "#500"), TrackedPlayerAlertEngine.buildConfigBadgeLabels(parsedWithTargets, 5))

        val legacyTopPlaysOnly = """
            {
              "top_play_limit_override": 8
            }
        """.trimIndent()
        val parsedNoTargets = gson.fromJson(legacyTopPlaysOnly, TrackedPlayerAlertConfig::class.java).sanitized()
        assertTrue(parsedNoTargets.isTopPlaysEnabled())
        assertFalse(parsedNoTargets.isPpEnabled())
        assertFalse(parsedNoTargets.isRankEnabled())
        assertEquals(listOf("Top 8"), TrackedPlayerAlertEngine.buildConfigBadgeLabels(parsedNoTargets, 5))
    }

    @Test
    fun singleCategoryTracking_rankOnly_skipsTopPlaysAndPpMilestones() {
        val initial = TrackedPlayerAlertConfig(
            customPpTargets = listOf(14_000.0),
            customRankTargets = listOf(500),
            trackTopPlays = false,
            trackPp = false,
            trackRank = true,
            baselinePp = 13_900.0,
            baselineRank = 600,
            seenBestScoreIds = setOf(1L, 2L, 3L)
        )
        assertFalse(initial.isTopPlaysEnabled())
        assertFalse(initial.isPpEnabled())
        assertTrue(initial.isRankEnabled())
        assertTrue(initial.hasAnyTrackingEnabled())

        assertEquals(listOf("#500"), TrackedPlayerAlertEngine.buildConfigBadgeLabels(initial, 5))
        assertEquals("Tracking #500", TrackedPlayerAlertEngine.formatTrackingChipLabel(initial, 5))

        val topEval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = initial,
            defaultLimit = 5,
            bestScores = listOf(score(id = 999L, createdAtMs = 5_000L)),
            nowEpochMs = 6_000L
        )
        assertTrue(topEval.newPlays.isEmpty())

        val milestoneEval = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = initial,
            currentPp = 14_500.0,
            currentRank = 450
        )
        assertEquals(1, milestoneEval.hits.size)
        assertTrue(milestoneEval.hits.single() is CustomMilestoneHit.RankTargetHit)
    }

    @Test
    fun singleCategoryTracking_ppOnly_skipsTopPlaysAndRankMilestones() {
        val initial = TrackedPlayerAlertConfig(
            customPpTargets = listOf(14_000.0),
            customRankTargets = listOf(500),
            trackTopPlays = false,
            trackPp = true,
            trackRank = false,
            baselinePp = 13_900.0,
            baselineRank = 600,
            seenBestScoreIds = setOf(1L, 2L, 3L)
        )
        assertFalse(initial.isTopPlaysEnabled())
        assertTrue(initial.isPpEnabled())
        assertFalse(initial.isRankEnabled())
        assertTrue(initial.hasAnyTrackingEnabled())

        assertEquals(listOf("14,000 pp"), TrackedPlayerAlertEngine.buildConfigBadgeLabels(initial, 5))
        assertEquals("Tracking 14,000 pp", TrackedPlayerAlertEngine.formatTrackingChipLabel(initial, 5))

        val topEval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = initial,
            defaultLimit = 5,
            bestScores = listOf(score(id = 999L, createdAtMs = 5_000L)),
            nowEpochMs = 6_000L
        )
        assertTrue(topEval.newPlays.isEmpty())

        val milestoneEval = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = initial,
            currentPp = 14_100.0,
            currentRank = 400
        )
        assertEquals(1, milestoneEval.hits.size)
        assertTrue(milestoneEval.hits.single() is CustomMilestoneHit.PpTargetHit)
    }

    @Test
    fun singleCategoryTracking_topPlaysOnly_skipsPpAndRankMilestones() {
        val initial = TrackedPlayerAlertConfig(
            customPpTargets = listOf(14_000.0),
            customRankTargets = listOf(500),
            topPlayLimitOverride = 10,
            trackTopPlays = true,
            trackPp = false,
            trackRank = false,
            baselinePp = 13_900.0,
            baselineRank = 600,
            seenBestScoreIds = setOf(1L, 2L, 3L),
            lastSyncedTopLimit = 10,
            lastSyncEpochMs = 1_000L
        )
        assertTrue(initial.isTopPlaysEnabled())
        assertFalse(initial.isPpEnabled())
        assertFalse(initial.isRankEnabled())
        assertTrue(initial.hasAnyTrackingEnabled())

        assertEquals(listOf("Top 10"), TrackedPlayerAlertEngine.buildConfigBadgeLabels(initial, 5))
        assertEquals("Tracking Top 10", TrackedPlayerAlertEngine.formatTrackingChipLabel(initial, 5))

        val milestoneEval = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = initial,
            currentPp = 15_000.0,
            currentRank = 100
        )
        assertTrue(milestoneEval.hits.isEmpty())

        val topEval = TrackedPlayerAlertEngine.evaluateNewTopPlays(
            config = initial,
            defaultLimit = 5,
            bestScores = listOf(score(id = 999L, createdAtMs = 2_000L), score(id = 1L), score(id = 2L)),
            nowEpochMs = 3_000L
        )
        assertEquals(1, topEval.newPlays.size)
        assertEquals(999L, topEval.newPlays.single().candidate.scoreId)
    }

    @Test
    fun gsonExplicitNullCollections_sanitizeWithoutNullPointerException() {
        val gson = Gson()
        val jsonWithExplicitNulls = """
            {
              "custom_pp_targets": null,
              "custom_rank_targets": null,
              "notified_target_keys": null,
              "seen_best_score_ids": null,
              "track_top_plays": false,
              "track_pp": true,
              "track_rank": false
            }
        """.trimIndent()
        val parsed = gson.fromJson(jsonWithExplicitNulls, TrackedPlayerAlertConfig::class.java)
        val sanitized = parsed.sanitized()
        assertTrue(sanitized.customPpTargets.isEmpty())
        assertTrue(sanitized.customRankTargets.isEmpty())
        assertTrue(sanitized.notifiedTargetKeys.isEmpty())
        assertTrue(sanitized.seenBestScoreIds.isEmpty())
        assertFalse(parsed.hasAnyTrackingEnabled())
        assertFalse(sanitized.hasAnyTrackingEnabled())
        assertTrue(sanitized.isPpEnabled())
        assertFalse(sanitized.isRankEnabled())
    }

    @Test
    fun resolveActiveTrackedModes_returnsAllEnabledModes_andSkipsDisabledDefaultMode() {
        val configs = mapOf(
            "42:osu" to TrackedPlayerAlertConfig(trackTopPlays = false, trackPp = false, trackRank = false),
            "42:taiko" to TrackedPlayerAlertConfig(trackTopPlays = true, topPlayLimitOverride = 15),
            "42:mania" to TrackedPlayerAlertConfig(trackTopPlays = false, trackPp = true, customPpTargets = listOf(8_000.0))
        )
        val active = TrackedPlayerAlertEngine.resolveActiveTrackedModes(
            userId = 42L,
            defaultPlaymode = "osu",
            configsMap = configs
        )
        assertEquals(2, active.size)
        assertEquals("taiko", active[0].first)
        assertEquals("mania", active[1].first)

        // When no explicit config exists, falls back to player's default playmode
        val defaultActive = TrackedPlayerAlertEngine.resolveActiveTrackedModes(
            userId = 99L,
            defaultPlaymode = "fruits",
            configsMap = configs
        )
        assertEquals(1, defaultActive.size)
        assertEquals("fruits", defaultActive.single().first)
    }

    @Test
    fun resolveModeProfileMetrics_blocksCrossModeFallbackOnTransientFailure() {
        val (taikoPp, taikoRank) = TrackedPlayerAlertEngine.resolveModeProfileMetrics(
            requestedMode = "taiko",
            fetchedPp = null,
            fetchedRank = null,
            defaultPlaymode = "osu",
            fallbackPp = 12_000.0,
            fallbackRank = 900
        )
        assertEquals(null, taikoPp)
        assertEquals(null, taikoRank)

        val (osuPp, osuRank) = TrackedPlayerAlertEngine.resolveModeProfileMetrics(
            requestedMode = "osu",
            fetchedPp = null,
            fetchedRank = null,
            defaultPlaymode = "osu",
            fallbackPp = 12_000.0,
            fallbackRank = 900
        )
        assertEquals(12_000.0, osuPp!!, 0.01)
        assertEquals(900, osuRank)
    }

    @Test
    fun evaluateCustomMilestones_consumeHitsFalse_preservesTargetUntilNotificationsEnabled() {
        val initial = TrackedPlayerAlertConfig(
            customPpTargets = listOf(10_000.0),
            trackTopPlays = false,
            trackPp = true,
            baselinePp = 9_950.0,
            baselineRank = 5_000
        )
        val whileDisabled = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = initial,
            currentPp = 10_040.0,
            currentRank = 4_900,
            consumeHits = false
        )
        assertEquals(1, whileDisabled.hits.size)
        assertTrue(whileDisabled.updatedConfig.notifiedTargetKeys.isEmpty())
        assertEquals(9_950.0, whileDisabled.updatedConfig.baselinePp!!, 0.01)

        val onceReenabled = TrackedPlayerAlertEngine.evaluateCustomMilestones(
            config = whileDisabled.updatedConfig,
            currentPp = 10_050.0,
            currentRank = 4_850,
            consumeHits = true
        )
        assertEquals(1, onceReenabled.hits.size)
        assertTrue(onceReenabled.updatedConfig.notifiedTargetKeys.contains("pp:10000.0"))
        assertEquals(10_050.0, onceReenabled.updatedConfig.baselinePp!!, 0.01)
    }
}
