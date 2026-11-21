package com.osuchat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.osuchat.app.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class DirectReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var notificationManager: ChatNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DIRECT_REPLY) return
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = results.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (replyText.isNullOrEmpty()) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val sendResult = withTimeoutOrNull(8_000L) {
                    chatRepository.sendMessage(channelId, replyText)
                }
                if (sendResult?.isSuccess == true) {
                    chatRepository.markChannelAsRead(channelId)
                    notificationManager.clearChannelNotifications(channelId)
                } else {
                    notificationManager.markReplyFailed(
                        channelId = channelId,
                        notificationId = notificationId,
                        errorMessage = sendResult?.exceptionOrNull()?.message
                    )
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "com.osuchat.app.ACTION_DIRECT_REPLY"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
