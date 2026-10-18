# CLAUDE.md — AWAKEN (Android)

You are building **AWAKEN**, an offline-first Android app that turns real-world effort into RPG progression ("the System"). Native Kotlin + Jetpack Compose. Single developer, shipping an MVP fast — but the code must be production quality.

## Read before any task
- `docs/PRD_MVP.md` — what we build and, more importantly, what we do NOT build.
- `docs/GAME_DESIGN.md` — every number and formula. **Source of truth.** Never invent a number.
- `docs/ARCHITECTURE.md` — modules, packages, schema, data flow.
- `docs/DESIGN_SYSTEM.md` — tokens, components, motion. Never hard-code a colour/size in a screen.
- `docs/ENGINE_DECISIONS.md` — how `:engine` resolves GAME_DESIGN ambiguities. Check before changing a rule.
Read only the sections relevant to the current task; don't dump whole files into context.

## Stack (do not swap without asking)
Kotlin 2.x · Jetpack Compose + Material 3 (heavily themed) · Navigation Compose (type-safe routes) · Hilt (KSP) · Room (KSP) · DataStore · WorkManager · Glance (widget) · Health Connect client · kotlinx.serialization · java.time · JUnit5/Truth/Turbine for tests (`:engine` uses JUnit5; `:app` unit tests use JUnit4 because Robolectric requires it).
`minSdk 28`, `targetSdk 36`, `compileSdk 37` (compile against 37 so current AndroidX resolves). AGP 9 uses built-in Kotlin — do not add the `kotlin-android` plugin. Java 17 bytecode. Use the latest *stable* versions via `gradle/libs.versions.toml`. No alpha/beta libraries unless stated here.

## Modules
- `:engine` — pure Kotlin/JVM. ALL game rules (XP, levels, ranks, streaks, caps, crits, directive generation, achievements, adaptive difficulty). Zero Android imports. 100% of GAME_DESIGN §12 test vectors live here.
- `:app` — Android: data (Room, DataStore, Health Connect), DI, UI, workers, widget.

## Hard invariants (violating any of these is a bug, not a style choice)
1. **Ledger only.** XP, stat XP, gold, items, fragments change ONLY by inserting ledger rows. No `UPDATE`/`DELETE` on ledger tables. Undo = reversing row.
2. **Idempotent writes.** Every ledger row has a unique `(sourceType, sourceId, kind)`. Applying the same event twice must be a no-op. Use `@Insert(onConflict = IGNORE)` + check the result.
3. **One transaction per game action.** Completing a quest = quest status + all ledger rows + cached balances + achievement unlocks in ONE `withTransaction {}`.
4. **Rules live in `:engine`.** ViewModels and repositories call engine functions; they never compute XP, levels, streaks, or caps themselves.
5. **No AI/LLM calls and no API keys in the app.** Quest content comes from `assets/quests/templates.json`.
6. **Time is injected.** Never call `System.currentTimeMillis()`/`LocalDate.now()` outside a `Clock` provider. Game-day uses `dayStartHour` (default 4).
7. **Derived, not stored:** level, rank band, stat level, streak are computed from data. Caches are allowed only if rebuildable.
8. **No network permission for v1** except what Health Connect needs (none). If you think you need INTERNET, stop and ask.

## Code conventions
- Package-by-feature in `:app`: `feature/today`, `feature/quest`, `feature/gate`, `feature/status`, `feature/vault`, `feature/onboarding`, `feature/settings`; shared in `core/designsystem`, `core/data`, `core/domain`.
- UDF: `ViewModel` exposes `StateFlow<UiState>` (immutable data class) + `fun onEvent(event)`. Screens are stateless composables taking state + lambdas; a thin `Route` composable wires the ViewModel.
- Naming: `camelCase` in Kotlin; Room columns `snake_case` via `@ColumnInfo`. One name per concept everywhere (`questInstanceId`, never `qid`/`quest_id` in Kotlin).
- No `!!`. No `GlobalScope`. No `runBlocking` outside tests. Coroutines: `viewModelScope`, injected `CoroutineDispatcher`s.
- Errors: repositories return `Result`/sealed outcomes; UI shows a System-styled error, never a crash.
- Strings in `strings.xml`. Content descriptions on all icons. Touch targets ≥ 48dp.
- Every screen has `@Preview`s (light content irrelevant — app is dark-only in v1) at phone + small phone sizes.

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
./gradlew :app:testDebugUnitTest       # app unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented (needs device/emulator)
./gradlew :app:assembleDebug           # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:bundleRelease           # signed AAB for Play (needs keystore.properties)
./gradlew lint
```
