# CLAUDE.md — AWAKEN (Android)

You are building **AWAKEN**, an offline-first Android app that turns real-world effort into RPG progression ("the System"). Native Kotlin + Jetpack Compose. Its headline feature is **AI Paths**: the user states a goal, an LLM (behind our own stateless proxy) breaks it into a personal quest ladder, and the deterministic engine decides every reward. Single developer, shipping an MVP fast — but the code must be production quality.

## Read before any task
- `docs/PRD_MVP.md` — what we build and, more importantly, what we do NOT build.
- `docs/GAME_DESIGN.md` — every number and formula. **Source of truth.** Never invent a number.
- `docs/ARCHITECTURE.md` — modules, packages, schema, data flow.
- `docs/DESIGN_SYSTEM.md` — tokens, components, motion. Never hard-code a colour/size in a screen.
- `docs/ENGINE_DECISIONS.md` — how `:engine` resolves GAME_DESIGN ambiguities. Check before changing a rule.
Read only the sections relevant to the current task; don't dump whole files into context.

## Stack (do not swap without asking)
Kotlin 2.x · Jetpack Compose + Material 3 (heavily themed) · Navigation Compose (type-safe routes) · Hilt (KSP) · Room (KSP) · DataStore · WorkManager · Glance (widget) · Health Connect client · kotlinx.serialization · java.time · Ktor client (OkHttp engine) · Play Integrity (Standard API) · JUnit5/Truth/Turbine for tests (`:engine` uses JUnit5; `:app` unit tests use JUnit4 because Robolectric requires it).
`minSdk 28`, `targetSdk 36`, `compileSdk 37` (compile against 37 so current AndroidX resolves). AGP 9 uses built-in Kotlin — do not add the `kotlin-android` plugin. Java 17 bytecode. Use the latest *stable* versions via `gradle/libs.versions.toml`. No alpha/beta libraries unless stated here.
Proxy (`/proxy`): TypeScript on Cloudflare Workers · zod · Wrangler · Vitest. See ARCHITECTURE §3.8.

## Modules
- `:engine` — pure Kotlin/JVM. ALL game rules (XP, levels, ranks, streaks, caps, crits, directive generation, achievements, adaptive difficulty, **Path validation and scheduling**). Zero Android imports. 100% of GAME_DESIGN §12 test vectors live here.
- `:app` — Android: data (Room, DataStore, Health Connect, Path Oracle client), DI, UI, workers, widget.
- `/proxy` — the Path Oracle: a stateless HTTPS function that holds the LLM key. Deployed separately; not a Gradle module.
- `/contracts` — JSON Schemas shared by `:engine` tests and `/proxy` (the wire contract for AI Paths).

## Hard invariants (violating any of these is a bug, not a style choice)
1. **Ledger only.** XP, stat XP, gold, items, fragments change ONLY by inserting ledger rows. No `UPDATE`/`DELETE` on ledger tables. Undo = reversing row.
2. **Idempotent writes.** Every ledger row has a unique `(sourceType, sourceId, kind)`. Applying the same event twice must be a no-op. Use `@Insert(onConflict = IGNORE)` + check the result.
3. **One transaction per game action.** Completing a quest = quest status + all ledger rows + cached balances + achievement unlocks in ONE `withTransaction {}`.
4. **Rules live in `:engine`.** ViewModels and repositories call engine functions; they never compute XP, levels, streaks, or caps themselves.
5. **AI writes words, never rewards.** The app never contains an API key and never calls an LLM provider directly; the only AI path is `PathOracleClient` → `/proxy`. Every AI response passes `:engine` `PathValidator` before it touches Room. Difficulty, verification, XP and gold for Path quests are decided by engine rules (GAME_DESIGN §6.6), never by model output. AI is optional: with AI off, offline, or the proxy down, the app still works on `assets/quests/templates.json`.
6. **Time is injected.** Never call `System.currentTimeMillis()`/`LocalDate.now()` outside a `Clock` provider. Game-day uses `dayStartHour` (default 4).
7. **Derived, not stored:** level, rank band, stat level, streak are computed from data. Caches are allowed only if rebuildable.
8. **One network destination.** `INTERNET` is allowed only for the Path Oracle (plus Google Play services for Play Integrity). No analytics, crash, ad or other SDKs that phone home; no other hosts. All network code lives in `core/data/ai`. Cleartext traffic disabled. If you think you need another endpoint, stop and ask. (Until Phase 15 adds the Path Oracle client, the app declares no INTERNET permission at all.)
9. **User text is untrusted input.** Path goal text goes to the model only inside the delimited data block defined in the proxy prompt, length-limited, and it can only influence wording within validator clamps.

## Code conventions
- Package-by-feature in `:app`: `feature/today`, `feature/quest`, `feature/trial`, `feature/paths`, `feature/gate`, `feature/status`, `feature/vault`, `feature/onboarding`, `feature/settings`; shared in `core/designsystem`, `core/data` (network code only in `core/data/ai`), `core/domain`, `core/work` (WorkManager workers).
- Every day rollover goes through `RunRolloverUseCase` (app foreground, day-start tick, `DayRolloverWorker`); it records one-shot System notices in `NoticeStore` (DataStore, UI-only).
- UDF: `ViewModel` exposes `StateFlow<UiState>` (immutable data class) + `fun onEvent(event)`. Screens are stateless composables taking state + lambdas; a thin `Route` composable wires the ViewModel.
- Naming: `camelCase` in Kotlin; Room columns `snake_case` via `@ColumnInfo`. One name per concept everywhere (`questInstanceId`, never `qid`/`quest_id` in Kotlin).
- No `!!`. No `GlobalScope`. No `runBlocking` outside tests. Coroutines: `viewModelScope`, injected `CoroutineDispatcher`s.
- Errors: repositories return `Result`/sealed outcomes; UI shows a System-styled error, never a crash.
- Strings in `strings.xml`. Content descriptions on all icons. Touch targets ≥ 48dp.
- Every screen has `@Preview`s (light content irrelevant — app is dark-only in v1) at phone + small phone sizes.
- Debug-only code lives in `app/src/debug` (Debug screen, `DebugClock` time travel) with a no-op twin in `app/src/release` (`DebugEntry`, `ClockModule`). Never reference debug classes from `src/main` except through `DebugEntry`.

## Workflow rules for you (Claude Code)
- Start each phase in **plan mode**; list files you'll touch; then implement.
- After each phase: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug` must pass, plus `./gradlew lint` with no new errors. Fix before moving on.
- Commit after each green phase: `feat(<area>): <what>` — small, reviewable commits.
- If a requirement in the docs is ambiguous or conflicts, stop and ask; don't guess numbers.
- Don't add features not in `PRD_MVP.md`. Put ideas in `docs/BACKLOG.md` instead.
- Keep this file updated if you add a module, a command, or a new invariant.

## Commands
```
./gradlew :engine:test                 # rules engine tests (fast, run constantly)
./gradlew :app:testDebugUnitTest       # app unit tests (Robolectric; includes Compose UI tests)
./gradlew :app:connectedDebugAndroidTest   # instrumented (needs device/emulator)
./gradlew :app:assembleDebug           # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:bundleRelease           # signed AAB for Play (needs keystore.properties)
./gradlew lint

cd proxy && npm test                   # proxy unit tests (mocked model)
cd proxy && npm run eval               # run the 30-goal eval set against staging; prints drop rate
cd proxy && npx wrangler deploy --env staging    # / --env production
```
