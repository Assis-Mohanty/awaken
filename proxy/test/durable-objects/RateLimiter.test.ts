import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import type { Env } from "../../src/env.ts";

function limiter(name: string) {
  return (env as Env).RATE_LIMITER.getByName(name);
}

describe("RateLimiter (GD §6.6.7)", () => {
  it("allows PLAN_PATH up to 3 per day, then blocks", async () => {
    const rl = limiter(crypto.randomUUID());
    for (let i = 0; i < 3; i++) {
      const { allowed } = await rl.checkPlanPath(crypto.randomUUID());
      expect(allowed).toBe(true);
    }
    const { allowed } = await rl.checkPlanPath(crypto.randomUUID());
    expect(allowed).toBe(false);
  });

  it("never double-counts the same requestId (idempotent retries)", async () => {
    const rl = limiter(crypto.randomUUID());
    const requestId = crypto.randomUUID();
    for (let i = 0; i < 5; i++) {
      const { allowed } = await rl.checkPlanPath(requestId);
      expect(allowed).toBe(true); // same requestId every time: never consumes a second slot
    }
    // The install's 3/day budget is still intact for a genuinely new request.
    expect((await rl.checkPlanPath(crypto.randomUUID())).allowed).toBe(true);
    expect((await rl.checkPlanPath(crypto.randomUUID())).allowed).toBe(true);
    expect((await rl.checkPlanPath(crypto.randomUUID())).allowed).toBe(false);
  });

  it("WEEK_QUESTS: 1 automatic + 2 manual per Path per week, tracked independently", async () => {
    const rl = limiter(crypto.randomUUID());
    const pathId = crypto.randomUUID();

    expect((await rl.checkWeekQuests({ pathId, requestId: crypto.randomUUID(), manual: false })).allowed).toBe(true);
    expect((await rl.checkWeekQuests({ pathId, requestId: crypto.randomUUID(), manual: false })).allowed).toBe(false);

    expect((await rl.checkWeekQuests({ pathId, requestId: crypto.randomUUID(), manual: true })).allowed).toBe(true);
    expect((await rl.checkWeekQuests({ pathId, requestId: crypto.randomUUID(), manual: true })).allowed).toBe(true);
    expect((await rl.checkWeekQuests({ pathId, requestId: crypto.randomUUID(), manual: true })).allowed).toBe(false);
  });

  it("WEEK_QUESTS counters are per Path, not per install", async () => {
    const rl = limiter(crypto.randomUUID());
    const pathA = crypto.randomUUID();
    const pathB = crypto.randomUUID();

    expect((await rl.checkWeekQuests({ pathId: pathA, requestId: crypto.randomUUID(), manual: false })).allowed).toBe(true);
    expect((await rl.checkWeekQuests({ pathId: pathA, requestId: crypto.randomUUID(), manual: false })).allowed).toBe(false);
    expect((await rl.checkWeekQuests({ pathId: pathB, requestId: crypto.randomUUID(), manual: false })).allowed).toBe(true);
  });
});
