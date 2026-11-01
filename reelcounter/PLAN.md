# Reel Counter — PLAN

Branch `feature/reel-counter`, worktree `../awaken-reelcounter` (branched from AWAKEN `132a0d3`).
Everything lives under `/reelcounter/`. Nothing outside it is edited. Clean room: built from SPEC.md and
public Android docs only.

Conventions mirror AWAKEN so the modules can merge later: minSdk 28, targetSdk 36, compileSdk 37, Java 17,
AGP 9 built-in Kotlin (no `kotlin-android` plugin), Hilt, Room, DataStore, Compose M3, kotlinx.serialization,
`java.time`, injected `Clock`, no `!!`, no `GlobalScope`, no `runBlocking` outside tests, strings in
`strings.xml`, ≥48dp touch targets. `:core` uses JUnit5 + Truth. `:android` uses JUnit4 + Robolectric + Turbine.

---

## 1. Decisions to confirm

| # | Decision | Why |
|---|---|---|
| D1 | **The bubble and nudge are `TYPE_ACCESSIBILITY_OVERLAY` windows** drawn by the AccessibilityService, so the app does **not** ask for `SYSTEM_ALERT_WINDOW`. The setup step "overlay permission" turns into an automatic ✓ ("Overlay: handled by the accessibility service"). You approved this in the earlier plan. SPEC feature 9 still lists the step, so please confirm. | One less permission to grant, and some OEMs (MIUI) refuse it. The bubble can't outlive the service anyway, and an accessibility overlay is allowed over other apps by design. |
| D2 | `detectors.json` ships **placeholder** container ids marked `"verified": false` (UNVERIFIED), plus a **structural fallback**: a scrollable node covering ≥85% of the screen whose visible child is full-screen. Once an app has verified ids, its fallback is turned off. | Clean room: I don't ship ids I can't source myself. The fallback makes counting work roughly before your phone dumps arrive. |
| D3 | `daily_total` gets an extra `snoozes` column. `reel_event` gets `day` and `session_id`. | Snooze cap per day survives a process death. A unique `(pkg, session_id, item_index)` makes a replayed event a no-op. |
| D4 | Count unit = a settled **forward** move to an index higher than any already counted in this session. A fast fling that skips videos counts +1 per settled stop, not +N. | A skipped video was never really "brought into view". |

---

## 2. Files per module

```
reelcounter/
  settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew(.bat)  gradle/wrapper/*
  gradle/libs.versions.toml          # versions copied from AWAKEN root catalog (+ android-library plugin)
  SPEC.md  PLAN.md  README.md  INTEGRATION.md
  branding/  reelcounter-512.png  icon-source.svg  wordmark.svg
```

### `:core` — `com.assismohanty.awaken.reelcounter.core` (pure Kotlin/JVM)
```
core/src/main/kotlin/.../core/
  ShortVideoApp.kt          enum + package lookup
  model/NodeSnapshot.kt     Bounds, NodeSnapshot (ids/class/bounds/flags only — no text field exists)
  model/ScrollSignal.kt     abstract scroll/pager event data
  detect/DetectorConfig.kt  @Serializable schema of detectors.json
  detect/DetectorConfigParser.kt
  detect/ScreenDetector.kt  NodeSnapshot + rule → Detection
  detect/ItemPositionTracker.kt   turns pager/scroll/child-swap signals into an item index
  count/CounterEvent.kt     ScreenEntered, ItemChanged, ScreenLeft, Tick, DayRolled, Snoozed, LimitsChanged
  count/CounterEffect.kt    Counted, TimeAdded, NudgeRequested, SnoozeAccepted, SnoozeRejected, BubbleChanged
  count/CounterState.kt     immutable state (+ AppDayState, Session)
  count/CounterConfig.kt    settleMillis, resumeGraceMillis, maxTickGapMillis, snoozeSize, maxSnoozes
  count/CounterMachine.kt   reduce(state, event): Transition
  time/GameDay.kt           day key + next boundary from Instant, ZoneId, dayStart LocalTime
  limit/LimitPolicy.kt      per-app limits, allowance = limit + snoozes*5
  stats/StatsCalculator.kt  7/30-day series, under-limit streak, equivalent minutes
core/src/test/kotlin/.../core/
  CounterMachineCountingTest.kt  CounterMachineSessionTest.kt  CounterMachineTimeTest.kt
  CounterMachineRolloverTest.kt  CounterMachineLimitTest.kt
  ItemPositionTrackerTest.kt  ScreenDetectorTest.kt  DetectorConfigParserTest.kt
  GameDayTest.kt  StatsCalculatorTest.kt  ShortVideoAppTest.kt
  CoreIsAndroidFreeTest.kt   # scans core sources for `import android.` / `androidx.`
```

### `:android` — `com.assismohanty.awaken.reelcounter` (Android library)
```
android/src/main/
  AndroidManifest.xml            service (BIND_ACCESSIBILITY_SERVICE), widget receiver, no INTERNET
  assets/detectors.json
  res/xml/reel_accessibility_service.xml   packageNames, eventTypes, canRetrieveWindowContent, flagReportViewIds
  res/xml/reel_widget_info.xml
  res/drawable/ ic_reel_foreground.xml ic_reel_background.xml ic_reel_monochrome.xml wordmark.xml
  res/mipmap-anydpi/ic_reel_launcher.xml (+ _round)
  res/values/strings.xml  res/values-night/…
  java/.../reelcounter/
    di/ReelCounterModule.kt            Clock, dispatchers, DB, DataStore, detector config
    service/ReelAccessibilityService.kt
    service/NodeSnapshotMapper.kt      AccessibilityNodeInfo → NodeSnapshot (never touches text/contentDescription)
    service/EventAdapter.kt            AccessibilityEvent → core events, throttling, Tick loop, day boundary
    service/CounterRunner.kt           owns CounterState, applies effects (DB, overlay, widget) on one dispatcher
    service/ServiceHeartbeat.kt        alive marker for "service killed" warning
    overlay/OverlayWindowHost.kt       ComposeView in an accessibility overlay + lifecycle/savedstate owner
    overlay/Bubble.kt  overlay/BubbleController.kt  overlay/NudgeOverlay.kt
    data/db/ReelDatabase.kt  data/db/ReelEventEntity.kt  data/db/DailyTotalEntity.kt  data/db/ReelDao.kt
    data/ReelRepository.kt             one transaction per effect batch
    data/SettingsStore.kt              DataStore: dayStart, limits, bubble position, setup done, inspector flag
    data/DetectorRepository.kt         asset + optional filesDir override
    data/ExportJson.kt
    designsystem/ReelTokens.kt  ReelTheme.kt  ReelType.kt  components/{ReelCard,HeroNumber,BarChart,StatusRow,ReelButton,ProgressRing}.kt
    ui/ReelCounterNavGraph.kt          host entry point (used by :demo, later by AWAKEN)
    ui/stats/{StatsScreen,StatsViewModel}.kt
    ui/limits/{LimitsScreen,LimitsViewModel}.kt
    ui/setup/{SetupScreen,SetupViewModel}.kt
    ui/status/{StatusScreen,StatusViewModel}.kt
    ui/PermissionChecker.kt  oem/OemAutostart.kt
    widget/{ReelWidget,ReelWidgetReceiver,WidgetRefresher,DayStartWidgetWorker}.kt
    inspector/InspectorEntry.kt        interface; debug impl / release no-op
  src/debug/java/.../inspector/  DetectorInspector.kt  InspectorScreen.kt  NodeTreeDumper.kt
  src/release/java/.../inspector/ NoOpInspector.kt
android/src/test/  NodeSnapshotMapperTest  EventAdapterTest  ReelDaoTest  ReelRepositoryTest
                   DetectorAssetTest  SettingsStoreTest  OemAutostartTest  *ViewModelTest  ExportJsonTest
                   BrandingExport (only with -PexportBranding: writes branding/reelcounter-512.png)
```

### `:demo` — app id `com.assismohanty.awaken.reelcounter.demo`
```
demo/src/main/AndroidManifest.xml  demo/src/main/java/.../demo/{DemoApp.kt (@HiltAndroidApp), MainActivity.kt}
```

---

## 3. `:core` public API

```kotlin
enum class ShortVideoApp(val packages: Set<String>) {
    INSTAGRAM(setOf("com.instagram.android")),
    YOUTUBE(setOf("com.google.android.youtube")),
    TIKTOK(setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    FACEBOOK(setOf("com.facebook.katana")),
    SNAPCHAT(setOf("com.snapchat.android"));
    companion object { fun fromPackage(pkg: String): ShortVideoApp?; val allPackages: Set<String> }
}

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) { val width; val height; fun coverage(screen: Bounds): Double }
data class NodeSnapshot(                     // deliberately has NO text / contentDescription
    val viewId: String?, val className: String?, val bounds: Bounds,
    val scrollable: Boolean, val visible: Boolean, val children: List<NodeSnapshot>,
)
data class ScrollSignal(val sourceViewId: String?, val fromIndex: Int, val toIndex: Int,
                        val itemCount: Int, val scrollDeltaY: Int)

// detectors.json
@Serializable data class DetectorConfig(val version: Int, val apps: List<AppDetector>)
@Serializable data class AppDetector(val app: ShortVideoApp, val verified: Boolean,
    val screens: List<ScreenRule>, val structuralFallback: Boolean = !verified)
@Serializable data class ScreenRule(val containerIds: List<String>, val excludeIds: List<String> = emptyList(),
    val signal: ItemSignal, val signalSourceIds: List<String> = emptyList(), val minCoverage: Double = 0.85)
@Serializable enum class ItemSignal { SCROLL_INDEX, PAGER_PAGE, CHILD_SWAP }
object DetectorConfigParser { fun parse(json: String): ParseResult }   // Ok(config) | Error(message)

sealed interface Detection {
    data object NotShortVideo : Detection
    data class ShortVideo(val app: ShortVideoApp, val rule: ScreenRule, val viaFallback: Boolean) : Detection
}
class ScreenDetector(config: DetectorConfig) {
    fun detect(packageName: String, root: NodeSnapshot?, screen: Bounds): Detection
}
class ItemPositionTracker {               // one per short-video screen visit
    fun onSignal(rule: ScreenRule, scroll: ScrollSignal?, visibleChildSignature: String?): Int?  // new index or null
    fun reset()
}

// counting
sealed interface CounterEvent { val at: Instant
    data class ScreenEntered(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class ItemChanged(val app: ShortVideoApp, val index: Int, override val at: Instant) : CounterEvent
    data class ScreenLeft(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class Tick(override val at: Instant) : CounterEvent
    data class DayRolled(val newDay: LocalDate, override val at: Instant) : CounterEvent
    data class Snoozed(val app: ShortVideoApp, override val at: Instant) : CounterEvent
    data class LimitsChanged(val limits: Map<ShortVideoApp, Int>, override val at: Instant) : CounterEvent
}
sealed interface CounterEffect {
    data class Counted(val app: ShortVideoApp, val day: LocalDate, val sessionId: Long, val index: Int,
                       val at: Instant, val countToday: Int) : CounterEffect
    data class TimeAdded(val app: ShortVideoApp, val day: LocalDate, val millis: Long) : CounterEffect
    data class NudgeRequested(val app: ShortVideoApp, val countToday: Int, val snoozesLeft: Int) : CounterEffect
    data class SnoozeAccepted(val app: ShortVideoApp, val day: LocalDate, val snoozesUsed: Int) : CounterEffect
    data class SnoozeRejected(val app: ShortVideoApp) : CounterEffect
    data class BubbleChanged(val app: ShortVideoApp?) : CounterEffect   // null = hide
}
data class CounterConfig(val settleMillis: Long = 350, val resumeGraceMillis: Long = 600_000,
    val maxTickGapMillis: Long = 5_000, val snoozeSize: Int = 5, val maxSnoozes: Int = 2)
data class CounterState(...) { companion object {
    fun initial(day: LocalDate, todayCounts: Map<ShortVideoApp, Int>, todaySnoozes: Map<ShortVideoApp, Int>,
                limits: Map<ShortVideoApp, Int>): CounterState } }
data class Transition(val state: CounterState, val effects: List<CounterEffect>)
class CounterMachine(private val config: CounterConfig = CounterConfig()) {
    fun reduce(state: CounterState, event: CounterEvent): Transition
}

class GameDay(private val zone: ZoneId, private val dayStart: LocalTime = LocalTime.of(4, 0)) {
    fun dayOf(at: Instant): LocalDate
    fun nextBoundary(after: Instant): Instant
}
object LimitPolicy { fun allowance(limit: Int?, snoozesUsed: Int, snoozeSize: Int): Int? }
data class DayTotal(val day: LocalDate, val app: ShortVideoApp, val count: Int, val seconds: Long)
object StatsCalculator {
    fun series(totals: List<DayTotal>, today: LocalDate, days: Int): List<DayBar>   // oldest → today, zero-filled
    fun underLimitStreak(totals: List<DayTotal>, limits: Map<ShortVideoApp, Int>, today: LocalDate, firstDay: LocalDate): Int
    fun equivalentMinutes(totals: List<DayTotal>, day: LocalDate): Long
}
```

**Machine rules.**
- Each app keeps a session `{id, highestCounted, pending(index, since), leftAt}`. `ItemChanged` sets `pending`. A pending index is **committed** by any later event when `at - since ≥ settleMillis` and it is still the latest index. A commit with `index > highestCounted` emits `Counted`. Anything else (lower, equal, or replaced before it settles) is dropped.
- The first item on `ScreenEntered` counts once it settles (index 0, or the first index seen).
- On `ScreenLeft`, the session is kept with `leftAt`. `ScreenEntered` within `resumeGraceMillis` resumes it. Later than that starts a new session with a new id.
- Time: while on screen, each event adds `min(at - lastAt, maxTickGapMillis)` to the current app/day. Totals are flushed as whole seconds.
- `DayRolled` first flushes time up to `at` into the old day, then resets counts and snoozes to 0 for the new day. An open session carries over: the video on screen is not recounted, and the next new one is +1 on the new day.
- Limit: after a `Counted`, if `countToday ≥ allowance`, emit `NudgeRequested`. `Snoozed` with `used < maxSnoozes` emits `SnoozeAccepted` (allowance += 5). Otherwise it emits `SnoozeRejected` and the nudge stays.

---

## 4. `detectors.json` schema + example

```json
{
  "version": 1,
  "apps": [
    {
      "app": "INSTAGRAM",
      "verified": false,
      "structuralFallback": true,
      "screens": [
        {
          "containerIds": ["com.instagram.android:id/UNVERIFIED_reels_pager"],
          "excludeIds": [],
          "signal": "PAGER_PAGE",
          "signalSourceIds": ["com.instagram.android:id/UNVERIFIED_reels_pager"],
          "minCoverage": 0.85
        }
      ]
    },
    { "app": "YOUTUBE",  "verified": false, "screens": [ { "containerIds": ["com.google.android.youtube:id/UNVERIFIED_shorts_container"], "signal": "SCROLL_INDEX" } ] },
    { "app": "TIKTOK",   "verified": false, "screens": [ { "containerIds": ["com.zhiliaoapp.musically:id/UNVERIFIED_feed_pager"], "signal": "PAGER_PAGE" } ] },
    { "app": "FACEBOOK", "verified": false, "screens": [ { "containerIds": ["com.facebook.katana:id/UNVERIFIED_reels_list"], "signal": "SCROLL_INDEX" } ] },
    { "app": "SNAPCHAT", "verified": false, "screens": [ { "containerIds": ["com.snapchat.android:id/UNVERIFIED_spotlight"], "signal": "CHILD_SWAP" } ] }
  ]
}
```
- A screen matches when any `containerId` is present, visible, and covers ≥ `minCoverage` of the screen, and no `excludeId` is visible. Unverified apps also try the structural fallback.
- `PAGER_PAGE` / `SCROLL_INDEX` use `fromIndex` of a `TYPE_VIEW_SCROLLED` whose source id is in `signalSourceIds` (any source if the list is empty). `CHILD_SWAP` uses the sign of `scrollDeltaY` plus a change in the full-screen child's bounds signature, and moves a virtual cursor ±1.
- Load order: `filesDir/detectors.json` (an on-phone override, written from the inspector) → `assets/detectors.json`. A parse error falls back to the asset and is shown on the Status screen.

---

## 5. Room schema (`ReelDatabase` v1, exported schema JSON)

```
reel_event
  id           INTEGER PK AUTOINCREMENT
  pkg          TEXT    NOT NULL           -- package name
  ts           INTEGER NOT NULL           -- epoch millis
  item_index   INTEGER NOT NULL
  day          TEXT    NOT NULL           -- ISO game day (D3)
  session_id   INTEGER NOT NULL           -- (D3)
  UNIQUE(pkg, session_id, item_index)     -- replay = no-op (INSERT OR IGNORE)
  INDEX(day)

daily_total
  date     TEXT    NOT NULL               -- ISO game day
  pkg      TEXT    NOT NULL
  count    INTEGER NOT NULL DEFAULT 0
  seconds  INTEGER NOT NULL DEFAULT 0
  snoozes  INTEGER NOT NULL DEFAULT 0     -- (D3)
  PRIMARY KEY(date, pkg)
```
`daily_total` is updated in the **same transaction** as the `reel_event` insert, and only if the insert actually added a row. It can be rebuilt from `reel_event` plus time. Export JSON = `{ exportedAt, dayStart, limits, dailyTotals[], events[] }`.

---

## 6. Counting edge cases → tests (`:core`, written first in Phase 1)

**CounterMachineCountingTest**
1. `firstVideoCountsAfterSettle`
2. `itemNotCountedBeforeSettleWindow`
3. `rapidDuplicateItemChangedSameIndexCountsOnce`
4. `burstOfEventsWithinSettleCountsOnce`
5. `partialSwipeThatBouncesBackDoesNotCount` (0→1→0 within settle)
6. `fullSwipeForwardCountsPlusOne`
7. `scrollBackDoesNotRecount`
8. `scrollBackThenForwardToSeenVideoDoesNotRecount`
9. `scrollBackThenPastHighestCountsOnlyNewVideo`
10. `fastFlingSkippingIndicesCountsOncePerSettledStop`
11. `tickAloneDoesNotCount`
12. `contentChangesWithoutIndexChange_likeCommentPause_doNotCount` (repeated ItemChanged with the same index)
13. `itemChangedForOtherAppWhileOnScreenIsIgnored`
14. `itemChangedWhenNotOnShortVideoScreenIsIgnored`
15. `countsArePerApp`
16. `countedEffectCarriesSessionIdIndexAndRunningTotal`

**CounterMachineSessionTest**
17. `leaveAndReturnWithinGraceToSameVideoDoesNotRecount`
18. `leaveAndReturnWithinGraceThenNewVideoCounts`
19. `returnAfterGraceStartsNewSessionAndCountsFirstVideo`
20. `newSessionGetsNewSessionId`
21. `switchingAppsKeepsEachAppsSessionSeparately`
22. `pendingItemIsCommittedOnScreenLeftIfSettled`
23. `pendingItemIsDroppedOnScreenLeftIfNotSettled`
24. `duplicateScreenEnteredIsIdempotent`
25. `screenLeftWithoutEnterIsNoOp`
26. `bubbleShownOnEnterAndHiddenOnLeave`

**CounterMachineTimeTest**
27. `timeAccumulatesFromTicksWhileOnScreen`
28. `noTimeAccumulatesOffScreen`
29. `tickGapLargerThanCapIsClamped` (screen off / doze)
30. `timeFlushedOnScreenLeft`
31. `timeAttributedToCorrectAppWhenSwitching`
32. `outOfOrderEventWithEarlierTimestampAddsNoTime`

**CounterMachineRolloverTest**
33. `dayRolledResetsCountsToZero`
34. `rolloverMidSessionSplitsTimeAcrossDays`
35. `rolloverMidSessionDoesNotRecountVideoOnScreen`
36. `rolloverMidSessionNextNewVideoCountsOnNewDay`
37. `rolloverResetsSnoozes`
38. `duplicateDayRolledForSameDayIsNoOp`
39. `rolloverWhileOffScreenThenEnterCountsOnNewDay`

**CounterMachineLimitTest**
40. `noLimitByDefaultNeverNudges`
41. `reachingLimitRequestsNudge` (50th video with limit 50 → `NudgeRequested(count=50)`)
42. `belowLimitNoNudge`
43. `snoozeGrantsFiveMore`
44. `nudgeAgainAfterSnoozedAllowanceUsed`
45. `snoozeCapIsTwoPerDay` (third → `SnoozeRejected`)
46. `snoozeWithoutLimitIsRejected`
47. `limitsChangedMidDayAppliesImmediately`
48. `lowerLimitBelowCurrentCountNudgesOnNextVideo`
49. `initialStateRestoresTodayCountsAndSnoozes` (process restart)

**GameDayTest**: `beforeDayStartBelongsToPreviousDay`, `exactlyAtDayStartIsNewDay`, `customDayStartMidnight`, `nextBoundaryIsTodayOrTomorrow`, `dstSpringForwardBoundary`, `dstFallBackBoundary`.

**ItemPositionTrackerTest**: `pagerFromIndexBecomesIndex`, `scrollIndexIgnoresOtherSourceIds`, `childSwapForwardIncrementsCursor`, `childSwapBackwardDecrementsCursor`, `childSwapSameSignatureNoChange`, `missingSignalReturnsNull`, `resetStartsAtZero`.

**ScreenDetectorTest**: `matchesContainerIdWithCoverage`, `smallContainerDoesNotMatch`, `excludeIdBlocksMatch`, `invisibleContainerDoesNotMatch`, `unsupportedPackageNeverMatches`, `structuralFallbackMatchesFullScreenPager`, `structuralFallbackDisabledWhenVerified`, `nullRootIsNotShortVideo`.

**DetectorConfigParserTest**: `parsesExample`, `unknownKeysIgnored`, `unknownAppRejected`, `emptyContainerIdsRejected`, `coverageOutOfRangeRejected`, `malformedJsonReturnsError`.

**StatsCalculatorTest**: `seriesZeroFillsMissingDays`, `seriesLength7And30`, `streakCountsConsecutiveDaysUnderLimit`, `streakBrokenByDayOverLimit`, `streakIgnoresAppsWithoutLimit`, `todayUnderLimitExtendsStreak`, `streakStopsAtFirstDay`, `equivalentMinutesRoundsDown`.

`CoreIsAndroidFreeTest`: no `android.`/`androidx.` imports in `:core`.

---

## 7. Detection pipeline (`:android`, Phase 2)

1. The service config is restricted to supported packages and the three event types, with `flagReportViewIds` and `notificationTimeout=100`.
2. `EventAdapter`: on `WINDOW_STATE_CHANGED` or a throttled `WINDOW_CONTENT_CHANGED` (≤ 1 per 300 ms), take `rootInActiveWindow`, map it to a `NodeSnapshot` with a depth/node cap (40 levels / 400 nodes) → `ScreenDetector`. A change in match emits `ScreenEntered`/`ScreenLeft`. `VIEW_SCROLLED` → `ScrollSignal` → `ItemPositionTracker` → `ItemChanged`. A 1 s Tick runs only while on a short-video screen. Screen-off (`ACTION_SCREEN_OFF` receiver) and leaving the package → `ScreenLeft`. The day boundary is scheduled from `GameDay.nextBoundary` → `DayRolled`.
3. `NodeSnapshotMapper` reads only `viewIdResourceName`, `className`, `getBoundsInScreen`, `isScrollable`, `isVisibleToUser`, children. A Robolectric test asserts `getText`/`getContentDescription` are never called (mock that throws). Nothing node-related is logged.
4. `CounterRunner` serialises everything on one `limitedParallelism(1)` dispatcher and persists effects through `ReelRepository`.

## 8. Phase gates

| Phase | Model | Gate |
|---|---|---|
| 1 Core | Opus | `./gradlew :core:test` (skeleton: all 3 modules configure/compile) |
| 2 Detection + inspector | Sonnet | `:android:testDebugUnitTest :demo:assembleDebug`, then phone loop on dumps |
| 3 Design, icon, bubble | Sonnet | same + previews |
| 4 Setup + status | Sonnet | same |
| 5 Limits, stats, widget, export | Sonnet | same |
| 6 Review + integration | Opus | `:core:test :android:testDebugUnitTest :demo:assembleDebug lint` |
