package com.osuchat.app.util

import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpPickBanMode
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpTeam

data class PoolSlotTapOutcome(
    val nextState: MpPoolSlotState,
    val shouldEmitMpCommands: Boolean
)

/**
 * Pure referee mappool pick/ban state transition resolver.
 */
object MappoolPickBanResolver {

    fun isBannedState(state: MpPoolSlotState): Boolean =
        state == MpPoolSlotState.BANNED ||
            state == MpPoolSlotState.BANNED_RED ||
            state == MpPoolSlotState.BANNED_BLUE

    fun isPickMode(mode: MpPickBanMode): Boolean =
        mode == MpPickBanMode.RED_PICK ||
            mode == MpPickBanMode.BLUE_PICK ||
            mode == MpPickBanMode.TB_NEUTRAL

    fun targetStateForMode(mode: MpPickBanMode): MpPoolSlotState = when (mode) {
        MpPickBanMode.RED_PICK -> MpPoolSlotState.PICKED_RED
        MpPickBanMode.BLUE_PICK -> MpPoolSlotState.PICKED_BLUE
        MpPickBanMode.RED_BAN -> MpPoolSlotState.BANNED_RED
        MpPickBanMode.BLUE_BAN -> MpPoolSlotState.BANNED_BLUE
        MpPickBanMode.TB_NEUTRAL -> MpPoolSlotState.PLAYED_TB
    }

    fun isSlotPickLocked(currentState: MpPoolSlotState, activeMode: MpPickBanMode): Boolean =
        isPickMode(activeMode) && isBannedState(currentState)

    /**
     * Resolves tapping a slot in [activeMode].
     * Returns `null` when the slot is banned while in a pick mode (locked; ignore tap).
     * Tapping a slot already matching the active mode clears it back to [MpPoolSlotState.AVAILABLE]
     * without emitting `!mp map` or `!mp mods`.
     */
    fun resolveTap(currentState: MpPoolSlotState, activeMode: MpPickBanMode): PoolSlotTapOutcome? {
        if (isSlotPickLocked(currentState, activeMode)) return null
        val target = targetStateForMode(activeMode)
        if (currentState == target) {
            return PoolSlotTapOutcome(
                nextState = MpPoolSlotState.AVAILABLE,
                shouldEmitMpCommands = false
            )
        }
        return PoolSlotTapOutcome(
            nextState = target,
            shouldEmitMpCommands = isPickMode(activeMode)
        )
    }

    /**
     * Applies [targetState] for [slot] to [current] series state, clearing active pick fields when
     * the currently active slot is reset back to [MpPoolSlotState.AVAILABLE].
     */
    fun applySlotTransition(
        current: MatchSeriesState,
        slot: MappoolSlot,
        targetState: MpPoolSlotState
    ): MatchSeriesState {
        val nextStates = current.slotStates.toMutableMap()
        if (targetState == MpPoolSlotState.AVAILABLE) {
            nextStates.remove(slot.id)
        } else {
            nextStates[slot.id] = targetState
        }
        val isPickState = targetState == MpPoolSlotState.PICKED_RED ||
            targetState == MpPoolSlotState.PICKED_BLUE ||
            targetState == MpPoolSlotState.PLAYED_TB ||
            targetState == MpPoolSlotState.PLAYED
        if (isPickState) {
            val pickedBy = when (targetState) {
                MpPoolSlotState.PICKED_RED -> MpTeam.RED
                MpPoolSlotState.PICKED_BLUE -> MpTeam.BLUE
                else -> MpTeam.NONE
            }
            return current.copy(
                slotStates = nextStates,
                activePickedSlotId = slot.id,
                activePickedSlotTag = slot.slotTag,
                activePickedBeatmapId = slot.beatmapId,
                activePickedBy = pickedBy
            )
        }
        val clearingActive = targetState == MpPoolSlotState.AVAILABLE && current.activePickedSlotId == slot.id
        return current.copy(
            slotStates = nextStates,
            activePickedSlotId = if (clearingActive) null else current.activePickedSlotId,
            activePickedSlotTag = if (clearingActive) null else current.activePickedSlotTag,
            activePickedBeatmapId = if (clearingActive) null else current.activePickedBeatmapId,
            activePickedBy = if (clearingActive) MpTeam.NONE else current.activePickedBy
        )
    }
}
