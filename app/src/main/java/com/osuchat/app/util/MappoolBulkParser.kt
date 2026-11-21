package com.osuchat.app.util

import com.google.gson.Gson
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlotTransferDto
import com.osuchat.app.domain.model.MappoolTransferDto
import java.util.Locale

data class ParsedMappoolSlot(
    val slotTag: String,
    val beatmapId: Long,
    val mods: String,
    val orderIndex: Int
)

object MappoolBulkParser {

    private val SLOT_PAIR_REGEX = Regex("""^([A-Za-z]{2,8}\d{0,2})(?:[\s\t:|,\-]+)(.+)$""")
    private val SLOT_PREFIX_REGEX = Regex("""^([A-Za-z]+)""")
    private val TOKEN_SPLIT_REGEX = Regex("""[\s\t,|]+""")
    private val ANY_NUMBER_REGEX = Regex("""\b(\d+)\b""")
    private val EXPLICIT_MOD_TOKENS = setOf(
        "NF", "EZ", "HD", "HR", "SD", "DT", "HT", "NC", "FL", "SO", "PF", "FREEMOD", "FM", "NONE", "V2", "CL"
    )
    private val gson = Gson()

    private val URL_PREFIX_GUARDS = listOf(
        "http://",
        "https://",
        "osu.ppy.sh/",
        "old.ppy.sh/"
    )

    fun toTransferDto(pool: Mappool): MappoolTransferDto {
        return MappoolTransferDto(
            version = 1,
            name = pool.name.trim(),
            slots = pool.slots.map { slot ->
                MappoolSlotTransferDto(
                    slotTag = slot.slotTag.trim(),
                    beatmapId = slot.beatmapId,
                    mods = slot.mods.trim().ifEmpty { "NF" }
                )
            }
        )
    }

    fun exportToJson(pool: Mappool): String {
        return gson.toJson(toTransferDto(pool))
    }

    fun exportToSheetText(pool: Mappool): String {
        return pool.slots.joinToString("\n") { slot ->
            val mods = slot.mods.trim().ifEmpty { "NF" }
            "${slot.slotTag}\t${slot.beatmapId}\t$mods"
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun parseTransferJsonOrNull(rawText: String): MappoolTransferDto? = runCatching {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{")) return@runCatching null
        val dto = gson.fromJson(trimmed, MappoolTransferDto::class.java) ?: return@runCatching null
        if (dto.slots == null) return@runCatching null
        val validSlots = dto.slots.mapNotNull { slot ->
            if (slot == null || slot.beatmapId <= 0L) return@mapNotNull null
            val tag = (slot.slotTag as String?).orEmpty().trim()
            if (tag.isEmpty()) return@mapNotNull null
            val mods = (slot.mods as String?).orEmpty().trim().ifEmpty { defaultModsForSlotTag(tag) }
            slot.copy(slotTag = tag, mods = mods)
        }
        if (validSlots.isEmpty()) return@runCatching null
        dto.copy(
            version = if (dto.version <= 0) 1 else dto.version,
            name = (dto.name as String?).orEmpty().trim(),
            slots = validSlots
        )
    }.getOrNull()

    fun defaultModsForSlotTag(slotTag: String): String {
        val prefix = SLOT_PREFIX_REGEX.find(slotTag.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase(Locale.ROOT)
            .orEmpty()

        return when (prefix) {
            "NM" -> "NF"
            "HD" -> "NF HD"
            "HR" -> "NF HR"
            "DT", "NC" -> "NF DT"
            "FM" -> "NF Freemod"
            "TB" -> "NF Freemod"
            "EZ" -> "NF EZ"
            "FL" -> "NF FL"
            "HT" -> "NF HT"
            else -> "NF"
        }
    }

    fun parseBulkText(rawText: String): List<ParsedMappoolSlot> {
        if (rawText.isBlank()) return emptyList()
        val results = mutableListOf<ParsedMappoolSlot>()
        var autoMapCounter = 1

        for (rawLine in rawText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val startsWithUrl = URL_PREFIX_GUARDS.any { line.startsWith(it, ignoreCase = true) }
            val pairMatch = if (!startsWithUrl) SLOT_PAIR_REGEX.matchEntire(line) else null

            val explicitTag: String?
            val targetSegment: String
            if (pairMatch != null) {
                val candidateTag = pairMatch.groupValues[1].trim().uppercase(Locale.ROOT)
                val remainder = pairMatch.groupValues[2].trim()
                if (candidateTag in RESERVED_NON_SLOT_TOKENS) {
                    explicitTag = null
                    targetSegment = line
                } else {
                    explicitTag = candidateTag
                    targetSegment = remainder
                }
            } else {
                explicitTag = null
                targetSegment = line
            }

            val beatmapId = extractBeatmapId(targetSegment) ?: continue
            val resolvedTag = explicitTag ?: "MAP${autoMapCounter++}"
            val explicitMods = extractTrailingExplicitMods(targetSegment)

            results.add(
                ParsedMappoolSlot(
                    slotTag = resolvedTag,
                    beatmapId = beatmapId,
                    mods = explicitMods ?: defaultModsForSlotTag(resolvedTag),
                    orderIndex = results.size
                )
            )
        }

        return results
    }

    private fun extractTrailingExplicitMods(segment: String): String? {
        val tabCols = segment.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (tabCols.size >= 2) {
            val candidate = tabCols.last()
            val modTokens = candidate.split(Regex("""[\s,+]+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (modTokens.isNotEmpty() && modTokens.all { it.uppercase(Locale.ROOT) in EXPLICIT_MOD_TOKENS }) {
                return modTokens.joinToString(" ")
            }
        }
        return null
    }

    private fun extractBeatmapId(segment: String): Long? {
        BeatmapUrlParser.extractBeatmapLinks(segment)
            .firstOrNull()
            ?.let { link -> return link.beatmapId ?: link.beatmapsetId }

        val numericTokens = segment.split(TOKEN_SPLIT_REGEX)
            .mapNotNull { token ->
                token.trim('(', ')', '[', ']', ',', ';', ':')
                    .toLongOrNull()
                    ?.takeIf { it > 0L }
            }
        if (numericTokens.isNotEmpty()) {
            return numericTokens.firstOrNull { it >= 10_000L } ?: numericTokens.first()
        }

        return ANY_NUMBER_REGEX.find(segment)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private val RESERVED_NON_SLOT_TOKENS = setOf("HTTP", "HTTPS", "OSU", "OLD", "WWW")
}
