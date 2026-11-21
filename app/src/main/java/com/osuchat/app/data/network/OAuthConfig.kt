package com.osuchat.app.data.network

/**
 * OAuth2 configuration for osu! API v2.
 *
 * Token exchange and refresh are routed through the Cloudflare Worker proxy so no
 * client secret is embedded in the APK.
 */
object OAuthConfig {
    const val CLIENT_ID: String = "75323"
    const val DEFAULT_CLIENT_ID: String = CLIENT_ID

    const val LOOPBACK_HOST: String = "127.0.0.1"
    const val LOOPBACK_PORT: Int = 42069
    const val REDIRECT_URI: String = "http://127.0.0.1:42069/callback"

    const val CUSTOM_SCHEME: String = "osuchat"
    const val CUSTOM_HOST: String = "oauth"
    const val CUSTOM_PATH_PREFIX: String = "/callback"
    const val CUSTOM_SCHEME_REDIRECT_URI: String = "osuchat://oauth/callback"

    const val AUTHORIZE_URL: String = "https://osu.ppy.sh/oauth/authorize"
    const val OAUTH_TOKEN_PROXY_URL: String = "https://osucompanion-auth.egemen147hesabi.workers.dev/"
    const val BASE_API_URL: String = "https://osu.ppy.sh/"
    const val PUBLIC_MIRROR_URL: String = "https://catboy.best/"

    val SCOPES: List<String> = listOf(
        "public",
        "identify",
        "friends.read"
    )

    val SUPPORTS_CHAT_READ_HISTORY: Boolean = "chat.read" in SCOPES
}
