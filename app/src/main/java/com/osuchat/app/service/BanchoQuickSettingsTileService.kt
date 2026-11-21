package com.osuchat.app.service

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.osuchat.app.MainActivity
import com.osuchat.app.R
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.repository.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BanchoQuickSettingsTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BanchoTileEntryPoint {
        fun chatRepository(): ChatRepository
        fun userPreferencesDataStore(): UserPreferencesDataStore
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    private fun entryPoint(): BanchoTileEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, BanchoTileEntryPoint::class.java)

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        val ep = entryPoint()
        val repo = ep.chatRepository()
        val prefs = ep.userPreferencesDataStore()
        listeningJob = serviceScope.launch {
            combine(
                repo.connectionStatus,
                prefs.settingsFlow
            ) { status, settings ->
                status to settings
            }
                .distinctUntilChanged()
                .collect { (status, settings) ->
                    updateTileState(status, settings)
                }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        listeningJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val ep = entryPoint()
        val repo = ep.chatRepository()
        val prefs = ep.userPreferencesDataStore()

        serviceScope.launch {
            val settings = prefs.settingsFlow.first()
            val status = repo.connectionStatus.value
            val isAuthenticated = settings.authMode == AuthMode.IRC ||
                (settings.authMode == AuthMode.OAUTH_V2 && settings.hasIrcPassword)

            if (!isAuthenticated) {
                launchMainActivityAndCollapse(autoConnect = false)
                return@launch
            }

            val isActiveOrConnecting = status == ConnectionStatus.ONLINE ||
                status == ConnectionStatus.CONNECTING ||
                status == ConnectionStatus.RECONNECTING

            if (isActiveOrConnecting) {
                applyImmediateTileState(Tile.STATE_INACTIVE, "Offline")
                prefs.setExplicitlyDisconnected(true)
                repo.disconnectExplicitly()
                ChatConnectionService.stop(applicationContext)
            } else {
                applyImmediateTileState(Tile.STATE_ACTIVE, "Connecting…")
                prefs.setExplicitlyDisconnected(false)
                repo.setExplicitlyDisconnected(false)

                val serviceIntent = Intent(applicationContext, ChatConnectionService::class.java).apply {
                    action = ChatConnectionService.ACTION_START
                }
                val fgsStarted = try {
                    ContextCompat.startForegroundService(applicationContext, serviceIntent)
                    true
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        e is ForegroundServiceStartNotAllowedException
                    ) {
                        false
                    } else if (e is SecurityException || e is IllegalStateException) {
                        false
                    } else {
                        false
                    }
                }

                if (fgsStarted) {
                    repo.reconnectIfAuthenticated()
                } else {
                    launchMainActivityAndCollapse(autoConnect = true)
                }
            }
        }
    }

    private fun updateTileState(status: ConnectionStatus, settings: AppSettings) {
        val isAuthenticated = settings.authMode == AuthMode.IRC ||
            (settings.authMode == AuthMode.OAUTH_V2 && settings.hasIrcPassword)
        val (state, subtitleText) = when {
            !isAuthenticated -> Tile.STATE_UNAVAILABLE to "Sign in required"
            status == ConnectionStatus.ONLINE -> Tile.STATE_ACTIVE to "Connected"
            status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECONNECTING ->
                Tile.STATE_ACTIVE to "Connecting…"
            else -> Tile.STATE_INACTIVE to "Offline"
        }
        applyImmediateTileState(state, subtitleText)
    }

    private fun applyImmediateTileState(state: Int, subtitleText: String) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_bancho_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_bancho)
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchMainActivityAndCollapse(autoConnect: Boolean) {
        val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (autoConnect) {
                putExtra(MainActivity.EXTRA_AUTO_CONNECT, true)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                if (autoConnect) REQUEST_CODE_AUTO_CONNECT else REQUEST_CODE_SIGN_IN,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activityIntent)
        }
    }

    companion object {
        private const val REQUEST_CODE_AUTO_CONNECT = 4101
        private const val REQUEST_CODE_SIGN_IN = 4102
    }
}
