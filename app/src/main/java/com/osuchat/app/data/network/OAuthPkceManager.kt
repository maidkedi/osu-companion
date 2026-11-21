package com.osuchat.app.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OAuthCallbackResult {
    data class Code(val code: String, val state: String?) : OAuthCallbackResult
    data class Error(val error: String, val message: String) : OAuthCallbackResult
    data object LoopbackAck : OAuthCallbackResult
    data class Invalid(val reason: String) : OAuthCallbackResult
}

@Singleton
class OAuthPkceManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val codeLock = Any()
    private val consumedCodes: MutableSet<String> = mutableSetOf()
    private val deliveredCallbackCodes: MutableSet<String> = mutableSetOf()
    private val exchangeInFlight = AtomicBoolean(false)

    @Volatile
    private var currentExpectedState: String? = null

    fun prepareNewLoginAttempt(): String {
        stopLoopbackServer()
        synchronized(codeLock) {
            exchangeInFlight.set(false)
        }
        val state = UUID.randomUUID().toString()
        currentExpectedState = state
        return state
    }

    fun cancelActiveLoginAttempt() {
        stopLoopbackServer()
        synchronized(codeLock) {
            exchangeInFlight.set(false)
        }
        currentExpectedState = null
    }

    fun getCurrentExpectedState(): String? = currentExpectedState

    fun isExpectedStateValid(callbackState: String?): Boolean {
        val expected = currentExpectedState
        return expected.isNullOrBlank() || callbackState?.trim() == expected
    }

    fun tryClaimCodeForExchange(code: String): Boolean {
        val clean = code.trim()
        if (clean.isEmpty()) return false
        synchronized(codeLock) {
            if (exchangeInFlight.get()) return false
            if (!consumedCodes.add(clean)) return false
            exchangeInFlight.set(true)
            return true
        }
    }

    fun isCodeConsumed(code: String): Boolean {
        val clean = code.trim()
        if (clean.isEmpty()) return false
        synchronized(codeLock) {
            return consumedCodes.contains(clean)
        }
    }

    fun isExchangeInFlight(): Boolean = exchangeInFlight.get()

    fun markExchangeFinished() {
        synchronized(codeLock) {
            exchangeInFlight.set(false)
        }
    }

    fun tryClaimCallbackCode(code: String): Boolean {
        val clean = code.trim()
        if (clean.isEmpty()) return false
        synchronized(codeLock) {
            if (consumedCodes.contains(clean)) return false
            return deliveredCallbackCodes.add(clean)
        }
    }

    fun buildAuthorizeUrl(
        clientId: String = OAuthConfig.CLIENT_ID,
        state: String? = currentExpectedState,
        redirectUri: String = OAuthConfig.REDIRECT_URI
    ): String {
        val cleanClientId = urlEncode(clientId.trim())
        val encodedRedirectUri = urlEncode(redirectUri.trim())
        val scopeParam = urlEncode(OAuthConfig.SCOPES.joinToString(" "))
        val effectiveState = state?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { currentExpectedState = it }
        return "${OAuthConfig.AUTHORIZE_URL}?client_id=$cleanClientId" +
            "&redirect_uri=$encodedRedirectUri" +
            "&response_type=code" +
            "&scope=$scopeParam" +
            "&state=${urlEncode(effectiveState)}"
    }

    fun buildAuthorizeUri(
        clientId: String = OAuthConfig.CLIENT_ID,
        state: String? = currentExpectedState,
        redirectUri: String = OAuthConfig.REDIRECT_URI
    ): Uri {
        return Uri.parse(buildAuthorizeUrl(clientId, state, redirectUri))
    }

    fun prepareAuthorizeUriForInAppWebView(
        clientId: String = OAuthConfig.CLIENT_ID,
        redirectUri: String = OAuthConfig.REDIRECT_URI
    ): Uri {
        val state = prepareNewLoginAttempt()
        return buildAuthorizeUri(clientId = clientId, state = state, redirectUri = redirectUri)
    }

    fun isOAuthCallbackUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        if (scheme == OAuthConfig.CUSTOM_SCHEME &&
            host == OAuthConfig.CUSTOM_HOST &&
            (path.isEmpty() || path.startsWith(OAuthConfig.CUSTOM_PATH_PREFIX))
        ) {
            return true
        }
        if ((scheme == "http" || scheme == "https") &&
            (host == OAuthConfig.LOOPBACK_HOST || host == "localhost") &&
            path.startsWith(OAuthConfig.CUSTOM_PATH_PREFIX)
        ) {
            return true
        }
        return false
    }

    fun validateAndExtractCode(uri: Uri?): OAuthCallbackResult {
        if (uri == null || !isOAuthCallbackUri(uri)) {
            return OAuthCallbackResult.Invalid("Not an osu! OAuth callback URI")
        }
        if (uri.getQueryParameter("loopback") == "1") {
            return OAuthCallbackResult.LoopbackAck
        }

        val errorParam = uri.getQueryParameter("error")?.trim()
        if (!errorParam.isNullOrBlank()) {
            val detail = uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("message")
                ?: uri.getQueryParameter("hint")
                ?: errorParam
            return OAuthCallbackResult.Error(error = errorParam, message = detail)
        }

        val callbackState = uri.getQueryParameter("state")?.trim()
        if (!isExpectedStateValid(callbackState)) {
            return OAuthCallbackResult.Error(
                error = "state_mismatch",
                message = "OAuth state mismatch. Please try signing in again."
            )
        }

        val code = uri.getQueryParameter("code")?.trim()
        if (code.isNullOrBlank()) {
            return OAuthCallbackResult.Invalid("Missing authorization code")
        }
        return OAuthCallbackResult.Code(code = code, state = callbackState)
    }

    fun startLoopbackServer(
        expectedState: String? = currentExpectedState,
        onResult: suspend (Result<String>) -> Unit
    ) {
        stopLoopbackServer()
        val effectiveExpectedState = expectedState?.takeIf { it.isNotBlank() } ?: currentExpectedState

        val boundSocket = ServerSocket().apply {
            reuseAddress = true
            soTimeout = SERVER_TIMEOUT_MS
            bind(
                InetSocketAddress(
                    InetAddress.getByName(OAuthConfig.LOOPBACK_HOST),
                    OAuthConfig.LOOPBACK_PORT
                )
            )
        }
        serverSocket = boundSocket

        serverJob = scope.launch {
            var delivered = false
            try {
                while (isActive && !delivered) {
                    val client = try {
                        boundSocket.accept()
                    } catch (_: SocketTimeoutException) {
                        delivered = true
                        onResult(
                            Result.failure(
                                IllegalStateException("OAuth sign-in timed out waiting for loopback callback.")
                            )
                        )
                        break
                    } catch (_: SocketException) {
                        // Socket closed externally via stopLoopbackServer()
                        break
                    }

                    val outcome = runCatching {
                        handleLoopbackClient(client, effectiveExpectedState)
                    }.getOrNull()

                    if (outcome != null) {
                        delivered = true
                        closeServerSocketQuietly()
                        onResult(outcome)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Loopback OAuth server failed", e)
                if (!delivered) {
                    onResult(Result.failure(e))
                }
            } finally {
                closeServerSocketQuietly()
            }
        }
    }

    private fun handleLoopbackClient(
        client: Socket,
        expectedState: String?
    ): Result<String>? {
        client.use { sock ->
            sock.soTimeout = CLIENT_READ_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            while (true) {
                val headerLine = reader.readLine() ?: break
                if (headerLine.isEmpty()) break
            }

            val requestParts = requestLine.split(" ")
            val method = requestParts.getOrNull(0).orEmpty()
            val rawTarget = requestParts.getOrNull(1).orEmpty()
            val pathOnly = rawTarget.substringBefore("?")
            if (!method.equals("GET", ignoreCase = true) || !pathOnly.startsWith("/callback")) {
                writeNoContent(sock)
                return null
            }

            val query = rawTarget.substringAfter("?", "")
            val params = parseQueryParams(query)
            val code = params["code"]?.trim()
            val error = params["error"]?.trim()
            val errorDescription = params["error_description"] ?: params["message"] ?: params["hint"]
            val callbackState = params["state"]?.trim()

            if (code.isNullOrBlank() && error.isNullOrBlank()) {
                writeNoContent(sock)
                return null
            }

            if (!expectedState.isNullOrBlank() && callbackState != expectedState) {
                writeNoContent(sock)
                return null
            }

            val isSuccess = !code.isNullOrBlank() && error.isNullOrBlank()
            val html = buildCallbackHtml(
                isSuccess = isSuccess,
                errorDetail = errorDescription ?: error
            )
            val bodyBytes = html.toByteArray(Charsets.UTF_8)
            val httpResponse = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/html; charset=utf-8\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }
            sock.getOutputStream().write(httpResponse.toByteArray(Charsets.UTF_8))
            sock.getOutputStream().write(bodyBytes)
            sock.getOutputStream().flush()

            return if (isSuccess) {
                if (!tryClaimCallbackCode(code!!)) {
                    null
                } else {
                    Result.success(code)
                }
            } else {
                val msg = buildString {
                    if (!error.isNullOrBlank()) append(error)
                    if (!errorDescription.isNullOrBlank()) {
                        if (isNotEmpty()) append(": ")
                        append(errorDescription)
                    }
                    if (isEmpty()) append("Missing OAuth authorization code in loopback callback.")
                }
                Result.failure(IllegalStateException(msg))
            }
        }
    }

    private fun writeNoContent(sock: Socket) {
        val noContent = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
        sock.getOutputStream().write(noContent.toByteArray(Charsets.UTF_8))
        sock.getOutputStream().flush()
    }

    fun stopLoopbackServer() {
        serverJob?.cancel()
        serverJob = null
        closeServerSocketQuietly()
    }

    fun launchCustomTab(context: Context, uri: Uri) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            customTabsIntent.launchUrl(context, uri)
        }.onFailure {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    private fun closeServerSocketQuietly() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        query.split("&").forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            val rawKey = if (idx >= 0) pair.substring(0, idx) else pair
            val rawVal = if (idx >= 0) pair.substring(idx + 1) else ""
            val key = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrDefault(rawKey)
            val value = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
            result[key] = value
        }
        return result
    }

    private fun buildCallbackHtml(isSuccess: Boolean, errorDetail: String?): String {
        val heading = if (isSuccess) "Login successful, returning to app..." else "osu! Sign-In Failed"
        val sub = if (isSuccess) {
            "You can close this browser tab if it does not return automatically."
        } else {
            (errorDetail ?: "Authorization was cancelled or denied.")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
        val baseDeepLink = OAuthConfig.CUSTOM_SCHEME_REDIRECT_URI
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>OsuCompanion</title>
              <style>
                body { font-family: -apple-system, sans-serif; background: #18141c; color: #f6eff7; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 24px; text-align: center; }
                .card { background: #261f2d; border-radius: 18px; padding: 28px 24px; max-width: 380px; box-shadow: 0 8px 28px rgba(0,0,0,0.45); }
                h2 { margin: 0 0 10px 0; color: ${if (isSuccess) "#ff66aa" else "#ffb4ab"}; }
                p { margin: 0 0 20px 0; color: #d6c8db; line-height: 1.45; }
                a.btn { display: inline-block; padding: 12px 20px; border-radius: 999px; background: #ff66aa; color: #ffffff; text-decoration: none; font-weight: 600; }
              </style>
            </head>
            <body>
              <div class="card">
                <h2>$heading</h2>
                <p>$sub</p>
                <a id="return-btn" class="btn" href="$baseDeepLink">Return to OsuCompanion</a>
              </div>
              <script>
                (function() {
                  var target = "$baseDeepLink" + (window.location.search || "?loopback=1");
                  var btn = document.getElementById("return-btn");
                  if (btn) btn.href = target;
                  window.location.replace(target);
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "OAuthLoopback"
        private const val SERVER_TIMEOUT_MS = 300_000
        private const val CLIENT_READ_TIMEOUT_MS = 5_000
    }
}
