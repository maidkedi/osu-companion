# osu! Companion

Android companion client for **osu! Bancho IRC chat**, **osu! API v2 (OAuth2)**, **multiplayer (`!mp`) tournament refereeing**, **beatmap exploration**, **on-device Wasm PP calculation (`rosu_pp`)**, and **home-screen stats widgets**.

## Key Features

- **Bancho IRC & OAuth v2:** Real-time channel and direct message chat with foreground keep-alive service, inline notification replies, keyword highlights, and user ignore controls.
- **Multiplayer & Tournament HUD:** Live `!mp` lobby board, active timer countdowns, mappool slot management, match setup presets, and post-match analytics.
- **Beatmap Discovery & PP Simulator:** Search and preview beatmaps, view diffspike telemetry charts, download `.osz` archives, and simulate star rating / PP on-device via `rosu_pp` WebAssembly.
- **Profile, Friends & Widgets:** Track friend scores, inspect rank/PP history charts, and pin customizable `4x2` / `2x2` home-screen widgets.
- **In-App Auto-Updater:** Checks GitHub Releases for newer signed APKs with SemVer comparison, progress tracking, and release notes.

## Building from Source

### 1. Prerequisites

- Android Studio or JDK 17+ and Android SDK (API 35)

### 2. Configure `local.properties`

Copy the example configuration file:

```bash
cp local.properties.example local.properties
```

Set `sdk.dir` to your local Android SDK path.

To enable built-in osu! OAuth2 login:
1. Create an OAuth application at [osu! Account Settings](https://osu.ppy.sh/home/account/edit#oauth).
2. Set **Application Callback URL** to `http://127.0.0.1:42069/callback`.
3. Fill in `osu.clientId` and `osu.clientSecret` inside `local.properties`.

Contributors can omit `osu.clientId` and `osu.clientSecret` to build with empty defaults and sign in via **Bancho IRC** (`irc.ppy.sh`) or enter a custom Client ID on the Login screen.

### 3. Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease --console=plain
```

### 4. Release Signing (Optional)

Release builds automatically sign with `keystore.properties` if present in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). If `keystore.properties` is absent, release builds fall back to the debug signing config so external contributors can run `assembleRelease` out of the box.

## License

Licensed under the [MIT License](LICENSE).
