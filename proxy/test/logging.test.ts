import { describe, expect, it, vi } from "vitest";
import { handlePlanPath } from "../src/routes/plan.ts";
import { handleWeekQuests } from "../src/routes/week.ts";
import { handleReport } from "../src/routes/reports.ts";
import { jsonRequest, testEnv, validConstraints, validPath } from "./helpers.ts";

/**
 * ARCH §3.8 "what the proxy logs" / PROMPTS.md Phase 14 item 5: "Write a test that fails if any request/response
 * field reaches the logger." Drives every route with fixtures carrying distinctive, greppable secret-shaped
 * values, captures every console.log/console.warn line, and asserts none of them contain those values.
 */
describe("logging never carries request/response bodies", () => {
  it("across plan, week and report routes", async () => {
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => undefined);
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);

    const SECRET_GOAL = "UNIQUE_GOAL_TOKEN_f3a9c1";
    const SECRET_NOTES = "UNIQUE_NOTES_TOKEN_7b2e4d";
    const SECRET_MILESTONE_TITLE = "UNIQUE_MILESTONE_TOKEN_9d1f20";
    const SECRET_BLUEPRINT_TITLE = "Secret Quest Token 4c8a";
    const SECRET_REPORT_TITLE = "Secret Report Token 6e5b";

    const planEnv = testEnv();
    planEnv.AI.run = (async () => ({
      response: JSON.stringify({
        milestones: Array.from({ length: 4 }, (_, i) => ({
          title: i === 0 ? SECRET_MILESTONE_TITLE : `Milestone ${i}`,
          summary: "Summary.",
          exitCriterion: "Criterion.",
        })),
      }),
      usage: { total_tokens: 42 },
    })) as never;
    await handlePlanPath(
      jsonRequest("https://path-oracle.test/v1/paths/plan", {
        requestId: crypto.randomUUID(),
        path: { ...validPath, goal: SECRET_GOAL, notes: SECRET_NOTES },
        constraints: validConstraints,
      }),
      planEnv,
    );

    const weekEnv = testEnv();
    weekEnv.AI.run = (async () => ({
      response: JSON.stringify({
        suggestAdvance: false,
        blueprints: [
          {
            title: SECRET_BLUEPRINT_TITLE,
            description: "Description.",
            stat: "INT",
            unit: "PROBLEMS",
            target: 2,
            effort: "NORMAL",
            estMinutes: 30,
            mode: "SELF",
            tags: [],
            rationale: "Rationale.",
          },
        ],
      }),
      usage: { total_tokens: 55 },
    })) as never;
    await handleWeekQuests(
      jsonRequest(
        "https://path-oracle.test/v1/paths/week",
        {
          requestId: crypto.randomUUID(),
          path: { ...validPath, goal: SECRET_GOAL },
          milestone: { title: "Foundations", summary: "Summary.", exitCriterion: "Criterion." },
          progressionSignal: "HOLD",
          maxBlueprints: 1,
          lastWeek: [],
          constraints: validConstraints,
        },
        { "x-path-id": crypto.randomUUID() },
      ),
      weekEnv,
    );

    const reportsEnv = testEnv();
    await handleReport(
      jsonRequest("https://path-oracle.test/v1/reports", {
        promptVersion: "week_quests.v1",
        reason: "other",
        title: SECRET_REPORT_TITLE,
        description: "Description.",
      }),
      reportsEnv,
    );

    const secrets = [SECRET_GOAL, SECRET_NOTES, SECRET_MILESTONE_TITLE, SECRET_BLUEPRINT_TITLE, SECRET_REPORT_TITLE];
    const loggedLines = [...logSpy.mock.calls, ...warnSpy.mock.calls].map((args) => args.join(" "));
    expect(loggedLines.length).toBeGreaterThan(0);
    for (const line of loggedLines) {
      for (const secret of secrets) {
        expect(line).not.toContain(secret);
      }
    }

    logSpy.mockRestore();
    warnSpy.mockRestore();
  });
});
