# OsuCompanion

OsuCompanion is an open-source Android companion client for **osu!** (lazer & stable) combining Bancho IRC chat, multiplayer tournament referee tools, player & beatmap analytics, an on-device `rosu-pp` PP simulator, score card export, and home-screen widgets.

Built natively in Kotlin with Jetpack Compose, Material 3, Room, Hilt, and WebAssembly `rosu-pp`.

---

## Features Overview

- **Bancho IRC & Direct Messaging**
  - Connect to `irc.ppy.sh:6667` with automatic reconnect, channel join/part, inline beatmap & user card embeds, `/np`, `/rs`, `/c`, `/me`, mention alerts, bookmarks, global message search, and chat log export (`.txt` / `.md`).
- **Multiplayer (`#mp_*`) Referee Console**
  - Live lobby roster, team & slot tracking, mappool manager with pick/ban progression, one-tap `!mp` referee commands, match setup templates, custom referee macros, and post-match analytics.
- **osu!lazer & Classic (`CL`) Score Clarity**
  - Fetches osu! API v2 (`x-api-version: 20220705`) scores with support for both legacy string mods and structured lazer mod objects.
  - Clearly labels **Lazer** vs **Classic (`CL`)** plays across player Top/Recent scores, Friend Score Feed, Beatmap Leaderboards, and exported Score Cards.
- **On-Device `rosu-pp` PP Simulator**
  - Runs `rosu-pp` locally via sandboxed WebAssembly (`assets/rosu_pp/`) for exact star rating, AR/CS/OD/HP, BPM, and PP calculations across all 4 rulesets (`osu!`, `taiko`, `catch`, `mania`).
  - Includes a **Lazer | Classic** toggle, mod chips, accuracy slider & presets, miss count, custom combo, and clock rate controls.
- **Explore, Beatmaps & Player Stats**
  - Beatmap search with filters, audio preview player with queue, `.osz` mirror downloads (`catboy.best` / `nerinyan.moe`), global & country rankings, player profile sheets with 24h session tracker, osu!track history charts, and medal catalog.
- **Friends, Tracking & Widgets**
  - Live friends list, Friend Score Feed, background PP/rank alert worker, customizable home-screen stat widgets, JSON backup & restore (excluding secrets), and in-app GitHub Releases updater with HTTPS-only and SHA-256 verification.

---

## Security, Privacy & Transparency

OsuCompanion is designed so you can inspect every network hop and credential boundary directly in this repository.

### OAuth2 Data Flow

```text
+-------------------+   1. Browser OAuth2 + PKCE    +------------------------------+
|  OsuCompanion     | ----------------------------> |  osu.ppy.sh/oauth/authorize  |
|  Android App      | <---------------------------- |  (user approves scopes)      |
|  (no secret in    |   2. Loopback redirect        +------------------------------+
|   APK)            |      127.0.0.1:42069/callback
|                   |
|                   |   3. POST / {grant_type, code / refresh_token}
|                   | ----------------------------> +------------------------------+
|                   |                               |  Cloudflare Worker Proxy     |
|                   |                               |  (/worker/index.js)          |
|                   |                               |  Injects OSU_CLIENT_ID &     |
|                   |                               |  OSU_CLIENT_SECRET           |
|                   |                               |  (stateless, zero logging)   |
|                   |                               +--------------+---------------+
|                   |                                              |
|                   |                               4. POST /oauth/token
|                   |                                              v
|                   |   5. Direct JSON response     +------------------------------+
|                   | <---------------------------- |  osu.ppy.sh/oauth/token      |
+-------------------+                               +------------------------------+
```

- **Zero `client_secret` in the APK:** Only the public `OSU_CLIENT_ID` is compiled into the app. Token exchange and refresh are forwarded through the stateless Cloudflare Worker whose full source code is published in [`worker/index.js`](worker/index.js).
- **Zero logging, zero storage, zero analytics, zero ads:** The OAuth proxy has no KV, D1, R2, or analytics bindings and never logs tokens, authorization codes, or user data. The Android app contains no ad SDKs or telemetry trackers.
- **Encrypted local credentials:** OAuth tokens (`access_token`, `refresh_token`) and the Bancho IRC password are encrypted at rest with **AES-256-GCM** using hardware-backed keys stored in `AndroidKeyStore`.
- **Backup safety:** `android:allowBackup="false"` is enforced in `AndroidManifest.xml` so Android cloud backup cannot extract local app state. User-initiated JSON backups explicitly exclude OAuth tokens and IRC passwords.
- **Honest disclosure on Bancho IRC transport:** osu!'s legacy Bancho IRC server (`irc.ppy.sh:6667`) only supports unencrypted TCP on port `6667` (it does not provide TLS). Avoid signing into Bancho IRC on untrusted public Wi-Fi networks, and rotate your IRC password at [osu! Account Settings](https://osu.ppy.sh/home/account/edit#legacy-api) if needed.

### Requested OAuth2 Scopes

| Scope | Why OsuCompanion Requests It |
| --- | --- |
| `public` | Read public player profiles, beatmaps, leaderboards, top/recent scores, and rankings. |
| `identify` | Identify your own signed-in osu! account (`/api/v2/me`) for profile stats, widgets, and IRC setup. |
| `friends.read` | Populate your friends list, online status indicators, and Friend Score Feed. |

---

## Download & Install

1. Open the [GitHub Releases](https://github.com/maidkedi/osu-companion/releases/latest) page.
2. Download the signed `OsuCompanion-v*.apk` asset (Android 8.0 / API 26 or newer required).
3. Install the APK on your device (enable "Install unknown apps" for your browser or file manager if prompted).
4. Future releases can be checked and verified directly from **Settings > App updates** inside OsuCompanion.

> **Upgrading from older OsuChat builds:** Package ID (`com.osuchat.app`), local database, preferences, and keystore aliases remain unchanged so existing installs upgrade smoothly without data loss.

---

## Build From Source & Self-Host Proxy

### Prerequisites

- Android Studio (Ladybug or newer) or Android SDK with **compileSdk 35** and **JDK 17**
- Optional: a [Cloudflare Workers](https://workers.cloudflare.com/) account if you want to host your own OAuth proxy

### Build Steps

1. Clone the repository and copy `local.properties.example` to `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
2. Configure `sdk.dir` in `local.properties` (and optionally `osu.clientId=<your_client_id>` if using your own osu! OAuth app).
3. Run unit tests and build a debug APK:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
4. To build a signed release APK, create a `keystore.properties` file in the repository root (gitignored):
   ```properties
   storeFile=path/to/your-release.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
   Then run:
   ```bash
   ./gradlew assembleRelease
   ```
5. To deploy your own OAuth token proxy instead of using the default Worker, follow [`worker/README.md`](worker/README.md).

---

## Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material 3, Coroutines & Flow
- **Architecture & DI:** MVVM, Dagger Hilt, Room (SQLite), Jetpack DataStore
- **Networking & Media:** Retrofit, OkHttp + Cronet transport, Coil, Media3 ExoPlayer
- **PP Engine:** `rosu-pp` (WebAssembly via sandboxed local bridge)
- **Auth Proxy:** Cloudflare Workers ([`worker/index.js`](worker/index.js))

---

## License

Licensed under the [MIT License](LICENSE).
osu! is a trademark of ppy Pty Ltd; OsuCompanion is an independent, unofficial open-source project.
