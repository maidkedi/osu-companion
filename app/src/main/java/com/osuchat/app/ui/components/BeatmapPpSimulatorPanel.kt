package com.osuchat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.OsuBeatmapCalculator
import com.osuchat.app.util.RosuPpEngine
import com.osuchat.app.util.SimulatedBeatmapStats
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RosuPpEngineEntryPoint {
    fun rosuPpEngine(): RosuPpEngine
}

@Immutable
data class RosuSimState(
    val stats: SimulatedBeatmapStats,
    val isCalculating: Boolean,
    val calcError: String? = null,
    val canRetry: Boolean = false,
    val onRetry: () -> Unit = {}
)

@Composable
fun rememberRosuSimulationState(
    meta: BeatmapMetadata,
    selectedMods: Set<String>,
    accuracyPercent: Double,
    nMisses: Int = 0,
    combo: Int? = null,
    clockRate: Double? = null
): RosuSimState {
    val appContext = LocalContext.current.applicationContext
    val engine = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, RosuPpEngineEntryPoint::class.java)
            .rosuPpEngine()
    }

    val safeMisses = nMisses.coerceAtLeast(0)
    val safeCombo = combo?.takeIf { it > 0 }
    val safeRate = clockRate?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.5, 2.0)

    val fallback = remember(meta, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate) {
        OsuBeatmapCalculator.simulate(
            meta = meta,
            mods = selectedMods,
            accuracyPercent = accuracyPercent,
            nMisses = safeMisses,
            combo = safeCombo,
            clockRate = safeRate
        )
    }

    var retryTrigger by remember(meta.beatmapId) { mutableIntStateOf(0) }
    var stats by remember(meta.beatmapId, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate) {
        mutableStateOf(fallback)
    }
    var isCalculating by remember(meta.beatmapId, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate) {
        mutableStateOf(meta.beatmapId > 0L)
    }
    var calcError by remember(meta.beatmapId, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate) {
        mutableStateOf<String?>(null)
    }
    var canRetry by remember(meta.beatmapId, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate) {
        mutableStateOf(false)
    }

    LaunchedEffect(meta.beatmapId, meta.mode, selectedMods, accuracyPercent, safeMisses, safeCombo, safeRate, retryTrigger) {
        stats = fallback
        if (meta.beatmapId <= 0L) {
            isCalculating = false
            calcError = "Beatmap ID unavailable — offline estimate"
            canRetry = false
            return@LaunchedEffect
        }
        isCalculating = true
        calcError = null
        canRetry = false
        delay(120L)

        val result = try {
            Result.success(
                engine.calculate(
                    meta = meta,
                    mods = selectedMods,
                    accuracyPercent = accuracyPercent,
                    nMisses = safeMisses,
                    combo = safeCombo,
                    clockRate = safeRate
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }

        if (!isActive) return@LaunchedEffect

        val exact = result.getOrNull()
        if (exact != null && exact.isExactRosuPp) {
            stats = exact
            calcError = null
            canRetry = false
        } else {
            stats = exact ?: fallback
            calcError = sanitizeCardError(
                result.exceptionOrNull(),
                "Exact rosu-pp unavailable — offline estimate"
            )
            canRetry = true
        }
        isCalculating = false
    }

    val onRetry: () -> Unit = remember(meta.beatmapId) {
        { retryTrigger += 1 }
    }

    return RosuSimState(
        stats = stats,
        isCalculating = isCalculating,
        calcError = calcError,
        canRetry = canRetry,
        onRetry = onRetry
    )
}

@Composable
fun rememberRosuSimulatedStats(
    meta: BeatmapMetadata,
    selectedMods: Set<String>,
    accuracyPercent: Double,
    nMisses: Int = 0,
    combo: Int? = null,
    clockRate: Double? = null
): Pair<SimulatedBeatmapStats, Boolean> {
    val state = rememberRosuSimulationState(
        meta = meta,
        selectedMods = selectedMods,
        accuracyPercent = accuracyPercent,
        nMisses = nMisses,
        combo = combo,
        clockRate = clockRate
    )
    return state.stats to state.isCalculating
}

@Composable
internal fun osuFilterChipBorder(selected: Boolean): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        borderWidth = OsuRadius.hairline,
        selectedBorderWidth = OsuRadius.hairline
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeatmapPpSimulatorPanel(
    meta: BeatmapMetadata,
    selectedMods: Set<String>,
    accuracyPercent: Double,
    onToggleMod: (String) -> Unit,
    onAccuracyChange: (Double) -> Unit,
    onReset: () -> Unit,
    nMisses: Int = 0,
    onMissesChange: (Int) -> Unit = {},
    combo: Int? = null,
    onComboChange: (Int?) -> Unit = {},
    clockRate: Double? = null,
    onClockRateChange: (Double?) -> Unit = {},
    simState: RosuSimState = rememberRosuSimulationState(
        meta = meta,
        selectedMods = selectedMods,
        accuracyPercent = accuracyPercent,
        nMisses = nMisses,
        combo = combo,
        clockRate = clockRate
    ),
    modifier: Modifier = Modifier
) {
    val surfaces = LocalOsuSurfaces.current
    val sim = simState.stats
    val safeMisses = nMisses.coerceAtLeast(0)
    val maxCombo = (sim.maxCombo ?: meta.maxCombo)?.takeIf { it > 1 }
    val isCustomCombo = combo != null && maxCombo != null && combo in 1 until maxCombo
    val effectiveRate = (clockRate ?: sim.clockRate).coerceIn(0.5, 2.0)
    val isCustomRate = clockRate != null && abs(clockRate - 1.0) >= 0.01
    val hasChanges = selectedMods.isNotEmpty() ||
        abs(accuracyPercent - 100.0) >= 0.05 ||
        safeMisses > 0 ||
        isCustomCombo ||
        clockRate != null
    val accPresets = remember { listOf(95.0, 97.0, 98.0, 99.0, 100.0) }
    val chipShape = RoundedCornerShape(OsuRadius.md)

    Surface(
        shape = RoundedCornerShape(OsuRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(OsuSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PP & Mod simulator",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            simState.isCalculating -> "Calculating rosu-pp…"
                            sim.isExactRosuPp -> "rosu-pp exact"
                            else -> "Offline estimate"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sim.isExactRosuPp && !simState.isCalculating) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            surfaces.textMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (safeMisses > 0 || isCustomCombo) "Est. choke" else "Est. FC",
                        style = MaterialTheme.typography.labelSmall,
                        color = surfaces.textMuted
                    )
                    val estFcValue = sim.estimatedFcPp
                        .takeIf { it.isFinite() }
                        ?.let { String.format(Locale.US, "%.2f pp", it) }
                        ?: "— pp"
                    Text(
                        text = estFcValue,
                        style = MaterialTheme.typography.headlineSmall.tnum(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Layout-stable progress slot so calculating state never shifts height.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                if (simState.isCalculating) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }

            if (!simState.isCalculating && simState.calcError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = simState.calcError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (simState.canRetry) {
                        TextButton(
                            onClick = simState.onRetry,
                            shape = chipShape,
                            contentPadding = PaddingValues(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            OsuHairlineDivider()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                OsuBeatmapCalculator.SUPPORTED_MODS.forEach { mod ->
                    OsuFilterChip(
                        label = "+$mod",
                        selected = mod in selectedMods,
                        onClick = { onToggleMod(mod) }
                    )
                }
                if (hasChanges) {
                    OutlinedButton(
                        onClick = onReset,
                        shape = chipShape,
                        border = osuHairlineBorder(),
                        contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accuracy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "%.1f%%", accuracyPercent),
                    style = MaterialTheme.typography.labelMedium.tnum(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
            ) {
                accPresets.forEach { preset ->
                    OsuFilterChip(
                        label = "${preset.toInt()}%",
                        selected = abs(accuracyPercent - preset) < 0.05,
                        onClick = { onAccuracyChange(preset) },
                        isNumeric = true
                    )
                }
            }

            Slider(
                value = accuracyPercent.toFloat().coerceIn(90f, 100f),
                onValueChange = { raw ->
                    onAccuracyChange(((raw * 10f).roundToInt() / 10.0).coerceIn(90.0, 100.0))
                },
                valueRange = 90f..100f
            )

            OsuHairlineDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Misses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                ) {
                    if (safeMisses > 0) {
                        Surface(
                            onClick = { onMissesChange(0) },
                            shape = chipShape,
                            color = surfaces.accentSubtle,
                            border = osuHairlineBorder()
                        ) {
                            Text(
                                text = "0m",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xs)
                            )
                        }
                    }
                    Surface(
                        onClick = { if (safeMisses > 0) onMissesChange(safeMisses - 1) },
                        enabled = safeMisses > 0,
                        shape = chipShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease misses",
                            tint = if (safeMisses > 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .padding(OsuSpacing.xs)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = "${safeMisses}m",
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        fontWeight = FontWeight.SemiBold,
                        color = if (safeMisses > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = OsuSpacing.sm)
                    )
                    Surface(
                        onClick = { onMissesChange(safeMisses + 1) },
                        shape = chipShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = osuHairlineBorder()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase misses",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(OsuSpacing.xs)
                                .size(16.dp)
                        )
                    }
                }
            }

            if (maxCombo != null) {
                val effectiveCombo = (combo ?: maxCombo).coerceIn(1, maxCombo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choke combo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                    ) {
                        if (isCustomCombo) {
                            Surface(
                                onClick = { onComboChange(null) },
                                shape = chipShape,
                                color = surfaces.accentSubtle,
                                border = osuHairlineBorder()
                            ) {
                                Text(
                                    text = "FC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                                )
                            }
                        }
                        Text(
                            text = if (effectiveCombo >= maxCombo) {
                                "Full combo ($maxCombo)"
                            } else {
                                "$effectiveCombo / ${maxCombo}x"
                            },
                            style = MaterialTheme.typography.labelMedium.tnum(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCustomCombo) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                Slider(
                    value = effectiveCombo.toFloat(),
                    onValueChange = { raw ->
                        val rounded = raw.roundToInt().coerceIn(1, maxCombo)
                        onComboChange(if (rounded >= maxCombo) null else rounded)
                    },
                    valueRange = 1f..maxCombo.toFloat()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rate adjust",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val bpmLabel = if (sim.bpm.isFinite()) "${sim.bpm.roundToInt()} BPM" else "— BPM"
                    val arLabel = if (sim.ar.isFinite()) String.format(Locale.US, "%.1f", sim.ar) else "—"
                    val odLabel = if (sim.od.isFinite()) String.format(Locale.US, "%.1f", sim.od) else "—"
                    Text(
                        text = "$bpmLabel · AR $arLabel · OD $odLabel",
                        style = MaterialTheme.typography.labelSmall.tnum(),
                        color = surfaces.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.size(OsuSpacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                ) {
                    if (clockRate != null || isCustomRate) {
                        Surface(
                            onClick = { onClockRateChange(null) },
                            shape = chipShape,
                            color = surfaces.accentSubtle,
                            border = osuHairlineBorder()
                        ) {
                            Text(
                                text = "1.00x",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs)
                            )
                        }
                    }
                    Text(
                        text = String.format(Locale.US, "%.2fx", effectiveRate),
                        style = MaterialTheme.typography.labelMedium.tnum(),
                        fontWeight = FontWeight.SemiBold,
                        color = if (abs(effectiveRate - 1.0) >= 0.01) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            Slider(
                value = effectiveRate.toFloat().coerceIn(0.5f, 2.0f),
                onValueChange = { raw ->
                    val snapped = ((raw * 20f).roundToInt() / 20.0).coerceIn(0.5, 2.0)
                    onClockRateChange(if (abs(snapped - 1.0) < 0.005 && "DT" !in selectedMods && "HT" !in selectedMods) null else snapped)
                },
                valueRange = 0.50f..2.00f,
                steps = 29
            )
        }
    }
}
