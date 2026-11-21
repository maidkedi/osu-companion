# osu! Companion OAuth2 Token Proxy (Cloudflare Worker)

This directory contains the complete, auditable source code for the stateless Cloudflare Worker that handles osu! API v2 OAuth token exchange (`authorization_code`) and token refresh (`refresh_token`) for osu! Companion.

## Why This Proxy Exists

osu! API v2 treats OAuth applications as confidential clients and requires a `client_secret` on `POST https://osu.ppy.sh/oauth/token`. Shipping a `client_secret` inside an Android APK would allow anyone to extract it. Routing only the token exchange/refresh request through this minimal Worker keeps `OSU_CLIENT_SECRET` out of the APK while keeping all other API and IRC traffic direct from your device to `osu.ppy.sh`.

## Endpoints

- `GET /health` — returns `200 OK` with `{"ok":true}`.
- `POST /` — accepts JSON payloads for `grant_type: "authorization_code"` or `grant_type: "refresh_token"`, attaches `OSU_CLIENT_ID` and `OSU_CLIENT_SECRET` from Worker secrets, forwards the request to `https://osu.ppy.sh/oauth/token`, and returns the response directly with `Cache-Control: no-store`.

No KV namespace, database, storage bucket, or request/token logging is used.

## Self-Hosting Instructions

1. **Create an osu! OAuth application:**
   - Open [osu! Account Settings](https://osu.ppy.sh/home/account/edit#oauth) and register a new OAuth application.
   - Set **Application Callback URL** to:
     ```
     http://127.0.0.1:42069/callback
     ```
   - Note your numeric **Client ID** and **Client Secret**.

2. **Configure Wrangler:**
   ```bash
   cd worker
   cp wrangler.toml.example wrangler.toml
   ```
   Edit `name` inside `wrangler.toml` to your own Cloudflare Worker name.

3. **Set encrypted Cloudflare secrets:**
   ```bash
   npx wrangler login
   npx wrangler secret put OSU_CLIENT_ID
   npx wrangler secret put OSU_CLIENT_SECRET
   ```

4. **Deploy and verify:**
   ```bash
   npx wrangler deploy
   curl https://<your-worker-subdomain>.workers.dev/health
   # Expected: {"ok":true}
   ```

5. **Point your self-built Android app to your Worker:**
   - Update `OAUTH_TOKEN_PROXY_URL` in `app/src/main/java/com/osuchat/app/data/network/OAuthConfig.kt` to your deployed Worker URL.
   - Set `osu.clientId=<your-client-id>` in `local.properties` before building the APK.
