import { describe, expect, it } from "vitest";
import { handlePlanPath } from "../../src/routes/plan.ts";
import { jsonRequest, testEnv, validConstraints, validPath } from "../helpers.ts";

const PLAN_URL = "https://path-oracle.test/v1/paths/plan";

/**
 * Its own file: it deliberately maxes out the shared "global" BudgetCounter (wrangler.test.toml:
 * MONTHLY_BUDGET_USD = "1", AI_PRICE_PER_1K_TOKENS_USD = "0.01" → 100,000 tokens = 100%), and storage isolation
 * is per TEST FILE, not per test (confirmed empirically) — mixing this into plan.test.ts would exhaust the
 * budget for every other test there.
 */
describe("POST /v1/paths/plan — budget exhaustion (ARCH §3.8 step 5)", () => {
  it("503s once the monthly AI budget is spent, before any model call", async () => {
    const env = testEnv();
    await env.BUDGET_COUNTER.getByName("global").addUsage(100_000);

    env.AI.run = (async () => {
      throw new Error("must not call the model once the budget is exhausted");
    }) as never;

    const body = { requestId: crypto.randomUUID(), path: validPath, constraints: validConstraints };
    const response = await handlePlanPath(jsonRequest(PLAN_URL, body), env);
    expect(response.status).toBe(503);
    expect((await response.json<{ error: string }>()).error).toBe("budget_exhausted");
  });
});
