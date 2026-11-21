package com.osuchat.app.util

import com.osuchat.app.domain.model.MatchAnalysisGame
import com.osuchat.app.domain.model.MatchAnalysisPlayerScore
import com.osuchat.app.domain.model.MatchAnalysisReport
import com.osuchat.app.domain.model.MatchAnalyzedGameItem
import com.osuchat.app.domain.model.MatchComputedAnalytics
import com.osuchat.app.domain.model.MatchPlayerStanding
import com.osuchat.app.domain.model.MpTeam
import java.text.NumberFormat
import java.util.Locale

object MatchAnalyzerCalculator {

    private class PlayerAccumulator(
        val userId: Long,
        var username: String,
        var avatarUrl: String,
        var countryCode: String
    ) {
        var redCount = 0
        var blueCount = 0
        val scores = mutableListOf<Long>()
        val accuracies = mutableListOf<Double>()
        val matchRatios = mutableListOf<Double>()
    }

    fun warmupIdsForSkipCount(report: MatchAnalysisReport, skipCount: Int): Set<Long> {
        val safeCount = skipCount.coerceAtLeast(0)
        if (safeCount == 0 || report.games.isEmpty()) return emptySet()
        return report.games.take(safeCount).map { it.gameId }.toSet()
    }

    fun compute(
        report: MatchAnalysisReport,
        warmupGameIds: Set<Long>
    ): MatchComputedAnalytics {
        var redSeriesWins = 0
        var blueSeriesWins = 0
        var warmupCount = 0
        var countedCount = 0
        val isTeamSeries = report.games.any { it.isTeamMatch }

        val accumulators = LinkedHashMap<Long, PlayerAccumulator>()
        val analyzedGames = report.games.map { game ->
            val isWarmup = game.gameId in warmupGameIds
            if (isWarmup) warmupCount++ else countedCount++

            val redTotal = game.scores.filter { it.team == MpTeam.RED }.sumOf { it.score.coerceAtLeast(0L) }
            val blueTotal = game.scores.filter { it.team == MpTeam.BLUE }.sumOf { it.score.coerceAtLeast(0L) }
            val sortedScores = game.scores.sortedByDescending { it.score.coerceAtLeast(0L) }
            val winnerTeam = when {
                !game.isTeamMatch -> MpTeam.NONE
                redTotal > blueTotal -> MpTeam.RED
                blueTotal > redTotal -> MpTeam.BLUE
                else -> MpTeam.NONE
            }
            val winnerUsername = if (!game.isTeamMatch) {
                sortedScores.firstOrNull()?.username
            } else {
                null
            }
            if (!isWarmup && game.isTeamMatch) {
                if (winnerTeam == MpTeam.RED) redSeriesWins++
                else if (winnerTeam == MpTeam.BLUE) blueSeriesWins++
            }

            if (!isWarmup && game.scores.isNotEmpty()) {
                val mapAvgScore = game.scores.map { it.score.coerceAtLeast(0L).toDouble() }.average()
                    .takeIf { it.isFinite() && it > 0.0 } ?: 1.0
                for (sc in game.scores) {
                    val acc = accumulators.getOrPut(sc.userId) {
                        PlayerAccumulator(
                            userId = sc.userId,
                            username = sc.username,
                            avatarUrl = sc.avatarUrl,
                            countryCode = sc.countryCode
                        )
                    }
                    acc.username = sc.username
                    if (sc.avatarUrl.isNotBlank()) acc.avatarUrl = sc.avatarUrl
                    if (sc.countryCode != "XX") acc.countryCode = sc.countryCode
                    when (sc.team) {
                        MpTeam.RED -> acc.redCount++
                        MpTeam.BLUE -> acc.blueCount++
                        MpTeam.NONE -> Unit
                    }
                    val safeScore = sc.score.coerceAtLeast(0L)
                    acc.scores.add(safeScore)
                    if (sc.accuracy.isFinite()) {
                        acc.accuracies.add(sc.accuracy.coerceIn(0.0, 100.0))
                    }
                    val ratio = safeScore.toDouble() / mapAvgScore
                    if (ratio.isFinite()) {
                        acc.matchRatios.add(ratio)
                    }
                }
            }

            MatchAnalyzedGameItem(
                game = game,
                isWarmup = isWarmup,
                redTeamTotal = redTotal,
                blueTeamTotal = blueTotal,
                scoreDelta = kotlin.math.abs(redTotal - blueTotal),
                winnerTeam = winnerTeam,
                winnerUsername = winnerUsername,
                sortedScores = sortedScores
            )
        }

        val rawStandings = accumulators.values.map { acc ->
            val mapsPlayed = acc.scores.size
            val totalScore = acc.scores.sum()
            val averageScore = if (mapsPlayed > 0) totalScore / mapsPlayed else 0L
            val highestScore = acc.scores.maxOrNull() ?: 0L
            val averageAcc = if (acc.accuracies.isNotEmpty()) {
                acc.accuracies.average().takeIf { it.isFinite() } ?: 0.0
            } else {
                0.0
            }
            val matchCost = if (acc.matchRatios.isNotEmpty()) {
                acc.matchRatios.average().takeIf { it.isFinite() } ?: 0.0
            } else {
                0.0
            }
            val primaryTeam = when {
                acc.redCount > acc.blueCount -> MpTeam.RED
                acc.blueCount > acc.redCount -> MpTeam.BLUE
                else -> MpTeam.NONE
            }
            MatchPlayerStanding(
                rank = 0,
                userId = acc.userId,
                username = acc.username,
                avatarUrl = acc.avatarUrl,
                countryCode = acc.countryCode,
                primaryTeam = primaryTeam,
                mapsPlayed = mapsPlayed,
                averageScore = averageScore,
                totalScore = totalScore,
                highestScore = highestScore,
                averageAccuracy = averageAcc,
                matchCost = matchCost,
                isMvp = false
            )
        }.sortedWith(
            compareByDescending<MatchPlayerStanding> { it.matchCost }
                .thenByDescending { it.averageScore }
                .thenByDescending { it.averageAccuracy }
        )

        val rankedStandings = rawStandings.mapIndexed { idx, standing ->
            standing.copy(rank = idx + 1, isMvp = idx == 0)
        }

        return MatchComputedAnalytics(
            matchId = report.matchId,
            roomName = report.roomName,
            startTime = report.startTime,
            endTime = report.endTime,
            isLive = report.isLive,
            isTeamSeries = isTeamSeries,
            redSeriesWins = redSeriesWins,
            blueSeriesWins = blueSeriesWins,
            countedMapCount = countedCount,
            warmupMapCount = warmupCount,
            games = analyzedGames,
            playerStandings = rankedStandings,
            mvpPlayer = rankedStandings.firstOrNull()
        )
    }

    fun formatDiscordSummary(analytics: MatchComputedAnalytics): String {
        val intFmt = NumberFormat.getIntegerInstance(Locale.US)
        return buildString {
            appendLine("**${analytics.roomName}**")
            appendLine("MP Link: https://osu.ppy.sh/mp/${analytics.matchId}")
            if (analytics.isTeamSeries) {
                appendLine("Series Score: **Red ${analytics.redSeriesWins} - ${analytics.blueSeriesWins} Blue**")
            }
            appendLine(
                "Maps Counted: ${analytics.countedMapCount}" +
                    if (analytics.warmupMapCount > 0) " (${analytics.warmupMapCount} warmup skipped)" else ""
            )

            analytics.mvpPlayer?.let { mvp ->
                appendLine(
                    String.format(
                        Locale.US,
                        "MVP: **%s** (Match Cost %.2f | Avg %s | %.2f%%)",
                        mvp.username,
                        mvp.matchCost,
                        intFmt.format(mvp.averageScore),
                        mvp.averageAccuracy
                    )
                )
            }

            if (analytics.playerStandings.isNotEmpty()) {
                appendLine()
                appendLine("**Player Standings:**")
                analytics.playerStandings.forEach { p ->
                    val teamTag = when (p.primaryTeam) {
                        MpTeam.RED -> "[Red] "
                        MpTeam.BLUE -> "[Blue] "
                        MpTeam.NONE -> ""
                    }
                    val mvpBadge = if (p.isMvp) " [MVP]" else ""
                    appendLine(
                        String.format(
                            Locale.US,
                            "%d. %s**%s**%s — Cost: **%.2f** | Avg: %s | Acc: %.2f%% (%d maps)",
                            p.rank,
                            teamTag,
                            p.username,
                            mvpBadge,
                            p.matchCost,
                            intFmt.format(p.averageScore),
                            p.averageAccuracy,
                            p.mapsPlayed
                        )
                    )
                }
            }

            if (analytics.games.isNotEmpty()) {
                appendLine()
                appendLine("**Maps Played:**")
                analytics.games.forEach { item ->
                    val modsLabel = if (item.game.mods.isEmpty()) {
                        ""
                    } else {
                        " +${item.game.mods.joinToString("")}"
                    }
                    val prefix = if (item.isWarmup) "[Warmup] " else "${item.game.gameIndex}. "
                    val resultText = if (item.game.isTeamMatch) {
                        val winSide = when (item.winnerTeam) {
                            MpTeam.RED -> "Red wins"
                            MpTeam.BLUE -> "Blue wins"
                            MpTeam.NONE -> "Tie"
                        }
                        "$winSide (${intFmt.format(item.redTeamTotal)} vs ${intFmt.format(item.blueTeamTotal)})"
                    } else {
                        val topScore = item.sortedScores.firstOrNull()
                        if (topScore != null) {
                            "${topScore.username} (${intFmt.format(topScore.score)})"
                        } else {
                            "No scores"
                        }
                    }
                    appendLine("$prefix${item.game.formattedMapLabel}$modsLabel — $resultText")
                }
            }
        }.trimEnd()
    }
}
