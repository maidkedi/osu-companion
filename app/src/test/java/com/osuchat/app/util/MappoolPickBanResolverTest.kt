package com.osuchat.app.util

import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpPickBanMode
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappoolPickBanResolverTest {

    private val sampleSlot = MappoolSlot(
        id = 42L,
        poolId = 1L,
        slotTag = "NM1",
        beatmapId = 123456L,
        mods = "NF",
        orderIndex = 0
    )

    private val otherSlot = MappoolSlot(
        id = 43L,
        poolId = 1L,
        slotTag = "HD1",
        beatmapId = 654321L,
        mods = "HD NF",
        orderIndex = 1
    )

    @Test
    fun tapAvailableSlot_inPickModes_setsPickAndEmitsMpCommands() {
        val redOutcome = MappoolPickBanResolver.resolveTap(MpPoolSlotState.AVAILABLE, MpPickBanMode.RED_PICK)
        assertEquals(MpPoolSlotState.PICKED_RED, redOutcome?.nextState)
        assertTrue(redOutcome?.shouldEmitMpCommands == true)

        val blueOutcome = MappoolPickBanResolver.resolveTap(MpPoolSlotState.AVAILABLE, MpPickBanMode.BLUE_PICK)
        assertEquals(MpPoolSlotState.PICKED_BLUE, blueOutcome?.nextState)
        assertTrue(blueOutcome?.shouldEmitMpCommands == true)

        val tbOutcome = MappoolPickBanResolver.resolveTap(MpPoolSlotState.AVAILABLE, MpPickBanMode.TB_NEUTRAL)
        assertEquals(MpPoolSlotState.PLAYED_TB, tbOutcome?.nextState)
        assertTrue(tbOutcome?.shouldEmitMpCommands == true)
    }

    @Test
    fun tapSameMode_clearsSlotToAvailable_withoutEmittingMpCommands() {
        val pairs = listOf(
            MpPoolSlotState.PICKED_RED to MpPickBanMode.RED_PICK,
            MpPoolSlotState.PICKED_BLUE to MpPickBanMode.BLUE_PICK,
            MpPoolSlotState.PLAYED_TB to MpPickBanMode.TB_NEUTRAL,
            MpPoolSlotState.BANNED_RED to MpPickBanMode.RED_BAN,
            MpPoolSlotState.BANNED_BLUE to MpPickBanMode.BLUE_BAN
        )

        for ((currentState, mode) in pairs) {
            val outcome = MappoolPickBanResolver.resolveTap(currentState, mode)
            assertEquals(MpPoolSlotState.AVAILABLE, outcome?.nextState)
            assertFalse(
                "Clearing $currentState in $mode must NOT emit !mp commands",
                outcome?.shouldEmitMpCommands == true
            )
        }
    }

    @Test
    fun pickModes_ignoreTapsOnBannedSlots() {
        val bannedStates = listOf(
            MpPoolSlotState.BANNED,
            MpPoolSlotState.BANNED_RED,
            MpPoolSlotState.BANNED_BLUE
        )
        val pickModes = listOf(
            MpPickBanMode.RED_PICK,
            MpPickBanMode.BLUE_PICK,
            MpPickBanMode.TB_NEUTRAL
        )

        for (bannedState in bannedStates) {
            for (pickMode in pickModes) {
                assertTrue(
                    "$bannedState should be locked in $pickMode",
                    MappoolPickBanResolver.isSlotPickLocked(bannedState, pickMode)
                )
                assertNull(
                    "Tapping $bannedState in $pickMode must return null (ignored)",
                    MappoolPickBanResolver.resolveTap(bannedState, pickMode)
                )
            }
        }
    }

    @Test
    fun banMode_isNotLockedOnBannedSlots_andClearsMatchingBan() {
        assertFalse(MappoolPickBanResolver.isSlotPickLocked(MpPoolSlotState.BANNED_RED, MpPickBanMode.RED_BAN))
        assertFalse(MappoolPickBanResolver.isSlotPickLocked(MpPoolSlotState.BANNED_BLUE, MpPickBanMode.BLUE_BAN))

        val clearRedBan = MappoolPickBanResolver.resolveTap(MpPoolSlotState.BANNED_RED, MpPickBanMode.RED_BAN)
        assertEquals(MpPoolSlotState.AVAILABLE, clearRedBan?.nextState)
        assertFalse(clearRedBan?.shouldEmitMpCommands == true)

        val clearBlueBan = MappoolPickBanResolver.resolveTap(MpPoolSlotState.BANNED_BLUE, MpPickBanMode.BLUE_BAN)
        assertEquals(MpPoolSlotState.AVAILABLE, clearBlueBan?.nextState)
        assertFalse(clearBlueBan?.shouldEmitMpCommands == true)
    }

    @Test
    fun applySlotTransition_clearsActivePickMetadata_whenActiveSlotReset() {
        val pickedState = MappoolPickBanResolver.applySlotTransition(
            current = MatchSeriesState(),
            slot = sampleSlot,
            targetState = MpPoolSlotState.PICKED_RED
        )
        assertEquals(MpPoolSlotState.PICKED_RED, pickedState.slotStates[sampleSlot.id])
        assertEquals(sampleSlot.id, pickedState.activePickedSlotId)
        assertEquals(sampleSlot.slotTag, pickedState.activePickedSlotTag)
        assertEquals(sampleSlot.beatmapId, pickedState.activePickedBeatmapId)
        assertEquals(MpTeam.RED, pickedState.activePickedBy)

        val clearedState = MappoolPickBanResolver.applySlotTransition(
            current = pickedState,
            slot = sampleSlot,
            targetState = MpPoolSlotState.AVAILABLE
        )
        assertFalse(clearedState.slotStates.containsKey(sampleSlot.id))
        assertNull(clearedState.activePickedSlotId)
        assertNull(clearedState.activePickedSlotTag)
        assertNull(clearedState.activePickedBeatmapId)
        assertEquals(MpTeam.NONE, clearedState.activePickedBy)
    }

    @Test
    fun applySlotTransition_preservesOtherActivePick_whenDifferentSlotCleared() {
        val withFirstPick = MappoolPickBanResolver.applySlotTransition(
            current = MatchSeriesState(),
            slot = sampleSlot,
            targetState = MpPoolSlotState.PICKED_RED
        )
        val withBanOnOther = MappoolPickBanResolver.applySlotTransition(
            current = withFirstPick,
            slot = otherSlot,
            targetState = MpPoolSlotState.BANNED_BLUE
        )
        val clearedBan = MappoolPickBanResolver.applySlotTransition(
            current = withBanOnOther,
            slot = otherSlot,
            targetState = MpPoolSlotState.AVAILABLE
        )
        assertEquals(sampleSlot.id, clearedBan.activePickedSlotId)
        assertEquals(MpTeam.RED, clearedBan.activePickedBy)
        assertEquals(MpPoolSlotState.PICKED_RED, clearedBan.slotStates[sampleSlot.id])
        assertFalse(clearedBan.slotStates.containsKey(otherSlot.id))
    }
}
