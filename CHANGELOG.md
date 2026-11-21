# osu! Companion — Architecture, Security & Changelog

Complete engineering overview and changelog for **osu! Companion** (`com.osuchat.app`), covering core integrations, network/Cloudflare hardening, security posture, database schema evolution, UI/performance refactoring, test coverage, and architectural direction.

---

## 1. System Overview & Tech Stack

- **Platform & Build:** Android (`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`), Kotlin `2.0.21`, AGP `8.7.3`, KSP `2.0.21-1.0.28`, R8 release minification (`isMinifyEnabled = true`, `isShrinkResources = true`).
- **UI & Architecture:** Jetpack Compose (BOM `2024.10.01`, Material 3, Navigation Compose `2.8.4`), MVVM with Hilt DI (`2.52`), Kotlin Coroutines (`1.9.0`), Coil (`2.7.0`).
- **Networking & Transport:** Retrofit (`2.11.0`) + Gson (`2.11.0`), OkHttp (`4.12.0`), Play Services Cronet (`18.1.1`) + `cronet-okhttp` (`0.1.1`) for Chromium TLS/HTTP2/QUIC fingerprinting, plus graceful OkHttp fallback when GMS Cronet is unavailable.
- **Persistence & Background Work:** Room (`2.6.1`, database schema `v6`), Jetpack DataStore Preferences (`1.1.1`) with Android Keystore AES-GCM encryption, WorkManager (`2.9.1`), foreground `ChatConnectionService`.
- **Domain Engines:** Custom TCP `BanchoIrcClient` with token-bucket `IrcRateLimiter`, `MpLobbyParser`, `IrcParser`, WebView-hosted `rosu_pp` Wasm PP calculator (`RosuPpEngine`), `BeatmapAudioPlayer`, and multi-theme AppWidget (`BanchoStatsWidget`).

---

## 2. What Is Built & Integrated

### A. Authentication, OAuth2 PKCE & Session Lifecycle
- **Dual-Mode OAuth2 PKCE (`OAuthConfig`, `OAuthPkceManager`, `MainActivity`, `AndroidManifest.xml`):**
  - Supports both `http://127.0.0.1:42069/callback` loopback redirect and `osuchat://oauth/callback` custom URI scheme.
  - Loopback `ServerSocket` binds with `reuseAddress = true` and `soTimeout = 300_000` (5 minutes) on an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)` so it survives Activity backgrounding during Chrome Custom Tabs login.
  - Responds with HTTP `200 OK` HTML redirecting to `osuchat://oauth/callback` while deduplicating code claims via `tryClaimCallbackCode` / `tryClaimCodeForExchange`.
  - `MainActivity.handleIntent` filters `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` and tracks `lastHandledOAuthUri` for idempotent redirect handling.
- **In-App Browser Fallback (`LoginScreen.kt`, `LoginViewModel.kt`):**
  - Secondary **"Sign in via In-App Browser (Fallback)"** dialog runs inside a WebView with `X-Requested-With` stripped (`WebSettingsCompat.setRequestedWithHeaderOriginAllowList(this, emptySet())`), WebView UA cleanup, `shouldOverrideUrlLoading` + `onPageStarted` single-fire redirect interception, and full spinner reset on cancellation.
- **Atomic Token Refresh (`OsuApiPoller.kt`, `UserPreferencesDataStore.kt`, `ChatRepositoryImpl.kt`):**
  - `tokenMutex` ensures concurrent `401` responses trigger only one `/oauth/token` refresh call.
  - New `access_token`, `refresh_token`, and `expires_at` values persist atomically inside a single DataStore `edit` transaction.
  - Revoked/expired refresh tokens (`400`/`401`) trigger atomic `clearOAuthTokens()` (preserving Room chat history), emit `OsuApiPollerEvent.AuthFailed("Session expired, please sign in again")`, and return navigation cleanly to `Routes.LOGIN`.

### B. Cloudflare Mitigation, Cronet Transport & Beatmap Mirror Fallback
- **Cronet + Compliant Headers (`AppModule.kt`, `CloudflareChallengeManager.kt`):**
  - `CronetInterceptor` (`enableHttp2(true)`, `enableQuic(true)`) runs after header, rate-limit, and logging interceptors for native Chromium TLS fingerprints on Android devices.
  - `createCompliantHeadersInterceptor` and `stripApiCookiesNetworkInterceptor` strip `Origin`, `Referer`, `Accept-Language`, `Upgrade-Insecure-Requests`, `Sec-Fetch-*`, and `sec-ch-ua*`, enforce `Accept: application/json`, and strip `Cookie` / `cf_clearance` / `__cf_bm` on `/oauth/token` and `/api/v2/*`.
  - Uses `User-Agent: osu!` after live probing showed `"osu-companion/1.0.0"` triggered Cloudflare `403` HTML responses on `/api/v2/me` and `/api/v2/beatmaps/1`.
- **Rate Limiting & Mirror Fallback (`AppModule.kt`, `OsuApiPoller.kt`, `OsuApiService.kt`, `RosuPpEngine.kt`):**
  - Outgoing `osu.ppy.sh` requests are spaced by a 300ms `Mutex` slot; `OsuApiPoller` honors `429` `Retry-After` backoff delays (`<= 10s` auto-retry).
  - `PublicBeatmapApiService` (`https://catboy.best/`) and `NerinyanApiService` (`https://api.nerinyan.moe/`) automatically serve beatmap metadata, search results, audio preview URLs, and `.osu` downloads (`RosuPpEngine.downloadOsuText`) when `osu.ppy.sh` returns `403`, `429`, or `5xx`.

### C. Bancho IRC Engine, `!mp` Multiplayer & Background Service
- **Flood Protection & Auth Recovery (`BanchoIrcClient.kt`, `IrcParser.kt`):**
  - Token-bucket `IrcRateLimiter` (capacity `5`, refill `1,200 ms`, minimum spacing `250 ms`) throttles `PRIVMSG`, `JOIN`, `PART`, and initial join bursts without blocking control frames (`PASS`/`NICK`/`USER`/`PING`/`PONG`/`QUIT`); outbound messages strip `\r`, `\n`, and `\u0000`.
  - Detects IRC `464`/`465` (`ERR_PASSWDMISMATCH` / `"Bad authentication token"`), immediately stops auto-reconnect loops, and displays a one-tap recovery link to `https://osu.ppy.sh/home/account/edit#legacy-api` in `LoginScreen`.
- **Multiplayer (`MpLobbyParser.kt`) & Foreground Service (`ChatConnectionService.kt`, `ChatNotificationManager.kt`):**
  - Parses `!mp settings`, `!mp host`, `!mp mods`, `!mp map` (with or without `:`), `!mp start`/finish/abort/close, `!mp set` (`Changed match settings to ...`), `Disabled FreeMod`, `TeamVS`, `ScoreV2`, and usernames with spaces/brackets.
  - `ChatConnectionService` strictly honors `isExplicitlyDisconnected`, cancels its state collector in `onDestroy()`, uses `.distinctUntilChanged()` to prevent redundant notification updates, and sets `.setOnlyAlertOnce(true)`.

### D. Wasm PP Engine (`rosu_pp`), Calculators & Audio Player
- **`RosuPpEngine.kt` & `bridge.html`:**
  - Enforces a 50 MB LRU disk cache cap (`osu_cache`) and 16 MB bounded download read.
  - Recovers from WebView renderer crashes (`onRenderProcessGone`), guards stale callbacks via `bridgeGeneration`, resets WebView on 5s execution timeout, and escapes/sanitizes all `.osu` payloads, mod tokens, and bitmasks before `evaluateJavascript`.
- **`OsuBeatmapCalculator.kt` & `RankPpMath.kt`:**
  - Sanitizes `NaN`/non-finite inputs and maps `"catch"` / `"ctb"` aliases cleanly to `"fruits"` rank estimation curves.
- **`BeatmapAudioPlayer.kt`:**
  - Integrates API 26+ `AudioFocusRequest` (`AUDIOFOCUS_GAIN_TRANSIENT`), pauses automatically on `ACTION_AUDIO_BECOMING_NOISY` (headphone/Bluetooth disconnect) with leak-safe receiver cleanup, and uses `PlayerState` + `playerGeneration` guards to eliminate `MediaPlayer` `IllegalStateException`s on rapid track switches.

### E. Room Database v6 & Flow Optimization
- **Schema Migration `v5 -> v6` (`OsuChatDatabase.kt`, `Entities.kt`, `AppModule.kt`):**
  - Added 12 `@Index` annotations across `channels`, `user_aliases`, `beatmap_cache`, `player_profile_cache`, `tracked_friends`, `saved_beatmaps`, and `self_profile_cache`, backed by `OsuChatDatabase.MIGRATION_5_6`.
- **Bounded Queries & Off-Main-Thread Flows (`Daos.kt`, `ChatRepository.kt`, `ChatRepositoryImpl.kt`):**
  - Replaced unbounded message queries with windowed `observeRecentMessagesForChannel` (`DEFAULT_MESSAGE_WINDOW = 200`, `MAX_MESSAGE_WINDOW = 1000`), `getMessagesBefore`, and `getLatestMessageId`.
  - Applied `.distinctUntilChanged()` and `.flowOn(Dispatchers.IO)` across repository streams.

### F. Compose UI, Release Minification & AppWidget Reliability
- **osu!lazer UI & Recomposition (`Type.kt`, screens, components):**
  - Scoped `fontFeatureSettings = "tnum"` exclusively to numeric/stat displays via `TnumTextStyle` / `.tnum()` while preserving proportional typography for chat messages and prose.
  - Annotated UI states (`HomeUiState`, `ChatUiState`, domain models) with `@Immutable`, added stable `key`s + `contentType`s across all lazy lists, memoized list callbacks, and harmonized cards to `surfaceContainerLow` + `0.5.dp` `outlineVariant` borders.
- **R8 & ProGuard (`app/proguard-rules.pro`):**
  - Hardened keep rules for `RosuJsBridge` (`@JavascriptInterface`), Room `@Entity`/`@Dao`/`@Database`/`Migration`, Cronet (`org.chromium.net.**`, `com.google.net.cronet.**`, `com.google.android.gms.net.**`), Retrofit/Gson `TypeToken`, and Hilt/WorkManager entrypoints.
  - Post-build inspection of `mapping.txt` and `seeds.txt` confirmed zero stripped bridge methods or missing classes.
- **AppWidget (`BanchoStatsWidget.kt`, `BanchoWidgetUpdateWorker.kt`):**
  - Enforces safe bitmap sizing (`MAX_WIDGET_BITMAP_PX = 128`, `256 KB` cap), atomic temp-file avatar caching, avatar-less fallback rendering against `TransactionTooLargeException`, `ExistingWorkPolicy.KEEP` coalescing, `isStopped` guards, and consistent layout rendering across osu! Dark, Material You, and Nothing OS themes.

---

## 3. Security Updates Summary

1. **Credentials Encrypted at Rest:** Added `CredentialCipher` in `UserPreferencesDataStore` using Android Keystore (`AES/GCM/NoPadding`, 256-bit key `osu_chat_credential_key_v1`, unique 12-byte GCM IV per write, `enc:v1:` prefix) for OAuth `access_token`, `refresh_token`, and Bancho `irc_password`, with transparent one-way migration from legacy plaintext values.
2. **Release Logging Locked Down:** `HttpLoggingInterceptor` is `Level.NONE` in release (`!BuildConfig.DEBUG`) and redacts `Authorization`, `Cookie`, and `Set-Cookie` in debug. Codebase scan confirmed zero plaintext secret/token logs.
3. **WebView & Bridge Hardening:** `RosuPpEngine` sanitizes/escapes JS strings, validates Base64/mod inputs, and isolates renderer crashes; In-App OAuth fallback strips `X-Requested-With` headers.
4. **Input & Socket Hygiene:** `BanchoIrcClient` strips `\r`, `\n`, and `\u0000` from outbound IRC messages to block IRC command injection.
5. **Known Architectural Debt (Direction):** `OAuthConfig.kt` still embeds a client-side OAuth `CLIENT_ID` / `CLIENT_SECRET` (suitable for companion/dev builds; production public distribution should use a backend token-exchange proxy or public-client PKCE flow supported by osu! without embedded secrets) and `release` `buildType` currently uses the debug signing config.

---

## 4. Direction & Next Steps

1. **Production Release Signing & Secret Management:** Configure a dedicated release keystore via environment/CI secrets (replacing `signingConfigs.getByName("debug")`) and move OAuth token exchange behind a lightweight backend proxy or secretless flow before Play Store distribution.
2. **Live Device Verification:** Run end-to-end QA on physical Android devices (with and without Google Play Services) covering Chrome Custom Tabs redirect, In-App WebView fallback, Play Services Cronet HTTP/3 negotiation, Wasm PP calculation, and AppWidget resizing.
3. **Database Exported Schemas & Paging UI:** Enable `exportSchema = true` with checked-in Room schema JSONs and wire UI scroll-back loading (`getMessagesBefore`) in `ChatScreen` to page older messages seamlessly beyond the initial 200-message window.

---

## 5. Verification Changelog & Artifacts

- **Unit Tests (`.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon --console=plain` — `BUILD SUCCESSFUL`, `63/63` passed, `0` failed, `0` skipped):**
  - `BeatmapUrlParserTest` (`7` tests)
  - `CloudflareProbeTest` (`1` live HTTP probe — `0` HTTP `403` responses)
  - `IrcParserTest` (`7` tests)
  - `IrcRateLimiterTest` (`9` tests)
  - `MpLobbyParserTest` (`8` tests)
  - `OsuApiPollerTest` (`5` `MockWebServer` + `CredentialCipher` tests)
  - `ParserUnitTest` (`3` tests)
  - `RoomMigrationTest` (`3` schema v6 + `MIGRATION_4_5` + `MIGRATION_5_6` tests)
  - `ParsersAuditTest` (`6` tests)
  - `RankPpMathTest` (`14` tests)
- **Built APKs:**
  - Debug: `app/build/outputs/apk/debug/app-debug.apk` (`22,845,669` bytes / `21.79` MiB)
  - Release (R8): `app/build/outputs/apk/release/app-release.apk` (`3,324,229` bytes / `3.17` MiB)
