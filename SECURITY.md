# Security Policy

## Supported Versions

Security fixes are applied to the latest release of OsuCompanion:

| Version | Supported |
| --- | --- |
| `1.0.3` (latest) | Yes |
| `< 1.0.3` | No — please upgrade to the latest release |

---

## Security Architecture

### Local Credential Protection
- **Encryption at rest:** OAuth2 `access_token`, `refresh_token`, and Bancho IRC passwords are encrypted using **AES-256-GCM** with hardware-backed keys managed by `AndroidKeyStore`.
- **No cloud auto-backup:** `android:allowBackup="false"` is set in `AndroidManifest.xml` to prevent Android Auto Backup from exporting local databases or preference files.
- **Secret-free JSON backups:** Manual export/import backups (`BackupRestoreRepository`) never include OAuth tokens, IRC passwords, or signing material.
- **Release log redaction:** OkHttp body logging is disabled in release builds so `Authorization` headers and token payloads are not written to Logcat.

### OAuth2 Proxy Scope (`worker/index.js`)
- No `client_secret` is bundled in the Android APK.
- Token exchange (`authorization_code`) and token refresh (`refresh_token`) are proxied through a minimal, stateless Cloudflare Worker whose full source code is published in [`worker/index.js`](worker/index.js).
- The Worker validates `grant_type` and `redirect_uri`, injects `OSU_CLIENT_ID` and `OSU_CLIENT_SECRET` from Cloudflare Worker Secrets, forwards directly to `https://osu.ppy.sh/oauth/token`, returns responses with `Cache-Control: no-store`, and does not log or store tokens, codes, or user identifiers.

### In-App Updater & File Sharing
- In-app update checks require **HTTPS-only** download URLs (`https://`) from GitHub Releases and verify the release asset's **SHA-256** digest when published by GitHub.
- `FileProvider` paths in `app/src/main/res/xml/file_paths.xml` are strictly scoped to dedicated subdirectories (`osz/`, `score_cards/`, `updates/`, `wallpapers/`, `Download/`, `Pictures/`).

### Bancho IRC Transport Note
- osu!'s legacy Bancho IRC server (`irc.ppy.sh:6667`) only accepts plaintext TCP connections on port `6667` and does not support TLS.
- All outgoing IRC lines, channel names, targets, usernames, and passwords are sanitized against CR/LF (`\r`, `\n`) and NUL (`\u0000`) command injection before being written to the socket.
- Because the upstream Bancho IRC server itself is unencrypted, avoid signing into Bancho IRC on untrusted networks and rotate your IRC password via [osu! Account Settings](https://osu.ppy.sh/home/account/edit#legacy-api) if you suspect exposure.

---

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Instead, report vulnerabilities privately using one of the following channels:
1. **GitHub Private Vulnerability Reporting** (preferred): Go to the repository's **Security** tab and click **Report a vulnerability**.
2. If Private Vulnerability Reporting is unavailable, contact the repository maintainer (`maidkedi`) privately via GitHub with:
   - Affected app version (`versionName` / `versionCode`)
   - Clear reproduction steps or proof-of-concept
   - Potential impact and any suggested remediation

We aim to acknowledge reports within **72 hours** and coordinate disclosure once a fix is published.
