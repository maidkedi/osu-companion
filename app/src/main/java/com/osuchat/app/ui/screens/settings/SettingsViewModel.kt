package com.osuchat.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.network.NetworkConnectivityMonitor
import com.osuchat.app.data.repository.AppUpdateRepository
import com.osuchat.app.data.repository.BackupRestoreRepository
import com.osuchat.app.data.repository.UpdateCheckResult
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MappoolSlot
import com.osuchat.app.domain.model.ParsedBackup
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.model.SeasonalBackgroundItem
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.UserAlias
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.domain.model.WidgetTheme
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.service.ChatConnectionService
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.widget.BanchoStatsWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class BackupLocalState(
    val isBusy: Boolean = false,
    val pendingRestoreBackup: ParsedBackup? = null,
    val actionMessage: String? = null
)

private data class UpdateLocalState(
    val isChecking: Boolean = false,
    val actionMessage: String? = null
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE,
    val isNetworkOnline: Boolean = true,
    val aliases: List<UserAlias> = emptyList(),
    val mappools: List<Mappool> = emptyList(),
    val selfProfile: PlayerProfile? = null,
    val onlineFriendsCount: Int = 0,
    val isPreviewLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val previewUpdatedAtMillis: Long = 0L,
    val actionError: String? = null,
    val actionMessage: String? = null,
    val seasonalBackgrounds: List<SeasonalBackgroundItem> = emptyList(),
    val isSeasonalLoading: Boolean = false,
    val downloadingSeasonalUrl: String? = null,
    val isBackupBusy: Boolean = false,
    val pendingRestoreBackup: ParsedBackup? = null,
    val isCheckingUpdates: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val backupRestoreRepository: BackupRestoreRepository,
    private val appUpdateRepository: AppUpdateRepository
) : ViewModel() {

    private val selfProfile = MutableStateFlow<PlayerProfile?>(null)
    private val isPreviewLoading = MutableStateFlow(false)
    private val isLoggingOut = MutableStateFlow(false)
    private val previewUpdatedAtMillis = MutableStateFlow(0L)
    private val actionError = MutableStateFlow<String?>(null)
    private val seasonalState = MutableStateFlow(Triple<List<SeasonalBackgroundItem>, Boolean, String?>(emptyList(), false, null))
    private val backupLocalState = MutableStateFlow(BackupLocalState())
    private val updateLocalState = MutableStateFlow(UpdateLocalState())

    val uiState: StateFlow<SettingsUiState> = combine(
        chatRepository.settings,
        chatRepository.connectionStatus,
        networkMonitor.isOnline,
        chatRepository.allAliases,
        chatRepository.friends,
        selfProfile,
        isPreviewLoading,
        isLoggingOut,
        previewUpdatedAtMillis,
        actionError,
        chatRepository.mappools,
        seasonalState,
        backupLocalState,
        updateLocalState
    ) { values ->
        val settings = values[0] as AppSettings
        val status = values[1] as ConnectionStatus
        val networkOnline = values[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val aliases = values[3] as List<UserAlias>
        @Suppress("UNCHECKED_CAST")
        val friends = values[4] as List<com.osuchat.app.domain.model.FriendContact>
        val profile = values[5] as PlayerProfile?
        val previewBusy = values[6] as Boolean
        val loggingOut = values[7] as Boolean
        val previewUpdated = values[8] as Long
        val err = values[9] as String?
        @Suppress("UNCHECKED_CAST")
        val mappools = values[10] as List<Mappool>
        @Suppress("UNCHECKED_CAST")
        val seasonal = values[11] as Triple<List<SeasonalBackgroundItem>, Boolean, String?>
        val backup = values[12] as BackupLocalState
        val update = values[13] as UpdateLocalState

        SettingsUiState(
            settings = settings,
            connectionStatus = status,
            isNetworkOnline = networkOnline,
            aliases = aliases,
            mappools = mappools,
            selfProfile = profile,
            onlineFriendsCount = friends.count { it.isOnline },
            isPreviewLoading = previewBusy,
            isLoggingOut = loggingOut,
            previewUpdatedAtMillis = previewUpdated,
            actionError = err,
            actionMessage = update.actionMessage ?: backup.actionMessage,
            seasonalBackgrounds = seasonal.first,
            isSeasonalLoading = seasonal.second,
            downloadingSeasonalUrl = seasonal.third,
            isBackupBusy = backup.isBusy,
            pendingRestoreBackup = backup.pendingRestoreBackup,
            isCheckingUpdates = update.isChecking
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        viewModelScope.launch {
            loadSeasonalBackgrounds()
        }
        viewModelScope.launch {
            chatRepository.settings
                .map { it.authMode to it.username }
                .distinctUntilChanged()
                .collect { (authMode, _) ->
                    if (authMode == AuthMode.NONE) {
                        selfProfile.value = null
                        previewUpdatedAtMillis.value = 0L
                    } else {
                        loadWidgetPreviewProfile(forceRefresh = false)
                    }
                }
        }
    }

    fun refreshWidgetPreviewProfile() {
        if (uiState.value.settings.authMode == AuthMode.NONE) return
        if (!networkMonitor.isOnline.value) {
            actionError.value = "Offline — showing cached widget stats."
            return
        }
        viewModelScope.launch {
            loadWidgetPreviewProfile(forceRefresh = true)
            chatRepository.refreshFriends()
        }
    }

    private suspend fun loadWidgetPreviewProfile(forceRefresh: Boolean) {
        isPreviewLoading.value = true
        try {
            val result = chatRepository.getCachedSelfProfile(forceRefresh = forceRefresh)
            result.getOrNull()?.let { profile ->
                selfProfile.value = profile
                previewUpdatedAtMillis.value = System.currentTimeMillis()
                actionError.value = null
            } ?: run {
                if (forceRefresh) {
                    actionError.value = "Could not refresh profile stats right now."
                }
            }
        } finally {
            isPreviewLoading.value = false
        }
    }

    fun reconnect(context: Context) {
        if (!networkMonitor.isOnline.value) {
            actionError.value = "No internet connection. Check Wi-Fi or mobile data and retry."
            return
        }
        actionError.value = null
        viewModelScope.launch {
            chatRepository.setExplicitlyDisconnected(false)
            chatRepository.reconnectIfAuthenticated()
            val s = uiState.value.settings
            if (s.keepAliveService && s.authMode != AuthMode.NONE) {
                ChatConnectionService.start(context)
            }
        }
    }

    fun clearActionError() {
        actionError.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { chatRepository.setThemeMode(mode) }
    }

    fun setWidgetTheme(
        context: Context,
        theme: WidgetTheme
    ) {
        viewModelScope.launch {
            chatRepository.setWidgetTheme(theme)
            BanchoStatsWidget.refreshThemeAndNotify(context)
        }
    }

    fun setWidgetTranslucent(
        context: Context,
        translucent: Boolean
    ) {
        viewModelScope.launch {
            chatRepository.setWidgetTranslucent(translucent)
            BanchoStatsWidget.refreshThemeAndNotify(context)
        }
    }

    fun setGlobalWallpaper(config: WallpaperConfig) {
        viewModelScope.launch { chatRepository.setGlobalWallpaper(config) }
    }

    fun loadSeasonalBackgrounds() {
        val current = seasonalState.value
        if (current.second) return
        viewModelScope.launch {
            seasonalState.value = Triple(current.first, true, current.third)
            val result = chatRepository.fetchSeasonalBackgrounds()
            val items = result.getOrDefault(seasonalState.value.first)
            seasonalState.value = Triple(items, false, seasonalState.value.third)
        }
    }

    fun selectSeasonalWallpaper(imageUrl: String) {
        if (seasonalState.value.third != null) return
        viewModelScope.launch {
            seasonalState.value = Triple(seasonalState.value.first, seasonalState.value.second, imageUrl)
            try {
                chatRepository.downloadSeasonalWallpaper(imageUrl)
                    .onSuccess { localUri ->
                        val current = uiState.value.settings.globalWallpaper
                        chatRepository.setGlobalWallpaper(
                            current.copy(
                                customImageUri = localUri,
                                scale = 1.0f,
                                biasX = 0.0f,
                                biasY = 0.0f
                            )
                        )
                    }
                    .onFailure {
                        actionError.value = "Could not download seasonal background."
                    }
            } finally {
                seasonalState.value = Triple(seasonalState.value.first, seasonalState.value.second, null)
            }
        }
    }

    fun addHighlightKeyword(keyword: String): String? {
        val clean = keyword.trim()
        if (clean.isEmpty()) return "Enter a keyword"
        if (clean.contains(",")) return "Commas are not allowed in a single keyword"
        val currentSettings = uiState.value.settings
        if (currentSettings.username.isNotBlank() && clean.equals(currentSettings.username, ignoreCase = true)) {
            return "Your username is already highlighted"
        }
        if (currentSettings.highlightKeywords.any { it.equals(clean, ignoreCase = true) }) {
            return "\"$clean\" is already in your keywords"
        }
        viewModelScope.launch {
            chatRepository.setHighlightKeywords(currentSettings.highlightKeywords + clean)
        }
        return null
    }

    fun removeHighlightKeyword(keyword: String) {
        viewModelScope.launch {
            val current = uiState.value.settings.highlightKeywords
            chatRepository.setHighlightKeywords(current.filterNot { it == keyword })
        }
    }

    fun setNotificationPrefs(notifyDm: Boolean, notifyKeywords: Boolean) {
        viewModelScope.launch {
            chatRepository.setNotificationPreferences(notifyDm, notifyKeywords)
        }
    }

    fun setTrackedAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.setTrackedAlertsEnabled(enabled)
        }
    }

    fun setDefaultTrackedTopPlayLimit(limit: Int) {
        viewModelScope.launch {
            chatRepository.setDefaultTrackedTopPlayLimit(limit)
        }
    }

    fun setTrackedAlertsIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            chatRepository.setTrackedAlertsIntervalMinutes(minutes)
        }
    }

    fun addIgnoredUser(rawUsername: String): String? {
        val clean = rawUsername.trim()
        if (clean.isEmpty()) return "Enter a username"
        val norm = BeatmapUrlParser.normalizeOsuNick(clean)
        if (norm.isEmpty()) return "Enter a valid username"
        if (norm == "banchobot" || norm == "system") return "Cannot ignore $clean"
        val current = uiState.value.settings
        val selfNorm = BeatmapUrlParser.normalizeOsuNick(current.username)
        val selfIrcNorm = BeatmapUrlParser.normalizeOsuNick(current.ircUsername)
        if ((selfNorm.isNotEmpty() && norm == selfNorm) || (selfIrcNorm.isNotEmpty() && norm == selfIrcNorm)) {
            return "Cannot ignore yourself"
        }
        if (norm in current.ignoredUsers) {
            return "\"$clean\" is already ignored"
        }
        viewModelScope.launch {
            chatRepository.ignoreUser(clean)
        }
        return null
    }

    fun removeIgnoredUser(username: String) {
        viewModelScope.launch {
            chatRepository.unignoreUser(username)
        }
    }

    fun setIgnoreDisplayMode(mode: String) {
        viewModelScope.launch {
            chatRepository.setIgnoreDisplayMode(mode)
        }
    }

    fun setKeepAliveService(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.setKeepAliveService(enabled)
            if (enabled) {
                ChatConnectionService.start(context)
            } else {
                ChatConnectionService.stop(context)
            }
        }
    }

    fun setAutoConnectOnBoot(enabled: Boolean) {
        viewModelScope.launch { chatRepository.setAutoConnectOnBoot(enabled) }
    }

    fun setAutoJoinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.setAutoJoinConfig(enabled, uiState.value.settings.autoJoinChannels)
        }
    }

    fun addAutoJoinChannel(rawChannel: String): String? {
        val trimmed = rawChannel.trim().lowercase()
        if (trimmed.isBlank()) return "Enter a channel name"
        val normalized = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        if (normalized.length < 2 || normalized.any { it.isWhitespace() }) {
            return "Channel name cannot contain spaces"
        }
        if (!normalized.matches(Regex("^#[a-z0-9_+.-]{1,48}$"))) {
            return "Use letters, numbers, '_', '+', '.', or '-' after #"
        }
        val current = uiState.value.settings.autoJoinChannels
        if (current.any { it.equals(normalized, ignoreCase = true) }) {
            return "$normalized is already in auto-join list"
        }
        viewModelScope.launch {
            chatRepository.setAutoJoinConfig(
                uiState.value.settings.autoJoinEnabled,
                current + normalized
            )
        }
        return null
    }

    fun removeAutoJoinChannel(channel: String) {
        viewModelScope.launch {
            val updated = uiState.value.settings.autoJoinChannels.filterNot {
                it.equals(channel, ignoreCase = true)
            }
            chatRepository.setAutoJoinConfig(uiState.value.settings.autoJoinEnabled, updated)
        }
    }

    fun moveAutoJoinChannel(index: Int, delta: Int) {
        val current = uiState.value.settings.autoJoinChannels
        val target = index + delta
        if (index !in current.indices || target !in current.indices) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        viewModelScope.launch {
            chatRepository.setAutoJoinConfig(uiState.value.settings.autoJoinEnabled, mutable)
        }
    }

    fun removeAlias(username: String) {
        viewModelScope.launch { chatRepository.setUserAlias(username, null) }
    }

    fun saveMappool(poolId: Long?, name: String, slots: List<MappoolSlot>) {
        viewModelScope.launch {
            chatRepository.saveMappool(poolId, name, slots)
                .onFailure { err ->
                    actionError.value = err.message ?: "Could not save mappool."
                }
        }
    }

    fun deleteMappool(poolId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMappool(poolId)
                .onFailure { err ->
                    actionError.value = err.message ?: "Could not delete mappool."
                }
        }
    }

    suspend fun getCachedBeatmaps(beatmapIds: Collection<Long>): Map<Long, BeatmapMetadata> {
        return chatRepository.getCachedBeatmaps(beatmapIds)
    }

    suspend fun resolveBeatmapDetails(beatmapId: Long): Result<BeatmapMetadata> {
        return chatRepository.getBeatmapDetails(beatmapId)
    }

    fun exportBackup(context: Context, uri: Uri) {
        if (backupLocalState.value.isBusy) return
        val appContext = context.applicationContext
        backupLocalState.update { it.copy(isBusy = true, actionMessage = null) }
        viewModelScope.launch {
            backupRestoreRepository.exportBackupToUri(appContext, uri)
                .onSuccess { summary ->
                    backupLocalState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = "Backup exported (${summary.mappoolsCount} pools, ${summary.savedBeatmapsCount} maps, ${summary.userAliasesCount} aliases)."
                        )
                    }
                }
                .onFailure { err ->
                    backupLocalState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = sanitizeBackupError(err, "Could not export backup.")
                        )
                    }
                }
        }
    }

    fun inspectBackupForRestore(context: Context, uri: Uri) {
        if (backupLocalState.value.isBusy) return
        val appContext = context.applicationContext
        backupLocalState.update { it.copy(isBusy = true, actionMessage = null) }
        viewModelScope.launch {
            backupRestoreRepository.inspectBackupUri(appContext, uri)
                .onSuccess { parsed ->
                    backupLocalState.update {
                        it.copy(isBusy = false, pendingRestoreBackup = parsed)
                    }
                }
                .onFailure { err ->
                    backupLocalState.update {
                        it.copy(
                            isBusy = false,
                            pendingRestoreBackup = null,
                            actionMessage = sanitizeBackupError(err, "Invalid backup JSON file.")
                        )
                    }
                }
        }
    }

    fun confirmRestoreBackup(context: Context) {
        val pending = backupLocalState.value.pendingRestoreBackup ?: return
        if (backupLocalState.value.isBusy) return
        val appContext = context.applicationContext
        backupLocalState.update { it.copy(isBusy = true, actionMessage = null) }
        viewModelScope.launch {
            backupRestoreRepository.restoreBackup(pending)
                .onSuccess { summary ->
                    BanchoStatsWidget.refreshThemeAndNotify(appContext)
                    backupLocalState.update {
                        it.copy(
                            isBusy = false,
                            pendingRestoreBackup = null,
                            actionMessage = "Backup restored (${summary.mappoolsCount} pools, ${summary.savedBeatmapsCount} maps, ${summary.userAliasesCount} aliases)."
                        )
                    }
                }
                .onFailure { err ->
                    backupLocalState.update {
                        it.copy(
                            isBusy = false,
                            pendingRestoreBackup = null,
                            actionMessage = sanitizeBackupError(err, "Could not restore backup.")
                        )
                    }
                }
        }
    }

    fun dismissRestoreConfirmation() {
        backupLocalState.update { it.copy(pendingRestoreBackup = null) }
    }

    fun clearActionMessage() {
        backupLocalState.update { it.copy(actionMessage = null) }
        updateLocalState.update { it.copy(actionMessage = null) }
    }

    fun checkForUpdates() {
        if (updateLocalState.value.isChecking) return
        if (!networkMonitor.isOnline.value) {
            updateLocalState.update {
                it.copy(actionMessage = "No internet connection. Check Wi-Fi or mobile data and retry.")
            }
            return
        }
        updateLocalState.update { it.copy(isChecking = true, actionMessage = null) }
        viewModelScope.launch {
            when (val result = appUpdateRepository.checkForUpdate(force = true)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateLocalState.update { it.copy(isChecking = false) }
                    appUpdateRepository.presentUpdate(result.update)
                }
                UpdateCheckResult.UpToDate,
                UpdateCheckResult.SkippedOrThrottled -> {
                    updateLocalState.update {
                        it.copy(
                            isChecking = false,
                            actionMessage = "You are on the latest version (v${BuildConfig.VERSION_NAME})."
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    updateLocalState.update {
                        it.copy(isChecking = false, actionMessage = result.message)
                    }
                }
            }
        }
    }

    private fun sanitizeBackupError(err: Throwable, fallback: String): String {
        val msg = err.message?.trim().orEmpty()
        return if (msg.isNotEmpty() && msg.length <= 120 && !msg.contains("Exception")) {
            msg
        } else {
            fallback
        }
    }

    fun logout(context: Context, onLoggedOut: () -> Unit) {
        if (isLoggingOut.value) return
        isLoggingOut.value = true
        viewModelScope.launch {
            try {
                ChatConnectionService.stop(context)
                chatRepository.logout()
                onLoggedOut()
            } catch (_: Throwable) {
                actionError.value = "Sign out failed. Please try again."
            } finally {
                isLoggingOut.value = false
            }
        }
    }
}
