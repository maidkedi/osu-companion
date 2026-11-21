package com.osuchat.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osuchat.app.data.local.TrackedFriendDao
import com.osuchat.app.data.model.TrackedPlayerAlertConfig
import com.osuchat.app.data.network.NetworkConnectivityMonitor
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.repository.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TrackedPlayerAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun prefs(): UserPreferencesDataStore
        fun trackedFriendDao(): TrackedFriendDao
        fun networkMonitor(): NetworkConnectivityMonitor
        fun chatRepository(): ChatRepository
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (isStopped) return@withContext Result.success()
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            )
            val settings = entryPoint.prefs().settingsFlow.first()
            if (!settings.trackedAlertsEnabled || settings.authMode != AuthMode.OAUTH_V2) {
                if (!settings.trackedAlertsEnabled) {
                    cancelScheduledWork(applicationContext)
                }
                return@withContext Result.success()
            }

            if (!entryPoint.networkMonitor().isOnline.value) {
                return@withContext Result.success()
            }

            val trackedFriends = entryPoint.trackedFriendDao()
                .getAllTrackedFriends()
                .filter { it.lastCheckedAt > 0L || it.topScoreIdsCsv.isNotEmpty() }
            if (trackedFriends.isEmpty()) {
                cancelScheduledWork(applicationContext)
                return@withContext Result.success()
            }

            val configsMap = entryPoint.prefs().getTrackedAlertConfigs()
            val hasAnyEnabledRule = trackedFriends.any { friend ->
                val cfg = configsMap.entries.firstOrNull { (key, _) ->
                    key.startsWith("${friend.userId}:")
                }?.value
                cfg == null || cfg.hasAnyTrackingEnabled()
            }
            if (!hasAnyEnabledRule) {
                return@withContext Result.success()
            }

            entryPoint.chatRepository().checkTrackedFriendsTopPlays(forceNotifyLatest = false)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            if (!isStopped && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME = "tracked_player_alerts_periodic"
        private const val MAX_RETRY_ATTEMPTS = 2

        fun syncSchedule(
            context: Context,
            enabled: Boolean,
            intervalMinutes: Int,
            hasTrackedPlayers: Boolean
        ) {
            val appContext = context.applicationContext
            if (!enabled || !hasTrackedPlayers) {
                cancelScheduledWork(appContext)
                return
            }
            val safeInterval = TrackedPlayerAlertConfig.clampIntervalMinutes(intervalMinutes).toLong()
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = PeriodicWorkRequestBuilder<TrackedPlayerAlertWorker>(
                    safeInterval,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun cancelScheduledWork(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
