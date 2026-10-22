import type { ZodType, z } from "zod";
import type { Env } from "../env.ts";

/**
 * Calls Workers AI with the hunter's fields delimited in a `<hunter_data>` block (ARCH §3.8 step 6; CLAUDE.md
 * invariant 9 "user text is untrusted input") and `response_format: json_schema` (ARCH §3.8 step 7). Retries once
 * on schema-invalid output, then gives up (step 8: `502 model_invalid_output`).
 *
 * Workers AI's JSON mode is OpenAI-SDK-compatible (https://developers.cloudflare.com/workers-ai/features/json-mode/);
 * the exact shape of a successful `env.AI.run()` result isn't pinned down in the public docs beyond "a `response`
 * field holding the generated text" — this defensively accepts the parsed object directly, a `.response` string
 * to `JSON.parse`, or a `.response` object, and normalizes `usage` into a token count for `BudgetCounter`.
 */

export type LlmOutcome<T> =
  | { ok: true; data: T; tokensUsed: number }
  | { ok: false; code: "model_invalid_output" | "budget_exhausted"; tokensUsed: number };

interface WorkersAiTextResult {
  response?: unknown;
  usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number } | undefined;
  [key: string]: unknown;
}

function extractPayload(result: WorkersAiTextResult): unknown {
  if (result.response === undefined) return result;
  if (typeof result.response === "string") {
    try {
      return JSON.parse(result.response);
    } catch {
      return undefined;
    }
  }
  return result.response;
}

function tokensOf(result: WorkersAiTextResult): number {
  const usage = result.usage;
  if (!usage) return 0;
  return usage.total_tokens ?? (usage.prompt_tokens ?? 0) + (usage.completion_tokens ?? 0);
}

export async function callWorkersAi<Schema extends ZodType>(params: {
  env: Env;
  systemPrompt: string;
  hunterData: Record<string, unknown>;
  jsonSchema: Record<string, unknown>;
  outputSchema: Schema;
}): Promise<LlmOutcome<z.infer<Schema>>> {
  const { env, systemPrompt, hunterData, jsonSchema, outputSchema } = params;
  const messages = [
    { role: "system" as const, content: systemPrompt },
    // "<" is escaped so hunter text can never close the delimiter early (CLAUDE.md invariant 9).
    { role: "user" as const, content: `<hunter_data>\n${JSON.stringify(hunterData).replace(/</g, "\\u003c")}\n</hunter_data>` },
  ];

  let tokensUsed = 0;
  for (let attempt = 0; attempt < 2; attempt++) {
    let result: WorkersAiTextResult;
    try {
      result = (await env.AI.run(
        env.AI_MODEL_ID as Parameters<Ai["run"]>[0],
        {
          messages,
          response_format: { type: "json_schema", json_schema: jsonSchema },
          temperature: 0.4,
          max_tokens: 2000,
        } as Parameters<Ai["run"]>[1],
      )) as WorkersAiTextResult;
    } catch (error) {
      // Workers AI throws on quota (error 4006: daily free neurons, or a paid cap) and on upstream failures; an uncaught
      // throw would reach the app as Cloudflare's HTML error page instead of our JSON contract.
      const message = error instanceof Error ? error.message : String(error);
      // Only the numeric Workers AI error code is logged: upstream messages could echo request text.
      console.error("workers_ai_error", /\b\d{4}\b/.exec(message)?.[0] ?? "unknown");
      const quota = /4006|daily free allocation/i.test(message);
      return { ok: false, code: quota ? "budget_exhausted" : "model_invalid_output", tokensUsed };
    }

    tokensUsed += tokensOf(result);
    const parsed = outputSchema.safeParse(extractPayload(result));
    if (parsed.success) return { ok: true, data: parsed.data, tokensUsed };
  }

  return { ok: false, code: "model_invalid_output", tokensUsed };
}
