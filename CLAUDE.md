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
Kotlin 2.x · Jetpack Compose + Material 3 (heavily themed) · Navigation Compose (type-safe routes) · Hilt (KSP) · Room (KSP) · DataStore · WorkManager · Glance (widget) · Health Connect client · kotlinx.serialization · java.time · Ktor client (OkHttp engine) · CameraX + ZXing core (Diet barcode scanner: frames are decoded on the phone, never stored or sent; `CAMERA` is optional hardware) · `java.net.HttpURLConnection` for the one Open Food Facts lookup (no new networking library) · LiteRT 1.4.2 interpreter (photo food scanner; standalone, no Play services; ships off with no model, ENGINE_DECISIONS #206) · Tesseract4Android (offline reading of a photographed nutrition table; Apache 2.0; published only on JitPack, so `settings.gradle.kts` lets that repository supply that one group and nothing else; ENGINE_DECISIONS #205) · Play Integrity (Standard API) · JUnit5/Truth/Turbine for tests (`:engine` uses JUnit5; `:app` unit tests use JUnit4 because Robolectric requires it).
`minSdk 28`, `targetSdk 36`, `compileSdk 37` (compile against 37 so current AndroidX resolves). AGP 9 uses built-in Kotlin — do not add the `kotlin-android` plugin. Java 17 bytecode. Use the latest *stable* versions via `gradle/libs.versions.toml`. No alpha/beta libraries unless stated here.
Proxy (`/proxy`): TypeScript on Cloudflare Workers · zod · Wrangler · Vitest. See ARCHITECTURE §3.8.

## Modules
- `:engine` — pure Kotlin/JVM. ALL game rules (XP, levels, ranks, streaks, caps, directive generation, adaptive difficulty, **Path validation and scheduling**). Zero Android imports. 100% of GAME_DESIGN §12 test vectors live here.
- `:app` — Android: data (Room, DataStore, Health Connect, Path Oracle client), DI, UI, workers, widget.
- `:reelcounter-core` — pure Kotlin/JVM: the Reel Counter state machine (a short-video count, its time on screen, limits, stats). Not game rules: it never touches the ledger, XP or gold. Also holds `ShortVideoBlockRule` (Strict+ short-video blocking: Back once a second, Home after two). Zero Android imports.
- `:reelcounter-android` — Android library for Reel Counter: its own `AccessibilityService` (reads view ids only, never node text, with ONE exception: Study mode reads the YouTube channel name from one verified id in `service/StudyChannelText.kt`, compares it and drops it (`NoNodeTextAccessTest` enforces exactly one read); declared separately from Focus Lock because it needs content retrieval; Back/Home for blocking go through it, only with the hunter's consent, a chosen app and a verified detector), its own Room file `reelcounter.db`, settings and detector rules. No visual design and no `INTERNET`: all UI lives in `:app` (`feature/reelcounter`) and reaches the module through `ReelOverlayUi`, `ReelClock` and `ReelDayStart`. See `docs/reelcounter/INTEGRATION.md`.
- `/proxy` — the Path Oracle: a stateless HTTPS function that holds the LLM key. Deployed separately; not a Gradle module.
- `/contracts` — JSON Schemas shared by `:engine` tests and `/proxy` (the wire contract for AI Paths).

## Hard invariants (violating any of these is a bug, not a style choice)
1. **Ledger only.** XP, stat XP, gold and items change ONLY by inserting ledger rows. No `UPDATE`/`DELETE` on ledger tables. Undo = reversing row.
2. **Idempotent writes.** Every ledger row has a unique `(sourceType, sourceId, kind)`. Applying the same event twice must be a no-op. Use `@Insert(onConflict = IGNORE)` + check the result.
3. **One transaction per game action.** Completing a quest = quest status + all ledger rows + cached balances + achievement unlocks in ONE `withTransaction {}`.
4. **Rules live in `:engine`.** ViewModels and repositories call engine functions; they never compute XP, levels, streaks, or caps themselves.
5. **AI writes words, never rewards.** The app never contains an API key and never calls an LLM provider directly; the only AI path is `PathOracleClient` → `/proxy`. Every AI response passes `:engine` `PathValidator` before it touches Room. Difficulty, verification, XP and gold for Path quests are decided by engine rules (GAME_DESIGN §6.6), never by model output. AI is optional: with AI off, offline, or the proxy down, the app still works on `assets/quests/templates.json`.
6. **Time is injected.** Never call `System.currentTimeMillis()`/`LocalDate.now()` outside a `Clock` provider. Game-day uses `dayStartHour` (default 4).
7. **Derived, not stored:** level, rank band, stat level, streak are computed from data. Caches are allowed only if rebuildable.
8. **Two network destinations, both narrow.** (a) The Path Oracle: `INTERNET` for it only in debug/internal builds (the Play release build has AI Paths compiled out: no Ktor, no Play Integrity, no `ORACLE_*` BuildConfig; `AiFeature.enabled` hides every Path entry); all of that network code lives in `app/src/debug/.../core/data/ai`. (b) Open Food Facts barcode lookup (`core/data/food/OpenFoodFactsClient`, changed 2026-11-11 at the owner's request, ENGINE_DECISIONS #204): in every build, `INTERNET` is declared in the main manifest, HTTPS only (`world.openfoodfacts.org`), one GET per scan, **only after the hunter opts in** (`UiPrefs.onlineFoodLookup`, off until asked), and **only the 13-digit barcode is sent**: no account, no key, no contact details, no identifier beyond `AWAKEN/<version>`. Its reply is size-capped and judged by `:engine` `LabelPlausibility` before it is shown. Plus Google Play services for Play Integrity (debug) and the on-device Recording API for phone steps (ENGINE_DECISIONS #110). No analytics, crash, ad or other SDKs that phone home; no other hosts. Cleartext traffic disabled. If you think you need another endpoint, stop and ask. (The Oracle endpoint comes from `local.properties` via BuildConfig, never from source.)
9. **User text is untrusted input.** Path goal text goes to the model only inside the delimited data block defined in the proxy prompt, length-limited, and it can only influence wording within validator clamps.

## Feature flags
`AiFeature.enabled` (`AI_PATHS_ENABLED`) and `StrictFeature.enabled` (`STRICT_PLUS_ENABLED`, Reel Counter and Strict+) are BuildConfig flags: debug on, Play release off. A switched-off feature opens the `feature/comingsoon` page instead of its screens. `DietScanFeature.photoEnabled` (`DIET_PHOTO_SCAN_ENABLED`) is **on in debug builds only** (so the owner can try the model in `app/src/debug/assets/scanner`) and false in release until a model passes `docs/diet/SCANNER_TEST.md`; with it off, or with no model in `assets/scanner/`, the Photo button is simply not shown. The release manifest removes the accessibility service and `INTERNET`, so turning a feature on for release is its flag plus that manifest entry (docs/protect/PLAN.md 4.0).

## Code conventions
- Package-by-feature in `:app`: `feature/today`, `feature/quest`, `feature/trial`, `feature/paths`, `feature/gym`, `feature/diet`, `feature/gate`, `feature/status`, `feature/vault`, `feature/onboarding`, `feature/settings`, `feature/health`, `feature/screentime`, `feature/review`, `feature/focus`, `feature/focuslock`, `feature/reelcounter`, `feature/quit` (quit counters, GD §16.13), `feature/program` (GD §16.15), `feature/protect` (Pause, Lockdown, Strict+; GD §19); shared in `core/designsystem`, `core/data` (network code only in `core/data/ai`), `core/domain`, `core/work` (WorkManager workers), `core/scan` (on-phone readers: ZXing barcode, Tesseract label, LiteRT dish classifier; nothing they see is stored or sent), `core/data/catalog` (read-only bundled databases: `assets/nutrition/foods.db` and `packaged.db`, rebuilt by `tools/catalog` and `tools/packaged-pack`), `core/lock` (Focus Lock state, allowed apps and the overlay window helper; no service: Focus Lock is drawn by the Protect service, GD 8.2), `core/protect` (the Protect service with the Pause, Lockdown and Focus Lock watchers and the controller that starts it: usage events and package names only, an overlay window, no accessibility; GD §19 and §8.2), `core/reelcounter` (Reel Counter host wiring and overlay windows; the service itself is `:reelcounter-android`).
- Every day rollover goes through `RunRolloverUseCase` (app foreground, day-start tick, `DayRolloverWorker`); it records one-shot System notices in `NoticeStore` (DataStore, UI-only).
- UDF: `ViewModel` exposes `StateFlow<UiState>` (immutable data class) + `fun onEvent(event)`. Screens are stateless composables taking state + lambdas; a thin `Route` composable wires the ViewModel.
- Naming: `camelCase` in Kotlin; Room columns `snake_case` via `@ColumnInfo`. One name per concept everywhere (`questInstanceId`, never `qid`/`quest_id` in Kotlin).
- No `!!`. No `GlobalScope`. No `runBlocking` outside tests. Coroutines: `viewModelScope`, injected `CoroutineDispatcher`s.
- Errors: repositories return `Result`/sealed outcomes; UI shows a System-styled error, never a crash.
- Strings in `strings.xml`. Content descriptions on all icons. Touch targets ≥ 48dp.
- Every screen has `@Preview`s at phone + small phone sizes via `@PhonePreviews`, which renders both the dark and light palettes (DESIGN_SYSTEM §2.1b). Never hard-code a colour that only works on black.
- Colours: feature code uses only the semantic tokens on `AwakenTheme.colors` (`surface`, `textPrimary`, `accent`, `critical`, `reward`, …, DESIGN_SYSTEM §8.4). Raw palette names (`AwakenTheme.palette`, `AwakenPalette`) are for `core/designsystem` and the widget only.
- Feature screens do layout only: charts through ChartKit (`core/designsystem/charts`, `Chart(ChartSpec)`), any drawing or styling (`Canvas`, `background`, `border`, `clip`) inside a `core/designsystem` component, and every word through the Lexicon (`lexString`/`lexPlural`, `LexResources`; notifications via `LexiconSource`). Copy is plain, second person, sentence case; there is a single set of strings and `LexiconResourcesTest` rejects game jargon. `FeatureBoundaryTest` and `LexiconResourcesTest` enforce this.
- Layering: each screen is a `*Route` (or `*Entry` / `*Host`) that wires the ViewModel and a stateless `*Screen(state, onEvent)` with `@PhonePreviews`. `hiltViewModel()` and `collectAsStateWithLifecycle()` appear only in the wiring function. `ScreenLayeringTest` enforces it; `TokenSourceTest` keeps hex colours in `core/designsystem/theme/Color.kt`.
- Debug-only code lives in `app/src/debug` (Debug screen, `DebugClock` time travel) with a no-op twin in `app/src/release` (`DebugEntry`, `ClockModule`). Never reference debug classes from `src/main` except through `DebugEntry`.

## Workflow rules for you (Claude Code)
- Start each phase in **plan mode**; list files you'll touch; then implement.
- After each phase: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug` must pass, plus `./gradlew lint` with no new errors and `./gradlew :app:verifyRoborazziDebug` with no unexpected diffs. Fix before moving on.
- Screenshot goldens (`app/src/test/screenshots/`, every `@Preview` at half scale) are re-recorded only on purpose, with the changed screens named in the commit. They are rendered on this Windows machine; another OS may differ by font rasterisation. Some golden file names exceed Windows MAX_PATH: run `git config core.longpaths true` once per clone.
- Commit after each green phase: `feat(<area>): <what>` — small, reviewable commits.
- If a requirement in the docs is ambiguous or conflicts, stop and ask; don't guess numbers.
- Don't add features not in `PRD_MVP.md`. Put ideas in `docs/BACKLOG.md` instead.
- Keep this file updated if you add a module, a command, or a new invariant.

## Commands
```
./gradlew :engine:test                 # rules engine tests (fast, run constantly)
./gradlew :app:testDebugUnitTest       # app unit tests (Robolectric; includes Compose UI tests)
./gradlew :app:recordRoborazziDebug     # re-record golden screenshots of every @Preview (deliberate visual changes only)
./gradlew :app:verifyRoborazziDebug     # compare against the committed goldens
node tools/catalog/build-catalog.mjs <sr_dir> <fndds_dir> <cofid_dir>   # rebuild assets/nutrition/foods.db (see tools/catalog/README.md)
./gradlew :app:connectedDebugAndroidTest   # instrumented (needs device/emulator). WARNING: it UNINSTALLS the app afterwards, which deletes its data. On a phone with data you want, do instead:
#   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
#   adb install -r -t app/build/outputs/apk/debug/app-debug.apk && adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
#   adb shell am instrument -w -e class <test class> com.assismohanty.awaken.test/androidx.test.runner.AndroidJUnitRunner
#   adb uninstall com.assismohanty.awaken.test        # the test package only
./gradlew :app:assembleDebug           # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:bundleRelease           # signed AAB for Play (needs keystore.properties)
./gradlew lint

cd proxy && npm test                   # proxy unit tests (mocked model)
cd proxy && npm run eval               # run the 30-goal eval set against staging; prints drop rate
cd proxy && npx wrangler deploy --env staging    # / --env production
```
