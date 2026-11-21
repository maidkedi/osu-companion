package com.osuchat.app.data.network

import com.osuchat.app.data.preferences.OAuthTokenStore
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class OsuApiException(
    val httpCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

sealed class OsuApiPollerEvent {
    data class UpdatesReceived(val updates: ChatUpdatesResponse) : OsuApiPollerEvent()
    data class Reconnecting(val reason: String) : OsuApiPollerEvent()
    data class AuthFailed(val message: String) : OsuApiPollerEvent()
}

@Singleton
class OsuApiPoller internal constructor(
    private val authService: OsuAuthService,
    private val apiService: OsuApiService,
    private val publicBeatmapApi: PublicBeatmapApiService,
    private val nerinyanApi: NerinyanApiService,
    private val prefs: OAuthTokenStore,
    cloudflareChallengeManagerOrNull: CloudflareChallengeManager?
) {
    @Inject
    constructor(
        authService: OsuAuthService,
        apiService: OsuApiService,
        publicBeatmapApi: PublicBeatmapApiService,
        nerinyanApi: NerinyanApiService,
        prefs: UserPreferencesDataStore,
        cloudflareChallengeManager: CloudflareChallengeManager
    ) : this(
        authService = authService,
        apiService = apiService,
        publicBeatmapApi = publicBeatmapApi,
        nerinyanApi = nerinyanApi,
        prefs = prefs as OAuthTokenStore,
        cloudflareChallengeManagerOrNull = cloudflareChallengeManager
    )

    val cloudflareChallengeManager: CloudflareChallengeManager by lazy {
        checkNotNull(cloudflareChallengeManagerOrNull) { "CloudflareChallengeManager unavailable" }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenMutex = Mutex()
    private val pollWakeup = Channel<Unit>(Channel.CONFLATED)
    private val inFlightGetRequests = ConcurrentHashMap<String, Deferred<Any?>>()
    private val lastEndpointRequestAtMillis = ConcurrentHashMap<String, Long>()

    private var pollingJob: Job? = null
    private var lastMessageId: Long? = null

    @Volatile
    private var refreshInvalidatedToken: String? = null

    @Volatile
    private var refreshRateLimitedUntilMillis: Long = 0L

    @Volatile
    private var apiRateLimitedUntilMillis: Long = 0L

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _events = MutableSharedFlow<OsuApiPollerEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<OsuApiPollerEvent> = _events.asSharedFlow()

    fun setForeground(foreground: Boolean) {
        _isForeground.value = foreground
        if (foreground) {
            pollWakeup.trySend(Unit)
        }
    }

    fun onNetworkChanged() {
        if (pollingJob?.isActive == true) {
            pollWakeup.trySend(Unit)
        }
    }

    fun updateLastMessageId(messageId: Long) {
        val current = lastMessageId
        if (current == null || messageId > current) {
            lastMessageId = messageId
        }
    }

    fun resetAuthState() {
        refreshInvalidatedToken = null
        refreshRateLimitedUntilMillis = 0L
        apiRateLimitedUntilMillis = 0L
    }

    fun isRateLimited(): Boolean = System.currentTimeMillis() < apiRateLimitedUntilMillis

    fun remainingRateLimitSeconds(): Long {
        val rem = apiRateLimitedUntilMillis - System.currentTimeMillis()
        return ((rem + 999L) / 1000L).coerceAtLeast(1L)
    }

    suspend fun getValidBearerToken(
        forceRefresh: Boolean = false,
        failedBearerToken: String? = null
    ): String? = tokenMutex.withLock {
        val tokens = prefs.getOAuthTokens() ?: return@withLock null
        if (tokens.accessToken.isBlank()) return@withLock null

        val currentBearer = "Bearer ${tokens.accessToken}"
        val now = System.currentTimeMillis()

        // If another coroutine already refreshed the token while we waited for tokenMutex, reuse it.
        if (failedBearerToken != null &&
            currentBearer != failedBearerToken &&
            tokens.expiresAtMillis - now >= EXPIRY_BUFFER_MS
        ) {
            return@withLock currentBearer
        }

        val needsRefresh = forceRefresh || (tokens.expiresAtMillis - now < EXPIRY_BUFFER_MS)
        if (!needsRefresh) {
            return@withLock currentBearer
        }

        val stillUsableWithoutForce = !forceRefresh && tokens.expiresAtMillis - now > MIN_USABLE_MS

        if (tokens.refreshToken.isBlank() || tokens.refreshToken == refreshInvalidatedToken) {
            if (!stillUsableWithoutForce && tokens.refreshToken.isBlank()) {
                withContext(NonCancellable) {
                    runCatching { prefs.clearOAuthTokens() }
                }
                _events.tryEmit(OsuApiPollerEvent.AuthFailed(SESSION_EXPIRED_MESSAGE))
                return@withLock null
            }
            return@withLock if (stillUsableWithoutForce) currentBearer else null
        }

        if (now < refreshRateLimitedUntilMillis) {
            return@withLock if (stillUsableWithoutForce) currentBearer else null
        }

        return@withLock try {
            val refreshed = authService.refreshToken(
                OAuthTokenRefreshRequest(
                    grantType = "refresh_token",
                    refreshToken = tokens.refreshToken
                )
            )
            val newAccess = refreshed.accessToken?.trim().orEmpty()
            if (newAccess.isBlank()) {
                if (stillUsableWithoutForce) currentBearer else null
            } else {
                refreshInvalidatedToken = null
                refreshRateLimitedUntilMillis = 0L
                val expiresInSec = refreshed.expiresIn.coerceAtLeast(60L)
                val newExpiresAt = System.currentTimeMillis() + (expiresInSec * 1000L)
                prefs.updateOAuthTokens(
                    accessToken = newAccess,
                    refreshToken = refreshed.refreshToken?.takeIf { it.isNotBlank() } ?: tokens.refreshToken,
                    expiresAtMillis = newExpiresAt
                )
                "Bearer $newAccess"
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: HttpException) {
            when (e.code()) {
                400, 401 -> {
                    // Refresh token revoked/expired: clear OAuth tokens atomically and emit AuthFailed so UI returns to LoginScreen.
                    refreshInvalidatedToken = tokens.refreshToken
                    withContext(NonCancellable) {
                        runCatching { prefs.clearOAuthTokens() }
                    }
                    _events.tryEmit(
                        OsuApiPollerEvent.AuthFailed(SESSION_EXPIRED_MESSAGE)
                    )
                    return@withLock null
                }
                429 -> {
                    val waitMs = parseRetryAfterMillis(e)
                    refreshRateLimitedUntilMillis = System.currentTimeMillis() + waitMs
                }
            }
            if (stillUsableWithoutForce) currentBearer else null
        } catch (_: Exception) {
            if (stillUsableWithoutForce) currentBearer else null
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> coalesceGet(
        key: String,
        minIntervalMs: Long = DEFAULT_GET_SPACING_MS,
        block: suspend () -> T
    ): T {
        while (true) {
            val existing = inFlightGetRequests[key] as? Deferred<T>
            if (existing != null && existing.isActive) {
                return existing.await()
            }

            val created = scope.async(start = CoroutineStart.LAZY) {
                if (isRateLimited()) {
                    throw OsuApiException(
                        429,
                        "osu! API rate limit reached (429). Retry in ${remainingRateLimitSeconds()}s."
                    )
                }
                if (minIntervalMs > 0L) {
                    val prevAt = lastEndpointRequestAtMillis[key] ?: 0L
                    val elapsed = System.currentTimeMillis() - prevAt
                    if (elapsed in 0L until minIntervalMs) {
                        delay(minIntervalMs - elapsed)
                    }
                }
                if (lastEndpointRequestAtMillis.size > MAX_THROTTLE_KEYS) {
                    lastEndpointRequestAtMillis.clear()
                }
                lastEndpointRequestAtMillis[key] = System.currentTimeMillis()
                block()
            }

            val racer = if (existing == null) {
                inFlightGetRequests.putIfAbsent(key, created)
            } else {
                if (inFlightGetRequests.replace(key, existing, created)) null else inFlightGetRequests[key]
            }

            if (racer == null) {
                created.invokeOnCompletion {
                    inFlightGetRequests.remove(key, created)
                }
                return try {
                    created.await()
                } catch (t: Throwable) {
                    inFlightGetRequests.remove(key, created)
                    throw t
                }
            } else {
                created.cancel()
            }
        }
    }

    suspend fun <T> coalesceBearerGet(
        key: String,
        missingTokenMessage: String = "Sign in with osu! OAuth to continue",
        minIntervalMs: Long = DEFAULT_GET_SPACING_MS,
        block: suspend (bearerToken: String) -> T
    ): T = coalesceGet(key = key, minIntervalMs = minIntervalMs) {
        withBearerRetry(missingTokenMessage = missingTokenMessage, block = block)
    }

    suspend fun <T> withBearerRetry(
        missingTokenMessage: String = "Sign in with osu! OAuth to continue",
        block: suspend (bearerToken: String) -> T
    ): T {
        val token = getValidBearerToken()
            ?: throw OsuApiException(401, missingTokenMessage)
        return withOptionalBearerRetry(token, block)
    }

    suspend fun <T> withOptionalBearerRetry(
        initialToken: String,
        block: suspend (bearerToken: String) -> T
    ): T {
        if (isRateLimited()) {
            throw OsuApiException(
                429,
                "osu! API rate limit reached (429). Retry in ${remainingRateLimitSeconds()}s."
            )
        }
        return try {
            block(initialToken)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: HttpException) {
            if (e.code() == 401) {
                val refreshed = getValidBearerToken(forceRefresh = true, failedBearerToken = initialToken)
                    ?: throw OsuApiException(401, SESSION_EXPIRED_MESSAGE, e)
                try {
                    return block(refreshed)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (retryErr: Throwable) {
                    throw toFriendlyApiException(retryErr)
                }
            }
            if (e.code() == 429) {
                val waitMs = parseRetryAfterMillis(e)
                if (waitMs <= MAX_INLINE_429_RETRY_MS) {
                    delay(waitMs)
                    try {
                        return block(initialToken)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (retryErr: Throwable) {
                        throw toFriendlyApiException(retryErr)
                    }
                }
            }
            throw toFriendlyApiException(e)
        } catch (t: Throwable) {
            throw toFriendlyApiException(t)
        }
    }

    fun shouldFallbackToMirror(t: Throwable): Boolean {
        val code = when (t) {
            is OsuApiException -> t.httpCode
            is HttpException -> t.code()
            else -> when (val cause = t.cause) {
                is OsuApiException -> cause.httpCode
                is HttpException -> cause.code()
                else -> null
            }
        }
        return code == 403 || code == 429 || (code != null && code in 500..599)
    }

    suspend fun <T> withMirrorFallback(
        primary: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        return try {
            primary()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (shouldFallbackToMirror(t)) fallback() else throw t
        }
    }

    suspend fun getBeatmapFromMirrors(beatmapId: Long): OsuBeatmapDto? = withContext(Dispatchers.IO) {
        runCatching { publicBeatmapApi.getBeatmap(beatmapId) }.getOrNull()
    }

    suspend fun getBeatmapsetFromMirrors(beatmapsetId: Long): OsuBeatmapsetDto? = withContext(Dispatchers.IO) {
        runCatching { publicBeatmapApi.getBeatmapset(beatmapsetId) }.getOrNull()
    }

    suspend fun searchBeatmapsetsFromMirrors(
        query: String,
        modeInt: Int? = null,
        limit: Int = 20
    ): List<OsuBeatmapsetDto> = withContext(Dispatchers.IO) {
        val catboy = runCatching {
            publicBeatmapApi.searchBeatmapsets(query = query, modeInt = modeInt, limit = limit)
        }.getOrNull()
        if (!catboy.isNullOrEmpty()) return@withContext catboy
        runCatching {
            nerinyanApi.searchBeatmapsets(query = query, modeInt = modeInt, pageSize = limit)
        }.getOrNull().orEmpty()
    }

    fun buildAudioPreviewFallbackUrls(
        beatmapsetId: Long,
        beatmapId: Long = 0L,
        primaryUrl: String? = null
    ): List<String> {
        val urls = LinkedHashSet<String>()
        primaryUrl?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            urls.add(if (raw.startsWith("//")) "https:$raw" else raw)
        }
        if (beatmapsetId > 0L) {
            urls.add("https://b.ppy.sh/preview/$beatmapsetId.mp3")
        }
        if (beatmapId > 0L) {
            urls.add("https://catboy.best/preview/audio/$beatmapId")
        }
        return urls.toList()
    }

    fun buildOsuDownloadUrls(beatmapId: Long): List<String> = listOf(
        "https://osu.ppy.sh/osu/$beatmapId",
        "https://catboy.best/osu/$beatmapId"
    )

    fun toFriendlyApiException(t: Throwable): Throwable {
        if (t is CancellationException) throw t
        if (t is OsuApiException) return t
        if (t is HttpException) {
            val code = t.code()
            return when (code) {
                401 -> OsuApiException(401, SESSION_EXPIRED_MESSAGE, t)
                403 -> {
                    val cfHeader = t.response()?.headers()?.get("cf-mitigated")
                    val body = runCatching { t.response()?.errorBody()?.string() }.getOrNull().orEmpty()
                    val isCf = cfHeader.equals("challenge", ignoreCase = true) ||
                        body.contains("Cloudflare", ignoreCase = true) ||
                        body.contains("Just a moment", ignoreCase = true) ||
                        body.contains("challenge-platform", ignoreCase = true)
                    val msg = if (isCf) {
                        "Access denied (403) — blocked by Cloudflare."
                    } else {
                        "Access denied (403) — insufficient osu! API permissions."
                    }
                    OsuApiException(403, msg, t)
                }
                404 -> OsuApiException(404, "Requested osu! resource was not found (404).", t)
                429 -> {
                    val waitMs = parseRetryAfterMillis(t)
                    apiRateLimitedUntilMillis = System.currentTimeMillis() + waitMs
                    val waitSec = ((waitMs + 999L) / 1000L).coerceAtLeast(1L)
                    OsuApiException(429, "osu! API rate limit reached (429). Retry in ${waitSec}s.", t)
                }
                502, 503, 504 -> OsuApiException(
                    code,
                    "osu! servers are temporarily unavailable (HTTP $code). Please try again shortly.",
                    t
                )
                else -> OsuApiException(code, "osu! API request failed (HTTP $code).", t)
            }
        }
        return when (t) {
            is SocketTimeoutException -> IOException("Request to osu! timed out. Check your connection.", t)
            is UnknownHostException -> IOException("Unable to reach osu.ppy.sh. Check your internet connection.", t)
            else -> t
        }
    }

    fun parseRetryAfterMillis(e: HttpException): Long {
        val raw = e.response()?.headers()?.get("Retry-After")?.trim()
        if (!raw.isNullOrBlank()) {
            raw.toLongOrNull()?.let { seconds ->
                return (seconds * 1000L).coerceIn(MIN_RETRY_AFTER_MS, MAX_RETRY_AFTER_MS)
            }
            val parsedEpoch = runCatching {
                ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull() ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            if (parsedEpoch != null) {
                val delta = parsedEpoch - System.currentTimeMillis()
                return delta.coerceIn(MIN_RETRY_AFTER_MS, MAX_RETRY_AFTER_MS)
            }
        }
        return DEFAULT_RETRY_AFTER_MS
    }

    fun startPolling() {
        if (!OAuthConfig.SUPPORTS_CHAT_READ_HISTORY) return
        if (pollingJob?.isActive == true) return
        _isPolling.value = true
        pollingJob = scope.launch {
            var consecutiveErrors = 0
            while (isActive) {
                try {
                    val token = getValidBearerToken()
                    if (token == null) {
                        if (refreshInvalidatedToken == null) {
                            _events.emit(OsuApiPollerEvent.AuthFailed(SESSION_EXPIRED_MESSAGE))
                        }
                        break
                    }

                    val updates = try {
                        apiService.getChatUpdates(
                            bearerToken = token,
                            sinceMessageId = lastMessageId,
                            limit = 50
                        )
                    } catch (e: HttpException) {
                        if (e.code() == 401) {
                            val refreshedToken = getValidBearerToken(forceRefresh = true, failedBearerToken = token)
                            if (refreshedToken != null) {
                                apiService.getChatUpdates(
                                    bearerToken = refreshedToken,
                                    sinceMessageId = lastMessageId,
                                    limit = 50
                                )
                            } else {
                                if (prefs.getOAuthTokens() == null) {
                                    break
                                }
                                throw e
                            }
                        } else {
                            throw e
                        }
                    }

                    val maxId = updates.messages.orEmpty().maxOfOrNull { it.messageId }
                    if (maxId != null) {
                        updateLastMessageId(maxId)
                    }

                    consecutiveErrors = 0
                    _events.emit(OsuApiPollerEvent.UpdatesReceived(updates))

                    val delayMs = if (_isForeground.value) FOREGROUND_POLL_MS else BACKGROUND_POLL_MS
                    withTimeoutOrNull(delayMs) { pollWakeup.receive() }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: HttpException) {
                    if (e.code() == 400 || e.code() == 403 || e.code() == 404) {
                        // Third-party OAuth tokens with `public identify` do not grant `/api/v2/chat/*`.
                        // Stop polling quietly without overwriting ConnectionStatus.ONLINE.
                        break
                    }
                    if (e.code() == 401) {
                        val meStillValid = runCatching {
                            getValidBearerToken()?.let { apiService.getMe(it) }
                        }.isSuccess
                        if (meStillValid) {
                            break
                        }
                        _events.emit(OsuApiPollerEvent.AuthFailed(SESSION_EXPIRED_MESSAGE))
                        break
                    }
                    if (e.code() == 429) {
                        val retryMs = parseRetryAfterMillis(e)
                        apiRateLimitedUntilMillis = System.currentTimeMillis() + retryMs
                        _events.emit(OsuApiPollerEvent.Reconnecting("Rate limited (429), pausing ${retryMs / 1000}s…"))
                        withTimeoutOrNull(retryMs) { pollWakeup.receive() }
                        continue
                    }
                    consecutiveErrors++
                    _events.emit(OsuApiPollerEvent.Reconnecting("Server error (${e.code()}), retrying…"))
                    val shift = (consecutiveErrors - 1).coerceIn(0, 4)
                    val backoff = (3_000L shl shift).coerceAtMost(60_000L)
                    withTimeoutOrNull(backoff) { pollWakeup.receive() }
                } catch (e: Exception) {
                    consecutiveErrors++
                    _events.emit(
                        OsuApiPollerEvent.Reconnecting(e.message ?: "Network interrupted, reconnecting…")
                    )
                    val shift = (consecutiveErrors - 1).coerceIn(0, 4)
                    val backoff = (3_000L shl shift).coerceAtMost(60_000L)
                    withTimeoutOrNull(backoff) { pollWakeup.receive() }
                }
            }
            _isPolling.value = false
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    companion object {
        const val SESSION_EXPIRED_MESSAGE = "Session expired, please sign in again"
        private const val FOREGROUND_POLL_MS = 2_000L
        private const val BACKGROUND_POLL_MS = 12_000L
        private const val EXPIRY_BUFFER_MS = 60_000L
        private const val MIN_USABLE_MS = 5_000L
        private const val MIN_RETRY_AFTER_MS = 1_000L
        private const val DEFAULT_RETRY_AFTER_MS = 15_000L
        private const val MAX_RETRY_AFTER_MS = 120_000L
        private const val MAX_INLINE_429_RETRY_MS = 10_000L
        private const val DEFAULT_GET_SPACING_MS = 250L
        private const val MAX_THROTTLE_KEYS = 512
    }
}