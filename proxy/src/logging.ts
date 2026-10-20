/**
 * ARCH §3.8 "what the proxy logs": route, status, latency, model tokens in/out, prompt version, SHA-256 of the
 * install ID, validation drop counts. Never request/response bodies. `LogEntry` has no field that could carry
 * one, so there's no `body`/`request`/`response` value for a caller to accidentally pass through — see
 * test/logging.test.ts, which asserts no logged line contains any fixture's field values.
 */
export interface LogEntry {
  route: "/v1/paths/plan" | "/v1/paths/week" | "/v1/reports";
  status: number;
  latencyMs: number;
  promptVersion?: string;
  tokensIn?: number;
  tokensOut?: number;
  installIdSha256?: string;
  dropCount?: number;
  note?: string;
}

export function logRequest(entry: LogEntry): void {
  console.log(JSON.stringify({ ts: new Date().toISOString(), ...entry }));
}

export async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
