package com.osuchat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.widget.BanchoStatsWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: UserPreferencesDataStore

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    BanchoStatsWidget.schedulePeriodicUpdates(appContext)
                    val settings = prefs.settingsFlow.first()
                    if (settings.autoConnectOnBoot && settings.authMode != AuthMode.NONE) {
                        val started = ChatConnectionService.tryStart(appContext)
                        if (!started) {
                            // Android 12+/14+/15 background FGS launch restriction fallback
                            enqueueBootSyncFallback(appContext)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun enqueueBootSyncFallback(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BootSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BOOT_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val BOOT_SYNC_WORK_NAME = "osu_chat_boot_sync_fallback"
    }
}

class BootSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootSyncEntryPoint {
        fun prefs(): UserPreferencesDataStore
        fun chatRepository(): ChatRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BootSyncEntryPoint::class.java
        )
        val settings = entryPoint.prefs().settingsFlow.first()
        if (!settings.autoConnectOnBoot || settings.authMode == AuthMode.NONE) {
            return Result.success()
        }

        withTimeoutOrNull(15_000L) {
            runCatching { entryPoint.chatRepository().reconnectIfAuthenticated() }
        }
        BanchoStatsWidget.enqueueImmediateRefresh(applicationContext)
        return Result.success()
    }
}
