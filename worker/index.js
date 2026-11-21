/**
 * osu! Companion Stateless OAuth2 Token Proxy (Cloudflare Worker)
 *
 * Purpose:
 *   osu! API v2 requires a confidential `client_secret` when calling
 *   `POST https://osu.ppy.sh/oauth/token` for both `authorization_code` and
 *   `refresh_token` grants. Embedding a `client_secret` in an Android APK would
 *   expose it to extraction. This Worker injects `env.OSU_CLIENT_ID` and
 *   `env.OSU_CLIENT_SECRET` server-side and forwards the JSON response directly
 *   back to the client.
 *
 * Security & Privacy Guarantees:
 *   - Strictly stateless: no KV, D1, R2, Durable Objects, or analytics bindings.
 *   - Zero token logging: authorization codes, refresh tokens, access tokens,
 *     and secrets are never logged or persisted.
 *   - Strict grant & redirect validation: only `authorization_code` and
 *     `refresh_token` grants are accepted.
 */

const OSU_TOKEN_ENDPOINT = "https://osu.ppy.sh/oauth/token";
const ALLOWED_REDIRECT_URIS = new Set([
  "http://127.0.0.1:42069/callback",
  "osuchat://oauth/callback",
]);

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Pragma": "no-cache",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function readNonEmptyString(obj, key, maxLength = 4096) {
  const value = obj?.[key];
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (trimmed.length === 0 || trimmed.length > maxLength) return null;
  return trimmed;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return jsonResponse({ ok: true }, 200);
    }

    if (request.method !== "POST" || url.pathname !== "/") {
      return jsonResponse(
        { error: "not_found", message: "Use GET /health or POST /." },
        request.method === "POST" ? 404 : 405
      );
    }

    const clientId = typeof env.OSU_CLIENT_ID === "string" ? env.OSU_CLIENT_ID.trim() : "";
    const clientSecret = typeof env.OSU_CLIENT_SECRET === "string" ? env.OSU_CLIENT_SECRET.trim() : "";
    if (!clientId || !clientSecret) {
      return jsonResponse(
        { error: "server_misconfigured", message: "OAuth credentials are not configured." },
        500
      );
    }

    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.toLowerCase().includes("application/json")) {
      return jsonResponse(
        { error: "invalid_request", message: "Content-Type must be application/json." },
        415
      );
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return jsonResponse(
        { error: "invalid_json", message: "Request body must be valid JSON." },
        400
      );
    }

    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return jsonResponse(
        { error: "invalid_request", message: "JSON body must be an object." },
        400
      );
    }

    const grantType = readNonEmptyString(body, "grant_type", 64);
    let upstreamPayload;

    if (grantType === "authorization_code") {
      const code = readNonEmptyString(body, "code", 4096);
      const redirectUri = readNonEmptyString(body, "redirect_uri", 256);
      const codeVerifier = readNonEmptyString(body, "code_verifier", 256);

      if (!code || !redirectUri) {
        return jsonResponse(
          { error: "invalid_request", message: "Missing required code or redirect_uri." },
          400
        );
      }
      if (!ALLOWED_REDIRECT_URIS.has(redirectUri)) {
        return jsonResponse(
          { error: "invalid_redirect_uri", message: "Unsupported redirect_uri." },
          400
        );
      }

      upstreamPayload = {
        client_id: clientId,
        client_secret: clientSecret,
        grant_type: "authorization_code",
        code,
        redirect_uri: redirectUri,
      };
      if (codeVerifier) {
        upstreamPayload.code_verifier = codeVerifier;
      }
    } else if (grantType === "refresh_token") {
      const refreshToken = readNonEmptyString(body, "refresh_token", 4096);
      if (!refreshToken) {
        return jsonResponse(
          { error: "invalid_request", message: "Missing required refresh_token." },
          400
        );
      }

      upstreamPayload = {
        client_id: clientId,
        client_secret: clientSecret,
        grant_type: "refresh_token",
        refresh_token: refreshToken,
      };
    } else {
      return jsonResponse(
        { error: "unsupported_grant_type", message: "Only authorization_code and refresh_token are supported." },
        400
      );
    }

    let upstreamResponse;
    try {
      upstreamResponse = await fetch(OSU_TOKEN_ENDPOINT, {
        method: "POST",
        headers: {
          "Accept": "application/json",
          "Content-Type": "application/json",
          "User-Agent": "osu!",
        },
        body: JSON.stringify(upstreamPayload),
      });
    } catch {
      return jsonResponse(
        { error: "upstream_unreachable", message: "Failed to reach osu.ppy.sh token endpoint." },
        502
      );
    }

    const rawText = await upstreamResponse.text();
    let parsed;
    try {
      parsed = JSON.parse(rawText);
    } catch {
      return jsonResponse(
        {
          error: "upstream_invalid_response",
          message: `osu! token endpoint returned HTTP ${upstreamResponse.status}.`,
        },
        502
      );
    }

    return jsonResponse(parsed, upstreamResponse.status);
  },
};
