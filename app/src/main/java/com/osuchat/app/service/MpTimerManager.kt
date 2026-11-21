package com.osuchat.app.service

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Immutable
data class ActiveMpTimer(
    val channelId: String,
    val roomName: String,
    val label: String,
    val totalSeconds: Int,
    val endTimeMillis: Long,
    val isMatchStart: Boolean,
    val remainingSeconds: Int = totalSeconds,
    val warned15s: Boolean = false,
    val finishedNotified: Boolean = false
)

@Singleton
class MpTimerManager @Inject constructor(
    private val notificationManager: ChatNotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private var tickerJob: Job? = null

    @Volatile
    private var activeChannelId: String? = null

    @Volatile
    private var isAppInForeground: Boolean = false

    private val _activeTimers = MutableStateFlow<Map<String, ActiveMpTimer>>(emptyMap())
    val activeTimers: StateFlow<Map<String, ActiveMpTimer>> = _activeTimers.asStateFlow()

    fun updateActiveChannel(channelId: String?) {
        val norm = channelId?.trim()?.lowercase(Locale.ROOT)
        activeChannelId = norm
        if (norm != null && norm.startsWith("#mp_")) {
            notificationManager.cancelMpTimerNotifications(norm)
        }
    }

    fun updateAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        val currentCh = activeChannelId
        if (inForeground && currentCh != null && currentCh.startsWith("#mp_")) {
            notificationManager.cancelMpTimerNotifications(currentCh)
        }
    }

    fun startOrSyncTimer(
        channelId: String,
        roomName: String,
        label: String,
        totalSeconds: Int,
        isMatchStart: Boolean
    ) {
        val normChannel = channelId.trim().lowercase(Locale.ROOT)
        if (!normChannel.startsWith("#mp_") || totalSeconds <= 0) return

        val now = System.currentTimeMillis()
        val endTime = now + (totalSeconds * 1000L)
        val cleanRoomName = roomName.trim().ifEmpty { normChannel }
        val cleanLabel = label.trim().ifEmpty { if (isMatchStart) "Match Starts" else "Countdown" }

        synchronized(stateLock) {
            val existing = _activeTimers.value[normChannel]
            val preservedTotal = if (existing != null && existing.isMatchStart == isMatchStart && existing.totalSeconds > totalSeconds) {
                existing.totalSeconds
            } else {
                totalSeconds
            }
            val alreadyWarned15 = existing != null &&
                existing.isMatchStart == isMatchStart &&
                existing.warned15s &&
                totalSeconds <= 15

            _activeTimers.update { current ->
                current + (
                    normChannel to ActiveMpTimer(
                        channelId = normChannel,
                        roomName = cleanRoomName,
                        label = cleanLabel,
                        totalSeconds = preservedTotal,
                        endTimeMillis = endTime,
                        isMatchStart = isMatchStart,
                        remainingSeconds = totalSeconds,
                        warned15s = alreadyWarned15,
                        finishedNotified = false
                    )
                )
            }
            ensureTickerRunningLocked()
        }
    }

    fun clearTimer(channelId: String) {
        val normChannel = channelId.trim().lowercase(Locale.ROOT)
        if (normChannel.isEmpty()) return
        synchronized(stateLock) {
            if (!_activeTimers.value.containsKey(normChannel)) return
            _activeTimers.update { it - normChannel }
            if (_activeTimers.value.isEmpty()) {
                tickerJob?.cancel()
                tickerJob = null
            }
        }
        notificationManager.cancelMpTimerNotifications(normChannel)
    }

    private fun isViewingChannelInForeground(channelId: String): Boolean {
        return isAppInForeground && activeChannelId.equals(channelId, ignoreCase = true)
    }

    private fun ensureTickerRunningLocked() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val shouldContinue = tickTimers()
                if (!shouldContinue) break
            }
        }
    }

    private fun tickTimers(): Boolean {
        val now = System.currentTimeMillis()
        val notificationsToFire = mutableListOf<Triple<ActiveMpTimer, String, Boolean>>()

        val hasRemaining = synchronized(stateLock) {
            val current = _activeTimers.value
            if (current.isEmpty()) {
                tickerJob = null
                return false
            }

            val nextMap = LinkedHashMap<String, ActiveMpTimer>(current.size)
            for ((chId, timer) in current) {
                val remainingMs = timer.endTimeMillis - now
                val remainingSec = if (remainingMs <= 0L) 0 else ceil(remainingMs / 1000.0).toInt()
                val viewingForeground = isViewingChannelInForeground(chId)

                if (remainingSec <= 0) {
                    if (!timer.finishedNotified && !viewingForeground) {
                        val displayName = timer.roomName.ifBlank { chId }
                        notificationsToFire.add(
                            Triple(
                                timer,
                                "Timer finished in $displayName",
                                true
                            )
                        )
                    }
                    // Expired timer is removed from activeTimers once finished.
                    continue
                }

                var warned15 = timer.warned15s
                if (!warned15 && timer.totalSeconds >= 30 && remainingSec <= 15) {
                    warned15 = true
                    if (!viewingForeground) {
                        val displayName = timer.roomName.ifBlank { chId }
                        notificationsToFire.add(
                            Triple(
                                timer,
                                "15s remaining in $displayName",
                                false
                            )
                        )
                    }
                }

                nextMap[chId] = timer.copy(
                    remainingSeconds = remainingSec,
                    warned15s = warned15
                )
            }

            _activeTimers.value = nextMap
            if (nextMap.isEmpty()) {
                tickerJob = null
                false
            } else {
                true
            }
        }

        for ((timer, alertText, isFinished) in notificationsToFire) {
            notificationManager.showMpTimerAlertNotification(
                channelId = timer.channelId,
                roomName = timer.roomName,
                alertText = alertText,
                isFinished = isFinished
            )
        }
        return hasRemaining
    }
}
