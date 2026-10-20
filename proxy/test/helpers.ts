import { env as poolEnv } from "cloudflare:workers";
import { vi } from "vitest";
import type { Env } from "../src/env.ts";

/**
 * Real RATE_LIMITER / BUDGET_COUNTER / REPORTS_DB from the Miniflare pool (wrangler.test.toml), with a fake `AI`
 * (Workers AI has no local simulation, see wrangler.test.toml's own comment) and a fake `IP_RATE_LIMITER` (the
 * `ratelimits` binding is likewise remote-only). `env.ENVIRONMENT = "staging"` and `env.DEBUG_KEY` let tests take
 * the ARCH §3.7 debug-key path instead of a real Play Integrity token.
 */
export function testEnv(overrides: Partial<Env> = {}): Env {
  return {
    ...(poolEnv as Env),
    AI: { run: vi.fn() } as unknown as Env["AI"],
    IP_RATE_LIMITER: { limit: vi.fn().mockResolvedValue({ success: true }) },
    ...overrides,
  };
}

export function freshInstallId(): string {
  return crypto.randomUUID();
}

export function jsonRequest(url: string, body: unknown, headers: Record<string, string> = {}): Request {
  return new Request(url, {
    method: "POST",
    headers: { "content-type": "application/json", "x-install-id": freshInstallId(), "x-debug-key": "test-debug-key", ...headers },
    body: JSON.stringify(body),
  });
}

export const validPath = {
  goal: "Reach 1900 on Codeforces",
  currentLevel: "Rated 1400, weak at graphs and DP",
  notes: "No gym, only evenings",
  primaryStat: "INT",
  secondaryStat: null,
  activeDays: ["MON", "WED", "FRI"],
  questsPerDay: 1,
  weeklyMinutes: 180,
};

export const validConstraints = { equipment: [], injuryTags: [] };
