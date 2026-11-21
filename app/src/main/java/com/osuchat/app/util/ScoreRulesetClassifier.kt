package com.osuchat.app.util

enum class ScoreRulesetClient {
    LAZER,
    CLASSIC
}

/**
 * Classifies osu! scores as Lazer vs Classic (CL / osu!stable) and normalizes mod display
 * so score lists, score cards, and the local rosu-pp simulator stay consistent.
 */
object ScoreRulesetClassifier {

    /**
     * Returns uppercase, trimmed, distinct mod acronyms preserving order.
     */
    fun normalizeMods(mods: Collection<String>): List<String> {
        if (mods.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>(mods.size)
        for (raw in mods) {
            val clean = raw.trim().uppercase()
            if (clean.isNotEmpty()) {
                seen.add(clean)
            }
        }
        return seen.toList()
    }

    /**
     * Classifies a score as [ScoreRulesetClient.CLASSIC] or [ScoreRulesetClient.LAZER].
     *
     * Rules (aligned with osu! API v2 `x-api-version: 20220705`):
     * 1. Explicit `CL` mod -> Classic (both osu!stable plays and osu!lazer plays using Classic mod).
     * 2. Non-CL score with a positive `buildId` -> Lazer.
     * 3. Non-CL score with a positive `legacyScoreId` (legacy/stable fallback without `buildId`) -> Classic.
     * 4. Otherwise -> Lazer.
     */
    fun classify(
        mods: Collection<String>,
        legacyScoreId: Long? = null,
        buildId: Long? = null
    ): ScoreRulesetClient {
        val norm = normalizeMods(mods)
        if ("CL" in norm) return ScoreRulesetClient.CLASSIC
        if (buildId != null && buildId > 0L) return ScoreRulesetClient.LAZER
        if (legacyScoreId != null && legacyScoreId > 0L) return ScoreRulesetClient.CLASSIC
        return ScoreRulesetClient.LAZER
    }

    fun isLazer(
        mods: Collection<String>,
        legacyScoreId: Long? = null,
        buildId: Long? = null
    ): Boolean = classify(mods, legacyScoreId, buildId) == ScoreRulesetClient.LAZER

    fun isClassic(
        mods: Collection<String>,
        legacyScoreId: Long? = null,
        buildId: Long? = null
    ): Boolean = !isLazer(mods, legacyScoreId, buildId)

    /**
     * Mod acronyms excluding client-marker/placeholder tokens (`CL`, `NM`, `NONE`).
     */
    fun gameplayMods(mods: Collection<String>): List<String> {
        return normalizeMods(mods).filter { it != "CL" && it != "NM" && it != "NONE" }
    }

    /**
     * Compact inline mod text without `CL`/`NM` noise (e.g. `"+HDDT"` or `""` for NoMod).
     */
    fun gameplayModsLabel(mods: Collection<String>): String {
        val active = gameplayMods(mods)
        return if (active.isEmpty()) "" else "+${active.joinToString("")}"
    }

    /**
     * Compact client badge label paired with [gameplayMods]:
     * - Lazer scores -> `"Lazer"`
     * - Classic scores with other mods -> `"CL"` (avoids wide badge next to `+HDDT`)
     * - Classic NoMod scores -> `"Classic"` (avoids confusing `NM + CL`)
     */
    fun clientBadgeLabel(
        mods: Collection<String>,
        isLazer: Boolean
    ): String {
        if (isLazer) return "Lazer"
        return if (gameplayMods(mods).isEmpty()) "Classic" else "CL"
    }
}
