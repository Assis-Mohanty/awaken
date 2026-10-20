import { describe, expect, it, vi } from "vitest";
import type { Env } from "../src/env.ts";
import { requestHashOf, verifyIntegrity } from "../src/integrity.ts";

async function generateServiceAccountJson(): Promise<string> {
  const keyPair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const pkcs8 = (await crypto.subtle.exportKey("pkcs8", keyPair.privateKey)) as ArrayBuffer;
  let binary = "";
  for (const b of new Uint8Array(pkcs8)) binary += String.fromCharCode(b);
  const base64 = btoa(binary);
  const pem = `-----BEGIN PRIVATE KEY-----\n${base64.match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({ client_email: "path-oracle@test.iam.gserviceaccount.com", private_key: pem });
}

describe("verifyIntegrity (ARCH §3.8 step 3, §3.7 staging debug key)", () => {
  it("accepts the staging debug key without any network call", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const env = { ENVIRONMENT: "staging", DEBUG_KEY: "test-debug-key" } as Env;
    const result = await verifyIntegrity({ env, integrityToken: null, debugKey: "test-debug-key", expectedRequestHash: "irrelevant" });
    expect(result.ok).toBe(true);
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it("rejects a wrong debug key on staging and falls through to the real check", async () => {
    const env = { ENVIRONMENT: "staging", DEBUG_KEY: "test-debug-key" } as Env;
    const result = await verifyIntegrity({ env, integrityToken: null, debugKey: "wrong-key", expectedRequestHash: "x" });
    expect(result).toEqual({ ok: false, reason: "missing X-Integrity-Token" });
  });

  it("ALWAYS rejects the debug key in production, even if correct", async () => {
    const env = { ENVIRONMENT: "production", DEBUG_KEY: "test-debug-key" } as Env;
    const result = await verifyIntegrity({ env, integrityToken: null, debugKey: "test-debug-key", expectedRequestHash: "x" });
    expect(result).toEqual({ ok: false, reason: "missing X-Integrity-Token" });
  });

  it("verifies a real Play Integrity token end to end (mocked Google endpoints)", async () => {
    const serviceAccountJson = await generateServiceAccountJson();
    const env = { ENVIRONMENT: "production", INTEGRITY_SERVICE_ACCOUNT_JSON: serviceAccountJson } as Env;
    const rawBody = new TextEncoder().encode('{"hello":"world"}').buffer as ArrayBuffer;
    const expectedRequestHash = await requestHashOf(rawBody);

    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : (input as Request).url;
      if (url === "https://oauth2.googleapis.com/token") {
        return Response.json({ access_token: "fake-access-token", expires_in: 3600 });
      }
      if (url.includes("decodeIntegrityToken")) {
        return Response.json({
          tokenPayloadExternal: {
            requestDetails: {
              requestPackageName: "com.assismohanty.awaken",
              requestHash: expectedRequestHash,
              timestampMillis: String(Date.now()),
            },
            appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED" },
            deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
          },
        });
      }
      throw new Error(`unexpected fetch to ${url}`);
    });

    const result = await verifyIntegrity({ env, integrityToken: "fake-integrity-token", debugKey: null, expectedRequestHash });
    expect(result).toEqual({ ok: true });
    fetchMock.mockRestore();
  });

  it("rejects when the decoded requestHash doesn't match the request body sent", async () => {
    const serviceAccountJson = await generateServiceAccountJson();
    const env = { ENVIRONMENT: "production", INTEGRITY_SERVICE_ACCOUNT_JSON: serviceAccountJson } as Env;

    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : (input as Request).url;
      if (url === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "fake-access-token", expires_in: 3600 });
      return Response.json({
        tokenPayloadExternal: {
          requestDetails: { requestPackageName: "com.assismohanty.awaken", requestHash: "tampered-hash", timestampMillis: String(Date.now()) },
          appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED" },
          deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
        },
      });
    });

    const result = await verifyIntegrity({ env, integrityToken: "fake-integrity-token", debugKey: null, expectedRequestHash: "expected-hash" });
    expect(result).toEqual({ ok: false, reason: "requestHash mismatch" });
    fetchMock.mockRestore();
  });
});
