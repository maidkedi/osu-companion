package com.osuchat.app.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class MatchSetupTemplate(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("roomNamePattern") val roomNamePattern: String = "TOURNEY: ({red}) vs ({blue})",
    @SerializedName("bestOf") val bestOf: Int = 9,
    @SerializedName("teamMode") val teamMode: Int = TEAM_MODE_TEAM_VS,
    @SerializedName("scoreMode") val scoreMode: Int = SCORE_MODE_SCORE_V2,
    @SerializedName("roomSize") val roomSize: Int = 4,
    @SerializedName("lockSlots") val lockSlots: Boolean = true,
    @SerializedName("password") val password: String = "",
    @SerializedName("defaultRefs") val defaultRefs: List<String> = emptyList(),
    @SerializedName("defaultInvites") val defaultInvites: List<String> = emptyList()
) {
    companion object {
        const val TEAM_MODE_HEAD_TO_HEAD = 0
        const val TEAM_MODE_TEAM_VS = 2

        const val SCORE_MODE_SCORE = 0
        const val SCORE_MODE_ACCURACY = 1
        const val SCORE_MODE_SCORE_V2 = 3

        val DEFAULT_TEMPLATES: List<MatchSetupTemplate> = listOf(
            MatchSetupTemplate(
                id = "1v1_sv2_bo7",
                name = "1v1 ScoreV2 Bo7",
                roomNamePattern = "TOURNEY: ({red}) vs ({blue})",
                bestOf = 7,
                teamMode = TEAM_MODE_TEAM_VS,
                scoreMode = SCORE_MODE_SCORE_V2,
                roomSize = 2,
                lockSlots = true
            ),
            MatchSetupTemplate(
                id = "2v2_sv2_bo9",
                name = "2v2 ScoreV2 Bo9",
                roomNamePattern = "TOURNEY: ({red}) vs ({blue})",
                bestOf = 9,
                teamMode = TEAM_MODE_TEAM_VS,
                scoreMode = SCORE_MODE_SCORE_V2,
                roomSize = 4,
                lockSlots = true
            ),
            MatchSetupTemplate(
                id = "3v3_sv2_bo11",
                name = "3v3 ScoreV2 Bo11",
                roomNamePattern = "TOURNEY: ({red}) vs ({blue})",
                bestOf = 11,
                teamMode = TEAM_MODE_TEAM_VS,
                scoreMode = SCORE_MODE_SCORE_V2,
                roomSize = 6,
                lockSlots = true
            ),
            MatchSetupTemplate(
                id = "4v4_sv2_bo13",
                name = "4v4 ScoreV2 Bo13",
                roomNamePattern = "TOURNEY: ({red}) vs ({blue})",
                bestOf = 13,
                teamMode = TEAM_MODE_TEAM_VS,
                scoreMode = SCORE_MODE_SCORE_V2,
                roomSize = 8,
                lockSlots = true
            )
        )
    }
}

@Immutable
data class MatchSetupRunConfig(
    val roomNamePattern: String = "TOURNEY: ({red}) vs ({blue})",
    val redTeamName: String = "Red",
    val blueTeamName: String = "Blue",
    val bestOf: Int = 9,
    val teamMode: Int = MatchSetupTemplate.TEAM_MODE_TEAM_VS,
    val scoreMode: Int = MatchSetupTemplate.SCORE_MODE_SCORE_V2,
    val roomSize: Int = 4,
    val lockSlots: Boolean = true,
    val password: String = "",
    val refs: List<String> = emptyList(),
    val invites: List<String> = emptyList()
) {
    val resolvedRoomName: String
        get() {
            val cleanRed = redTeamName.trim().ifEmpty { "Red" }
            val cleanBlue = blueTeamName.trim().ifEmpty { "Blue" }
            return roomNamePattern.trim()
                .ifEmpty { "TOURNEY: ({red}) vs ({blue})" }
                .replace("{red}", cleanRed, ignoreCase = true)
                .replace("{blue}", cleanBlue, ignoreCase = true)
                .replace(Regex("""[\r\n\u0000\t]+"""), " ")
                .trim()
        }

    fun buildPreviewCommandQueue(): List<String> {
        val commands = mutableListOf<String>()
        commands.add("!mp make $resolvedRoomName")
        commands.add("!mp set ${teamMode.coerceIn(0, 3)} ${scoreMode.coerceIn(0, 3)} ${roomSize.coerceIn(2, 16)}")
        val cleanPass = password.replace(Regex("""[\r\n\u0000\t]+"""), " ").trim()
        if (cleanPass.isNotEmpty()) {
            commands.add("!mp password $cleanPass")
        }
        if (lockSlots) {
            commands.add("!mp lock")
        }
        refs.mapNotNull { it.trim().takeIf { r -> r.isNotEmpty() }?.replace(' ', '_') }
            .distinct()
            .forEach { ref -> commands.add("!mp addref $ref") }
        invites.mapNotNull { it.trim().takeIf { p -> p.isNotEmpty() }?.replace(' ', '_') }
            .distinct()
            .forEach { player -> commands.add("!mp invite $player") }
        return commands
    }
}
