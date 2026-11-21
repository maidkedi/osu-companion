package com.osuchat.app.domain.usecase

import com.osuchat.app.domain.model.BeatmapLinkRef
import com.osuchat.app.domain.model.BeatmapMetadata
import com.osuchat.app.domain.model.ChatChannel
import com.osuchat.app.domain.model.PlayerProfile
import com.osuchat.app.domain.repository.ChatRepository
import javax.inject.Inject

class SendChatCommandUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(channelId: String, input: String): Result<CommandOutcome> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.success(CommandOutcome.Ignored)

        return when {
            trimmed.startsWith("/join ", ignoreCase = true) -> {
                val target = trimmed.substringAfter(" ").trim()
                if (target.isEmpty()) {
                    Result.failure(IllegalArgumentException("Usage: /join #channel"))
                } else {
                    val channel = repository.joinPublicChannel(target).getOrElse { return Result.failure(it) }
                    Result.success(CommandOutcome.NavigateToChannel(channel.id))
                }
            }
            trimmed.equals("/part", ignoreCase = true) || trimmed.equals("/leave", ignoreCase = true) -> {
                repository.leaveChannel(channelId).getOrElse { return Result.failure(it) }
                Result.success(CommandOutcome.LeftChannel)
            }
            trimmed.equals("/clear", ignoreCase = true) -> {
                repository.clearChannelMessages(channelId)
                Result.success(CommandOutcome.Cleared)
            }
            trimmed.startsWith("/msg ", ignoreCase = true) || trimmed.startsWith("/dm ", ignoreCase = true) -> {
                val rest = trimmed.substringAfter(" ").trim()
                val parts = rest.split(" ", limit = 2)
                val targetUser = parts.getOrNull(0).orEmpty()
                if (targetUser.isBlank()) {
                    Result.failure(IllegalArgumentException("Usage: /dm <username> [message]"))
                } else {
                    val channel = repository.startDirectMessage(targetUser).getOrElse { return Result.failure(it) }
                    val msg = parts.getOrNull(1)?.trim()
                    if (!msg.isNullOrBlank()) {
                        repository.sendMessage(channel.id, msg)
                    }
                    Result.success(CommandOutcome.NavigateToChannel(channel.id))
                }
            }
            trimmed.startsWith("/stats", ignoreCase = true) -> {
                val arg = trimmed.substringAfter("/stats", "").trim()
                Result.success(CommandOutcome.OpenPlayerStats(arg.ifBlank { null }))
            }
            trimmed.equals("/rs", ignoreCase = true) || trimmed.startsWith("/rs ", ignoreCase = true) -> {
                val arg = trimmed.substringAfter("/rs", "").trim()
                repository.sendRecentScoreCommand(channelId, arg.ifBlank { null })
                    .map { CommandOutcome.MessageSent }
            }
            trimmed.equals("/c", ignoreCase = true) || trimmed.startsWith("/c ", ignoreCase = true) -> {
                val arg = trimmed.substringAfter("/c", "").trim()
                repository.sendCompareScoreCommand(channelId, arg.ifBlank { null })
                    .map { CommandOutcome.MessageSent }
            }
            trimmed.equals("/top", ignoreCase = true) || trimmed.startsWith("/top ", ignoreCase = true) -> {
                val arg = trimmed.substringAfter("/top", "").trim()
                repository.sendTopScoreCommand(channelId, arg.ifBlank { null })
                    .map { CommandOutcome.MessageSent }
            }
            else -> {
                // Handles /me, /np, and regular messages directly in ChatRepository
                repository.sendMessage(channelId, trimmed)
                    .map { CommandOutcome.MessageSent }
            }
        }
    }
}

sealed class CommandOutcome {
    data object Ignored : CommandOutcome()
    data object MessageSent : CommandOutcome()
    data object LeftChannel : CommandOutcome()
    data object Cleared : CommandOutcome()
    data class NavigateToChannel(val channelId: String) : CommandOutcome()
    data class OpenPlayerStats(val usernameOrNull: String?) : CommandOutcome()
}

class ResolveBeatmapUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(linkRef: BeatmapLinkRef): Result<BeatmapMetadata> {
        return repository.getBeatmapMetadata(linkRef)
    }
}

class FetchPlayerProfileUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(usernameOrId: String): Result<PlayerProfile> {
        return repository.getPlayerProfile(usernameOrId)
    }
}

class UpdateUserAliasUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(username: String, nickname: String?, userId: Long? = null) {
        repository.setUserAlias(username, nickname, userId)
    }
}
