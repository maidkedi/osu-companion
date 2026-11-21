package com.osuchat.app.data.network

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareChallengeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cookieStoreLock = Any()
    private val cookiesByKey = ConcurrentHashMap<String, Cookie>()
    @Volatile
    private var storeLoaded = false

    private val challengeMutex = Mutex()
    private val challengeLock = Any()
    private var activeDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var lastVerifiedAtMillis: Long = 0L

    @Volatile
    private var lastResolutionAtMillis: Long = 0L

    @Volatile
    private var lastResolutionSucceeded: Boolean = false

    private val _challengeUrl = MutableStateFlow<String?>(null)
    val challengeUrl: StateFlow<String?> = _challengeUrl.asStateFlow()

    val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (isCookieStrippedEndpoint(url.encodedPath)) return
            ensureLoaded()
            if (cookies.isEmpty()) return
            val now = System.currentTimeMillis()
            synchronized(cookieStoreLock) {
                for (cookie in cookies) {
                    if (isOsuHost(cookie.domain)) {
                        removeOsuCookiesNamedLocked(cookie.name)
                    }
                    val key = cookieKey(cookie)
                    if (cookie.expiresAt <= now) {
                        cookiesByKey.remove(key)
                    } else {
                        cookiesByKey[key] = cookie
                    }
                }
                pruneExpiredLocked(now)
                persistLocked()
            }
            if (isOsuHost(url.host)) {
                pushCookiesToWebView(url.toString(), cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            // Never send browser/Cloudflare cookies to /oauth/token or /api/v2/* endpoints.
            if (isCookieStrippedEndpoint(url.encodedPath)) return emptyList()
            ensureLoaded()
            val now = System.currentTimeMillis()
            if (isOsuHost(url.host) && !hasCookieNamed("cf_clearance", now)) {
                syncCookiesFromWebView(url.toString())
            }
            return synchronized(cookieStoreLock) {
                if (pruneExpiredLocked(now)) {
                    persistLocked()
                }
                cookiesByKey.values.filter { it.matches(url) }
            }
        }
    }

    suspend fun awaitChallengeResolution(
        url: String = DEFAULT_OSU_URL,
        hadCfClearanceCookie: Boolean = false,
        failedCfClearance: String? = null,
        attemptStartedAtMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = DEFAULT_CHALLENGE_TIMEOUT_MS
    ): Boolean {
        // Never open WebView on /oauth/token (opening a POST OAuth endpoint as GET causes an immediate Cloudflare block).
        if (isOAuthEndpoint(url)) {
            return false
        }

        return challengeMutex.withLock {
            // Always load the interactive osu.ppy.sh/home page, never raw /api/v2/... or /oauth/... URLs.
            val targetUrl = DEFAULT_OSU_URL

            // If a concurrent request resolved a challenge after this request started,
            // reuse its outcome without opening a duplicate dialog.
            if (lastResolutionAtMillis >= attemptStartedAtMillis) {
                if (lastResolutionSucceeded) {
                    syncCookiesFromWebView(DEFAULT_OSU_URL)
                    if (hasCfClearanceCookie(targetUrl)) {
                        return@withLock true
                    }
                } else {
                    return@withLock false
                }
            }

            val currentCf = currentCfClearance(targetUrl)
            val now = System.currentTimeMillis()
            if (!currentCf.isNullOrBlank() &&
                currentCf != failedCfClearance &&
                (!hadCfClearanceCookie || (now - lastVerifiedAtMillis) in 0L until RECENT_VERIFICATION_GRACE_MS)
            ) {
                syncCookiesFromWebView(DEFAULT_OSU_URL)
                return@withLock true
            }

            val deferred = CompletableDeferred<Boolean>()
            synchronized(challengeLock) {
                activeDeferred = deferred
                _challengeUrl.value = targetUrl
            }

            val verified = try {
                withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
            } finally {
                synchronized(challengeLock) {
                    if (activeDeferred === deferred) {
                        activeDeferred = null
                    }
                    if (!deferred.isCompleted) {
                        deferred.complete(false)
                    }
                    _challengeUrl.value = null
                }
            }
            lastResolutionAtMillis = System.currentTimeMillis()
            lastResolutionSucceeded = verified
            verified
        }
    }

    fun awaitChallengeResolutionBlocking(
        url: String = DEFAULT_OSU_URL,
        hadCfClearanceCookie: Boolean = false,
        failedCfClearance: String? = null,
        attemptStartedAtMillis: Long = System.currentTimeMillis(),
        timeoutMs: Long = DEFAULT_CHALLENGE_TIMEOUT_MS
    ): Boolean = runBlocking {
        awaitChallengeResolution(
            url = url,
            hadCfClearanceCookie = hadCfClearanceCookie,
            failedCfClearance = failedCfClearance,
            attemptStartedAtMillis = attemptStartedAtMillis,
            timeoutMs = timeoutMs
        )
    }

    fun resolveChallenge(verified: Boolean) {
        val now = System.currentTimeMillis()
        if (verified) {
            runCatching { CookieManager.getInstance().flush() }
            syncCookiesFromWebView(DEFAULT_OSU_URL)
            lastVerifiedAtMillis = now
        } else {
            clearCloudflareCookies()
        }
        lastResolutionAtMillis = now
        lastResolutionSucceeded = verified
        val toComplete: CompletableDeferred<Boolean>?
        synchronized(challengeLock) {
            toComplete = activeDeferred
            activeDeferred = null
            _challengeUrl.value = null
        }
        toComplete?.complete(verified)
    }

    fun onVerified() = resolveChallenge(true)

    fun onCancelled() = resolveChallenge(false)

    fun clearCloudflareCookies() {
        ensureLoaded()
        synchronized(cookieStoreLock) {
            removeOsuCookiesNamedLocked("cf_clearance")
            removeOsuCookiesNamedLocked("__cf_bm")
            persistLocked()
        }
        lastVerifiedAtMillis = 0L
        lastResolutionAtMillis = 0L
        lastResolutionSucceeded = false
        runCatching {
            val manager = CookieManager.getInstance()
            val expiredCookies = listOf(
                "cf_clearance=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure",
                "cf_clearance=; Domain=.ppy.sh; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure",
                "cf_clearance=; Domain=osu.ppy.sh; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure",
                "__cf_bm=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure",
                "__cf_bm=; Domain=.ppy.sh; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure",
                "__cf_bm=; Domain=osu.ppy.sh; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure"
            )
            val cookieTargets = listOf(
                "https://osu.ppy.sh/",
                "https://osu.ppy.sh/home",
                "https://.ppy.sh/"
            )
            for (target in cookieTargets) {
                for (expired in expiredCookies) {
                    manager.setCookie(target, expired)
                }
            }
            manager.flush()
        }
    }

    fun syncCookiesFromWebView(url: String = DEFAULT_OSU_URL) {
        ensureLoaded()
        runCatching { CookieManager.getInstance().flush() }
        val rawHeader = runCatching {
            CookieManager.getInstance().getCookie(url)
        }.getOrNull()?.trim().orEmpty()
        if (rawHeader.isBlank()) return

        val httpUrl = url.toHttpUrlOrNull() ?: DEFAULT_OSU_URL.toHttpUrlOrNull() ?: return
        val host = httpUrl.host
        val fallbackExpiry = System.currentTimeMillis() + WEBVIEW_COOKIE_TTL_MS
        val parsed = rawHeader.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) return@mapNotNull null
            val name = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            if (name.isBlank()) return@mapNotNull null
            val domain = if (host.endsWith("ppy.sh", ignoreCase = true)) "ppy.sh" else host
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .expiresAt(fallbackExpiry)
                    .apply {
                        if (httpUrl.isHttps) secure()
                    }
                    .build()
            }.getOrNull()
        }
        if (parsed.isEmpty()) return

        synchronized(cookieStoreLock) {
            for (cookie in parsed) {
                if (isOsuHost(cookie.domain)) {
                    removeOsuCookiesNamedLocked(cookie.name)
                }
                cookiesByKey[cookieKey(cookie)] = cookie
            }
            persistLocked()
        }
    }

    fun currentCfClearance(url: String = DEFAULT_OSU_URL): String? {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val inMemory = synchronized(cookieStoreLock) {
            cookiesByKey.values.firstOrNull { cookie ->
                cookie.name.equals("cf_clearance", ignoreCase = true) && cookie.expiresAt > now
            }?.value?.takeIf { it.isNotBlank() }
        }
        if (inMemory != null) return inMemory
        return extractCfClearance(
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        ) ?: extractCfClearance(
            runCatching { CookieManager.getInstance().getCookie(DEFAULT_OSU_URL) }.getOrNull()
        )
    }

    fun hasCfClearanceCookie(url: String = DEFAULT_OSU_URL): Boolean {
        return !currentCfClearance(url).isNullOrBlank()
    }

    private fun removeOsuCookiesNamedLocked(name: String) {
        val iter = cookiesByKey.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next().value
            if (entry.name.equals(name, ignoreCase = true) && isOsuHost(entry.domain)) {
                iter.remove()
            }
        }
    }

    private fun hasCookieNamed(name: String, now: Long): Boolean {
        return cookiesByKey.values.any { cookie ->
            cookie.name.equals(name, ignoreCase = true) && cookie.expiresAt > now
        }
    }

    private fun pushCookiesToWebView(url: String, cookies: List<Cookie>) {
        runCatching {
            val manager = CookieManager.getInstance()
            for (cookie in cookies) {
                val setCookieValue = "${cookie.name}=${cookie.value}; Domain=${cookie.domain}; Path=${cookie.path}"
                manager.setCookie(url, setCookieValue)
            }
            manager.flush()
        }
    }

    private fun ensureLoaded() {
        if (storeLoaded) return
        synchronized(cookieStoreLock) {
            if (storeLoaded) return
            val raw = runCatching { prefs.getString(KEY_COOKIES_JSON, null) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                runCatching {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val cookie = decodeCookie(obj) ?: continue
                        if (cookie.expiresAt > now) {
                            cookiesByKey[cookieKey(cookie)] = cookie
                        }
                    }
                }
            }
            storeLoaded = true
        }
    }

    private fun pruneExpiredLocked(now: Long): Boolean {
        var removed = false
        val iter = cookiesByKey.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.expiresAt <= now) {
                iter.remove()
                removed = true
            }
        }
        return removed
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (cookie in cookiesByKey.values) {
            array.put(encodeCookie(cookie))
        }
        runCatching {
            prefs.edit().putString(KEY_COOKIES_JSON, array.toString()).apply()
        }
    }

    private fun encodeCookie(cookie: Cookie): JSONObject = JSONObject().apply {
        put("name", cookie.name)
        put("value", cookie.value)
        put("expiresAt", cookie.expiresAt)
        put("domain", cookie.domain)
        put("path", cookie.path)
        put("secure", cookie.secure)
        put("httpOnly", cookie.httpOnly)
        put("hostOnly", cookie.hostOnly)
    }

    private fun decodeCookie(obj: JSONObject): Cookie? {
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val value = obj.optString("value")
        val domain = obj.optString("domain").takeIf { it.isNotBlank() } ?: return null
        val path = obj.optString("path").takeIf { it.isNotBlank() } ?: "/"
        val expiresAt = obj.optLong("expiresAt", 0L)
        if (expiresAt <= 0L) return null
        return runCatching {
            Cookie.Builder().apply {
                name(name)
                value(value)
                expiresAt(expiresAt)
                if (obj.optBoolean("hostOnly", false)) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (obj.optBoolean("secure", false)) secure()
                if (obj.optBoolean("httpOnly", false)) httpOnly()
            }.build()
        }.getOrNull()
    }

    private fun cookieKey(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"

    companion object {
        const val USER_AGENT = "osu!"
        const val DEFAULT_OSU_URL = "https://osu.ppy.sh/home"
        const val DEFAULT_CHALLENGE_TIMEOUT_MS = 120_000L

        private const val RECENT_VERIFICATION_GRACE_MS = 5_000L
        private const val WEBVIEW_COOKIE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private const val PREFS_NAME = "osu_cookies_prefs"
        private const val KEY_COOKIES_JSON = "cookies_json"

        fun isOsuHost(host: String): Boolean {
            return host.equals("osu.ppy.sh", ignoreCase = true) ||
                host.endsWith(".ppy.sh", ignoreCase = true)
        }

        fun isOAuthEndpoint(urlOrPath: String): Boolean {
            return urlOrPath.contains("/oauth/token", ignoreCase = true) ||
                urlOrPath.contains("/oauth/authorize", ignoreCase = true)
        }

        fun isCookieStrippedEndpoint(path: String): Boolean {
            return path.contains("/oauth/token", ignoreCase = true) ||
                path.startsWith("/api/v2/", ignoreCase = true) ||
                path.equals("/api/v2", ignoreCase = true)
        }

        fun createCompliantHeadersInterceptor(
            bearerTokenProvider: (() -> String?)? = null
        ): Interceptor = Interceptor { chain ->
            val original = chain.request()
            val isOsu = isOsuHost(original.url.host)
            val path = original.url.encodedPath
            val lowerPath = path.lowercase()
            val isBinaryOrBeatmapFile = lowerPath.startsWith("/osu/") ||
                lowerPath.endsWith(".osu") ||
                lowerPath.endsWith(".mp3") ||
                lowerPath.endsWith(".jpg") ||
                lowerPath.endsWith(".jpeg") ||
                lowerPath.endsWith(".png") ||
                lowerPath.endsWith(".webp")

            val builder = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .removeHeader("Origin")
                .removeHeader("Referer")
                .removeHeader("Accept-Language")
                .removeHeader("Upgrade-Insecure-Requests")

            for (headerName in original.headers.names()) {
                if (headerName.startsWith("Sec-Fetch-", ignoreCase = true) ||
                    headerName.startsWith("sec-ch-ua", ignoreCase = true)
                ) {
                    builder.removeHeader(headerName)
                }
            }

            if (!isBinaryOrBeatmapFile) {
                builder.header("Accept", "application/json")
            }

            if (isOsu && isCookieStrippedEndpoint(path)) {
                builder.removeHeader("Cookie")
            }

            if (isOsu && lowerPath.startsWith("/api/v2/")) {
                val existingAuth = original.header("Authorization")
                if (existingAuth.isNullOrBlank()) {
                    val rawToken = bearerTokenProvider?.invoke()?.trim().orEmpty()
                    if (rawToken.isNotBlank()) {
                        builder.header("Authorization", "Bearer ${rawToken.removePrefix("Bearer ").trim()}")
                    }
                } else if (!existingAuth.startsWith("Bearer ", ignoreCase = true)) {
                    builder.header("Authorization", "Bearer ${existingAuth.trim()}")
                }
            }

            chain.proceed(builder.build())
        }

        fun extractCfClearance(rawCookieHeader: String?): String? {
            if (rawCookieHeader.isNullOrBlank()) return null
            return rawCookieHeader.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("cf_clearance=", ignoreCase = true) }
                ?.substringAfter('=', "")
                ?.takeIf { it.isNotBlank() }
        }
    }
}
