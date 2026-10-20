import type { Env } from "./env.ts";
import { handlePlanPath } from "./routes/plan.ts";
import { handleWeekQuests } from "./routes/week.ts";
import { handleReport, sweepExpiredReports } from "./routes/reports.ts";

export { RateLimiter } from "./durable-objects/RateLimiter.ts";
export { BudgetCounter } from "./durable-objects/BudgetCounter.ts";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "POST") return new Response(null, { status: 405 });

    const { pathname } = new URL(request.url);
    switch (pathname) {
      case "/v1/paths/plan":
        return handlePlanPath(request, env);
      case "/v1/paths/week":
        return handleWeekQuests(request, env);
      case "/v1/reports":
        return handleReport(request, env);
      default:
        return new Response(null, { status: 404 });
    }
  },

  async scheduled(_event: ScheduledController, env: Env): Promise<void> {
    await sweepExpiredReports(env);
  },
} satisfies ExportedHandler<Env>;
