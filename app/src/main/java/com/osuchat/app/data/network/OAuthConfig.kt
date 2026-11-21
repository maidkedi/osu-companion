package com.osuchat.app.data.network

import com.osuchat.app.BuildConfig

/**
 * OAuth2 configuration for osu! API v2.
 *
 * Register your OAuth Application at https://osu.ppy.sh/home/account/edit#oauth
 * Set Application Callback URL to:
 *     http://127.0.0.1:42069/callback
 */
object OAuthConfig {
    val CLIENT_ID: String = BuildConfig.OSU_CLIENT_ID
    val CLIENT_SECRET: String = BuildConfig.OSU_CLIENT_SECRET

    val DEFAULT_CLIENT_ID: String = CLIENT_ID
    val DEFAULT_CLIENT_SECRET: String = CLIENT_SECRET

    const val LOOPBACK_HOST: String = "127.0.0.1"
    const val LOOPBACK_PORT: Int = 42069
    const val REDIRECT_URI: String = "http://127.0.0.1:42069/callback"

    const val CUSTOM_SCHEME: String = "osuchat"
    const val CUSTOM_HOST: String = "oauth"
    const val CUSTOM_PATH_PREFIX: String = "/callback"
    const val CUSTOM_SCHEME_REDIRECT_URI: String = "osuchat://oauth/callback"

    const val AUTHORIZE_URL: String = "https://osu.ppy.sh/oauth/authorize"
    const val TOKEN_URL: String = "https://osu.ppy.sh/oauth/token"
    const val BASE_API_URL: String = "https://osu.ppy.sh/"
    const val PUBLIC_MIRROR_URL: String = "https://catboy.best/"

    val SCOPES: List<String> = listOf(
        "public",
        "identify",
        "friends.read",
        "chat.write",
        "chat.read",
        "chat.write_manage"
    )
}
