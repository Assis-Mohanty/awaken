import type { Env } from "./env.ts";

/**
 * Play Integrity Standard API verification (ARCH §3.8 step 3, PROMPTS.md Phase 14 item 2).
 *
 * Auth: a Google service-account JWT-bearer exchange (RFC 7523) against https://oauth2.googleapis.com/token,
 * signed with Web Crypto (RSASSA-PKCS1-v1_5 / SHA-256) since there's no google-auth-library in the Workers
 * runtime. The resulting access token is cached in module scope until shortly before it expires. Verify the
 * endpoint and verdict field names against https://developer.android.com/google/play/integrity/verdicts and
 * https://developers.google.com/android/reference/rest/playintegrity before rotating the service account.
 *
 * Staging accepts `X-Debug-Key` instead of a real token (ARCH §3.7 "sideloaded debug APKs fail Play Integrity");
 * production ALWAYS requires a real token, even if DEBUG_KEY happens to be set.
 */

const PACKAGE_NAME = "com.assismohanty.awaken";
const INTEGRITY_SCOPE = "https://www.googleapis.com/auth/playintegrity";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const TOKEN_MAX_AGE_MS = 5 * 60 * 1000;

export type IntegrityOutcome = { ok: true } | { ok: false; reason: string };

interface ServiceAccountKey {
  client_email: string;
  private_key: string;
}

let cachedToken: { accessToken: string; expiresAtMs: number } | null = null;

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function importSigningKey(pem: string): Promise<CryptoKey> {
  const der = pemToDer(pem);
  return crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

async function signJwt(serviceAccount: ServiceAccountKey): Promise<string> {
  const nowSec = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: serviceAccount.client_email,
    scope: INTEGRITY_SCOPE,
    aud: TOKEN_URL,
    iat: nowSec,
    exp: nowSec + 3600,
  };
  const encoder = new TextEncoder();
  const signingInput = `${base64UrlEncode(encoder.encode(JSON.stringify(header)))}.${base64UrlEncode(encoder.encode(JSON.stringify(claims)))}`;
  const key = await importSigningKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(signingInput));
  return `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;
}

async function getAccessToken(serviceAccountJson: string): Promise<string> {
  const now = Date.now();
  if (cachedToken && cachedToken.expiresAtMs - 60_000 > now) return cachedToken.accessToken;

  const serviceAccount: ServiceAccountKey = JSON.parse(serviceAccountJson);
  const assertion = await signJwt(serviceAccount);
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${encodeURIComponent(assertion)}`,
  });
  if (!response.ok) throw new Error(`token exchange failed: ${response.status}`);
  const body = (await response.json()) as { access_token: string; expires_in: number };
  cachedToken = { accessToken: body.access_token, expiresAtMs: now + body.expires_in * 1000 };
  return body.access_token;
}

interface DecodedIntegrityToken {
  tokenPayloadExternal?: {
    requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string };
    appIntegrity?: { appRecognitionVerdict?: string };
    deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  };
}

async function decodeIntegrityToken(accessToken: string, integrityToken: string): Promise<DecodedIntegrityToken> {
  const response = await fetch(`https://playintegrity.googleapis.com/v1/${PACKAGE_NAME}:decodeIntegrityToken`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ integrity_token: integrityToken }),
  });
  if (!response.ok) throw new Error(`decodeIntegrityToken failed: ${response.status}`);
  return response.json();
}

/** SHA-256 of the exact request body bytes, standard base64 (matches the Android client's requestHash, ARCH §3.7). */
export async function requestHashOf(rawBody: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", rawBody);
  let binary = "";
  for (const b of new Uint8Array(digest)) binary += String.fromCharCode(b);
  return btoa(binary);
}

export async function verifyIntegrity(params: {
  env: Env;
  integrityToken: string | null;
  debugKey: string | null;
  expectedRequestHash: string;
}): Promise<IntegrityOutcome> {
  const { env, integrityToken, debugKey, expectedRequestHash } = params;
  const isStaging = env.ENVIRONMENT === "staging";

  if (isStaging && debugKey !== null && env.DEBUG_KEY && debugKey === env.DEBUG_KEY) {
    return { ok: true };
  }

  if (!integrityToken) return { ok: false, reason: "missing X-Integrity-Token" };
  if (!env.INTEGRITY_SERVICE_ACCOUNT_JSON) return { ok: false, reason: "proxy misconfigured: no service account" };

  try {
    const accessToken = await getAccessToken(env.INTEGRITY_SERVICE_ACCOUNT_JSON);
    const decoded = await decodeIntegrityToken(accessToken, integrityToken);
    const payload = decoded.tokenPayloadExternal;
    if (!payload) return { ok: false, reason: "empty verdict payload" };

    if (payload.requestDetails?.requestPackageName !== PACKAGE_NAME) return { ok: false, reason: "package mismatch" };
    if (payload.requestDetails?.requestHash !== expectedRequestHash) return { ok: false, reason: "requestHash mismatch" };

    const timestampMillis = Number(payload.requestDetails?.timestampMillis ?? 0);
    if (!timestampMillis || Date.now() - timestampMillis > TOKEN_MAX_AGE_MS) {
      return { ok: false, reason: "token too old" };
    }

    if (payload.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") {
      return { ok: false, reason: "app not PLAY_RECOGNIZED" };
    }
    if (!payload.deviceIntegrity?.deviceRecognitionVerdict?.includes("MEETS_DEVICE_INTEGRITY")) {
      return { ok: false, reason: "device does not meet integrity" };
    }

    return { ok: true };
  } catch (error) {
    return { ok: false, reason: error instanceof Error ? error.message : "integrity check failed" };
  }
}
