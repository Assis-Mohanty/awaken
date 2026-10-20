/// <reference path="../node_modules/@cloudflare/vitest-plugin/types/cloudflare-test.d.ts" />
import { env } from "cloudflare:workers";
import { applyD1Migrations } from "cloudflare:test";
import { beforeAll } from "vitest";
import type { Env } from "../src/env.ts";

beforeAll(async () => {
  const e = env as Env & { TEST_MIGRATIONS: Parameters<typeof applyD1Migrations>[1] };
  await applyD1Migrations(e.REPORTS_DB, e.TEST_MIGRATIONS);
});
