package com.osuchat.app.util

import com.osuchat.app.domain.model.ChatMessage
import com.osuchat.app.domain.model.Mappool
import com.osuchat.app.domain.model.MatchSeriesState
import com.osuchat.app.domain.model.MpFinishedRoundEvent
import com.osuchat.app.domain.model.MpLobbyState
import com.osuchat.app.domain.model.MpPoolSlotState
import com.osuchat.app.domain.model.MpSlot
import com.osuchat.app.domain.model.MpSlotStatus
import com.osuchat.app.domain.model.MpTeam
import java.text.NumberFormat
import java.util.Locale

object MpLobbyParser {

    private val MATCH_ID_REGEX = Regex(
        """(?:#mp_|(?:https?://)?(?:osu|old)\.ppy\.sh/(?:mp|community/matches)/|^)(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val CREATED_MATCH_REGEX = Regex(
        """Created the tournament match https?://(?:osu|old)\.ppy\.sh/(?:mp|community/matches)/(\d+)\s*(.*)""",
        RegexOption.IGNORE_CASE
    )

    private val ROOM_NAME_HISTORY_REGEX = Regex(
        """^Room name:\s*(.+),\s*History:\s*(https?://\S+)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val BEATMAP_SETTING_REGEX = Regex(
        """^(?:Beatmap:|Changed beatmap to:?)\s*(https?://(?:osu|old)\.ppy\.sh/(?:(?:b|beatmaps)/(\d+)|beatmapsets/\d+#[a-z]+/(\d+)))(?:\s+(.*))?$""",
        RegexOption.IGNORE_CASE
    )

    private val TEAM_WIN_REGEX = Regex(
        """Team mode:\s*([^,]+),\s*Win condition:\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val CHANGED_SETTINGS_REGEX = Regex(
        """^Changed match settings to\s+(?:(\d+)\s+slots,\s*)?([^,]+),\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val ACTIVE_MODS_REGEX = Regex(
        """Active mods:\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val ENABLED_MODS_REGEX = Regex(
        """^Enabled\s+(.+?)(?:,\s*(enabled|disabled)\s+FreeMod)?\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val SLOT_LINE_REGEX = Regex(
        """^Slot\s+(\d+)\s+(Ready|Not Ready|No Map)\s+https?://(?:osu|old)\.ppy\.sh/(?:u|users)/(\d+)\s+(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_BRACKET_REGEX = Regex("""^(.*?)\s+\[([^\]\[]+)\]$""")

    private val KNOWN_SLOT_MOD_TOKENS = setOf(
        "none", "nomod", "freemod", "nf", "nofail", "ez", "easy", "td", "touchdevice",
        "hd", "hidden", "hr", "hardrock", "sd", "suddendeath", "dt", "doubletime",
        "rx", "relax", "ht", "halftime", "nc", "nightcore", "fl", "flashlight",
        "at", "autoplay", "so", "spunout", "ap", "autopilot", "pf", "perfect",
        "fi", "fadein", "rd", "random", "cn", "cinema", "tp", "target", "v2", "scorev2",
        "cl", "classic", "mr", "mirror", "coop", "1k", "2k", "3k", "4k", "5k", "6k", "7k", "8k", "9k", "10k",
        "key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8", "key9", "key10",
        "keycoop"
    )

    private val JOINED_SLOT_REGEX = Regex(
        """^(.+?)\s+joined in slot\s+(\d+)(?:\s+for team\s+(blue|red))?\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val MOVED_SLOT_REGEX = Regex(
        """^(.+?)\s+moved to slot\s+(\d+)\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val CHANGED_TEAM_REGEX = Regex(
        """^(.+?)\s+changed to\s+(Blue|Red)\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val LEFT_GAME_REGEX = Regex(
        """^(.+?)\s+left the game\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val HOST_CHANGED_REGEX = Regex(
        """^(?:Changed match host to\s+(.+)|(.+?)\s+became the host\.?)$""",
        RegexOption.IGNORE_CASE
    )

    private val ROOM_SIZE_REGEX = Regex(
        """Changed match size to\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val PLAYER_FINISHED_REGEX = Regex(
        """^(.+?)\s+finished playing \(Score:\s*([\d,]+),\s*(PASSED|FAILED)\)\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val TOURNEY_TEAMS_EXACT_REGEX = Regex(
        """^(?:[^()]+:\s*)?\(\s*(.+?)\s*\)\s+vs\.?\s+\(\s*(.+?)\s*\)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val TOURNEY_TEAMS_FALLBACK_REGEX = Regex(
        """\(\s*([^()]+?)\s*\)\s+vs\.?\s+\(\s*([^()]+?)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val IRC_CONTROL_WHITESPACE_REGEX = Regex("""[\r\n\u0000\t]+""")

    private val TIMER_ENDS_REGEX = Regex(
        """^(Countdown ends|Match starts) in (?:(\d+)\s+minutes?(?:\s+and)?\s*)?(?:(\d+)\s+seconds?)?\.?$""",
        RegexOption.IGNORE_CASE
    )

    private val OUTGOING_MP_TIMER_REGEX = Regex(
        """^!mp\s+(timer|start)(?:\s+(\d+))?\s*$""",
        RegexOption.IGNORE_CASE
    )

    sealed interface MpTimerDirective {
        data class Start(
            val totalSeconds: Int,
            val label: String,
            val isMatchStart: Boolean
        ) : MpTimerDirective

        data object Clear : MpTimerDirective
    }

    fun parseBanchoBotTimerDirective(line: String): MpTimerDirective? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("Countdown aborted", ignoreCase = true) ||
            trimmed.startsWith("Countdown aborted", ignoreCase = true) ||
            trimmed.equals("Countdown finished", ignoreCase = true) ||
            trimmed.startsWith("Countdown finished", ignoreCase = true) ||
            trimmed.startsWith("Aborted the match", ignoreCase = true) ||
            trimmed.startsWith("The match has started", ignoreCase = true) ||
            trimmed.startsWith("Closed the match", ignoreCase = true)
        ) {
            return MpTimerDirective.Clear
        }
        val match = TIMER_ENDS_REGEX.find(trimmed) ?: return null
        val kind = match.groupValues[1]
        val mins = match.groupValues[2].toIntOrNull() ?: 0
        val secs = match.groupValues[3].toIntOrNull() ?: 0
        val total = (mins * 60) + secs
        if (total <= 0) return MpTimerDirective.Clear
        val isMatchStart = kind.equals("Match starts", ignoreCase = true)
        return MpTimerDirective.Start(
            totalSeconds = total,
            label = if (isMatchStart) "Match Starts" else "Countdown",
            isMatchStart = isMatchStart
        )
    }

    fun parseOutgoingMpTimerCommand(rawCommand: String): MpTimerDirective? {
        val trimmed = rawCommand.trim()
        if (trimmed.equals("!mp abort", ignoreCase = true) ||
            trimmed.equals("!mp aborttimer", ignoreCase = true) ||
            trimmed.equals("!mp close", ignoreCase = true)
        ) {
            return MpTimerDirective.Clear
        }
        val match = OUTGOING_MP_TIMER_REGEX.find(trimmed) ?: return null
        val sub = match.groupValues[1]
        val secs = match.groupValues[2].toIntOrNull()
        if (sub.equals("timer", ignoreCase = true)) {
            val total = secs?.coerceIn(1, 3600) ?: return null
            return MpTimerDirective.Start(
                totalSeconds = total,
                label = "Countdown",
                isMatchStart = false
            )
        }
        if (sub.equals("start", ignoreCase = true)) {
            if (secs == null || secs <= 0) return MpTimerDirective.Clear
            return MpTimerDirective.Start(
                totalSeconds = secs.coerceIn(1, 300),
                label = "Match Starts",
                isMatchStart = true
            )
        }
        return null
    }
    private val MULTI_SPACE_REGEX = Regex(""" {2,}""")

    fun sanitizeIrcArg(raw: String): String {
        return raw
            .replace(IRC_CONTROL_WHITESPACE_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    fun sanitizeRoomName(roomName: String): String = sanitizeIrcArg(roomName)

    /**
     * Formats a player target for BanchoBot `!mp` commands.
     * Prefers `#<userId>` when known (immune to spaces, brackets, and special characters),
     * otherwise converts spaces to underscores as required by Bancho IRC/BanchoBot argument parsing.
     */
    fun formatUserTarget(usernameOrId: String, userId: Long? = null): String? {
        if (userId != null && userId > 0L) {
            return "#$userId"
        }
        val clean = sanitizeIrcArg(usernameOrId)
        if (clean.isEmpty()) return null
        if (clean.startsWith("#") && clean.length > 1 && clean.drop(1).all { it.isDigit() }) {
            return clean
        }
        return clean.replace(' ', '_')
    }

    fun buildMakeCommand(roomName: String): String? {
        val clean = sanitizeRoomName(roomName)
        return if (clean.isEmpty()) null else "!mp make $clean"
    }

    fun buildMapCommand(beatmapId: Long, playMode: Int? = null): String? {
        if (beatmapId <= 0L) return null
        return if (playMode != null) {
            "!mp map $beatmapId ${playMode.coerceIn(0, 3)}"
        } else {
            "!mp map $beatmapId"
        }
    }

    fun buildModsCommand(mods: Collection<String>): String {
        val cleaned = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (mod in mods) {
            val token = sanitizeIrcArg(mod).replace(" ", "")
            if (token.isEmpty() || token.equals("None", ignoreCase = true)) continue
            if (seen.add(token.lowercase())) {
                cleaned.add(token)
            }
        }
        return if (cleaned.isEmpty()) "!mp mods None" else "!mp mods ${cleaned.joinToString(" ")}"
    }

    fun buildStartCommand(delaySeconds: Int? = null): String {
        return if (delaySeconds != null && delaySeconds > 0) {
            "!mp start ${delaySeconds.coerceIn(1, 300)}"
        } else {
            "!mp start"
        }
    }

    fun buildAbortCommand(): String = "!mp abort"
    fun buildCloseCommand(): String = "!mp close"
    fun buildSettingsCommand(): String = "!mp settings"
    fun buildLockCommand(lock: Boolean): String = if (lock) "!mp lock" else "!mp unlock"
    fun buildSizeCommand(size: Int): String = "!mp size ${size.coerceIn(1, 16)}"

    fun buildSetCommand(teamMode: Int, scoreMode: Int, size: Int? = null): String {
        val base = "!mp set ${teamMode.coerceIn(0, 3)} ${scoreMode.coerceIn(0, 3)}"
        return if (size != null) "$base ${size.coerceIn(1, 16)}" else base
    }

    fun buildPasswordCommand(password: String): String {
        val clean = sanitizeIrcArg(password)
        return if (clean.isEmpty()) "!mp password" else "!mp password $clean"
    }

    fun buildInviteCommand(usernameOrId: String, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp invite $target"
    }

    fun buildMoveCommand(usernameOrId: String, slotNumber: Int, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp move $target ${slotNumber.coerceIn(1, 16)}"
    }

    fun buildTeamCommand(usernameOrId: String, team: String, userId: Long? = null): String? {
        val normalizedTeam = when (team.trim().lowercase()) {
            "red" -> "red"
            "blue" -> "blue"
            else -> return null
        }
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp team $target $normalizedTeam"
    }

    fun buildHostCommand(usernameOrId: String, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp host $target"
    }

    fun buildKickCommand(usernameOrId: String, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp kick $target"
    }

    fun buildAddRefCommand(usernameOrId: String, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp addref $target"
    }

    fun buildRemoveRefCommand(usernameOrId: String, userId: Long? = null): String? {
        val target = formatUserTarget(usernameOrId, userId) ?: return null
        return "!mp removeref $target"
    }

    private fun isValidSlotBracketMeta(inner: String): Boolean {
        val parts = inner.split("/").map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() }) return false
        return parts.all { part ->
            part.equals("Host", ignoreCase = true) ||
                part.equals("Team Blue", ignoreCase = true) ||
                part.equals("Team Red", ignoreCase = true) ||
                part.split(",").map { it.trim() }.let { tokens ->
                    tokens.isNotEmpty() && tokens.all { token ->
                        token.isNotEmpty() && token.lowercase() in KNOWN_SLOT_MOD_TOKENS
                    }
                }
        }
    }

    fun extractMatchId(inputOrChannel: String): Long? {
        val trimmed = inputOrChannel.trim()
        trimmed.toLongOrNull()?.let { return it }
        return MATCH_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun parseCreatedMatchResponse(text: String): Pair<Long, String>? {
        val match = CREATED_MATCH_REGEX.find(text.trim()) ?: return null
        val id = match.groupValues[1].toLongOrNull() ?: return null
        val name = match.groupValues.getOrNull(2)?.trim().orEmpty()
        return id to name
    }

    fun extractTournamentTeams(roomName: String): Pair<String, String>? {
        val trimmed = roomName.trim()
        if (trimmed.isEmpty()) return null
        val match = TOURNEY_TEAMS_EXACT_REGEX.matchEntire(trimmed)
            ?: TOURNEY_TEAMS_FALLBACK_REGEX.find(trimmed)
            ?: return null
        val red = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val blue = match.groupValues.getOrNull(2)?.trim().orEmpty()
        return if (red.isNotEmpty() && blue.isNotEmpty()) red to blue else null
    }

    fun resolveRefereeMacro(
        template: String,
        series: MatchSeriesState,
        lobby: MpLobbyState?
    ): String {
        val scoreStr = "${series.redScore} - ${series.blueScore}"
        val mpUrl = lobby?.matchId?.let { "https://osu.ppy.sh/mp/$it" }
            ?: lobby?.historyUrl?.takeIf { it.isNotBlank() }
            ?: lobby?.channelId?.takeIf { it.isNotBlank() }
            ?: series.channelId
        return template
            .replace("{red}", series.redTeamName, ignoreCase = true)
            .replace("{blue}", series.blueTeamName, ignoreCase = true)
            .replace("{score}", scoreStr, ignoreCase = true)
            .replace("{mp}", mpUrl, ignoreCase = true)
    }

    fun formatDiscordMatchSummary(
        lobby: MpLobbyState,
        series: MatchSeriesState,
        activePool: Mappool?
    ): String {
        val fmt = NumberFormat.getIntegerInstance(Locale.US)
        val mpUrl = lobby.matchId?.let { "https://osu.ppy.sh/mp/$it" }
            ?: lobby.historyUrl?.takeIf { it.isNotBlank() }
            ?: lobby.channelId

        val slotById = activePool?.slots.orEmpty().associateBy { it.id }
        val redBans = series.slotStates.entries
            .filter { it.value == MpPoolSlotState.BANNED_RED }
            .mapNotNull { slotById[it.key]?.slotTag }
        val blueBans = series.slotStates.entries
            .filter { it.value == MpPoolSlotState.BANNED_BLUE }
            .mapNotNull { slotById[it.key]?.slotTag }

        return buildString {
            appendLine("**${lobby.roomName}** (Bo${series.bestOf})")
            appendLine("**${series.redTeamName}** ${series.redScore} - ${series.blueScore} **${series.blueTeamName}**")
            appendLine("MP: $mpUrl")

            if (redBans.isNotEmpty() || blueBans.isNotEmpty()) {
                appendLine()
                if (redBans.isNotEmpty()) {
                    appendLine("${series.redTeamName} Bans: ${redBans.joinToString(", ")}")
                }
                if (blueBans.isNotEmpty()) {
                    appendLine("${series.blueTeamName} Bans: ${blueBans.joinToString(", ")}")
                }
            }

            if (series.rounds.isNotEmpty()) {
                appendLine()
                appendLine("**Picks & Results:**")
                var roundNum = 0
                series.rounds.forEach { round ->
                    if (round.isWarmup) {
                        val mapLabel = round.slotTag.ifBlank { round.beatmapLabel.ifBlank { "Warmup" } }
                        appendLine("- [Warmup] $mapLabel")
                        return@forEach
                    }
                    roundNum++
                    val pickPrefix = when (round.pickedBy) {
                        MpTeam.RED -> "[Red Pick] "
                        MpTeam.BLUE -> "[Blue Pick] "
                        MpTeam.NONE -> ""
                    }
                    val mapTitle = when {
                        round.slotTag.isNotBlank() && round.beatmapLabel.isNotBlank() ->
                            "${round.slotTag} (${round.beatmapLabel})"
                        round.slotTag.isNotBlank() -> round.slotTag
                        round.beatmapLabel.isNotBlank() -> round.beatmapLabel
                        else -> "Map $roundNum"
                    }
                    val winnerText = when (round.winner) {
                        MpTeam.RED -> "${series.redTeamName} wins"
                        MpTeam.BLUE -> "${series.blueTeamName} wins"
                        MpTeam.NONE -> "Draw"
                    }
                    val scorePart = if (round.redTotalScore > 0L || round.blueTotalScore > 0L) {
                        " (${fmt.format(round.redTotalScore)} vs ${fmt.format(round.blueTotalScore)})"
                    } else {
                        ""
                    }
                    appendLine("$roundNum. $pickPrefix$mapTitle — $winnerText$scorePart")
                }
            }
        }.trimEnd()
    }

    fun parseLobbyState(
        channelId: String,
        channelName: String,
        messages: List<ChatMessage>
    ): MpLobbyState {
        val matchId = extractMatchId(channelId) ?: extractMatchId(channelName)
        var roomName = "Multiplayer Lobby ${matchId?.let { "#$it" } ?: ""}".trim()
        var historyUrl = matchId?.let { "https://osu.ppy.sh/mp/$it" }
        var beatmapId: Long? = null
        var beatmapTitle: String? = null
        var beatmapUrl: String? = null
        var teamMode = "HeadToHead"
        var winCondition = "Score"
        var activeMods = "None"
        var isFreeMod = false
        var isMatchInProgress = false
        var isLocked = false
        var isClosed = false
        var roomSize = 16
        var hostUsername: String? = null
        val slots = MutableList(16) { index -> MpSlot(slotNumber = index + 1) }
        val finishedRounds = mutableListOf<MpFinishedRoundEvent>()
        var roundRedScore = 0L
        var roundBlueScore = 0L

        fun clearSlots() {
            for (i in slots.indices) {
                slots[i] = MpSlot(slotNumber = i + 1)
            }
        }

        fun findSlotIndexByUser(name: String): Int {
            val clean = name.trim()
            val exact = slots.indexOfFirst { it.username.equals(clean, ignoreCase = true) }
            if (exact >= 0) return exact
            val normalized = clean.replace('_', ' ')
            return slots.indexOfFirst { it.username?.replace('_', ' ').equals(normalized, ignoreCase = true) }
        }

        for (msg in messages) {
            if (!msg.senderName.equals("BanchoBot", ignoreCase = true)) {
                if (msg.content.trim().equals("!mp close", ignoreCase = true)) {
                    isClosed = true
                    isMatchInProgress = false
                }
                continue
            }
            val lines = msg.content.lines()
            for (lineIdx in lines.indices) {
                val line = lines[lineIdx].trim()
                if (line.isEmpty()) continue

                val roomMatch = ROOM_NAME_HISTORY_REGEX.find(line)
                if (roomMatch != null) {
                    roomName = roomMatch.groupValues[1].trim()
                    historyUrl = roomMatch.groupValues[2].trim()
                    hostUsername = null
                    roundRedScore = 0L
                    roundBlueScore = 0L
                    clearSlots()
                    continue
                }

                val mapMatch = BEATMAP_SETTING_REGEX.find(line)
                if (mapMatch != null) {
                    val newUrl = mapMatch.groupValues[1].trim()
                    val newId = mapMatch.groupValues[2].ifBlank { mapMatch.groupValues[3] }.toLongOrNull()
                    val newTitle = mapMatch.groupValues[4].trim()
                    beatmapUrl = newUrl
                    beatmapTitle = newTitle.ifBlank { if (newId == beatmapId) beatmapTitle else null }
                    beatmapId = newId
                    continue
                }

                val modeMatch = TEAM_WIN_REGEX.find(line)
                if (modeMatch != null) {
                    teamMode = modeMatch.groupValues[1].trim()
                    winCondition = modeMatch.groupValues[2].trim()
                    continue
                }

                val changedSettingsMatch = CHANGED_SETTINGS_REGEX.find(line)
                if (changedSettingsMatch != null) {
                    changedSettingsMatch.groupValues[1].toIntOrNull()?.let { size ->
                        roomSize = size.coerceIn(1, 16)
                    }
                    teamMode = changedSettingsMatch.groupValues[2].trim()
                    winCondition = changedSettingsMatch.groupValues[3].trim()
                    continue
                }

                val modsMatch = ACTIVE_MODS_REGEX.find(line)
                if (modsMatch != null) {
                    activeMods = modsMatch.groupValues[1].trim()
                    isFreeMod = activeMods.contains("Freemod", ignoreCase = true)
                    continue
                }

                if (line.equals("Enabled FreeMod", ignoreCase = true)) {
                    isFreeMod = true
                    if (!activeMods.contains("Freemod", ignoreCase = true)) {
                        activeMods = if (activeMods.equals("None", ignoreCase = true)) "Freemod" else "$activeMods, Freemod"
                    }
                    continue
                }
                if (line.equals("Disabled FreeMod", ignoreCase = true)) {
                    isFreeMod = false
                    val remainingMods = activeMods.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.equals("Freemod", ignoreCase = true) }
                    activeMods = if (remainingMods.isEmpty()) "None" else remainingMods.joinToString(", ")
                    continue
                }
                if (line.startsWith("Disabled all mods", ignoreCase = true)) {
                    isFreeMod = when {
                        line.contains("enabled FreeMod", ignoreCase = true) -> true
                        line.contains("disabled FreeMod", ignoreCase = true) -> false
                        else -> isFreeMod
                    }
                    activeMods = if (isFreeMod) "Freemod" else "None"
                    continue
                }
                val enabledMatch = ENABLED_MODS_REGEX.find(line)
                if (enabledMatch != null) {
                    val modsPart = enabledMatch.groupValues[1].trim()
                    val freeModToggle = enabledMatch.groupValues[2]
                    isFreeMod = when {
                        freeModToggle.equals("enabled", ignoreCase = true) -> true
                        freeModToggle.equals("disabled", ignoreCase = true) -> false
                        modsPart.equals("FreeMod", ignoreCase = true) -> true
                        else -> isFreeMod
                    }
                    activeMods = if (isFreeMod && !modsPart.contains("Freemod", ignoreCase = true)) {
                        "$modsPart, Freemod"
                    } else {
                        modsPart
                    }
                    continue
                }
                if (line.startsWith("Players:", ignoreCase = true)) {
                    continue
                }

                val slotMatch = SLOT_LINE_REGEX.find(line)
                if (slotMatch != null) {
                    val slotNum = slotMatch.groupValues[1].toIntOrNull()
                    if (slotNum != null && slotNum in 1..16) {
                        val statusRaw = slotMatch.groupValues[2]
                        val uid = slotMatch.groupValues[3].toLongOrNull()
                        val rawTail = slotMatch.groupValues[4].trim()
                        val bracketMatch = TRAILING_BRACKET_REGEX.find(rawTail)
                        val (uname, bracketMeta) = if (bracketMatch != null && isValidSlotBracketMeta(bracketMatch.groupValues[2])) {
                            bracketMatch.groupValues[1].trim() to bracketMatch.groupValues[2].trim()
                        } else {
                            rawTail to ""
                        }

                        val status = when {
                            isMatchInProgress -> MpSlotStatus.PLAYING
                            statusRaw.equals("Ready", ignoreCase = true) -> MpSlotStatus.READY
                            statusRaw.equals("No Map", ignoreCase = true) -> MpSlotStatus.NO_MAP
                            else -> MpSlotStatus.NOT_READY
                        }

                        val metaParts = if (bracketMeta.isBlank()) {
                            emptyList()
                        } else {
                            bracketMeta.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                        }
                        val isHost = metaParts.any { it.equals("Host", ignoreCase = true) }
                        if (isHost) hostUsername = uname
                        val team = when {
                            metaParts.any { it.equals("Team Blue", ignoreCase = true) } -> MpTeam.BLUE
                            metaParts.any { it.equals("Team Red", ignoreCase = true) } -> MpTeam.RED
                            else -> MpTeam.NONE
                        }
                        val playerMods = metaParts.filterNot {
                            it.equals("Host", ignoreCase = true) ||
                                it.equals("Team Blue", ignoreCase = true) ||
                                it.equals("Team Red", ignoreCase = true)
                        }.joinToString(", ")

                        slots[slotNum - 1] = MpSlot(
                            slotNumber = slotNum,
                            username = uname,
                            userId = uid,
                            status = status,
                            team = team,
                            isHost = isHost,
                            mods = playerMods
                        )
                        if (slotNum > roomSize) roomSize = slotNum
                    }
                    continue
                }

                val joinedMatch = JOINED_SLOT_REGEX.find(line)
                if (joinedMatch != null) {
                    val uname = joinedMatch.groupValues[1].trim()
                    val slotNum = joinedMatch.groupValues[2].toIntOrNull()
                    if (slotNum != null && slotNum in 1..16) {
                        val teamRaw = joinedMatch.groupValues[3]
                        val team = when {
                            teamRaw.equals("blue", ignoreCase = true) -> MpTeam.BLUE
                            teamRaw.equals("red", ignoreCase = true) -> MpTeam.RED
                            else -> MpTeam.NONE
                        }
                        val oldIdx = findSlotIndexByUser(uname)
                        if (oldIdx >= 0) slots[oldIdx] = MpSlot(slotNumber = oldIdx + 1)
                        slots[slotNum - 1] = MpSlot(
                            slotNumber = slotNum,
                            username = uname,
                            status = MpSlotStatus.NOT_READY,
                            team = team,
                            isHost = uname.equals(hostUsername, ignoreCase = true)
                        )
                    }
                    continue
                }

                val movedMatch = MOVED_SLOT_REGEX.find(line)
                if (movedMatch != null) {
                    val uname = movedMatch.groupValues[1].trim()
                    val newSlot = movedMatch.groupValues[2].toIntOrNull()
                    if (newSlot != null && newSlot in 1..16) {
                        val oldIdx = findSlotIndexByUser(uname)
                        val existing = if (oldIdx >= 0) slots[oldIdx] else null
                        if (oldIdx >= 0) slots[oldIdx] = MpSlot(slotNumber = oldIdx + 1)
                        slots[newSlot - 1] = (existing ?: MpSlot(newSlot, username = uname, status = MpSlotStatus.NOT_READY))
                            .copy(slotNumber = newSlot)
                    }
                    continue
                }

                val teamMatch = CHANGED_TEAM_REGEX.find(line)
                if (teamMatch != null) {
                    val uname = teamMatch.groupValues[1].trim()
                    val newTeam = if (teamMatch.groupValues[2].equals("Blue", ignoreCase = true)) MpTeam.BLUE else MpTeam.RED
                    val idx = findSlotIndexByUser(uname)
                    if (idx >= 0) {
                        slots[idx] = slots[idx].copy(team = newTeam)
                    }
                    continue
                }

                val leftMatch = LEFT_GAME_REGEX.find(line)
                if (leftMatch != null) {
                    val uname = leftMatch.groupValues[1].trim()
                    val idx = findSlotIndexByUser(uname)
                    if (idx >= 0) {
                        slots[idx] = MpSlot(slotNumber = idx + 1)
                    }
                    if (uname.equals(hostUsername, ignoreCase = true)) {
                        hostUsername = null
                    }
                    continue
                }

                val hostMatch = HOST_CHANGED_REGEX.find(line)
                if (hostMatch != null) {
                    val newHost = hostMatch.groupValues[1].ifBlank { hostMatch.groupValues[2] }.trim()
                    if (newHost.isNotEmpty()) {
                        hostUsername = newHost
                        for (i in slots.indices) {
                            val s = slots[i]
                            if (s.username != null) {
                                slots[i] = s.copy(isHost = s.username.equals(newHost, ignoreCase = true))
                            }
                        }
                    }
                    continue
                }

                val sizeMatch = ROOM_SIZE_REGEX.find(line)
                if (sizeMatch != null) {
                    sizeMatch.groupValues[1].toIntOrNull()?.let { size ->
                        roomSize = size.coerceIn(1, 16)
                    }
                    continue
                }

                val playerFinishedMatch = PLAYER_FINISHED_REGEX.find(line)
                if (playerFinishedMatch != null) {
                    val uname = playerFinishedMatch.groupValues[1].trim()
                    val scoreVal = playerFinishedMatch.groupValues[2].replace(",", "").toLongOrNull() ?: 0L
                    val slotIdx = findSlotIndexByUser(uname)
                    if (slotIdx >= 0) {
                        when (slots[slotIdx].team) {
                            MpTeam.RED -> roundRedScore += scoreVal
                            MpTeam.BLUE -> roundBlueScore += scoreVal
                            MpTeam.NONE -> Unit
                        }
                    }
                    continue
                }

                when {
                    line.equals("Cleared match host", ignoreCase = true) -> {
                        hostUsername = null
                        for (i in slots.indices) {
                            if (slots[i].isHost) slots[i] = slots[i].copy(isHost = false)
                        }
                    }
                    line.equals("All players are ready", ignoreCase = true) -> {
                        for (i in slots.indices) {
                            if (slots[i].username != null) {
                                slots[i] = slots[i].copy(status = MpSlotStatus.READY)
                            }
                        }
                    }
                    line.startsWith("The match has started", ignoreCase = true) -> {
                        isMatchInProgress = true
                        roundRedScore = 0L
                        roundBlueScore = 0L
                        for (i in slots.indices) {
                            if (slots[i].username != null) {
                                slots[i] = slots[i].copy(status = MpSlotStatus.PLAYING)
                            }
                        }
                    }
                    line.startsWith("The match has finished", ignoreCase = true) -> {
                        isMatchInProgress = false
                        finishedRounds.add(
                            MpFinishedRoundEvent(
                                finishKey = "${msg.id}_$lineIdx",
                                beatmapId = beatmapId,
                                beatmapTitle = beatmapTitle,
                                teamMode = teamMode,
                                redTotalScore = roundRedScore,
                                blueTotalScore = roundBlueScore
                            )
                        )
                        roundRedScore = 0L
                        roundBlueScore = 0L
                        for (i in slots.indices) {
                            if (slots[i].username != null) {
                                slots[i] = slots[i].copy(status = MpSlotStatus.NOT_READY)
                            }
                        }
                    }
                    line.startsWith("Aborted the match", ignoreCase = true) -> {
                        isMatchInProgress = false
                        roundRedScore = 0L
                        roundBlueScore = 0L
                        for (i in slots.indices) {
                            if (slots[i].username != null) {
                                slots[i] = slots[i].copy(status = MpSlotStatus.NOT_READY)
                            }
                        }
                    }
                    line.equals("Locked the match", ignoreCase = true) -> {
                        isLocked = true
                    }
                    line.equals("Unlocked the match", ignoreCase = true) -> {
                        isLocked = false
                    }
                    line.startsWith("Closed the match", ignoreCase = true) -> {
                        isClosed = true
                        isMatchInProgress = false
                    }
                }
            }
        }

        return MpLobbyState(
            channelId = channelId,
            matchId = matchId,
            roomName = roomName,
            historyUrl = historyUrl,
            beatmapId = beatmapId,
            beatmapTitle = beatmapTitle,
            beatmapUrl = beatmapUrl,
            teamMode = teamMode,
            winCondition = winCondition,
            activeMods = activeMods,
            isFreeMod = isFreeMod,
            isMatchInProgress = isMatchInProgress,
            isLocked = isLocked,
            isClosed = isClosed,
            roomSize = roomSize,
            hostUsername = hostUsername,
            slots = slots,
            finishedRounds = finishedRounds
        )
    }
}
