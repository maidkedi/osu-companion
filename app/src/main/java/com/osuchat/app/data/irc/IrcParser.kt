package com.osuchat.app.data.irc

data class ParsedIrcMessage(
    val prefix: String?,
    val nick: String?,
    val command: String,
    val params: List<String>,
    val trailing: String?
)

sealed class IrcEvent {
    data class Connected(val serverHost: String) : IrcEvent()
    data class AuthFailed(val reason: String) : IrcEvent()
    data class Disconnected(val reason: String?, val willReconnect: Boolean) : IrcEvent()
    data class Ping(val token: String) : IrcEvent()
    data class PrivMsg(
        val sender: String,
        val target: String, // "#osu" or recipient nick (our nick if DM)
        val text: String,
        val isAction: Boolean
    ) : IrcEvent()
    data class Ctcp(
        val sender: String,
        val target: String,
        val command: String,
        val args: String?
    ) : IrcEvent()
    data class UserJoined(val nick: String, val channel: String) : IrcEvent()
    data class UserParted(val nick: String, val channel: String, val reason: String?) : IrcEvent()
    data class UserQuit(val nick: String, val reason: String?) : IrcEvent()
    data class NickChanged(val oldNick: String, val newNick: String) : IrcEvent()
    data class NoSuchNick(val target: String, val reason: String) : IrcEvent()
    data class NamesChunk(val channel: String, val nicks: List<String>) : IrcEvent()
    data class NamesEnd(val channel: String) : IrcEvent()
    data class ServerNotice(val text: String) : IrcEvent()
}

object IrcParser {

    fun parseLine(rawLine: String): ParsedIrcMessage? {
        val line = rawLine.trimEnd('\r', '\n')
        if (line.isBlank()) return null

        var index = 0
        var prefix: String? = null

        // Optional IRCv3 message tags (@key=value;...)
        if (line.startsWith("@")) {
            val tagEnd = line.indexOf(' ')
            if (tagEnd == -1) return null
            index = tagEnd + 1
            while (index < line.length && line[index] == ' ') index++
            if (index >= line.length) return null
        }

        if (line.startsWith(":", startIndex = index)) {
            val spaceIdx = line.indexOf(' ', startIndex = index)
            if (spaceIdx == -1) return null
            prefix = line.substring(index + 1, spaceIdx)
            index = spaceIdx + 1
        }

        while (index < line.length && line[index] == ' ') index++
        if (index >= line.length) return null

        val trailingStart = line.indexOf(" :", startIndex = index)
        val mainPart: String
        val trailing: String?
        if (trailingStart != -1) {
            mainPart = line.substring(index, trailingStart)
            trailing = line.substring(trailingStart + 2)
        } else {
            mainPart = line.substring(index)
            trailing = null
        }

        val tokens = mainPart.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val command = tokens[0].uppercase()
        val params = if (tokens.size > 1) tokens.drop(1) else emptyList()
        val nick = prefix?.substringBefore('!')?.substringBefore('@')?.takeIf { it.isNotBlank() }

        return ParsedIrcMessage(
            prefix = prefix,
            nick = nick,
            command = command,
            params = params,
            trailing = trailing
        )
    }

    fun toIrcEvent(rawLine: String): IrcEvent? {
        val msg = parseLine(rawLine) ?: return null
        return when (msg.command) {
            "PING" -> {
                val token = msg.trailing ?: msg.params.firstOrNull().orEmpty()
                IrcEvent.Ping(token)
            }
            "001" -> {
                IrcEvent.Connected(msg.prefix ?: "irc.ppy.sh")
            }
            "464", "465" -> {
                IrcEvent.AuthFailed(msg.trailing ?: "Invalid Bancho IRC credentials")
            }
            "401" -> {
                // :cho.ppy.sh 401 mynick target_nick :No such nick/channel
                val target = msg.params.getOrNull(1) ?: msg.params.firstOrNull() ?: return null
                val reason = msg.trailing?.takeIf { it.isNotBlank() } ?: "No such nick/channel"
                IrcEvent.NoSuchNick(target = target, reason = reason)
            }
            "NICK" -> {
                val oldNick = msg.nick ?: return null
                val newNick = msg.trailing?.takeIf { it.isNotBlank() }
                    ?: msg.params.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: return null
                IrcEvent.NickChanged(oldNick = oldNick, newNick = newNick)
            }
            "PRIVMSG" -> {
                val sender = msg.nick ?: return null
                val target = msg.params.firstOrNull() ?: return null
                val rawText = msg.trailing ?: msg.params.drop(1).joinToString(" ")
                if (rawText.startsWith("\u0001")) {
                    val inner = rawText.removePrefix("\u0001").removeSuffix("\u0001")
                    if (inner.equals("ACTION", ignoreCase = true) || inner.startsWith("ACTION ", ignoreCase = true)) {
                        val actionText = if (inner.length > 6) inner.substring(7) else ""
                        return IrcEvent.PrivMsg(
                            sender = sender,
                            target = target,
                            text = actionText,
                            isAction = true
                        )
                    }
                    val spaceIdx = inner.indexOf(' ')
                    val ctcpCmd = (if (spaceIdx == -1) inner else inner.substring(0, spaceIdx)).trim().uppercase()
                    val ctcpArgs = if (spaceIdx == -1) null else inner.substring(spaceIdx + 1).takeIf { it.isNotBlank() }
                    if (ctcpCmd.isNotEmpty()) {
                        return IrcEvent.Ctcp(
                            sender = sender,
                            target = target,
                            command = ctcpCmd,
                            args = ctcpArgs
                        )
                    }
                }
                IrcEvent.PrivMsg(
                    sender = sender,
                    target = target,
                    text = rawText,
                    isAction = false
                )
            }
            "JOIN" -> {
                val nick = msg.nick ?: return null
                val channel = msg.trailing?.takeIf { it.isNotBlank() }
                    ?: msg.params.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: return null
                IrcEvent.UserJoined(nick = nick, channel = channel)
            }
            "PART" -> {
                val nick = msg.nick ?: return null
                val channel = msg.params.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: msg.trailing?.takeIf { it.startsWith("#") || it.startsWith("&") }
                    ?: return null
                val reason = if (msg.params.isNotEmpty()) msg.trailing else null
                IrcEvent.UserParted(nick = nick, channel = channel, reason = reason)
            }
            "QUIT" -> {
                val nick = msg.nick ?: return null
                IrcEvent.UserQuit(nick = nick, reason = msg.trailing)
            }
            "353" -> {
                // :server 353 mynick = #channel :nick1 @nick2 +nick3
                val channel = msg.params.lastOrNull { it.startsWith("#") || it.startsWith("&") } ?: return null
                val nicks = msg.trailing.orEmpty()
                    .split(' ')
                    .map { it.trimStart('@', '+', '%', '~', '&') }
                    .filter { it.isNotBlank() }
                IrcEvent.NamesChunk(channel = channel, nicks = nicks)
            }
            "366" -> {
                val channel = msg.params.lastOrNull { it.startsWith("#") || it.startsWith("&") } ?: return null
                IrcEvent.NamesEnd(channel = channel)
            }
            "NOTICE", "ERROR" -> {
                val fallbackParams = if (msg.command == "ERROR") msg.params else msg.params.drop(1)
                val text = msg.trailing ?: fallbackParams.joinToString(" ").takeIf { it.isNotBlank() } ?: return null
                if (text.contains("Bad authentication", ignoreCase = true) ||
                    text.contains("authentication failed", ignoreCase = true) ||
                    text.contains("ERR_PASSWDMISMATCH", ignoreCase = true)
                ) {
                    IrcEvent.AuthFailed(text)
                } else {
                    IrcEvent.ServerNotice(text)
                }
            }
            else -> null
        }
    }
}
