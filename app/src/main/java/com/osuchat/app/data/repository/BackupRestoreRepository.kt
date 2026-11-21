package com.osuchat.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.local.BookmarkedMessageEntity
import com.osuchat.app.data.local.MappoolEntity
import com.osuchat.app.data.local.MappoolSlotEntity
import com.osuchat.app.data.local.OsuChatDatabase
import com.osuchat.app.data.local.ProfileHistoryEntity
import com.osuchat.app.data.local.SavedBeatmapEntity
import com.osuchat.app.data.local.TrackedFriendEntity
import com.osuchat.app.data.local.UserAliasEntity
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.BackupBookmarkedMessageDto
import com.osuchat.app.domain.model.BackupMappoolDto
import com.osuchat.app.domain.model.BackupMappoolSlotDto
import com.osuchat.app.domain.model.BackupPreferencesDto
import com.osuchat.app.domain.model.BackupProfileHistoryDto
import com.osuchat.app.domain.model.BackupSavedBeatmapDto
import com.osuchat.app.domain.model.BackupSummary
import com.osuchat.app.domain.model.BackupTrackedFriendDto
import com.osuchat.app.domain.model.BackupUserAliasDto
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.domain.model.OsuChatBackupDto
import com.osuchat.app.domain.model.ParsedBackup
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.domain.model.WidgetTheme
import com.osuchat.app.util.BeatmapUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreRepository @Inject constructor(
    private val database: OsuChatDatabase,
    private val prefs: UserPreferencesDataStore,
    private val gson: Gson
) {
    private val prettyGson: Gson = gson.newBuilder().setPrettyPrinting().create()

    suspend fun exportBackupToUri(context: Context, uri: Uri): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            try {
                val pools = database.mappoolDao().observeMappools().first()
                val slotsByPoolId = database.mappoolDao().observeAllSlots().first().groupBy { it.poolId }
                val savedBeatmaps = database.savedBeatmapDao().getAllSavedBeatmaps()
                val aliases = database.userAliasDao().observeAllAliases().first()
                val trackedFriends = database.trackedFriendDao().getAllTrackedFriends()
                val bookmarks = database.bookmarkedMessageDao().observeBookmarks().first()
                val profileHistory = database.profileHistoryDao().getAllSnapshots()

                val settings = prefs.settingsFlow.first()
                val mutedChannels = prefs.mutedChannels.first().sorted()
                val dotOnlyChannels = prefs.dotOnlyChannels.first().sorted()
                val ignoredUsers = prefs.ignoredUsersFlow.first().sorted()
                val ignoreDisplayMode = prefs.ignoreDisplayModeFlow.first()
                val refereeMacros = prefs.refereeMacrosFlow.first()
                val matchSetupTemplates = prefs.matchSetupTemplatesFlow.first()

                val backupDto = OsuChatBackupDto(
                    schemaVersion = OsuChatBackupDto.CURRENT_SCHEMA_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    mappools = pools.map { pool ->
                        BackupMappoolDto(
                            name = pool.name,
                            createdAt = pool.createdAt,
                            slots = slotsByPoolId[pool.id].orEmpty()
                                .sortedWith(compareBy<MappoolSlotEntity> { it.orderIndex }.thenBy { it.id })
                                .mapIndexed { idx, slot ->
                                    BackupMappoolSlotDto(
                                        slotTag = slot.slotTag,
                                        beatmapId = slot.beatmapId,
                                        mods = slot.mods,
                                        orderIndex = idx
                                    )
                                }
                        )
                    },
                    savedBeatmaps = savedBeatmaps.map { entity ->
                        BackupSavedBeatmapDto(
                            beatmapId = entity.beatmapId,
                            beatmapsetId = entity.beatmapsetId,
                            title = entity.title,
                            artist = entity.artist,
                            creator = entity.creator,
                            version = entity.version,
                            starRating = entity.starRating,
                            bpm = entity.bpm,
                            mode = entity.mode,
                            coverUrl = entity.coverUrl,
                            previewAudioUrl = entity.previewAudioUrl,
                            savedAt = entity.savedAt,
                            tagsCsv = entity.tagsCsv,
                            totalLengthSeconds = entity.totalLengthSeconds
                        )
                    },
                    userAliases = aliases.map { alias ->
                        BackupUserAliasDto(
                            normalizedUsername = alias.normalizedUsername,
                            originalUsername = alias.originalUsername,
                            userId = alias.userId,
                            customNickname = alias.customNickname,
                            updatedAt = alias.updatedAt,
                            note = alias.note
                        )
                    },
                    trackedFriends = trackedFriends.map { friend ->
                        BackupTrackedFriendDto(
                            normalizedUsername = friend.normalizedUsername,
                            userId = friend.userId,
                            username = friend.username,
                            avatarUrl = friend.avatarUrl,
                            topScoreIdsCsv = friend.topScoreIdsCsv,
                            lastCheckedAt = friend.lastCheckedAt,
                            addedAt = friend.addedAt,
                            notifyOnline = friend.notifyOnline
                        )
                    },
                    bookmarkedMessages = bookmarks.map { bm ->
                        BackupBookmarkedMessageDto(
                            messageId = bm.messageId,
                            channelId = bm.channelId,
                            channelName = bm.channelName,
                            senderName = bm.senderName,
                            content = bm.content,
                            messageTimestamp = bm.messageTimestamp,
                            bookmarkedAt = bm.bookmarkedAt
                        )
                    },
                    profileHistory = profileHistory.map { point ->
                        BackupProfileHistoryDto(
                            dayKey = point.dayKey,
                            mode = point.mode,
                            globalRank = point.globalRank,
                            countryRank = point.countryRank,
                            pp = point.pp,
                            accuracy = point.accuracy,
                            playCount = point.playCount,
                            recordedAt = point.recordedAt
                        )
                    },
                    preferences = BackupPreferencesDto(
                        autoJoinEnabled = settings.autoJoinEnabled,
                        autoJoinChannels = settings.autoJoinChannels,
                        mutedChannels = mutedChannels,
                        highlightKeywords = settings.highlightKeywords,
                        notifyDm = settings.notifyDirectMessages,
                        notifyKeywords = settings.notifyKeywordMentions,
                        keepAliveService = settings.keepAliveService,
                        autoConnectBoot = settings.autoConnectOnBoot,
                        themeMode = settings.themeMode.name,
                        widgetTheme = settings.widgetTheme.name,
                        widgetTranslucent = settings.widgetTranslucent,
                        refereeMacros = refereeMacros,
                        ignoredUsers = ignoredUsers,
                        ignoreDisplayMode = ignoreDisplayMode,
                        dotOnlyChannels = dotOnlyChannels,
                        matchSetupTemplates = matchSetupTemplates,
                        wallpaperPreset = settings.globalWallpaper.presetId,
                        wallpaperDim = settings.globalWallpaper.dimAmount,
                        wallpaperBlur = settings.globalWallpaper.blurRadiusDp,
                        wallpaperScale = settings.globalWallpaper.scale,
                        wallpaperBiasX = settings.globalWallpaper.biasX,
                        wallpaperBiasY = settings.globalWallpaper.biasY
                    )
                )

                val jsonPayload = prettyGson.toJson(backupDto)
                val outStream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: context.contentResolver.openOutputStream(uri)
                    ?: error("Unable to open destination file for writing.")
                outStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(jsonPayload)
                }

                Result.success(backupDto.toSummary())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    suspend fun inspectBackupUri(context: Context, uri: Uri): Result<ParsedBackup> =
        withContext(Dispatchers.IO) {
            try {
                val rawJson = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: error("Unable to read selected backup file.")

                if (rawJson.isBlank()) {
                    error("Selected backup file is empty.")
                }

                val rootElement = JsonParser.parseString(rawJson)
                if (!rootElement.isJsonObject) {
                    error("Invalid backup file format.")
                }
                val rootObj = rootElement.asJsonObject
                val versionEl = rootObj.get("schemaVersion")
                    ?: error("Missing schemaVersion in backup file.")
                if (!versionEl.isJsonPrimitive || !versionEl.asJsonPrimitive.isNumber) {
                    error("Invalid schemaVersion in backup file.")
                }

                val parsedDto = gson.fromJson(rootObj, OsuChatBackupDto::class.java)
                    ?: error("Failed to parse backup JSON.")
                if (parsedDto.schemaVersion < 1) {
                    error("Unsupported backup schemaVersion: ${parsedDto.schemaVersion}")
                }

                val sanitized = sanitizeDto(parsedDto)
                Result.success(ParsedBackup(dto = sanitized, summary = sanitized.toSummary()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    suspend fun restoreBackup(parsed: ParsedBackup): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            try {
                val dto = sanitizeDto(parsed.dto)
                if (dto.schemaVersion < 1) {
                    error("Unsupported backup schemaVersion: ${dto.schemaVersion}")
                }

                database.withTransaction {
                    val mappoolDao = database.mappoolDao()
                    val existingPools = mappoolDao.observeMappools().first()
                    val existingByName = LinkedHashMap<String, Long>(existingPools.size)
                    for (pool in existingPools) {
                        existingByName.putIfAbsent(pool.name.trim().lowercase(Locale.ROOT), pool.id)
                    }

                    for (poolDto in dto.mappools) {
                        val cleanName = poolDto.name.trim()
                        if (cleanName.isEmpty()) continue
                        val nameKey = cleanName.lowercase(Locale.ROOT)
                        val existingId = existingByName[nameKey]
                        val resolvedPoolId = if (existingId != null) {
                            mappoolDao.deleteSlotsForPool(existingId)
                            existingId
                        } else {
                            val insertedId = mappoolDao.insertPool(
                                MappoolEntity(
                                    id = 0L,
                                    name = cleanName,
                                    createdAt = poolDto.createdAt.takeIf { it > 0L }
                                        ?: System.currentTimeMillis()
                                )
                            )
                            existingByName[nameKey] = insertedId
                            insertedId
                        }

                        val orderedSlots = poolDto.slots
                            .filter { it.beatmapId > 0L }
                            .sortedBy { it.orderIndex }
                            .mapIndexed { idx, slot ->
                                MappoolSlotEntity(
                                    id = 0L,
                                    poolId = resolvedPoolId,
                                    slotTag = slot.slotTag.trim(),
                                    beatmapId = slot.beatmapId,
                                    mods = slot.mods.trim().ifEmpty { "NF" },
                                    orderIndex = idx
                                )
                            }
                        if (orderedSlots.isNotEmpty()) {
                            mappoolDao.insertSlots(orderedSlots)
                        }
                    }

                    val savedBeatmapDao = database.savedBeatmapDao()
                    for (saved in dto.savedBeatmaps) {
                        if (saved.beatmapId <= 0L) continue
                        savedBeatmapDao.upsert(
                            SavedBeatmapEntity(
                                beatmapId = saved.beatmapId,
                                beatmapsetId = saved.beatmapsetId,
                                title = saved.title,
                                artist = saved.artist,
                                creator = saved.creator,
                                version = saved.version,
                                starRating = saved.starRating,
                                bpm = saved.bpm,
                                mode = saved.mode.ifBlank { "osu" },
                                coverUrl = saved.coverUrl,
                                previewAudioUrl = saved.previewAudioUrl,
                                savedAt = saved.savedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                tagsCsv = saved.tagsCsv,
                                totalLengthSeconds = saved.totalLengthSeconds.coerceAtLeast(0)
                            )
                        )
                    }

                    val userAliasDao = database.userAliasDao()
                    for (alias in dto.userAliases) {
                        val original = alias.originalUsername.trim()
                        val norm = alias.normalizedUsername.trim()
                            .ifEmpty { BeatmapUrlParser.normalizeUsername(original) }
                        if (norm.isEmpty()) continue
                        userAliasDao.upsertAlias(
                            UserAliasEntity(
                                normalizedUsername = norm,
                                originalUsername = original.ifEmpty { norm },
                                userId = alias.userId,
                                customNickname = alias.customNickname,
                                updatedAt = alias.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                note = alias.note
                            )
                        )
                    }

                    val trackedFriendDao = database.trackedFriendDao()
                    for (friend in dto.trackedFriends) {
                        val uname = friend.username.trim()
                        val norm = friend.normalizedUsername.trim()
                            .ifEmpty { BeatmapUrlParser.normalizeUsername(uname) }
                        if (norm.isEmpty()) continue
                        trackedFriendDao.upsertTrackedFriend(
                            TrackedFriendEntity(
                                normalizedUsername = norm,
                                userId = friend.userId,
                                username = uname.ifEmpty { norm },
                                avatarUrl = friend.avatarUrl,
                                topScoreIdsCsv = friend.topScoreIdsCsv,
                                lastCheckedAt = friend.lastCheckedAt,
                                addedAt = friend.addedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                notifyOnline = friend.notifyOnline
                            )
                        )
                    }

                    val bookmarkedDao = database.bookmarkedMessageDao()
                    for (bm in dto.bookmarkedMessages) {
                        if (bm.messageId.isBlank() || bm.channelId.isBlank()) continue
                        bookmarkedDao.upsertBookmark(
                            BookmarkedMessageEntity(
                                messageId = bm.messageId,
                                channelId = bm.channelId,
                                channelName = bm.channelName,
                                senderName = bm.senderName,
                                content = bm.content,
                                messageTimestamp = bm.messageTimestamp,
                                bookmarkedAt = bm.bookmarkedAt.takeIf { it > 0L }
                                    ?: System.currentTimeMillis()
                            )
                        )
                    }

                    val profileHistoryDao = database.profileHistoryDao()
                    for (point in dto.profileHistory) {
                        if (point.dayKey.isBlank() || point.mode.isBlank()) continue
                        profileHistoryDao.upsertSnapshot(
                            ProfileHistoryEntity(
                                dayKey = point.dayKey,
                                mode = point.mode,
                                globalRank = point.globalRank,
                                countryRank = point.countryRank,
                                pp = point.pp,
                                accuracy = point.accuracy,
                                playCount = point.playCount,
                                recordedAt = point.recordedAt.takeIf { it > 0L }
                                    ?: System.currentTimeMillis()
                            )
                        )
                    }
                }

                prefs.restoreNonSensitivePreferences(dto.preferences)
                Result.success(dto.toSummary())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    @Suppress("USELESS_ELVIS")
    private fun sanitizeDto(raw: OsuChatBackupDto): OsuChatBackupDto {
        val rawPrefs = raw.preferences ?: BackupPreferencesDto()
        val safePrefs = BackupPreferencesDto(
            autoJoinEnabled = rawPrefs.autoJoinEnabled,
            autoJoinChannels = rawPrefs.autoJoinChannels ?: AppSettings.DEFAULT_AUTO_JOIN_CHANNELS,
            mutedChannels = rawPrefs.mutedChannels ?: emptyList(),
            highlightKeywords = rawPrefs.highlightKeywords ?: emptyList(),
            notifyDm = rawPrefs.notifyDm,
            notifyKeywords = rawPrefs.notifyKeywords,
            keepAliveService = rawPrefs.keepAliveService,
            autoConnectBoot = rawPrefs.autoConnectBoot,
            themeMode = (rawPrefs.themeMode ?: ThemeMode.SYSTEM.name),
            widgetTheme = (rawPrefs.widgetTheme ?: WidgetTheme.NOTHING_OS.name),
            widgetTranslucent = rawPrefs.widgetTranslucent,
            refereeMacros = (rawPrefs.refereeMacros ?: RefereeMacro.DEFAULT_MACROS).map { macro ->
                RefereeMacro(
                    id = macro.id ?: "",
                    label = macro.label ?: "",
                    message = macro.message ?: ""
                )
            },
            ignoredUsers = rawPrefs.ignoredUsers
                ?.mapNotNull { it?.let(BeatmapUrlParser::normalizeOsuNick)?.takeIf { n -> n.isNotEmpty() } }
                ?.distinct(),
            ignoreDisplayMode = rawPrefs.ignoreDisplayMode
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it == "HIDE" || it == "COLLAPSE" },
            dotOnlyChannels = rawPrefs.dotOnlyChannels
                ?.mapNotNull { it?.trim()?.takeIf { c -> c.isNotEmpty() } }
                ?.distinct(),
            matchSetupTemplates = rawPrefs.matchSetupTemplates?.map { t ->
                MatchSetupTemplate(
                    id = t.id ?: "",
                    name = t.name ?: "",
                    roomNamePattern = t.roomNamePattern ?: "TOURNEY: ({red}) vs ({blue})",
                    bestOf = t.bestOf.coerceIn(1, 21),
                    teamMode = t.teamMode.coerceIn(0, 3),
                    scoreMode = t.scoreMode.coerceIn(0, 3),
                    roomSize = t.roomSize.coerceIn(2, 16),
                    lockSlots = t.lockSlots,
                    password = t.password ?: "",
                    defaultRefs = (t.defaultRefs ?: emptyList()).mapNotNull { r -> r?.trim()?.takeIf { it.isNotEmpty() } },
                    defaultInvites = (t.defaultInvites ?: emptyList()).mapNotNull { i -> i?.trim()?.takeIf { it.isNotEmpty() } }
                )
            },
            wallpaperPreset = rawPrefs.wallpaperPreset?.coerceAtLeast(0),
            wallpaperDim = rawPrefs.wallpaperDim?.takeIf { it.isFinite() }?.coerceIn(0f, 0.85f),
            wallpaperBlur = rawPrefs.wallpaperBlur?.takeIf { it.isFinite() }?.coerceIn(0f, 25f),
            wallpaperScale = rawPrefs.wallpaperScale?.takeIf { it.isFinite() }?.coerceIn(
                WallpaperConfig.MIN_SCALE,
                WallpaperConfig.MAX_SCALE
            ),
            wallpaperBiasX = rawPrefs.wallpaperBiasX?.takeIf { it.isFinite() }?.coerceIn(
                WallpaperConfig.MIN_BIAS,
                WallpaperConfig.MAX_BIAS
            ),
            wallpaperBiasY = rawPrefs.wallpaperBiasY?.takeIf { it.isFinite() }?.coerceIn(
                WallpaperConfig.MIN_BIAS,
                WallpaperConfig.MAX_BIAS
            )
        )

        return OsuChatBackupDto(
            schemaVersion = raw.schemaVersion,
            exportedAt = raw.exportedAt,
            appVersionName = raw.appVersionName ?: "",
            mappools = (raw.mappools ?: emptyList()).map { pool ->
                BackupMappoolDto(
                    name = pool.name ?: "",
                    createdAt = pool.createdAt,
                    slots = (pool.slots ?: emptyList()).map { slot ->
                        BackupMappoolSlotDto(
                            slotTag = slot.slotTag ?: "",
                            beatmapId = slot.beatmapId,
                            mods = slot.mods ?: "NF",
                            orderIndex = slot.orderIndex
                        )
                    }
                )
            },
            savedBeatmaps = (raw.savedBeatmaps ?: emptyList()).map { item ->
                BackupSavedBeatmapDto(
                    beatmapId = item.beatmapId,
                    beatmapsetId = item.beatmapsetId,
                    title = item.title ?: "",
                    artist = item.artist ?: "",
                    creator = item.creator ?: "",
                    version = item.version ?: "",
                    starRating = item.starRating,
                    bpm = item.bpm,
                    mode = item.mode ?: "osu",
                    coverUrl = item.coverUrl ?: "",
                    previewAudioUrl = item.previewAudioUrl ?: "",
                    savedAt = item.savedAt,
                    tagsCsv = item.tagsCsv ?: "",
                    totalLengthSeconds = item.totalLengthSeconds
                )
            },
            userAliases = (raw.userAliases ?: emptyList()).map { alias ->
                BackupUserAliasDto(
                    normalizedUsername = alias.normalizedUsername ?: "",
                    originalUsername = alias.originalUsername ?: "",
                    userId = alias.userId,
                    customNickname = alias.customNickname ?: "",
                    updatedAt = alias.updatedAt,
                    note = alias.note ?: ""
                )
            },
            trackedFriends = (raw.trackedFriends ?: emptyList()).map { friend ->
                BackupTrackedFriendDto(
                    normalizedUsername = friend.normalizedUsername ?: "",
                    userId = friend.userId,
                    username = friend.username ?: "",
                    avatarUrl = friend.avatarUrl ?: "",
                    topScoreIdsCsv = friend.topScoreIdsCsv ?: "",
                    lastCheckedAt = friend.lastCheckedAt,
                    addedAt = friend.addedAt,
                    notifyOnline = friend.notifyOnline
                )
            },
            bookmarkedMessages = (raw.bookmarkedMessages ?: emptyList()).map { bm ->
                BackupBookmarkedMessageDto(
                    messageId = bm.messageId ?: "",
                    channelId = bm.channelId ?: "",
                    channelName = bm.channelName ?: "",
                    senderName = bm.senderName ?: "",
                    content = bm.content ?: "",
                    messageTimestamp = bm.messageTimestamp,
                    bookmarkedAt = bm.bookmarkedAt
                )
            },
            profileHistory = (raw.profileHistory ?: emptyList()).map { point ->
                BackupProfileHistoryDto(
                    dayKey = point.dayKey ?: "",
                    mode = point.mode ?: "osu",
                    globalRank = point.globalRank,
                    countryRank = point.countryRank,
                    pp = point.pp,
                    accuracy = point.accuracy,
                    playCount = point.playCount,
                    recordedAt = point.recordedAt
                )
            },
            preferences = safePrefs
        )
    }

    private fun OsuChatBackupDto.toSummary(): BackupSummary = BackupSummary(
        schemaVersion = schemaVersion,
        exportedAt = exportedAt,
        appVersionName = appVersionName,
        mappoolsCount = mappools.count { it.name.isNotBlank() },
        savedBeatmapsCount = savedBeatmaps.count { it.beatmapId > 0L },
        userAliasesCount = userAliases.count {
            it.normalizedUsername.isNotBlank() || it.originalUsername.isNotBlank()
        },
        trackedFriendsCount = trackedFriends.count {
            it.normalizedUsername.isNotBlank() || it.username.isNotBlank()
        },
        bookmarkedMessagesCount = bookmarkedMessages.count {
            it.messageId.isNotBlank() && it.channelId.isNotBlank()
        },
        profileHistoryCount = profileHistory.count {
            it.dayKey.isNotBlank() && it.mode.isNotBlank()
        },
        refereeMacrosCount = preferences.refereeMacros.count {
            it.label.isNotBlank() && it.message.isNotBlank()
        },
        matchSetupTemplatesCount = preferences.matchSetupTemplates.orEmpty().count {
            it.name.isNotBlank() && it.roomNamePattern.isNotBlank()
        },
        ignoredUsersCount = preferences.ignoredUsers.orEmpty().count { it.isNotBlank() }
    )
}
