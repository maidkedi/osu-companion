# osu! Companion (`com.osuchat.app`)

Android companion app for osu! Bancho IRC chat, OAuth2 PKCE API v2 access, multiplayer (`!mp`) lobby tracking, beatmap exploration, Wasm PP calculation (`rosu_pp`), and home-screen stats widgets.

## Build & Tech Stack

- **Package / SDK:** `namespace` and `applicationId` = `com.osuchat.app`, `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, `versionCode = 4`, `versionName = "1.0.3"`, Java/Kotlin `17` (`app/build.gradle.kts`).
- **Plugins:** `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt`.
- **UI:** Jetpack Compose (`compose-bom`), Material 3, Navigation Compose, Activity Compose, Coil Compose.
- **DI & Async:** Hilt (`hilt-android` + KSP `hilt-compiler`), Kotlin Coroutines, Lifecycle (`runtime-compose`, `viewmodel-compose`, `process`).
- **Networking:** Retrofit + Gson, OkHttp + logging interceptor, Play Services Cronet + `cronet-okhttp`, AndroidX Browser (Custom Tabs), AndroidX WebKit (`WebView`).
- **Storage & Background:** Room (`room-runtime`, `room-ktx`, KSP `room-compiler`), DataStore Preferences, WorkManager (`work-runtime-ktx`), RemoteViews AppWidget (`BanchoStatsWidget`).
- **Unit Testing:** JUnit4, OkHttp `MockWebServer`, `kotlinx-coroutines-test` (`app/src/test/java/com/osuchat/app/`).

## Directory Structure (`app/src/main/`)

- `AndroidManifest.xml` — App entry points and permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- `assets/rosu_pp/` — `bridge.html`, `rosu_pp_js.js`, `rosu_pp_js_bg.wasm`.
- `java/com/osuchat/app/`
  - `OsuChatApp.kt`, `MainActivity.kt` (`singleTask`, `MAIN`/`LAUNCHER` + `VIEW`/`BROWSABLE` for `osuchat://oauth/callback`)
  - `di/AppModule.kt` — Hilt bindings (OkHttp/Cronet, Retrofit services, Room DB, DataStore).
  - `data/irc/` — `BanchoIrcClient.kt`, `IrcParser.kt`.
  - `data/local/` — `OsuChatDatabase.kt`, `Entities.kt`, `Daos.kt`.
  - `data/network/` — `OAuthConfig.kt`, `OAuthPkceManager.kt`, `CloudflareChallengeManager.kt`, `NetworkConnectivityMonitor.kt`, `OsuApiService.kt`, `OsuApiModels.kt`, `OsuApiPoller.kt`.
  - `data/preferences/` — `UserPreferencesDataStore.kt`.
  - `data/repository/` — `ChatRepositoryImpl.kt`.
  - `domain/` — `model/Models.kt`, `repository/ChatRepository.kt`, `usecase/ChatUseCases.kt`.
  - `service/` — `ChatConnectionService.kt` (`remoteMessaging` FGS), `ChatNotificationManager.kt`, `DirectReplyReceiver.kt`, `BootCompletedReceiver.kt`, `BeatmapAudioPlayer.kt`.
  - `ui/theme/` — `Color.kt`, `Theme.kt`, `Type.kt`.
  - `ui/components/` — `AvatarAndStatus.kt`, `BeatmapPpSimulatorPanel.kt`, `BeatmapPreviewCard.kt`, `ChatBackgroundContainer.kt`, `MpLobbyBoard.kt`, `PlayerStatsBottomSheet.kt`, `ScoreCard.kt`.
  - `ui/screens/` — `login/` (`LoginScreen.kt`, `LoginViewModel.kt`), `home/` (`HomeScreen.kt`, `HomeViewModel.kt`, `ExploreAndProfileSection.kt`, `MultiplayerHubSection.kt`), `chat/` (`ChatScreen.kt`, `ChatViewModel.kt`), `settings/` (`SettingsScreen.kt`, `SettingsViewModel.kt`).
  - `util/` — `BeatmapUrlParser.kt`, `MpLobbyParser.kt`, `OsuBeatmapCalculator.kt`, `RankPpMath.kt`, `RosuPpEngine.kt`.
  - `widget/` — `BanchoStatsWidget.kt`, `BanchoWidgetUpdateWorker.kt`.
- `res/` — Adaptive launcher icons (`mipmap-anydpi-v26/`), widget drawables/layouts/colors/info (`drawable/`, `layout/`, `values*/`, `xml/bancho_widget_info.xml`), and app strings/themes (`values/`, `values-night/`).

## Build & Verification Commands

Run Gradle CLI directly from the repository root (do not launch an emulator or run `adb install` unless explicitly requested):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon --console=plain
```

Unit tests live under `app/src/test/java/com/osuchat/app/`:
- `BeatmapUrlParserTest.kt`, `CloudflareProbeTest.kt`, `IrcParserTest.kt`, `IrcRateLimiterTest.kt`, `MpLobbyParserTest.kt`, `OsuApiPollerTest.kt`, `ParserUnitTest.kt`, `RoomMigrationTest.kt`, `util/ParsersAuditTest.kt`, `util/RankPpMathTest.kt`.

## Project Conventions

- Keep `applicationId` (`com.osuchat.app`) and OAuth callback URI (`osuchat://oauth/callback`) unchanged unless explicitly migrating OAuth credentials.
- Release builds run with `isMinifyEnabled = true` and `isShrinkResources = true`; keep `app/proguard-rules.pro` synchronized with any new Gson DTOs, `@JavascriptInterface` bridges, Room entities/DAOs, Cronet classes, or Hilt entry points.
- Never log OAuth tokens or Bancho IRC passwords.
