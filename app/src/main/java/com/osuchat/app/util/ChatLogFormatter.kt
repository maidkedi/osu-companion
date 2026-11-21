package com.osuchat.app.util

import com.osuchat.app.domain.model.ChatLogExportFormat
import com.osuchat.app.domain.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ChatLogFormatter {

    private val MP_CHANNEL_REGEX = Regex("^#mp_(\\d+)$", RegexOption.IGNORE_CASE)
    private val NON_FILENAME_CHARS_REGEX = Regex("[^A-Za-z0-9._-]+")

    fun extractMpHistoryUrl(channelName: String, channelId: String): String? {
        val match = MP_CHANNEL_REGEX.matchEntire(channelName.trim())
            ?: MP_CHANNEL_REGEX.matchEntire(channelId.trim())
            ?: return null
        val matchId = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return "https://osu.ppy.sh/mp/$matchId"
    }

    fun buildDefaultFilename(
        channelName: String,
        channelId: String,
        format: ChatLogExportFormat,
        timestampMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val raw = channelName.trim().ifBlank { channelId.trim() }
            .removePrefix("#")
        val sanitized = NON_FILENAME_CHARS_REGEX.replace(raw, "_")
            .trim('_')
            .ifBlank { "channel" }
            .take(48)
        val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US)
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(timestampMillis))
        return "osucompanion_${sanitized}_${fileStamp}.${format.extension}"
    }

    fun format(
        channelName: String,
        channelId: String,
        messages: List<ChatMessage>,
        format: ChatLogExportFormat,
        exportedAtMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
            .withZone(zoneId)
        val displayChannel = channelName.trim().ifBlank { channelId.trim() }
        val exportedAt = timeFormatter.format(Instant.ofEpochMilli(exportedAtMillis))
        val mpHistoryUrl = extractMpHistoryUrl(displayChannel, channelId)

        val sb = StringBuilder()
        when (format) {
            ChatLogExportFormat.PLAIN_TEXT -> {
                sb.appendLine("osu! Companion Chat Log")
                sb.appendLine("Channel: $displayChannel")
                sb.appendLine("Exported: $exportedAt")
                sb.appendLine("Messages: ${messages.size}")
                if (mpHistoryUrl != null) {
                    sb.appendLine("Match History: $mpHistoryUrl")
                }
                sb.appendLine("----------------------------------------")
                if (messages.isNotEmpty()) {
                    sb.appendLine()
                }
                for (msg in messages) {
                    val stamp = timeFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = msg.senderDisplayName.ifBlank { msg.senderName }.ifBlank { "Unknown" }
                    if (msg.isAction) {
                        sb.appendLine("[$stamp] * $sender ${msg.content}")
                    } else {
                        sb.appendLine("[$stamp] $sender: ${msg.content}")
                    }
                }
            }
            ChatLogExportFormat.MARKDOWN -> {
                sb.appendLine("# Chat Log: $displayChannel")
                sb.appendLine()
                sb.appendLine("- **Exported:** $exportedAt")
                sb.appendLine("- **Messages:** ${messages.size}")
                if (mpHistoryUrl != null) {
                    sb.appendLine("- **Match History:** [$mpHistoryUrl]($mpHistoryUrl)")
                }
                sb.appendLine()
                sb.appendLine("---")
                if (messages.isNotEmpty()) {
                    sb.appendLine()
                }
                for (msg in messages) {
                    val stamp = timeFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = msg.senderDisplayName.ifBlank { msg.senderName }.ifBlank { "Unknown" }
                    if (msg.isAction) {
                        sb.appendLine("- `[$stamp]` * $sender ${msg.content}")
                    } else {
                        sb.appendLine("- `[$stamp]` **$sender**: ${msg.content}")
                    }
                }
            }
        }
        return sb.toString()
    }
}
