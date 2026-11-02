# Build plan: v1.2 feature batch

Specs: GAME_DESIGN §16 (rules, numbers `N1`–`N31`, vectors #85–#134), DESIGN_SYSTEM §9 (components, screens, states), ARCHITECTURE §2.1 + §3.15 (tables, migrations, flows), PRD F35–F51.

**Status: Batch 0 done (spec only) in e9b2329. GD §16 approved 2026-11-02; every question in §0 resolved with its recommendation. Next: Batch 1.**

## How every batch works

1. Read this batch's section, GD §16 for its features, DS §9 and ARCH §3.15. Follow them exactly. If something is ambiguous or conflicts, write it in `docs/BUILD_QUESTIONS.md` and **stop**; never guess a number.
2. Engine first: rules + the batch's GD §16.17 vectors as JUnit5 tests in `:engine`. Then data (entity, DAO, migration, `MigrationTest`, export), then domain, then UI.
3. Invariants (CLAUDE.md): ledger only, idempotent `(sourceType, sourceId, kind)` keys, one transaction per game action, rules in `:engine`, injected `Clock`, no network, no new Accessibility Service, semantic tokens / ChartKit / Lexicon in `feature/**`, strings in `strings.xml` (+ `_clean` twin for System-voice strings, then regenerate `LexiconPairs`), `@PhonePreviews` for every state in DS §9, TalkBack text, reduce motion.
4. Gate: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint` and `./gradlew :app:verifyRoborazziDebug`. Re-record goldens (`recordRoborazziDebug`) only for the screens this batch changes, and name them in the commit and the summary.
5. Add an ENGINE_DECISIONS row (#115 onward) for every decision made while building. Mark each item here with `✅ built in <commit>` under its heading. Commit `feat(<area>): …`, write a short summary, **stop until the user says "next"**.

## 0. Open questions — resolved 2026-11-02: the user chose every recommendation

Q1 keep H1 dropped (Batch 6 builds nothing for H1) · Q2 separate allowance, max 2 habit reminders · Q3 on a tap · Q4 evening tap · Q5 ship D4 without a lock; H3 added to `BACKLOG.md` · Q6 yes, one-time gold · Q7 calculator only · Q8 mutually exclusive · Q9 no · Q10 1.5 × fee.

| # | Question | Recommendation |
|---|---|---|
| Q1 | **H1 Clean/System mode** was dropped by you on 2026-10-27 (System mode removed in the v2 redesign; ENGINE_DECISIONS #109, PRD F34). It is in this scope list. Bring it back (Settings toggle + the System lexicon strings, gate glow off in Clean), or keep it dropped? | Keep dropped unless you want the game words back; if yes, Batch 6 builds GD §14.9 as written minus the invite sheet. |
| Q2 | **Habit reminder budget.** ARCH §3.5 caps non-gate notifications at 2/day and both are used (directive, last call). The ideas file says reminders count "inside" that cap, which leaves no room. | A separate allowance: at most 2 habits with a reminder, each at most once a day, own channel `habit_reminder` (`N5`). |
| Q3 | **Pomodoro next round:** start automatically when the break ends, or on a tap? A round that starts while you're out of the app spends its 90 s breach budget. | On a tap (notification action or in the app). |
| Q4 | **Negative ("Avoid") habits:** auto-complete at day end unless you log a slip, or require an evening "Kept it today" tap? Auto-complete pays XP for a day the app never saw (fails open). | Require the evening tap (fails closed). |
| Q5 | **D4 and app lock.** The ideas file says H3 app lock must ship before D4 relapse logs; H3 is not in scope. | Ship D4 without a lock (notes optional, stored on-device, in your export only) and put H3 in the backlog; or add H3 to Batch 5. |
| Q6 | **PR achievements pay gold** (10/50/150) from self-logged weights. Gym is a no-reward tracker today (GD §13). | Yes, one-time achievements only (bounded); or no gold, badges only. |
| Q7 | **lb support:** only inside the plate calculator, or a global kg/lb unit for all gym logging? | Calculator only (global units touch every gym screen, the catalog and records). |
| Q8 | **Program and 45-day Campaign together?** Both take habit slots and both are "long arcs". | Mutually exclusive: one long arc at a time. |
| Q9 | Programs: should **FINISHED** (completed with continued breaks) earn anything? | No; only a no-break run unlocks the achievement. |
| Q10 | Dungeon fee/reward numbers make it roughly neutral for someone who clears ~70 %. Want it more of a sink (reward 1.25 × fee) or more of a reward (2 × fee)? | 1.5 × fee as proposed (`N29`). |

## Batch 1 — Habits: A1 types, A2 reminders + skip

**Engine** (`engine/src/main/kotlin/com/assismohanty/awaken/engine/`)
- [ ] `model/Enums.kt`: `HabitType { CHECK, QUANTITY, TIMED, NEGATIVE }`, `HabitFrequency { DAYS_OF_WEEK, TIMES_PER_WEEK }`, `QuestStatus.SKIPPED` (not hidden; not an item; not a miss).
- [ ] `HabitRules.kt`: validation (`N1`–`N4`), timed difficulty from minutes (§8.1 P2 tiers), `instanceFor(habit, day, completionsThisWeek)`, flexible expiry (EXPIRED not MISSED), negative confirm window (Evening block start from `TimeBlocks`), `canSkip(skipsThisWeek)` (`N6`), reminder limit (`N5`).
- [ ] `StreakRules` / day-clear item counting: SKIPPED is not an item; flexible EXPIRED is not a miss.
- [ ] Tests: `HabitRulesTest` covering vectors **#85–#95**.

**Data** (`app/src/main/java/com/assismohanty/awaken/core/data/`)
- [ ] `db/entity/Entities.kt`: `HabitEntity` + `type`, `frequency`, `timesPerWeek`, `reminderTime`; `QuestInstanceEntity` + `progress`; `GateRunEntity` + `habitQuestId`.
- [ ] `db/AwakenDatabase.kt` `SCHEMA_VERSION = 19`; `db/Migrations.kt` `MIGRATION_18_19` (ALTER TABLE … ADD COLUMN with defaults); exported `app/schemas/…/19.json`.
- [ ] `app/src/test/…/core/data/MigrationTest.kt`: 18 → 19 case (old rows get CHECK / DAYS_OF_WEEK / progress 0).
- [ ] DAOs: open-instance progress update, skips this ISO week, completions this ISO week per habit.
- [ ] Export: columns ride on existing tables; `GameDataPorterTest` round-trip with the new columns.

**Domain** (`core/domain/`)
- [ ] `HabitUseCases.kt`: draft gains type/frequency/timesPerWeek/reminderTime; `SaveHabitUseCase` validates via `HabitRules` (timed difficulty derived, not user-chosen).
- [ ] `GenerateDirectiveUseCase.kt`: flexible weekly instances.
- [ ] New `HabitProgressUseCases.kt`: `+1/−1` (completes at target via `CompleteQuestUseCase`; −1 on completed = undo), `ConfirmKeptUseCase`, `SlipUseCase` / undo slip, `SkipHabitUseCase` / `UndoSkipUseCase`.
- [ ] TIMED: `GateUseCases.kt` start with `habitQuestId`; `GateRunController` pays the quest once (mirror `PathGateUseCase.kt`), not P5.
- [ ] `RunRolloverUseCase` / `DayRolloverUseCase`: flexible → EXPIRED, unconfirmed negative → MISSED.

**Work**
- [ ] `core/work/HabitReminderWork.kt` (`HabitReminderScheduler` + worker, unique per habit), channel `habit_reminder` in `ReminderNotifications.kt`; re-armed by `ReminderCoordinator.kt` and rollover; posts only if the instance is open.

**UI** (`feature/today/`, `core/designsystem/components/`)
- [ ] Components: `QuantityStepper`, `HabitTypeChip`, `NegativeHabitRow` (+ previews of every DS §9.1 state).
- [ ] `HabitSheets.kt` editor per DS §9.3 (type, frequency, reminder; read-only campaign/program).
- [ ] `TodayScreen.kt` rows per type; long-press **Skip today / Undo skip**.
- [ ] Strings (+ `_clean` twins where System voice), regenerate `LexiconPairs`.

**Acceptance:** vectors #85–#95 pass; MigrationTest 18→19 passes; an existing habit looks and behaves exactly as before; a timed habit pays one QUEST reward with V TIMER; reminders never fire for a done/skipped habit; previews for every state; gate green; goldens changed: habit editor, Today habit rows (name them).

## Batch 2 — Focus: B1 Pomodoro, B2 goal ring + streak, A4 tile, A5 Start focus widget

**Engine**
- [ ] `PomodoroRules.kt` (phase machine: FOCUS → SHORT_BREAK … → LONG_BREAK → DONE; collapse → COLLAPSED; `N7`–`N10` bounds).
- [ ] `FocusGoalRules.kt` (`focusMinutes`, versioned goal lookup, `met`, focus streak skipping REST/RECOVERY; `N11`, `N12`).
- [ ] `AchievementEvaluator.kt`: `focus_streak7/30/100` (`N31`).
- [ ] Tests: vectors **#96–#101**.

**Data**
- [ ] Entities `PomodoroSessionEntity`, `FocusGoalEntity`; `GateRunEntity` + `pomodoroId`, `pomodoroRound`. `SCHEMA_VERSION = 20`, `MIGRATION_19_20`, schema 20.json, MigrationTest 19→20.
- [ ] `ExportDocument.TABLES` += `pomodoro_session`, `focus_goal`; `ExportUpgrade`; round-trip test.
- [ ] `core/data/repo/LastFocusStore.kt` (DataStore: last mode, minutes/rank, stat, subject; Pomodoro defaults).

**Domain / work**
- [ ] `core/domain/PomodoroController.kt` (start session, start round as CUSTOM gate with `pomodoroId`, break timing, skip break, end, collapse handling).
- [ ] `core/work/BreakEndWorker.kt` (+ notification on `gate` channel, action opens the app).
- [ ] Focus goal: `FocusGoalUseCases.kt` (set goal from today, observe ring + streak).
- [ ] `LastFocusStore` written on every session start (preset, custom, stopwatch, Pomodoro).

**Surfaces**
- [ ] `core/widget/FocusTileService.kt` + manifest `<service … android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">` with `android.service.quicksettings.action.QS_TILE`; `ACTION_START_FOCUS` / `ACTION_OPEN_GATE` handled in `MainActivity` / nav host.
- [ ] `core/widget/StartFocusWidget.kt` + receiver + `res/xml/start_focus_widget_info.xml` (2×1); `WidgetUpdater.kt` refresh on gate start/end and `LastFocusStore` change.

**UI**
- [ ] Components: `PomodoroTrack`, `FocusGoalRing` (+ previews).
- [ ] `feature/gate/GatesScreen.kt` (goal ring, Pomodoro tile + setup sheet), `feature/focus/FocusScreens.kt` (track during rounds, break screen), Settings → Focus "Daily focus goal".

**Acceptance:** vectors #96–#101; a Pomodoro round is indistinguishable from a custom run in the ledger; breaks never collapse anything; tile and widget start the last-used session and show the running one; MigrationTest 19→20; export round-trip; gate green; goldens named.

## Batch 3 — Gym: C1 hints, C2 PRs, C3 plates, C7 history

**Engine** (`engine/…/body/`)
- [ ] `ProgressionRules.kt` (`N14`, `N15`, `Muscle.isLowerBody`).
- [ ] `RecordRules.kt` (four record types, "before this session", ties are not PRs, first session flags none; PR event count for achievements).
- [ ] `PlateMath.kt` (`N16`, `N17`; exact / short / below bar; kg and lb).
- [ ] `ExerciseHistory.kt` (per-session series, weekly best for Year/All, `N18`).
- [ ] `AchievementEvaluator.kt`: `pr1/pr25/pr100` (pending Q6).
- [ ] Tests: vectors **#103–#112**.

**Data**
- [ ] `ExercisePrefEntity`, `GymSettingsEntity`; `SCHEMA_VERSION = 21`, `MIGRATION_20_21`, schema, MigrationTest 20→21; export tables += `exercise_pref`, `gym_settings`.
- [ ] `GymDao`: finished sets per exercise ordered by date.

**Domain / UI**
- [ ] `GymTracker.kt`: `hint`, `recordsBefore`, `history`, settings; PR unlocks after `finish` via `AchievementUnlocker`.
- [ ] ChartKit `ChartSpec.TrendLine` (+ preview) in `core/designsystem/charts/ChartKit.kt`.
- [ ] Components: `ProgressionHint`, `PrChip`, `PlateDiagram`.
- [ ] `feature/gym/`: hint above set 1, PR chip, finish summary records, Exercise history route, Plate calculator sheet, Gym settings.

**Acceptance:** vectors #103–#112; no ledger rows from gym except PR achievements (if Q6 = yes); history charts have descriptions; MigrationTest 20→21; export round-trip; gate green; goldens named.

## Batch 4 — Screen & health: D1 pickups, D2 step goal, D3 phone-down nights

**Engine**
- [ ] `PickupRules.kt` (pickups / screen-ons, first pickup, last use, longest stretch from first pickup).
- [ ] `BedtimeRules.kt` (window validity `N21`, wake day, allowed packages, grace `N22`, verdict KEPT / BROKEN / NO_DATA).
- [ ] `model/Enums.kt`: `QuestType.BEDTIME` (not generated, no reroll, can crit).
- [ ] Tests: vectors **#113–#121**.

**Data**
- [ ] `hunter_profile.step_goal`, `BedtimeRuleEntity`; `SCHEMA_VERSION = 22`, `MIGRATION_21_22`, schema, MigrationTest 21→22; export += `bedtime_rule`.
- [ ] `core/data/usage/UsageGateway.kt`: `events(start, end): EventsRead` (`NoAccess | Unavailable | Tracked(events)`), real + `DebugUsageGateway` + `FakeUsageGateway`; allowed packages resolver (default dialer, telecom, `AlarmClock.ACTION_SHOW_ALARMS` handlers) via `<queries>` intents only.

**Domain / work**
- [ ] `ScreenTimeUseCases.kt`: pickups overview.
- [ ] `BedtimePlanner` in `GenerateDirectiveUseCase`; `SettleBedtimeUseCase` from app foreground and `ScreenTimeWork.kt`; `completeVerified(id, USAGE)` or EXPIRED.
- [ ] Step goal setting in `SettingsUseCases.kt`.

**UI**
- [ ] Components: `StepGoalRing`, `BreathingGuide`.
- [ ] `feature/screentime/ScreenTimeScreen.kt` PICKUPS card; Today Health tile ring (`SummaryTiles.kt`); Settings → Health step goal; Settings → Focus "Phone-down nights"; Today bedtime card; Wind-down route.

**Acceptance:** vectors #113–#121; no usage access → no BEDTIME quest and no zeros on screen; a broken night deducts nothing; edits apply next night; MigrationTest 21→22; gate green; goldens named.

## Batch 5 — Quit counters: D4

**Engine**
- [ ] `QuitRules.kt` (runs, best run, in-app run from `max(start, created)`, money/time, milestones `N25`, doomscrolling day verdicts `N26`, counter limit `N24`).
- [ ] `AchievementEvaluator.kt`: `quit_7/30/90/365`.
- [ ] Tests: vectors **#122–#126**.

**Data**
- [ ] `QuitCounterEntity`, `RelapseEntity` (UNIQUE `(counter_id, game_date)` for auto rows); `SCHEMA_VERSION = 23`, `MIGRATION_22_23`, schema, MigrationTest 22→23; export += `quit_counter`, `relapse`.

**Domain / UI**
- [ ] `QuitCounterRepository` + use cases (add, archive, log restart, doomscrolling auto-relapse in `RunRolloverUseCase` from the Screen Discipline usage read; quit achievements in the same transaction).
- [ ] Components: `QuitCounterCard`, `MilestoneLadder`. Route `feature/quit/` (add `feature/quit` to CLAUDE.md's package list) per DS §9.3; copy per DS §9.2 (supportive, no medical claims).
- [ ] Q5 outcome (app lock or not).

**Acceptance:** vectors #122–#126; no XP row ever comes from a counter; back-dating never unlocks an achievement; doomscrolling with no usage access pauses instead of counting; wording review against DS §9.2; MigrationTest 22→23; gate green; goldens named.

## Batch 6 — Consistency + presentation: G3, H1

- [ ] **G3:** already built (PRD F28, GD §14.2): verify the Today/Status meter and "Don't miss twice" line still render after Batches 1–5 (new SKIPPED/EXPIRED statuses don't change day outcomes). No new code expected.
- [ ] **H1:** only if Q1 = "bring it back": `presentationMode` pref (`UiPrefs.kt` already has the field), Settings toggle, System lexicon strings, gate glow in System mode, GD §14.9 minus whatever you exclude; previews of key screens in both modes. If Q1 = "keep dropped": nothing to build; mark this batch done.

**Acceptance:** G3 vectors #50–#58 still pass; H1 per Q1; gate green.

## Batch 7 — Crystal: B3

- [ ] Engine `CrystalRules.kt` (stage from fraction, stopwatch to 90 min `N13`, tier size, colour index by `fnv1a64(subjectId) mod 6`); vector **#102**.
- [ ] Components `GateCrystal`, `CrystalGalleryGrid` (vector only, every stage/cracked/shard previewed).
- [ ] `GateDial` shows the growing crystal (reduce motion: stage steps only); gallery route from the Gates screen (`GatesScreen.kt`), query over `gate_run`.

**Acceptance:** #102; no new tables; no ledger rows; gallery TalkBack labels; gate green; goldens named.

## Batch 8 — Long arcs: F3 dungeons, F6 programs

**Engine**
- [ ] `model/Enums.kt`: `SourceType.DUNGEON`, `QuestType.DUNGEON`.
- [ ] `DungeonRules.kt` (unlock `N27`, fees `N28`, reward `N29`, stage template pick, verdict), `ProgramRules.kt` (catalog `N30`, day k, kept/rest/broken, default CONTINUE, end status).
- [ ] `AchievementEvaluator.kt`: dungeon and program achievements.
- [ ] Tests: vectors **#127–#134**.

**Data**
- [ ] `DungeonEntity`, `ProgramEntity`, `ProgramMarkEntity`; `quest_instance.dungeon_id`, `dungeon_stage`; `habit.program_id`. `SCHEMA_VERSION = 24`, `MIGRATION_23_24`, schema, MigrationTest 23→24; export += `dungeon`, `program`, `program_mark`.
- [ ] `assets/programs.json` (or a Kotlin catalog in `:engine`) validated by a test like `ContentAssetsTest`.

**Domain / UI**
- [ ] `EnterDungeonUseCase` (one transaction: row + fee + balance), stage generation, rollover verdict + reward rows (`IGNORE`).
- [ ] `StartProgramUseCase`, rollover program verdicts, `ChooseProgramBreakUseCase`, abandon.
- [ ] Components `DungeonStageTrack`, `ProgramCalendar`; routes `feature/dungeon/`, `feature/program/` (add both to CLAUDE.md's package list); Lexicon term `dungeon` already exists (Clean "Challenge").

**Acceptance:** #127–#134; fee and reward rows idempotent; failing never deducts beyond the fee; programs and campaign follow Q8; MigrationTest 23→24; export round-trip; gate green; goldens named.

## Batch 9 — Review (on Opus)

- [ ] Run the review prompt in `docs/PROMPTS.md` ("Review prompt (after every phase)") over the whole range of Batches 1–8.
- [ ] Extra checks: no new network call or host; no XP for reflection, opening the app or self-reported abstinence beyond the SELF cap; every fail-closed path (#90 negative, #115/#120 usage, #125 doomscrolling) has a test; no raw palette/hex/`dp` literals or drawing in `feature/**` (`FeatureBoundaryTest`); every game term through the Lexicon (`LexiconResourcesTest`); every `N` number approved in GD §16 (no "PROPOSED" left in code comments).
- [ ] Fix, gate, commit, then a phone test checklist per feature (F35–F51).
