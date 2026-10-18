# AWAKEN — Claude Code Prompt Playbook

Paste these **in order**, one phase per fresh context. Each prompt is self-contained and points at the docs, so Claude Code reads only what it needs.

## How to run each phase (do this every time)

1. `/clear` (fresh context per phase — keeps quality high and usage low).
2. Press **Shift+Tab** until you're in **plan mode**, paste the phase prompt.
3. Read the plan. Push back if it touches files outside the phase or invents numbers. Then approve.
4. When it finishes: run the app on your phone/emulator yourself. Look at it. Tap everything.
5. Paste the **Review prompt** (bottom of this file). Fix what it finds.
6. Commit: Claude Code can do it — "commit this phase with a conventional commit message".

Your Pro plan's usage resets on a rolling 5-hour window and is shared with the Claude app. Batch your phases so a heavy phase starts right after a reset; while you wait, do the human tasks in `00_START_HERE.md` (screenshots, listing, testers).

**Cut line if you're behind schedule** (move to v1.1, in this order): F16 share card → F15 widget → F10 Rank Trials → F11 adaptive difficulty → F9 Health Connect. Never cut: ledger correctness, rollover/streak, onboarding, Today, completion, Gates, Status radar.

---

## Phase 1 — Project foundation  *(Day 1, ~1.5 h)*

```
Read CLAUDE.md fully, and ARCHITECTURE.md §1 and §4.

This is a fresh Android Studio "Empty Activity" Compose project with package com.assismohanty.awaken.
Set up the foundation:
1. Gradle version catalog (gradle/libs.versions.toml) with the latest STABLE versions of: Kotlin, AGP, Compose BOM,
   Material3, Navigation Compose, Hilt + KSP, Room + KSP, DataStore Preferences, WorkManager (+ hilt-work),
   Glance appwidget, Health Connect client, kotlinx-serialization-json, Timber, JUnit5, Truth, Turbine, coroutines-test,
   Robolectric. minSdk 28, targetSdk 36, compileSdk 36, Java 17.
2. Create module :engine (kotlin jvm, JUnit5) and make :app depend on it.
3. :app — @HiltAndroidApp AwakenApp, MainActivity with edge-to-edge, AwakenNavHost with 4 bottom destinations
   (Today, Gates, Status, Vault) using type-safe routes, plus placeholder screens.
4. Clock interface + SystemClock impl + DispatchersModule.
5. Debug-only Timber tree. R8 enabled for release with sane keep rules for Room/Hilt/serialization.
6. Release signing reads from keystore.properties (git-ignored). Add .gitignore entries.
7. Add docs/BACKLOG.md (empty template) and docs/QA_CHECKLIST.md (skeleton with sections from ARCHITECTURE §6 "Manual").
Do not implement game logic yet. Finish with: ./gradlew :engine:test :app:assembleDebug passing. Show me the final libs.versions.toml.
```

## Phase 2 — Rules engine with tests  *(Day 1, ~2 h — the most important phase)*

```
Read CLAUDE.md, then GAME_DESIGN.md completely.

Implement the pure-Kotlin rules engine in :engine exactly as specified. No Android imports.
- model: Stat, Difficulty, Verification, QuestType, QuestStatus, Rank, LedgerKind, QuestTemplate (kotlinx.serialization),
  QuestDraft, HunterContext (streak, todaySelfXp, todayTimerXp, tiers, focusStats, equipment, injuryMode), QuestReward
  (xp, statXp map, gold, crit, cappedSource?, fragments, breakdown: List<BreakdownItem>).
- XpCalculator (GD §4.1, §4.3, §9.1), CritRoller with FNV-1a 64-bit (GD §4.2), LevelCurve (GD §4.4, cumulative table
  cached once, level capped 100), StatLevel (GD §3), RankRules (band + trial gating, GD §5), StreakRules (GD §7:
  cleared day, rest, recovery budget, shield consumption, penalty flag — expose a pure fold over a list of DayRecords),
  DailyCaps, DirectiveGenerator (GD §6.3: deterministic SplittableRandom seeded by hash(hunterId, gameDate), filters,
  7-day novelty, weights, main/side rules, reroll), DifficultyAdapter (GD §6.4), AchievementEvaluator (GD §10; pure
  function snapshot -> newly unlocked ids), GameDay (Instant + ZoneId + dayStartHour -> LocalDate).
Tests (JUnit5 + Truth):
- Every row of GAME_DESIGN §12 as a named test.
- LevelCurve: monotonic, level(cumulative(L)) == L for all L in 1..100, level(cumulative(L)-1) == L-1.
- CritRoller: rate over 100_000 random UUIDs within 7.5%..8.5%; same id -> same result.
- DirectiveGenerator: same inputs -> identical output; no template repeats within 7 days across a simulated 30-day run
  with a 150-template fixture; respects equipment and injury filters; never returns fewer than 4 quests when the pool
  allows it.
- GameDay: 03:59 belongs to previous day with dayStartHour=4; DST transition days in Europe/London and Asia/Kolkata.
- StreakRules: gap of 5 unopened days, rest day, recovery budget exhaustion, shield used once.
If anything in GAME_DESIGN is ambiguous, list the ambiguity and your proposed resolution before coding.
Done when ./gradlew :engine:test passes. Print the test summary.
```

## Phase 3 — Content: templates, narrative, system lines  *(Day 1, ~1 h)*

```
Read GAME_DESIGN.md §3, §6.2, §8, §10, §11 and DESIGN_SYSTEM.md §5 (copy voice).

Create content assets in app/src/main/assets:
1. quests/templates.json — at least 150 templates following the GD §6.2 schema: >=25 per stat, all 4 difficulty targets,
   2–3 evocative original titles each (no Solo Leveling names/quotes), realistic targets (beginner-safe EASY),
   correct equipment and tags (impact, legs, upper, outdoor, indoor, social, quiet). Include:
   - HEALTH_STEPS templates (steps targets 4k/7k/10k/14k), HEALTH_EXERCISE (15/30/45/60 min), HEALTH_SLEEP (7h baseline only for VIT),
   - a "penalty" set of 15 short Normal quests doable anywhere,
   - CHA quests that are realistic for introverts too.
2. narrative/chapters.json — 6 chapters (L1, L5, L10, L20, L35, L50), each <=120 words, second-person, original lore about
   Gates and the System.
3. narrative/system_lines.json — ~60 lines keyed by context (streak_milestone, comeback, neglected_<stat>, trial_active,
   cleared_day, generic).
4. A :engine test that loads templates.json (copy it as a test resource via Gradle task) and validates: unique ids,
   all targets present & increasing, valid enums, >=25 per stat, no empty titles.
Run the tests.
```

## Phase 4 — Data layer & use cases  *(Day 1, ~2 h)*

```
Read CLAUDE.md invariants, ARCHITECTURE.md §2, §3.1, §3.2, §4, §5, §6 and docs/ENGINE_DECISIONS.md (#17, #20–#24).

Implement in :app core/data + core/domain:
1. Room AwakenDatabase (exportSchema=true, schemas in app/schemas) with ALL entities in ARCH §2, including `completion`,
   `ledger_entry.xp_source` and `day_record.outcome`. Ledger uniqueness must not be defeated by NULLs (store '' for absent
   stat/item_id). DAOs return Flow for observed data.
2. LedgerRepository: insertIdempotent(rows) returns rows actually inserted; balances cache updated in the SAME transaction;
   rebuildBalancesFromLedger(). Undo rows copy the original's game_date and xp_source.
3. Use cases:
   - GenerateDirectiveUseCase (idempotent per game_date; deterministic instance ids make re-inserts no-ops).
   - CompleteQuestUseCase (ARCH §3.1, returns CompletionOutcome). Each attempt gets completionId = questInstanceId#attemptN;
     ledger sourceId = completionId; the crit stays seeded by questInstanceId. Daily caps use today's net XP per
     xp_source. Manual completion is SELF-only (GD §6.5); TIMER/HEALTH completions come through a separate verified entry
     point used by Gates (Phase 8) and Health sync (Phase 11).
   - UndoCompletionUseCase (10-min window, reversing rows with sourceId = completionId, sets completion.undone_at,
     quest back to ACTIVE/STARTED). Achievements are never revoked (GD §6.5).
   - RerollQuestUseCase (side quests only; 1 free + max 3 paid at 40 gold).
   - DayRolloverUseCase (ARCH §3.2 catch-up loop; outcome stored per day; DAY_CLEAR +20 gold on CLEARED; shield ITEM -1
     on SHIELDED; one penalty quest today if any finalized day was BROKEN; DifficultyAdapter when due).
   - ObserveHunterStateUseCase (combines balances + derived level/rank/streak into one Flow; streak = StreakRules.replay
     over finalized outcomes, +1 once today is cleared).
4. TemplateLibrary loader (assets -> engine models, validated) provided by Hilt as a singleton, loaded off the main thread.
5. Tests (Robolectric + in-memory Room + FakeClock): same completion applied twice = one set of rows; undo nets to zero;
   complete -> undo -> complete = one net reward with the same crit; manual completion of a HEALTH quest is rejected;
   rebuild == cache; rollover across 5 missed days with 1 shield; generation twice same day = no duplicates; level-up
   detected when crossing threshold; daily cap counts undone XP as zero.
6. Debug screen (debug source set only): ledger table viewer, balances, today's quests with complete/undo/reroll,
   buttons: seed test hunter (no onboarding until Phase 6), +1 day (FakeClock offset persisted in DataStore, debug only),
   force rollover, rebuild balances, wipe data.
Done when all tests pass and debug APK builds.
```

## Phase 5 — Design system & component catalog  *(Day 1 evening, ~2 h)*

```
Read DESIGN_SYSTEM.md completely, then do exactly what §7 "Design prompt to give Claude Code" says.

Fonts: download Saira Condensed (600, 700) and IBM Plex Sans (400, 500, 600) TTFs from the google/fonts GitHub repo
(OFL) into app/src/main/res/font with snake_case names; include their OFL.txt in app/src/main/assets/licenses/.
If downloading fails, stop and tell me which files to place manually.

Build every component in DESIGN_SYSTEM §3 with previews, the rank-tinted background (radial gate glow), the level-up
ceremony as a reusable composable driven by (oldLevel, newLevel, statGains) with a reduce-motion variant, and the
Haptics wrapper. Add the DesignSystemCatalog to the Debug screen.
Install on device; I will review visually before we continue.
```

## Phase 6 — Onboarding, Today, completion loop  *(Day 2 morning, ~3 h)*

```
Read PRD_MVP.md F1–F6, DESIGN_SYSTEM.md §4.1–4.3 and §5, ARCHITECTURE.md §3.1.

Implement:
1. Awakening onboarding exactly per DESIGN_SYSTEM §4.1 (steps 1–7; step 5 Health Connect button may be a stub that
   says "Coming in this build" until Phase 11 — wire the real flow then). Calibration answers map to baseline tiers:
   Rarely/<1h/<6h -> EASY, Sometimes/1–3h/6–7h -> NORMAL, Often/3h+/7h+ -> HARD (STR, INT, VIT respectively;
   WIL and CHA start EASY). First directive guarantees one EASY quest in a focus stat.
2. Today screen per §4.2 with HunterHeader, Directive window, habits section, System line, penalty/trial slots.
3. Quest row interactions: swipe-to-complete, tap -> Quest detail with Begin/Complete/Reroll, undo snackbar (10 min).
4. RewardSheet with the formula breakdown; chain into level-up ceremony and SystemToasts for achievements.
5. Custom habits: add/edit/archive sheet (max 8; difficulty capped at Normal for SELF per GD §4.3).
6. "First directive guarantees one EASY quest in a focus stat" needs an engine change first (the generator currently makes
   the main quest tier+1). Add it to DirectiveGenerator with a test and a line in GAME_DESIGN §6.3 before building the UI.
ViewModels follow UDF from CLAUDE.md. Add Compose UI tests: onboarding happy path; completing a quest shows the reward
sheet with correct XP for a known fixture.
```

## Phase 7 — Days, streaks, penalty, trials, adaptation  *(Day 2, ~2 h)*

```
Read GAME_DESIGN.md §5, §6.4, §7 and ARCHITECTURE.md §3.2.

Wire DayRolloverUseCase to: app start, ON_START lifecycle, and a periodic DayRolloverWorker (Hilt worker).
Implement UI for: Penalty Zone moment (full-screen ember window once per failed day, reduce-motion aware) + pinned
penalty quest; Rest Day picker (next 7 days, 1 per ISO week, must be set before the day starts); Recovery Mode toggle
with remaining-days budget; Streak Shield auto-use message the next morning ("A shield absorbed yesterday's miss");
Rank Trial banner, trial detail (3 stages, 5-day window), promotion ceremony (reuse level-up ceremony with rank crest);
weekly recalibration System message. Test with the Debug screen's +1 day. Add use-case tests for each path.
```

## Phase 8 — Focus Gates  *(Day 2, ~1.5 h)*

```
Read GAME_DESIGN.md §8, ARCHITECTURE.md §3.3, DESIGN_SYSTEM.md §4.4 (GateDial).

Implement Gates tab and the gate run WITHOUT a foreground service, exactly as ARCH §3.3 describes: Room-persisted run,
ProcessLifecycleOwner background accounting (survives process death), ongoing notification with system chronometer
countdown, end-of-gate notification via WorkManager one-shot, one pause ≤5 min, breach at >90 s background, clear ->
CompleteQuestUseCase with Verification.TIMER, collapse screen. Edge cases: app killed mid-run (background time =
now − lastStopAt on next start); device rebooted mid-run (elapsedRealtime resets, so background time is unknowable →
mark COLLAPSED with reason "Device restarted"). Tests for the background-accounting math.
```

## Phase 9 — Status screen  *(Day 2, ~2 h)*

```
Read PRD_MVP.md F12, F16 and DESIGN_SYSTEM.md §3 (RadarChart, Heatmap, ShareCard) and §4.5.

Build Status: large HunterHeader, RadarChart (this week vs last week stat XP gained, normalised per DS), stat list,
18-week Heatmap, achievements & titles grid (equip title), chapters list with reveal on unlock, Weekly Report card
(Sunday, or on demand in debug) with Share as image (1080×1350). Charts must have TalkBack descriptions.
Everything reads from Room flows via ObserveHunterStateUseCase / dedicated queries — no business logic in composables.
```

## Phase 10 — Vault, settings, export/import  *(Day 2 evening, ~2 h)*

```
Read GAME_DESIGN.md §9, PRD_MVP.md F13, F17, DESIGN_SYSTEM.md §4.6–4.7.

Vault: shop items exactly per GD §9.2 (prices, limits, rank requirement for Sovereign Gold theme), purchase use case
writing GOLD -n and ITEM +1 rows in one transaction with idempotency key = client-generated purchase UUID, insufficient
funds state, Relic Cache (10 fragments -> random unowned cosmetic, deterministic seed = cache-open UUID), equip themes
(re-tints background glow + window edges; theme overrides rank colour only for accents, never for rank crest).
Settings per DS §4.7 including equipment owned, injury mode tags, day start hour, reminder time, haptics, reduce motion.
Export: SAF "create document" JSON with schemaVersion + all tables. Import: validate, replace DB in a transaction,
rebuild balances, show summary. Tests for purchase atomicity and export->import round-trip equality.
```

## Phase 11 — Health Connect verification  *(Day 3 morning, ~2 h)*

```
Read ARCHITECTURE.md §3.4 and the official Health Connect "get started" guide (search the web if you need the current
API surface; do not guess method names).

Implement HealthConnectGateway (availability check incl. "not installed" -> deep link to Play Store, permission
contract for READ Steps, ExerciseSession, SleepSession only), the rationale activity/intent filter + privacy policy
screen required by Health Connect, onboarding step 5 + Settings toggle, HealthSyncWorker (30 min + on open) that
aggregates today's steps, exercise minutes and last night's sleep and auto-completes matching HEALTH quests via
CompleteQuestUseCase's verified entry point (ledger sourceId = completionId per GD §9.3). If HC is unavailable, HEALTH templates are excluded
from generation. Add a fake gateway for tests and a Debug toggle "simulate 9,000 steps".
```

## Phase 12 — Notifications & widget  *(Day 3, ~1.5 h)*

```
Read ARCHITECTURE.md §3.5–3.6.

Notification channels and the two daily notifications (directive at reminder time; last-call at 21:00 only if day not
cleared) scheduled with WorkManager respecting game-day; POST_NOTIFICATIONS requested after the first completed quest
with a System-styled pre-prompt. Glance TodayWidget 4x2 per spec, refreshed after every ledger write and at rollover.
Deep links from notifications/widget to Today. Test on Android 13+ (permission) and Android 10.
```

## Phase 13 — Hardening & release build  *(Day 3, ~2 h)*

```
Act as a strict senior Android reviewer. Read CLAUDE.md, PRD_MVP.md §7–9, DESIGN_SYSTEM.md §6.

1. Run lint, fix all errors and meaningful warnings.
2. Accessibility pass on every screen (content descriptions, 48dp, font scale 200% screenshots via previews, TalkBack order).
3. Performance: no work on main thread (StrictMode in debug), stable/immutable UI state classes, lazy list keys, remember
   derivedStateOf for heavy calcs; add a Baseline Profile module if time permits.
4. Edge cases: process death on every screen (SavedStateHandle), rotation, airplane mode, clock changed backwards,
   timezone change, empty states everywhere.
5. Verify AndroidManifest has NO INTERNET permission and only the permissions we need; list them for me.
6. App icon (adaptive, monochrome layer for themed icons): an original gate/portal glyph in system blue on void.
   Splash via core-splashscreen.
7. Build signed release AAB (I will create the keystore: give me the exact keytool command and keystore.properties format).
8. Fill docs/QA_CHECKLIST.md with concrete steps; run the automatable ones.
Output a release-readiness report: what's done, what's risky, what to cut.
```

## Phase 14 — Store kit  *(Day 3 evening, ~1 h)*

```
Read docs/PLAY_STORE_LAUNCH.md.
1. Create /privacy-policy/index.html (static, GitHub Pages-ready) from the template in that doc, filled for this app.
2. Add a debug-only "Screenshot mode" that seeds a realistic demo hunter (LV 24, C-rank, 12-day streak, varied stats,
   unlocked achievements) so I can capture store screenshots.
3. Generate feature graphic source (1024×500) as an SVG/Compose render using our design system.
4. Write fastlane-style metadata files under /store/en-US/ (title, short_description, full_description) from the doc.
```

---

## Review prompt (after every phase)

```
Switch to reviewer mode. Do not write code yet.
Review the diff of this phase against CLAUDE.md invariants, GAME_DESIGN numbers, and DESIGN_SYSTEM rules.
List: (1) invariant violations, (2) wrong numbers/formulas, (3) bugs and edge cases (time, process death, idempotency),
(4) UI deviations (raw colours/dp, missing previews, accessibility), (5) missing tests.
Rank by severity. Then fix everything severity high/medium and re-run the full test + build commands.
```

## Debugging prompt (when something breaks)

```
Bug: <what I did> → <what happened> → <what I expected>. Logcat attached below.
First reproduce it with a failing test (engine or Robolectric). Then find the root cause — explain it in 3 lines.
Then fix with the smallest change, make the test pass, and check whether the same bug pattern exists elsewhere.
```
