import { z } from "zod";

/**
 * zod mirror of /contracts/path_*.schema.json, field-for-field (GD §6.6, ARCH §3.8).
 * `scripts/check-contracts.ts` is the drift check between this file and the JSON Schemas :engine also loads.
 */

const uuid = z.string().regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

const stat = z.enum(["STR", "INT", "WIL", "VIT", "CHA"]);

const weekday = z.enum(["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]);

const equipment = z.enum([
  "dumbbells",
  "kettlebell",
  "pullup_bar",
  "resistance_band",
  "jump_rope",
  "gym_access",
  "bicycle",
]);

const injuryTag = z.enum(["impact", "legs", "upper"]);

const path = z
  .object({
    goal: z.string().min(3).max(120),
    currentLevel: z.string().max(120),
    notes: z.string().max(280),
    primaryStat: stat,
    secondaryStat: stat.nullable().optional(),
    activeDays: z.array(weekday).min(1).max(7),
    questsPerDay: z.int().min(1).max(2),
    weeklyMinutes: z.int().min(30).max(900),
  })
  .strict();

const constraints = z
  .object({
    equipment: z.array(equipment).max(7),
    injuryTags: z.array(injuryTag).max(3),
  })
  .strict();

const milestone = z
  .object({
    title: z.string().min(1).max(48),
    summary: z.string().max(200),
    exitCriterion: z.string().max(140),
  })
  .strict();

const promptVersion = z.string().regex(/^[a-z_]+\.v[0-9]+$/);

export const pathPlanRequestSchema = z
  .object({
    requestId: uuid,
    path,
    constraints,
  })
  .strict();
export type PathPlanRequest = z.infer<typeof pathPlanRequestSchema>;

export const pathPlanResponseSchema = z
  .object({
    promptVersion,
    milestones: z.array(milestone).min(4).max(10),
  })
  .strict();
export type PathPlanResponse = z.infer<typeof pathPlanResponseSchema>;

/** What the model itself returns, before the proxy adds `promptVersion` (ARCH §3.8 step 9). */
export const modelPlanOutputSchema = z
  .object({
    milestones: z.array(milestone).min(4).max(10),
  })
  .strict();

const weekOutcome = z
  .object({
    title: z.string().max(48),
    outcome: z.enum(["PENDING", "COMPLETED", "EXPIRED", "SKIPPED", "REROLLED", "REPORTED"]),
    feedback: z.enum(["TOO_EASY", "RIGHT", "TOO_HARD"]).nullable().optional(),
  })
  .strict();

export const pathWeekRequestSchema = z
  .object({
    requestId: uuid,
    path,
    milestone,
    progressionSignal: z.enum(["UP", "HOLD", "DOWN"]),
    maxBlueprints: z.int().min(1).max(14),
    lastWeek: z.array(weekOutcome).max(14),
    constraints,
  })
  .strict();
export type PathWeekRequest = z.infer<typeof pathWeekRequestSchema>;

const blueprint = z
  .object({
    title: z.string().min(1).max(48),
    description: z.string().max(280),
    stat,
    unit: z.enum(["MINUTES", "REPS", "PAGES", "PROBLEMS", "ITEMS", "SESSIONS", "KM"]),
    target: z.int().min(0).max(1000),
    effort: z.enum(["EASY", "NORMAL", "HARD", "EXTREME"]),
    estMinutes: z.int().min(1).max(600),
    mode: z.enum(["SELF", "GATE"]),
    tags: z
      .array(z.enum(["impact", "legs", "upper", "outdoor", "indoor", "social", "quiet", "screen"]))
      .max(8),
    rationale: z.string().max(140),
  })
  .strict();

export const pathWeekResponseSchema = z
  .object({
    promptVersion,
    suggestAdvance: z.boolean(),
    blueprints: z.array(blueprint).max(14),
  })
  .strict();
export type PathWeekResponse = z.infer<typeof pathWeekResponseSchema>;

/** What the model itself returns, before the proxy adds `promptVersion`. */
export const modelWeekOutputSchema = z
  .object({
    suggestAdvance: z.boolean(),
    blueprints: z.array(blueprint).max(14),
  })
  .strict();

export const reportRequestSchema = z
  .object({
    promptVersion,
    reason: z.string().min(1).max(60),
    title: z.string().max(48),
    description: z.string().max(280),
  })
  .strict();
export type ReportRequest = z.infer<typeof reportRequestSchema>;

/** Converts the JSON Schema objects in /contracts into the raw object Workers AI's `json_schema` response_format
 * needs (no `$schema`/`$id`/`title`/`description` — the model only needs structural fields). */
export function toModelJsonSchema(jsonSchema: Record<string, unknown>): Record<string, unknown> {
  const { $schema, $id, title, description, $defs, ...rest } = jsonSchema;
  return rest;
}
