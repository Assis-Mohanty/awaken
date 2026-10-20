import type { Env } from "../env.ts";
import { logRequest, sha256Hex } from "../logging.ts";
import { callWorkersAi } from "../llm/workersAi.ts";
import { checkBudget, errorResponse, preChecks } from "../pipeline.ts";
import { modelPlanOutputSchema, pathPlanRequestSchema, pathPlanResponseSchema, toModelJsonSchema } from "../schemas/path.ts";
import planResponseContract from "../../../contracts/path_plan.response.schema.json";
import SYSTEM_PROMPT from "../../prompts/plan_path.v1.md";

const PROMPT_VERSION = "plan_path.v1";
const MODEL_JSON_SCHEMA = toModelJsonSchema(planResponseContract as Record<string, unknown>);

/** POST /v1/paths/plan — ARCH §3.8 9-step pipeline; GD §6.6.2 PLAN_PATH. */
export async function handlePlanPath(request: Request, env: Env): Promise<Response> {
  const started = Date.now();
  const pre = await preChecks(request, env, pathPlanRequestSchema);
  if (!pre.ok) return pre.response;
  const { body, installId } = pre.value;

  const rateLimiter = env.RATE_LIMITER.getByName(installId);
  const { allowed } = await rateLimiter.checkPlanPath(body.requestId);
  if (!allowed) {
    return errorResponse("rate_limited", "PLAN_PATH limit reached for this install", { "Retry-After": "3600" });
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
      constraints: body.constraints,
    },
    jsonSchema: MODEL_JSON_SCHEMA,
    outputSchema: modelPlanOutputSchema,
  });

  await env.BUDGET_COUNTER.getByName("global").addUsage(llm.ok ? llm.tokensUsed : 0);

  const installIdSha256 = await sha256Hex(installId);
  if (!llm.ok) {
    logRequest({ route: "/v1/paths/plan", status: 502, latencyMs: Date.now() - started, installIdSha256 });
    return errorResponse("model_invalid_output", "the model's response didn't match the schema");
  }

  const responsePayload = pathPlanResponseSchema.parse({ promptVersion: PROMPT_VERSION, ...llm.data });
  logRequest({
    route: "/v1/paths/plan",
    status: 200,
    latencyMs: Date.now() - started,
    promptVersion: PROMPT_VERSION,
    tokensOut: llm.tokensUsed,
    installIdSha256,
  });
  return Response.json(responsePayload);
}
