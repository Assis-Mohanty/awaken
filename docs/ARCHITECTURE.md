# AWAKEN — Android Architecture (v1.0)

## 0. Why this architecture (and not the 13-microservice one)

v1 has **zero users**. A distributed backend buys nothing yet and costs weeks: auth, hosting, sync conflicts, a privacy policy that now covers server data, and a Play Data-safety form that says "collects data". An offline-first app:

- ships in days, works on a plane, and lets the Data-safety form say **"No data collected"** — a real trust signal competitors can't claim;
- keeps the *same invariants* the backend design wanted (append-only ledger, idempotency keys, deterministic engine), so syncing later is "upload ledger events, server recomputes" — not a rewrite.

The existing `userService`/`questService` repo stays as a learning/portfolio project and the future sync target (v1.2+). See `REPO_AUDIT.md`.

```
┌──────────────────────────────── :app (Android) ────────────────────────────────┐
│  UI (Compose)          feature/today  quest  gate  status  vault  onboarding    │
│     │ events ▲ StateFlow<UiState>                                               │
│  ViewModels (Hilt)                                                              │
│     │                                                                           │
│  core/domain  — use cases: CompleteQuest, StartGate, GenerateDirective, ...     │
│     │   calls pure functions ───────────────────────┐                           │
│  core/data    — Room DAOs, repositories, DataStore,  │                          │
│                 HealthConnectGateway, Clock          │                          │
│  workers      — DayRolloverWorker, HealthSyncWorker, ReminderWorker            │
│  widget       — Glance TodayWidget                   │                          │
└──────────────────────────────────────────────────────┼──────────────────────────┘
                                                       ▼
                                   ┌──────── :engine (pure Kotlin/JVM) ────────┐
                                   │ XpCalculator  LevelCurve  RankRules        │
                                   │ StreakRules   DailyCaps   CritRoller       │
                                   │ DirectiveGenerator  DifficultyAdapter      │
                                   │ AchievementEvaluator  model/*              │
                                   └────────────────────────────────────────────┘
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
│     └─ GameDay.kt  (Instant + zone + dayStartHour -> LocalDate)
│  └─ src/test/kotlin/...  (GAME_DESIGN §12 vectors + property tests)
└─ app/
   └─ src/main/
      ├─ assets/quests/templates.json   assets/narrative/chapters.json, system_lines.json
      ├─ res/font/  (Saira Condensed, IBM Plex Sans — OFL; see DESIGN_SYSTEM §2.2)
      └─ java/com/assismohanty/awaken/
         ├─ AwakenApp.kt (@HiltAndroidApp)  MainActivity.kt
         ├─ core/designsystem/  theme/ (Color, Type, Shape, Motion, Haptics), components/
         ├─ core/data/  db/ (AwakenDatabase, entities, daos), repo/, prefs/, health/, clock/
         ├─ core/domain/ usecases
         ├─ feature/onboarding|today|quest|gate|status|vault|settings/
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

quest_instance(id PK, game_date, type, template_id NULL, habit_id NULL,
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
```

UNIQUE with nullable columns: SQLite treats NULLs as distinct → store `''` instead of NULL for `stat`/`item_id` in the ledger to keep the uniqueness guarantee. (This is a classic bug; add a test.)

Ledger sourceIds: QUEST/GATE → completionId; UNDO → the undone row's sourceId; DAY_CLEAR and SHIELD_USE → game_date; ACHIEVEMENT → achievement id; SHOP → purchase UUID (rerolls: `reroll:<game_date>#n`).

Migrations: `exportSchema = true`, schemas committed under `app/schemas/`. Every schema change bumps the version and adds a `Migration` (`core/data/db/Migrations.kt`, listed in `ALL_MIGRATIONS`) plus a case in `MigrationTest`, which builds the old database from its exported JSON (the schemas are debug-only assets) and opens it with the current Room, so Room validates the result. (`MigrationTestHelper` is not used: its `createDatabase` rejects Robolectric database paths on Windows.) Current version: 4 (v1→v2 rebuilt the then-empty `gate_run` for Focus Gates; v2→v3 added `hunter_profile.equipped_frame_id` and `equipped_sigil_id` for the Vault; v3→v4 added `injury_tags` for Settings). **No destructive fallback, ever: hunters never lose data.**

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

### 3.9 Status & Weekly Report share
`ObserveStatusUseCase` derives everything Status shows from Room flows: radar (current stat levels vs levels from ledger rows before this Monday), 18-week heatmap (active completions per game-day), the 32 achievements, chapters (`level ≥ unlockLevel`), and the Weekly Report with a projected recalibration. Equipping a title updates `hunter_profile.equipped_title_id` only, never the ledger. To share, `ShareCard` is laid out offscreen at 360×450dp with density 3, recorded into a `GraphicsLayer` (1080×1350px), written by `ShareImageWriter` to `cache/share/` (one file, overwritten), and handed to `ACTION_SEND` through `androidx.core.content.FileProvider` (authority `${applicationId}.share`, cache path only, with a per-share read grant). No network.

### 3.10 Vault, Settings and export/import
`PurchaseItemUseCase` and `OpenRelicCacheUseCase` each run in one transaction and are idempotent on a client-generated id (`SHOP` / `CACHE_OPEN` ledger sources). `ShopRules` (engine) owns the catalogue, prices and the deterministic cache drop. Settings edits go through `UpdateSettingsUseCase` (profile row only); haptics and reduce-motion live in a DataStore (`UiPrefsRepository`) and feed `AwakenTheme` with the equipped theme via `AppearanceViewModel`. `GameDataPorter` exports the raw rows of 12 tables as JSON (SAF, no permission, no network) and imports them atomically (ENGINE_DECISIONS #48).

## 4. State & DI
- Hilt modules: `DatabaseModule`, `DispatchersModule` (`@IoDispatcher`, `@DefaultDispatcher`), `ClockModule` (`Clock` interface; `SystemClock` in prod, `FakeClock` in tests), `EngineModule` (provides engine objects + loaded template library).
- DataStore (Preferences) only for UI prefs: reduce motion, haptics on/off, sound on/off.
- Everything game-relevant lives in Room (single source of truth, transactional).

## 5. Security, privacy, abuse
- No INTERNET permission in v1 → nothing to exfiltrate, no backend to attack.
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
| Manual | `docs/QA_CHECKLIST.md` | Real device: notifications, widget, HC permission flow, backgrounding a gate, airplane mode, font scale 200%, TalkBack on main flows |

## 7. Observability (offline app edition)
- `Timber` debug tree only in debug builds.
- In-app **Debug screen** (debug builds): ledger viewer, balance rebuild, FakeClock time-travel (+1 day), force rollover, seed templates check. This is how you (and Claude Code) test day-based logic without waiting a day.
- Production crash/ANR data: Play Console → Android vitals (no SDK required).

## 8. Later (v1.2+): sync & social — sketch only
```
App ledger events ──(batched, idempotent, signed session)──▶ Ingest API (your existing Node/TS services)
                                                              │  recompute with the SAME engine rules
                                                              ▼  (port :engine or share via KMP)
                                               Postgres ledger ─▶ Leaderboard (Redis ZSET, verified-XP only)
```
Kotlin Multiplatform could share `:engine` with a Ktor backend; with a TS backend, port the engine and run the GD §12 vectors as a cross-language contract test.
