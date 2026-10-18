# Audit — `Assis-Mohanty/Solo-Leveling-App` (as of Oct 2026)

**Verdict:** a reasonable backend *learning* project (Express + TS + Prisma, 3 services, ~6.3k lines TS), but **no client app exists**, the core loop isn't wired end to end, and there is one emergency. It is not a foundation for a 3-day Android launch. Keep it as your backend/portfolio track and as the future sync target.

## 🔴 Fix today (before anything else)

### 1. API keys are committed to a public repo
`crewai/.env` is tracked in git and contains 3 non-empty `*_API_KEY` values (Anthropic / Gemini / OpenAI per the code). Anyone can read them; bots scrape GitHub for exactly this.
1. **Revoke/rotate all three keys now** in each provider's console. Deleting the file is not enough — it's in git history.
2. `git rm --cached crewai/.env`, add `.env` to `.gitignore`, commit.
3. Optionally purge history (`git filter-repo --path crewai/.env --invert-paths`) — but rotation is what actually protects you.
4. Also remove committed `__pycache__/`.

### 2. Clients can mint XP
`POST /api/v1/xp` (`xp.router.ts` → `xp.controller.ts`) takes `xp_delta` **from the request body** and writes it to the ledger. That violates your own #1 invariant ("AI/clients never decide rewards"). Any authenticated user can grant themselves a million XP. XP must only be produced by the reward engine in response to a domain event (quest completed), never by a public endpoint. Delete the route or make it admin-only + audited.

### 3. Identity is a trusted header
`xUserId.middleware.ts` builds `req.user` (including **role** and `is_verified`) from `x-user-id` / `x-user-role` / `x-user-verified` headers. That's only safe if the service is unreachable except via the gateway *and* the gateway strips any client-supplied copies of those headers. If userService is ever exposed (Docker port mapping, misconfigured ingress), sending `x-user-role: ADMIN` = admin. Fix: gateway signs an internal JWT (or mTLS between services); services verify it; gateway always deletes inbound `x-user-*` headers.

## 🟠 Architectural gaps
- **The quest → reward loop is not connected.** questService publishes `QuestCompleted` to RabbitMQ, but userService has no consumer (`onCompletedQuest` in `external.services.ts` is never subscribed). Completing a quest grants nothing.
- **Outbox exists but the relay is half-built** — check the outbox poller marks rows published only after broker confirm, and that the consumer is idempotent (dedupe on event id).
- **Migration hygiene**: names like `updated_skills_table_qqqqqwq`, plus a stray `migrations/001_initial_schema.sql` beside Prisma migrations. Squash before anyone else depends on the schema.
- **Typos in paths** (`repostory/`, `userSklillStats.services.ts`, `userWalltetTransactions.repository.ts`, `implementaion.md`) — cosmetic but signals; fix in one rename commit.
- **No tests** in any service. The reward/ledger path is exactly where you need them first.
- **Root README is one line.** For a portfolio project, the README is the interview.
- Repo name/branding uses the series name — fine for a private learning repo, not for a product.

## 🟢 What's genuinely good (keep these ideas)
- Append-only XP ledger + wallet transaction ledger, idempotency fields.
- Outbox pattern + confirm channel publishing — the right instinct for reliable events.
- Correlation-ID middleware, validators, layered controller/service/repository structure.
- Separation of quest generation (template + LLM) from rewards.

## How the Android app relates
The Android `:engine` module re-implements the reward rules as pure functions with test vectors (GAME_DESIGN §12). When you add sync (v1.2), port those same vectors to the TS backend as a **contract test** so the server recomputes rewards from events and never trusts client totals. That's a strong interview story: *"I designed the reward engine to be deterministic so the client and server provably agree."*

## Project docs status
| Doc | Status |
|---|---|
| `PRD_MASTER.md` | Vision doc for a 8–12 engineer, 6-month org. Keep as long-term vision; **superseded for v1 by `PRD_MVP.md`**. Contains a double level-scaling flaw in the XP formula (fixed in GAME_DESIGN §4.1). |
| `ARCHITECTURE_AND_SERVICES.md` | Microservice design (the PRD lists 29 services) for zero users. Keep for v2+ reference. v1 = `ARCHITECTURE.md` (offline Android). |
| `PSYCHOLOGY_AND_ENGAGEMENT.md` | Useful research list, but several mechanics are dark patterns (public shame on streak breaks, slot-machine chests, "deletion = self-destruction", unpredictable FOMO notifications, Hardcore account freeze) and several "validation" stats are unsourced. Treat numbers as hypotheses; v1 deliberately uses the humane subset. |
| `SYSTEM_CONTEXT.md`, CrewAI docs | Tooling for the doc pipeline; not needed for the app build. |
| `soloLevelingReq.txt`, `soloLevelingTechStackq.txt`, `pasted.txt`, `gitingest`, `current folder structure`, `SoloLeveling_PRD_v1.0.docx` | Historical. Superseded by the build kit. |
