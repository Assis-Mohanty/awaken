# Reel Counter — PLAN (v2)

> **Status: historical.** Reel Counter now lives inside the AWAKEN app. Module names, file layout and the UI sections
> below (§2, §7, §11 phases 3–5) are superseded by `INTEGRATION.md`; the counting engine, detection, data and test
> sections (§3–§6, §10) still describe the code.

Branch `feature/reel-counter`, worktree `../awaken-reelcounter` (branched from AWAKEN `132a0d3`).
Everything lives under `/reelcounter/`. **Nothing outside `/reelcounter/` is ever edited.** Clean room: built
from SPEC.md and public Android docs only.

> **For the implementer (Part B).** This plan is the contract. Use the signatures, names, numbers, file
> names and test names exactly as written. If something here is ambiguous, contradictory or doesn't compile
> as specified, do **not** redesign it: write the question in `reelcounter/PLAN_QUESTIONS.md`, stop, and
> tell the user. Tick the checkboxes in §11 as you go (edit this file).
>
> v2 replaces the v1 outline (commit `c534a04`). The WIP commit `477ac2a` already contains the Gradle skeleton,
> `MachineHarness.kt` and `CounterMachineCountingTest.kt`. Both are kept: v2's API matches them, except
> `MachineHarness` gains one constructor parameter (§6.0).

Conventions (mirroring AWAKEN so the modules can merge later):
- minSdk 28, targetSdk 36, compileSdk 37, Java 17 bytecode, AGP 9 built-in Kotlin. Do **not** add the `kotlin-android` plugin.
- Hilt (KSP), Room (KSP), DataStore Preferences, Compose Material 3, Navigation Compose with type-safe routes, kotlinx.serialization, `java.time`, Glance, WorkManager.
- No `!!`, no `GlobalScope`, no `runBlocking` outside tests. Coroutine dispatchers are injected.
- Every user-visible string goes in `strings.xml`. Every icon has a content description. Touch targets are ≥ 48dp.
- **Every Android resource name is prefixed `rc_`.** `resourcePrefix = "rc_"` is set in `:android`, so names don't collide inside AWAKEN.
- `:core` tests use JUnit5 + Truth. `:android` tests use JUnit4 + Robolectric + Truth + Turbine + kotlinx-coroutines-test.
- Time comes only from the injected `ReelClock` (§3.1). Never call `Instant.now()`, `System.currentTimeMillis()` or `LocalDate.now()` outside `SystemReelClock`.
- **Never read node text.** No call to `getText`, `text`, `getContentDescription`, `contentDescription`, `getHintText`, `getStateDescription`, `getTooltipText` or `getPaneTitle` on any `AccessibilityNodeInfo`. Never call `AccessibilityEvent.getText`/`text`, `getContentDescription`/`contentDescription` or `getBeforeText`. Enforced by `NoNodeTextAccessTest` (§10.2).
- No `INTERNET` permission and no network library. Enforced by `NoNetworkTest` (§10.2).

---

## 1. Decisions (final, no confirmation needed unless the user vetoes)

| # | Decision | Why |
|---|---|---|
| D1 | The bubble, tap panel, nudge and debug chip are **`TYPE_ACCESSIBILITY_OVERLAY`** windows added by the AccessibilityService. The app never requests `SYSTEM_ALERT_WINDOW`. SPEC 9's "overlay permission" step stays in the setup flow as an **automatic ✓ step** ("Overlay: handled by the accessibility service, no extra permission"). The Status screen shows the same row as ✓. | One less permission, works on MIUI, and the bubble can't outlive the service. |
| D2 | `detectors.json` ships placeholder ids marked `"verified": false` (UNVERIFIED) plus a **structural fallback** (§5.3), which is on by default for unverified apps. | Clean room. Counting roughly works before the phone dumps arrive. |
| D3 | `reel_event` also stores `day` and `session_id`. `daily_total` also stores `snoozes`. | Replay idempotency via `UNIQUE(pkg, session_id, item_index)`. The snooze cap survives a restart. |
| D4 | One count = one settled stop on an index higher than any already counted in this session. A fling that skips videos counts +1 per settled stop. | A skipped video was never really in view. |
| D5 | `pkg` columns store the app's **primary package** (`ShortVideoApp.primaryPackage`), so TikTok's two packages count as one app. | One app, one row. |
| D6 | The "equivalent to X minutes" line uses **measured seconds**: `X = seconds / 60` (floor). | We measure time, so there's no need to guess an average reel length. |
| D7 | "Days under limit" streak: a day is *within limit* when, for every app that has a limit, `count ≤ limit`. The current limits apply to every past day (limit history isn't stored). With no limits set, there's no streak. | Simple and explainable. A snoozed day is over the limit. |
| D8 | Battery optimisation uses `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no extra permission), not `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. | Play policy restricts the direct-request permission. Keeps AWAKEN merge-safe. |
| D9 | App identity in the UI is a **letter monogram** (I, Y, T, F, S) in a neutral circle, not the installed app icon. | Original, no brand assets, no package-visibility `<queries>`. |
| D10 | Font is the system default (`FontFamily.Default`). Numerals use `fontFeatureSettings = "tnum"`. | No font assets to ship. AWAKEN swaps its own typography on merge. |

---

## 2. Project layout and every file

Root of the standalone project is `reelcounter/`. Paths below are relative to it. `…` = `com/assismohanty/awaken/reelcounter`.

### 2.1 Build files (exist from WIP unless marked NEW/EDIT)
| File | Purpose |
|---|---|
| `settings.gradle.kts` | includes `:core`, `:android`, `:demo` (exists) |
| `build.gradle.kts` | plugin aliases `apply false` (exists) |
| `gradle.properties` | JVM args, config cache (exists) |
| `gradle/libs.versions.toml` | EDIT in Phase 2: add exactly two library entries copied from the AWAKEN root catalog: `androidx-room-testing` (`androidx.room:room-testing`, `version.ref = "room"`) and `androidx-work-testing` (`androidx.work:work-testing`, `version.ref = "work"`). No other additions, no version changes. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | wrapper, Gradle 9.6.0 (exists) |
| `local.properties` | NOT committed. Must contain only `sdk.dir=…`. Phase 1 step 0: delete every other line (the WIP copy contains AWAKEN oracle keys that this project must not hold). |
| `.gitignore` | exists |
| `core/build.gradle.kts` | Kotlin JVM + serialization, JUnit5 (exists) |
| `android/build.gradle.kts` | EDIT Phase 2: add plugins `kotlin-serialization`, `ksp`, `hilt`, `room`; `resourcePrefix = "rc_"`; room `schemaDirectory("$projectDir/schemas")`; deps listed in §11 Phase 2 |
| `demo/build.gradle.kts` | EDIT Phase 2: add `ksp`, `hilt`; deps `hilt-android`, `hilt-compiler` (ksp) |
| `android/schemas/…/1.json` | Room exported schema (generated, committed) |
| `branding/rc_icon_512.png`, `branding/rc_icon.svg`, `branding/rc_wordmark.svg` | Phase 3 branding exports |
| `SPEC.md`, `PLAN.md`, `PLAN_QUESTIONS.md` (only if needed), `README.md` (Phase 5), `INTEGRATION.md` (Part C) | docs |

### 2.2 `:core` (`core/src/main/kotlin/…/core/`), pure Kotlin, package `com.assismohanty.awaken.reelcounter.core`
| File | Purpose |
|---|---|
| `ShortVideoApp.kt` | enum of the 5 apps, packages, primary package, lookup |
| `time/ReelClock.kt` | `ReelClock` interface (`now()`, `zone()`) + `FixedReelClock` for tests/previews |
| `time/GameDay.kt` | game-day key and next boundary from `Instant`, `ZoneId`, `dayStart` |
| `model/Bounds.kt` | screen rectangle + coverage maths |
| `model/NodeSnapshot.kt` | text-free node model (key, id, class, bounds, flags, children) |
| `model/NodeSource.kt` | `NodeSource` interface + `TreeNodeSource` (in-memory impl used by tests and inspector) |
| `model/ScrollSignal.kt` | abstract `TYPE_VIEW_SCROLLED` payload |
| `detect/DetectorConfig.kt` | `@Serializable` schema of `detectors.json` |
| `detect/DetectorConfigParser.kt` | parse + validate → `ParseResult` |
| `detect/ScreenDetector.kt` | `NodeSource` + config → `Detection` (ids first, structural fallback second) |
| `detect/ItemPositionTracker.kt` | per-app item index from pager/scroll/child-swap signals |
| `detect/RawSignal.kt` | abstract adapter input (window/content/scroll/probe/screen on-off) |
| `detect/ScreenTracker.kt` | `RawSignal` → `CounterEvent`s, content-change throttle, leave detection |
| `count/CounterEvent.kt` | sealed input events |
| `count/CounterEffect.kt` | sealed output effects |
| `count/CounterConfig.kt` | timing/limit constants |
| `count/CounterState.kt` | immutable state + `AppDayState`, `Session`, `Pending`, `ActiveScreen`, `RestoredSession` |
| `count/CounterMachine.kt` | `reduce(state, event): Transition` |
| `limit/LimitPolicy.kt` | allowance = limit + snoozes × 5; limit bounds |
| `stats/DayTotal.kt` | `DayTotal`, `DayBar` |
| `stats/StatsCalculator.kt` | series, within-limit streak, average, minutes |
| `export/ExportDocument.kt` | `@Serializable` export DTOs + `ExportJson.encode/decode` |

`core/src/test/kotlin/…/core/` (JUnit5):
| File | Purpose |
|---|---|
| `count/MachineHarness.kt` | exists; EDIT: add `restored` ctor param (§6.0) |
| `count/CounterMachineCountingTest.kt` | exists (tests C1–C16) |
| `count/CounterMachineSessionTest.kt` | S1–S14 |
| `count/CounterMachineTimeTest.kt` | T1–T8 |
| `count/CounterMachineRolloverTest.kt` | R1–R9 |
| `count/CounterMachineLimitTest.kt` | L1–L13 |
| `count/CounterMachineRestartTest.kt` | X1–X4 |
| `time/GameDayTest.kt` | G1–G8 |
| `detect/ItemPositionTrackerTest.kt` | P1–P11 |
| `detect/ScreenDetectorTest.kt` | D1–D11 |
| `detect/DetectorConfigParserTest.kt` | J1–J10 |
| `detect/ScreenTrackerTest.kt` | K1–K16 |
| `detect/NodeFixtures.kt` | builders for `NodeSnapshot` trees (`node(...)`, `screen1080x2400`) |
| `stats/StatsCalculatorTest.kt` | M1–M12 |
| `export/ExportDocumentTest.kt` | E1–E3 |
| `ShortVideoAppTest.kt` | A1–A4 |
| `CoreIsAndroidFreeTest.kt` | scans `core/src/main` for `import android.` / `import androidx.` → must be none |

### 2.3 `:android` (Android library, namespace `com.assismohanty.awaken.reelcounter`)
`android/src/main/`:
| File | Purpose |
|---|---|
| `AndroidManifest.xml` | accessibility service, widget receiver, no INTERNET |
| `assets/rc_detectors.json` | built-in detector rules (§5.2) |
| `res/xml/rc_accessibility_service.xml` | service config (§5.4) |
| `res/xml/rc_widget_info.xml` | Glance widget provider info |
| `res/drawable/rc_ic_launcher_foreground.xml` | icon foreground vector (§8) |
| `res/drawable/rc_ic_launcher_background.xml` | icon background vector (solid accent) |
| `res/drawable/rc_ic_launcher_monochrome.xml` | themed-icon layer (same paths as foreground) |
| `res/drawable/rc_mark.xml` | the mark alone (§8), `fillColor="#FFFFFFFF"`, tinted at the use site (wordmark, nudge) |
| `res/drawable/rc_ic_check.xml`, `rc_ic_cross.xml`, `rc_ic_warning.xml`, `rc_ic_chevron.xml`, `rc_ic_back.xml`, `rc_ic_settings.xml`, `rc_ic_limit.xml`, `rc_ic_status.xml` | 24dp line icons, 2dp stroke, round caps, drawn by hand |
| `res/mipmap-anydpi/rc_ic_launcher.xml`, `rc_ic_launcher_round.xml` | adaptive icon (background + foreground + monochrome) |
| `res/values/rc_strings.xml` | all strings + plurals (§7.6) |
| `res/values/rc_colors.xml` | only the two icon colours + widget fallback colours |
| `java/…/ReelCounterEntry.kt` | public API for hosts: `ReelCounterNavGraph` composable + `ReelCounterRoutes` |
| `java/…/di/ReelCounterModule.kt` | Hilt: clock, dispatchers, DB, DAO, DataStore, json |
| `java/…/di/Dispatchers.kt` | `@IoDispatcher`, `@CounterDispatcher` qualifiers |
| `java/…/time/SystemReelClock.kt` | the only real clock |
| `java/…/service/ReelAccessibilityService.kt` | `@AndroidEntryPoint` service: lifecycle, events → adapter → runner, receivers |
| `java/…/service/EventAdapter.kt` | `AccessibilityEvent` → `RawSignal` (thin, no text) |
| `java/…/service/AccessibilityNodeSource.kt` | `NodeSource` over live windows (ids/class/bounds/flags only); also holds `internal object NodeSnapshotMapping { fun map(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): NodeSnapshot }` (budget[0] = nodes left) |
| `java/…/service/CounterRunner.kt` | `@Singleton`: owns `CounterState`, serial dispatcher, probe loop, day guard, applies effects |
| `java/…/service/EffectApplier.kt` | maps `CounterEffect` → repository / overlay / widget calls; also declares the seams `interface OverlaySink { fun showBubble(app: ShortVideoApp); fun hideAll(); fun showNudge(app: ShortVideoApp, countToday: Int, snoozesLeft: Int); fun hideNudge() }` (implemented by `OverlayController`) and `fun interface WidgetSink { fun request() }` (implemented by `WidgetRefresher`), bound in `ReelCounterModule` |
| `java/…/service/ServiceStatus.kt` | `@Singleton`: `connected: StateFlow<Boolean>`, heartbeat writer |
| `java/…/service/ScreenStateReceiver.kt` | `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` → runner |
| `java/…/overlay/OverlayWindowHost.kt` | adds/removes one ComposeView window with lifecycle + saved-state owners |
| `java/…/overlay/OverlayController.kt` | `@Singleton`: bubble/panel/nudge/debug-chip windows, positions, hidden sessions |
| `java/…/overlay/BubbleOverlay.kt` | bubble composable wiring (drag, tap, long-press) |
| `java/…/overlay/TodayPanelOverlay.kt` | tap panel wiring |
| `java/…/overlay/NudgeOverlay.kt` | full-screen nudge wiring |
| `java/…/overlay/BubblePosition.kt` | edge + yFraction model and snap maths (pure, unit-tested) |
| `java/…/data/db/ReelDatabase.kt` | Room DB v1 |
| `java/…/data/db/ReelEventEntity.kt` | `reel_event` |
| `java/…/data/db/DailyTotalEntity.kt` | `daily_total` |
| `java/…/data/db/ReelDao.kt` | all queries (§6.2) |
| `java/…/data/ReelRepository.kt` | transactions, restore data, flows, export |
| `java/…/data/SettingsStore.kt` | DataStore keys (§6.3) |
| `java/…/data/DetectorRepository.kt` | loads asset + `filesDir/rc_detectors_override.json`, exposes `StateFlow<DetectorLoad>` |
| `java/…/data/ExportWriter.kt` | builds `ExportDocument`, writes to a SAF `Uri` |
| `java/…/designsystem/ReelColors.kt` | token data class + light/dark instances (§7.1) |
| `java/…/designsystem/ReelTypography.kt` | type scale (§7.2) |
| `java/…/designsystem/ReelDimens.kt` | spacing, radii, sizes (§7.3) |
| `java/…/designsystem/ReelTheme.kt` | `ReelTheme {}` + `ReelTheme.colors/type/dimens` accessors |
| `java/…/designsystem/ReelPreviews.kt` | `@ReelPreviews` multi-preview annotation |
| `java/…/designsystem/PreviewData.kt` | sample data for every preview state |
| `java/…/designsystem/components/ReelCard.kt` | rounded surface card |
| `java/…/designsystem/components/HeroNumber.kt` | very large tabular numeral + caption |
| `java/…/designsystem/components/ReelButton.kt` | Primary / Secondary / Quiet buttons |
| `java/…/designsystem/components/ProgressRing.kt` | ring used by bubble |
| `java/…/designsystem/components/CounterBubble.kt` | 44dp bubble visual (stateless) |
| `java/…/designsystem/components/TodayPanel.kt` | per-app counts + time list (stateless) |
| `java/…/designsystem/components/NudgeContent.kt` | full-screen nudge layout (stateless) |
| `java/…/designsystem/components/BarChart.kt` | 7/30-day bars (Canvas) |
| `java/…/designsystem/components/SegmentedToggle.kt` | 7D / 30D toggle |
| `java/…/designsystem/components/AppMonogram.kt` | letter circle per app |
| `java/…/designsystem/components/AppRow.kt` | monogram + name + count + minutes + optional limit bar |
| `java/…/designsystem/components/LimitBar.kt` | thin linear progress to limit |
| `java/…/designsystem/components/StatusRow.kt` | ✓/✗/⚠ row with fix button |
| `java/…/designsystem/components/StepDots.kt` | setup progress dots |
| `java/…/designsystem/components/Wordmark.kt` | mark + "Reel Counter" |
| `java/…/designsystem/components/LimitStepper.kt` | switch + − value + |
| `java/…/designsystem/components/SettingRow.kt` | label/value/chevron row |
| `java/…/designsystem/components/ReelTopBar.kt` | title + back |
| `java/…/designsystem/components/DebugChip.kt` | inspector chip visual |
| `java/…/ui/ReelCounterNavGraph.kt` | NavHost: Setup / Stats / Status / Limits / Settings / Inspector |
| `java/…/ui/ReelRoutes.kt` | `@Serializable` route objects |
| `java/…/ui/stats/StatsScreen.kt`, `StatsViewModel.kt`, `StatsUiState.kt` | Feature 6 |
| `java/…/ui/limits/LimitsScreen.kt`, `LimitsViewModel.kt`, `LimitsUiState.kt` | Feature 5 settings |
| `java/…/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsUiState.kt` | day start, bubble, export |
| `java/…/ui/setup/SetupScreen.kt`, `SetupViewModel.kt`, `SetupUiState.kt` | Feature 9 |
| `java/…/ui/status/StatusScreen.kt`, `StatusViewModel.kt`, `StatusUiState.kt` | Feature 10 |
| `java/…/ui/today/TodayDebugScreen.kt` | Phase 2 only plain-text counts screen, DELETED in Phase 5 |
| `java/…/system/PermissionChecker.kt` | accessibility enabled?, battery exempt?, OEM |
| `java/…/system/SystemIntents.kt` | accessibility settings deep link, battery settings, app details |
| `java/…/system/OemAutostart.kt` | OEM detection + ordered intent candidates (§7.5) |
| `java/…/widget/ReelWidget.kt` | `GlanceAppWidget` |
| `java/…/widget/ReelWidgetReceiver.kt` | `GlanceAppWidgetReceiver` |
| `java/…/widget/WidgetRefresher.kt` | throttled `updateAll` |
| `java/…/widget/DayBoundaryWorker.kt` | refresh widget at the day boundary, re-schedules itself |
| `java/…/widget/WidgetEntryPoint.kt` | Hilt `@EntryPoint` for Glance/worker |
| `java/…/inspector/InspectorHooks.kt` | interface used by main code |
| `src/debug/java/…/inspector/DebugInspectorHooks.kt` | real impl: scroll log, dump writer |
| `src/debug/java/…/inspector/NodeTreeDumper.kt` | `NodeSource.tree()` → text (ids only) |
| `src/debug/java/…/inspector/InspectorScreen.kt` | toggle, file list, viewer, override editor |
| `src/debug/java/…/inspector/InspectorModule.kt` | Hilt binding debug impl + `inspectorScreen` provider |
| `src/release/java/…/inspector/NoOpInspectorHooks.kt` | no-op |
| `src/release/java/…/inspector/InspectorModule.kt` | Hilt binding no-op, `inspectorScreen = null` |

`android/src/test/java/…/` (JUnit4 + Robolectric): see §10.2 for names.

### 2.4 `:demo`
| File | Purpose |
|---|---|
| `demo/src/main/AndroidManifest.xml` | EDIT: `android:name=".DemoApp"`, icon `@mipmap/rc_ic_launcher`, roundIcon, label |
| `demo/src/main/java/…/demo/DemoApp.kt` | `@HiltAndroidApp` + WorkManager default init |
| `demo/src/main/java/…/demo/MainActivity.kt` | EDIT: `@AndroidEntryPoint`, `enableEdgeToEdge()`, `ReelTheme { ReelCounterNavGraph() }` |
| `demo/src/main/res/values/strings.xml` | `demo_app_name` = "Reel Counter" (exists) |

---

## 3. `:core` public API (exact signatures)

Package root `com.assismohanty.awaken.reelcounter.core`. All classes are immutable. Nothing throws on bad input except the parser, which returns `ParseResult.Error`.

### 3.1 Basics
```kotlin
// ShortVideoApp.kt
@Serializable
enum class ShortVideoApp(val packages: List<String>, val monogram: Char) {
    INSTAGRAM(listOf("com.instagram.android"), 'I'),
    YOUTUBE(listOf("com.google.android.youtube"), 'Y'),
    TIKTOK(listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), 'T'),
    FACEBOOK(listOf("com.facebook.katana"), 'F'),
    SNAPCHAT(listOf("com.snapchat.android"), 'S');

    val primaryPackage: String get() = packages.first()

    companion object {
        val allPackages: Set<String>                         // all 6 packages
        fun fromPackage(pkg: String?): ShortVideoApp?        // null for unknown/null
    }
}

// time/ReelClock.kt
interface ReelClock {
    fun now(): Instant
    fun zone(): ZoneId
}
class FixedReelClock(var instant: Instant, var zoneId: ZoneId = ZoneOffset.UTC) : ReelClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advanceMillis(ms: Long) { instant = instant.plusMillis(ms) }
}

// time/GameDay.kt
class GameDay(private val zone: ZoneId, private val dayStart: LocalTime = DEFAULT_DAY_START) {
    /** Local date-time of [at]; if its time-of-day < dayStart, the previous date; else its date. */
    fun dayOf(at: Instant): LocalDate
    /** First instant strictly after [after] at which dayOf() changes. Uses ZonedDateTime.of(date, dayStart, zone)
     *  (gaps resolve forward by the gap length, overlaps resolve to the earlier offset). */
    fun nextBoundary(after: Instant): Instant
    companion object { val DEFAULT_DAY_START: LocalTime = LocalTime.of(4, 0) }
}
```

### 3.2 Nodes and detection
```kotlin
// model/Bounds.kt
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height
    /** intersection area / screen.area, 0.0 when screen.area == 0. */
    fun coverage(screen: Bounds): Double
}

// model/NodeSnapshot.kt  — deliberately has NO text/contentDescription field
data class NodeSnapshot(
    val key: String,              // opaque identity of the live node (Android: Integer.toHexString(node.hashCode()))
    val viewId: String?,          // "pkg:id/name" or null
    val className: String?,
    val bounds: Bounds,
    val scrollable: Boolean,
    val visibleToUser: Boolean,
    val children: List<NodeSnapshot> = emptyList(),
)

// model/NodeSource.kt
interface NodeSource {
    val packageName: String?      // package of the window(s) searched; null if no window
    val screen: Bounds            // full display bounds
    /** Nodes with this view id, each with its DIRECT children only (depth 1). Empty if none. */
    fun findById(viewId: String): List<NodeSnapshot>
    /** Whole tree, pre-order, capped: depth ≤ maxDepth, total nodes ≤ maxNodes. Null if no window. */
    fun tree(maxDepth: Int = 40, maxNodes: Int = 400): NodeSnapshot?
}
class TreeNodeSource(
    private val root: NodeSnapshot?, override val packageName: String?, override val screen: Bounds,
) : NodeSource   // findById: pre-order search of root; returns copies whose children are kept at depth 1

// model/ScrollSignal.kt
data class ScrollSignal(
    val sourceViewId: String?,
    val fromIndex: Int,           // -1 when not reported
    val toIndex: Int,             // -1 when not reported
    val itemCount: Int,           // -1 when not reported
    val scrollDeltaY: Int,        // 0 when not reported
)

// detect/DetectorConfig.kt
@Serializable data class DetectorConfig(val version: Int, val apps: List<AppDetector>)
@Serializable data class AppDetector(
    val app: ShortVideoApp,
    val verified: Boolean,
    val structuralFallback: Boolean? = null,   // null → !verified
    val screens: List<ScreenRule>,
    val note: String = "",                     // free text for humans, ignored by code
) { fun usesFallback(): Boolean = structuralFallback ?: !verified }
@Serializable data class ScreenRule(
    val containerIds: List<String>,
    val excludeIds: List<String> = emptyList(),
    val signal: ItemSignal,
    val signalSourceIds: List<String> = emptyList(),   // empty = any scroll source in the package
    val minCoverage: Double = 0.85,
)
@Serializable enum class ItemSignal { SCROLL_INDEX, PAGER_PAGE, CHILD_SWAP }

// detect/DetectorConfigParser.kt
sealed interface ParseResult {
    data class Ok(val config: DetectorConfig) : ParseResult
    data class Error(val message: String) : ParseResult
}
object DetectorConfigParser {
    const val SUPPORTED_VERSION = 1
    val VIEW_ID_REGEX = Regex("^[A-Za-z0-9._]+:id/[A-Za-z0-9_.]+$")
    fun parse(json: String): ParseResult   // Json { ignoreUnknownKeys = true }; validation rules in §5.1
}

// detect/ScreenDetector.kt
sealed interface Detection {
    data object NotShortVideo : Detection
    data class ShortVideo(
        val app: ShortVideoApp,
        val rule: ScreenRule,
        val viaFallback: Boolean,
        val childSignature: String?,   // key of the visible full-screen child of the container, if any
    ) : Detection
}
class ScreenDetector(private val config: DetectorConfig) {
    fun detect(packageName: String?, nodes: NodeSource?): Detection   // algorithm §5.3
    companion object { const val FALLBACK_COVERAGE = 0.85 }
}

// detect/ItemPositionTracker.kt   — one per app, lives as long as the ScreenTracker
class ItemPositionTracker(private val childSwapWindowMillis: Long = 1_000) {
    val index: Int                                   // current index, starts at 0
    fun setRule(rule: ScreenRule)                    // rule of the current visit (signal type + source filter)
    fun onScroll(scroll: ScrollSignal, at: Instant): Int?          // new index, or null if unchanged/ignored
    fun onChildSignature(signature: String?, at: Instant): Int?    // CHILD_SWAP only; new index or null
}

// detect/RawSignal.kt
sealed interface RawSignal {
    val at: Instant
    data class WindowStateChanged(val packageName: String, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class WindowContentChanged(val packageName: String, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class ViewScrolled(val packageName: String, val scroll: ScrollSignal, val nodes: NodeSource?, override val at: Instant) : RawSignal
    /** Sent every 1 000 ms while a short-video screen is active, and once at a requested recheck time. */
    data class Probe(val foregroundPackage: String?, val nodes: NodeSource?, override val at: Instant) : RawSignal
    data class ScreenOff(override val at: Instant) : RawSignal
    data class ScreenOn(val foregroundPackage: String?, val nodes: NodeSource?, override val at: Instant) : RawSignal
}

// detect/ScreenTracker.kt
data class TrackerConfig(val contentThrottleMillis: Long = 300, val childSwapWindowMillis: Long = 1_000)
data class TrackerOutput(val events: List<CounterEvent>, val recheckAt: Instant? = null)
class ScreenTracker(detector: ScreenDetector, private val config: TrackerConfig = TrackerConfig()) {
    val activeApp: ShortVideoApp?
    fun onSignal(signal: RawSignal): TrackerOutput   // algorithm §5.5
    fun replaceDetector(detector: ScreenDetector)    // keeps active app + trackers
}
```

### 3.3 Counting
```kotlin
// count/CounterEvent.kt
sealed interface CounterEvent {
    val at: Instant
    data class ScreenEntered(val app: ShortVideoApp, override val at: Instant, val index: Int) : CounterEvent
    data class ItemChanged(val app: ShortVideoApp, val index: Int, override val at: Instant) : CounterEvent
    data class ScreenLeft(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class Tick(override val at: Instant) : CounterEvent
    data class DayRolled(val newDay: LocalDate, override val at: Instant) : CounterEvent
    data class Snoozed(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class LimitsChanged(val limits: Map<ShortVideoApp, Int>, override val at: Instant) : CounterEvent
}

// count/CounterEffect.kt
sealed interface CounterEffect {
    data class Counted(val app: ShortVideoApp, val day: LocalDate, val sessionId: Long, val index: Int,
                       val at: Instant /* = pending.since */, val countToday: Int) : CounterEffect
    data class TimeAdded(val app: ShortVideoApp, val day: LocalDate, val seconds: Long) : CounterEffect
    data class NudgeRequested(val app: ShortVideoApp, val countToday: Int, val snoozesLeft: Int) : CounterEffect
    data class SnoozeAccepted(val app: ShortVideoApp, val day: LocalDate, val snoozesUsed: Int) : CounterEffect
    data class SnoozeRejected(val app: ShortVideoApp) : CounterEffect
    data class BubbleChanged(val app: ShortVideoApp?) : CounterEffect   // null = hide
}

// count/CounterConfig.kt
data class CounterConfig(
    val settleMillis: Long = 350,          // DEBOUNCE: an index must stay unchanged this long to count
    val resumeGraceMillis: Long = 600_000, // leave + return within 10 min = same session
    val maxTickGapMillis: Long = 5_000,    // time added per event is clamped to this (screen off / doze)
    val snoozeSize: Int = 5,
    val maxSnoozes: Int = 2,
)

// count/CounterState.kt
data class Pending(val index: Int, val since: Instant)
data class Session(
    val id: Long,                 // = epoch millis of the ScreenEntered that opened it
    val highestCounted: Int,      // -1 = nothing counted yet
    val currentIndex: Int,        // last settled index, -1 = none
    val pending: Pending?,
    val leftAt: Instant?,         // null while active
)
data class AppDayState(val count: Int = 0, val snoozesUsed: Int = 0, val carryMillis: Long = 0)
data class ActiveScreen(val app: ShortVideoApp, val lastAt: Instant)
data class RestoredSession(val sessionId: Long, val highestCounted: Int, val lastSeenAt: Instant)

data class CounterState(
    val day: LocalDate,
    val apps: Map<ShortVideoApp, AppDayState>,      // always contains all 5 apps
    val sessions: Map<ShortVideoApp, Session>,
    val active: ActiveScreen?,
    val limits: Map<ShortVideoApp, Int>,            // absent = no limit; values always ≥ 1
) {
    fun countToday(app: ShortVideoApp): Int
    fun totalToday(): Int
    fun snoozesUsed(app: ShortVideoApp): Int
    fun allowance(app: ShortVideoApp, snoozeSize: Int): Int? = LimitPolicy.allowance(limits[app], snoozesUsed(app), snoozeSize)

    companion object {
        fun initial(
            day: LocalDate,
            todayCounts: Map<ShortVideoApp, Int>,
            todaySnoozes: Map<ShortVideoApp, Int>,
            limits: Map<ShortVideoApp, Int>,
            restored: Map<ShortVideoApp, RestoredSession> = emptyMap(),
        ): CounterState
        // restored → Session(id = sessionId, highestCounted, currentIndex = highestCounted, pending = null, leftAt = lastSeenAt)
        // limits are filtered to values ≥ 1
    }
}
data class Transition(val state: CounterState, val effects: List<CounterEffect>)

// count/CounterMachine.kt
class CounterMachine(private val config: CounterConfig = CounterConfig()) {
    fun reduce(state: CounterState, event: CounterEvent): Transition   // algorithm §4
}

// limit/LimitPolicy.kt
object LimitPolicy {
    const val MIN_LIMIT = 5; const val MAX_LIMIT = 500; const val STEP = 5; const val DEFAULT_LIMIT = 50
    fun allowance(limit: Int?, snoozesUsed: Int, snoozeSize: Int): Int? = limit?.let { it + snoozesUsed * snoozeSize }
    fun clamp(limit: Int): Int = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)
}
```

### 3.4 Stats and export
```kotlin
// stats/DayTotal.kt
data class DayTotal(val day: LocalDate, val app: ShortVideoApp, val count: Int, val seconds: Long, val snoozes: Int = 0)
data class DayBar(val day: LocalDate, val total: Int, val perApp: Map<ShortVideoApp, Int>, val seconds: Long)

// stats/StatsCalculator.kt
object StatsCalculator {
    /** Exactly [days] bars, oldest first, last = today; days without rows are zero. */
    fun series(totals: List<DayTotal>, today: LocalDate, days: Int): List<DayBar>
    /** Null when limits is empty. Counts back from today while the day is within limit (D7) and ≥ firstDay.
     *  Today over limit → 0. firstDay null → 0. Days with no rows (≥ firstDay) are within limit. */
    fun withinLimitStreak(totals: List<DayTotal>, limits: Map<ShortVideoApp, Int>, today: LocalDate, firstDay: LocalDate?): Int?
    /** Round-half-up of sum(total) / number of bars whose day ≥ firstDay; 0 when none or firstDay null. */
    fun dailyAverage(series: List<DayBar>, firstDay: LocalDate?): Int
    fun minutes(seconds: Long): Long = seconds / 60
    fun totalsForDay(totals: List<DayTotal>, day: LocalDate): Map<ShortVideoApp, DayTotal>   // missing apps absent
}

// export/ExportDocument.kt
@Serializable data class ExportDocument(
    val format: String = "reelcounter-export", val version: Int = 1,
    val exportedAt: String, val zone: String, val dayStart: String,
    val limits: Map<String, Int>, val dailyTotals: List<ExportDailyTotal>, val events: List<ExportEvent>,
)
@Serializable data class ExportDailyTotal(val date: String, val app: String, val pkg: String, val count: Int, val seconds: Long, val snoozes: Int)
@Serializable data class ExportEvent(val app: String, val pkg: String, val ts: String, val day: String, val itemIndex: Int, val sessionId: Long)
object ExportJson {
    fun encode(doc: ExportDocument): String   // Json { prettyPrint = true; encodeDefaults = true }
    fun decode(json: String): ExportDocument
}
```

---

## 4. Counting state machine

### 4.1 States
Per app (`Session`): **NoSession** → **Active(settled | pending)** ⇄ **Left(leftAt)** → (after grace) **Expired**, which behaves like NoSession.
Globally there is at most one `active` app. Per day (`AppDayState`): count, snoozes, sub-second time carry.

### 4.2 `reduce(s, e)`: exact algorithm (always in this order)
1. **Day guard.** If `e is DayRolled && e.newDay <= s.day`, return `Transition(s, [])`.
2. **Advance time.** If `s.active != null`: `delta = (e.at − active.lastAt).toMillis()`. If `delta > 0`, then `add = min(delta, maxTickGapMillis)`, `carry += add`, `active.lastAt = e.at`, `secs = carry / 1000`. If `secs > 0`, emit `TimeAdded(active.app, s.day, secs)` and set `carry −= secs*1000`. If `delta ≤ 0`, nothing changes (out-of-order events add no time and don't move `lastAt`).
3. **Commit check.** If `s.active != null` and its session has `pending = p` and `e.at − p.since ≥ settleMillis`: `currentIndex = p.index`, `pending = null`. If `p.index > highestCounted`: `highestCounted = p.index`, `count += 1`, emit `Counted(app, s.day, session.id, p.index, p.since, count)`. Then, if `allowance(app) != null && count ≥ allowance`, emit `NudgeRequested(app, count, maxSnoozes − snoozesUsed)`.
4. **Event-specific** (table §4.3).

`setItem(app, index, at)`: `ref = session.pending?.index ?: session.currentIndex`. If `index == ref`, do nothing. Otherwise `pending = Pending(index, at)`. A repeat of the same index therefore never restarts the settle window.
`leave(app, at)`: `pending = null` (dropped if it didn't settle in step 3), `leftAt = at`, `active = null`.

### 4.3 Transition table (step 4)
| Event | Condition | New state | Effects (after any from steps 2–3) |
|---|---|---|---|
| `ScreenEntered(app, at, i)` | `active?.app == app` (duplicate) | `setItem(app, i, at)` | — |
| | `active` is another app B | `leave(B, at)`, then same as the next three rows | `BubbleChanged(app)` only (no `BubbleChanged(null)` in between) |
| | no session, or `at − leftAt > resumeGraceMillis` | new `Session(id = at.toEpochMilli(), highest = −1, current = −1, null, null)`; `active = ActiveScreen(app, at)`; `setItem(app, i, at)` | `BubbleChanged(app)` |
| | session with `at − leftAt ≤ resumeGraceMillis` | session `leftAt = null`; `active = ActiveScreen(app, at)`; `setItem(app, i, at)` | `BubbleChanged(app)` |
| `ItemChanged(app, i, at)` | `active?.app == app` | `setItem(app, i, at)` | — |
| | otherwise | unchanged | — (ignored) |
| `ScreenLeft(app, at)` | `active?.app == app` | `leave(app, at)` | `BubbleChanged(null)` |
| | otherwise | unchanged | — |
| `Tick(at)` | any | unchanged (steps 2–3 did the work) | — |
| `DayRolled(d, at)` | `d > s.day` | `day = d`; every `AppDayState` reset to `AppDayState()` (counts, snoozes, carry = 0); sessions and `active` kept | — |
| `Snoozed(app, at)` | `limits[app] == null` | unchanged | `SnoozeRejected(app)` |
| | `snoozesUsed < maxSnoozes` | `snoozesUsed += 1` | `SnoozeAccepted(app, s.day, snoozesUsed)` |
| | else | unchanged | `SnoozeRejected(app)` |
| `LimitsChanged(m, at)` | any | `limits = m.filterValues { it ≥ 1 }` | — (no immediate nudge) |

Effect order inside one transition: `TimeAdded` → `Counted` → `NudgeRequested` → event-specific effect.

### 4.4 Same video vs new video
- An app's **index** is a position in its feed, reported by the adapter (§5.5): the pager/scroll `fromIndex`, or a ±1 cursor for CHILD_SWAP.
- A video is **in view** once its index has been unchanged for `settleMillis = 350 ms` (the debounce). Bounces, mid-fling indices and duplicate reports never settle or never change the index.
- It's **new** iff its settled index `> highestCounted` of the current session. Scroll-back (lower index), returning to a seen index, likes, comments, pause and re-reports of the same index are all `≤ highestCounted` or no index change → no count.
- A **session** survives leaving the screen for ≤ 10 min (`resumeGraceMillis`), app switches, screen off and day rollover. So "returning to the same video" is not recounted. After 10 min away, a new session starts and the video on screen counts once.
- A **service restart** restores each app's last session from Room (`RestoredSession`, §6.2 `lastEventPerPkg`). Coming back within the grace period doesn't recount.

---

## 5. Detection

### 5.1 `detectors.json` schema + validation (`DetectorConfigParser`)
```jsonc
{
  "version": 1,                                   // must equal 1
  "apps": [                                       // each ShortVideoApp at most once; unknown app name → Error
    {
      "app": "INSTAGRAM",                          // ShortVideoApp name
      "verified": false,                           // false = ids are UNVERIFIED placeholders
      "structuralFallback": true,                  // optional; default = !verified
      "note": "UNVERIFIED: placeholder ids, replace from inspector dump",
      "screens": [                                 // ≥ 1 entry
        {
          "containerIds": ["com.instagram.android:id/…"],   // ≥ 1; each matches VIEW_ID_REGEX
          "excludeIds": [],                                  // each matches VIEW_ID_REGEX
          "signal": "PAGER_PAGE",                            // SCROLL_INDEX | PAGER_PAGE | CHILD_SWAP
          "signalSourceIds": [],                             // each matches VIEW_ID_REGEX; empty = any
          "minCoverage": 0.85                                // 0.0 < x ≤ 1.0
        }
      ]
    }
  ]
}
```
Error messages are in the form `"apps[2].screens[0].containerIds[1]: invalid view id 'x'"`. Malformed JSON → `Error("malformed JSON: <exception message>")`. An app missing from the file means it has no rules and is never detected.

### 5.2 Shipped `assets/rc_detectors.json` (all UNVERIFIED)
```json
{
  "version": 1,
  "apps": [
    { "app": "INSTAGRAM", "verified": false, "note": "UNVERIFIED placeholder ids",
      "screens": [ { "containerIds": ["com.instagram.android:id/clips_viewer_view_pager"],
                     "signal": "PAGER_PAGE",
                     "signalSourceIds": ["com.instagram.android:id/clips_viewer_view_pager"] } ] },
    { "app": "YOUTUBE", "verified": false, "note": "UNVERIFIED placeholder ids",
      "screens": [ { "containerIds": ["com.google.android.youtube:id/reel_recycler"],
                     "signal": "SCROLL_INDEX",
                     "signalSourceIds": ["com.google.android.youtube:id/reel_recycler"] } ] },
    { "app": "TIKTOK", "verified": false, "note": "UNVERIFIED placeholder ids; both packages share this entry",
      "screens": [ { "containerIds": ["com.zhiliaoapp.musically:id/feed_pager", "com.ss.android.ugc.trill:id/feed_pager"],
                     "signal": "PAGER_PAGE" } ] },
    { "app": "FACEBOOK", "verified": false, "note": "UNVERIFIED placeholder ids",
      "screens": [ { "containerIds": ["com.facebook.katana:id/reels_viewer_pager"],
                     "signal": "SCROLL_INDEX" } ] },
    { "app": "SNAPCHAT", "verified": false, "note": "UNVERIFIED placeholder ids",
      "screens": [ { "containerIds": ["com.snapchat.android:id/spotlight_container"],
                     "signal": "CHILD_SWAP" } ] }
  ]
}
```
These ids are **guesses, not sourced from any app**. They exist to exercise the code path. The structural fallback does the real work until the Phase 2 phone loop replaces them with ids from inspector dumps and sets `"verified": true`.

### 5.3 `ScreenDetector.detect(packageName, nodes)` algorithm
1. `app = ShortVideoApp.fromPackage(packageName)`. If null, or `nodes == null`, or there's no `AppDetector` for `app` → `NotShortVideo`.
2. For each `rule` in `screens` (file order):
   a. If any `excludeIds` id has a `findById` result with `visibleToUser`, skip this rule.
   b. For each `id` in `containerIds`, for each `n` in `nodes.findById(id)`: if `n.visibleToUser && n.bounds.coverage(nodes.screen) ≥ rule.minCoverage`, it matches. `childSignature` = `key` of the first child `c` of `n` with `c.visibleToUser && c.bounds.coverage(screen) ≥ rule.minCoverage`, or null if none. Return `ShortVideo(app, rule, viaFallback = false, childSignature)`.
3. If `usesFallback()`: `root = nodes.tree()`. Walk pre-order and take the first node `n` with `n.scrollable && n.visibleToUser && coverage(n) ≥ 0.85` that has a child `c` with `c.visibleToUser && coverage(c) ≥ 0.85`. Return `ShortVideo(app, ScreenRule(containerIds = listOf(n.viewId ?: "*"), signal = SCROLL_INDEX, signalSourceIds = listOfNotNull(n.viewId)), viaFallback = true, childSignature = c.key)`.
4. Otherwise → `NotShortVideo`.

### 5.4 Service config `res/xml/rc_accessibility_service.xml`
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:packageNames="com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically,com.ss.android.ugc.trill,com.facebook.katana,com.snapchat.android"
    android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:isAccessibilityTool="false"
    android:description="@string/rc_service_description" />
```
`ServiceConfigTest` asserts `packageNames` equals `ShortVideoApp.allPackages` and that the event types are exactly these three.

Manifest `<service>`: `android:name=".service.ReelAccessibilityService"`, `android:label="@string/rc_service_label"`, `android:exported="true"`, `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, intent-filter `android.accessibilityservice.AccessibilityService`, meta-data `android.accessibilityservice` → `@xml/rc_accessibility_service`.

### 5.5 Adapter: raw `AccessibilityEvent` → `RawSignal` → `CounterEvent`

**`EventAdapter.toSignal(event, at): RawSignal?`** (`at = clock.now()`, never `event.eventTime`):
| `event.eventType` | Signal |
|---|---|
| any, `event.packageName` not in `ShortVideoApp.allPackages` | `null` |
| `TYPE_WINDOW_STATE_CHANGED` | `WindowStateChanged(pkg, AccessibilityNodeSource(service, pkg), at)` |
| `TYPE_WINDOW_CONTENT_CHANGED` | `WindowContentChanged(pkg, AccessibilityNodeSource(service, pkg), at)` |
| `TYPE_VIEW_SCROLLED` | `ViewScrolled(pkg, ScrollSignal(event.source?.viewIdResourceName, event.fromIndex, event.toIndex, event.itemCount, event.scrollDeltaY), AccessibilityNodeSource(service, pkg), at)` |
| other | `null` |
`EventAdapter` reads only `eventType`, `packageName`, `source.viewIdResourceName`, `fromIndex`, `toIndex`, `itemCount` and `scrollDeltaY`.

**`AccessibilityNodeSource(service, pkg)`**, lazy (no IPC until a method is called):
- `roots` = `service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }.filter { it.packageName == pkg }`. If that's empty, use `listOfNotNull(service.rootInActiveWindow?.takeIf { it.packageName == pkg })`. Cached for the object's lifetime.
- `packageName` = `pkg` if `roots` is non-empty, else null.
- `screen` = `Bounds(0, 0, w, h)` from `WindowManager.currentWindowMetrics.bounds` (API 30+) or `DisplayMetrics` real size (API 28/29).
- `findById(id)` = `roots.flatMap { it.findAccessibilityNodeInfosByViewId(id) }` mapped with depth 1.
- `tree(maxDepth, maxNodes)` = pre-order walk of the first root with caps. When there are several roots, a synthetic root with `key = "windows"` and `viewId = null` holds them.
- Mapping reads only `hashCode()` (→ `key`), `viewIdResourceName`, `className`, `getBoundsInScreen`, `isScrollable`, `isVisibleToUser`, `childCount` and `getChild(i)`.

**`foregroundPackage(service)`** (for `Probe`/`ScreenOn`): `service.windows.firstOrNull { it.isActive }?.root?.packageName?.toString() ?: service.rootInActiveWindow?.packageName?.toString()`.

**`ScreenTracker.onSignal(signal)`** (pure, in `:core`). Internal state: `active: ShortVideoApp?`, `lastEvalAt: Instant?`, `trackers: MutableMap<ShortVideoApp, ItemPositionTracker>` (created on first use, never reset).
`evaluate(pkg, nodes, at)`: sets `lastEvalAt = at` and runs `d = detector.detect(pkg, nodes)`.
- `d is ShortVideo`: `t = trackers[d.app]`, `t.setRule(d.rule)`.
  - If `active != d.app`: `active = d.app`, emit `ScreenEntered(d.app, at, t.index)`. If `d.rule.signal == CHILD_SWAP`, call `t.onChildSignature(d.childSignature, at)` to seed it (its return value is ignored on entry).
  - Else, if `d.rule.signal == CHILD_SWAP`: `t.onChildSignature(d.childSignature, at)?.let { emit ItemChanged(d.app, it, at) }`.
- `d is NotShortVideo` and `active != null`: emit `ScreenLeft(active, at)`, `active = null`.

| Signal | Behaviour |
|---|---|
| `WindowStateChanged` | `evaluate` (never throttled) |
| `WindowContentChanged` | if `lastEvalAt != null && at − lastEvalAt < 300 ms` → `TrackerOutput([], recheckAt = lastEvalAt + 300 ms)`; else `evaluate` |
| `ViewScrolled` | if `active == fromPackage(pkg)`: `trackers[active].onScroll(scroll, at)?.let { ItemChanged }`. Else, throttled `evaluate` as for content changes, then if `active == fromPackage(pkg)` afterwards, also feed `onScroll` and emit `ItemChanged` after the `ScreenEntered`. |
| `Probe` | if `active != null && fromPackage(foregroundPackage) != active` → `ScreenLeft(active)`, `active = null`. Else if `foregroundPackage` is supported → `evaluate(foregroundPackage, nodes, at)` (not throttled). Then **always** append `Tick(at)` last. |
| `ScreenOff` | if `active != null` → `ScreenLeft(active, at)`, `active = null` |
| `ScreenOn` | if `foregroundPackage` is supported → `evaluate` |

**`ItemPositionTracker` rules** (`index` starts at 0):
- `PAGER_PAGE`: accept a scroll if the source passes the filter and `fromIndex ≥ 0 && (toIndex == fromIndex || toIndex < 0)`. New index = `fromIndex`.
- `SCROLL_INDEX`: accept if the source passes the filter and `fromIndex ≥ 0`. New index = `fromIndex`.
- Source filter: `rule.signalSourceIds.isEmpty() || scroll.sourceViewId in rule.signalSourceIds`.
- `onScroll` on a `CHILD_SWAP` rule only records `lastDeltaY = scroll.scrollDeltaY` and `lastDeltaAt = at`, and returns null.
- `CHILD_SWAP` `onChildSignature(sig, at)`: if `sig == null` or `sig == lastSignature` → null. If `lastSignature == null` (first seen): store it and return null. Else: `direction = if (lastDeltaAt != null && at − lastDeltaAt ≤ 1 000 ms && lastDeltaY < 0) −1 else +1`, `index = max(0, index + direction)`, store `sig`, return `index`.
- A returned index equal to the current one → return null instead.

**`CounterRunner`** (`:android`, `@Singleton`). Serial dispatcher: `Dispatchers.Default.limitedParallelism(1)`, injected as `@CounterDispatcher`.
- `start()` (from `onServiceConnected`):
  - Load `RestoreData` from `ReelRepository.loadRestore(day)`, limits from `SettingsStore`, detector from `DetectorRepository`.
  - Build `CounterState.initial(...)`.
  - Collect `SettingsStore.limits` → submit `LimitsChanged`. Collect `DetectorRepository.config` → `tracker.replaceDetector`. Collect `SettingsStore.dayStartMinutes` → rebuild `GameDay`.
- `submitSignal(raw)`: on the serial dispatcher:
  1. **Day guard.** `d = gameDay.dayOf(raw.at)`. If `d > state.day`, reduce `DayRolled(d, raw.at)` first. If `d < state.day`, do nothing (never roll back).
  2. `out = tracker.onSignal(raw)`. Reduce each event in order and collect the effects.
  3. `EffectApplier.apply(effects)`.
  4. If `out.recheckAt != null`, (re)schedule a single job that submits a `Probe` at `recheckAt`. The `Probe` gets a fresh `foregroundPackage` and `AccessibilityNodeSource`, built by a lambda the service passes in.
  5. **Probe loop.** While `tracker.activeApp != null`, run a 1 000 ms loop that submits `Probe`. Cancel it when `activeApp == null`.
- `submitSnooze(app)`: reduce `Snoozed(app, clock.now())` on the serial dispatcher.
- `stop()`: cancel jobs, submit `ScreenOff(now)` (flushes time and hides overlays), then `EffectApplier.flushTime()`.
- `state: StateFlow<CounterState>` drives the bubble and panel.

**`EffectApplier`**:
- `Counted` → `repository.recordReel(...)` (transaction) → `widgetRefresher.request()`.
- `TimeAdded` → in-memory buffer keyed by `(day, app)`. Flushed with `repository.addSeconds` when ≥ 15 s have passed since the last flush, or on `BubbleChanged(null)`, `DayRolled` (detected by `day` change) or `stop()`.
- `NudgeRequested` → `overlay.showNudge(app, count, snoozesLeft)`.
- `SnoozeAccepted` → `repository.setSnoozes(day, app, used)`, then `overlay.hideNudge()`.
- `SnoozeRejected` → `overlay.showNudge(app, state.countToday(app), 0)`.
- `BubbleChanged(app)` → `overlay.showBubble(app)`, or `overlay.hideAll()` when null; plus `widgetRefresher.request()`.

**`ScreenStateReceiver`**: registered in `onServiceConnected` with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` for `ACTION_SCREEN_OFF` → `ScreenOff(now)` and `ACTION_SCREEN_ON` → `ScreenOn(fg, nodes, now)`. Unregistered in `onUnbind`/`onDestroy` (inside `runCatching`).

**Battery budget per event:** throttled content evaluation is ≤ 3.3 per second. Each one does `findById` × (container ids + exclude ids) IPCs. A full-tree walk (≤ 400 nodes) happens only for unverified apps with no id match. Off short-video screens there are no ticks.

---

## 6. Data

### 6.0 `MachineHarness` edit (Phase 1)
Add the constructor parameter `restored: Map<ShortVideoApp, RestoredSession> = emptyMap()` as the last parameter and pass it to `CounterState.initial(day, todayCounts, todaySnoozes, limits, restored)`. Add `fun snoozeEffects() = effects.filter { it is CounterEffect.SnoozeAccepted || it is CounterEffect.SnoozeRejected }`. Nothing else changes.

### 6.1 Room entities (`ReelDatabase`, version 1, `exportSchema = true`)
```kotlin
@Entity(tableName = "reel_event",
        indices = [Index(value = ["pkg", "session_id", "item_index"], unique = true), Index(value = ["day"])])
data class ReelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pkg") val pkg: String,
    @ColumnInfo(name = "ts") val ts: Long,                 // epoch millis = Counted.at
    @ColumnInfo(name = "item_index") val itemIndex: Int,
    @ColumnInfo(name = "day") val day: String,             // ISO yyyy-MM-dd game day
    @ColumnInfo(name = "session_id") val sessionId: Long,
)
@Entity(tableName = "daily_total", primaryKeys = ["date", "pkg"])
data class DailyTotalEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "pkg") val pkg: String,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "seconds") val seconds: Long,
    @ColumnInfo(name = "snoozes") val snoozes: Int,
)
```

### 6.2 `ReelDao`
API 28 ships SQLite 3.22, so `ON CONFLICT DO UPDATE` is **not** available. Upserts are `INSERT OR IGNORE` + `UPDATE`.
```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(e: ReelEventEntity): Long   // -1 = duplicate
@Query("INSERT OR IGNORE INTO daily_total(date, pkg, count, seconds, snoozes) VALUES(:date, :pkg, 0, 0, 0)") suspend fun ensureTotal(date: String, pkg: String)
@Query("UPDATE daily_total SET count = count + 1 WHERE date = :date AND pkg = :pkg") suspend fun incrementCount(date: String, pkg: String)
@Query("UPDATE daily_total SET seconds = seconds + :seconds WHERE date = :date AND pkg = :pkg") suspend fun addSeconds(date: String, pkg: String, seconds: Long)
@Query("UPDATE daily_total SET snoozes = MAX(snoozes, :snoozes) WHERE date = :date AND pkg = :pkg") suspend fun raiseSnoozes(date: String, pkg: String, snoozes: Int)
@Query("SELECT * FROM daily_total WHERE date = :date") fun observeDay(date: String): Flow<List<DailyTotalEntity>>          // Today hero + breakdown, bubble panel, widget
@Query("SELECT * FROM daily_total WHERE date = :date") suspend fun day(date: String): List<DailyTotalEntity>               // restore, widget
@Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date") fun observeRange(from: String, to: String): Flow<List<DailyTotalEntity>>   // 7/30-day charts, streak (from = today − 365)
@Query("SELECT MIN(date) FROM daily_total") fun observeFirstDay(): Flow<String?>                                           // streak + average lower bound
@Query("SELECT * FROM reel_event WHERE id IN (SELECT MAX(id) FROM reel_event GROUP BY pkg)") suspend fun lastEventPerPkg(): List<ReelEventEntity>   // RestoredSession
@Query("SELECT * FROM daily_total ORDER BY date, pkg") suspend fun allTotals(): List<DailyTotalEntity>                     // export
@Query("SELECT * FROM reel_event ORDER BY ts") suspend fun allEvents(): List<ReelEventEntity>                              // export
```

`ReelRepository` (`@Singleton`, uses `@IoDispatcher`):
```kotlin
suspend fun recordReel(c: CounterEffect.Counted): Boolean      // withTransaction { id = insertEvent; if (id == -1L) return false; ensureTotal; incrementCount; true }
suspend fun addSeconds(day: LocalDate, app: ShortVideoApp, seconds: Long)   // withTransaction { ensureTotal; addSeconds }
suspend fun setSnoozes(day: LocalDate, app: ShortVideoApp, snoozes: Int)    // withTransaction { ensureTotal; raiseSnoozes }
suspend fun loadRestore(day: LocalDate): RestoreData
fun observeDay(day: LocalDate): Flow<List<DayTotal>>
fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayTotal>>
fun observeFirstDay(): Flow<LocalDate?>
suspend fun exportDocument(now: Instant, zone: ZoneId, dayStart: LocalTime, limits: Map<ShortVideoApp, Int>): ExportDocument
data class RestoreData(val counts: Map<ShortVideoApp, Int>, val snoozes: Map<ShortVideoApp, Int>, val sessions: Map<ShortVideoApp, RestoredSession>)
// sessions: from lastEventPerPkg → RestoredSession(sessionId, highestCounted = itemIndex, lastSeenAt = Instant.ofEpochMilli(ts))
```
Rows whose `pkg` isn't a known primary package are skipped when mapping to `DayTotal`.

### 6.3 DataStore (`preferencesDataStore(name = "rc_settings")`) via `SettingsStore`
| Key | Type | Default | Meaning |
|---|---|---|---|
| `day_start_minutes` | Int | 240 | minutes after local midnight (0–1380, multiples of 60 in the UI) |
| `limit_INSTAGRAM` … `limit_SNAPCHAT` | Int | absent | per-app limit; absent = off |
| `bubble_enabled` | Boolean | true | show the bubble at all |
| `bubble_edge` | String | `"END"` | `"START"` or `"END"` |
| `bubble_y_fraction` | Float | 0.35 | bubble centre Y / usable height, 0..1 |
| `setup_completed` | Boolean | false | start destination Stats vs Setup |
| `last_heartbeat_ms` | Long | absent | service heartbeat (written on connect and every 5 min) |
| `inspector_enabled` | Boolean | false | debug only; the release build ignores it |

The "hidden for this session" bubble state is **in memory only**: `OverlayController.hiddenSessionIds: MutableSet<Long>`. The bubble stays hidden while `state.sessions[app].id` is in that set.

### 6.4 JSON export (`ExportDocument`), example
```json
{
  "format": "reelcounter-export",
  "version": 1,
  "exportedAt": "2026-11-01T18:30:00Z",
  "zone": "Asia/Kolkata",
  "dayStart": "04:00",
  "limits": { "INSTAGRAM": 50 },
  "dailyTotals": [
    { "date": "2026-11-01", "app": "INSTAGRAM", "pkg": "com.instagram.android", "count": 42, "seconds": 2520, "snoozes": 1 }
  ],
  "events": [
    { "app": "INSTAGRAM", "pkg": "com.instagram.android", "ts": "2026-11-01T12:01:05.120Z", "day": "2026-11-01", "itemIndex": 12, "sessionId": 1762000000000 }
  ]
}
```
Written via SAF `ActivityResultContracts.CreateDocument("application/json")` with suggested name `reel-counter-<yyyy-MM-dd>.json`. Nothing is sent anywhere.

---

## 7. UI

### 7.1 Tokens (`ReelColors`): feature code uses only these
| Token | Light | Dark | Used for |
|---|---|---|---|
| `background` | `#F6F6F3` | `#0E0F11` | screen background |
| `surface` | `#FFFFFF` | `#17181B` | cards, bubble, panel |
| `surfaceMuted` | `#EEEEEA` | `#202226` | monogram circle, segmented track, chart empty bars |
| `outline` | `#E3E3DD` | `#2C2E33` | 1dp borders (bubble, dark-mode cards) |
| `textPrimary` | `#16171A` | `#F2F2EF` | numerals, titles |
| `textSecondary` | `#5E6168` | `#A4A7AE` | body |
| `textTertiary` | `#8E9198` | `#6F737B` | captions, axis labels |
| `accent` | `#2F6F62` | `#7CC4B2` | the one accent: ring, primary button, today bar |
| `onAccent` | `#FFFFFF` | `#0E0F11` | text on accent |
| `accentMuted` | `#DCEBE6` | `#1F3A34` | ring track, past bars, selected segment bg |
| `critical` | `#B3261E` | `#F2B8B5` | over limit, ✗ |
| `onCritical` | `#FFFFFF` | `#601410` | text on critical |
| `positive` | `#2E7D4F` | `#8BD3A6` | ✓ |
| `warning` | `#9A6700` | `#E5C07B` | ⚠ service killed, unknown |
| `scrim` | `#66000000` | `#99000000` | behind the tap panel only |

No gradients, shadows, glows or neon. Cards have no elevation. In dark mode only, cards get a 1dp `outline` border.

### 7.2 Type (`ReelTypography`, `FontFamily.Default`)
| Style | Size / line | Weight | Letter spacing | Notes |
|---|---|---|---|---|
| `hero` | 96 / 100 sp | Light (300) | −2 sp | `tnum`, Stats hero |
| `display` | 56 / 60 sp | Light | −1 sp | `tnum`, nudge number |
| `title` | 22 / 28 sp | SemiBold | −0.2 sp | screen titles |
| `headline` | 17 / 22 sp | SemiBold | 0 | card titles |
| `body` | 16 / 22 sp | Normal | 0 | |
| `bodyNumeric` | 16 / 22 sp | Medium | 0 | `tnum`, counts in rows |
| `label` | 13 / 18 sp | Medium | 0.1 sp | buttons secondary, segment |
| `caption` | 12 / 16 sp | Normal | 0.2 sp | axis, footnotes |
| `bubble` | 15 / 18 sp | SemiBold | −0.2 sp | `tnum`, bubble numeral; 13 sp when ≥ 3 chars |

### 7.3 Dimens (`ReelDimens`)
Spacing `xs 4`, `s 8`, `m 12`, `l 16`, `xl 24`, `xxl 32`, `xxxl 48` (dp). Screen side gutter 20dp.
Radii: card 24, button 16, panel 20, segment 12, chip 12.
Sizes:
- Button height 52dp. Min touch target 48dp.
- Bubble 44dp; ring stroke 3dp, inset 2dp.
- Monogram 32dp. Bar chart height 160dp, bar corner 4dp.
- Bar gap: 8dp for 7 days, 3dp for 30 days.
- Bubble edge margin 8dp.

### 7.4 Components (stateless, in `designsystem/components`; each has `@ReelPreviews` for the listed states)
`@ReelPreviews` = four previews: phone 411×891 light, phone dark (`uiMode = UI_MODE_NIGHT_YES`), small phone 360×640 light, small phone dark.

| Component | Signature | Preview states |
|---|---|---|
| `ReelCard` | `(modifier, content)` | plain |
| `HeroNumber` | `(value: Int, caption: String)` | 0, 7, 1 284 |
| `ReelButton` | `(text, onClick, style: Primary/Secondary/Quiet, enabled)` | each style, disabled |
| `ProgressRing` | `(progress: Float?, over: Boolean, size, stroke)` | null, 0.46, 1.0, over |
| `CounterBubble` | `(count: Int, limit: Int?, allowance: Int?)` | no limit 7; 23/50; 50/50; over 57/55 (critical ring); 999+ (count ≥ 1000 shows "999+") |
| `TodayPanel` | `(rows: List<PanelRow>, onClose)` where `PanelRow(app, count, minutes)` | 1 app; 5 apps; all zero |
| `NudgeContent` | `(appName, count, limit, snoozesLeft, onClose, onSnooze)` | snoozesLeft 2, 1, 0 |
| `BarChart` | `(bars: List<DayBar>, highlightLast: Boolean)` | 7 days typical, 30 days, all zero |
| `SegmentedToggle` | `(options: List<String>, selected: Int, onSelect)` | 7D selected, 30D selected |
| `AppMonogram` | `(app)` | each app |
| `AppRow` | `(app, count, minutes, limit: Int?)` | no limit, under, over |
| `LimitBar` | `(count, allowance)` | 0.3, 1.0, over |
| `StatusRow` | `(title, detail, state: Ok/Bad/Warn/Info, actionLabel?, onAction?)` | each state |
| `StepDots` | `(count, current)` | 0/6, 3/6 |
| `Wordmark` | `(modifier)` | — |
| `LimitStepper` | `(enabled, value, onToggle, onMinus, onPlus)` | off, 50, min 5, max 500 |
| `SettingRow` | `(label, value?, onClick)` | with/without value |
| `ReelTopBar` | `(title, onBack?)` | with/without back |
| `DebugChip` | `(label, onClick)` | match, no match |

Visual specs:
- **CounterBubble**: `surface` circle with a 1dp `outline` border. `ProgressRing` inset 2dp: track `accentMuted`, progress `accent`, sweeping clockwise from 12 o'clock with round caps.
  - With no limit, the track and progress are not drawn.
  - `count ≥ allowance` draws the full ring in `critical`.
  - The numeral is centred in `textPrimary`.
  - Content description: "Instagram: 23 reels today".
- **NudgeContent**: full-bleed `background`, centred column.
  - `rc_mark` 40dp tinted `accent`, then `display` "50" in `textPrimary`.
  - `title` "reels today" (plural), then `body` "on Instagram · your limit is 50" in `textSecondary`.
  - Bottom: Primary [Close app]. Below it, Secondary "5 more · 2 left". When `snoozesLeft == 0`, the secondary button is replaced by a caption "No more snoozes today".
- **TodayPanel**: `surface` card, 20dp radius, width 260dp.
  - Header `headline` "Today". One `AppRow` per app with `count > 0`, sorted by count desc. "Nothing yet today" when empty.
  - Footer caption "Long-press the bubble to hide it".
- **BarChart**:
  - Each bar's height is proportional to `total / max(maxTotal, 1)`. A zero bar is a 2dp `surfaceMuted` stub.
  - Past bars are `accentMuted`; the last (today) bar is `accent`.
  - 7-day labels are weekday initials. 30-day labels show only every 7th date (d MMM). All labels are `caption`/`textTertiary`. No gridlines.

### 7.5 Screens (layout only; stateless `XScreen(state, onEvent)` + thin `XRoute`; previews per state)
Routes (`ReelRoutes.kt`): `@Serializable object SetupRoute`, `StatsRoute`, `StatusRoute`, `LimitsRoute`, `SettingsRoute`, `InspectorRoute`. The start destination is `StatsRoute` if `setup_completed`, else `SetupRoute`. `InspectorRoute` is only added when `InspectorModule.inspectorScreen != null`.

**Setup** (`SetupUiState(step: SetupStep, accessibilityEnabled, serviceConnected, batteryExempt, oem: Oem?, restrictedSettingsHint: Boolean)`). Steps in order: `INTRO → ACCESSIBILITY → OVERLAY → BATTERY → AUTOSTART → DONE`. AUTOSTART is skipped when `oem == null`.
- INTRO: `Wordmark`, `title` "Count the reels, not the hours". Three short `body` lines:
  - "Reel Counter reads only the on-screen element IDs of Instagram, YouTube, TikTok, Facebook and Snapchat, to tell when a short-video screen is open."
  - "It never reads text, messages or captions."
  - "Nothing leaves your phone."
  
  Primary [Continue].
- ACCESSIBILITY:
  - States: `NotEnabled` (Primary [Open Accessibility settings]), `EnabledNotConnected` (spinner caption "Waiting for the service…", re-checked every 500 ms up to 10 s, then the ✗ hint), `Connected` (✓ row + Primary [Continue]).
  - Quiet expandable "Can't turn it on?" explains Android 13+ restricted settings: App info → ⋮ → Allow restricted settings.
  - Verification runs in `onResume` (lifecycle `ON_RESUME` → `SetupEvent.Recheck`).
- OVERLAY: ✓ row "Overlay: handled by the accessibility service, no extra permission". Primary [Continue] (D1).
- BATTERY: states `Restricted` (Primary [Open battery settings], Quiet [Skip]) and `Exempt` (✓ + [Continue]). Re-checked on resume via `PowerManager.isIgnoringBatteryOptimizations`.
- AUTOSTART: OEM-specific instructions, Primary [Open autostart settings] (tries the §7.5.1 candidates in order), Quiet [Done, continue]. Autostart can't be verified, so the step always lets the user continue.
- DONE: ✓ mark, `title` "You're set", Primary [See today] → writes `setup_completed = true`, navigates to Stats (pop Setup).

Preview states: one per step, plus Accessibility×3 and Battery×2.

**7.5.1 `OemAutostart`**: `Oem` enum from `Build.MANUFACTURER.lowercase()` (and `Build.BRAND`). Each candidate is an explicit `ComponentName`, tried with `startActivity` in `runCatching` (`ActivityNotFoundException`/`SecurityException` → next). The last fallback is `ACTION_APPLICATION_DETAILS_SETTINGS` for our package, then `Settings.ACTION_SETTINGS`.
| Oem | Match | Candidates (in order) |
|---|---|---|
| XIAOMI | xiaomi, redmi, poco | `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` |
| OPPO | oppo, realme | `com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity`, `com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity`, `com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity` |
| VIVO | vivo, iqoo | `com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity`, `com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.BgStartUpManager` |
| ONEPLUS | oneplus | `com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity` |
| SAMSUNG | samsung | `com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity`, `com.samsung.android.sm/com.samsung.android.sm.ui.battery.BatteryActivity` |
These component names are public OEM settings entry points. They are UNVERIFIED on the user's phone, which is why the fallbacks exist. Each OEM gets a 2–3 line instruction string (`rc_autostart_<oem>`) saying what toggle to look for ("Autostart", "Allow background activity", "Never sleeping apps").

**Stats** (`StatsUiState(todayTotal, todayMinutes, rows: List<AppRowUi>, range: Range(SEVEN|THIRTY), bars: List<DayBar>, average: Int, streak: Int?, hasAnyData, serviceProblem: Boolean)`).
- Layout top to bottom:
  - `ReelTopBar` "Today" with settings icon.
  - If `serviceProblem`: a warning `StatusRow` → Status.
  - `HeroNumber(todayTotal, "reels today")`.
  - `body` "≈ 42 minutes of short video" (hidden when minutes = 0).
  - Card "By app": `AppRow`s for all 5 apps, zero rows in `textTertiary`.
  - Card with `SegmentedToggle` 7D/30D, `BarChart`, caption "Average 38 a day".
  - Card streak: "5 days within your limits", or with no limits "Set a daily limit to start a streak" + [Set limits].
  - Row of `SettingRow`s: Limits, Status.
- States: Empty (no data), Typical, OverLimit, NoLimits, ThirtyDays, ServiceProblem.
- Data: `observeDay(today)`, `observeRange(today − 365, today)` combined with limits, `firstDay`, `ServiceStatus` and `PermissionChecker`. `today` comes from `GameDay(clock.zone(), dayStart).dayOf(clock.now())`, recomputed every minute via a ticker flow.

**Limits** (`LimitsUiState(rows: List<LimitRowUi(app, enabled, value)>)`): `ReelTopBar` "Daily limits" + back. Caption "When you reach a limit, you'll see a reminder with an option for 5 more, twice a day." One `LimitStepper` per app. Toggling on sets `DEFAULT_LIMIT` 50; ± changes by `STEP` within `[MIN, MAX]`; toggling off removes the key. States: all off, some on, min/max.

**Settings** (`SettingsUiState(dayStartHour, bubbleEnabled, exportInProgress, exportResult: Done/Failed/null, showInspector)`):
- Rows:
  - Day starts at (opens a dialog listing 00:00–23:00; changes apply from the next boundary, per the runner's day guard).
  - Show bubble (switch).
  - Daily limits.
  - Status.
  - Set up again.
  - Export data (JSON).
  - Detector inspector (debug only).
- States: default, exporting, export done, export failed.

**Status** (`StatusUiState(rows: List<StatusRowUi>, serviceKilled: Boolean, lastActive: Instant?)`):
- Rows:
  1. Accessibility service: Ok / Bad [Fix → accessibility settings].
  2. Service running: Ok, Warn "Android stopped Reel Counter" [Fix → accessibility settings] when enabled-in-settings but `!connected` for > 5 s after the screen opens, or Info "Not running" when disabled.
  3. Overlay: Ok "Handled by the accessibility service".
  4. Battery: Ok / Bad [Fix].
  5. Autostart (only on a known OEM): Info "Can't be checked" [Open].
  6. Detector rules: Ok "Built-in rules (unverified ids)" / Ok "Your custom rules" / Bad "Custom rules have an error: <msg>; using built-in".
  7. Last active: caption "Last active 3 min ago".
- Re-checked on resume. States: AllGood, ServiceOff, ServiceKilled, BatteryRestricted, DetectorError.

**Inspector** (debug only): switch "Show dump chip on supported apps"; list of dump files (newest first) → viewer with monospace text, [Copy] (clipboard), [Delete]; card "Custom detector rules" with a multiline field, [Validate & save] (parser result shown inline), [Reset to built-in].

**Overlays** (`OverlayController`, all windows `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `PixelFormat.TRANSLUCENT`):
- **Bubble**: `WRAP_CONTENT`, gravity `TOP|START`, `x`/`y` from `BubblePosition`.
  - Drag (`detectDragGestures`) moves the window via `updateViewLayout`. On release it snaps to the nearest horizontal edge (`x = 8dp` or `screenW − 44dp − 8dp`), with `y` clamped to `[statusBar + 8dp, screenH − navBar − 44dp − 8dp]`. Then `bubble_edge` and `bubble_y_fraction` are saved.
  - Tap toggles the panel.
  - Long-press (system long-press timeout): add `sessions[app].id` to `hiddenSessionIds`, hide the bubble and panel, and show a `Toast` "Hidden until your next session".
  - Shown only when `bubble_enabled`, an app is active and its session isn't hidden.
- **Panel**: `WRAP_CONTENT` next to the bubble, on the side away from the edge, vertically centred on it and clamped to the screen. Flags add `FLAG_WATCH_OUTSIDE_TOUCH`; `ACTION_OUTSIDE` closes it. It also auto-closes after 5 s and on `BubbleChanged`.
- **Nudge**: `MATCH_PARENT`.
  - [Close app] → `service.performGlobalAction(GLOBAL_ACTION_HOME)`, then hide.
  - [5 more] → `runner.submitSnooze(app)`.
  - Removed on `BubbleChanged(null)`.
- **Debug chip** (debug builds with `inspector_enabled`): top-centre, shown whenever the last `WindowStateChanged`/`Probe` package is supported. Label `"Dump · ✓ IG"` (match, monogram) or `"Dump · no match"`. Tap → `InspectorHooks.dump(...)` + Toast "Saved <file name>".
- Every `addView`/`updateViewLayout`/`removeView` is wrapped in `runCatching` (service may be gone). `hideAll()` is called from `stop()`.

`BubblePosition` (pure): `data class BubblePosition(val edge: Edge, val yFraction: Float)`, `fun toPx(screenW, usableTop, usableBottom, sizePx, marginPx): Pair<Int, Int>`, `companion fun snap(xCenterPx, yPx, screenW, usableTop, usableBottom, sizePx): BubblePosition`.

### 7.6 Strings (`rc_strings.xml`, all keys `rc_…`)
Implement every string the screens above name, with exactly that English copy. Plurals: `rc_reels_today` (one: "reel today", other: "reels today"), `rc_minutes_equivalent` ("≈ %d minute of short video" / "≈ %d minutes of short video"), `rc_streak_days` ("%d day within your limits" / "%d days within your limits"), `rc_average_per_day` ("Average %d a day"), `rc_snooze_left` ("%d left"). Service description `rc_service_description` = "Counts the short videos you watch on Instagram, YouTube, TikTok, Facebook and Snapchat. It only looks at on-screen element IDs, never at text, and nothing leaves your phone."

### 7.7 Widget (`ReelWidget`, Glance)
`SizeMode.Responsive(setOf(DpSize(110.dp, 48.dp), DpSize(250.dp, 110.dp)))`.
- Small: caption "Today", numeral total (32 sp, bold).
- Large: total + up to 5 rows "Instagram 23" (apps with count > 0, desc), or "No reels yet today".

Colours come from `ColorProvider(day = light token, night = dark token)` for `surface`, `textPrimary`, `textSecondary` and `accent`, defined in `rc_colors.xml` (`rc_widget_*`).
- Tap → host launch intent (`packageManager.getLaunchIntentForPackage(context.packageName)`).
- Data: `WidgetEntryPoint` → `ReelRepository.day(today)`.
- `rc_widget_info.xml`: `minWidth 110dp`, `minHeight 48dp`, `resizeMode horizontal|vertical`, `updatePeriodMillis 0`, `targetCellWidth 2`, `targetCellHeight 1`, description string.
- `WidgetRefresher.request()`: at most one `updateAll` per 10 s (trailing).
- `DayBoundaryWorker`: `OneTimeWorkRequest`, unique name `rc_day_boundary`, `REPLACE`, delay = `nextBoundary(now) − now`. It refreshes the widget and re-enqueues itself. First enqueued by `CounterRunner.start()` and by `SettingsStore` day-start changes.

---

## 8. Icon and wordmark construction

**Grid:** adaptive icon 108×108 viewport, safe zone = centred circle Ø66 (21..87). Two colours only: **accent `#2F6F62`** (background) and **white `#FFFFFF`** (foreground). All coordinates in viewport units.

**Background layer** `rc_ic_launcher_background.xml`: one path filling 0,0–108,108 with `#2F6F62`.

**Foreground layer** `rc_ic_launcher_foreground.xml`: four white paths. Holes use `android:fillType="evenOdd"` (shape + hole in one path). The cards are a stack seen from the front: the two back cards show only as strips above the front card, with 2-unit gaps.
1. **Back strip**: rect x 42→58, y 26→30. Top corners radius 4, bottom corners square.
2. **Middle strip**: rect x 39→61, y 32→36. Top corners radius 5, bottom square, **minus** circle centre (64, 40) r 10.
3. **Front card**: rounded rect x 36→64, y 38→80, radius 6, **minus**:
   - the circle centre (64, 40) r 10 (gap ring around the badge)
   - the play triangle (45, 52) → (45, 66) → (57, 59)
4. **Badge**: circle centre (64, 40) r 8, **minus** a plus sign made of two rects: x 60→68, y 39→41 and x 63→65, y 36→44.

Bounding box x 34..74, y 26..82: visually centred at (54, 54), inside the safe zone. At 48px (≈ 0.44 px/unit), the 2-unit gaps render ≈ 0.9 px. That's acceptable at 48px; verify visually in the Phase 3 preview at 48dp.

**Monochrome layer** `rc_ic_launcher_monochrome.xml`: identical to the foreground paths (white). The launcher tints it.
**Adaptive** `mipmap-anydpi/rc_ic_launcher.xml` and `_round.xml`: `<adaptive-icon>` with background, foreground and monochrome.
**Mark** `rc_mark.xml`: the same four paths in a 108 viewport cropped to `viewportWidth=40 viewportHeight=56`, with a group `translateX=-34 translateY=-26`. White fill, tinted at the use site.
**SVG** `branding/rc_icon.svg`: the same geometry, `viewBox="0 0 108 108"`, accent background rect (no rounding, so launchers mask it), white paths with `fill-rule="evenodd"`.
**PNG** `branding/rc_icon_512.png`: 512×512, rendered by `BrandingExportTest` (`:android` unit test, JVM `java.awt`). It only runs when the system property `rc.exportBranding=true` is set (`./gradlew :android:testDebugUnitTest --tests '*BrandingExportTest' -Prc.exportBranding=true`; the build file forwards `-Prc.exportBranding` to `systemProperty`). It draws the same geometry scaled ×(512/108) with `java.awt.geom.Area` subtraction, full-bleed background, antialiasing on, and writes `../branding/rc_icon_512.png` relative to the `android` module directory.
**Wordmark**:
- Compose `Wordmark()`: `Row` of `rc_mark` 28dp tall tinted `accent`, 10dp gap, then `Text("Reel Counter")` in `title` with `textPrimary` ("Reel") + `textSecondary` ("Counter") as one `AnnotatedString`.
- `branding/rc_wordmark.svg`: same layout with `<text font-family="system-ui, sans-serif" font-weight="600" font-size="22">`.

---

## 10. Tests

### 10.0 Harness notation (core)
`h = MachineHarness(...)`; times are ms after `T0` (`2026-11-01T10:00:00Z`, `DAY_1`). `enter(app, ms, index)`, `item(app, i, ms)`, `leave(app, ms)`, `tick(ms)`, `roll(day, ms)`, `snooze(app, ms)`, `setLimits(map, ms)`.
- `open(app, ms)` = `enter` at `ms` + `tick` at `ms + 400`.
- `settle()` = `tick(t + 400)`.
- `watchNext(app, n)` = n × (`item(cursor + 1, t + 1 000)` + `settle()`).
- `swipeTo(app, i)` = `item(i, t + 1 000)` + `settle()`.

"count" = `h.state.countToday(app)`, "secs(app, day)" = sum of `TimeAdded.seconds`. IG = INSTAGRAM, YT = YOUTUBE.

### 10.1 `:core` edge-case tests (write ALL first in Phase 1)

**CounterMachineCountingTest** (C1–C16 already written in WIP, keep as is): `firstVideoCountsAfterSettle`, `itemNotCountedBeforeSettleWindow` (tick 349 → 0, tick 350 → 1), `rapidDuplicateItemChangedSameIndexCountsOnce`, `burstOfEventsWithinSettleCountsOnce`, `partialSwipeThatBouncesBackDoesNotCount`, `fullSwipeForwardCountsPlusOne`, `scrollBackDoesNotRecount`, `scrollBackThenForwardToSeenVideoDoesNotRecount`, `scrollBackThenPastHighestCountsOnlyNewVideo`, `fastFlingSkippingIndicesCountsOncePerSettledStop`, `tickAloneDoesNotCount`, `contentChangesWithoutIndexChange_likeCommentPause_doNotCount`, `itemChangedForOtherAppWhileOnScreenIsIgnored`, `itemChangedWhenNotOnShortVideoScreenIsIgnored`, `countsArePerApp`, `countedEffectCarriesSessionIdIndexAndRunningTotal`.

**CounterMachineSessionTest**
| # | Name | Sequence | Expect |
|---|---|---|---|
| S1 | `leaveAndReturnWithinGraceToSameVideoDoesNotRecount` | open(IG,0); watchNext(IG,1); leave(IG,5 000); enter(IG,65 000,1); tick(66 000) | count 2; `counted()` size 2 |
| S2 | `leaveAndReturnWithinGraceThenNewVideoCounts` | S1 then item(IG,2,67 000); tick(67 400) | count 3; last Counted index 2, sessionId == first sessionId |
| S3 | `returnExactlyAtGraceResumesSession` | open(IG,0); leave(IG,1 000); enter(IG,601 000,0); tick(601 400) | count 1 |
| S4 | `returnAfterGraceStartsNewSessionAndCountsVisibleVideo` | open(IG,0); leave(IG,1 000); enter(IG,601 001,0); tick(601 401) | count 2; counted[1].index 0 |
| S5 | `newSessionIdIsEnterTimestamp` | S4 | counted[0].sessionId == T0 ms; counted[1].sessionId == T0 + 601 001 ms |
| S6 | `appSwitchMidVideoKeepsSessionsSeparate` | open(IG,0); watchNext(IG,1); open(YT,3 000); enter(IG,10 000,1); tick(10 400) | IG 2, YT 1; bubbles == [IG, YT, IG] (no null between) |
| S7 | `appSwitchBeforeSettleDropsPendingOfOldApp` | open(IG,0); item(IG,1,1 000); enter(YT,1 100,0); tick(1 500) | IG 1, YT 1 |
| S8 | `droppedPendingIsCountedWhenReturningToIt` | S7 then enter(IG,2 000,1); tick(2 400) | IG 2 |
| S9 | `pendingItemIsCommittedOnScreenLeftIfSettled` | open(IG,0); item(IG,1,1 000); leave(IG,1 400) | count 2 |
| S10 | `pendingItemIsDroppedOnScreenLeftIfNotSettled` | open(IG,0); item(IG,1,1 000); leave(IG,1 200) | count 1 |
| S11 | `duplicateScreenEnteredIsIdempotent` | enter(IG,0,0); enter(IG,100,0); tick(500) | count 1; bubbles == [IG]; one session |
| S12 | `screenLeftWithoutEnterIsNoOp` | leave(IG,0) | no effects; state == initial |
| S13 | `screenLeftForOtherAppIsIgnored` | open(IG,0); leave(YT,1 000) | active app still IG; no BubbleChanged(null) |
| S14 | `screenOffThenOnSameVideoNoRecountNoOffTime` | enter(IG,0,0); tick(1 000); tick(2 000); leave(IG,2 000); enter(IG,62 000,0); tick(63 000) | count 1; secs(IG) 3 |

**CounterMachineTimeTest**
| # | Name | Sequence | Expect |
|---|---|---|---|
| T1 | `timeAccumulatesFromTicksWhileOnScreen` | enter(IG,0); tick every 1 000 to 10 000 | secs 10; ten `TimeAdded(IG, DAY_1, 1)` |
| T2 | `noTimeAccumulatesOffScreen` | tick 0..10 000; enter(IG,10 000); leave(IG,12 000); tick(20 000) | secs 2 |
| T3 | `tickGapLargerThanCapIsClamped` | enter(IG,0); tick(1 000); tick(61 000) | secs 6 |
| T4 | `subSecondCarryKeptAcrossLeave` | enter(IG,0); tick(1 000); leave(IG,2 500); enter(IG,10 000); tick(10 500) | secs 2 after leave (`apps[IG].carryMillis` 500); secs 3 at end |
| T5 | `timeAttributedToCorrectAppWhenSwitching` | enter(IG,0); tick(1 000); tick(2 000); enter(YT,3 000); tick(4 000) | IG 3, YT 1 |
| T6 | `outOfOrderEventWithEarlierTimestampAddsNoTime` | enter(IG,0); tick(2 000); tick(1 500); tick(3 000) | secs 3; tick(1 500) emits nothing |
| T7 | `itemChangedAndScreenLeftAlsoAdvanceTime` | enter(IG,0); item(IG,1,1 000); leave(IG,2 000) | secs 2 |
| T8 | `timeAddedCarriesCurrentDay` | enter(IG,0); tick(1 000) | `TimeAdded.day == DAY_1` |

**CounterMachineRolloverTest**
| # | Name | Sequence | Expect |
|---|---|---|---|
| R1 | `dayRolledResetsCountsToZero` | open(IG,0); watchNext(IG,2); roll(DAY_2,10 000) | count 0; `state.day` DAY_2; total 0 |
| R2 | `rolloverMidSessionSplitsTimeAcrossDays` | enter(IG,0); tick 1 000/2 000/3 000; roll(DAY_2,3 500); tick(4 500) | secs(IG,DAY_1) 3; secs(IG,DAY_2) 1 (the 500 ms carry at the roll is dropped) |
| R3 | `rolloverMidSessionDoesNotRecountVideoOnScreen` | open(IG,0); watchNext(IG,1); roll(DAY_2,5 000); tick(6 000); tick(7 000) | count 0; no Counted after roll |
| R4 | `rolloverMidSessionNextNewVideoCountsOnNewDay` | R3 then item(IG,2,8 000); tick(8 400) | count 1; last Counted day DAY_2, countToday 1, same sessionId |
| R5 | `settledPendingAtRolloverCountsOnOldDay` | open(IG,0); item(IG,1,1 000); roll(DAY_2,1 400) | Counted(index 1, day DAY_1, countToday 2) emitted; then count 0 |
| R6 | `rolloverResetsSnoozes` | limits IG=5; snooze(IG,0); roll(DAY_2,1 000) | snoozesUsed(IG) 0; allowance 5 |
| R7 | `duplicateOrOlderDayRolledIsNoOp` | roll(DAY_1,0) → no effects; roll(DAY_2,1 000); open(IG,2 000); roll(DAY_2,3 000); roll(DAY_1,4 000) | count 1 on DAY_2; last two rolls emit nothing |
| R8 | `rolloverWhileOffScreenThenReturnAfterGraceCounts` | open(IG,0); leave(IG,1 000); roll(DAY_2,2 000); enter(IG,700 000,0); tick(700 400) | count 1; Counted.day DAY_2 |
| R9 | `rolloverWhileOffScreenReturnWithinGraceSameVideoNotRecounted` | open(IG,0); leave(IG,1 000); roll(DAY_2,2 000); enter(IG,3 000,0); tick(3 400) | count 0 |

**CounterMachineLimitTest**
| # | Name | Sequence | Expect |
|---|---|---|---|
| L1 | `noLimitByDefaultNeverNudges` | open(IG,0); watchNext(IG,99) | count 100; no nudges |
| L2 | `reachingLimitRequestsNudge` | limits IG=50; open(IG,0); watchNext(IG,49) | count 50; nudges == [Nudge(IG,50,2)]; in that transition Counted precedes Nudge |
| L3 | `belowLimitNoNudge` | limits IG=50; open; watchNext(48) | count 49; no nudges |
| L4 | `snoozeGrantsFiveMore` | limits IG=3; open; watchNext(2); snooze(IG); watchNext(4) | SnoozeAccepted(IG,DAY_1,1); count 7; nudges size 1 |
| L5 | `nudgeAgainWhenSnoozedAllowanceReached` | L4 then watchNext(1) | count 8; last nudge (IG,8,1) |
| L6 | `nudgeRepeatsForEveryVideoWhileOverAllowance` | limits IG=1; open; watchNext(2) | nudges counts [1,2,3] |
| L7 | `snoozeCapIsTwoPerDay` | limits IG=1; open; snooze ×3 | Accepted(1), Accepted(2), Rejected; snoozesUsed 2; allowance 11 |
| L8 | `snoozeWithoutLimitIsRejected` | snooze(IG,0) | [SnoozeRejected(IG)]; snoozesUsed 0 |
| L9 | `limitsChangedMidDayAppliesImmediately` | open; watchNext(9); setLimits(IG=12); watchNext(1); watchNext(1) | setLimits emits nothing; no nudge at 11; nudge at 12 |
| L10 | `lowerLimitBelowCurrentCountNudgesOnNextVideo` | open; watchNext(9); setLimits(IG=5); watchNext(1) | setLimits emits nothing; nudge (IG,11,2) |
| L11 | `removingLimitStopsNudges` | limits IG=1; open; setLimits(empty); watchNext(1) | nudges size 1 (only the first) |
| L12 | `limitsChangedKeepsSnoozes` | limits IG=1; open; snooze; setLimits(IG=2) | snoozesUsed 1; allowance 7 |
| L13 | `nonPositiveLimitsAreIgnored` | setLimits(IG=0, YT=−3) | `state.limits` empty |

**CounterMachineRestartTest** (service restart)
| # | Name | Sequence | Expect |
|---|---|---|---|
| X1 | `initialStateRestoresTodayCountsAndSnoozes` | Harness(todayCounts IG=4, todaySnoozes IG=2, limits IG=5); open(IG,0); snooze(IG) | count 5; no nudge (allowance 15); SnoozeRejected |
| X2 | `restartWithinGraceOfRestoredSessionDoesNotRecount` | Harness(todayCounts IG=10, restored IG=RestoredSession(111, 7, T0)); enter(IG,60 000,7); tick(60 400); item(IG,8,61 000); tick(61 400) | count 11; the only Counted has sessionId 111, index 8 |
| X3 | `restartAfterGraceOfRestoredSessionCountsVisibleVideo` | Harness(todayCounts IG=10, restored IG=RestoredSession(111,7,T0)); enter(IG,700 000,0); tick(700 400) | count 11; new sessionId ≠ 111 |
| X4 | `restartWithoutRestoreCountsVisibleVideoOnce` | Harness(todayCounts IG=10); enter(IG,0,7); tick(400); tick(5 000) | count 11 (documented one-off) |

**GameDayTest** (zone `Asia/Kolkata` unless stated, dayStart 04:00):
- G1 `beforeDayStartBelongsToPreviousDay`: 2026-11-02T03:59 local → 2026-11-01.
- G2 `exactlyAtDayStartIsNewDay`: 04:00 → 2026-11-02.
- G3 `customDayStartMidnight`: 00:00 start, 00:00 → same date.
- G4 `nextBoundaryLaterToday`: 2026-11-02T03:00 local → 2026-11-02T04:00 local.
- G5 `nextBoundaryTomorrow`: 10:00 → next day 04:00.
- G6 `nextBoundaryAtExactBoundaryIsNextDay`: 04:00 → next day 04:00.
- G7 `dstSpringForwardGapResolvesForward`: zone America/New_York, start 02:30, after 2026-03-08T01:00 local → 2026-03-08T03:30 EDT.
- G8 `dstFallBackOverlapUsesEarlierOffset`: zone America/New_York, start 01:30, after 2026-11-01T00:00 local → 2026-11-01T01:30-04:00.

**ItemPositionTrackerTest**:
- P1 `pagerSettledFromEqualsToBecomesIndex`: (3,3) → 3.
- P2 `pagerMidSwipeFromDiffersFromToIgnored`: (3,4) → null.
- P3 `pagerToIndexMissingAccepted`: (5,−1) → 5.
- P4 `scrollIndexUsesFromIndex`: (4,6) → 4.
- P5 `scrollFromOtherSourceIdIgnored`.
- P6 `emptySourceFilterAcceptsAnySource`.
- P7 `negativeFromIndexIgnored`.
- P8 `sameIndexReturnsNull`.
- P9 `childSwapFirstSignatureSeedsWithoutChange`.
- P10 `childSwapForwardAndBackward`: sigs A→B (+1 → 1), then scroll deltaY −500 at t, sig C at t+200 (→ 0), then a scroll deltaY −500 at t, sig D at t+1 500 (→ 1, the window expired so forward).
- P11 `childSwapNeverBelowZero` and `childSwapSameSignatureNull` as one test with two asserts.

**ScreenDetectorTest** (fixtures 1080×2400):
- D1 `matchesContainerIdWithCoverage`.
- D2 `smallContainerDoesNotMatch` (coverage 0.5).
- D3 `excludeIdBlocksMatch`.
- D4 `invisibleContainerDoesNotMatch`.
- D5 `unsupportedPackageNeverMatches`.
- D6 `nullNodesIsNotShortVideo`.
- D7 `childSignatureIsFullScreenChildKey`.
- D8 `structuralFallbackMatchesFullScreenPager` (unverified, no id match).
- D9 `structuralFallbackRejectsFeedWithSmallChildren` (children 0.4 coverage).
- D10 `structuralFallbackDisabledWhenVerified`.
- D11 `tiktokSecondPackageMatchesSameApp`.

**DetectorConfigParserTest**:
- J1 `parsesShippedAsset` (reads `android/src/main/assets/rc_detectors.json` via a relative path from the `core` project dir: `File("../android/src/main/assets/rc_detectors.json")`).
- J2 `unknownKeysIgnored`.
- J3 `unknownAppRejected`.
- J4 `duplicateAppRejected`.
- J5 `emptyContainerIdsRejected`.
- J6 `invalidViewIdRejectedWithPath` (message contains `apps[0].screens[0].containerIds[0]`).
- J7 `coverageOutOfRangeRejected` (0.0 and 1.1).
- J8 `wrongVersionRejected`.
- J9 `malformedJsonReturnsError`.
- J10 `fallbackDefaultsToNotVerified`.

**ScreenTrackerTest** (FakeNodeSource = `TreeNodeSource`):
- K1 `windowStateChangeOnContainerEmitsScreenEntered` (index 0).
- K2 `windowStateChangeAwayEmitsScreenLeft`.
- K3 `contentChangeWithin300msIsThrottledWithRecheck` (events empty, `recheckAt = first + 300`).
- K4 `contentChangeAfter300msEvaluates`.
- K5 `windowStateChangeIsNeverThrottled`.
- K6 `pagerScrollEmitsItemChanged`.
- K7 `scrollFromOtherSourceIgnored`.
- K8 `scrollWhileNotActiveEvaluatesThenEntersThenItem` (events `[ScreenEntered, ItemChanged]`).
- K9 `probeWithOtherForegroundEmitsScreenLeftThenTick`.
- K10 `probeWithNullForegroundEmitsScreenLeft`.
- K11 `probeAlwaysEndsWithTick`.
- K12 `screenOffEmitsScreenLeft`.
- K13 `screenOnOnShortVideoReenters`.
- K14 `childSwapSignatureChangeEmitsItemChanged`.
- K15 `switchBetweenSupportedAppsEmitsScreenEnteredForNew` (no ScreenLeft; the machine handles it).
- K16 `indexPersistsAcrossVisits` (enter, scroll to 4, leave, re-enter → `ScreenEntered.index == 4`).

**StatsCalculatorTest**:
- M1 `seriesZeroFillsMissingDays`.
- M2 `seriesHasExactlyNDaysEndingToday` (7 and 30).
- M3 `seriesPerAppBreakdown`.
- M4 `streakNullWithoutLimits`.
- M5 `streakCountsConsecutiveDaysWithinLimit`.
- M6 `streakBrokenByDayOverLimit`.
- M7 `streakIgnoresAppsWithoutLimit`.
- M8 `todayOverLimitStreakZero`.
- M9 `streakStopsAtFirstDay`.
- M10 `exactlyAtLimitIsWithin`.
- M11 `averageRoundsHalfUpAndIgnoresDaysBeforeFirstDay`.
- M12 `minutesFloor` (119 s → 1).

**ExportDocumentTest**:
- E1 `roundTrip`.
- E2 `matchesDocumentedExampleShape` (keys present, `format`, `version`).
- E3 `encodesDefaults`.

**ShortVideoAppTest**:
- A1 `fromPackageKnown`.
- A2 `fromPackageUnknownOrNull`.
- A3 `tiktokBothPackages`.
- A4 `allPackagesHasSix`.

**CoreIsAndroidFreeTest**: `noAndroidImportsInCoreMain`.

### 10.2 `:android` tests (JUnit4, Robolectric on SDK 35 via `src/test/resources/robolectric.properties`; Robolectric 4.17 fails on SDK 36 under JDK 25, same as the main app)
| Test | Asserts |
|---|---|
| `NoNodeTextAccessTest` | scans `android/src/main/java` and `android/src/debug/java` sources for the regex `\.(text|contentDescription|hintText|stateDescription|tooltipText|paneTitle|beforeText)\b|get(Text|ContentDescription|HintText|StateDescription|TooltipText|PaneTitle|BeforeText)\(` inside files under `service/`, `inspector/` and `overlay/` → none |
| `NoNetworkTest` | `android/src/main/AndroidManifest.xml` and `demo/src/main/AndroidManifest.xml` contain no `android.permission.INTERNET`; no source imports `java.net.`, `okhttp3`, `io.ktor` |
| `ServiceConfigTest` | parses `rc_accessibility_service.xml`: packageNames == `ShortVideoApp.allPackages`, the 3 event types, flags include `flagReportViewIds` |
| `DetectorAssetTest` | the asset parses `Ok`, has all 5 apps, all `verified = false`, every note contains `UNVERIFIED` |
| `EventAdapterTest` | Robolectric `AccessibilityEvent.obtain(type)` with packageName → the right `RawSignal`; unsupported package → null; scroll fields copied |
| `AccessibilityNodeSourceMappingTest` | `NodeSnapshotMapping.map(node, depth)` on Robolectric `AccessibilityNodeInfo.obtain()` copies id/class/bounds/flags; children capped |
| `ReelDaoTest` | in-memory Room: insert duplicate event → −1; ensure+increment; addSeconds; raiseSnoozes never lowers; observeRange ordering; lastEventPerPkg |
| `ReelRepositoryTest` | `recordReel` twice with the same Counted → count 1; restore maps sessions; unknown pkg skipped |
| `CounterRunnerTest` | real `ReelRepository` on in-memory Room, fake `OverlaySink`/`WidgetSink`, `FixedReelClock`, `StandardTestDispatcher` as `@CounterDispatcher`, signals fed with `TreeNodeSource`: window→enter→probe loop ticks; day guard rolls on a boundary crossing and never rolls back; time buffer flushes at 15 s and on leave |
| `EffectApplierTest` | each effect → the right collaborator call |
| `SettingsStoreTest` | defaults; limits round-trip; removing a limit removes the key; bubble position |
| `DetectorRepositoryTest` | override valid → used; override invalid → asset + error surfaced; reset deletes override |
| `BubblePositionTest` | snap left/right at the midline; y clamp; toPx/snap round trip |
| `OemAutostartTest` | manufacturer/brand strings → Oem (poco→XIAOMI, iqoo→VIVO, realme→OPPO, unknown→null); candidate order |
| `PermissionCheckerTest` | enabled-services string parsing (full and short component forms, colon-separated) |
| `ExportWriterTest` | writes the encoded document to a `ByteArrayOutputStream` sink |
| `StatsViewModelTest`, `LimitsViewModelTest`, `SettingsViewModelTest`, `SetupViewModelTest`, `StatusViewModelTest` | state per scenario listed in §7.5 (Turbine) |
| `BrandingExportTest` | skipped unless `rc.exportBranding=true`; writes the PNG, asserts 512×512 |

---

## 11. Phases (tick as you go)

All gate commands run from `reelcounter/` with `./gradlew` (Git Bash on Windows). Each phase: run the gate, fix until green, commit, summarise, **STOP**. Use the commit trailer from the session's attribution instructions.

### Phase 1 — Core engine
Commit: `feat(reelcounter): core counting engine`
- [x] Step 0: trim `reelcounter/local.properties` to only the `sdk.dir` line (not committed).
- [x] Edit `MachineHarness.kt` (§6.0).
- [x] Write every test file in §2.2 with every test in §10.1 (C1–C16 exist).
- [x] Create `NodeFixtures.kt`.
- [x] Implement every `:core` main file in §2.2 with the exact signatures in §3 and the algorithms in §4–§5.
- [x] Create `android/src/main/assets/rc_detectors.json` exactly as §5.2 (needed by J1; this file is inside `/reelcounter`).
- [x] `:android` and `:demo` still compile (`./gradlew :demo:assembleDebug` must also succeed; the skeleton is untouched otherwise).

Acceptance: all §10.1 tests exist with the listed names and pass. `CoreIsAndroidFreeTest` passes. No TODOs in `:core` main.
Gate: `./gradlew :core:test :demo:assembleDebug`

### Phase 2 — Detection + inspector + Room
Commit: `feat(reelcounter): accessibility detection, inspector and storage`
- [x] Catalog additions (§2.1). `android/build.gradle.kts`:
  - Plugins: `kotlin-serialization`, `ksp`, `hilt`, `room`.
  - `resourcePrefix = "rc_"`. Room schema dir.
  - Deps: core-ktx, lifecycle-runtime-ktx/compose/viewmodel-compose, activity-compose, compose bom/ui/material3/tooling-preview (debugImplementation tooling), navigation-compose, room runtime/ktx (+ksp compiler), datastore-preferences, work-runtime-ktx, glance-appwidget + glance-material3, hilt-android (+ksp hilt-compiler), hilt-lifecycle-viewmodel-compose, kotlinx-serialization-json, coroutines-android.
  - Tests: junit4, truth, turbine, robolectric, androidx-test-junit, androidx-test-core, coroutines-test, room-testing, work-testing.
- [x] `:demo`: hilt + ksp, `DemoApp`, manifest `android:name`, `MainActivity` `@AndroidEntryPoint`.
- [x] Files: `AndroidManifest.xml` (service only), `rc_accessibility_service.xml`, `rc_strings.xml` (service + Phase 2 strings), `di/*`, `time/SystemReelClock.kt`, `service/*` (all), `overlay/OverlayWindowHost.kt`, `overlay/OverlayController.kt` (debug chip only this phase; bubble/nudge are no-ops), `data/db/*`, `data/ReelRepository.kt`, `data/SettingsStore.kt`, `data/DetectorRepository.kt`, `inspector/InspectorHooks.kt`, debug + release inspector files, `ui/ReelRoutes.kt`, `ui/ReelCounterNavGraph.kt` (Today debug + Inspector only), `ui/today/TodayDebugScreen.kt` (plain text: each app count + seconds today, from `observeDay`), `ReelCounterEntry.kt`. Delete `ReelCounterRoot.kt` and update `MainActivity`.
- [x] Tests: `NoNodeTextAccessTest`, `NoNetworkTest`, `ServiceConfigTest`, `DetectorAssetTest`, `EventAdapterTest`, `AccessibilityNodeSourceMappingTest`, `ReelDaoTest`, `ReelRepositoryTest`, `CounterRunnerTest`, `EffectApplierTest`, `SettingsStoreTest`, `DetectorRepositoryTest`.
- [x] Inspector dump format (`NodeTreeDumper`), one line per node, 2 spaces of indent per depth:
  ```
  # pkg=com.instagram.android at=2026-11-01T12:00:00Z screen=1080x2400 detection=ShortVideo(INSTAGRAM, viaFallback=true, signal=SCROLL_INDEX)
  FrameLayout id=- bounds=0,0,1080,2400 scroll=false vis=true children=2
    androidx.recyclerview.widget.RecyclerView id=com.instagram.android:id/xyz bounds=0,0,1080,2400 scroll=true vis=true children=3
  # scroll log (last 50 for this package)
  12:00:01.250 src=com.instagram.android:id/xyz from=3 to=3 count=-1 dy=2400
  ```
  The file goes to `filesDir/rc_inspector/<APP>-<yyyyMMdd-HHmmss>.txt` (≤ 2 000 nodes, depth ≤ 60). The scroll log is an in-memory ring buffer of 50 entries per package, kept only while the inspector is enabled.

Acceptance: gate green. The APK installs. With the service enabled manually (Settings → Accessibility → Reel Counter), the TodayDebug screen shows counts rising. The debug chip shows match state and dumps a file.
Gate: `./gradlew :core:test :android:testDebugUnitTest :demo:assembleDebug`
After commit: give the user per-app inspector instructions:
1. Install with `./gradlew :demo:installDebug`.
2. Enable the service.
3. Open the Inspector and turn the chip on.
4. In each app, go to the short-video screen, swipe 3 videos, tap the chip; also tap it on that app's home feed.
5. Copy each file and paste it back.

Also document the alternative `adb shell uiautomator dump /sdcard/rc.xml && adb pull /sdcard/rc.xml`, piped through `sed -E 's/ (text|content-desc|hint)="[^"]*"//g'` before sharing. Note it often fails during video playback ("could not get idle state"); pause first.
Phone loop: update `rc_detectors.json` (set `verified: true` and the real ids/signals per app), add a `ScreenDetectorTest` fixture from each dump (ids only), re-run the gate, commit `fix(reelcounter): verified detector ids for <apps>`. Repeat until the user confirms counting is right. Then wait for "next".

### Phase 3 — Design system, icon, bubble
Commit: `feat(reelcounter): design system, icon and counter bubble`
- [ ] `designsystem/*` (tokens §7.1, type §7.2, dimens §7.3, theme, previews annotation, PreviewData).
- [ ] Components: ReelCard, HeroNumber, ReelButton, ProgressRing, CounterBubble, TodayPanel, AppMonogram, AppRow, DebugChip, Wordmark, all with previews.
- [ ] Icons §8: foreground/background/monochrome/mark vectors, adaptive mipmaps, line icons. `:demo` manifest uses `@mipmap/rc_ic_launcher`.
- [ ] `branding/rc_icon.svg`, `branding/rc_wordmark.svg`, `BrandingExportTest` + `rc.exportBranding` forwarding. Generate and commit `branding/rc_icon_512.png`.
- [ ] Overlay: `BubblePosition` (+ test), `BubbleOverlay`, `TodayPanelOverlay`, full `OverlayController` bubble/panel behaviour (drag, snap, persist, tap, long-press hide-for-session, auto-hide on leave, `bubble_enabled`).
- [ ] TodayDebug screen now wrapped in `ReelTheme`.

Acceptance: previews render for every state in §7.4 for these components. The bubble appears only on short-video screens, counts live, drags and snaps, remembers its position across a service restart, tap opens the panel, long-press hides it until a new session. The icon is legible at 48dp in preview.
Gate: `./gradlew :core:test :android:testDebugUnitTest :demo:assembleDebug`

### Phase 4 — Setup + status
Commit: `feat(reelcounter): setup flow and status screen`
- [ ] Components: StepDots, StatusRow, ReelTopBar, SettingRow (+ previews).
- [ ] `system/PermissionChecker.kt`, `system/SystemIntents.kt`, `system/OemAutostart.kt`.
- [ ] `ui/setup/*`, `ui/status/*`, nav graph start-destination logic, `ServiceStatus` heartbeat.
- [ ] Tests: `OemAutostartTest`, `PermissionCheckerTest`, `SetupViewModelTest`, `StatusViewModelTest`.

Acceptance: all setup steps and status rows from §7.5 exist with previews per state. Accessibility verification works on resume. The "service stopped" warning shows when the service is enabled in Settings but `ServiceStatus.connected` is false for > 5 s (unit-tested with a fake `ServiceStatus`, no device needed).
Gate: `./gradlew :core:test :android:testDebugUnitTest :demo:assembleDebug`

### Phase 5 — Limits, stats, widget, export
Commit: `feat(reelcounter): limits, stats, widget and export`
- [ ] Components: NudgeContent, BarChart, SegmentedToggle, LimitBar, LimitStepper (+ previews).
- [ ] `overlay/NudgeOverlay.kt` + OverlayController nudge wiring + `submitSnooze`.
- [ ] `ui/stats/*`, `ui/limits/*`, `ui/settings/*`; delete `ui/today/TodayDebugScreen.kt`; Stats becomes the start destination after setup.
- [ ] `widget/*` + manifest receiver + `rc_widget_info.xml`.
- [ ] `data/ExportWriter.kt` + SAF wiring in Settings.
- [ ] Tests: `StatsViewModelTest`, `LimitsViewModelTest`, `SettingsViewModelTest`, `ExportWriterTest`, `BubblePositionTest` (if not done).
- [ ] `README.md`: build, install, enabling the service, restricted-settings note, inspector use, uiautomator method, editing `rc_detectors.json` (asset vs on-phone override), export.
- [ ] `lint` passes with no errors (same `informational` overrides as `:demo` for `OldTargetApi`/`NewerVersionAvailable`).

Acceptance: every SPEC feature 4–8 works. Previews for every §7.4/§7.5 state. Phone test checklist delivered.
Gate: `./gradlew :core:test :android:testDebugUnitTest :demo:assembleDebug lint`
Then: do NOT merge. Say "Optional: switch to Opus and say review".

---

## 12. Risks and what only a real phone can verify

| # | Risk | Mitigation | Phone check |
|---|---|---|---|
| R1 | Real view ids are unknown, obfuscated, or absent (Compose/custom views) | structural fallback; inspector; override file | Phase 2 dumps per app |
| R2 | Apps rename ids on update | on-phone override without rebuild; Status shows which rules are active | re-dump after app updates |
| R3 | Scroll events missing, or `fromIndex` semantics differ (−1, first-visible vs page) | 3 signal types; scroll log in dumps | swipe 3 videos with the chip on, read the scroll log |
| R4 | The `packageNames` filter may hide other packages' windows from `windows`/`rootInActiveWindow` | leaving is detected by `Probe` (null/other foreground → leave) and the screen-off receiver | press Home mid-video; bubble hides within 1 s |
| R5 | OEM kills the service (MIUI, ColorOS, Funtouch) | autostart guide, battery step, "service stopped" warning | leave the phone overnight, check Status |
| R6 | Android 13+ "restricted settings" blocks enabling the service on sideloaded APKs | hint in Setup; `adb install` isn't restricted | install via file manager once |
| R7 | Battery: content-change floods during playback | 300 ms throttle, id lookups first, no ticks off-screen | 1 h of reels → Settings → Battery usage for Reel Counter |
| R8 | Accessibility-overlay touch and drag behaviour varies by OEM; nudge buttons | `FLAG_NOT_FOCUSABLE`, global HOME action | drag, tap, long-press, nudge on the user's phone |
| R9 | Undercount if an app reloads its feed (indices restart) within the 10 min grace | absolute indices usually persist; grace configurable in `CounterConfig` | leave reels 5 min, return, check new videos count |
| R10 | Comment sheets / share dialogs as separate windows | `NodeSource` searches all app windows of the package | open comments mid-video: bubble stays, no recount |
| R11 | YouTube Shorts shelf / Facebook inline reels in the home feed falsely matching | `minCoverage` 0.85, `excludeIds`, fallback child ≥ 0.85 | scroll the home feeds: no bubble |
| R12 | Instagram reels opened from DMs/profile use a different container | multiple `screens` per app | open a reel from a DM and from a profile |
| R13 | Snapchat Spotlight may emit no scroll events | CHILD_SWAP signal | swipe Spotlight 5×, count 5 (+1 first) |
| R14 | Time-zone change / manual clock change mid-day | day guard never rolls back; `GameDay` uses the current zone | — (accepted) |
| R15 | Play policy on AccessibilityService use (for the AWAKEN merge) | `isAccessibilityTool=false`, prominent disclosure (Intro screen) | — (Part C INTEGRATION note) |
| R16 | OEM autostart component names are unverified on the user's phone | ordered candidates + app-details + settings fallbacks | tap [Open autostart settings] |
| R17 | ≤ 15 s of time lost on a crash (buffered seconds) | accepted | — |
| R18 | YouTube Shorts counting. **Checked 2026-11-12 on an Android 17 emulator with YouTube 21.18: the old rule never counted a swipe.** YouTube sends scroll events with no index (-1) and marks its Shorts pages not important for accessibility, so the service saw `reel_recycler` with no children. Fixed: the service asks for `flagIncludeNotImportantViews` (still view ids only), YouTube uses CHILD_SWAP on `reel_recycler`, and the direction of a swipe is read from which page is on top while two are on screen (`PageHint`, from scroll events and looks; scroll amounts come in either sign for the same swipe). On the emulator: 0 direction errors in 8 moves, the count equals the furthest new video reached, going back and forward again counts nothing, and a swipe that YouTube refused (an ad) counts nothing | `ScreenDetector`, `ItemPositionTracker.onPages`, `rc_accessibility_service.xml` | **On your phone:** swipe 10 Shorts forward, 3 back, 3 forward: the count rises by 10 only. If it does not, open the inspector dump and send it |
| R19 | A same-app non-reel screen ends the visit after a 1 s miss | `TrackerConfig.missLeaveMillis` | **Checked on the emulator:** tapping YouTube's Home tab hid the bubble in about 1.1 s. Still check mid-swipe on a phone: the bubble must not flicker off |
| R20 | Testing caution: `uiautomator dump` (and anything using UiAutomation) briefly takes over accessibility, so Reel Counter sees the app leave and come back each time | — | Do not run UI dumps while testing counts; read `reelcounter.db` with `run-as` instead |

Only on a real phone: real ids/signals (R1, R3, R13), leave detection (R4), OEM killing and autostart (R5, R16), restricted settings (R6), battery (R7), overlay touch (R8), dialogs (R10), false matches (R11, R12).
