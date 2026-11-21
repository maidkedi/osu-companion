package com.osuchat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.osuchat.app.MainActivity
import com.osuchat.app.R
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.domain.model.ChannelNotificationMode
import com.osuchat.app.domain.model.ChannelType
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.model.OsuScoreItem
import com.osuchat.app.util.BeatmapUrlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val recentMessagesByChannel = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            context.getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.service_notification_channel_desc)
            setShowBadge(false)
        }

        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            context.getString(R.string.message_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.message_notification_channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val ppTrackerChannel = NotificationChannel(
            PP_TRACKER_CHANNEL_ID,
            "Friend Top PP Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when tracked friends set new Top 5 PP plays"
            enableVibration(true)
            setShowBadge(true)
        }

        val friendOnlineChannel = NotificationChannel(
            FRIEND_ONLINE_CHANNEL_ID,
            "Friend Online Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when selected friends come online on osu!"
            setShowBadge(false)
        }

        val mpTimerChannel = NotificationChannel(
            MP_TIMER_CHANNEL_ID,
            "Multiplayer Timers",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Countdown warnings and finished alerts for #mp_ lobbies"
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(messageChannel)
        notificationManager.createNotificationChannel(ppTrackerChannel)
        notificationManager.createNotificationChannel(friendOnlineChannel)
        notificationManager.createNotificationChannel(mpTimerChannel)
    }

    fun buildServiceNotification(status: ConnectionStatus, username: String?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_DISCONNECT,
            Intent(context, ChatConnectionService::class.java).apply {
                action = ChatConnectionService.ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val userTag = username?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val title = when (status) {
            ConnectionStatus.ONLINE -> "osu! Companion connected$userTag"
            ConnectionStatus.CONNECTING -> "Connecting to osu! Companion$userTag…"
            ConnectionStatus.RECONNECTING -> "Reconnecting to osu! Companion$userTag…"
            ConnectionStatus.ERROR -> "osu! Companion connection issue"
            ConnectionStatus.OFFLINE -> "osu! Companion disconnected"
        }

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Background message sync active")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                "Disconnect",
                disconnectIntent
            )
            .build()
    }

    fun updateServiceNotification(status: ConnectionStatus, username: String?) {
        notificationManager.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(status, username))
    }

    suspend fun showIncomingMessageNotification(
        channel: ChatChannel,
        message: ChatMessage,
        selfUsername: String,
        highlightKeywords: List<String> = emptyList()
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (message.isOutgoing) return

        val mutedSet = userPreferencesDataStore.mutedChannels.first()
        if (isChannelMuted(channel, mutedSet)) return

        when (channel.notificationMode) {
            ChannelNotificationMode.MUTED -> return
            ChannelNotificationMode.MENTIONS_ONLY -> {
                if (!isMentionOrKeywordMatch(message, selfUsername, highlightKeywords)) {
                    return
                }
            }
            ChannelNotificationMode.ALL -> Unit
        }

        val history = recentMessagesByChannel.getOrPut(channel.id) { mutableListOf() }
        synchronized(history) {
            history.add(message)
            if (history.size > 6) {
                history.removeAt(0)
            }
        }

        val avatarBitmap = loadAvatarBitmap(
            message.senderAvatarUrl ?: channel.avatarUrl
        )
        val senderIcon = avatarBitmap?.let { IconCompat.createWithBitmap(it) }

        val selfPerson = Person.Builder()
            .setName(selfUsername.ifBlank { "Me" })
            .build()

        val senderLabel = message.senderDisplayName
        val senderPerson = Person.Builder()
            .setName(senderLabel)
            .apply { if (senderIcon != null) setIcon(senderIcon) }
            .build()

        val style = NotificationCompat.MessagingStyle(selfPerson)
            .setConversationTitle(
                if (channel.type == ChannelType.PUBLIC) channel.name else channel.displayTitle
            )
            .setGroupConversation(channel.type == ChannelType.PUBLIC)

        val snapshot = synchronized(history) { history.toList() }
        for (item in snapshot) {
            val itemPerson = if (item.isOutgoing) {
                selfPerson
            } else {
                val useSenderIcon = senderIcon != null &&
                    item.senderName.equals(message.senderName, ignoreCase = true)
                Person.Builder()
                    .setName(item.senderDisplayName)
                    .apply { if (useSenderIcon) setIcon(senderIcon) }
                    .build()
            }
            val body = if (item.isAction) "* ${item.content}" else item.content
            style.addMessage(body, item.timestamp, itemPerson)
        }

        val notificationId = channelNotificationId(channel.id)

        val openChatIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, channel.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply to ${channel.displayTitle}")
            .build()

        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, DirectReplyReceiver::class.java).apply {
                action = DirectReplyReceiver.ACTION_DIRECT_REPLY
                putExtra(DirectReplyReceiver.EXTRA_CHANNEL_ID, channel.id)
                putExtra(DirectReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )

        val replyAction = NotificationCompat.Action.Builder(
            0,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val builder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_MESSAGES)
            .setContentIntent(openChatIntent)
            .addAction(replyAction)
            .addPerson(senderPerson)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        notificationManager.notify(notificationId, builder.build())

        // Group summary notification for Android notification shade grouping
        val summaryNotification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("osu! Companion")
            .setContentText("New messages")
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    fun markReplyFailed(channelId: String, notificationId: Int, errorMessage: String?) {
        val resolvedId = if (notificationId != -1) notificationId else channelNotificationId(channelId)
        if (!notificationManager.areNotificationsEnabled()) {
            notificationManager.cancel(resolvedId)
            return
        }

        val openChatIntent = PendingIntent.getActivity(
            context,
            resolvedId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, channelId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val failureNotification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reply failed to send")
            .setContentText(errorMessage?.takeIf { it.isNotBlank() } ?: "Tap to open chat and retry")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_MESSAGES)
            .setContentIntent(openChatIntent)
            .build()
        notificationManager.notify(resolvedId, failureNotification)
    }

    fun clearChannelNotifications(channelId: String) {
        recentMessagesByChannel.remove(channelId)
        val notificationId = channelNotificationId(channelId)
        notificationManager.cancel(notificationId)

        val hasRemainingGroupNotifications = notificationManager.activeNotifications.any { sbn ->
            sbn.id != SUMMARY_NOTIFICATION_ID &&
                sbn.id != SERVICE_NOTIFICATION_ID &&
                sbn.notification.group == GROUP_KEY_MESSAGES
        }
        if (!hasRemainingGroupNotifications) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
        }
    }

    fun clearAllChatNotifications() {
        recentMessagesByChannel.clear()
        notificationManager.activeNotifications
            .filter { it.id != SERVICE_NOTIFICATION_ID }
            .forEach { notificationManager.cancel(it.id) }
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
    }

    suspend fun showFriendNewTopPlayNotification(
        displayName: String,
        rankPosition: Int,
        score: OsuScoreItem,
        avatarUrl: String?
    ) {
        if (!notificationManager.areNotificationsEnabled()) return

        val title = "$displayName set a new #$rankPosition top PP play!"
        val modsPart = if (score.mods.isNotEmpty()) " +${score.mods.joinToString("")}" else ""
        val ppRounded = kotlin.math.round(score.pp ?: 0.0).toInt()
        val accPercent = if (score.accuracy <= 1.0) score.accuracy * 100.0 else score.accuracy
        val accFormatted = String.format(Locale.US, "%.2f", accPercent)
        val body = "${score.beatmapArtist} - ${score.beatmapTitle} [${score.beatmapVersion}]$modsPart • ${ppRounded}pp ($accFormatted%)"

        val avatarBitmap = loadAvatarBitmap(avatarUrl)
        val notifId = ppNotificationId(score.userId, score.id)
        val openAppIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, PP_TRACKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        notificationManager.notify(notifId, builder.build())
    }

    suspend fun showFriendOnlineNotification(
        username: String,
        userId: Long,
        avatarUrl: String?
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val cleanName = username.trim()
        if (cleanName.isEmpty()) return

        val dmChannelId = "dm:${BeatmapUrlParser.normalizeUsername(cleanName)}"
        val resolvedAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() }
            ?: userId.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }
        val avatarBitmap = loadAvatarBitmap(resolvedAvatarUrl)
        val notifId = friendOnlineNotificationId(userId, cleanName)
        val openDmIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, dmChannelId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, FRIEND_ONLINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$cleanName is now online")
            .setContentText("Tap to open direct message")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openDmIntent)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        notificationManager.notify(notifId, builder.build())
    }

    fun showMpTimerAlertNotification(
        channelId: String,
        roomName: String,
        alertText: String,
        isFinished: Boolean
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val cleanChannelId = channelId.trim().lowercase(Locale.ROOT)
        if (cleanChannelId.isEmpty()) return
        val notifId = mpTimerNotificationId(cleanChannelId)
        val openLobbyIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, cleanChannelId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (isFinished) "Timer Finished" else "Countdown Warning"
        val builder = NotificationCompat.Builder(context, MP_TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(alertText)
            .setSubText(roomName.ifBlank { cleanChannelId })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openLobbyIntent)

        notificationManager.notify(notifId, builder.build())
    }

    fun cancelMpTimerNotifications(channelId: String) {
        val cleanChannelId = channelId.trim().lowercase(Locale.ROOT)
        if (cleanChannelId.isEmpty()) return
        notificationManager.cancel(mpTimerNotificationId(cleanChannelId))
    }

    internal fun isChannelMuted(channel: ChatChannel, mutedChannels: Set<String>): Boolean {
        if (channel.notificationMode == ChannelNotificationMode.MUTED) return true
        if (mutedChannels.isEmpty()) return false
        val normName = channel.name.trim().lowercase(Locale.ROOT)
        val normId = channel.id.trim().lowercase(Locale.ROOT)
        if (normName in mutedChannels || normId in mutedChannels) return true
        if (channel.type == ChannelType.PUBLIC) {
            val bareName = normName.removePrefix("#")
            if (bareName.isNotEmpty() && (bareName in mutedChannels || "#$bareName" in mutedChannels)) {
                return true
            }
        }
        return false
    }

    private fun isMentionOrKeywordMatch(
        message: ChatMessage,
        selfUsername: String,
        highlightKeywords: List<String>
    ): Boolean {
        if (message.isHighlighted) return true
        val content = message.content
        if (content.isBlank()) return false

        val cleanSelf = selfUsername.trim()
        if (cleanSelf.isNotEmpty()) {
            if (content.contains(cleanSelf, ignoreCase = true)) return true
            val ircSelf = cleanSelf.replace(' ', '_')
            if (ircSelf != cleanSelf && content.contains(ircSelf, ignoreCase = true)) return true
        }
        return highlightKeywords.any { kw ->
            val trimmed = kw.trim()
            trimmed.isNotEmpty() && content.contains(trimmed, ignoreCase = true)
        }
    }

    private fun channelNotificationId(channelId: String): Int =
        (channelId.hashCode() and 0x3FFFFFFF) + 10_000

    private fun ppNotificationId(userId: Long, scoreId: Long): Int =
        -((("pp_${userId}_${scoreId}").hashCode() and 0x3FFFFFFF) + 10_000)

    private fun friendOnlineNotificationId(userId: Long, username: String): Int =
        -((("online_${userId}_${username.lowercase(Locale.ROOT)}").hashCode() and 0x3FFFFFFF) + 200_000)

    private fun mpTimerNotificationId(channelId: String): Int =
        -((("mp_timer_${channelId.lowercase(Locale.ROOT)}").hashCode() and 0x3FFFFFFF) + 300_000)

    private suspend fun loadAvatarBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) {
            return@withContext null
        }
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(MAX_AVATAR_PX, MAX_AVATAR_PX)
                .transformations(CircleCropTransformation())
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                val width = drawable.intrinsicWidth.coerceIn(1, MAX_AVATAR_PX)
                val height = drawable.intrinsicHeight.coerceIn(1, MAX_AVATAR_PX)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } else {
                null
            }
        }.getOrNull()
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "osu_chat_connection_channel"
        const val MESSAGE_CHANNEL_ID = "osu_chat_messages_channel"
        const val PP_TRACKER_CHANNEL_ID = "osu_chat_pp_tracker_channel"
        const val FRIEND_ONLINE_CHANNEL_ID = "osu_chat_friend_online_channel"
        const val MP_TIMER_CHANNEL_ID = "osu_chat_mp_timer_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val SUMMARY_NOTIFICATION_ID = 1002
        const val GROUP_KEY_MESSAGES = "com.osuchat.app.MESSAGES_GROUP"
        private const val REQUEST_CODE_OPEN_APP = 0
        private const val REQUEST_CODE_DISCONNECT = 1
        private const val MAX_AVATAR_PX = 128
    }
}
