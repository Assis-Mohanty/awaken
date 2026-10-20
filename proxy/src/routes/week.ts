import type { Env } from "../env.ts";
import { logRequest, sha256Hex } from "../logging.ts";
import { callWorkersAi } from "../llm/workersAi.ts";
import { checkBudget, errorResponse, preChecks } from "../pipeline.ts";
import { modelWeekOutputSchema, pathWeekRequestSchema, pathWeekResponseSchema, toModelJsonSchema } from "../schemas/path.ts";
import weekResponseContract from "../../../contracts/path_week.response.schema.json";
import SYSTEM_PROMPT from "../../prompts/week_quests.v1.md";

const PROMPT_VERSION = "week_quests.v1";
const MODEL_JSON_SCHEMA = toModelJsonSchema(weekResponseContract as Record<string, unknown>);

/**
 * POST /v1/paths/week — ARCH §3.8 9-step pipeline; GD §6.6.2 WEEK_QUESTS.
 *
 * `pathId` and whether this is a manual retry aren't in the wire contract (GD §6.6.7 distinguishes "1 automatic +
 * 2 manual retries per Path per ISO week", but the request body has no field for it) — the Android client sends
 * them as headers: `X-Path-Id` (required, keys the per-Path counters) and `X-Retry-Kind: manual` (its absence
 * means automatic). This is a proxy-defined convention Phase 15's PathOracleClient must follow.
 */
export async function handleWeekQuests(request: Request, env: Env): Promise<Response> {
  const started = Date.now();
  const pre = await preChecks(request, env, pathWeekRequestSchema);
  if (!pre.ok) return pre.response;
  const { body, installId } = pre.value;

  const pathId = request.headers.get("x-path-id");
  if (!pathId) return errorResponse("invalid_request", "missing X-Path-Id");
  const manual = request.headers.get("x-retry-kind") === "manual";

  const rateLimiter = env.RATE_LIMITER.getByName(installId);
  const { allowed } = await rateLimiter.checkWeekQuests({ pathId, requestId: body.requestId, manual });
  if (!allowed) {
    return errorResponse("rate_limited", "WEEK_QUESTS limit reached for this Path this week", { "Retry-After": "3600" });
  }

  const budget = await checkBudget(env);
  if (!budget.ok) return budget.response;

  const llm = await callWorkersAi({
    env,
    systemPrompt: SYSTEM_PROMPT,
    hunterData: {
      goal: body.path.goal,
      currentLevel: body.path.currentLevel,
      notes: body.path.notes,
      milestone: body.milestone,
      progressionSignal: body.progressionSignal,
      maxBlueprints: body.maxBlueprints,
      lastWeek: body.lastWeek,
      constraints: body.constraints,
    },
    jsonSchema: MODEL_JSON_SCHEMA,
    outputSchema: modelWeekOutputSchema,
  });

  await env.BUDGET_COUNTER.getByName("global").addUsage(llm.ok ? llm.tokensUsed : 0);

  const installIdSha256 = await sha256Hex(installId);
  if (!llm.ok) {
    logRequest({ route: "/v1/paths/week", status: 502, latencyMs: Date.now() - started, installIdSha256 });
    return errorResponse("model_invalid_output", "the model's response didn't match the schema");
  }

  const responsePayload = pathWeekResponseSchema.parse({ promptVersion: PROMPT_VERSION, ...llm.data });
  logRequest({
    route: "/v1/paths/week",
    status: 200,
    latencyMs: Date.now() - started,
    promptVersion: PROMPT_VERSION,
    tokensOut: llm.tokensUsed,
    installIdSha256,
  });
  return Response.json(responsePayload);
}
