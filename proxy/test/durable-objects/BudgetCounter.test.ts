import { env } from "cloudflare:workers";
import { describe, expect, it, vi } from "vitest";
import type { Env } from "../../src/env.ts";

function counter(name: string) {
  return (env as Env).BUDGET_COUNTER.getByName(name);
}

// wrangler.test.toml: MONTHLY_BUDGET_USD = "1", AI_PRICE_PER_1K_TOKENS_USD = "0.01" → $1 cap / $0.00001 per token
// → 100,000 tokens exactly reaches 100%.
describe("BudgetCounter (ARCH §3.8 step 5 / GD §6.6.7 global cap)", () => {
  it("allows requests with no usage recorded yet", async () => {
    const budget = counter(crypto.randomUUID());
    const { allowed, percentUsed } = await budget.checkBudget();
    expect(allowed).toBe(true);
    expect(percentUsed).toBe(0);
  });

  it("accumulates usage across calls and blocks at 100%", async () => {
    const budget = counter(crypto.randomUUID());
    await budget.addUsage(60_000);
    expect((await budget.checkBudget()).allowed).toBe(true);

    await budget.addUsage(40_000); // 100,000 total → 100%
    const { allowed, percentUsed } = await budget.checkBudget();
    expect(percentUsed).toBeCloseTo(100, 5);
    expect(allowed).toBe(false);
  });

  it("logs a warning once usage crosses 80%", async () => {
    const budget = counter(crypto.randomUUID());
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);

    await budget.addUsage(85_000); // 85% of the $1 cap
    expect(warnSpy).toHaveBeenCalled();
    const logged = JSON.parse(warnSpy.mock.calls[0]?.[0] as string);
    expect(logged.note).toBe("budget_warning");
    expect(logged.percentUsed).toBeGreaterThanOrEqual(80);

    warnSpy.mockRestore();
  });

  it("does not warn below 80%", async () => {
    const budget = counter(crypto.randomUUID());
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);

    await budget.addUsage(50_000); // 50%
    expect(warnSpy).not.toHaveBeenCalled();

    warnSpy.mockRestore();
  });
});
