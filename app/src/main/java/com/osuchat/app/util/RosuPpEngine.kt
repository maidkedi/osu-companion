package com.osuchat.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.annotations.SerializedName
import com.osuchat.app.domain.model.BeatmapMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

@Singleton
class RosuPpEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    data class RawRosuResult(
        @SerializedName("starRating") val starRating: Double,
        @SerializedName("pp") val pp: Double,
        @SerializedName("ar") val ar: Double,
        @SerializedName("cs") val cs: Double,
        @SerializedName("od") val od: Double,
        @SerializedName("hp") val hp: Double,
        @SerializedName("clockRate") val clockRate: Double,
        @SerializedName("maxCombo") val maxCombo: Int? = null
    )

    companion object {
        private const val TAG = "RosuPpEngine"
        private const val VIRTUAL_HOST = "rosu-pp.local"
        private const val BRIDGE_URL = "https://$VIRTUAL_HOST/bridge.html"
        private const val OSU_CACHE_DIR = "osu_cache"
        private const val MAX_DISK_CACHE_BYTES = 50L * 1024L * 1024L
        private const val MAX_OSU_FILE_BYTES = 16L * 1024L * 1024L
        private const val BRIDGE_TIMEOUT_MS = 5_000L
        private const val EXEC_TIMEOUT_MS = 5_000L
        private const val FAILED_DOWNLOAD_COOLDOWN_MS = 30_000L
        private const val MAX_RENDER_CRASH_RETRIES = 1

        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]+$")
        private val MOD_TOKEN_PATTERN = Regex("^[A-Z0-9]{1,8}$")

        private val MOD_BIT_MAP = mapOf(
            "NF" to 1,
            "EZ" to 2,
            "TD" to 4,
            "HD" to 8,
            "HR" to 16,
            "SD" to 32,
            "DT" to 64,
            "RX" to 128,
            "HT" to 256,
            "NC" to 576,
            "FL" to 1024,
            "SO" to 4096,
            "PF" to 16416
        )

        fun modsToBitmask(mods: Iterable<String>): Int {
            var mask = 0
            for (token in mods) {
                val bit = MOD_BIT_MAP[token.trim().uppercase()] ?: continue
                mask = mask or bit
            }
            return mask
        }

        internal fun sanitizeModTokens(mods: Set<String>): Set<String> {
            return mods.asSequence()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() && MOD_TOKEN_PATTERN.matches(it) }
                .toSet()
        }

        internal fun sanitizeModsForJs(mods: Set<String>): String {
            val clean = sanitizeModTokens(mods)
            if (clean.isEmpty()) return ""
            val hasHr = "HR" in clean
            return clean.filter { mod ->
                mod != "NM" && mod != "NONE" && mod != "CL" && !(mod == "EZ" && hasHr)
            }.sorted().joinToString("")
        }

        internal fun sanitizeModBitmask(bitmask: Int): Int = bitmask.coerceAtLeast(0)

        internal fun sanitizeClockRate(clockRate: Double?): Double? =
            clockRate?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.5, 2.0)

        internal fun escapeJsStringLiteral(value: String): String {
            val sb = StringBuilder(value.length + 12)
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\'' -> sb.append("\\u0027")
                    '<' -> sb.append("\\u003c")
                    '>' -> sb.append("\\u003e")
                    '&' -> sb.append("\\u0026")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\u2028' -> sb.append("\\u2028")
                    '\u2029' -> sb.append("\\u2029")
                    else -> {
                        if (ch.code < 0x20) {
                            sb.append("\\u%04x".format(java.util.Locale.US, ch.code))
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            sb.append('"')
            return sb.toString()
        }

        internal fun buildCalculateScript(
            requestId: Long,
            osuBase64: String,
            modeArg: String,
            modsArg: String,
            accuracyPercent: Double,
            nMisses: Int = 0,
            combo: Int? = null,
            clockRate: Double? = null,
            isLazer: Boolean = false
        ): String {
            require(requestId >= 0L) { "Invalid requestId" }
            require(BASE64_PATTERN.matches(osuBase64)) { "Invalid base64 payload" }
            val safeAcc = if (accuracyPercent.isFinite()) accuracyPercent.coerceIn(0.0, 100.0) else 100.0
            val safeMisses = nMisses.coerceAtLeast(0)
            val safeCombo = combo?.takeIf { it > 0 }?.toString() ?: "null"
            val safeRate = sanitizeClockRate(clockRate)?.toString() ?: "null"
            val escapedOsu = escapeJsStringLiteral(osuBase64)
            val escapedMode = escapeJsStringLiteral(modeArg)
            val escapedMods = escapeJsStringLiteral(modsArg)
            return "window.calculateRosu($requestId, $escapedOsu, $escapedMode, $escapedMods, $safeAcc, $safeMisses, $safeCombo, $safeRate, $isLazer);"
        }

        internal fun buildCalculateScript(
            requestId: Long,
            osuBase64: String,
            modeArg: String,
            modBitmask: Int,
            accuracyPercent: Double,
            nMisses: Int = 0,
            combo: Int? = null,
            clockRate: Double? = null,
            isLazer: Boolean = false
        ): String {
            require(requestId >= 0L) { "Invalid requestId" }
            require(BASE64_PATTERN.matches(osuBase64)) { "Invalid base64 payload" }
            val safeAcc = if (accuracyPercent.isFinite()) accuracyPercent.coerceIn(0.0, 100.0) else 100.0
            val safeBitmask = sanitizeModBitmask(modBitmask)
            val safeMisses = nMisses.coerceAtLeast(0)
            val safeCombo = combo?.takeIf { it > 0 }?.toString() ?: "null"
            val safeRate = sanitizeClockRate(clockRate)?.toString() ?: "null"
            val escapedOsu = escapeJsStringLiteral(osuBase64)
            val escapedMode = escapeJsStringLiteral(modeArg)
            return "window.calculateRosu($requestId, $escapedOsu, $escapedMode, $safeBitmask, $safeAcc, $safeMisses, $safeCombo, $safeRate, $isLazer);"
        }

        internal fun evictDiskCacheLocked(
            cacheDir: File,
            maxBytes: Long = MAX_DISK_CACHE_BYTES,
            protectedFile: File? = null
        ) {
            if (!cacheDir.exists() || !cacheDir.isDirectory) return
            val entries = cacheDir.listFiles() ?: return

            // Remove orphaned temp files from interrupted writes while holding diskCacheLock.
            val osuFiles = mutableListOf<File>()
            for (file in entries) {
                if (!file.isFile) continue
                if (file.name.endsWith(".tmp")) {
                    runCatching { file.delete() }
                } else {
                    osuFiles.add(file)
                }
            }

            var totalBytes = osuFiles.sumOf { it.length() }
            if (totalBytes <= maxBytes) return

            val protectedPath = protectedFile?.absolutePath
            val sorted = osuFiles.sortedWith(
                compareBy<File> { it.absolutePath == protectedPath }
                    .thenBy { it.lastModified() }
            )
            for (file in sorted) {
                if (totalBytes <= maxBytes) break
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    totalBytes -= size
                }
            }
        }
    }

    private class WebViewRenderProcessGoneException :
        IllegalStateException("WebView render process gone")

    private val calculationMutex = Mutex()
    private val diskCacheLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val memoryCache = LruCache<Long, String>(8)
    private val inFlightOsuDownloads = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val failedDownloadUntilMs = ConcurrentHashMap<Long, Long>()
    private val requestSeq = AtomicLong(0L)
    private val bridgeGeneration = AtomicLong(0L)

    @Volatile
    private var diskCachePrunedOnce: Boolean = false

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Accessed on Main thread only.
    private var webView: WebView? = null

    @Volatile
    private var isBridgeReady: Boolean = false

    @Volatile
    private var bridgeReadyDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingRequestId: Long = -1L

    @Volatile
    private var pendingResultDeferred: CompletableDeferred<String>? = null

    suspend fun calculate(
        meta: BeatmapMetadata,
        mods: Set<String>,
        accuracyPercent: Double,
        nMisses: Int = 0,
        combo: Int? = null,
        clockRate: Double? = null,
        isLazer: Boolean = true
    ): SimulatedBeatmapStats {
        val safeAcc = if (accuracyPercent.isFinite()) accuracyPercent.coerceIn(0.0, 100.0) else 100.0
        val safeMisses = nMisses.coerceAtLeast(0)
        val safeCombo = combo?.takeIf { it > 0 }
        val safeRate = sanitizeClockRate(clockRate)
        val fallback = OsuBeatmapCalculator.simulate(
            meta = meta,
            mods = mods,
            accuracyPercent = safeAcc,
            nMisses = safeMisses,
            combo = safeCombo,
            clockRate = safeRate,
            isLazer = isLazer
        )
        if (meta.beatmapId <= 0L) return fallback

        val normMods = sanitizeModTokens(mods)
        val raw = calculateRosuPp(
            beatmapId = meta.beatmapId,
            mode = meta.mode,
            mods = normMods,
            accuracyPercent = safeAcc,
            nMisses = safeMisses,
            combo = safeCombo,
            clockRate = safeRate,
            isLazer = isLazer
        ) ?: return fallback

        val resolvedRate = raw.clockRate.takeIf { it.isFinite() && it > 0.0 } ?: fallback.clockRate
        val bpm = if (meta.bpm.isFinite() && meta.bpm > 0.0) meta.bpm * resolvedRate else fallback.bpm
        val totalLen = if (meta.totalLengthSeconds > 0) {
            (meta.totalLengthSeconds / resolvedRate).roundToInt().coerceAtLeast(1)
        } else {
            fallback.totalLengthSeconds
        }
        val drainLen = if (meta.drainLengthSeconds > 0) {
            (meta.drainLengthSeconds / resolvedRate).roundToInt().coerceAtLeast(1)
        } else {
            totalLen
        }
        val resolvedMaxCombo = raw.maxCombo ?: meta.maxCombo

        return SimulatedBeatmapStats(
            starRating = raw.starRating.coerceAtLeast(0.0),
            cs = raw.cs.takeIf { it.isFinite() } ?: fallback.cs,
            ar = raw.ar.takeIf { it.isFinite() } ?: fallback.ar,
            od = raw.od.takeIf { it.isFinite() } ?: fallback.od,
            hp = raw.hp.takeIf { it.isFinite() } ?: fallback.hp,
            bpm = bpm,
            totalLengthSeconds = totalLen,
            drainLengthSeconds = drainLen,
            estimatedFcPp = raw.pp.coerceAtLeast(0.0),
            activeMods = normMods,
            accuracyPercent = safeAcc,
            isExactRosuPp = true,
            nMisses = safeMisses,
            combo = safeCombo?.let { c -> resolvedMaxCombo?.let { c.coerceIn(1, it) } ?: c },
            maxCombo = resolvedMaxCombo,
            clockRate = resolvedRate,
            isLazer = isLazer
        )
    }

    suspend fun calculateRosuPp(
        beatmapId: Long,
        mode: String,
        mods: Set<String>,
        accuracyPercent: Double,
        nMisses: Int = 0,
        combo: Int? = null,
        clockRate: Double? = null,
        isLazer: Boolean = false
    ): RawRosuResult? {
        if (beatmapId <= 0L) return null
        val modsArg = sanitizeModsForJs(mods)
        return executeRosuCalculation(beatmapId) { reqId, osuBase64 ->
            buildCalculateScript(
                requestId = reqId,
                osuBase64 = osuBase64,
                modeArg = normalizeMode(mode),
                modsArg = modsArg,
                accuracyPercent = accuracyPercent,
                nMisses = nMisses,
                combo = combo,
                clockRate = clockRate,
                isLazer = isLazer
            )
        }
    }

    suspend fun calculateRosuPp(
        beatmapId: Long,
        mode: String,
        modsBitmask: Int,
        accuracyPercent: Double,
        nMisses: Int = 0,
        combo: Int? = null,
        clockRate: Double? = null,
        isLazer: Boolean = false
    ): RawRosuResult? {
        if (beatmapId <= 0L) return null
        return executeRosuCalculation(beatmapId) { reqId, osuBase64 ->
            buildCalculateScript(
                requestId = reqId,
                osuBase64 = osuBase64,
                modeArg = normalizeMode(mode),
                modBitmask = modsBitmask,
                accuracyPercent = accuracyPercent,
                nMisses = nMisses,
                combo = combo,
                clockRate = clockRate,
                isLazer = isLazer
            )
        }
    }

    private suspend fun executeRosuCalculation(
        beatmapId: Long,
        scriptBuilder: (reqId: Long, osuBase64: String) -> String
    ): RawRosuResult? {
        return try {
            val osuContent = getOrDownloadOsu(beatmapId) ?: return null
            val osuBase64 = withContext(Dispatchers.Default) {
                Base64.encodeToString(osuContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            if (!BASE64_PATTERN.matches(osuBase64)) return null

            val rawJson = calculationMutex.withLock {
                var resolvedJson: String? = null
                for (attempt in 0..MAX_RENDER_CRASH_RETRIES) {
                    if (!ensureBridgeReady()) {
                        if (attempt < MAX_RENDER_CRASH_RETRIES) continue
                        break
                    }
                    val reqId = requestSeq.incrementAndGet()
                    val deferred = CompletableDeferred<String>()
                    pendingRequestId = reqId
                    pendingResultDeferred = deferred
                    var retryAfterCrash = false

                    val attemptJson = try {
                        val script = scriptBuilder(reqId, osuBase64)
                        withContext(Dispatchers.Main) {
                            val view = webView
                            if (view == null || !isBridgeReady) {
                                deferred.completeExceptionally(WebViewRenderProcessGoneException())
                            } else {
                                view.evaluateJavascript(script, null)
                            }
                        }
                        val awaited = withTimeoutOrNull(EXEC_TIMEOUT_MS) { deferred.await() }
                        if (awaited == null) {
                            Log.w(
                                TAG,
                                "rosu-pp calculation timed out after ${EXEC_TIMEOUT_MS}ms for beatmap $beatmapId; resetting WebView"
                            )
                            withContext(Dispatchers.Main) { destroyWebViewOnMain() }
                        }
                        awaited
                    } catch (_: WebViewRenderProcessGoneException) {
                        Log.w(
                            TAG,
                            "rosu-pp WebView renderer crashed during beatmap $beatmapId calculation (attempt ${attempt + 1})"
                        )
                        withContext(Dispatchers.Main) { destroyWebViewOnMain() }
                        retryAfterCrash = attempt < MAX_RENDER_CRASH_RETRIES
                        null
                    } finally {
                        if (pendingRequestId == reqId) {
                            pendingResultDeferred = null
                            pendingRequestId = -1L
                        }
                    }

                    if (attemptJson != null) {
                        resolvedJson = attemptJson
                        break
                    }
                    if (!retryAfterCrash) break
                }
                resolvedJson
            } ?: return null

            parseRawResult(rawJson, beatmapId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "rosu-pp calculation failed for beatmap $beatmapId: ${t.message}")
            null
        }
    }

    /**
     * Releases the headless WebView and cancels any in-flight evaluation.
     * Safe to call from any thread; subsequent [calculate] calls will lazily reinitialize it.
     */
    fun destroy() {
        isBridgeReady = false
        bridgeGeneration.incrementAndGet()
        bridgeReadyDeferred?.complete(false)
        bridgeReadyDeferred = null
        pendingResultDeferred?.cancel(CancellationException("RosuPpEngine destroyed"))
        pendingResultDeferred = null
        pendingRequestId = -1L

        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyWebViewOnMain()
        } else {
            mainHandler.post { destroyWebViewOnMain() }
        }
    }

    private fun destroyWebViewOnMain() {
        isBridgeReady = false
        bridgeGeneration.incrementAndGet()
        val current = webView ?: return
        webView = null
        runCatching {
            current.stopLoading()
            current.removeJavascriptInterface("AndroidRosuBridge")
            current.webViewClient = WebViewClient()
            current.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureBridgeReady(): Boolean {
        if (isBridgeReady && webView != null) return true

        val readyDeferred = CompletableDeferred<Boolean>()
        bridgeReadyDeferred = readyDeferred

        val created = withContext(Dispatchers.Main) {
            destroyWebViewOnMain()
            val instanceGen = bridgeGeneration.incrementAndGet()
            val newView = runCatching {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    addJavascriptInterface(RosuJsBridge(instanceGen), "AndroidRosuBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.scheme != "https" || url.host != VIRTUAL_HOST) {
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    404,
                                    "Not Found",
                                    emptyMap(),
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            val assetName = url.lastPathSegment ?: return null
                            return openAssetResponse(assetName)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true && bridgeGeneration.get() == instanceGen) {
                                isBridgeReady = false
                                readyDeferred.complete(false)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true && bridgeGeneration.get() == instanceGen) {
                                isBridgeReady = false
                                readyDeferred.complete(false)
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            val didCrash = detail?.didCrash() ?: true
                            Log.e(TAG, "rosu-pp WebView render process gone (didCrash=$didCrash)")
                            if (bridgeGeneration.get() == instanceGen) {
                                isBridgeReady = false
                                bridgeGeneration.incrementAndGet()
                                if (view === webView) {
                                    webView = null
                                }
                                readyDeferred.complete(false)
                                bridgeReadyDeferred = null
                                pendingResultDeferred?.completeExceptionally(
                                    WebViewRenderProcessGoneException()
                                )
                                pendingResultDeferred = null
                                pendingRequestId = -1L
                            }
                            runCatching {
                                view?.stopLoading()
                                view?.removeJavascriptInterface("AndroidRosuBridge")
                                view?.webViewClient = WebViewClient()
                                view?.destroy()
                            }
                            return true
                        }
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed to create rosu-pp WebView: ${err.message}")
            }.getOrNull()

            if (newView == null) {
                readyDeferred.complete(false)
                false
            } else {
                webView = newView
                newView.loadUrl(BRIDGE_URL)
                true
            }
        }

        if (!created) {
            bridgeReadyDeferred = null
            return false
        }

        val ready = withTimeoutOrNull(BRIDGE_TIMEOUT_MS) { readyDeferred.await() } == true
        if (bridgeReadyDeferred === readyDeferred) {
            bridgeReadyDeferred = null
        }
        if (!ready) {
            Log.w(TAG, "rosu-pp WebView bridge initialization timed out or failed")
            withContext(Dispatchers.Main) { destroyWebViewOnMain() }
        }
        return ready
    }

    private fun openAssetResponse(assetName: String): WebResourceResponse {
        val (mime, encoding) = when (assetName) {
            "bridge.html" -> "text/html" to "utf-8"
            "rosu_pp_js.js" -> "application/javascript" to "utf-8"
            "rosu_pp_js_bg.wasm" -> "application/wasm" to null
            else -> return WebResourceResponse(
                "text/plain",
                "utf-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
        return try {
            val stream = context.assets.open("rosu_pp/$assetName")
            WebResourceResponse(
                mime,
                encoding,
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                stream
            )
        } catch (e: IOException) {
            Log.e(TAG, "Missing asset rosu_pp/$assetName", e)
            WebResourceResponse(
                "text/plain",
                "utf-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    suspend fun getOrDownloadOsu(beatmapId: Long): String? {
        if (beatmapId <= 0L) return null
        memoryCache.get(beatmapId)?.let { return it }

        val cacheDir = File(context.filesDir, OSU_CACHE_DIR)
        val cachedFile = File(cacheDir, "$beatmapId.osu")
        val diskText = withContext(Dispatchers.IO) {
            synchronized(diskCacheLock) {
                if (!diskCachePrunedOnce) {
                    evictDiskCacheLocked(cacheDir, MAX_DISK_CACHE_BYTES)
                    diskCachePrunedOnce = true
                }
                if (!cachedFile.exists() || !cachedFile.isFile) return@synchronized null
                val text = runCatching { cachedFile.readText(Charsets.UTF_8) }.getOrNull()
                if (text != null && isValidOsuContent(text)) {
                    runCatching { cachedFile.setLastModified(System.currentTimeMillis()) }
                    text
                } else {
                    runCatching { cachedFile.delete() }
                    null
                }
            }
        }
        if (diskText != null) {
            memoryCache.put(beatmapId, diskText)
            return diskText
        }

        val cooldownUntil = failedDownloadUntilMs[beatmapId] ?: 0L
        if (System.currentTimeMillis() < cooldownUntil) return null

        val newDeferred = CompletableDeferred<String?>()
        val existing = inFlightOsuDownloads.putIfAbsent(beatmapId, newDeferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val downloaded = downloadOsuText(beatmapId)
            if (downloaded == null) {
                failedDownloadUntilMs[beatmapId] = System.currentTimeMillis() + FAILED_DOWNLOAD_COOLDOWN_MS
            } else {
                failedDownloadUntilMs.remove(beatmapId)
                memoryCache.put(beatmapId, downloaded)
                withContext(Dispatchers.IO) {
                    synchronized(diskCacheLock) {
                        runCatching {
                            if (!cacheDir.exists()) cacheDir.mkdirs()
                            val tmp = File(cacheDir, "$beatmapId.osu.tmp")
                            tmp.writeText(downloaded, Charsets.UTF_8)
                            if (!tmp.renameTo(cachedFile)) {
                                cachedFile.writeText(downloaded, Charsets.UTF_8)
                                tmp.delete()
                            }
                            cachedFile.setLastModified(System.currentTimeMillis())
                            evictDiskCacheLocked(cacheDir, MAX_DISK_CACHE_BYTES, protectedFile = cachedFile)
                            diskCachePrunedOnce = true
                        }
                    }
                }
            }
            newDeferred.complete(downloaded)
            downloaded
        } catch (t: Throwable) {
            newDeferred.complete(null)
            throw t
        } finally {
            inFlightOsuDownloads.remove(beatmapId, newDeferred)
        }
    }

    private suspend fun downloadOsuText(beatmapId: Long): String? = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://osu.ppy.sh/osu/$beatmapId",
            "https://catboy.best/osu/$beatmapId"
        )
        for (url in urls) {
            val result = fetchOsuTextFromUrl(url)
            if (result != null) return@withContext result
        }
        null
    }

    private suspend fun fetchOsuTextFromUrl(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/plain, */*")
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = downloadClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (cont.isActive) cont.resume(null)
                            return
                        }
                        val body = res.body
                        if (body == null || body.contentLength() > MAX_OSU_FILE_BYTES) {
                            if (cont.isActive) cont.resume(null)
                            return
                        }
                        val raw = runCatching {
                            readBoundedUtf8(body.byteStream(), MAX_OSU_FILE_BYTES)
                        }.getOrNull()
                        val valid = raw?.takeIf { isValidOsuContent(it) }
                        if (cont.isActive) cont.resume(valid)
                    }
                }
            })
        }
    }

    private fun readBoundedUtf8(input: InputStream, maxBytes: Long): String? {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isValidOsuContent(content: String): Boolean {
        return content.length > 64 &&
            content.contains("osu file format", ignoreCase = true) &&
            content.contains("[HitObjects]", ignoreCase = true)
    }

    private fun normalizeMode(mode: String): String {
        return when (mode.trim().lowercase()) {
            "taiko", "1" -> "taiko"
            "fruits", "catch", "ctb", "2" -> "fruits"
            "mania", "3" -> "mania"
            else -> "osu"
        }
    }

    private fun parseRawResult(rawJson: String, beatmapId: Long): RawRosuResult? {
        val obj = JSONObject(rawJson)
        if (!obj.optBoolean("ok", false)) {
            val err = obj.optString("error", "unknown")
            Log.w(TAG, "rosu-pp returned error for beatmap $beatmapId: $err")
            return null
        }

        val starRating = obj.optDouble("starRating", Double.NaN)
        val pp = obj.optDouble("pp", Double.NaN)
        if (!starRating.isFinite() || !pp.isFinite()) return null

        val clockRate = obj.optDouble("clockRate", 1.0)
            .takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val ar = obj.optDouble("ar", Double.NaN)
        val cs = obj.optDouble("cs", Double.NaN)
        val od = obj.optDouble("od", Double.NaN)
        val hp = obj.optDouble("hp", Double.NaN)
        val rawMaxCombo = obj.optInt("maxCombo", -1).takeIf { it > 0 }

        return RawRosuResult(
            starRating = starRating.coerceAtLeast(0.0),
            pp = pp.coerceAtLeast(0.0),
            ar = ar,
            cs = cs,
            od = od,
            hp = hp,
            clockRate = clockRate,
            maxCombo = rawMaxCombo
        )
    }

    private inner class RosuJsBridge(
        private val instanceGeneration: Long
    ) {
        @JavascriptInterface
        fun onReady(ok: Boolean, error: String?) {
            if (bridgeGeneration.get() != instanceGeneration) return
            isBridgeReady = ok
            if (!ok && !error.isNullOrBlank()) {
                Log.w(TAG, "rosu-pp bridge init error: $error")
            }
            bridgeReadyDeferred?.complete(ok)
        }

        @JavascriptInterface
        fun onResult(json: String) {
            if (bridgeGeneration.get() != instanceGeneration) return
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
            val reqId = obj.optLong("requestId", -1L)
            if (reqId == pendingRequestId) {
                pendingResultDeferred?.complete(json)
            }
        }
    }
}
