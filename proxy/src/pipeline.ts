import type { ZodType, z } from "zod";
import type { Env } from "./env.ts";
import { requestHashOf, verifyIntegrity } from "./integrity.ts";

/** ARCH §3.8 error codes, steps 1-4 (cheap checks first, the model last). */
export type ApiErrorCode = "invalid_request" | "integrity_failed" | "rate_limited" | "budget_exhausted" | "model_invalid_output";

const STATUS: Record<ApiErrorCode, number> = {
  invalid_request: 400,
  integrity_failed: 401,
  rate_limited: 429,
  budget_exhausted: 503,
  model_invalid_output: 502,
};

export function errorResponse(code: ApiErrorCode, message: string, extraHeaders?: HeadersInit): Response {
  const init: ResponseInit = { status: STATUS[code] };
  if (extraHeaders) init.headers = extraHeaders;
  return Response.json({ error: code, message }, init);
}

const MAX_BODY_BYTES = 8 * 1024;

export interface PreCheckedRequest<T> {
  body: T;
  installId: string;
}

/** ARCH §3.8 steps 1-4: size/content-type, zod-validate, Play Integrity (or staging debug key), per-IP backstop. */
export async function preChecks<Schema extends ZodType>(
  request: Request,
  env: Env,
  schema: Schema,
): Promise<{ ok: true; value: PreCheckedRequest<z.infer<Schema>> } | { ok: false; response: Response }> {
  if (request.headers.get("content-type")?.split(";")[0]?.trim() !== "application/json") {
    return { ok: false, response: errorResponse("invalid_request", "content-type must be application/json") };
  }

  const rawBody = await request.arrayBuffer();
  if (rawBody.byteLength === 0 || rawBody.byteLength > MAX_BODY_BYTES) {
    return { ok: false, response: errorResponse("invalid_request", `body must be 1-${MAX_BODY_BYTES} bytes`) };
  }

  let json: unknown;
  try {
    json = JSON.parse(new TextDecoder().decode(rawBody));
  } catch {
    return { ok: false, response: errorResponse("invalid_request", "body is not valid JSON") };
  }

  const parsed = schema.safeParse(json);
  if (!parsed.success) {
    return { ok: false, response: errorResponse("invalid_request", parsed.error.issues[0]?.message ?? "schema validation failed") };
  }

  const installId = request.headers.get("x-install-id");
  if (!installId) {
    return { ok: false, response: errorResponse("invalid_request", "missing X-Install-Id") };
  }

  const expectedRequestHash = await requestHashOf(rawBody);
  const integrity = await verifyIntegrity({
    env,
    integrityToken: request.headers.get("x-integrity-token"),
    debugKey: request.headers.get("x-debug-key"),
    expectedRequestHash,
  });
  if (!integrity.ok) {
    return { ok: false, response: errorResponse("integrity_failed", integrity.reason) };
  }

  const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
  const ipLimit = await env.IP_RATE_LIMITER.limit({ key: ip });
  if (!ipLimit.success) {
    return { ok: false, response: errorResponse("rate_limited", "too many requests from this network", { "Retry-After": "60" }) };
  }

  return { ok: true, value: { body: parsed.data, installId } };
}

export async function checkBudget(env: Env): Promise<{ ok: true } | { ok: false; response: Response }> {
  const budget = env.BUDGET_COUNTER.getByName("global");
  const { allowed } = await budget.checkBudget();
  if (!allowed) return { ok: false, response: errorResponse("budget_exhausted", "monthly AI budget reached; try again next month") };
  return { ok: true };
}
