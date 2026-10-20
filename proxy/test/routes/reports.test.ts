import { describe, expect, it } from "vitest";
import { handleReport, sweepExpiredReports } from "../../src/routes/reports.ts";
import { jsonRequest, testEnv } from "../helpers.ts";

const REPORTS_URL = "https://path-oracle.test/v1/reports";

function validBody(overrides: Partial<Record<string, unknown>> = {}) {
  return { promptVersion: "week_quests.v1", reason: "needs_equipment", title: "Deep Work Siege", description: "Needs a gym I don't have.", ...overrides };
}

describe("POST /v1/reports", () => {
  it("202s and stores the report with no install ID (ARCH §3.8)", async () => {
    const env = testEnv();
    const response = await handleReport(jsonRequest(REPORTS_URL, validBody()), env);
    expect(response.status).toBe(202);

    const row = await env.REPORTS_DB.prepare("SELECT * FROM reports ORDER BY createdAtMs DESC LIMIT 1").first<Record<string, unknown>>();
    expect(row).toMatchObject({ promptVersion: "week_quests.v1", reason: "needs_equipment", title: "Deep Work Siege" });
    expect(Object.keys(row ?? {})).not.toContain("installId");
    expect(Object.keys(row ?? {})).not.toContain("installIdSha256");
  });

  it("400s on a schema violation", async () => {
    const env = testEnv();
    const response = await handleReport(jsonRequest(REPORTS_URL, validBody({ reason: "" })), env);
    expect(response.status).toBe(400);
  });

  it("401s without a valid integrity token or debug key", async () => {
    const env = testEnv();
    const response = await handleReport(jsonRequest(REPORTS_URL, validBody(), { "x-debug-key": "wrong" }), env);
    expect(response.status).toBe(401);
  });

  it("sweepExpiredReports deletes only rows past their TTL", async () => {
    const env = testEnv();
    const now = Date.now();
    await env.REPORTS_DB.prepare(
      "INSERT INTO reports (id, promptVersion, reason, title, description, createdAtMs, expiresAtMs) VALUES (?, ?, ?, ?, ?, ?, ?)",
    )
      .bind(crypto.randomUUID(), "week_quests.v1", "expired", "Old", "Old report", now - 1000, now - 500)
      .run();
    await env.REPORTS_DB.prepare(
      "INSERT INTO reports (id, promptVersion, reason, title, description, createdAtMs, expiresAtMs) VALUES (?, ?, ?, ?, ?, ?, ?)",
    )
      .bind(crypto.randomUUID(), "week_quests.v1", "fresh", "New", "New report", now, now + 1000 * 60 * 60 * 24 * 90)
      .run();

    await sweepExpiredReports(env);

    const remaining = await env.REPORTS_DB.prepare("SELECT reason FROM reports").all<{ reason: string }>();
    const reasons = remaining.results.map((r) => r.reason);
    expect(reasons).not.toContain("expired");
    expect(reasons).toContain("fresh");
  });
});
