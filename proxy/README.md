# The Path Oracle (`/proxy`)

A stateless Cloudflare Worker that brokers AI Paths' two LLM calls (`PLAN_PATH`, `WEEK_QUESTS`) plus "Report this
quest" — see `docs/ARCHITECTURE.md` §3.7–3.8 and `docs/GAME_DESIGN.md` §6.6 for the design this implements. It
holds no user data beyond a 90-day report log with no install ID attached, and it never decides rewards: that's
`:engine`'s job, always re-checked on the Android side (`CLAUDE.md` invariant 5).

**LLM backend: Cloudflare Workers AI**, via the Worker's own `AI` binding — no separate API key or account.
Chosen for the lowest setup friction of the free options considered (Workers AI vs. Groq vs. Gemini): it bills to
the same Cloudflare account already needed for Durable Objects and D1, with a free daily neuron allowance.

## One-time setup (you — I can't do this without your Cloudflare/Google accounts)

1. **Install dependencies**: `npm install` (Node 22+; this repo was built against Node 24).
2. **Log in to Cloudflare**: `npx wrangler login`.
3. **Create the D1 database** for each environment you'll use, then paste the returned `database_id` into
   `wrangler.toml` in place of `REPLACE_WITH_WRANGLER_D1_CREATE_OUTPUT`:
   ```
   npx wrangler d1 create awaken-path-oracle-reports-staging
   npx wrangler d1 create awaken-path-oracle-reports-production
   ```
4. **Apply the D1 migration** (creates the `reports` table) for each environment:
   ```
   npx wrangler d1 migrations apply awaken-path-oracle-reports-staging --env staging --remote
   npx wrangler d1 migrations apply awaken-path-oracle-reports-production --env production --remote
   ```
5. **Create the per-IP rate-limiting namespaces.** `wrangler.toml` already picks arbitrary `namespace_id`s
   (1001/1002/1003); the Cloudflare dashboard (Workers & Pages → your account → Rate Limiting, or the API) is
   where you actually provision namespaces with those IDs if your account requires it — follow whatever the
   `wrangler deploy` output tells you if it complains about an unrecognized namespace.
6. **Play Integrity service account** (for verifying real Android installs; ARCH §3.7):
   - In Google Cloud Console, create a service account with access to the Play Integrity API for this app's
     Play Console listing (Play Console → Setup → App integrity → link the service account).
   - Download its JSON key, then set it as a secret (paste the **entire JSON file contents**):
     ```
     npx wrangler secret put INTEGRITY_SERVICE_ACCOUNT_JSON --env staging
     npx wrangler secret put INTEGRITY_SERVICE_ACCOUNT_JSON --env production
     ```
   - This is the one secret this proxy actually holds. There is no LLM API key to manage.
7. **Staging debug key** (lets a sideloaded debug APK skip Play Integrity, which always fails for non-Play
   installs — ARCH §3.7). Pick any long random string and set it **only for staging**:
   ```
   npx wrangler secret put DEBUG_KEY --env staging
   ```
   Never set `DEBUG_KEY` for `--env production` — `src/integrity.ts` also hard-codes a check so production
   rejects `X-Debug-Key` even if the secret were present, but don't rely on that as the only guard.
8. **Verify the configured model still supports JSON-schema structured output** before deploying: check
   https://developers.cloudflare.com/workers-ai/features/json-mode/ and
   https://developers.cloudflare.com/workers-ai/models/ against `wrangler.toml`'s `AI_MODEL_ID` — Workers AI's
   catalog changes over time and models get retired.
9. **Set a real monthly budget price.** `AI_PRICE_PER_1K_TOKENS_USD` in `wrangler.toml` is a placeholder (see the
   comment next to it) — Workers AI bills in "neurons" and doesn't return a per-call neuron count, so
   `BudgetCounter` approximates spend from token usage instead. Replace it with `AI_MODEL_ID`'s actual $/1k-token
   rate from https://developers.cloudflare.com/workers-ai/platform/pricing/.

## Commands

```
npm install                 # once
npm run typecheck           # tsc, both the Worker code and the Node-run scripts/eval
npm test                    # Vitest, fully local — no Cloudflare auth needed (see "How tests run" below)
npm run check:contracts     # ajv (JSON Schema) vs zod agreement over a shared fixture battery
npm run dev                 # wrangler dev, against the base (un-named) environment
npm run deploy:staging      # wrangler deploy --env staging
npm run deploy:production   # wrangler deploy --env production
npm run eval                # eval/run.ts against a deployed Path Oracle — see its header comment for env vars
```

`npm test` and `npm run check:contracts` passing is this phase's "done" bar per `docs/PROMPTS.md`. Everything
involving a real deploy (steps 2–7 above, and the "staging answers a curl with the debug key" check in
PROMPTS.md) needs your Cloudflare/Google accounts and hasn't been run from here.

## How tests run without a Cloudflare account

Workers AI and the `ratelimits` binding have no local simulation — even `wrangler dev`/tests proxy them to the
real Cloudflare API, which needs `CLOUDFLARE_API_TOKEN`. `wrangler.test.toml` is a test-only config (pointed to
by `vitest.config.ts`) that keeps the bindings Miniflare *can* fully simulate locally — Durable Objects (so
`RateLimiter`/`BudgetCounter` are tested for real, SQL storage included) and D1 — and drops `[ai]`/`[[ratelimits]]`
entirely. `test/helpers.ts`'s `testEnv()` merges the real pool bindings with plain mock objects for `env.AI` and
`env.IP_RATE_LIMITER`. Storage isolation is per **test file**, not per test (confirmed empirically while writing
these tests) — see the comment in `test/routes/budget-exhausted.test.ts` for why that scenario lives in its own
file.

## Verify a staging deploy once it's up

```
curl -X POST https://awaken-path-oracle-staging.<your-subdomain>.workers.dev/v1/paths/plan \
  -H "content-type: application/json" \
  -H "x-install-id: $(uuidgen)" \
  -H "x-debug-key: <the DEBUG_KEY secret you set>" \
  -d '{"requestId":"<a uuid>","path":{"goal":"Learn to juggle","currentLevel":"","notes":"","primaryStat":"STR","secondaryStat":null,"activeDays":["MON","WED","FRI"],"questsPerDay":1,"weeklyMinutes":90},"constraints":{"equipment":[],"injuryTags":[]}}'
```
A `200` with a `milestones` array means the Worker, the Workers AI binding, and the Durable Objects are all
wired up correctly end to end.

## Rotating secrets

- **`INTEGRITY_SERVICE_ACCOUNT_JSON`**: create a new Google Cloud service account key, `wrangler secret put` it
  for the affected environment, then delete the old key in Google Cloud Console once you've confirmed the new
  one works (the proxy caches an access token in memory until it expires, so a rotation takes effect on the next
  cold start — redeploy, or just wait).
- **`DEBUG_KEY`** (staging only): `wrangler secret put DEBUG_KEY --env staging` with a new value; update whatever
  debug-build config on the Android side reads it (`local.properties`, per ARCH §3.7 — never commit it).

## What this proxy deliberately doesn't do

- No analytics/crash SDK, no host other than Google's OAuth/Play Integrity endpoints and the model (which never
  leaves Cloudflare's own infrastructure, since it's a Workers AI binding, not an external HTTP call).
- No request or response bodies ever reach a log line (`src/logging.ts`; `test/logging.test.ts` asserts this).
- No install ID stored alongside a report (`src/routes/reports.ts`).
