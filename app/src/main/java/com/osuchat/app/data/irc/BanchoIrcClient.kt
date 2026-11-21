package com.osuchat.app.data.irc

import com.osuchat.app.domain.model.ConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

class IrcAuthException(message: String) : IllegalStateException(message)

/**
 * Token-bucket + minimum-spacing rate limiter for outbound Bancho IRC commands/messages.
 * Prevents Bancho flood kicks/silences during rapid messages, channel bursts, or Play Later batches.
 */
class IrcRateLimiter(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val refillIntervalMs: Long = DEFAULT_REFILL_INTERVAL_MS,
    private val minSpacingMs: Long = DEFAULT_MIN_SPACING_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    private val mutex = Mutex()
    private var tokens: Double = capacity.toDouble()
    private var lastRefillAtMs: Long = nowMs()
    private var lastPermitAtMs: Long = Long.MIN_VALUE

    init {
        require(capacity >= 1) { "capacity must be >= 1" }
        require(refillIntervalMs > 0L) { "refillIntervalMs must be > 0" }
        require(minSpacingMs >= 0L) { "minSpacingMs must be >= 0" }
    }

    /**
     * Suspends until one outbound IRC permit is available and consumes it.
     * Returns total delay (ms) waited.
     */
    suspend fun acquire(): Long {
        var totalWaitedMs = 0L
        while (true) {
            val waitMs = mutex.withLock {
                val now = nowMs()
                refillLocked(now)

                val effectiveTokens = tokens + TOKEN_EPSILON
                val tokenWait = if (effectiveTokens >= 1.0) {
                    0L
                } else {
                    val missing = (1.0 - tokens).coerceAtLeast(0.0)
                    kotlin.math.ceil(missing * refillIntervalMs.toDouble()).toLong().coerceAtLeast(1L)
                }

                val spacingWait = if (lastPermitAtMs == Long.MIN_VALUE || minSpacingMs == 0L) {
                    0L
                } else {
                    (lastPermitAtMs + minSpacingMs - now).coerceAtLeast(0L)
                }

                val requiredWait = maxOf(tokenWait, spacingWait)
                if (requiredWait <= 0L) {
                    tokens = (tokens - 1.0).coerceAtLeast(0.0)
                    lastPermitAtMs = now
                    0L
                } else {
                    requiredWait
                }
            }

            if (waitMs <= 0L) {
                return totalWaitedMs
            }
            delayFn(waitMs)
            totalWaitedMs += waitMs
        }
    }

    /**
     * Non-blocking check/consume. Returns true and consumes one token if immediately permitted.
     */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        val now = nowMs()
        refillLocked(now)
        val spacingOk = lastPermitAtMs == Long.MIN_VALUE ||
            minSpacingMs == 0L ||
            (now - lastPermitAtMs) >= minSpacingMs
        if (tokens + TOKEN_EPSILON >= 1.0 && spacingOk) {
            tokens = (tokens - 1.0).coerceAtLeast(0.0)
            lastPermitAtMs = now
            true
        } else {
            false
        }
    }

    suspend fun availableTokens(): Double = mutex.withLock {
        refillLocked(nowMs())
        tokens
    }

    suspend fun reset() = mutex.withLock {
        tokens = capacity.toDouble()
        lastRefillAtMs = nowMs()
        lastPermitAtMs = Long.MIN_VALUE
    }

    private fun refillLocked(now: Long) {
        if (now > lastRefillAtMs) {
            val elapsed = now - lastRefillAtMs
            val added = elapsed.toDouble() / refillIntervalMs.toDouble()
            tokens = minOf(capacity.toDouble(), tokens + added)
            lastRefillAtMs = now
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 5
        const val DEFAULT_REFILL_INTERVAL_MS = 1_200L
        const val DEFAULT_MIN_SPACING_MS = 250L
        private const val TOKEN_EPSILON = 1e-9
    }
}

@Singleton
class BanchoIrcClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val rateLimiter = IrcRateLimiter()
    private val socketLock = Any()
    private val reconnectWakeup = Channel<Unit>(Channel.CONFLATED)
    private val connectionSeq = AtomicLong(0L)
    private val isExplicitlyDisconnected = AtomicBoolean(false)

    @Volatile
    private var activeToken: Long = 0L

    private var connectionJob: Job? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    @Volatile
    private var manualDisconnect = false

    @Volatile
    private var resetBackoff = false

    @Volatile
    private var currentUsername: String = ""

    @Volatile
    private var currentPassword: String = ""

    private val pendingChannels = linkedSetOf("#turkish", "#osu", "#lobby", "#announce")
    private val joinedInCurrentSocket = linkedSetOf<String>()

    private val _status = MutableStateFlow(ConnectionStatus.OFFLINE)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _isAuthError = MutableStateFlow(false)
    val isAuthError: StateFlow<Boolean> = _isAuthError.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _events = MutableSharedFlow<IrcEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<IrcEvent> = _events.asSharedFlow()

    suspend fun connectAndVerify(
        username: String,
        password: String,
        initialChannels: List<String> = listOf("#turkish", "#osu", "#lobby", "#announce")
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanNick = username.trim().replace(' ', '_')
        if (cleanNick.isBlank() || password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("IRC username and password are required"))
        }

        resetExplicitDisconnect()
        clearAuthError()
        val authDeferred = CompletableDeferred<Result<Unit>>()
        val myToken = startConnectionLoop(
            nick = cleanNick,
            password = password,
            initialChannels = initialChannels,
            authDeferred = authDeferred,
            failFastOnConnectError = true
        )

        val outcome = try {
            withTimeoutOrNull(VERIFY_TIMEOUT_MS) { authDeferred.await() }
        } catch (ce: CancellationException) {
            if (!authDeferred.isCompleted) {
                cancelIfTokenMatches(myToken, ConnectionStatus.OFFLINE)
            }
            throw ce
        }
        if (outcome != null) {
            return@withContext outcome
        }

        cancelIfTokenMatches(myToken, ConnectionStatus.ERROR)
        Result.failure(IllegalStateException("Timed out connecting to Bancho IRC (irc.ppy.sh)"))
    }

    fun isExplicitlyDisconnected(): Boolean = isExplicitlyDisconnected.get()

    fun setExplicitlyDisconnected(explicitlyDisconnected: Boolean) {
        isExplicitlyDisconnected.set(explicitlyDisconnected)
    }

    fun resetExplicitDisconnect() {
        isExplicitlyDisconnected.set(false)
    }

    fun clearAuthError() {
        _isAuthError.value = false
        _authErrorMessage.value = null
        if (_status.value == ConnectionStatus.ERROR) {
            _status.value = ConnectionStatus.OFFLINE
        }
    }

    fun startBackgroundReconnect(username: String, password: String, initialChannels: List<String>) {
        if (isExplicitlyDisconnected.get()) return
        val cleanNick = username.trim().replace(' ', '_')
        if (cleanNick.isBlank() || password.isBlank()) return
        scope.launch {
            lifecycleMutex.withLock {
                if (isExplicitlyDisconnected.get()) return@withLock
                val sameCreds = currentUsername == cleanNick && currentPassword == password
                if (_isAuthError.value && sameCreds) {
                    // Never auto-retry Bancho IRC with credentials already rejected by 464/465
                    return@withLock
                }
                val active = connectionJob?.isActive == true && !manualDisconnect
                if (!active || !sameCreds) {
                    _isAuthError.value = false
                    _authErrorMessage.value = null
                    startConnectionLoopLocked(
                        nick = cleanNick,
                        password = password,
                        initialChannels = initialChannels,
                        authDeferred = null,
                        failFastOnConnectError = false
                    )
                }
            }
        }
    }

    private suspend fun startConnectionLoop(
        nick: String,
        password: String,
        initialChannels: List<String>,
        authDeferred: CompletableDeferred<Result<Unit>>?,
        failFastOnConnectError: Boolean
    ): Long = lifecycleMutex.withLock {
        startConnectionLoopLocked(
            nick = nick,
            password = password,
            initialChannels = initialChannels,
            authDeferred = authDeferred,
            failFastOnConnectError = failFastOnConnectError
        )
    }

    private suspend fun startConnectionLoopLocked(
        nick: String,
        password: String,
        initialChannels: List<String>,
        authDeferred: CompletableDeferred<Result<Unit>>?,
        failFastOnConnectError: Boolean
    ): Long {
        manualDisconnect = true
        val oldJob: Job?
        synchronized(socketLock) {
            activeToken = connectionSeq.incrementAndGet()
            oldJob = connectionJob
            connectionJob = null
        }
        oldJob?.cancel()
        closeSocketQuietly(expectedToken = null)
        if (oldJob != null) {
            withTimeoutOrNull(2_000L) { oldJob.join() }
        }

        while (reconnectWakeup.tryReceive().isSuccess) {
            // Drain stale network wakeup signals from prior connection lifecycle
        }

        manualDisconnect = false
        resetBackoff = false
        currentUsername = nick
        currentPassword = password
        synchronized(pendingChannels) {
            pendingChannels.clear()
            val normalized = initialChannels
                .mapNotNull { normalizeChannelName(it) }
                .ifEmpty { DEFAULT_CHANNELS }
            pendingChannels.addAll(normalized)
            joinedInCurrentSocket.clear()
        }

        val myToken = synchronized(socketLock) {
            activeToken = connectionSeq.incrementAndGet()
            activeToken
        }
        _status.value = ConnectionStatus.CONNECTING

        val newJob = scope.launch {
            runConnectionLoop(
                myToken = myToken,
                authDeferred = authDeferred,
                failFastOnConnectError = failFastOnConnectError
            )
        }
        synchronized(socketLock) {
            if (activeToken == myToken) {
                connectionJob = newJob
            } else {
                newJob.cancel()
            }
        }
        return myToken
    }

    private suspend fun CoroutineScope.runConnectionLoop(
        myToken: Long,
        authDeferred: CompletableDeferred<Result<Unit>>?,
        failFastOnConnectError: Boolean
    ) {
        var attempt = 0
        while (isActive && !manualDisconnect && !isExplicitlyDisconnected.get() && activeToken == myToken) {
            if (resetBackoff) {
                attempt = 0
                resetBackoff = false
            }
            if (attempt > 0) {
                _status.value = ConnectionStatus.RECONNECTING
                val backoffMs = (2000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(60_000L)
                withTimeoutOrNull(backoffMs) { reconnectWakeup.receive() }
                if (manualDisconnect || isExplicitlyDisconnected.get() || !isActive || activeToken != myToken) break
                if (resetBackoff) {
                    attempt = 0
                    resetBackoff = false
                }
            }

            var localSocket: Socket? = null
            var localWriter: BufferedWriter? = null
            var localReader: BufferedReader? = null
            var initialJoinJob: Job? = null
            var authenticated = false

            try {
                val (openedSocket, openedWriter, openedReader) = openSocketForToken(myToken)
                localSocket = openedSocket
                localWriter = openedWriter
                localReader = openedReader

                if (!sendRawLine("PASS $currentPassword") ||
                    !sendRawLine("NICK $currentUsername") ||
                    !sendRawLine("USER $currentUsername 0 * :$currentUsername")
                ) {
                    throw IllegalStateException("Failed to write IRC handshake")
                }

                var awaitingKeepalivePong = false

                while (isActive && !manualDisconnect && !isExplicitlyDisconnected.get() && activeToken == myToken) {
                    val line = try {
                        openedReader.readLine()
                    } catch (_: SocketTimeoutException) {
                        if (awaitingKeepalivePong) {
                            // Server did not answer keepalive PING: treat as half-open socket
                            break
                        }
                        awaitingKeepalivePong = true
                        runCatching { openedSocket.soTimeout = PONG_TIMEOUT_MS }
                        if (!sendRawLine("PING :keepalive_${System.currentTimeMillis()}")) {
                            break
                        }
                        continue
                    }
                    if (line == null) break

                    if (awaitingKeepalivePong) {
                        awaitingKeepalivePong = false
                        runCatching { openedSocket.soTimeout = READ_TIMEOUT_MS }
                    }

                    val event = IrcParser.toIrcEvent(line) ?: continue

                    when (event) {
                        is IrcEvent.Ping -> {
                            if (!sendRawLine("PONG :${event.token}")) {
                                break
                            }
                        }
                        is IrcEvent.Connected -> {
                            authenticated = true
                            attempt = 0
                            _isAuthError.value = false
                            _authErrorMessage.value = null
                            _status.value = ConnectionStatus.ONLINE
                            rateLimiter.reset()
                            if (authDeferred != null && !authDeferred.isCompleted) {
                                authDeferred.complete(Result.success(Unit))
                            }
                            val channelsToJoin = synchronized(pendingChannels) {
                                joinedInCurrentSocket.clear()
                                pendingChannels.toList()
                            }
                            initialJoinJob?.cancel()
                            initialJoinJob = launch {
                                for (ch in channelsToJoin) {
                                    if (!isActive || manualDisconnect || isExplicitlyDisconnected.get() || activeToken != myToken) {
                                        break
                                    }
                                    if (ch.startsWith("#") && sendRawLine("JOIN $ch", rateLimited = true)) {
                                        synchronized(pendingChannels) { joinedInCurrentSocket.add(ch) }
                                    }
                                }
                            }
                            _events.emit(event)
                        }
                        is IrcEvent.AuthFailed -> {
                            val formattedReason = formatAuthFailureMessage(event.reason)
                            _isAuthError.value = true
                            _authErrorMessage.value = formattedReason
                            _status.value = ConnectionStatus.ERROR
                            manualDisconnect = true
                            if (authDeferred != null && !authDeferred.isCompleted) {
                                authDeferred.complete(Result.failure(IrcAuthException(formattedReason)))
                            }
                            _events.emit(IrcEvent.AuthFailed(formattedReason))
                            break
                        }
                        is IrcEvent.ServerNotice -> {
                            if (event.text.contains("Bad authentication token", ignoreCase = true) ||
                                event.text.contains("ERR_PASSWDMISMATCH", ignoreCase = true)
                            ) {
                                val formattedReason = formatAuthFailureMessage(event.text)
                                _isAuthError.value = true
                                _authErrorMessage.value = formattedReason
                                _status.value = ConnectionStatus.ERROR
                                manualDisconnect = true
                                if (authDeferred != null && !authDeferred.isCompleted) {
                                    authDeferred.complete(Result.failure(IrcAuthException(formattedReason)))
                                }
                                _events.emit(IrcEvent.AuthFailed(formattedReason))
                                break
                            } else {
                                _events.emit(event)
                            }
                        }
                        else -> {
                            _events.emit(event)
                        }
                    }
                }

                if (!manualDisconnect && !isExplicitlyDisconnected.get() && activeToken == myToken) {
                    attempt++
                    if (failFastOnConnectError && !authenticated && authDeferred != null && !authDeferred.isCompleted && attempt >= 2) {
                        val err = IllegalStateException("Connection closed by Bancho IRC before authentication")
                        _status.value = ConnectionStatus.ERROR
                        authDeferred.complete(Result.failure(err))
                        manualDisconnect = true
                        break
                    }
                    if (authenticated) {
                        _status.value = ConnectionStatus.RECONNECTING
                        _events.emit(IrcEvent.Disconnected("Connection dropped", willReconnect = true))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!manualDisconnect && !isExplicitlyDisconnected.get() && activeToken == myToken) {
                    attempt++
                    if (failFastOnConnectError && authDeferred != null && !authDeferred.isCompleted && attempt >= 2) {
                        _status.value = ConnectionStatus.ERROR
                        authDeferred.complete(Result.failure(e))
                        manualDisconnect = true
                        break
                    }
                    _status.value = ConnectionStatus.RECONNECTING
                    _events.emit(IrcEvent.Disconnected(e.message, willReconnect = true))
                }
            } finally {
                initialJoinJob?.cancel()
                closeSocketResources(localSocket, localWriter, localReader, expectedToken = myToken)
            }
        }

        if (activeToken == myToken && (manualDisconnect || isExplicitlyDisconnected.get()) && _status.value != ConnectionStatus.ERROR) {
            _status.value = ConnectionStatus.OFFLINE
        }
    }

    /**
     * Called on network interface transitions (Wi-Fi <-> Mobile Data) or connectivity recovery.
     * Closes any half-open socket on the old interface and wakes backoff sleep immediately.
     */
    fun onNetworkChanged() {
        val jobActive = synchronized(socketLock) { connectionJob?.isActive == true }
        if (isExplicitlyDisconnected.get() || manualDisconnect || !jobActive) return
        resetBackoff = true
        _status.value = ConnectionStatus.RECONNECTING
        scope.launch {
            closeSocketQuietly(expectedToken = activeToken)
            reconnectWakeup.trySend(Unit)
        }
    }

    private fun openSocketForToken(myToken: Long): Triple<Socket, BufferedWriter, BufferedReader> {
        val plainSocket = Socket()
        synchronized(socketLock) {
            if (manualDisconnect || isExplicitlyDisconnected.get() || activeToken != myToken) {
                runCatching { plainSocket.close() }
                throw CancellationException("Connection cancelled before socket open")
            }
            socket = plainSocket
            joinedInCurrentSocket.clear()
        }

        try {
            plainSocket.keepAlive = true
            plainSocket.tcpNoDelay = true
            plainSocket.connect(InetSocketAddress(HOST, PLAIN_PORT), CONNECT_TIMEOUT_MS)
            plainSocket.soTimeout = READ_TIMEOUT_MS

            val outWriter = BufferedWriter(OutputStreamWriter(plainSocket.getOutputStream(), Charsets.UTF_8))
            val inReader = BufferedReader(InputStreamReader(plainSocket.getInputStream(), Charsets.UTF_8))

            synchronized(socketLock) {
                if (manualDisconnect || isExplicitlyDisconnected.get() || activeToken != myToken) {
                    runCatching { plainSocket.close() }
                    runCatching { outWriter.close() }
                    runCatching { inReader.close() }
                    throw CancellationException("Connection cancelled during handshake setup")
                }
                writer = outWriter
            }
            return Triple(plainSocket, outWriter, inReader)
        } catch (t: Throwable) {
            runCatching { plainSocket.close() }
            synchronized(socketLock) {
                if (socket === plainSocket) {
                    socket = null
                    writer = null
                }
            }
            throw t
        }
    }

    suspend fun joinChannel(channelName: String) {
        val normalized = normalizeChannelName(channelName) ?: return
        val shouldSend = synchronized(pendingChannels) {
            pendingChannels.add(normalized)
            normalized !in joinedInCurrentSocket
        }
        if (shouldSend && sendRawLine("JOIN $normalized", rateLimited = true)) {
            synchronized(pendingChannels) {
                if (socket != null) {
                    joinedInCurrentSocket.add(normalized)
                }
            }
        }
    }

    suspend fun partChannel(channelName: String) {
        val normalized = normalizeChannelName(channelName) ?: return
        synchronized(pendingChannels) {
            pendingChannels.remove(normalized)
            joinedInCurrentSocket.remove(normalized)
        }
        sendRawLine("PART $normalized", rateLimited = true)
    }

    suspend fun sendPrivMsg(target: String, text: String, isAction: Boolean = false): Boolean {
        val trimmedTarget = target.replace('\u0000', ' ').replace("\r", "").replace("\n", "").trim()
        if (trimmedTarget.isEmpty()) return false
        val ircTarget = if (trimmedTarget.startsWith("#")) trimmedTarget.lowercase() else trimmedTarget.replace(' ', '_')
        val lines = text.replace("\r", "").replace('\u0000', ' ').split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return false
        var anySent = false
        for (line in lines) {
            val payload = if (isAction) "\u0001ACTION $line\u0001" else line
            if (sendRawLine("PRIVMSG $ircTarget :$payload", rateLimited = true)) {
                anySent = true
            } else {
                break
            }
        }
        return anySent
    }

    private suspend fun sendRawLine(line: String, rateLimited: Boolean = false): Boolean {
        if (rateLimited) {
            rateLimiter.acquire()
        }
        return writeMutex.withLock {
            val activeWriter = synchronized(socketLock) { writer } ?: return@withLock false
            runCatching {
                activeWriter.write(line)
                activeWriter.write("\r\n")
                activeWriter.flush()
                true
            }.getOrDefault(false)
        }
    }

    fun disconnect() {
        disconnectExplicitly()
    }

    fun disconnectExplicitly() {
        isExplicitlyDisconnected.set(true)
        manualDisconnect = true
        _isAuthError.value = false
        _authErrorMessage.value = null
        val oldJob: Job?
        val oldSocket: Socket?
        val oldWriter: BufferedWriter?
        synchronized(socketLock) {
            activeToken = connectionSeq.incrementAndGet()
            oldJob = connectionJob
            connectionJob = null
            oldSocket = socket
            oldWriter = writer
            socket = null
            writer = null
            joinedInCurrentSocket.clear()
        }
        oldJob?.cancel()
        while (reconnectWakeup.tryReceive().isSuccess) {
            // Drain pending wakeup signals so no reconnect can fire after explicit disconnect
        }
        scope.launch {
            if (oldWriter != null) {
                withTimeoutOrNull(QUIT_FLUSH_TIMEOUT_MS) {
                    writeMutex.withLock {
                        runCatching {
                            oldWriter.write("QUIT :osu! Companion disconnected\r\n")
                            oldWriter.flush()
                        }
                    }
                }
            }
            runCatching { oldSocket?.close() }
            runCatching { oldWriter?.close() }
        }
        _status.value = ConnectionStatus.OFFLINE
    }

    private fun cancelIfTokenMatches(token: Long, targetStatus: ConnectionStatus) {
        val oldJob: Job?
        val oldSocket: Socket?
        val oldWriter: BufferedWriter?
        synchronized(socketLock) {
            if (activeToken != token) return
            manualDisconnect = true
            activeToken = connectionSeq.incrementAndGet()
            oldJob = connectionJob
            connectionJob = null
            oldSocket = socket
            oldWriter = writer
            socket = null
            writer = null
            joinedInCurrentSocket.clear()
        }
        oldJob?.cancel()
        scope.launch {
            runCatching { oldSocket?.close() }
            runCatching { oldWriter?.close() }
        }
        _status.value = targetStatus
    }

    private fun closeSocketQuietly(expectedToken: Long?) {
        val oldSocket: Socket?
        val oldWriter: BufferedWriter?
        synchronized(socketLock) {
            if (expectedToken != null && expectedToken != activeToken) return
            oldSocket = socket
            oldWriter = writer
            socket = null
            writer = null
            joinedInCurrentSocket.clear()
        }
        runCatching { oldSocket?.close() }
        runCatching { oldWriter?.close() }
    }

    private fun closeSocketResources(
        localSocket: Socket?,
        localWriter: BufferedWriter?,
        localReader: BufferedReader?,
        expectedToken: Long
    ) {
        synchronized(socketLock) {
            if (expectedToken == activeToken) {
                if (socket === localSocket) socket = null
                if (writer === localWriter) writer = null
                joinedInCurrentSocket.clear()
            }
        }
        runCatching { localSocket?.close() }
        runCatching { localWriter?.close() }
        runCatching { localReader?.close() }
    }

    private fun normalizeChannelName(raw: String): String? {
        val clean = raw.trim().lowercase()
        if (clean.isEmpty()) return null
        return if (clean.startsWith("#")) clean else "#$clean"
    }

    private fun formatAuthFailureMessage(rawReason: String): String {
        val clean = rawReason.trim().ifBlank { "Bad authentication token" }
        return "Bancho IRC authentication failed ($clean). Update your IRC password at $LEGACY_IRC_SETTINGS_URL"
    }

    companion object {
        const val HOST = "irc.ppy.sh"
        const val TLS_PORT = 6697
        const val PLAIN_PORT = 6667
        const val LEGACY_IRC_SETTINGS_URL = "https://osu.ppy.sh/home/account/edit#legacy-api"
        private val DEFAULT_CHANNELS = listOf("#turkish", "#osu", "#lobby", "#announce")
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val PONG_TIMEOUT_MS = 15_000
        private const val VERIFY_TIMEOUT_MS = 20_000L
        private const val QUIT_FLUSH_TIMEOUT_MS = 1_000L
    }
}
