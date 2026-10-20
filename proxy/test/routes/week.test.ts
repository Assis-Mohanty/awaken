import { describe, expect, it } from "vitest";
import { handleWeekQuests } from "../../src/routes/week.ts";
import { jsonRequest, testEnv, validConstraints, validPath } from "../helpers.ts";

const WEEK_URL = "https://path-oracle.test/v1/paths/week";

function validBody(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    requestId: crypto.randomUUID(),
    path: validPath,
    milestone: { title: "Foundations", summary: "Lock in the basics.", exitCriterion: "Solve 10 problems." },
    progressionSignal: "UP",
    maxBlueprints: 3,
    lastWeek: [],
    constraints: validConstraints,
    ...overrides,
  };
}

function fakeModelResponse(blueprints: unknown[], suggestAdvance = false) {
  return { response: JSON.stringify({ suggestAdvance, blueprints }), usage: { total_tokens: 200 } };
}

const VALID_BLUEPRINT = {
  title: "Graph Hunt",
  description: "Solve 2 problems tagged graphs rated 1500-1600.",
  stat: "INT",
  unit: "PROBLEMS",
  target: 2,
  effort: "HARD",
  estMinutes: 60,
  mode: "SELF",
  tags: ["quiet", "screen"],
  rationale: "Last contest lost points on graphs.",
};

/** `jsonRequest` picks a fresh random X-Install-Id by default; pass `installId` explicitly whenever several calls
 * in one test must look like the same hunter (e.g. per-Path WEEK_QUESTS counters, which live inside the
 * install's own RateLimiter instance — see RateLimiter.ts). */
function headers(pathId: string, installId: string, extra: Record<string, string> = {}) {
  return { "x-path-id": pathId, "x-install-id": installId, ...extra };
}

describe("POST /v1/paths/week", () => {
  it("200s with a schema-valid week on a well-formed request", async () => {
    const env = testEnv();
    env.AI.run = async () => fakeModelResponse([VALID_BLUEPRINT]) as never;

    const response = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(crypto.randomUUID(), crypto.randomUUID())), env);
    expect(response.status).toBe(200);
    const payload = (await response.json()) as { promptVersion: string; blueprints: unknown[] };
    expect(payload.promptVersion).toBe("week_quests.v1");
    expect(payload.blueprints).toHaveLength(1);
  });

  it("400s when X-Path-Id is missing", async () => {
    const env = testEnv();
    const response = await handleWeekQuests(jsonRequest(WEEK_URL, validBody()), env);
    expect(response.status).toBe(400);
  });

  it("automatic and manual WEEK_QUESTS retries for the same Path are rate-limited independently", async () => {
    const env = testEnv();
    env.AI.run = async () => fakeModelResponse([VALID_BLUEPRINT]) as never;
    const pathId = crypto.randomUUID();
    const installId = crypto.randomUUID();

    const automatic = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(pathId, installId)), env);
    expect(automatic.status).toBe(200);
    const secondAutomatic = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(pathId, installId)), env);
    expect(secondAutomatic.status).toBe(429);

    const manual1 = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(pathId, installId, { "x-retry-kind": "manual" })), env);
    expect(manual1.status).toBe(200);
    const manual2 = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(pathId, installId, { "x-retry-kind": "manual" })), env);
    expect(manual2.status).toBe(200);
    const manual3 = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(pathId, installId, { "x-retry-kind": "manual" })), env);
    expect(manual3.status).toBe(429);
  });

  it("502s when the model returns a blueprint missing a required field", async () => {
    const env = testEnv();
    const { rationale: _drop, ...incomplete } = VALID_BLUEPRINT;
    env.AI.run = async () => fakeModelResponse([incomplete]) as never;

    const response = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(crypto.randomUUID(), crypto.randomUUID())), env);
    expect(response.status).toBe(502);
  });

  it("carries suggestAdvance through to the response", async () => {
    const env = testEnv();
    env.AI.run = async () => fakeModelResponse([VALID_BLUEPRINT], true) as never;
    const response = await handleWeekQuests(jsonRequest(WEEK_URL, validBody(), headers(crypto.randomUUID(), crypto.randomUUID())), env);
    expect((await response.json<{ suggestAdvance: boolean }>()).suggestAdvance).toBe(true);
  });
});
