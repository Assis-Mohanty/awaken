# AWAKEN — Android Architecture (v1.0)

## 0. Why this architecture (and not the 13-microservice one)

v1 has **zero users**. A distributed backend buys nothing yet and costs weeks: auth, hosting, sync conflicts, a privacy policy that now covers server data. So the app is **offline-first with exactly one network feature**:

- All game state lives on the phone. No account, no user database, no sync. It works on a plane.
- **AI Paths** call one **stateless proxy** (the "Path Oracle", §3.8) that holds the LLM key, authenticates the *install* with Play Integrity instead of a person with a login, rate-limits, forwards to the model, and stores nothing. Losing the proxy degrades personalisation, never the game.
- The *same invariants* the backend design wanted (append-only ledger, idempotency keys, deterministic engine) hold, so syncing later is "upload ledger events, server recomputes", not a rewrite.

Why not a real backend with accounts for AI? An account system only buys identity, and v1 needs none: rate limiting needs "is this a genuine install of our app" (Play Integrity answers that) and the AI needs only the Path text sent in the request. Every user record we don't store is a record we can't leak.

The existing `userService`/`questService` repo stays as a learning/portfolio project and the future sync target (v1.2+). See `REPO_AUDIT.md`.

```
┌──────────────────────────────── :app (Android) ────────────────────────────────┐
│  UI (Compose)     feature/today  quest  paths  gate  status  vault  onboarding  │
│     │ events ▲ StateFlow<UiState>                                               │
│  ViewModels (Hilt)                                                              │
│     │                                                                           │
│  core/domain  — use cases: CompleteQuest, StartGate, GenerateDirective,         │
│                 CreatePath, AcceptWeekPlan, ...                                 │
│     │   calls pure functions ───────────────────────┐                           │
│  core/data    — Room DAOs, repositories, DataStore,  │                          │
│                 HealthConnectGateway, Clock,         │                          │
│                 ai/PathOracleClient ─────────────────┼──── HTTPS ──┐            │
│  workers      — DayRolloverWorker, HealthSyncWorker, │             │            │
│                 ReminderWorker, PlanWeekWorker       │             │            │
│  widget       — Glance TodayWidget                   │             │            │
└──────────────────────────────────────────────────────┼─────────────┼────────────┘
                                                       ▼             ▼
                   ┌──────── :engine (pure Kotlin/JVM) ────────┐   ┌──── /proxy (Cloudflare Worker) ────┐
                   │ XpCalculator  LevelCurve  RankRules        │   │ Play Integrity verify → rate limit │
                   │ StreakRules   DailyCaps   CritRoller       │   │ → budget check → LLM (structured   │
                   │ DirectiveGenerator  DifficultyAdapter      │   │ output) → zod validate → respond   │
                   │ AchievementEvaluator                       │   │ Stores no user data.               │
                   │ PathValidator  PathScheduler               │   └─────────────────┬──────────────────┘
                   │ ProgressionSignal  model/*                 │                     ▼
                   └────────────────────────────────────────────┘               LLM provider API
```

## 1. Project layout

```
awaken/
├─ CLAUDE.md
├─ docs/  (PRD_MVP, GAME_DESIGN, ARCHITECTURE, DESIGN_SYSTEM, PROMPTS, BACKLOG)
├─ gradle/libs.versions.toml
├─ engine/                         # kotlin("jvm")
│  └─ src/main/kotlin/com/assismohanty/awaken/engine/
│     ├─ model/  Stat, Difficulty, Verification, QuestTemplate, QuestDraft, Hunter, Rank, LedgerKind ...
│     ├─ XpCalculator.kt  LevelCurve.kt  RankRules.kt  StatLevel.kt
│     ├─ DailyCaps.kt  CritRoller.kt (fnv1a64)  StreakRules.kt
│     ├─ DirectiveGenerator.kt  DifficultyAdapter.kt  AchievementEvaluator.kt
│     ├─ path/  PathValidator.kt  PathPricing.kt  PathScheduler.kt  PathProgression.kt  Keywords.kt (StatSuggester,
│     │         EquipmentKeywords)  PathText.kt  PathModels.kt  PathWire.kt (wire DTOs)
│     └─ GameDay.kt  (Instant + zone + dayStartHour -> LocalDate)
│  └─ src/test/kotlin/...  (GAME_DESIGN §12 vectors + property tests)
│  └─ src/test/resources/paths/  (hand-written response fixtures for vectors 16–35)
├─ contracts/
│  ├─ path_plan.request.schema.json   path_plan.response.schema.json
│  └─ path_week.request.schema.json   path_week.response.schema.json
├─ proxy/                          # Cloudflare Worker, TypeScript (deployed independently)
│  ├─ src/index.ts  routes.ts  integrity.ts  ratelimit.ts (Durable Object)  budget.ts  llm.ts  schemas.ts
│  ├─ prompts/  plan_path.v1.md  week_quests.v1.md     # versioned; version echoed in every response
│  ├─ test/  (Vitest, mocked LLM)   eval/goals.json (30 goals)  eval/run.ts
│  └─ wrangler.toml  (envs: staging, production)
└─ app/
   └─ src/main/
      ├─ assets/quests/templates.json   assets/narrative/chapters.json, system_lines.json
      ├─ res/font/  (Saira Condensed, IBM Plex Sans — OFL; see DESIGN_SYSTEM §2.2)
      └─ java/com/assismohanty/awaken/
         ├─ AwakenApp.kt (@HiltAndroidApp)  MainActivity.kt
         ├─ core/designsystem/  theme/ (Color, Type, Shape, Motion, Haptics), components/
         ├─ core/data/  db/ (AwakenDatabase, entities, daos), repo/, prefs/, health/, clock/,
         │              ai/ (PathOracleClient, IntegrityTokenProvider, dto/, FakePathOracle for debug/tests)
         ├─ core/domain/ usecases
         ├─ feature/onboarding|today|quest|paths|gate|status|vault|settings/
         ├─ notifications/  work/  widget/
         └─ navigation/AwakenNavHost.kt
```

Package name: `com.assismohanty.awaken` (permanent once published — note: `in.*` is invalid in Kotlin since `in` is a keyword).

## 2. Data model (Room)

All IDs are `String` UUIDs unless noted. Timestamps `Long` epoch millis UTC. `game_date` = ISO `yyyy-MM-dd` string.

```sql
hunter_profile(id PK='me', hunter_id,                 -- hunter_id: random UUID, seeds generation (GD §6.3)
               name, created_at, first_game_date, focus_stat_1, focus_stat_2, day_start_hour,
               reminder_time NULL, equipped_title_id NULL, theme_id, equipped_frame_id NULL, equipped_sigil_id NULL, onboarding_done,
               owned_equipment TEXT,                 -- comma-separated equipment ids (GD §6.3 filter)
               injury_mode BOOL, injury_tags (default impact,legs), health_enabled BOOL, last_recalibrated_on NULL)

stat_tier(stat PK, tier TEXT)                         -- EASY..EXTREME, adaptive (GD §6.4)

quest_instance(id PK, game_date, type, template_id NULL, habit_id NULL, path_slot_id NULL,
               stat, secondary_stat NULL, title, description, unit, target INT,
               difficulty, verification, min_duration_sec,
               status,                               -- ACTIVE|STARTED|COMPLETED|EXPIRED|MISSED|REROLLED
               started_at NULL, completed_at NULL, created_at,
               INDEX(game_date), INDEX(status))

-- One row per completion attempt (GD §9.3). id = completionId = questInstanceId#attemptN. Not a ledger table:
-- undone_at is set on undo. Feeds the undo window, re-completion attempt numbers and achievement counts.
completion(id PK, quest_instance_id, attempt INT, game_date, quest_type, stat, verification_source,  -- SELF|TIMER|HEALTH
           crit BOOL, xp INT, completed_at, undone_at NULL,
           UNIQUE(quest_instance_id, attempt), INDEX(game_date))

habit(id PK, title, stat, difficulty, verification, days_of_week_mask INT, target, unit,
      active BOOL, created_at)

-- THE LEDGER (append-only). Every economy change is a row here.
ledger_entry(id INTEGER PK AUTOINCREMENT,
             kind TEXT,        -- XP_TOTAL | XP_STAT | GOLD | ITEM | FRAGMENT
             stat TEXT NULL,   -- for XP_STAT
             item_id TEXT NULL,-- for ITEM
             delta INTEGER,
             source_type TEXT, -- QUEST | GATE | TRIAL | ACHIEVEMENT | DAY_CLEAR | SHOP | UNDO | SHIELD_USE | CACHE_OPEN
             source_id TEXT,
             game_date TEXT,   -- undo rows copy the ORIGINAL row's game_date so daily caps net out
             xp_source TEXT,   -- SELF|TIMER|HEALTH on quest XP rows (daily caps), '' otherwise
             created_at,
             breakdown_json TEXT NULL,               -- formula breakdown for the UI
             UNIQUE(source_type, source_id, kind, stat, item_id))

balance_cache(key PK, value INTEGER)                  -- 'xp_total','gold','xp_STR',..., 'item_streak_shield', 'fragments'
                                                       -- updated in same txn; rebuildable from ledger

day_record(game_date PK, cleared BOOL, rest_day BOOL, recovery BOOL,
           outcome TEXT NULL,                        -- CLEARED|REST|RECOVERY|SHIELDED|BROKEN once finalized
           shield_used BOOL, penalty_issued BOOL, finalized_at NULL)
-- The streak is derived by replaying finalized outcomes (StreakRules.replay); no shield history is needed.

gate_run(id PK, quest_instance_id, gate_rank, stat, label NULL, planned_sec, started_at, ended_at NULL,
         background_ms INTEGER, paused_at NULL, resumed_at NULL,          -- the one pause (ENGINE_DECISIONS #34)
         last_stop_at NULL, last_stop_elapsed_ms NULL, boot_count,        -- open background stretch (#37)
         status, collapse_reason NULL)   -- RUNNING|CLEARED|COLLAPSED|ABANDONED; BREACH|DEVICE_RESTARTED|DAY_RESET

trial(id PK, target_rank, started_game_date, expires_game_date, status, ended_at NULL)   -- ACTIVE|PASSED|FAILED; ended_at drives the 48 h retry
achievement_unlock(achievement_id PK, unlocked_at)
owned_cosmetic(cosmetic_id PK, acquired_at, source)
health_sync_state(record_type PK, last_change_token NULL, last_synced_at)

-- AI PATHS (GAME_DESIGN §6.6). None of these tables affect balances directly;
-- rewards still flow only through quest_instance → CompleteQuest → ledger.
path(id PK, goal, current_level, notes, primary_stat, secondary_stat NULL, last_replan_iso_week NULL,
     active_days_mask INT, quests_per_day INT, weekly_minutes INT,
     status,                                   -- PLANNING|ACTIVE|PAUSED|ARCHIVED
     current_milestone_index INT, milestone_started_game_date, created_at, created_game_date)

path_milestone(path_id, idx, title, summary, exit_criterion, plan_version INT,
               PRIMARY KEY(path_id, plan_version, idx))

path_week(path_id, iso_week TEXT,               -- e.g. '2026-W43'
          status,                               -- REQUESTED|READY|EMPTY|FAILED
          progression_signal,                   -- UP|HOLD|DOWN
          prompt_version, response_sha256, requested_at, received_at NULL,
          manual_retries INT, suggest_advance BOOL,
          PRIMARY KEY(path_id, iso_week))

path_slot(id PK, path_id, iso_week, blueprint_index INT,
          assigned_game_date, carried_over BOOL,
          title, description, stat, unit, target INT, est_minutes INT,
          mode,                                 -- SELF|GATE
          priced_difficulty, priced_verification, gate_rank NULL, min_duration_sec,
          tags_json, rationale,
          outcome,                              -- PENDING|COMPLETED|EXPIRED|SKIPPED|REROLLED|REPORTED
          feedback NULL,                        -- TOO_EASY|RIGHT|TOO_HARD
          UNIQUE(path_id, iso_week, blueprint_index))

ai_report(id PK, path_slot_id, reason, created_at, sent_at NULL)   -- outbox for /v1/reports
```

`path_slot` holds the *validated, priced* blueprint. `quest_instance` for a Path quest is created from a slot on its game-day by `GenerateDirective`, with `path_slot_id` set; the priced fields are copied, never recomputed from model output.

UNIQUE with nullable columns: SQLite treats NULLs as distinct → store `''` instead of NULL for `stat`/`item_id` in the ledger to keep the uniqueness guarantee. (This is a classic bug; add a test.)

Ledger sourceIds: QUEST/GATE → completionId; UNDO → the undone row's sourceId; DAY_CLEAR and SHIELD_USE → game_date; ACHIEVEMENT → achievement id; SHOP → purchase UUID (rerolls: `reroll:<game_date>#n`).

Migrations: `exportSchema = true`, schemas committed under `app/schemas/`. Every schema change bumps the version and adds a `Migration` (`core/data/db/Migrations.kt`, listed in `ALL_MIGRATIONS`) plus a case in `MigrationTest`, which builds the old database from its exported JSON (the schemas are debug-only assets) and opens it with the current Room, so Room validates the result. (`MigrationTestHelper` is not used: its `createDatabase` rejects Robolectric database paths on Windows.) Current version: 5 (v1→v2 rebuilt the then-empty `gate_run` for Focus Gates; v2→v3 added `hunter_profile.equipped_frame_id` and `equipped_sigil_id` for the Vault; v3→v4 added `injury_tags` for Settings; v4→v5 added the five AI Paths tables and `quest_instance.path_slot_id`). **No destructive fallback, ever: hunters never lose data.**

## 3. Core flows

### 3.1 Complete quest (the economy pipeline — the only one that mints XP)

```
QuestScreen ─onEvent(Complete)─▶ QuestViewModel
   ▶ CompleteQuestUseCase(questInstanceId)
       db.withTransaction {
         q = questDao.get(id); require(q.status in ACTIVE|STARTED)
         ctx = loadContext(gameDate)         // streak, today's self/timer XP, tiers
         result = engine.XpCalculator.forCompletion(q, ctx, CritRoller(q.id))
                 → QuestReward(xp, statXp[], gold, crit, capped, fragments, breakdown)
         questDao.markCompleted(id, now)
         ledger.insertAll(result.toLedgerRows(sourceType=QUEST, sourceId=completionId))   // id#attemptN; IGNORE on conflict
         balances.apply(rowsActuallyInserted)
         newLevel = LevelCurve.level(balances.xpTotal)
         unlocks = AchievementEvaluator.evaluate(snapshot)  → insert unlocks + reward rows
       }
   ◀ CompletionOutcome(reward, levelUp?, rankTrialUnlocked?, unlocks[])
   UI: reward sheet → (level-up cinematic) → (achievement toasts)
```

### 3.2 Day rollover (deterministic, idempotent, catch-up safe)

Runs on app start, on resume, and via `DayRolloverWorker` (periodic 1 h + exact-ish at day start). For every game-date between `lastFinalized+1` and `yesterday`:
1. Expire ACTIVE/STARTED generated quests, mark habits MISSED.
2. `StreakRules.finalizeDay` → CLEARED (ledger `GOLD +20, DAY_CLEAR`), REST, RECOVERY, SHIELDED (ledger `ITEM -1, SHIELD_USE`) or BROKEN (flag penalty).
3. Finalize `day_record` with the outcome. Each day is its own transaction.
Then for today: `GenerateDirective` (idempotent per game_date — check before insert), add penalty quest if flagged, run DifficultyAdapter on Mondays.

Phone was off for 5 days → loop handles all 5. Clock moved backwards → never "un-finalize"; log and ignore.

### 3.3 Focus Gate

**Decision: no foreground service.** A 50–90 min timer service would need a foreground-service type and a Play Console justification (`shortService` caps at ~3 min; `specialUse` invites review questions). A timer doesn't need a running process — it needs two timestamps:
- Store `started_at`, `planned_sec` in Room. Countdown is computed from wall/elapsed time, not ticked by a service.
- `ProcessLifecycleOwner` observer accumulates `background_ms` while the app is backgrounded (record `onStop` elapsedRealtime, add on `onStart`).
- Post an ongoing notification with a `Chronometer` countdown (`setUsesChronometer` + `setChronometerCountDown(true)`) so the user sees time outside the app; schedule an exact-ish alarm/WorkManager one-shot at the end time to notify "Gate cleared — return to claim".
- On return: if `background_ms > 90_000` → COLLAPSED. Else when `now ≥ end` → claim → CompleteQuest pipeline with `V=TIMER`.
This avoids foreground-service-type policy declarations entirely. The process may die while backgrounded; state is in Room so nothing is lost. (If killed, background time is computed as `now − lastStopAt` on next start.)

### 3.4 Health Connect verification

- Permissions requested **only** at the moment the user enables "Verified quests" (onboarding step or Settings), with a rationale screen first. Read-only: `Steps`, `ExerciseSession`, `SleepSession`. Nothing else.
- Must declare `<activity-alias>` / intent filter for `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` and the privacy-policy screen per Health Connect docs.
- **Foreground-only sync** (ENGINE_DECISIONS #51): `HealthSyncCoordinator` on `ProcessLifecycleOwner` syncs on every app start and every 30 min while visible. Health Connect refuses background reads without `READ_HEALTH_DATA_IN_BACKGROUND`, which we do not request, so there is no background worker. `HealthSyncUseCase` reads the game-day window (`aggregate(StepsRecord.COUNT_TOTAL)`, exercise sessions, last night's sleep), matches with `HealthRules` (engine, #50) and completes via `CompleteQuestUseCase.completeVerified(id, HEALTH)`. Health completions are not undoable (#52).
- `HealthGateway` (real: `HealthConnectGateway`, connect-client 1.1.0; debug: `DebugHealthGateway` with "Simulate 9,000 steps"; tests: `FakeHealthGateway`).
- If HC unavailable/not installed/denied: HEALTH templates are excluded from generation; no nagging.
- Never write to Health Connect in v1.

### 3.5 Notifications (respectful by default)
- Channel `directive` (default importance): 1× daily at `reminder_time` — "Your directive is ready: 4 quests."
- Channel `last_call` (default): 1× at 21:00 local *only if* the day isn't cleared — "2 quests from clearing the day."
- Channel `gate` (low): ongoing gate countdown.
- Channel `system` (low): level-up / trial unlocked when app is backgrounded (rare). **Not built** (ENGINE_DECISIONS #55): nothing raises that event while the app is away.
- Max 2 non-gate notifications/day (one of each kind, fixed ids). Android 13+ `POST_NOTIFICATIONS` asked after the first quest completion, not at launch, through a System-styled pre-prompt (once ever).
- Built as `ReminderScheduler`/`ReminderWorker` (one-shot, self-re-arming, inexact), `ReminderCoordinator` (re-arms on reminder/day-start change), `BuildReminderUseCase` + `ReminderRules` (engine). Reminder off silences both (#53-#55).

### 3.6 Widget (Glance)
`TodayWidget` 4×2: rank badge, level, streak flame, "3/5 cleared", next quest title, tap → app. Refresh on every ledger write (`updateAll`) and at rollover.
Built in `core/widget`: `WidgetSnapshot` (pure mapping from the Today model), `TodayWidget` + `TodayWidgetReceiver` (Glance 1.2.0, Hilt entry point for data), `WidgetUpdater` (re-renders only when the snapshot changes). ENGINE_DECISIONS #56.

### 3.7 AI Paths: client flow

```
CreatePathUseCase (consent given?) ──no──▶ show consent sheet (DESIGN_SYSTEM §4.8); stop
      │ yes
      ▼
insert path(status=PLANNING) ─▶ enqueue PlanWeekWorker(pathId, PLAN_THEN_WEEK)
                                         │  WorkManager: unique name "path-{pathId}-{isoWeek}",
                                         │  NetworkType.CONNECTED, exponential backoff 30 s, max 5 attempts
                                         ▼
PathOracleClient.planPath(req)  ── POST /v1/paths/plan ──▶ proxy
      ▼ response
PathValidator.validatePlan(...)  (engine) → insert path_milestone rows → path.status = ACTIVE
      ▼
PathOracleClient.weekQuests(req) ── POST /v1/paths/week ──▶ proxy
      ▼ response
AcceptWeekPlanUseCase, in ONE transaction:
   v = PathValidator.validateWeek(response, path, hunterSettings, last14DaysTitles)    // GD §6.6.4
   priced = PathPricing.price(v)                                                     // GD §6.6.4
   slots  = PathScheduler.assign(priced, path.activeDays, isoWeek, today)            // GD §6.6.6
   insert path_slot rows (IGNORE on UNIQUE) ; upsert path_week(status = READY | EMPTY)
   if today's directive already exists → GenerateDirective.addPathQuestsForToday()   // idempotent
```

- **Weekly trigger:** `DayRolloverUseCase` on the first rollover of an ISO week computes `ProgressionSignal` per active Path from last week's `path_slot` outcomes (GD §6.6.5) and enqueues `PlanWeekWorker(pathId, WEEK)`.
- **Request content** (the only data that leaves the phone): `goal`, `currentLevel`, `notes`, stats, `activeDays`, `questsPerDay`, `weeklyMinutes`, current and next milestone titles, `progressionSignal`, and last week's `[{title, outcome, feedback}]`. Plus equipment owned and Injury Mode tags, so the model avoids them up front (the validator enforces it anyway). Never XP, level, gold, streak, Health Connect data, hunter name or device identifiers beyond the random install ID.
- **Headers beyond the contract:** week calls send `X-Path-Id` (keys the per-Path weekly limits) and `X-Retry-Kind: manual` on user retries (absent = automatic); see `proxy/src/routes/week.ts`.
- **Integrity:** `IntegrityTokenProvider` wraps the Play Integrity **Standard API** (`prepareIntegrityToken` once per app process, then `request()` per call with `requestHash = SHA-256(canonical request body)`). Headers: `X-Install-Id` (random UUID in DataStore, created at consent) and `X-Integrity-Token`.
- **Retries:** each logical request carries a `requestId` UUID that is reused across WorkManager retries; the proxy doesn't charge quota twice for the same `requestId`.
- **Timeouts:** connect 10 s, total 45 s. Model latency is the long pole.
- **Failure states** → `path_week.status = FAILED` after max attempts; Paths tab shows the System-styled error with [Retry] (counts toward the manual-retry limit). The Directive never waits on the network: it is generated from whatever slots exist.
- **Consent revoked** (Settings → AI Paths off): cancel all `PlanWeekWorker`s, delete the install ID, keep existing Paths and slots readable and completable; no new plans.
- **Reports:** "Report this quest" sets `path_slot.outcome = REPORTED` (removed from today immediately, no reward impact) and inserts an `ai_report` outbox row; `ReportWorker` POSTs it to `/v1/reports`.
- **Debug builds** use the `staging` proxy env and send `X-Debug-Key` (from `local.properties`, never in release) because sideloaded debug APKs fail Play Integrity. A Debug-screen toggle swaps in `FakePathOracle` (fixture responses) for offline UI work.

### 3.8 The Path Oracle proxy

One Cloudflare Worker, three routes. Why Workers: no servers to patch, a free tier that covers early usage, secrets in the platform, and Durable Objects for strongly consistent per-install counters. (A FastAPI container on Cloud Run is a fine alternative; the contract below doesn't change.)

| Route | Body schema | Notes |
|---|---|---|
| `POST /v1/paths/plan` | `contracts/path_plan.request.schema.json` | → milestones |
| `POST /v1/paths/week` | `contracts/path_week.request.schema.json` | → blueprints + `suggestAdvance` |
| `POST /v1/reports` | `{ promptVersion, reason, title, description }` | Stored in D1, 90-day TTL. The only thing the proxy stores. No install ID kept with it |

Request pipeline, in order (cheap checks first, the model last):

```
1. size ≤ 8 KB, content-type JSON                        → 400 invalid_request
2. zod-validate body against the contract (lengths, enums) → 400 invalid_request
3. Play Integrity: decode token via Google's decodeIntegrityToken API; require
   requestPackageName == com.assismohanty.awaken, requestHash == SHA-256(raw body),
   appRecognitionVerdict == PLAY_RECOGNIZED, deviceRecognitionVerdict ∋ MEETS_DEVICE_INTEGRITY,
   token timestamp ≤ 5 min old                              → 401 integrity_failed
4. Rate limit (Durable Object keyed by install ID; GD §6.6.7) + per-IP backstop
   (Workers rate-limiting binding); same requestId never double-counts → 429 rate_limited + Retry-After
5. Budget: monthly spend counter (DO singleton, estimated from token usage × configured price)
                                                            → 503 budget_exhausted
6. Build prompt: system prompt from prompts/<name>.v<N>.md; user fields inserted ONLY inside a
   delimited <hunter_data> block with an instruction that its contents are data, not instructions
7. Call the model with structured output (JSON schema = the response contract), max 2,000 output tokens,
   temperature low (≈0.4). Model ID from env (a small, fast tier; verify current IDs on the provider's docs)
8. zod-validate the model output. Invalid → retry once → still invalid             → 502 model_invalid_output
9. Respond { promptVersion, ...payload }. The app's PathValidator runs again (defence in depth)
```

**Prompt content rules** (in both prompt files): concrete and measurable tasks only; respect `notes`, equipment and injury tags; never medical, diet, calorie, weight, fasting, supplement or substance advice; nothing illegal, dangerous, sexual or involving money; no contacting strangers; no links. Keep the System voice in `title`, plain language in `description`.

**What the proxy logs:** route, status, latency, model tokens in/out, prompt version, SHA-256 of the install ID (for abuse forensics), validation drop counts. **Never** request or response bodies. Retention 30 days.

**Known limit (state it honestly):** a determined attacker can reinstall to get a fresh install ID. Play Integrity still forces them through a genuine Play-installed app on a real device, the per-IP limit slows bulk abuse, and the monthly budget cap bounds the worst case to a number you chose. That is proportionate for a free feature.

### 3.9 Status & Weekly Report share
`ObserveStatusUseCase` derives everything Status shows from Room flows: radar (current stat levels vs levels from ledger rows before this Monday), 18-week heatmap (active completions per game-day), the 32 achievements, chapters (`level ≥ unlockLevel`), and the Weekly Report with a projected recalibration. Equipping a title updates `hunter_profile.equipped_title_id` only, never the ledger. To share, `ShareCard` is laid out offscreen at 360×450dp with density 3, recorded into a `GraphicsLayer` (1080×1350px), written by `ShareImageWriter` to `cache/share/` (one file, overwritten), and handed to `ACTION_SEND` through `androidx.core.content.FileProvider` (authority `${applicationId}.share`, cache path only, with a per-share read grant). No network.

### 3.10 Vault, Settings and export/import
`PurchaseItemUseCase` and `OpenRelicCacheUseCase` each run in one transaction and are idempotent on a client-generated id (`SHOP` / `CACHE_OPEN` ledger sources). `ShopRules` (engine) owns the catalogue, prices and the deterministic cache drop. Settings edits go through `UpdateSettingsUseCase` (profile row only); haptics and reduce-motion live in a DataStore (`UiPrefsRepository`) and feed `AwakenTheme` with the equipped theme via `AppearanceViewModel`. `GameDataPorter` exports the raw rows of 17 tables as JSON (SAF, no permission, no network) and imports them atomically (ENGINE_DECISIONS #48).

## 4. State & DI
- Hilt modules: `DatabaseModule`, `DispatchersModule` (`@IoDispatcher`, `@DefaultDispatcher`), `ClockModule` (`Clock` interface; `SystemClock` in prod, `FakeClock` in tests), `EngineModule` (provides engine objects + loaded template library), `AiModule` (Ktor `HttpClient`, `PathOracleClient` bound to the real or fake implementation, `IntegrityTokenProvider`; base URL from `BuildConfig` per build type).
- DataStore (Preferences) for UI prefs (reduce motion, haptics on/off, sound on/off) and AI settings (consent given + timestamp, install ID).
- Everything game-relevant lives in Room (single source of truth, transactional).

## 5. Security, privacy, abuse
- `INTERNET` is used only by `PathOracleClient` (and Play services for Integrity). `network_security_config.xml`: cleartext disabled. No third-party SDKs that make network calls. Phase 16 verifies the merged manifest and dependency list.
- The proxy is the only server. It holds the LLM key as a Worker secret; the key never exists in the repo, the APK, or CI logs.
- Threat model for AI Paths, and the control for each: **key theft** → key only on the proxy; **cost abuse** → Play Integrity + per-install and per-IP limits + monthly budget cap; **prompt injection** via goal text → delimited data block + schema-constrained output + engine validator + rewards never derived from model output; **XP farming** via trivial goals → SELF Path quests capped at Normal and the daily self cap (GD §6.6.4); **harmful content** → prompt rules + unit enum without diet/weight units + in-app report; **data leak** → proxy stores no request data and logs no bodies.
- `android:allowBackup="true"` with `dataExtractionRules` including the Room DB so users don't lose progress on a new phone (Google auto-backup). Plus manual **Export/Import JSON** of the ledger (Settings) — export is a full ledger dump; import validates with the engine and rebuilds caches.
- Cheating is possible offline (clock changes, self-report). Accept it for v1: it only affects the cheater. Mitigations that matter for later sync: ledger keeps `created_at` + `game_date`; record `SystemClock.elapsedRealtime()` deltas on completions; flag days where wall-clock moved backwards. When leaderboards arrive, **only HEALTH/TIMER-verified XP counts toward ranked boards**, and the server recomputes from events.
- R8 enabled for release; no secrets in repo; `keystore.properties` git-ignored.

## 6. Testing strategy
| Layer | Tooling | What |
|---|---|---|
| `:engine` | JUnit5 + Truth (+ kotest-property optional) | All GD §12 vectors; level monotonicity; caps; streak edge cases (rest, recovery, shield, multi-day gap); generator determinism & novelty window; crit rate ≈ 8% over 100k seeds (±0.5%) |
| Data | Room in-memory + Robolectric or instrumented | Ledger idempotency (same event twice), NULL-uniqueness bug, undo, rebuild-from-ledger == cache, migrations (`MigrationTest`) |
| Use cases | Fakes + FakeClock | Complete→level-up; rollover over 5 missed days; DST boundary; day-start 04:00 edge (03:59 vs 04:00) |
| UI | Compose UI test | Onboarding happy path; complete quest shows reward; gate collapse state |
| Paths (app) | Fakes + FakeClock + Robolectric | Week accepted twice = no duplicate slots; plan arrives mid-week (only remaining days scheduled); carry-over then SKIPPED at week end; consent revoked mid-request; gate cleared from a Path quest pays once (GD vector 33); worker retries reuse `requestId` |
| Contract | JSON Schema in `/contracts` | `:engine` tests load every fixture and validate it against the response schema; the proxy's zod schemas are checked against the same files in CI, so client and proxy can't drift |
| Proxy | Vitest + mocked LLM + mocked Integrity API | Each pipeline step's error code; requestId idempotency; budget exhaustion; injection fixtures ("ignore previous instructions…") still produce schema-valid output; no body ever reaches the logger |
| Prompt eval | `proxy/eval/run.ts` against staging | 30 goals across all 5 stats (incl. vague, non-English, adversarial, injured-user cases). Pass bar: ≥ 90% of blueprints survive PathValidator, 0 blueprints break the content rules on manual review. Re-run on every prompt version change |
| Manual | `docs/QA_CHECKLIST.md` | Real device: notifications, widget, HC permission flow, backgrounding a gate, airplane mode (Paths fall back cleanly), consent decline/revoke, report a quest, font scale 200%, TalkBack on main flows |

## 7. Observability (offline app edition)
- `Timber` debug tree only in debug builds.
- In-app **Debug screen** (debug builds): ledger viewer, balance rebuild, FakeClock time-travel (+1 day), force rollover, seed templates check. This is how you (and Claude Code) test day-based logic without waiting a day.
- Production crash/ANR data: Play Console → Android vitals (no SDK required).
- Debug screen also shows: each Path's `path_week` rows, raw last response (debug builds only), validator drop reasons, and buttons "Force week re-plan" and "Use FakePathOracle".
- **Proxy:** Workers analytics + structured logs (fields in §3.8). Dashboards: requests by route and status, p50/p95 model latency, validation drop rate, integrity failures, 429s, spend vs budget. Alerts: spend ≥ 80% of the monthly budget; integrity failures > 20% of traffic for 1 h; `model_invalid_output` > 5% for 1 h.

## 8. Later (v1.2+): sync & social — sketch only
When accounts arrive, the proxy gains an authenticated route and Paths can sync like everything else. Until then it stays stateless.
```
App ledger events ──(batched, idempotent, signed session)──▶ Ingest API (your existing Node/TS services)
                                                              │  recompute with the SAME engine rules
                                                              ▼  (port :engine or share via KMP)
                                               Postgres ledger ─▶ Leaderboard (Redis ZSET, verified-XP only)
```
Kotlin Multiplatform could share `:engine` with a Ktor backend; with a TS backend, port the engine and run the GD §12 vectors as a cross-language contract test.
