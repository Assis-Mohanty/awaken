#!/usr/bin/env tsx
/**
 * Contract drift check (CLAUDE.md "the proxy's zod schemas are checked against the same files in CI, so client
 * and proxy can't drift"): compiles the 4 JSON Schemas in /contracts with ajv (draft 2020-12, the same files
 * :engine's tests load) and runs a shared fixture battery through both ajv and src/schemas/path.ts's zod
 * schemas. If they ever disagree on a fixture, this script fails — that's the signal that a contract edit
 * landed in one place and not the other. It does not diff the schema ASTs: behavioural agreement over a
 * representative fixture set is cheap to maintain for 4 small schemas and catches the drift that matters
 * (a field renamed, an enum value added/removed, a bound changed).
 */
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import type { ZodType } from "zod";
import {
  pathPlanRequestSchema,
  pathPlanResponseSchema,
  pathWeekRequestSchema,
  pathWeekResponseSchema,
} from "../src/schemas/path.ts";

const here = dirname(fileURLToPath(import.meta.url));
const contractsDir = join(here, "..", "..", "contracts");
const engineFixturesDir = join(here, "..", "..", "engine", "src", "test", "resources", "paths");

function loadContract(name: string): Record<string, unknown> {
  return JSON.parse(readFileSync(join(contractsDir, name), "utf-8"));
}

const contracts = {
  planRequest: loadContract("path_plan.request.schema.json"),
  planResponse: loadContract("path_plan.response.schema.json"),
  weekRequest: loadContract("path_week.request.schema.json"),
  weekResponse: loadContract("path_week.response.schema.json"),
};

const ajv = new Ajv2020({ allErrors: true, strict: true });
// path_week.*.schema.json $ref into path_plan.request.schema.json#/$defs/..., so every contract must be
// registered before any of them are compiled.
for (const schema of Object.values(contracts)) ajv.addSchema(schema);

const compiled = {
  planRequest: ajv.compile(contracts.planRequest),
  planResponse: ajv.compile(contracts.planResponse),
  weekRequest: ajv.compile(contracts.weekRequest),
  weekResponse: ajv.compile(contracts.weekResponse),
};

type Case = { name: string; ajvValidate: (data: unknown) => boolean; zodSchema: ZodType; fixture: unknown };

const cases: Case[] = [];

// Response-side fixtures: reuse :engine's PathValidator test vectors (plan_*.json → plan response shape,
// v*.json → week response shape). These exercise enum typos, out-of-range numbers, and the "too few milestones"
// fixture that PathValidator handles but the contract itself forbids (minItems 4) — both ajv and zod must still
// agree it's schema-invalid.
for (const file of readdirSync(engineFixturesDir)) {
  const fixture = JSON.parse(readFileSync(join(engineFixturesDir, file), "utf-8"));
  const isPlan = file.startsWith("plan_");
  cases.push({
    name: `engine fixture ${file}`,
    ajvValidate: isPlan ? compiled.planResponse : compiled.weekResponse,
    zodSchema: isPlan ? pathPlanResponseSchema : pathWeekResponseSchema,
    fixture,
  });
}

// Request-side fixtures: hand-written, since :engine doesn't need request fixtures (it only validates responses).
const validPath = {
  goal: "Reach 1900 on Codeforces",
  currentLevel: "Rated 1400, weak at graphs and DP",
  notes: "No gym, only evenings",
  primaryStat: "INT",
  secondaryStat: null,
  activeDays: ["MON", "WED", "FRI"],
  questsPerDay: 1,
  weeklyMinutes: 180,
};
const validConstraints = { equipment: ["gym_access"], injuryTags: [] };

cases.push(
  {
    name: "plan request: valid",
    ajvValidate: compiled.planRequest,
    zodSchema: pathPlanRequestSchema,
    fixture: { requestId: "0a1b2c3d-0000-4000-8000-0123456789ab", path: validPath, constraints: validConstraints },
  },
  {
    // The contract does not require secondaryStat; a client that omits it must be accepted by both validators.
    name: "plan request: secondaryStat omitted",
    ajvValidate: compiled.planRequest,
    zodSchema: pathPlanRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: Object.fromEntries(Object.entries(validPath).filter(([key]) => key !== "secondaryStat")),
      constraints: validConstraints,
    },
  },
  {
    name: "plan request: goal too short",
    ajvValidate: compiled.planRequest,
    zodSchema: pathPlanRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: { ...validPath, goal: "Hi" },
      constraints: validConstraints,
    },
  },
  {
    name: "plan request: unknown stat",
    ajvValidate: compiled.planRequest,
    zodSchema: pathPlanRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: { ...validPath, primaryStat: "LUCK" },
      constraints: validConstraints,
    },
  },
  {
    name: "plan request: additional property rejected",
    ajvValidate: compiled.planRequest,
    zodSchema: pathPlanRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: validPath,
      constraints: validConstraints,
      extra: "nope",
    },
  },
  {
    name: "week request: valid",
    ajvValidate: compiled.weekRequest,
    zodSchema: pathWeekRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: validPath,
      milestone: { title: "Foundations", summary: "Lock in the basics.", exitCriterion: "Solve 10 problems." },
      progressionSignal: "UP",
      maxBlueprints: 3,
      lastWeek: [{ title: "Graph Hunt", outcome: "COMPLETED", feedback: "RIGHT" }],
      constraints: validConstraints,
    },
  },
  {
    name: "week request: maxBlueprints out of range",
    ajvValidate: compiled.weekRequest,
    zodSchema: pathWeekRequestSchema,
    fixture: {
      requestId: "0a1b2c3d-0000-4000-8000-0123456789ab",
      path: validPath,
      milestone: { title: "Foundations", summary: "Lock in the basics.", exitCriterion: "Solve 10 problems." },
      progressionSignal: "UP",
      maxBlueprints: 15,
      lastWeek: [],
      constraints: validConstraints,
    },
  },
  {
    name: "week request: bad requestId",
    ajvValidate: compiled.weekRequest,
    zodSchema: pathWeekRequestSchema,
    fixture: {
      requestId: "not-a-uuid",
      path: validPath,
      milestone: { title: "Foundations", summary: "Lock in the basics.", exitCriterion: "Solve 10 problems." },
      progressionSignal: "UP",
      maxBlueprints: 3,
      lastWeek: [],
      constraints: validConstraints,
    },
  },
);

let failures = 0;
for (const { name, ajvValidate, zodSchema, fixture } of cases) {
  const ajvOk = ajvValidate(fixture);
  const zodOk = zodSchema.safeParse(fixture).success;
  if (ajvOk !== zodOk) {
    failures++;
    console.error(`DRIFT  ${name}: ajv=${ajvOk} zod=${zodOk}`);
  } else {
    console.log(`ok     ${name}: both ${ajvOk ? "accept" : "reject"}`);
  }
}

console.log(`\n${cases.length - failures}/${cases.length} cases agree.`);
if (failures > 0) {
  console.error(`\n${failures} case(s) disagree — the zod schemas in src/schemas/path.ts have drifted from /contracts.`);
  process.exit(1);
}
