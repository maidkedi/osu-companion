package com.osuchat.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.ImageLoader
import com.google.android.gms.net.CronetProviderInstaller
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.net.cronet.okhttptransport.CronetInterceptor
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.local.BeatmapCacheDao
import com.osuchat.app.data.local.BookmarkedMessageDao
import com.osuchat.app.data.local.ChannelDao
import com.osuchat.app.data.local.FriendScoreEventDao
import com.osuchat.app.data.local.MappoolDao
import com.osuchat.app.data.local.MessageDao
import com.osuchat.app.data.local.OsuChatDatabase
import com.osuchat.app.data.local.PlayerProfileCacheDao
import com.osuchat.app.data.local.ProfileHistoryDao
import com.osuchat.app.data.local.SavedBeatmapDao
import com.osuchat.app.data.local.SelfProfileCacheDao
import com.osuchat.app.data.local.TrackedFriendDao
import com.osuchat.app.data.local.UserAliasDao
import com.osuchat.app.data.network.CloudflareChallengeManager
import com.osuchat.app.data.network.NerinyanApiService
import com.osuchat.app.data.network.OAuthConfig
import com.osuchat.app.data.network.OsuApiService
import com.osuchat.app.data.network.OsuAuthService
import com.osuchat.app.data.network.PublicBeatmapApiService
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.data.repository.ChatRepositoryImpl
import com.osuchat.app.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.chromium.net.CronetEngine
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExternalChatImageClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkAndDatabaseModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cloudflareChallengeManager: CloudflareChallengeManager,
        userPreferencesDataStore: UserPreferencesDataStore
    ): OkHttpClient {
        val headersInterceptor = CloudflareChallengeManager.createCompliantHeadersInterceptor {
            runCatching {
                runBlocking { userPreferencesDataStore.getOAuthTokens() }
            }.getOrNull()?.accessToken
        }

        val osuRateLimitMutex = Mutex()
        var lastOsuRequestAtMillis = 0L
        suspend fun awaitOsuRequestSlot() {
            osuRateLimitMutex.withLock {
                val elapsed = System.currentTimeMillis() - lastOsuRequestAtMillis
                if (elapsed in 0L until 300L) {
                    delay(300L - elapsed)
                }
                lastOsuRequestAtMillis = System.currentTimeMillis()
            }
        }
        val rateLimitInterceptor = Interceptor { chain ->
            val request = chain.request()
            if (!CloudflareChallengeManager.isOsuHost(request.url.host)) {
                return@Interceptor chain.proceed(request)
            }

            runBlocking { awaitOsuRequestSlot() }
            var response = chain.proceed(request)
            if ((response.code == 429 || response.code == 503) && !chain.call().isCanceled()) {
                response.close()
                runBlocking {
                    delay(1_500L)
                    awaitOsuRequestSlot()
                }
                if (!chain.call().isCanceled()) {
                    response = chain.proceed(request.newBuilder().build())
                }
            }
            response
        }

        val stripApiCookiesNetworkInterceptor = Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            if (CloudflareChallengeManager.isOsuHost(request.url.host) &&
                CloudflareChallengeManager.isCookieStrippedEndpoint(path)
            ) {
                chain.proceed(request.newBuilder().removeHeader("Cookie").build())
            } else {
                chain.proceed(request)
            }
        }
        val cloudflareInterceptor = Interceptor { chain ->
            val request = chain.request()
            val attemptStartedAtMillis = System.currentTimeMillis()
            val response = chain.proceed(request)
            val responseCode = response.code
            if ((responseCode != 403 && responseCode != 503) ||
                !CloudflareChallengeManager.isOsuHost(request.url.host) ||
                !request.method.equals("GET", ignoreCase = true) ||
                CloudflareChallengeManager.isOAuthEndpoint(request.url.encodedPath) ||
                CloudflareChallengeManager.isCookieStrippedEndpoint(request.url.encodedPath)
            ) {
                return@Interceptor response
            }

            val cfMitigated = response.header("cf-mitigated")
            val isCfChallenge = cfMitigated.equals("challenge", ignoreCase = true) || run {
                val peek = runCatching { response.peekBody(32_768L).string() }.getOrNull().orEmpty()
                peek.contains("cf-mitigated", ignoreCase = true) ||
                    peek.contains("Just a moment", ignoreCase = true)
            }
            if (!isCfChallenge) {
                return@Interceptor response
            }

            val failedCfClearance = cloudflareChallengeManager.cookieJar
                .loadForRequest(request.url)
                .firstOrNull { it.name.equals("cf_clearance", ignoreCase = true) }
                ?.value
            response.close()

            val callTimeout = chain.call().timeout()
            val prevNanos = callTimeout.timeoutNanos()
            val asyncTimeout = callTimeout as? okio.AsyncTimeout
            val extended = if (asyncTimeout != null && prevNanos > 0L) {
                val timedOut = asyncTimeout.exit()
                if (!timedOut) {
                    asyncTimeout.timeout(135, TimeUnit.SECONDS)
                    asyncTimeout.enter()
                    true
                } else {
                    false
                }
            } else {
                callTimeout.timeout(135, TimeUnit.SECONDS)
                false
            }

            val resolved = try {
                cloudflareChallengeManager.awaitChallengeResolutionBlocking(
                    url = CloudflareChallengeManager.DEFAULT_OSU_URL,
                    hadCfClearanceCookie = !failedCfClearance.isNullOrBlank(),
                    failedCfClearance = failedCfClearance,
                    attemptStartedAtMillis = attemptStartedAtMillis
                )
            } finally {
                if (extended && asyncTimeout != null && !chain.call().isCanceled()) {
                    val timedOut = asyncTimeout.exit()
                    if (!timedOut) {
                        asyncTimeout.timeout(prevNanos, TimeUnit.NANOSECONDS)
                        asyncTimeout.enter()
                    }
                } else if (!extended && prevNanos > 0L) {
                    callTimeout.timeout(prevNanos, TimeUnit.NANOSECONDS)
                }
            }

            if (!resolved) {
                throw java.io.IOException(
                    "Cloudflare verification cancelled or timed out (HTTP $responseCode)."
                )
            }

            val retryRequest = request.newBuilder().build()
            chain.proceed(retryRequest)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        val cronetInterceptor: Interceptor? = runCatching {
            runCatching { CronetProviderInstaller.installProvider(context) }
            val cronetEngine = CronetEngine.Builder(context)
                .enableHttp2(true)
                .enableQuic(true)
                .build()
            CronetInterceptor.newBuilder(cronetEngine).build()
        }.getOrNull()

        val builder = OkHttpClient.Builder()
            .cookieJar(cloudflareChallengeManager.cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(headersInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(cloudflareInterceptor)
            .addInterceptor(logging)
            .addNetworkInterceptor(stripApiCookiesNetworkInterceptor)
        if (cronetInterceptor != null) {
            // CronetInterceptor executes directly via Chromium's TLS/ALPN stack; run after header/rate-limit/logging interceptors.
            builder.addInterceptor(cronetInterceptor)
        }
        return builder.build()
    }

    @Volatile
    private var externalChatImageLoaderInstance: ImageLoader? = null

    fun getOrCreateExternalChatImageLoader(context: Context): ImageLoader {
        return externalChatImageLoaderInstance ?: synchronized(this) {
            externalChatImageLoaderInstance ?: buildExternalChatImageLoader(
                context.applicationContext,
                buildExternalChatImageOkHttpClient()
            ).also { externalChatImageLoaderInstance = it }
        }
    }

    private fun buildExternalChatImageOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "OsuChat-Android/1.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private fun buildExternalChatImageLoader(context: Context, client: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context.applicationContext)
            .okHttpClient(client)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    @Provides
    @Singleton
    @ExternalChatImageClient
    fun provideExternalChatImageOkHttpClient(): OkHttpClient =
        buildExternalChatImageOkHttpClient()

    @Provides
    @Singleton
    @ExternalChatImageClient
    fun provideExternalChatImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader = getOrCreateExternalChatImageLoader(context)

    @Provides
    @Singleton
    fun provideOsuAuthService(okHttpClient: OkHttpClient, gson: Gson): OsuAuthService {
        return Retrofit.Builder()
            .baseUrl(OAuthConfig.BASE_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OsuAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideOsuApiService(okHttpClient: OkHttpClient, gson: Gson): OsuApiService {
        return Retrofit.Builder()
            .baseUrl(OAuthConfig.BASE_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OsuApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePublicBeatmapApiService(okHttpClient: OkHttpClient, gson: Gson): PublicBeatmapApiService {
        return Retrofit.Builder()
            .baseUrl(OAuthConfig.PUBLIC_MIRROR_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PublicBeatmapApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNerinyanApiService(okHttpClient: OkHttpClient, gson: Gson): NerinyanApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.nerinyan.moe/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NerinyanApiService::class.java)
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracked_friends (
                    normalizedUsername TEXT NOT NULL PRIMARY KEY,
                    userId INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    avatarUrl TEXT NOT NULL,
                    topScoreIdsCsv TEXT NOT NULL,
                    lastCheckedAt INTEGER NOT NULL,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_beatmaps (
                    beatmapId INTEGER NOT NULL PRIMARY KEY,
                    beatmapsetId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    creator TEXT NOT NULL,
                    version TEXT NOT NULL,
                    starRating REAL NOT NULL,
                    bpm REAL NOT NULL,
                    mode TEXT NOT NULL,
                    coverUrl TEXT NOT NULL,
                    previewAudioUrl TEXT NOT NULL,
                    savedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_channels_isJoined_lastMessageTimestamp` ON `channels` (`isJoined`, `lastMessageTimestamp`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_player_profile_cache_userId` ON `player_profile_cache` (`userId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_saved_beatmaps_savedAt` ON `saved_beatmaps` (`savedAt`)"
            )
            db.execSQL(
                """
                UPDATE messages
                SET channelId = 'dm:' || REPLACE(LOWER(TRIM(SUBSTR(TRIM(channelId), 4))), ' ', '_')
                WHERE LOWER(TRIM(channelId)) LIKE 'dm\_%' ESCAPE '\'
                   OR (LOWER(TRIM(channelId)) LIKE 'dm:%' AND channelId != 'dm:' || REPLACE(LOWER(TRIM(SUBSTR(TRIM(channelId), 4))), ' ', '_'))
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE messages
                SET channelId = LOWER(TRIM(channelId))
                WHERE TRIM(channelId) LIKE '#%' AND channelId != LOWER(TRIM(channelId))
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OsuChatDatabase {
        return Room.databaseBuilder(
            context,
            OsuChatDatabase::class.java,
            "osu_chat.db"
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            OsuChatDatabase.MIGRATION_4_5,
            OsuChatDatabase.MIGRATION_5_6,
            OsuChatDatabase.MIGRATION_6_7,
            OsuChatDatabase.MIGRATION_7_8,
            OsuChatDatabase.MIGRATION_8_9
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChannelDao(db: OsuChatDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideMessageDao(db: OsuChatDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideUserAliasDao(db: OsuChatDatabase): UserAliasDao = db.userAliasDao()

    @Provides
    fun provideBeatmapCacheDao(db: OsuChatDatabase): BeatmapCacheDao = db.beatmapCacheDao()

    @Provides
    fun providePlayerProfileCacheDao(db: OsuChatDatabase): PlayerProfileCacheDao =
        db.playerProfileCacheDao()

    @Provides
    fun provideTrackedFriendDao(db: OsuChatDatabase): TrackedFriendDao = db.trackedFriendDao()

    @Provides
    fun provideSavedBeatmapDao(db: OsuChatDatabase): SavedBeatmapDao = db.savedBeatmapDao()

    @Provides
    fun provideSelfProfileCacheDao(db: OsuChatDatabase): SelfProfileCacheDao = db.selfProfileCacheDao()

    @Provides
    fun provideMappoolDao(db: OsuChatDatabase): MappoolDao = db.mappoolDao()

    @Provides
    fun provideBookmarkedMessageDao(db: OsuChatDatabase): BookmarkedMessageDao = db.bookmarkedMessageDao()

    @Provides
    fun provideFriendScoreEventDao(db: OsuChatDatabase): FriendScoreEventDao = db.friendScoreEventDao()

    @Provides
    fun provideProfileHistoryDao(db: OsuChatDatabase): ProfileHistoryDao = db.profileHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
