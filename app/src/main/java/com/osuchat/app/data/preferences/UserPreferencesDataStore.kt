package com.osuchat.app.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.osuchat.app.domain.model.AppSettings
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.domain.model.BackupPreferencesDto
import com.osuchat.app.domain.model.MatchSetupTemplate
import com.osuchat.app.domain.model.RefereeMacro
import com.osuchat.app.domain.model.ThemeMode
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.domain.model.WidgetTheme
import com.osuchat.app.util.BeatmapUrlParser
import com.osuchat.app.widget.BanchoStatsWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "osu_chat_prefs")

interface OAuthTokenStore {
    suspend fun getOAuthTokens(): OAuthTokenData?
    suspend fun updateOAuthTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long)
    suspend fun clearOAuthTokens()
}

internal object CredentialCipher {
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "osu_chat_credential_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    fun encrypt(plaintext: String, keyOverride: SecretKey? = null): String {
        if (plaintext.isEmpty() || isEncrypted(plaintext)) return plaintext
        val key = keyOverride ?: getOrCreateKeystoreKey() ?: return plaintext
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined)
        }.getOrDefault(plaintext)
    }

    fun decrypt(stored: String, keyOverride: SecretKey? = null): String? {
        if (!isEncrypted(stored)) return stored
        val key = keyOverride ?: getOrCreateKeystoreKey() ?: return null
        return runCatching {
            val payload = Base64.getDecoder().decode(stored.removePrefix(ENCRYPTED_PREFIX))
            if (payload.size <= IV_BYTES) return null
            val iv = payload.copyOfRange(0, IV_BYTES)
            val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    private fun getOrCreateKeystoreKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@runCatching it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }.getOrNull()
}

data class OAuthTokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

data class PendingPkceData(
    val codeVerifier: String,
    val state: String,
    val clientId: String
)

data class IrcCredentials(
    val username: String,
    val password: String
)

data class WidgetStatsSnapshot(
    val username: String = "",
    val avatarUrl: String? = null,
    val globalRank: Int? = null,
    val countryRank: Int? = null,
    val countryCode: String = "XX",
    val pp: Double? = null,
    val accuracy: Double? = null,
    val onlineFriendsCount: Int = 0,
    val updatedAtMillis: Long = 0L,
    val widgetTheme: WidgetTheme = WidgetTheme.NOTHING_OS,
    val widgetTranslucent: Boolean = false
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : OAuthTokenStore {
    private val store = context.dataStore
    private val explicitDisconnectCache = AtomicReference<Boolean?>(null)
    private val macroGson = Gson()
    private val macroListType = object : TypeToken<List<RefereeMacro>>() {}.type
    private val matchTemplateListType = object : TypeToken<List<MatchSetupTemplate>>() {}.type

    private object Keys {
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val USERNAME = stringPreferencesKey("username")
        val USER_ID = longPreferencesKey("user_id")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val CUSTOM_CLIENT_ID = stringPreferencesKey("custom_client_id")

        val OAUTH_ACCESS_TOKEN = stringPreferencesKey("oauth_access_token")
        val OAUTH_REFRESH_TOKEN = stringPreferencesKey("oauth_refresh_token")
        val OAUTH_EXPIRES_AT = longPreferencesKey("oauth_expires_at")

        val PKCE_VERIFIER = stringPreferencesKey("pkce_verifier")
        val PKCE_STATE = stringPreferencesKey("pkce_state")
        val PKCE_CLIENT_ID = stringPreferencesKey("pkce_client_id")

        val IRC_USERNAME = stringPreferencesKey("irc_username")
        val IRC_PASSWORD = stringPreferencesKey("irc_password")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WALLPAPER_PRESET = intPreferencesKey("wallpaper_preset")
        val WALLPAPER_IMAGE_URI = stringPreferencesKey("wallpaper_image_uri")
        val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
        val WALLPAPER_BLUR = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_SCALE = floatPreferencesKey("wallpaper_scale")
        val WALLPAPER_BIAS_X = floatPreferencesKey("wallpaper_bias_x")
        val WALLPAPER_BIAS_Y = floatPreferencesKey("wallpaper_bias_y")

        val HIGHLIGHT_KEYWORDS = stringPreferencesKey("highlight_keywords")
        val KEEP_ALIVE_SERVICE = booleanPreferencesKey("keep_alive_service")
        val AUTO_CONNECT_BOOT = booleanPreferencesKey("auto_connect_boot")
        val NOTIFY_DM = booleanPreferencesKey("notify_dm")
        val NOTIFY_KEYWORDS = booleanPreferencesKey("notify_keywords")

        val CACHED_FRIENDS_JSON = stringPreferencesKey("cached_friends_json")
        val LOCAL_ADDED_FRIENDS_JSON = stringPreferencesKey("local_added_friends_json")
        val LOCAL_REMOVED_FRIEND_IDS = stringPreferencesKey("local_removed_friend_ids")

        val WIDGET_USERNAME = stringPreferencesKey("widget_username")
        val WIDGET_AVATAR_URL = stringPreferencesKey("widget_avatar_url")
        val WIDGET_GLOBAL_RANK = intPreferencesKey("widget_global_rank")
        val WIDGET_COUNTRY_RANK = intPreferencesKey("widget_country_rank")
        val WIDGET_COUNTRY_CODE = stringPreferencesKey("widget_country_code")
        val WIDGET_PP = doublePreferencesKey("widget_pp")
        val WIDGET_ACCURACY = doublePreferencesKey("widget_accuracy")
        val WIDGET_ONLINE_FRIENDS = intPreferencesKey("widget_online_friends")
        val WIDGET_UPDATED_AT = longPreferencesKey("widget_updated_at")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val WIDGET_TRANSLUCENT = booleanPreferencesKey("widget_translucent")

        val AUTO_JOIN_ENABLED = booleanPreferencesKey("auto_join_enabled")
        val AUTO_JOIN_CHANNELS = stringPreferencesKey("auto_join_channels")
        val EXPLICITLY_DISCONNECTED = booleanPreferencesKey("explicitly_disconnected")
        val MUTED_CHANNELS = stringSetPreferencesKey("muted_channels")
        val DOT_ONLY_CHANNELS = stringSetPreferencesKey("dot_only_channels")
        val REFEREE_MACROS_JSON = stringPreferencesKey("referee_macros_json")
        val MATCH_SETUP_TEMPLATES_JSON = stringPreferencesKey("match_setup_templates_json")
        val IGNORED_USERS = stringSetPreferencesKey("ignored_users")
        val IGNORE_DISPLAY_MODE = stringPreferencesKey("ignore_display_mode")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
    }

    val refereeMacrosFlow: Flow<List<RefereeMacro>> = store.data.map { prefs ->
        val raw = prefs[Keys.REFEREE_MACROS_JSON]
        if (raw.isNullOrBlank()) {
            RefereeMacro.DEFAULT_MACROS
        } else {
            runCatching {
                macroGson.fromJson<List<RefereeMacro>>(raw, macroListType)
                    ?.filter { it.label.isNotBlank() && it.message.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: RefereeMacro.DEFAULT_MACROS
            }.getOrDefault(RefereeMacro.DEFAULT_MACROS)
        }
    }.distinctUntilChanged()

    val matchSetupTemplatesFlow: Flow<List<MatchSetupTemplate>> = store.data.map { prefs ->
        val raw = prefs[Keys.MATCH_SETUP_TEMPLATES_JSON]
        if (raw.isNullOrBlank()) {
            MatchSetupTemplate.DEFAULT_TEMPLATES
        } else {
            runCatching {
                macroGson.fromJson<List<MatchSetupTemplate>>(raw, matchTemplateListType)
                    ?.filter { it.name.isNotBlank() && it.roomNamePattern.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: MatchSetupTemplate.DEFAULT_TEMPLATES
            }.getOrDefault(MatchSetupTemplate.DEFAULT_TEMPLATES)
        }
    }.distinctUntilChanged()

    val mutedChannels: Flow<Set<String>> = store.data.map { prefs ->
        prefs[Keys.MUTED_CHANNELS]
            ?.map(::normalizeMutedChannelName)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }.distinctUntilChanged()

    val dotOnlyChannels: Flow<Set<String>> = store.data.map { prefs ->
        prefs[Keys.DOT_ONLY_CHANNELS]
            ?.map(::normalizeMutedChannelName)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }.distinctUntilChanged()

    val ignoredUsersFlow: Flow<Set<String>> = store.data.map { prefs ->
        prefs[Keys.IGNORED_USERS]
            ?.map(BeatmapUrlParser::normalizeOsuNick)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }.distinctUntilChanged()

    val ignoreDisplayModeFlow: Flow<String> = store.data.map { prefs ->
        prefs[Keys.IGNORE_DISPLAY_MODE]
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it == "HIDE" || it == "COLLAPSE" }
            ?: "HIDE"
    }.distinctUntilChanged()

    val settingsFlow: Flow<AppSettings> = store.data.map { prefs ->
        val authMode = runCatching {
            AuthMode.valueOf(prefs[Keys.AUTH_MODE] ?: AuthMode.NONE.name)
        }.getOrDefault(AuthMode.NONE)

        val themeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val widgetTheme = runCatching {
            WidgetTheme.valueOf(prefs[Keys.WIDGET_THEME] ?: WidgetTheme.NOTHING_OS.name)
        }.getOrDefault(WidgetTheme.NOTHING_OS)

        val rawKeywords = prefs[Keys.HIGHLIGHT_KEYWORDS]
        val keywords = if (rawKeywords.isNullOrBlank()) {
            emptyList()
        } else {
            rawKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        val rawAutoJoin = prefs[Keys.AUTO_JOIN_CHANNELS]
        val autoJoinChannels = when {
            rawAutoJoin == null -> AppSettings.DEFAULT_AUTO_JOIN_CHANNELS
            rawAutoJoin.isBlank() -> emptyList()
            else -> normalizeChannelList(rawAutoJoin.split(","))
        }
        val explicitlyDisconnected = prefs[Keys.EXPLICITLY_DISCONNECTED] ?: false
        explicitDisconnectCache.compareAndSet(null, explicitlyDisconnected)

        val ignoredUsers = prefs[Keys.IGNORED_USERS]
            ?.map(BeatmapUrlParser::normalizeOsuNick)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        val ignoreDisplayMode = prefs[Keys.IGNORE_DISPLAY_MODE]
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it == "HIDE" || it == "COLLAPSE" }
            ?: "HIDE"

        AppSettings(
            authMode = authMode,
            username = prefs[Keys.USERNAME].orEmpty(),
            ircUsername = prefs[Keys.IRC_USERNAME].orEmpty(),
            userId = prefs[Keys.USER_ID] ?: 0L,
            avatarUrl = prefs[Keys.AVATAR_URL],
            hasIrcPassword = !prefs[Keys.IRC_PASSWORD].isNullOrBlank(),
            customClientId = prefs[Keys.CUSTOM_CLIENT_ID].orEmpty(),
            themeMode = themeMode,
            globalWallpaper = WallpaperConfig(
                presetId = prefs[Keys.WALLPAPER_PRESET] ?: 0,
                customImageUri = prefs[Keys.WALLPAPER_IMAGE_URI],
                dimAmount = (prefs[Keys.WALLPAPER_DIM] ?: 0.35f).coerceIn(0f, 0.85f),
                blurRadiusDp = (prefs[Keys.WALLPAPER_BLUR] ?: 0f).coerceIn(0f, 25f),
                scale = (prefs[Keys.WALLPAPER_SCALE] ?: 1.0f).coerceIn(
                    WallpaperConfig.MIN_SCALE,
                    WallpaperConfig.MAX_SCALE
                ),
                biasX = (prefs[Keys.WALLPAPER_BIAS_X] ?: 0.0f).coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                ),
                biasY = (prefs[Keys.WALLPAPER_BIAS_Y] ?: 0.0f).coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                )
            ),
            highlightKeywords = keywords,
            keepAliveService = prefs[Keys.KEEP_ALIVE_SERVICE] ?: true,
            autoConnectOnBoot = prefs[Keys.AUTO_CONNECT_BOOT] ?: false,
            notifyDirectMessages = prefs[Keys.NOTIFY_DM] ?: true,
            notifyKeywordMentions = prefs[Keys.NOTIFY_KEYWORDS] ?: true,
            widgetTheme = widgetTheme,
            widgetTranslucent = prefs[Keys.WIDGET_TRANSLUCENT] ?: false,
            autoJoinEnabled = prefs[Keys.AUTO_JOIN_ENABLED] ?: true,
            autoJoinChannels = autoJoinChannels,
            isExplicitlyDisconnected = explicitlyDisconnected,
            ignoredUsers = ignoredUsers,
            ignoreDisplayMode = ignoreDisplayMode
        )
    }

    fun observeChannelWallpaper(channelId: String): Flow<WallpaperConfig?> {
        val prefix = "ch_wp_${channelId.hashCode()}"
        val kPreset = intPreferencesKey("${prefix}_preset")
        val kUri = stringPreferencesKey("${prefix}_uri")
        val kDim = floatPreferencesKey("${prefix}_dim")
        val kBlur = floatPreferencesKey("${prefix}_blur")
        val kScale = floatPreferencesKey("${prefix}_scale")
        val kBiasX = floatPreferencesKey("${prefix}_bias_x")
        val kBiasY = floatPreferencesKey("${prefix}_bias_y")
        val kEnabled = booleanPreferencesKey("${prefix}_enabled")

        return store.data.map { prefs ->
            if (prefs[kEnabled] != true) return@map null
            WallpaperConfig(
                presetId = prefs[kPreset] ?: 0,
                customImageUri = prefs[kUri],
                dimAmount = (prefs[kDim] ?: 0.35f).coerceIn(0f, 0.85f),
                blurRadiusDp = (prefs[kBlur] ?: 0f).coerceIn(0f, 25f),
                scale = (prefs[kScale] ?: 1.0f).coerceIn(
                    WallpaperConfig.MIN_SCALE,
                    WallpaperConfig.MAX_SCALE
                ),
                biasX = (prefs[kBiasX] ?: 0.0f).coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                ),
                biasY = (prefs[kBiasY] ?: 0.0f).coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                )
            )
        }
    }

    suspend fun setChannelWallpaper(channelId: String, config: WallpaperConfig?) {
        val prefix = "ch_wp_${channelId.hashCode()}"
        val kPreset = intPreferencesKey("${prefix}_preset")
        val kUri = stringPreferencesKey("${prefix}_uri")
        val kDim = floatPreferencesKey("${prefix}_dim")
        val kBlur = floatPreferencesKey("${prefix}_blur")
        val kScale = floatPreferencesKey("${prefix}_scale")
        val kBiasX = floatPreferencesKey("${prefix}_bias_x")
        val kBiasY = floatPreferencesKey("${prefix}_bias_y")
        val kEnabled = booleanPreferencesKey("${prefix}_enabled")

        store.edit { prefs ->
            if (config == null) {
                prefs[kEnabled] = false
                prefs.remove(kUri)
                prefs.remove(kScale)
                prefs.remove(kBiasX)
                prefs.remove(kBiasY)
            } else {
                prefs[kEnabled] = true
                prefs[kPreset] = config.presetId
                if (config.customImageUri != null) {
                    prefs[kUri] = config.customImageUri
                } else {
                    prefs.remove(kUri)
                }
                prefs[kDim] = config.dimAmount.coerceIn(0f, 0.85f)
                prefs[kBlur] = config.blurRadiusDp.coerceIn(0f, 25f)
                prefs[kScale] = config.scale.coerceIn(
                    WallpaperConfig.MIN_SCALE,
                    WallpaperConfig.MAX_SCALE
                )
                prefs[kBiasX] = config.biasX.coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                )
                prefs[kBiasY] = config.biasY.coerceIn(
                    WallpaperConfig.MIN_BIAS,
                    WallpaperConfig.MAX_BIAS
                )
            }
        }
    }

    suspend fun savePendingPkce(verifier: String, state: String, clientId: String) {
        store.edit { prefs ->
            prefs[Keys.PKCE_VERIFIER] = verifier
            prefs[Keys.PKCE_STATE] = state
            prefs[Keys.PKCE_CLIENT_ID] = clientId
        }
    }

    suspend fun getPendingPkce(): PendingPkceData? {
        val prefs = store.data.first()
        val verifier = prefs[Keys.PKCE_VERIFIER] ?: return null
        val state = prefs[Keys.PKCE_STATE] ?: return null
        val clientId = prefs[Keys.PKCE_CLIENT_ID] ?: return null
        return PendingPkceData(verifier, state, clientId)
    }

    suspend fun clearPendingPkce() {
        store.edit { prefs ->
            prefs.remove(Keys.PKCE_VERIFIER)
            prefs.remove(Keys.PKCE_STATE)
            prefs.remove(Keys.PKCE_CLIENT_ID)
        }
    }

    suspend fun saveOAuthSession(
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long,
        username: String,
        userId: Long,
        avatarUrl: String?,
        clientId: String
    ) {
        val encAccess = CredentialCipher.encrypt(accessToken)
        val encRefresh = CredentialCipher.encrypt(refreshToken)
        store.edit { prefs ->
            prefs[Keys.AUTH_MODE] = AuthMode.OAUTH_V2.name
            prefs[Keys.OAUTH_ACCESS_TOKEN] = encAccess
            prefs[Keys.OAUTH_REFRESH_TOKEN] = encRefresh
            prefs[Keys.OAUTH_EXPIRES_AT] = expiresAtMillis
            prefs[Keys.USERNAME] = username
            prefs[Keys.IRC_USERNAME] = username
            prefs[Keys.USER_ID] = userId
            if (avatarUrl != null) prefs[Keys.AVATAR_URL] = avatarUrl else prefs.remove(Keys.AVATAR_URL)
            prefs[Keys.CUSTOM_CLIENT_ID] = clientId
            prefs[Keys.EXPLICITLY_DISCONNECTED] = false
        }
        explicitDisconnectCache.set(false)
    }

    override suspend fun updateOAuthTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        val encAccess = CredentialCipher.encrypt(accessToken)
        val encRefresh = CredentialCipher.encrypt(refreshToken)
        store.edit { prefs ->
            prefs[Keys.OAUTH_ACCESS_TOKEN] = encAccess
            prefs[Keys.OAUTH_REFRESH_TOKEN] = encRefresh
            prefs[Keys.OAUTH_EXPIRES_AT] = expiresAtMillis
        }
    }

    override suspend fun clearOAuthTokens() {
        store.edit { prefs ->
            prefs.remove(Keys.OAUTH_ACCESS_TOKEN)
            prefs.remove(Keys.OAUTH_REFRESH_TOKEN)
            prefs.remove(Keys.OAUTH_EXPIRES_AT)
            prefs[Keys.AUTH_MODE] = AuthMode.NONE.name
        }
    }

    override suspend fun getOAuthTokens(): OAuthTokenData? {
        val prefs = store.data.first()
        val rawAccess = prefs[Keys.OAUTH_ACCESS_TOKEN] ?: return null
        val rawRefresh = prefs[Keys.OAUTH_REFRESH_TOKEN].orEmpty()
        val expires = prefs[Keys.OAUTH_EXPIRES_AT] ?: 0L

        val access = CredentialCipher.decrypt(rawAccess) ?: return null
        val refresh = if (rawRefresh.isEmpty()) "" else CredentialCipher.decrypt(rawRefresh).orEmpty()

        // Transparently migrate legacy plaintext tokens to AndroidKeyStore AES-GCM in-place.
        val needsAccessMigration = access.isNotEmpty() && !CredentialCipher.isEncrypted(rawAccess)
        val needsRefreshMigration = refresh.isNotEmpty() && !CredentialCipher.isEncrypted(rawRefresh)
        if (needsAccessMigration || needsRefreshMigration) {
            val encAccess = if (needsAccessMigration) CredentialCipher.encrypt(access) else rawAccess
            val encRefresh = if (needsRefreshMigration) CredentialCipher.encrypt(refresh) else rawRefresh
            if (encAccess != rawAccess || encRefresh != rawRefresh) {
                store.edit { mutable ->
                    if (mutable[Keys.OAUTH_ACCESS_TOKEN] == rawAccess) {
                        mutable[Keys.OAUTH_ACCESS_TOKEN] = encAccess
                    }
                    if (mutable[Keys.OAUTH_REFRESH_TOKEN] == rawRefresh) {
                        mutable[Keys.OAUTH_REFRESH_TOKEN] = encRefresh
                    }
                }
            }
        }
        return OAuthTokenData(access, refresh, expires)
    }

    suspend fun saveIrcSession(username: String, password: String) {
        val encPassword = CredentialCipher.encrypt(password)
        store.edit { prefs ->
            prefs[Keys.AUTH_MODE] = AuthMode.IRC.name
            prefs[Keys.IRC_USERNAME] = username
            prefs[Keys.IRC_PASSWORD] = encPassword
            prefs[Keys.USERNAME] = username
            prefs[Keys.USER_ID] = 0L
            prefs.remove(Keys.AVATAR_URL)
            prefs[Keys.EXPLICITLY_DISCONNECTED] = false
        }
        explicitDisconnectCache.set(false)
    }

    suspend fun saveIrcCredentialsForOAuth(username: String, password: String) {
        val encPassword = CredentialCipher.encrypt(password.trim())
        store.edit { prefs ->
            prefs[Keys.IRC_USERNAME] = username.trim()
            prefs[Keys.IRC_PASSWORD] = encPassword
        }
    }

    suspend fun getIrcCredentials(): IrcCredentials? {
        val prefs = store.data.first()
        val user = prefs[Keys.IRC_USERNAME] ?: return null
        val rawPass = prefs[Keys.IRC_PASSWORD] ?: return null
        val pass = CredentialCipher.decrypt(rawPass) ?: return null
        if (pass.isNotEmpty() && !CredentialCipher.isEncrypted(rawPass)) {
            val encPass = CredentialCipher.encrypt(pass)
            if (encPass != rawPass) {
                store.edit { mutable ->
                    if (mutable[Keys.IRC_PASSWORD] == rawPass) {
                        mutable[Keys.IRC_PASSWORD] = encPass
                    }
                }
            }
        }
        return IrcCredentials(user, pass)
    }

    suspend fun updateSelfProfile(userId: Long, username: String, avatarUrl: String?) {
        store.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USERNAME] = username
            if (avatarUrl != null) prefs[Keys.AVATAR_URL] = avatarUrl
        }
    }

    suspend fun saveCustomClientId(clientId: String) {
        store.edit { prefs ->
            prefs[Keys.CUSTOM_CLIENT_ID] = clientId.trim()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setWidgetTheme(theme: WidgetTheme) {
        store.edit { prefs ->
            prefs[Keys.WIDGET_THEME] = theme.name
        }
    }

    suspend fun setWidgetTranslucent(translucent: Boolean) {
        store.edit { prefs ->
            prefs[Keys.WIDGET_TRANSLUCENT] = translucent
        }
    }

    suspend fun setGlobalWallpaper(config: WallpaperConfig) {
        store.edit { prefs ->
            prefs[Keys.WALLPAPER_PRESET] = config.presetId
            if (config.customImageUri != null) {
                prefs[Keys.WALLPAPER_IMAGE_URI] = config.customImageUri
            } else {
                prefs.remove(Keys.WALLPAPER_IMAGE_URI)
            }
            prefs[Keys.WALLPAPER_DIM] = config.dimAmount.coerceIn(0f, 0.85f)
            prefs[Keys.WALLPAPER_BLUR] = config.blurRadiusDp.coerceIn(0f, 25f)
            prefs[Keys.WALLPAPER_SCALE] = config.scale.coerceIn(
                WallpaperConfig.MIN_SCALE,
                WallpaperConfig.MAX_SCALE
            )
            prefs[Keys.WALLPAPER_BIAS_X] = config.biasX.coerceIn(
                WallpaperConfig.MIN_BIAS,
                WallpaperConfig.MAX_BIAS
            )
            prefs[Keys.WALLPAPER_BIAS_Y] = config.biasY.coerceIn(
                WallpaperConfig.MIN_BIAS,
                WallpaperConfig.MAX_BIAS
            )
        }
    }

    suspend fun setHighlightKeywords(keywords: List<String>) {
        store.edit { prefs ->
            prefs[Keys.HIGHLIGHT_KEYWORDS] = keywords.joinToString(",")
        }
    }

    suspend fun setKeepAliveService(enabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.KEEP_ALIVE_SERVICE] = enabled
        }
    }

    suspend fun setAutoConnectOnBoot(enabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.AUTO_CONNECT_BOOT] = enabled
        }
    }

    suspend fun setNotificationPreferences(notifyDm: Boolean, notifyKeywords: Boolean) {
        store.edit { prefs ->
            prefs[Keys.NOTIFY_DM] = notifyDm
            prefs[Keys.NOTIFY_KEYWORDS] = notifyKeywords
        }
    }

    suspend fun ignoreUser(username: String) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username)
        if (norm.isEmpty() || norm == "banchobot" || norm == "system") return
        store.edit { prefs ->
            val current = prefs[Keys.IGNORED_USERS]
                ?.map(BeatmapUrlParser::normalizeOsuNick)
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.add(norm)
            prefs[Keys.IGNORED_USERS] = current
        }
    }

    suspend fun unignoreUser(username: String) {
        val norm = BeatmapUrlParser.normalizeOsuNick(username)
        if (norm.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[Keys.IGNORED_USERS]
                ?.map(BeatmapUrlParser::normalizeOsuNick)
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            current.remove(norm)
            if (current.isEmpty()) {
                prefs.remove(Keys.IGNORED_USERS)
            } else {
                prefs[Keys.IGNORED_USERS] = current
            }
        }
    }

    suspend fun setIgnoreDisplayMode(mode: String) {
        val normalized = if (mode.trim().equals("COLLAPSE", ignoreCase = true)) "COLLAPSE" else "HIDE"
        store.edit { prefs ->
            prefs[Keys.IGNORE_DISPLAY_MODE] = normalized
        }
    }

    suspend fun toggleChannelMute(channelName: String): Boolean {
        val normalized = normalizeMutedChannelName(channelName)
        if (normalized.isEmpty()) return false
        var nowMuted = false
        store.edit { prefs ->
            val current = prefs[Keys.MUTED_CHANNELS]
                ?.map(::normalizeMutedChannelName)
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            val variants = mutedChannelVariants(normalized)
            if (variants.any { it in current }) {
                current.removeAll(variants)
                nowMuted = false
            } else {
                current.add(normalized)
                nowMuted = true
            }
            if (current.isEmpty()) {
                prefs.remove(Keys.MUTED_CHANNELS)
            } else {
                prefs[Keys.MUTED_CHANNELS] = current
            }
        }
        return nowMuted
    }

    suspend fun setChannelMuted(channelName: String, muted: Boolean) {
        val normalized = normalizeMutedChannelName(channelName)
        if (normalized.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[Keys.MUTED_CHANNELS]
                ?.map(::normalizeMutedChannelName)
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            val variants = mutedChannelVariants(normalized)
            if (muted) {
                current.add(normalized)
            } else {
                current.removeAll(variants)
            }
            if (current.isEmpty()) {
                prefs.remove(Keys.MUTED_CHANNELS)
            } else {
                prefs[Keys.MUTED_CHANNELS] = current
            }
        }
    }

    suspend fun toggleChannelDotOnly(channelId: String): Boolean {
        val normalized = normalizeMutedChannelName(channelId)
        if (normalized.isEmpty()) return false
        var nowDotOnly = false
        store.edit { prefs ->
            val current = prefs[Keys.DOT_ONLY_CHANNELS]
                ?.map(::normalizeMutedChannelName)
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            val variants = mutedChannelVariants(normalized)
            if (variants.any { it in current }) {
                current.removeAll(variants)
                nowDotOnly = false
            } else {
                current.add(normalized)
                nowDotOnly = true
            }
            if (current.isEmpty()) {
                prefs.remove(Keys.DOT_ONLY_CHANNELS)
            } else {
                prefs[Keys.DOT_ONLY_CHANNELS] = current
            }
        }
        return nowDotOnly
    }

    private fun normalizeMutedChannelName(channelName: String): String =
        channelName.trim().lowercase(Locale.ROOT)

    private fun mutedChannelVariants(normalized: String): Set<String> {
        if (normalized.startsWith("dm:") || normalized.startsWith("dm_")) {
            return setOf(normalized)
        }
        val bare = normalized.removePrefix("#")
        return if (bare.isEmpty()) setOf(normalized) else setOf(normalized, bare, "#$bare")
    }

    suspend fun setAutoJoinConfig(enabled: Boolean, channels: List<String>) {
        val normalized = normalizeChannelList(channels)
        store.edit { prefs ->
            prefs[Keys.AUTO_JOIN_ENABLED] = enabled
            prefs[Keys.AUTO_JOIN_CHANNELS] = normalized.joinToString(",")
        }
    }

    suspend fun setExplicitlyDisconnected(disconnected: Boolean) {
        explicitDisconnectCache.set(disconnected)
        store.edit { prefs ->
            prefs[Keys.EXPLICITLY_DISCONNECTED] = disconnected
        }
    }

    suspend fun isExplicitlyDisconnected(): Boolean {
        explicitDisconnectCache.get()?.let { return it }
        val persisted = store.data.first()[Keys.EXPLICITLY_DISCONNECTED] ?: false
        explicitDisconnectCache.set(persisted)
        return persisted
    }

    private fun normalizeChannelList(raw: List<String>): List<String> {
        return raw.mapNotNull { entry ->
            val trimmed = entry.trim().lowercase()
            if (trimmed.isEmpty()) null else if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        }.distinct()
    }

    suspend fun getCachedFriendsJson(): String? {
        return store.data.first()[Keys.CACHED_FRIENDS_JSON]
    }

    suspend fun saveCachedFriendsJson(json: String) {
        store.edit { prefs ->
            prefs[Keys.CACHED_FRIENDS_JSON] = json
        }
    }

    suspend fun getLocalAddedFriendsJson(): String? {
        return store.data.first()[Keys.LOCAL_ADDED_FRIENDS_JSON]
    }

    suspend fun saveLocalAddedFriendsJson(json: String) {
        store.edit { prefs ->
            prefs[Keys.LOCAL_ADDED_FRIENDS_JSON] = json
        }
    }

    suspend fun getLocalRemovedFriendIds(): Set<Long> {
        val raw = store.data.first()[Keys.LOCAL_REMOVED_FRIEND_IDS].orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    suspend fun saveLocalRemovedFriendIds(ids: Set<Long>) {
        store.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(Keys.LOCAL_REMOVED_FRIEND_IDS)
            } else {
                prefs[Keys.LOCAL_REMOVED_FRIEND_IDS] = ids.joinToString(",")
            }
        }
    }

    suspend fun saveRefereeMacros(macros: List<RefereeMacro>) {
        val cleaned = macros.mapNotNull { macro ->
            val label = macro.label.trim()
            val message = macro.message.trim()
            if (label.isEmpty() || message.isEmpty()) return@mapNotNull null
            macro.copy(
                id = macro.id.trim().ifEmpty { "${label.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}" },
                label = label,
                message = message
            )
        }
        store.edit { prefs ->
            prefs[Keys.REFEREE_MACROS_JSON] = macroGson.toJson(cleaned)
        }
    }

    suspend fun resetRefereeMacros() {
        store.edit { prefs ->
            prefs.remove(Keys.REFEREE_MACROS_JSON)
        }
    }

    suspend fun saveMatchSetupTemplates(templates: List<MatchSetupTemplate>) {
        val cleaned = templates.mapNotNull { t ->
            val name = t.name.trim()
            val pattern = t.roomNamePattern.trim()
            if (name.isEmpty() || pattern.isEmpty()) return@mapNotNull null
            t.copy(
                id = t.id.trim().ifEmpty { "tpl_${System.currentTimeMillis()}_${name.hashCode()}" },
                name = name,
                roomNamePattern = pattern,
                bestOf = t.bestOf.coerceIn(1, 21),
                teamMode = t.teamMode.coerceIn(0, 3),
                scoreMode = t.scoreMode.coerceIn(0, 3),
                roomSize = t.roomSize.coerceIn(2, 16),
                password = t.password.trim(),
                defaultRefs = t.defaultRefs.map { it.trim() }.filter { it.isNotEmpty() },
                defaultInvites = t.defaultInvites.map { it.trim() }.filter { it.isNotEmpty() }
            )
        }
        store.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.MATCH_SETUP_TEMPLATES_JSON)
            } else {
                prefs[Keys.MATCH_SETUP_TEMPLATES_JSON] = macroGson.toJson(cleaned)
            }
        }
    }

    suspend fun restoreNonSensitivePreferences(backup: BackupPreferencesDto) {
        val normalizedAutoJoin = normalizeChannelList(backup.autoJoinChannels)
        val normalizedMuted = backup.mutedChannels
            .map(::normalizeMutedChannelName)
            .filter { it.isNotEmpty() }
            .toSet()
        val cleanedKeywords = backup.highlightKeywords
            .map { it.trim().replace(",", " ") }
            .filter { it.isNotEmpty() }
            .distinct()
        val safeThemeMode = runCatching {
            ThemeMode.valueOf(backup.themeMode.trim())
        }.getOrDefault(ThemeMode.SYSTEM)
        val safeWidgetTheme = runCatching {
            WidgetTheme.valueOf(backup.widgetTheme.trim())
        }.getOrDefault(WidgetTheme.NOTHING_OS)
        val cleanedMacros = backup.refereeMacros.mapNotNull { macro ->
            val label = macro.label.trim()
            val message = macro.message.trim()
            if (label.isEmpty() || message.isEmpty()) return@mapNotNull null
            macro.copy(
                id = macro.id.trim().ifEmpty { "${label.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}" },
                label = label,
                message = message
            )
        }
        val normalizedDotOnly = backup.dotOnlyChannels?.map(::normalizeMutedChannelName)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
        val normalizedIgnoredUsers = backup.ignoredUsers
            ?.map(BeatmapUrlParser::normalizeOsuNick)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
        val safeIgnoreDisplayMode = backup.ignoreDisplayMode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it == "HIDE" || it == "COLLAPSE" }
        val cleanedTemplates = backup.matchSetupTemplates?.mapNotNull { t ->
            val name = t.name.trim()
            val pattern = t.roomNamePattern.trim()
            if (name.isEmpty() || pattern.isEmpty()) return@mapNotNull null
            t.copy(
                id = t.id.trim().ifEmpty { "tpl_${System.currentTimeMillis()}_${name.hashCode()}" },
                name = name,
                roomNamePattern = pattern,
                bestOf = t.bestOf.coerceIn(1, 21),
                teamMode = t.teamMode.coerceIn(0, 3),
                scoreMode = t.scoreMode.coerceIn(0, 3),
                roomSize = t.roomSize.coerceIn(2, 16),
                password = t.password.trim(),
                defaultRefs = t.defaultRefs.map { it.trim() }.filter { it.isNotEmpty() },
                defaultInvites = t.defaultInvites.map { it.trim() }.filter { it.isNotEmpty() }
            )
        }

        store.edit { prefs ->
            prefs[Keys.AUTO_JOIN_ENABLED] = backup.autoJoinEnabled
            prefs[Keys.AUTO_JOIN_CHANNELS] = normalizedAutoJoin.joinToString(",")
            if (normalizedMuted.isEmpty()) {
                prefs.remove(Keys.MUTED_CHANNELS)
            } else {
                prefs[Keys.MUTED_CHANNELS] = normalizedMuted
            }
            prefs[Keys.HIGHLIGHT_KEYWORDS] = cleanedKeywords.joinToString(",")
            prefs[Keys.NOTIFY_DM] = backup.notifyDm
            prefs[Keys.NOTIFY_KEYWORDS] = backup.notifyKeywords
            prefs[Keys.KEEP_ALIVE_SERVICE] = backup.keepAliveService
            prefs[Keys.AUTO_CONNECT_BOOT] = backup.autoConnectBoot
            prefs[Keys.THEME_MODE] = safeThemeMode.name
            prefs[Keys.WIDGET_THEME] = safeWidgetTheme.name
            prefs[Keys.WIDGET_TRANSLUCENT] = backup.widgetTranslucent
            if (cleanedMacros.isEmpty()) {
                prefs.remove(Keys.REFEREE_MACROS_JSON)
            } else {
                prefs[Keys.REFEREE_MACROS_JSON] = macroGson.toJson(cleanedMacros)
            }
            if (normalizedDotOnly != null) {
                if (normalizedDotOnly.isEmpty()) {
                    prefs.remove(Keys.DOT_ONLY_CHANNELS)
                } else {
                    prefs[Keys.DOT_ONLY_CHANNELS] = normalizedDotOnly
                }
            }
            if (normalizedIgnoredUsers != null) {
                if (normalizedIgnoredUsers.isEmpty()) {
                    prefs.remove(Keys.IGNORED_USERS)
                } else {
                    prefs[Keys.IGNORED_USERS] = normalizedIgnoredUsers
                }
            }
            if (safeIgnoreDisplayMode != null) {
                prefs[Keys.IGNORE_DISPLAY_MODE] = safeIgnoreDisplayMode
            }
            if (cleanedTemplates != null) {
                if (cleanedTemplates.isEmpty()) {
                    prefs.remove(Keys.MATCH_SETUP_TEMPLATES_JSON)
                } else {
                    prefs[Keys.MATCH_SETUP_TEMPLATES_JSON] = macroGson.toJson(cleanedTemplates)
                }
            }
            backup.wallpaperPreset?.let { prefs[Keys.WALLPAPER_PRESET] = it.coerceAtLeast(0) }
            backup.wallpaperDim?.let { if (it.isFinite()) prefs[Keys.WALLPAPER_DIM] = it.coerceIn(0f, 0.85f) }
            backup.wallpaperBlur?.let { if (it.isFinite()) prefs[Keys.WALLPAPER_BLUR] = it.coerceIn(0f, 25f) }
            backup.wallpaperScale?.let {
                if (it.isFinite()) {
                    prefs[Keys.WALLPAPER_SCALE] = it.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE)
                }
            }
            backup.wallpaperBiasX?.let {
                if (it.isFinite()) {
                    prefs[Keys.WALLPAPER_BIAS_X] = it.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
                }
            }
            backup.wallpaperBiasY?.let {
                if (it.isFinite()) {
                    prefs[Keys.WALLPAPER_BIAS_Y] = it.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
                }
            }
        }
    }

    suspend fun clearSession() {
        var preservedTheme = WidgetTheme.NOTHING_OS
        var preservedTranslucent = false
        store.edit { prefs ->
            preservedTheme = runCatching {
                WidgetTheme.valueOf(prefs[Keys.WIDGET_THEME] ?: WidgetTheme.NOTHING_OS.name)
            }.getOrDefault(WidgetTheme.NOTHING_OS)
            preservedTranslucent = prefs[Keys.WIDGET_TRANSLUCENT] ?: false

            prefs[Keys.AUTH_MODE] = AuthMode.NONE.name
            prefs.remove(Keys.OAUTH_ACCESS_TOKEN)
            prefs.remove(Keys.OAUTH_REFRESH_TOKEN)
            prefs.remove(Keys.OAUTH_EXPIRES_AT)
            prefs.remove(Keys.PKCE_VERIFIER)
            prefs.remove(Keys.PKCE_STATE)
            prefs.remove(Keys.PKCE_CLIENT_ID)
            prefs.remove(Keys.IRC_USERNAME)
            prefs.remove(Keys.IRC_PASSWORD)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.AVATAR_URL)
            prefs.remove(Keys.WIDGET_USERNAME)
            prefs.remove(Keys.WIDGET_AVATAR_URL)
            prefs.remove(Keys.WIDGET_GLOBAL_RANK)
            prefs.remove(Keys.WIDGET_COUNTRY_RANK)
            prefs.remove(Keys.WIDGET_COUNTRY_CODE)
            prefs.remove(Keys.WIDGET_PP)
            prefs.remove(Keys.WIDGET_ACCURACY)
            prefs.remove(Keys.WIDGET_ONLINE_FRIENDS)
            prefs.remove(Keys.WIDGET_UPDATED_AT)
            prefs[Keys.EXPLICITLY_DISCONNECTED] = false
        }
        explicitDisconnectCache.set(false)
        BanchoStatsWidget.clearAvatarCache(context)
        BanchoStatsWidget.updateFromSnapshot(
            context,
            WidgetStatsSnapshot(
                widgetTheme = preservedTheme,
                widgetTranslucent = preservedTranslucent
            )
        )
    }

    suspend fun saveWidgetSnapshot(
        username: String? = null,
        avatarUrl: String? = null,
        globalRank: Int? = null,
        countryRank: Int? = null,
        countryCode: String? = null,
        pp: Double? = null,
        accuracy: Double? = null,
        onlineFriendsCount: Int? = null,
        updatedAtMillis: Long = System.currentTimeMillis()
    ) {
        val isFullProfileUpdate = !username.isNullOrBlank()
        store.edit { prefs ->
            if (isFullProfileUpdate) {
                prefs[Keys.WIDGET_USERNAME] = username!!
                if (globalRank != null && globalRank > 0) {
                    prefs[Keys.WIDGET_GLOBAL_RANK] = globalRank
                } else {
                    prefs.remove(Keys.WIDGET_GLOBAL_RANK)
                }
                if (countryRank != null && countryRank > 0) {
                    prefs[Keys.WIDGET_COUNTRY_RANK] = countryRank
                } else {
                    prefs.remove(Keys.WIDGET_COUNTRY_RANK)
                }
            } else {
                if (globalRank != null && globalRank > 0) prefs[Keys.WIDGET_GLOBAL_RANK] = globalRank
                if (countryRank != null && countryRank > 0) prefs[Keys.WIDGET_COUNTRY_RANK] = countryRank
            }
            if (!avatarUrl.isNullOrBlank()) prefs[Keys.WIDGET_AVATAR_URL] = avatarUrl
            if (!countryCode.isNullOrBlank() && !countryCode.equals("XX", true)) {
                prefs[Keys.WIDGET_COUNTRY_CODE] = countryCode.uppercase()
            }
            if (pp != null) prefs[Keys.WIDGET_PP] = pp
            if (accuracy != null) prefs[Keys.WIDGET_ACCURACY] = accuracy
            if (onlineFriendsCount != null) prefs[Keys.WIDGET_ONLINE_FRIENDS] = onlineFriendsCount.coerceAtLeast(0)
            prefs[Keys.WIDGET_UPDATED_AT] = updatedAtMillis
        }
    }

    suspend fun getWidgetSnapshot(): WidgetStatsSnapshot {
        val prefs = store.data.first()
        val widgetTheme = runCatching {
            WidgetTheme.valueOf(prefs[Keys.WIDGET_THEME] ?: WidgetTheme.NOTHING_OS.name)
        }.getOrDefault(WidgetTheme.NOTHING_OS)
        val widgetTranslucent = prefs[Keys.WIDGET_TRANSLUCENT] ?: false
        val authMode = runCatching {
            AuthMode.valueOf(prefs[Keys.AUTH_MODE] ?: AuthMode.NONE.name)
        }.getOrDefault(AuthMode.NONE)
        if (authMode == AuthMode.NONE) {
            return WidgetStatsSnapshot(
                widgetTheme = widgetTheme,
                widgetTranslucent = widgetTranslucent
            )
        }
        return WidgetStatsSnapshot(
            username = prefs[Keys.WIDGET_USERNAME] ?: prefs[Keys.USERNAME].orEmpty(),
            avatarUrl = prefs[Keys.WIDGET_AVATAR_URL] ?: prefs[Keys.AVATAR_URL],
            globalRank = prefs[Keys.WIDGET_GLOBAL_RANK]?.takeIf { it > 0 },
            countryRank = prefs[Keys.WIDGET_COUNTRY_RANK]?.takeIf { it > 0 },
            countryCode = prefs[Keys.WIDGET_COUNTRY_CODE] ?: "XX",
            pp = prefs[Keys.WIDGET_PP],
            accuracy = prefs[Keys.WIDGET_ACCURACY],
            onlineFriendsCount = prefs[Keys.WIDGET_ONLINE_FRIENDS] ?: 0,
            updatedAtMillis = prefs[Keys.WIDGET_UPDATED_AT] ?: 0L,
            widgetTheme = widgetTheme,
            widgetTranslucent = widgetTranslucent
        )
    }

    suspend fun getLastUpdateCheckAt(): Long {
        return store.data.first()[Keys.LAST_UPDATE_CHECK_AT] ?: 0L
    }

    suspend fun setLastUpdateCheckAt(timestampMillis: Long) {
        store.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_AT] = timestampMillis
        }
    }

    suspend fun getSkippedUpdateVersion(): String {
        return store.data.first()[Keys.SKIPPED_UPDATE_VERSION].orEmpty()
    }

    suspend fun setSkippedUpdateVersion(versionTag: String) {
        val clean = versionTag.trim()
        store.edit { prefs ->
            if (clean.isEmpty()) {
                prefs.remove(Keys.SKIPPED_UPDATE_VERSION)
            } else {
                prefs[Keys.SKIPPED_UPDATE_VERSION] = clean
            }
        }
    }
}
