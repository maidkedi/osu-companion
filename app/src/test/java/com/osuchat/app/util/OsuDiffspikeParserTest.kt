package com.osuchat.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsuDiffspikeParserTest {

    @Test
    fun parse_computesBucketsPeakDensityAndClockRateScaling() {
        val sb = StringBuilder()
        sb.appendLine("osu file format v14")
        sb.appendLine("[HitObjects]")
        // 0ms..60000ms sparse, dense burst around 40000..45000ms
        for (t in 1000..30000 step 1000) {
            sb.appendLine("256,192,$t,1,0,0:0:0:0:")
        }
        for (t in 40000..44000 step 100) {
            sb.appendLine("256,192,$t,1,0,0:0:0:0:")
        }
        for (t in 50000..61000 step 1000) {
            sb.appendLine("256,192,$t,1,0,0:0:0:0:")
        }

        val profile1x = OsuDiffspikeParser.parse(sb.toString(), clockRate = 1.0, bucketCount = 50)
        assertNotNull(profile1x)
        profile1x!!
        assertEquals(50, profile1x.buckets.size)
        assertEquals(60, profile1x.drainDurationSeconds)
        assertTrue(profile1x.peakObjectsPerSecond > profile1x.averageObjectsPerSecond)
        assertTrue(profile1x.buckets.any { it.isPeakDiffspike })

        val profileDt = OsuDiffspikeParser.parse(sb.toString(), clockRate = 1.5, bucketCount = 50)!!
        assertEquals(40, profileDt.drainDurationSeconds)
        assertEquals(profile1x.peakObjectsPerSecond * 1.5, profileDt.peakObjectsPerSecond, 1e-3)
    }

    @Test
    fun parse_returnsNullForMissingOrEmptyHitObjects() {
        assertNull(OsuDiffspikeParser.parse(""))
        assertNull(OsuDiffspikeParser.parse("osu file format v14\n[General]\nMode: 0"))
        assertNull(OsuDiffspikeParser.parse("[HitObjects]\n256,192,1000,1,0"))
    }

    @Test
    fun parse_ignoresNonFiniteAndOverflowHitObjectTimestamps() {
        val osuText = """
            osu file format v14
            [HitObjects]
            256,192,NaN,1,0,0:0:0:0:
            256,192,Infinity,1,0,0:0:0:0:
            256,192,-500,1,0,0:0:0:0:
            256,192,999999999999,1,0,0:0:0:0:
            256,192,1000,1,0,0:0:0:0:
            256,192,5000,1,0,0:0:0:0:
        """.trimIndent()

        val profile = OsuDiffspikeParser.parse(osuText)
        assertNotNull(profile)
        profile!!
        assertEquals(2, profile.totalObjects)
        assertTrue(profile.peakObjectsPerSecond.isFinite())
    }
}