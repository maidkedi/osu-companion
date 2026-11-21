package com.osuchat.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.osuchat.app.data.irc.BanchoIrcClient
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatConnectionService : Service() {

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var notificationManager: ChatNotificationManager

    @Inject
    lateinit var banchoIrcClient: BanchoIrcClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationCollectorJob: Job? = null
    private var foregroundPromoted = false

    @Volatile
    private var isShuttingDown = false

    override fun onCreate() {
        super.onCreate()
        foregroundPromoted = promoteToForeground(ConnectionStatus.CONNECTING, null)
        if (!foregroundPromoted) {
            return
        }

        notificationCollectorJob = serviceScope.launch {
            combine(
                chatRepository.connectionStatus,
                chatRepository.settings
            ) { status, settings ->
                Triple(
                    status,
                    settings.username,
                    settings.isExplicitlyDisconnected || banchoIrcClient.isExplicitlyDisconnected()
                )
            }
                .distinctUntilChanged()
                .collect { (status, username, explicitlyDisconnected) ->
                    if (!isShuttingDown && !explicitlyDisconnected) {
                        notificationManager.updateServiceNotification(status, username)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            isShuttingDown = true
            notificationCollectorJob?.cancel()
            banchoIrcClient.disconnectExplicitly()
            serviceScope.launch {
                runCatching { chatRepository.disconnectExplicitly() }
                sendBroadcast(
                    Intent(ACTION_APP_SHUTDOWN).apply {
                        setPackage(packageName)
                    }
                )
                ServiceCompat.stopForeground(
                    this@ChatConnectionService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (!foregroundPromoted || banchoIrcClient.isExplicitlyDisconnected()) {
            isShuttingDown = true
            notificationCollectorJob?.cancel()
            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val settings = chatRepository.settings.first()
            if (settings.authMode != AuthMode.NONE &&
                !settings.isExplicitlyDisconnected &&
                !banchoIrcClient.isExplicitlyDisconnected()
            ) {
                chatRepository.reconnectIfAuthenticated()
            } else {
                isShuttingDown = true
                notificationCollectorJob?.cancel()
                ServiceCompat.stopForeground(
                    this@ChatConnectionService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(status: ConnectionStatus, username: String?): Boolean {
        val notification = notificationManager.buildServiceNotification(status, username)
        return runCatching {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                ChatNotificationManager.SERVICE_NOTIFICATION_ID,
                notification,
                fgsType
            )
            true
        }.getOrElse {
            stopSelf()
            false
        }
    }

    override fun onTimeout(startId: Int) {
        handleForegroundTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundTimeout()
    }

    private fun handleForegroundTimeout() {
        isShuttingDown = true
        banchoIrcClient.disconnectExplicitly()
        serviceScope.launch {
            runCatching { chatRepository.disconnect() }
            ServiceCompat.stopForeground(
                this@ChatConnectionService,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf()
        }
    }

    override fun onDestroy() {
        isShuttingDown = true
        notificationCollectorJob?.cancel()
        notificationCollectorJob = null
        serviceScope.cancel()
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.osuchat.app.ACTION_START_SYNC"
        const val ACTION_DISCONNECT = "com.osuchat.app.ACTION_DISCONNECT_SYNC"
        const val ACTION_APP_SHUTDOWN = "com.osuchat.app.ACTION_APP_SHUTDOWN"

        fun start(context: Context) {
            tryStart(context)
        }

        fun tryStart(context: Context): Boolean {
            val intent = Intent(context, ChatConnectionService::class.java).apply {
                action = ACTION_START
            }
            return try {
                ContextCompat.startForegroundService(context, intent)
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
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatConnectionService::class.java))
        }
    }
}
