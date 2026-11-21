package com.osuchat.app.ui.screens.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osuchat.app.data.irc.BanchoIrcClient
import com.osuchat.app.data.irc.IrcAuthException
import com.osuchat.app.data.network.NetworkConnectivityMonitor
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

data class LoginUiState(
    val settings: AppSettings = AppSettings(),
    val isIrcBusy: Boolean = false,
    val isOAuthBusy: Boolean = false,
    val isIrcAuthError: Boolean = false,
    val isSessionExpired: Boolean = false,
    val isNetworkOnline: Boolean = true,
    val errorMessage: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE,
    val legacyIrcSettingsUrl: String = BanchoIrcClient.LEGACY_IRC_SETTINGS_URL
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkMonitor: NetworkConnectivityMonitor
) : ViewModel() {

    private val isIrcBusy = MutableStateFlow(false)
    private val isOAuthBusy = MutableStateFlow(false)
    private val localError = MutableStateFlow<String?>(null)
    private val dismissedRepoError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoginUiState> = combine(
        chatRepository.settings,
        isIrcBusy,
        isOAuthBusy,
        localError,
        dismissedRepoError,
        chatRepository.connectionStatus,
        chatRepository.connectionError,
        chatRepository.isIrcAuthError,
        chatRepository.isSessionExpired,
        networkMonitor.isOnline
    ) { values ->
        val settings = values[0] as AppSettings
        val ircBusy = values[1] as Boolean
        val oauthBusy = values[2] as Boolean
        val err = values[3] as String?
        val dismissed = values[4] as String?
        val status = values[5] as ConnectionStatus
        val repoErr = values[6] as String?
        val ircAuthErr = values[7] as Boolean
        val sessionExpired = values[8] as Boolean
        val networkOnline = values[9] as Boolean

        val activeRepoErr = repoErr
            ?.takeIf { it != dismissed }
            ?.let { sanitizeAuthError(null, it, "Authentication failed. Please retry.") }

        LoginUiState(
            settings = settings,
            isIrcBusy = ircBusy,
            isOAuthBusy = oauthBusy || status == ConnectionStatus.CONNECTING,
            isIrcAuthError = ircAuthErr,
            isSessionExpired = sessionExpired,
            isNetworkOnline = networkOnline,
            errorMessage = err ?: activeRepoErr,
            connectionStatus = status
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoginUiState())

    fun startOAuthLogin(context: Context) {
        if (!networkMonitor.isOnline.value) {
            localError.value = "No internet connection. Check Wi-Fi or mobile data and retry."
            return
        }
        localError.value = null
        dismissedRepoError.value = null
        chatRepository.clearConnectionError()
        viewModelScope.launch {
            chatRepository.launchOAuthLogin(context = context)
        }
    }

    fun prepareInAppOAuthUrl(): String? {
        if (!networkMonitor.isOnline.value) {
            localError.value = "No internet connection. Check Wi-Fi or mobile data and retry."
            return null
        }
        localError.value = null
        dismissedRepoError.value = null
        chatRepository.clearConnectionError()
        return chatRepository.prepareInAppOAuthAuthorizeUrl()
    }

    fun isOAuthCallbackUri(uri: Uri?): Boolean {
        return uri != null && chatRepository.isOAuthCallbackUri(uri)
    }

    fun handleInAppOAuthRedirect(uri: Uri) {
        localError.value = null
        dismissedRepoError.value = null
        isOAuthBusy.value = true
        viewModelScope.launch {
            try {
                val result = chatRepository.handleOAuthRedirect(uri)
                if (result.isFailure) {
                    localError.value = sanitizeAuthError(
                        throwable = result.exceptionOrNull(),
                        rawMessage = result.exceptionOrNull()?.message,
                        fallback = "OAuth sign-in failed. Please retry."
                    )
                }
            } finally {
                isOAuthBusy.value = false
            }
        }
    }

    fun onInAppOAuthCancelled() {
        isOAuthBusy.value = false
        chatRepository.cancelOAuthLoginAttempt()
    }

    fun completeIrcOnboarding(ircPassword: String, onSuccess: () -> Unit) {
        if (!networkMonitor.isOnline.value) {
            localError.value = "No internet connection. Check Wi-Fi or mobile data and retry."
            return
        }
        localError.value = null
        dismissedRepoError.value = null
        chatRepository.clearConnectionError()
        isIrcBusy.value = true
        viewModelScope.launch {
            try {
                val result = chatRepository.completeIrcSetupForOAuth(ircPassword)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    localError.value = sanitizeAuthError(
                        throwable = result.exceptionOrNull(),
                        rawMessage = result.exceptionOrNull()?.message,
                        fallback = "Could not connect to Bancho IRC. Check your IRC password and retry."
                    )
                }
            } finally {
                isIrcBusy.value = false
            }
        }
    }

    fun loginWithIrc(username: String, ircPassword: String, onSuccess: () -> Unit) {
        if (!networkMonitor.isOnline.value) {
            localError.value = "No internet connection. Check Wi-Fi or mobile data and retry."
            return
        }
        localError.value = null
        dismissedRepoError.value = null
        chatRepository.clearConnectionError()
        isIrcBusy.value = true
        viewModelScope.launch {
            try {
                val result = chatRepository.loginWithIrc(username, ircPassword)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    localError.value = sanitizeAuthError(
                        throwable = result.exceptionOrNull(),
                        rawMessage = result.exceptionOrNull()?.message,
                        fallback = "Bancho IRC login failed. Verify your username and Legacy IRC password."
                    )
                }
            } finally {
                isIrcBusy.value = false
            }
        }
    }

    fun resetOAuthAccount() {
        localError.value = null
        dismissedRepoError.value = null
        isIrcBusy.value = false
        isOAuthBusy.value = false
        viewModelScope.launch {
            chatRepository.logout()
        }
    }

    fun reportError(message: String) {
        localError.value = message
    }

    fun clearError() {
        localError.value = null
        dismissedRepoError.value = chatRepository.connectionError.value
        chatRepository.clearConnectionError()
    }

    private fun sanitizeAuthError(
        throwable: Throwable?,
        rawMessage: String?,
        fallback: String
    ): String {
        val msg = (rawMessage ?: throwable?.message).orEmpty().trim()
        val lower = msg.lowercase()
        return when {
            throwable is IrcAuthException ||
                lower.contains("464") ||
                lower.contains("err_passwdmismatch") ||
                lower.contains("authentication failed") ||
                lower.contains("bad password") ||
                lower.contains("invalid bancho irc") ->
                "Bancho rejected your IRC password. Copy or reset your Legacy IRC password in osu! account settings."
            throwable is UnknownHostException ||
                lower.contains("unable to resolve host") ||
                lower.contains("no address associated") ||
                lower.contains("no internet") ->
                "No internet connection. Check Wi-Fi or mobile data and retry."
            throwable is SocketTimeoutException ||
                lower.contains("timed out") ||
                lower.contains("timeout") ->
                "Connection to osu! timed out. Please retry."
            throwable is ConnectException ||
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("network is unreachable") ||
                lower.contains("enetunreach") ||
                lower.contains("ehostunreach") ->
                "Could not reach osu! servers. Please retry in a moment."
            throwable is SSLException ||
                lower.contains("ssl") ||
                lower.contains("certificate") ||
                lower.contains("trust anchor") ->
                "Secure connection failed. Check device date/time or network."
            lower.contains("cloudflare") || lower.contains("http 403") ->
                "osu! blocked the automated request. Try In-App Browser Sign-In."
            lower.contains("http 429") || lower.contains("too many") ->
                "Too many sign-in attempts. Wait a moment and retry."
            lower.contains("http 5") ->
                "osu! servers are temporarily unavailable. Try again shortly."
            lower.contains("already used") ->
                "Authorization code expired. Start Sign in with osu! again."
            lower.contains("access_denied") || lower.contains("cancelled") ->
                "Sign-in was cancelled before authorization completed."
            lower.contains("empty authorization") || lower.contains("empty access_token") ->
                "osu! did not return a valid token. Please try again."
            lower.contains("loopback") || lower.contains("address already in use") ->
                "Local callback listener could not start. Use In-App Browser Sign-In."
            msg.isNotBlank() &&
                !msg.contains("Exception", ignoreCase = true) &&
                !msg.contains("java.", ignoreCase = true) &&
                !msg.contains("okhttp", ignoreCase = true) ->
                msg
            else -> fallback
        }
    }
}
