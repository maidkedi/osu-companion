package com.osuchat.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.osuchat.app.domain.model.OsuMedalCatalogItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private data class OsekaiMedalDto(
    @SerializedName("MedalID") val medalIdCamel: Int? = null,
    @SerializedName("ID") val idUpper: Int? = null,
    @SerializedName("id") val idLower: Int? = null,
    @SerializedName("Name") val nameCamel: String? = null,
    @SerializedName("name") val nameLower: String? = null,
    @SerializedName("Description") val descCamel: String? = null,
    @SerializedName("description") val descLower: String? = null,
    @SerializedName("Grouping") val groupCamel: String? = null,
    @SerializedName("grouping") val groupLower: String? = null,
    @SerializedName("Link") val linkCamel: String? = null,
    @SerializedName("link") val linkLower: String? = null,
    @SerializedName("Instructions") val instructionsCamel: String? = null,
    @SerializedName("instructions") val instructionsLower: String? = null,
    @SerializedName("Mode") val modeCamel: String? = null,
    @SerializedName("mode") val modeLower: String? = null
)

@Singleton
class MedalsCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val cacheMutex = Mutex()
    private val cacheFile = File(context.filesDir, "medals_catalog.json")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var memoryCache: List<OsuMedalCatalogItem>? = null

    suspend fun getCatalog(forceRefresh: Boolean = false): List<OsuMedalCatalogItem> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                memoryCache?.let { return@withContext it }
            }

            cacheMutex.withLock {
                if (!forceRefresh) {
                    memoryCache?.let { return@withContext it }
                    val diskItems = readDiskCacheIfValid(maxAgeMs = CACHE_TTL_MS)
                    if (diskItems != null && diskItems.isNotEmpty()) {
                        memoryCache = diskItems
                        return@withContext diskItems
                    }
                }

                val remoteItems = fetchRemoteCatalog()
                if (remoteItems.isNotEmpty()) {
                    val merged = mergeWithFallback(remoteItems)
                    writeDiskCache(merged)
                    memoryCache = merged
                    return@withContext merged
                }

                val staleDisk = readDiskCacheIfValid(maxAgeMs = Long.MAX_VALUE)
                val resolved = if (!staleDisk.isNullOrEmpty()) {
                    mergeWithFallback(staleDisk)
                } else {
                    FALLBACK_MEDALS
                }
                memoryCache = resolved
                resolved
            }
        }

    private fun readDiskCacheIfValid(maxAgeMs: Long): List<OsuMedalCatalogItem>? {
        if (!cacheFile.exists() || !cacheFile.isFile) return null
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        if (maxAgeMs != Long.MAX_VALUE && age > maxAgeMs) return null
        return runCatching {
            val raw = cacheFile.readText()
            val listType = object : TypeToken<List<OsuMedalCatalogItem>>() {}.type
            gson.fromJson<List<OsuMedalCatalogItem>>(raw, listType)
                ?.filter { it.id > 0 && it.name.isNotBlank() }
        }.getOrNull()
    }

    private fun writeDiskCache(items: List<OsuMedalCatalogItem>) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(gson.toJson(items))
        }
    }

    private fun fetchRemoteCatalog(): List<OsuMedalCatalogItem> {
        return try {
            val request = Request.Builder()
                .url(OSEKAI_MEDALS_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "OsuChat-Android/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return emptyList()
                parseOsekaiResponse(body)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseOsekaiResponse(jsonText: String): List<OsuMedalCatalogItem> {
        return runCatching {
            val root: JsonElement = JsonParser.parseString(jsonText)
            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject && root.asJsonObject.has("data") -> root.asJsonObject.getAsJsonArray("data")
                else -> return emptyList()
            }
            val parsed = ArrayList<OsuMedalCatalogItem>(array.size())
            for (elem in array) {
                if (!elem.isJsonObject) continue
                val dto = gson.fromJson(elem, OsekaiMedalDto::class.java) ?: continue
                val id = dto.medalIdCamel ?: dto.idUpper ?: dto.idLower ?: 0
                val name = (dto.nameCamel ?: dto.nameLower)?.trim().orEmpty()
                if (id <= 0 || name.isEmpty()) continue
                val desc = (dto.descCamel ?: dto.descLower)?.trim().orEmpty()
                val grouping = normalizeGrouping(dto.groupCamel ?: dto.groupLower)
                val slug = extractIconSlug(dto.linkCamel ?: dto.linkLower)
                val instructions = (dto.instructionsCamel ?: dto.instructionsLower)?.trim()?.takeIf { it.isNotEmpty() }
                val mode = (dto.modeCamel ?: dto.modeLower)?.trim()?.takeIf { it.isNotEmpty() }
                parsed += OsuMedalCatalogItem(
                    id = id,
                    name = name,
                    description = desc,
                    grouping = grouping,
                    iconSlug = slug,
                    instructions = instructions,
                    mode = mode
                )
            }
            parsed
        }.getOrDefault(emptyList())
    }

    private fun mergeWithFallback(primary: List<OsuMedalCatalogItem>): List<OsuMedalCatalogItem> {
        val byId = LinkedHashMap<Int, OsuMedalCatalogItem>(primary.size + FALLBACK_MEDALS.size)
        for (fb in FALLBACK_MEDALS) {
            byId[fb.id] = fb
        }
        for (item in primary) {
            val prev = byId[item.id]
            byId[item.id] = if (prev != null && item.iconSlug.isBlank()) {
                item.copy(iconSlug = prev.iconSlug)
            } else {
                item
            }
        }
        return byId.values.sortedBy { it.id }
    }

    private fun extractIconSlug(rawLink: String?): String {
        val trimmed = rawLink?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val afterSlash = trimmed.substringAfterLast('/')
        return afterSlash
            .removeSuffix("@2x.png")
            .removeSuffix(".png")
            .trim()
    }

    companion object {
        private const val OSEKAI_MEDALS_URL = "https://osekai.net/medals/api/medals.php"
        private const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L

        fun normalizeGrouping(raw: String?): String {
            val clean = raw?.trim().orEmpty()
            return when {
                clean.contains("hush", ignoreCase = true) -> "Hush-Hush"
                clean.contains("pack", ignoreCase = true) -> "Beatmap Packs"
                clean.contains("mod", ignoreCase = true) -> "Mod Introductions"
                clean.contains("season", ignoreCase = true) ||
                    clean.contains("spotlight", ignoreCase = true) -> "Seasonal Spotlights"
                else -> "Skill & Dedication"
            }
        }

        val MEDAL_CATEGORY_FILTERS = listOf(
            "All",
            "Skill & Dedication",
            "Hush-Hush",
            "Beatmap Packs",
            "Mod Introductions",
            "Seasonal Spotlights",
            "Locked / Missing"
        )

        val FALLBACK_MEDALS: List<OsuMedalCatalogItem> = listOf(
            OsuMedalCatalogItem(50, "500 Combo", "Get 500 max combo.", "Skill & Dedication", "osu-combo-500"),
            OsuMedalCatalogItem(51, "750 Combo", "Get 750 max combo.", "Skill & Dedication", "osu-combo-750"),
            OsuMedalCatalogItem(52, "1,000 Combo", "Get 1,000 max combo.", "Skill & Dedication", "osu-combo-1000"),
            OsuMedalCatalogItem(53, "2,000 Combo", "Get 2,000 max combo.", "Skill & Dedication", "osu-combo-2000"),
            OsuMedalCatalogItem(55, "Rising Star", "Clear a 1-star beatmap.", "Skill & Dedication", "osu-skill-pass-1"),
            OsuMedalCatalogItem(56, "Constellation Prize", "Clear a 2-star beatmap.", "Skill & Dedication", "osu-skill-pass-2"),
            OsuMedalCatalogItem(57, "Building Confidence", "Clear a 3-star beatmap.", "Skill & Dedication", "osu-skill-pass-3"),
            OsuMedalCatalogItem(58, "Insanity Approaches", "Clear a 4-star beatmap.", "Skill & Dedication", "osu-skill-pass-4"),
            OsuMedalCatalogItem(59, "These Clarion Skies", "Clear a 5-star beatmap.", "Skill & Dedication", "osu-skill-pass-5"),
            OsuMedalCatalogItem(60, "Above and Beyond", "Clear a 6-star beatmap.", "Skill & Dedication", "osu-skill-pass-6"),
            OsuMedalCatalogItem(61, "Supremacy", "Clear a 7-star beatmap.", "Skill & Dedication", "osu-skill-pass-7"),
            OsuMedalCatalogItem(62, "Absolution", "Clear an 8-star beatmap.", "Skill & Dedication", "osu-skill-pass-8"),
            OsuMedalCatalogItem(63, "Totality", "Full combo a 1-star beatmap.", "Skill & Dedication", "osu-skill-fc-1"),
            OsuMedalCatalogItem(64, "Business As Usual", "Full combo a 2-star beatmap.", "Skill & Dedication", "osu-skill-fc-2"),
            OsuMedalCatalogItem(65, "Building Steam", "Full combo a 3-star beatmap.", "Skill & Dedication", "osu-skill-fc-3"),
            OsuMedalCatalogItem(66, "Moving Forward", "Full combo a 4-star beatmap.", "Skill & Dedication", "osu-skill-fc-4"),
            OsuMedalCatalogItem(67, "Paradigm Shift", "Full combo a 5-star beatmap.", "Skill & Dedication", "osu-skill-fc-5"),
            OsuMedalCatalogItem(68, "Anguish Quelled", "Full combo a 6-star beatmap.", "Skill & Dedication", "osu-skill-fc-6"),
            OsuMedalCatalogItem(69, "Never Give Up", "Full combo a 7-star beatmap.", "Skill & Dedication", "osu-skill-fc-7"),
            OsuMedalCatalogItem(70, "Aberration", "Full combo an 8-star beatmap.", "Skill & Dedication", "osu-skill-fc-8"),
            OsuMedalCatalogItem(71, "My First Don", "Clear a 1-star taiko beatmap.", "Skill & Dedication", "taiko-skill-pass-1"),
            OsuMedalCatalogItem(119, "Finality", "Clear a 9-star beatmap.", "Mod Introductions", "all-intro-flashlight"),
            OsuMedalCatalogItem(120, "Perfectionist", "Full combo with Sudden Death + Hidden + Hard Rock.", "Mod Introductions", "all-intro-sd"),
            OsuMedalCatalogItem(121, "Time And A Half", "Play with Half Time.", "Mod Introductions", "all-intro-ht"),
            OsuMedalCatalogItem(122, "Really Slow", "Play with Half Time.", "Mod Introductions", "all-intro-ht"),
            OsuMedalCatalogItem(123, "Speed Freak", "Play with Double Time.", "Mod Introductions", "all-intro-dt"),
            OsuMedalCatalogItem(124, "Nightcore", "Play with Nightcore.", "Mod Introductions", "all-intro-nc"),
            OsuMedalCatalogItem(125, "Hard Rock", "Play with Hard Rock.", "Mod Introductions", "all-intro-hr"),
            OsuMedalCatalogItem(126, "Easy", "Play with Easy.", "Mod Introductions", "all-intro-ez"),
            OsuMedalCatalogItem(127, "No Fail", "Play with No Fail.", "Mod Introductions", "all-intro-nf"),
            OsuMedalCatalogItem(128, "Hidden", "Play with Hidden.", "Mod Introductions", "all-intro-hd"),
            OsuMedalCatalogItem(132, "5,000 Plays", "Reach 5,000 osu! plays.", "Skill & Dedication", "osu-plays-5000"),
            OsuMedalCatalogItem(133, "15,000 Plays", "Reach 15,000 osu! plays.", "Skill & Dedication", "osu-plays-15000"),
            OsuMedalCatalogItem(134, "25,000 Plays", "Reach 25,000 osu! plays.", "Skill & Dedication", "osu-plays-25000"),
            OsuMedalCatalogItem(135, "50,000 Plays", "Reach 50,000 osu! plays.", "Skill & Dedication", "osu-plays-50000")
        )
    }
}
