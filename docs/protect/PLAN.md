# Focus protection: plan

Status: **APPROVED 2026-11-10** (user: "Accept all recommendations"). Every PROPOSED number below is approved as
written, and every question in §11 is answered with its Recommended column. Where the text says "PROPOSED" or
"if Q1 = (a)", read it as decided. Part B may start at Batch 1.

In short: Focus Lock moves onto the watcher and `FocusLockService` is deleted (this replaces the 2026-11-03 decision in
`RELEASE_PREP_PLAN.md` Batch 1); the build flag is built and takes Reel Counter out of the Play build; Lockdown pays
× 1.00; 3a is built after real ids are captured; 3b is built only if the inspector finds a stable view id for the
channel name (otherwise stop and write `QUESTIONS.md`); **3c is not built**.
Branch: `feature/focus-protection` (cut from `feat/plates` at `69d5514f`, which is also `main`).

Three features, plain names everywhere in UI and code:

1. **Pause**: a short breathing delay before a chosen app opens.
2. **Lockdown**: a focus session with no early end, where only up to 3 chosen apps open.
3. **Strict+**: accessibility-based extras (short-video blocking, YouTube Study mode, uninstall protection).

Every number marked **PROPOSED** is a proposal. On approval, Batch 1 copies the approved numbers into
`GAME_DESIGN.md` §19 before any code uses them (§9 of this plan holds the draft text).

---

## 0. What the repo actually has (read this first)

The brief assumes some things that are not true on this branch. Each one changes the plan, so each has a decision in §11.

| # | The brief says | The repo has | Effect |
|---|---|---|---|
| R1 | Focus Lock is a usage-stats poller plus an overlay block screen | `core/lock/FocusLockService` is an **AccessibilityService**. It listens to window-change events and draws a `TYPE_ACCESSIBILITY_OVERLAY`. There is no poller, no foreground service for it, and no `SYSTEM_ALERT_WINDOW`. On 2026-11-03 the user decided to keep it that way (`RELEASE_PREP_PLAN.md` Batch 1: "Do NOT replace it with UsageStats polling ... do NOT add `SYSTEM_ALERT_WINDOW`") | The poller and the overlay permission must be **built**, not reused. Q1 |
| R2 | The app has exactly one AccessibilityService | There are **two**: `FocusLockService` (`:app`) and `ReelAccessibilityService` (`:reelcounter-android`) | Getting to one means moving Focus Lock off accessibility. Q1 |
| R3 | Accessibility features sit behind the Reel Counter build flag, off in the Play build | **No such flag exists.** `:reelcounter-android` is a plain `implementation` dependency; the release manifest ships both accessibility services. `PLAY_STORE_LAUNCH.md` §5c already drafts an accessibility declaration for Focus Lock | The flag must be built. Q2 |
| R4 | Reuse `detectors.json` | The file is `rc_detectors.json`, and **every entry is `"verified": false` with placeholder ids** | Strict+ 3a cannot work until ids are captured on a phone with the existing inspector |
| R5 | Reuse Focus Session persistence | Gate runs are timestamps in Room plus a notification, **no foreground service**, and they collapse on reboot (`GateCollapseReason.DEVICE_RESTARTED`) | Lockdown needs its own row, its own service and an exemption from that collapse |
| R6 | Reuse the block screen | The Focus Lock cover has **[End session and open]** | Lockdown reuses the composable without that button |

What does exist and is reused as is: `FocusLockRules` (never-block list, payment list, `decide()`), `AlwaysAllowed`
(launcher, IME, dialer, SMS, UPI handlers, tap-to-pay resolved at runtime), `BlockOverlay` (Compose-in-a-window host),
`UsageGateway` (usage events, fail closed), `AppKind`/`AppCategory` (Screen Time), `FocusAccessScreen` (permission and
battery status rows), `batteryGuide(oem)` and `OemSettingsTargets`, `StartGateUseCase`/`GateRunController`, the Reel
inspector, `ScreenDetector`.

---

## 1. Shared base: the watcher (new)

Pause and Lockdown both need "which app is on top right now" without accessibility.

### 1.1 `ProtectService` (foreground service, `core/protect`)

- One service for both features. It runs only while **Pause is on with at least one target app and inside active
  hours**, or **a Lockdown session is RUNNING**. Otherwise it stops itself.
- `foregroundServiceType="specialUse"`, like `RestTimerService`. Subtype text for Play (PROPOSED wording):
  "Runs while the user has switched on Pause or started a Lockdown session: notices when a chosen app comes to the
  front so the app can show the user's own pause or block screen."
- Ongoing notification on a new low-importance channel `protect`. Pause: "Pause is on" with a [Turn off] action.
  Lockdown: "Lockdown · ends 14:32" (system-drawn countdown, as gates do), no action.
- Foreground detection: `UsageStatsManager.queryEvents(lastSeen, now)`, keep the newest `ACTIVITY_RESUMED` package.
  This goes into `UsageGateway` as one new function `foreground(since): ForegroundRead` with the same
  `NoAccess / Unavailable / data` shape as the other reads.
- Phone call: skip all overlays while `AudioManager.mode` is `MODE_RINGTONE`, `MODE_IN_CALL` or
  `MODE_IN_COMMUNICATION` (needs no permission). Keyguard locked: no overlay (same check Focus Lock uses).

### 1.2 Poll interval (all PROPOSED, tuned by the measurement in §1.5)

| State | Interval |
|---|---|
| Screen off, or keyguard locked | **no polling**. The loop is cancelled on `ACTION_SCREEN_OFF` and restarted on `ACTION_USER_PRESENT` |
| Fast: the 15 s after screen-on or after any foreground change, and while a launcher is on top | 400 ms |
| Steady: the same non-target app on top for more than 15 s | 1,500 ms |
| Inside a target app during its cooldown | 1,500 ms |
| Lockdown RUNNING, screen on | 400 ms always |

Honest limit: a poller is slower than an accessibility event. The chosen app can show for up to one interval
(about half a second) before the pause or block screen covers it. Focus Lock today has no such gap.

### 1.3 Overlay

- New permission `SYSTEM_ALERT_WINDOW` ("Display over other apps"), granted on the system settings page.
- `BlockOverlay` gets one constructor parameter for the window type (`TYPE_ACCESSIBILITY_OVERLAY` today,
  `TYPE_APPLICATION_OVERLAY` for the watcher). No second overlay class.
- [Not now] and [Back to session] need to start an activity from the background (HOME, or `MainActivity`). An app
  holding the overlay permission is allowed to do that. Xiaomi/POCO also needs its own "Display pop-up windows while
  running in the background" switch: one more row on the status screen, shown for `OemFamily.XIAOMI` only.

### 1.4 Never covered (Pause and Lockdown)

`FocusLockRules.neverBlocked()` and `AlwaysAllowed.current()` + `paymentApps()` already cover dialer, in-call, SMS,
emergency, SystemUI, Settings, package installer, launcher, IME, UPI/bank and tap-to-pay. Added for the watcher:

- no overlay during a call (§1.1), on the keyguard, or while the foreground package is the permission controller
  (resolved with `PackageManager.getPermissionControllerPackageName()`);
- any **system app with no launcher icon** is never covered (PROPOSED rule, §2.4). This catches OEM permission
  dialogs, share sheets and resolvers without naming each one.

### 1.5 Battery: design and phone measurement plan

Design: no work with the screen off; adaptive interval; service not running at all when no target is set, outside
active hours (an inexact alarm restarts it at the next window start), or when the feature is off; one `queryEvents`
call per tick over a window of a few seconds; no wake lock.

Measurement (the user runs this on their phone; results go in `docs/protect/BATTERY.md`):

1. Charge to 100 %, unplug, `adb shell dumpsys batterystats --reset`.
2. Baseline day: Pause off, normal use. Record `adb shell dumpsys batterystats com.assismohanty.awaken` (computed
   drain, CPU time) and Settings → Battery → AWAKEN %.
3. Pause day: 5 target apps, normal use, same readings.
4. Idle test: screen off for 2 h with Pause on. Expected CPU time delta: about zero.
5. Pass (PROPOSED): AWAKEN's share of drain rises by **no more than 2 percentage points** over a day, and the idle
   test shows no wake-ups from the service. If it fails: raise the steady interval first, then the fast one.

---

## 2. Mindful pause

### 2.1 Behaviour

- **Targets.** The user picks apps from the Screen Time app list (`UsageGateway.launcherApps()`, with `AppKind`).
  Suggested (pre-ticked nothing, shown first when installed): Instagram, YouTube, TikTok, Facebook, Snapchat, X, Reddit.
  Payment and never-block apps cannot be picked.
- **Trigger.** A target app comes to the front and the last *real* app on top was a different package. Packages in
  the never-block set (keyboard, SystemUI, permission dialog, our own overlay) do not count as "a different package",
  so coming back from the keyboard does not pause again.
- **Screen.** Full-screen overlay: breathing ring, countdown, "Opened 4 times today", one line from the bank,
  [Not now]. Back key = Not now. When the countdown ends the overlay goes away by itself; with "Require a tap to
  continue" on, a [Continue to Instagram] button appears instead.
- **[Not now]** goes HOME at any time and records NOT_NOW. No cooldown starts.
- **Cooldown.** After a continue, that app is not paused again for N minutes, even if the user leaves and returns.
- **Progressive delay.** The countdown grows with today's pauses for that app.
- **Intention prompt (optional, off by default).** "What for?" chips shown under the line; a chip tap is stored with
  the event. Skipping is allowed.
- **Active hours (optional).** One daily time window and a set of weekdays. Outside it the service does not run.
- **Never fires** while a Lockdown is RUNNING, or while a Focus Gate is running with Focus Lock on (those cover the app
  themselves).
- **No XP, no gold, no achievement**, for pausing, for Not now, or for anything else here.

### 2.2 Numbers (all PROPOSED)

| # | Rule | Proposed |
|---|---|---|
| PZ1 | Base delay | **3 s** (setting: 3 / 5 / 10 s) |
| PZ2 | Progressive delay | delay for the n-th pause of that app today = `min(base + 2 s × (n − 1), 15 s)`. With base 3: 3, 5, 7, 9, 11, 13, 15, 15 … Setting to switch it off (default on) |
| PZ3 | Cooldown after continue | **5 min** per app (setting: 1 / 5 / 10 / 15). No pause while `now < continuedAt + cooldown` |
| PZ4 | After Not now | no cooldown; the next open pauses again and counts as the next n |
| PZ5 | "Today" | the game-day (`dayStartHour`), per app |
| PZ6 | Target apps | at most **12** |
| PZ7 | Intention chips | 5 fixed: Reply to someone · Look something up · Post something · Take a break · No reason |
| PZ8 | Active hours | default: all day, every day. One window `[start, end)`, may cross midnight; a window that crosses midnight belongs to the weekday it started on |
| PZ9 | Stats | per app, last **7** game-days: pauses, Not now count. Rows older than 8 game-days are deleted at rollover |
| PZ10 | Line bank | **40** bundled lines; the line for a pause is `lines[(gameDayIndex + n) mod 40]`, so it is deterministic and does not repeat within a day |
| PZ11 | Require a tap to continue | default **off** |

"Opened N times today" counts pauses shown (n), which is the number of times the app was opened outside a cooldown.

### 2.3 Line bank rules

Reflective questions and plain observations. Never shaming, never a health or science claim stated as fact (no
"dopamine", no "addiction"). Examples of the register: "What did you come here for?", "Is this the thing you meant to
do?", "One breath, then choose." The 40 lines live in `strings.xml` as a string-array, go through the Lexicon, and
must pass `LexiconResourcesTest`. The user reviews the full list in the Batch 2 summary.

### 2.4 Pipeline: variants

```kotlin
// :engine
sealed interface PauseVariant {
    data class Breathing(val seconds: Int) : PauseVariant
    data class Line(val index: Int) : PauseVariant
    data object Intention : PauseVariant
}
data class PausePlan(val n: Int, val variants: List<PauseVariant>)
sealed interface PauseDecision { data object None : PauseDecision; data class Show(val plan: PausePlan) : PauseDecision }
```

`PauseRules.decide(pkg, previousPkg, now, config, appState, lockdownRunning, lockCovering)` is pure. The overlay
renders the variant list in order with one `when`. A later "mascot call" is one more subclass and one more branch;
nothing for it is built now.

### 2.5 Data

- Settings (targets, PZ1/PZ3 choices, toggles, active hours): DataStore, a new `PauseStore`.
- Events: Room table `pause_event` in `AwakenDatabase` (schema 29 → 30): `id`, `package_name`, `game_day`, `at`,
  `outcome` (CONTINUED / NOT_NOW), `delay_seconds`, `intention` (nullable enum name). Not a ledger table; pruned per PZ9.

### 2.6 Screens (`feature/protect`)

`PauseSettingsRoute/Screen` (toggle, targets, delay, cooldown, progressive, require tap, intention, active hours,
status rows), `PauseAppsScreen` (picker, reusing the Allowed apps list pattern), `PauseStatsScreen` (7-day bars through
ChartKit), `PauseOverlayContent` (the overlay, previewed in every state: counting, tap-to-continue, with chips, with
reduce-motion, long app name, small phone, dark and light).

Permission flow: usage access → display over other apps → notifications (13+) → battery guide. Reuses
`FocusAccessScreen` rows and `batteryGuide(oem)`; adds an "Appear on top" row and the Xiaomi pop-up row.

---

## 3. Lockdown session

### 3.1 Behaviour

- **Setup.** Duration, up to 3 allowed apps, then a full-screen confirmation that states plainly: no early end, what
  stays available (calls, messages, payments, settings, the 3 apps), how the emergency exit works and what it costs.
  [I understand, start] and [Cancel].
- **During.** Any app that is not allowed gets the block screen: "Lockdown · 18:42 left · Not available",
  [Back to session]. No end button on it. AWAKEN shows only the session screen: timer, the 3 allowed-app shortcuts,
  and a low-emphasis "Emergency exit" link. Navigation to the rest of AWAKEN is closed until the session ends.
- **Emergency exit** (session screen only): hold the button, then wait on that screen, then the session is BROKEN.
  Leaving the screen during the wait cancels the exit.
- **Broken** = no XP or gold, a record in history, and a cooldown before the next Lockdown.
- **Pause** does not fire during Lockdown. **Focus Lock** (the accessibility service) ignores Lockdown runs, so its
  [End session and open] cover never shows over one.
- **Allowed apps cannot be Distracting** (Screen Time category) or of kind SOCIAL / VIDEO / GAME (PROPOSED, LD7).
  Without this, a 90-minute Lockdown with a video app allowed pays the top tier for watching videos.

### 3.2 Numbers (all PROPOSED)

| # | Rule | Proposed |
|---|---|---|
| LD1 | Duration | **15–180 min**, 5-min steps, default 30 (same range family as §8.1 P1; under 15 pays nothing, so it is not offered) |
| LD2 | Allowed apps | at most **3** |
| LD3 | Emergency exit | hold **3 s**, then wait **60 s** on the screen, then it ends |
| LD4 | Cooldown after a broken session | **60 min** before another Lockdown can start |
| LD5 | Reward | a cleared Lockdown pays as a CUSTOM gate of its length (§8.1 P2 tiers, `V = TIMER 1.15`), **multiplier × 1.00**. Counts toward the §8.1 P5 cap of 4 paid runs per game-day; a 5th still locks but pays 0 |
| LD6 | Uncovered time | time with the watcher not running: up to **90 s** total is tolerated (the existing breach budget). Past 90 s the usage log decides: only allowed or never-covered apps seen → the session continues; anything else, or an unreadable log → BROKEN |
| LD7 | Allowed-app limits | not Distracting, not SOCIAL / VIDEO / GAME |
| LD8 | Heartbeat | the service saves "time served" every **30 s** |
| LD9 | Permission check | usage access and overlay permission are re-checked every **5 s** |

Why × 1.00 for LD5: in Lockdown the user may sit in 3 other apps, so it is not harder than a normal gate where they
must stay in AWAKEN. A bonus would pay more for less. × 1.00 also needs no new engine code. If the user wants a bonus
anyway, the alternative is × 1.10.

### 3.3 Always allowed, by phone maker

Rule order (first match wins, all ALLOW):

1. `FocusLockRules.neverBlocked()` (static list + dialer/in-call/emergency/telecom name pattern + payment apps).
2. `AlwaysAllowed.current()` + `paymentApps()`: this phone's launcher(s), IME, default dialer, default SMS app, UPI
   handlers, tap-to-pay app, AWAKEN. Resolved at runtime, so it follows the phone, not a list.
3. New runtime lookups: the clock (`SHOW_ALARMS` handler, already queried for phone-down nights), the permission
   controller (`getPermissionControllerPackageName()`), the package installer.
4. **Any system app with no launcher icon** (`FLAG_SYSTEM` and no `MAIN/LAUNCHER` activity).
5. The session's 3 allowed apps.

Static names added to `NEVER_BLOCK` as a backstop. **Unverified**: these come from memory, not from a phone or a
live source. Batch 3 confirms each on the user's devices with `adb shell dumpsys activity activities` and the phone
test checklist; rule 4 is what actually protects unknown ROMs.

| Role | Pixel / AOSP | Samsung | Xiaomi / POCO | Oppo / Realme | OnePlus | Vivo / iQOO |
|---|---|---|---|---|---|---|
| Launcher | `com.google.android.apps.nexuslauncher` | `com.sec.android.app.launcher` | `com.miui.home`, `com.mi.android.globallauncher` | `com.oppo.launcher`, `com.android.launcher` | `net.oneplus.launcher`, `com.android.launcher` | `com.bbk.launcher2` |
| Clock / alarm | `com.google.android.deskclock` | `com.sec.android.app.clockpackage` | `com.android.deskclock` | `com.coloros.alarmclock` | `com.oneplus.deskclock` | `com.android.BBKClock` |
| Keyboard | `com.google.android.inputmethod.latin` | `com.samsung.android.honeyboard` | Gboard | Gboard | Gboard | Gboard |
| Permission dialogs | `com.google.android.permissioncontroller`, `com.android.permissioncontroller` | same | + `com.lbe.security.miui`, `com.miui.securitycenter` | + `com.coloros.securitypermission`, `com.oplus.securitypermission`, `com.coloros.safecenter` | + `com.oneplus.security` | + `com.vivo.permissionmanager`, `com.iqoo.secure` |
| Share sheet / resolver | `android`, `com.android.intentresolver` | same | same | same | same | same |
| Dialer, in-call, SMS, emergency | already in `NEVER_BLOCK` + name pattern | already listed | `com.android.contacts`, `com.android.incallui`, `com.android.mms` | name pattern | name pattern | name pattern |
| Also | `com.google.android.gms` (sign-in and payment sheets) | same | same | same | same | same |

Settings stays allowed (it is in `NEVER_BLOCK` today). That means a user can revoke a permission or force-stop the
app from there; §3.6 says what happens then. See Q5.

### 3.4 State machine (`:engine`, `LockdownRules`)

```
IDLE ──start (not in cooldown, perms ok, ≤3 apps, LD7)──▶ RUNNING
RUNNING ──served ≥ planned──▶ COMPLETED   (pays through the existing gate clear)
RUNNING ──exit hold 3 s──▶ EXIT_WAIT ──60 s on screen──▶ BROKEN(EMERGENCY_EXIT)
EXIT_WAIT ──left the screen / cancel──▶ RUNNING
RUNNING ──usage or overlay permission gone──▶ BROKEN(PERMISSION_REVOKED)
RUNNING ──uncovered > 90 s and log bad──▶ BROKEN(UNCOVERED)
```

Pure functions: `remaining(anchor, now)`, `onTick`, `onBoot`, `onPermissionCheck`, `onGap`, `canStart(lastBrokenAt, now)`,
`decide(pkg, allowed, …)`.

**Partner-code hook.** The exit transition takes an `ExitReason`. A later accountability-partner code is one new
`ExitReason` value plus its check. Nothing is built for it now: no table, no screen, no interface.

### 3.5 Time: a session can never get shorter

Stored at start: `plannedMillis`, `servedMillis = 0`, `anchorElapsed` (`SystemClock.elapsedRealtime()`),
`anchorWall`, `bootCount` (`Settings.Global.BOOT_COUNT`).

- **Same boot** (`bootCount` unchanged): `served = servedMillis + (elapsedNow − anchorElapsed)`. The wall clock is
  **not used** for timing at all, so changing the time or zone does nothing.
- **Wall-clock cross-check:** if `(wallNow − anchorWall)` and `(elapsedNow − anchorElapsed)` differ by more than 60 s
  (PROPOSED), set `clockChanged = true` on the row. It changes nothing about timing; it is shown in history.
- **After a reboot** (`bootCount` differs): `elapsedRealtime` restarted, so take `servedMillis` from the last 30 s
  heartbeat and re-anchor. **Time with the phone off counts as zero.** A session can therefore get longer by up to
  30 s plus the off time, never shorter. See Q6 for the alternative.
- Monotonic time and boot count come through a new injected `MonotonicClock` (invariant 6: no direct
  `SystemClock` calls in logic). `DebugClock` time travel does not move it.
- The cooldown (LD4) uses the wall clock. Setting the clock forward skips it. Low stakes; accepted.

### 3.6 Persistence and fail-closed

- Room table `lockdown_session` (same migration as `pause_event`): `id`, `gate_run_id`, `planned_millis`,
  `served_millis`, `anchor_elapsed`, `anchor_wall`, `boot_count`, `allowed_packages`, `status`, `broken_reason`,
  `clock_changed`, `uncovered_millis`, `started_at`, `ended_at`.
- **Gate link.** Start goes through `StartGateUseCase.startFocus(...)` as a CUSTOM run with a new `lockdown` flag on
  the run. A Lockdown run: no pause, no 90 s app-background breach (LD6 replaces it), no `DEVICE_RESTARTED` collapse.
  Completion calls the existing clear path, so reward, cap, ledger rows and achievements stay in one transaction with
  no new reward code. Broken calls the existing collapse with a new `GateCollapseReason.LOCKDOWN_BROKEN`.
- **Reboot.** New `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`; the app has none today) starts
  `ProtectService` when a session is RUNNING or Pause is on. New permission `RECEIVE_BOOT_COMPLETED`.
- **Watchdog.** `START_STICKY`, a 15-minute periodic `ProtectWatchdogWorker`, and a check on every app foreground
  (next to `RunRolloverUseCase`). Each restart measures the gap since the last heartbeat and applies LD6.
- **Permission revoked** mid-session → BROKEN(PERMISSION_REVOKED), no XP.
- **What Lockdown without accessibility cannot stop**, stated honestly: force-stop from Settings, the system "Stop"
  button for foreground services (Android 13+), uninstall, safe mode. In each case the session is found broken the
  next time AWAKEN runs: no XP, recorded, cooldown. Lockdown is costly to break, not impossible to break.

### 3.7 Screens

`LockdownSetupScreen`, `LockdownConfirmScreen`, `LockdownSessionScreen` (timer, shortcuts, emergency exit with
hold-then-wait states), `LockdownBlockContent` (the overlay), `LockdownResultScreen` (completed / broken and why),
history rows. Previews for every state including exit hold, exit wait, broken by each reason, 1/2/3 allowed apps.

---

## 4. Strict+ (accessibility; flag off in the Play build)

### 4.0 Prerequisites (Batch 4 starts with these)

- **Build flag.** `BuildConfig.STRICT_PLUS_ENABLED` (debug true, release false), a `StrictFeature.enabled` gate that
  hides every entry (the `AiFeature` pattern), and `app/src/release/AndroidManifest.xml` removes the accessibility
  service with `tools:node="remove"`. Reel Counter moves behind the same flag (Q2).
- **One service.** `ReelAccessibilityService` becomes the single service, with a neutral label. Its package scope
  is set at runtime (`serviceInfo.packageNames`) to exactly what the switched-on features need.
- **Verified detectors only.** A rule acts on an app only if its `rc_detectors.json` entry is `"verified": true`.
  Capturing real ids with the inspector is the first task of Batch 4, on the user's phone.
- `CLAUDE.md` is updated: the module description, and the "view ids only, never node text" line if 3b is approved.

### 4.1 3a: short-video blocking

- User picks apps (from the 5 Reel Counter apps) and when: **during Lockdown and focus sessions**, or **always**.
- When `ScreenDetector` reports a short-video screen in a chosen app and the rule is active:
  `GLOBAL_ACTION_BACK`, plus a short message on the existing accessibility overlay ("Short videos are off right now").
- PROPOSED: at most one Back per **1 s**; if the short-video screen is still there after **2** Backs, go HOME.
- The rest of the app is untouched. Reads view ids only, as today.
- Rule is a pure function in `:reelcounter-core` (`ShortVideoBlockRule`); the host tells the module whether a session
  is running through one small interface, like `ReelClock`.

### 4.2 3b: YouTube Study mode

- The user types channel names (or @handles) into a list. Offline; nothing is looked up.
- Active only for `com.google.android.youtube`, only while Study mode is on. On the watch page the service finds
  **one node by view id** (the channel name), reads its text, normalises, compares, and drops the string. Nothing is
  logged or stored. Allowed → nothing happens. Not allowed → block overlay "Not on your study list", [Back].
- Shorts are blocked through 3a's detector. The list cannot be edited while Study mode is on.
- **Matching** (`ChannelMatch`, pure): NFKC, lower-case, drop a leading "@", keep letters and digits only, then exact
  equality. So "@3blue1brown" matches "3Blue1Brown". Limit: a handle that differs from the display name (for example
  a channel shown as "Kurzgesagt – In a Nutshell" with handle "@kurzgesagt") does not match; the settings screen says
  "Type the name shown under the video."
- **Feasibility risk, high.** YouTube's watch page is largely drawn without resource ids. If the channel-name node has
  no stable id, "read only that node by id" is not possible, and the alternatives read more of the screen. Batch 5
  starts with an inspector capture; **if there is no stable id, stop and write `QUESTIONS.md`.**

Decisions for the user:

| Case | Options | Recommended |
|---|---|---|
| Home feed | (a) leave it alone, check only the watch page · (b) cover the home feed too | **(a)**. Nothing is read on the feed, and search stays usable. Cost: thumbnails are still visible |
| Search | (a) leave it alone · (b) block | **(a)**. Finding a study video needs search |
| Channel can't be determined | (a) **fail open**: the video plays, a small "Study mode can't check this video" note shows, and settings shows a warning · (b) **fail closed**: the video is covered | **(a)**. YouTube changes its layout often. Fail closed would block every video, including study ones, after an update, with no fix until a new build. Fail open is weaker but never breaks studying. With (b), add a grace of a few seconds for the page to load |
| When is it on | (a) only during a Lockdown or focus session that includes YouTube · (b) also a manual timed switch | **(a)**. It gives "the list is locked while on" a clear end |

### 4.3 3c: uninstall / force-stop protection

**Not planned as a build.** Policy finding in §5: this is the one item the live Play text prohibits outright, and the
Malware policy describes the behaviour itself. Recommendation: **do not build it, in any build.** Lockdown already
fails closed (§3.6).

For the review only, what it would involve (unverified, no screen captured): detect our app-info page
(`com.android.settings`, OEM variants in `com.miui.securitycenter`, `com.coloros.safecenter`, `com.samsung.android.lool`),
the uninstall confirmation (`com.google.android.packageinstaller` / `com.android.packageinstaller`), and the
Accessibility settings page, then send Back/Home. It would have to read window titles or text on Settings pages to
tell "our app's page" from any other, which is a wider read than anything else in this plan.

---

## 5. Policy and Play findings

Read live on 2026-11-10. Quotes are verbatim but were returned in pieces of at most 125 characters, so a long sentence
is shown as joined fragments; **check each against the page before relying on it.**

Pages read:
[Accessibility API guidance](https://support.google.com/googleplay/android-developer/answer/10964491) ·
[Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170) ·
[Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379) ·
[Mobile Unwanted Software](https://support.google.com/googleplay/android-developer/answer/9970222) ·
[Malware](https://support.google.com/googleplay/android-developer/answer/9888380) ·
[Foreground service types](https://support.google.com/googleplay/android-developer/answer/13392821) ·
[Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/9888077).

**Not read:** the User Data policy page (prominent disclosure rules are referenced from the pages above), the
"Unauthorized Use or Imitation of System Functionality" subpage, and the Disruptive Ads page. The Permissions page
says "Effective January 27, 2027" for some changes and "This article will be updated with recently announced
changes"; what changes on that date was not captured.

### 5.1 Key text

Accessibility (answer/9888170):
- "The Accessibility API cannot be used to:" "Change user settings without their permission or prevent the ability
  for users to disable or uninstall any app or service" "unless authorized by a parent or guardian through a parental
  control app" "or by authorized administrators through enterprise management software;"
- "Work around Android built-in platform security controls, privacy controls and notifications; or"
- "Change or leverage the user interface in a way that is deceptive or otherwise violates Google Play Developer Policies."
- "Apps must use more narrowly scoped APIs and permissions in lieu of the Accessibility API when possible to achieve
  the desired functionality."
- "Apps not eligible for IsAccessibilityTool may not use the flag and must meet prominent disclosure and consent requirements"
- "The use of the Accessibility API must be documented in the Google Play listing."

Accessibility (answer/10964491):
- "Any use of the Accessibility API that enables an app to autonomously initiate, plan, and execute actions" "or
  decisions is strictly prohibited."
- "This does not prohibit deterministic, rule-based automation, where behavior follows a static, human-defined script"
- "Apps using the Accessibility API for automation purposes must ensure that all actions performed on a user's behalf
  are for a narrow and clearly understood purpose."
- Disclosure: "Must be within the app itself, cannot only be in the app description or on a website;" · "Must be
  displayed in the normal usage of the app and not require the user to navigate through a menu or settings;" · "Must
  describe the data being accessed or collected through the AccessibilityService API ;" · "Must explain how the data
  will be used and/or shared;" · "Must require affirmative user action for consent (for example, tap to accept, or
  tick a check box);" · "Cannot be included with other disclosures pertaining to personal or sensitive data collection."
- Video: "provide a link to a short video which shows the prominent disclosure shown to users in your app."
- "In-app prominent disclosure cannot be substituted with AccessibilityService_description or AccessibilityService_htmlDescription"
- "If you change how your app uses this API, you must submit the form again with updated and accurate information."

Malware (answer/9888380):
- "Apps that abuse features to prevent them from being uninstalled or stopped."
- "Don't write code that prevents a user's app from being uninstalled."
- "You must not lock users out, encrypt data, or prevent uninstallation."

Mobile Unwanted Software (answer/9970222):
- "Apps should not interfere with other apps or the usability of the device"
- "Ensure the uninstall process is simple, clear and straightforward."
- "Explicitly and clearly explain to the user what system changes will be made by the app."
- "Mobile software should not mimic prompts from the device OS or other apps."

Device and Network Abuse (answer/9888379):
- "We don't allow apps that interfere with, disrupt, damage, or access in an unauthorized manner the user's device,
  other devices or computers, servers, networks"
- Foreground services: "Apps are only allowed to declare a foreground service permission if the use:" "is initiated
  by the user or is user perceptible" · "can be terminated or stopped by the user" · "runs only for as long as
  necessary to complete the task"
- "Submit a declaration form in your Play Console if targeting Android 14+ and describe the use case for each"

Foreground service types (answer/13392821):
- "In limited scenarios, if your use case meets the other characteristics required for foreground service usage"
  "you may declare the foreground service TYPE_SPECIAL_USE type. All foreground service types are subject to review."
- "Include a link to a video demonstrating each foreground service feature."

Overlay: **no dedicated policy text was found** on any page read. The only mention is "Direct users to the system
settings page for approval of special permissions (for example, SYSTEM_ALERT_WINDOW)." (answer/9888170).
Usage access: **`PACKAGE_USAGE_STATS` is not mentioned** on the Permissions page.

### 5.2 Classification

| Item | Class | Basis | Needs |
|---|---|---|---|
| **Pause** (usage access + overlay + FGS) | **Unclear, leaning allowed** | No text prohibits a user-configured overlay. Open points: "should not interfere with other apps" (MUwS) is written about unwanted behaviour, and this is user-chosen per app; an always-on service against "runs only for as long as necessary" | FGS `specialUse` declaration + video; in-app explanation before each special permission; the overlay must look like AWAKEN, never like a system dialog; `PLAY_STORE_LAUNCH.md` §5b must be rewritten (it currently promises the app "never blocks, overlays or interrupts other apps") |
| **Lockdown** (same APIs) | **Risky** | "can be terminated or stopped by the user" (FGS) against a session with no end button; "You must not lock users out" (Malware). In its favour: user-started, time-boxed, phone/SMS/payments/Settings never covered, an emergency exit always exists, uninstall is untouched | The confirmation screen is the disclosure: say what is blocked, for how long, how to exit. Describe the emergency exit in the FGS declaration. Never cover Settings |
| **3a** short-video blocking | **Allowed with declaration; some review risk** | "This does not prohibit deterministic, rule-based automation, where behavior follows a static, human-defined script". The action (one Back) is narrow and user-defined. Residual: "interfere with other apps" | Accessibility declaration, in-app prominent disclosure with a consent tap, disclosure video, mention in the listing |
| **3b** YouTube Study mode | **Risky** | Same rule-based basis, but it **reads on-screen text of another app**. The disclosure must "describe the data being accessed". No narrower API exists for this, which the "more narrowly scoped APIs" line asks about | Its own disclosure naming exactly what is read (the channel name on the YouTube watch page), that it is compared on the phone and never stored or sent; declaration resubmitted; privacy policy line |
| **3c** uninstall / force-stop protection | **Prohibited** | Accessibility text above ("prevent the ability for users to disable or uninstall any app or service"; the only exceptions are parental control and enterprise management, and a self-imposed lock is neither). Also Malware: "Apps that abuse features to prevent them from being uninstalled or stopped." | Nothing makes it acceptable on Play. Outside Play, the same behaviour is what Play Protect is built to flag |

The Play build ships **no accessibility service** if Q1 = (a) and Q2 = yes, so the accessibility declaration is only
needed if Strict+ is ever switched on for Play.

### 5.3 Wording (drafts for `PLAY_STORE_LAUNCH.md`, written in Batch 7)

**FGS special-use description:** see §1.1.

**In-app disclosure, Pause (before the two system switches):**
"Pause needs two permissions. Usage access lets AWAKEN see which app is open, so it knows when one of your chosen
apps starts. Appear on top lets it show the pause screen over that app. AWAKEN reads the app's name only, keeps your
pause counts on this phone, and sends nothing anywhere."

**In-app disclosure, Strict+ accessibility (own screen, consent by tap, not combined with anything else):**
"AWAKEN uses Android's Accessibility service for the rules you switch on here. Short-video blocking: in the apps you
choose, AWAKEN notices when a short-video screen is open and presses Back. Study mode: in YouTube only, while Study
mode is on, AWAKEN reads the channel name under the video and compares it with your list. It reads nothing else,
stores none of it, and sends nothing off this phone. You can switch this off at any time in Android settings."
[I agree] [Not now]

**Accessibility declaration (Play Console), if ever enabled for Play:** "Not an accessibility tool. The service
applies rules the user defines: (1) in user-chosen apps it detects the short-video screen by view id and performs a
single Back action; (2) in YouTube, while the user's Study mode is on, it reads the channel-name text of the playing
video and compares it on-device with the user's list. No data is collected, stored or transmitted. The service is
scoped to the packages the user chose."

**Review video script, about 30 s:** 0–5 s open AWAKEN → Protect → Strict+. 5–13 s the disclosure screen in full,
tap [I agree]. 13–18 s Android Accessibility settings, switch the service on. 18–24 s open the chosen app, swipe to
the short-video tab, AWAKEN presses Back and shows "Short videos are off right now". 24–30 s back in AWAKEN, switch
the rule off.

**Privacy policy lines:** "Pause and Lockdown use Android usage access to see which app is in front and the 'appear
on top' permission to show a pause or block screen. Only app names and counts are kept, on your device." · "If you
switch on Strict+ rules, AWAKEN uses an Accessibility service to detect short-video screens and, in YouTube Study
mode, to read the channel name of the video being played. This is processed on your device, never stored and never
transmitted."

**Data Safety:** unchanged, no data collected. Everything stays on the device and the release build has no `INTERNET`.

---

## 6. Docs to change

| Doc | Change | Batch |
|---|---|---|
| `PRD_MVP.md` | Add Focus protection to scope (the PRD rule: no feature that is not in it) | 1 |
| `GAME_DESIGN.md` | New §19 with the approved PZ / LD numbers; §12 vectors 178+; §8.2 note that Lockdown runs are not covered by Focus Lock | 1 and 3 |
| `ENGINE_DECISIONS.md` | #165+ : pause "previous package" rule, n counting, cooldown boundary, window crossing midnight, Lockdown time rules, LD6 gap rule | 1 and 3 |
| `ARCHITECTURE.md` | `core/protect` (service, watcher, boot receiver, watchdog), `feature/protect`, the two tables, `MonotonicClock`, the build flag, the single accessibility service | 2, 3, 4 |
| `DESIGN_SYSTEM.md` | New components: `RisingFill` (reduce-motion: whole-second steps), `HoldToConfirmButton`, `PauseOverlay`, `BlockScreen` variant without the end button | 2 and 3 |
| `PLAY_STORE_LAUNCH.md` | §5b rewritten; new §5d FGS declaration; §5c replaced by the Strict+ declaration, disclosure, video script; privacy lines; Data Safety note | 7 |
| `CLAUDE.md` | New packages, the flag, the one-service invariant, new commands if any | 2 and 4 |
| `docs/protect/PHONE_TEST.md`, `BATTERY.md` | Phone checklist and measurement log | 7 |

---

## 7. Tests

`:engine` (JUnit5), **PauseRulesTest**: delay for n = 1, 4, 9 (cap); progressive off; cooldown boundary (4:59 no pause,
5:00 pause); Not now gives no cooldown; non-target app; previous package is the same app; previous package is a
never-block package; active hours inside, outside, end exclusive, crossing midnight, weekday of a crossing window;
Lockdown running; Focus Lock covering; line index is deterministic and in range; n resets at the game-day boundary.

`:engine`, **LockdownRulesTest**: remaining time on the same boot ignores wall clock forward and back; `clockChanged`
set past 60 s of drift; reboot resumes from the heartbeat and counts off-time as zero; completion at exactly planned;
emergency exit hold then wait → BROKEN; leaving at 59 s → RUNNING; permission revoked → BROKEN; gap ≤ 90 s tolerated;
gap > 90 s with a clean log continues, with a blocked app or unreadable log → BROKEN; cooldown boundary at 60 min;
`decide()` for allowed app, non-allowed app, payment app, dialer, system app without icon, AWAKEN; LD7 rejects a
Distracting or VIDEO app at setup; more than 3 apps rejected.

`:app` (Robolectric): `ForegroundRead` from fake usage events; `ProtectService` stops polling on screen off and stops
itself with no targets; `BootReceiver` starts the service only when needed; Lockdown start/complete/break use-cases
(ledger rows on clear, none on break, P5 cap, idempotent double-complete); migration 29 → 30; view-model tests;
Compose tests for the overlay (TalkBack labels, 48 dp targets, reduce-motion) and the hold-to-confirm button;
Roborazzi goldens for every new preview.

`:reelcounter-core`: **ShortVideoBlockRuleTest** (chosen app + session → BACK; not chosen → nothing; "always" mode;
unverified detector → nothing; rate limit; 2 Backs then HOME). **ChannelMatchTest** (case, "@", spaces, punctuation,
NFKC, non-Latin names, empty input, near-miss does not match). **ProtectedScreenMatchTest** only if 3c is ever approved.

---

## 8. Build batches (Part B)

Each batch: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint` green, `verifyRoborazziDebug`
with only the named new goldens, commit, short summary, STOP.

| Batch | Contents | Main files |
|---|---|---|
| **1** | `GAME_DESIGN` §19, PRD, ENGINE_DECISIONS. `PauseRules`, `PauseVariant`, `PausePlan` and tests | `engine/.../PauseRules.kt`, `PauseRulesTest.kt`, docs |
| **2** | Watcher: `UsageGateway.foreground()`, `ProtectService`, overlay window type on `BlockOverlay`, `SYSTEM_ALERT_WINDOW` + FGS manifest entries, `protect` channel. Pause: `PauseStore`, `pause_event` + migration, settings / apps / stats screens, overlay content, permission flow, line bank, battery handling | `core/protect/*`, `feature/protect/Pause*`, `core/data/usage/*`, `AwakenDatabase`, `strings_protect.xml`, designsystem `RisingFill` |
| **3** | `LockdownRules` + tests, `MonotonicClock`, `lockdown_session`, gate `lockdown` flag and `LOCKDOWN_BROKEN`, start / complete / break use-cases, `BootReceiver`, watchdog worker, setup / confirm / session / block / result screens, emergency exit, Focus Lock ignores Lockdown runs. If Q1 = (a): move Focus Lock onto the watcher and delete `FocusLockService` | `engine/.../LockdownRules.kt`, `core/protect/*`, `core/domain/Lockdown*UseCase.kt`, `GateUseCases.kt`, `feature/protect/Lockdown*`, designsystem `HoldToConfirmButton` |
| **4** (if approved) | Build flag + release manifest removal + single service; capture real detector ids; `ShortVideoBlockRule`; settings + disclosure screen | `app/build.gradle.kts`, `app/src/release/AndroidManifest.xml`, `reelcounter-core`, `reelcounter-android/service`, `feature/protect/Strict*` |
| **5** (if approved) | Inspector capture first (stop if no stable id); `ChannelMatch`; list screen; Study mode | `reelcounter-core`, `reelcounter-android`, `feature/protect/Study*` |
| **6** | **Not planned.** Only if the user approves after reading §5 | — |
| **7** | `PLAY_STORE_LAUNCH`, privacy lines, `PHONE_TEST.md`, `BATTERY.md`, review pass | docs |

---

## 9. Draft for `GAME_DESIGN.md` §19 (written on approval)

> ## 19. Focus protection (PROPOSED 2026-11-10)
> **19.1 Pause**: table PZ1–PZ11 from §2.2 of the plan. Pays nothing: no XP, gold, achievement or streak effect.
> **19.2 Lockdown**: table LD1–LD9 from §3.2. A cleared Lockdown is a CUSTOM gate run of its length (§8.1 P2, P5).
> A broken one pays nothing and deducts nothing.
> **19.3 Strict+**: no numbers that touch rewards. Back rate limit 1 s, HOME after 2 Backs.

Test vectors (§12, numbering continues from 177):

| # | Input | Expected |
|---|---|---|
| 178 | Pause, base 3, step 2, max 15, n = 1 | 3 s |
| 179 | same, n = 4 | 9 s |
| 180 | same, n = 9 | 15 s |
| 181 | continued 10:00:00, reopened 10:04:59 / 10:05:00 | no pause / pause |
| 182 | Not now 10:00:00, reopened 10:00:10 | pause, n = 2 |
| 183 | hours 09:00–18:00 Mon–Fri; Mon 08:59 / Mon 09:00 / Mon 18:00 / Sat 10:00 | none / pause / none / none |
| 184 | hours 22:00–02:00 Mon only; Tue 01:00 / Wed 01:00 | pause / none |
| 185 | target opened while Lockdown RUNNING | none |
| 186 | target on top, previous real package is the same target | none |
| 187 | Lockdown 30 min, same boot, 10 min elapsed, wall clock +3 h | 20 min left, `clockChanged` |
| 188 | same, wall clock −2 h | 20 min left, `clockChanged` |
| 189 | Lockdown 30 min, heartbeat at 10:30 served, then reboot, any wall time | 19:30 left |
| 190 | Lockdown 30 min served in full | COMPLETED, pays as CUSTOM 30 min (tier D) |
| 191 | emergency exit: hold 3 s, wait 60 s | BROKEN, ledger unchanged |
| 192 | emergency exit: hold 3 s, leave at 59 s | RUNNING |
| 193 | usage access revoked mid-session | BROKEN (PERMISSION_REVOKED), ledger unchanged |
| 194 | start 59 min / 60 min after a BROKEN session | refused / allowed |
| 195 | watcher down 91 s, usage log shows a non-allowed app / only allowed apps / unreadable | BROKEN / RUNNING / BROKEN |
| 196 | watcher down 90 s | RUNNING |
| 197 | 5th paid custom run of the game-day is a cleared Lockdown | 0 XP, session still recorded COMPLETED |

---

## 10. Every PROPOSED number in one place

Pause: base 3 s (3/5/10) · step 2 s per pause · max 15 s · cooldown 5 min (1/5/10/15) · max 12 targets · 5 chips ·
7-day stats, rows kept 8 days · 40 lines · require-tap off · progressive on.
Watcher: fast 400 ms · steady 1,500 ms · fast window 15 s · battery pass ≤ 2 points per day.
Lockdown: 15–180 min, step 5, default 30 · 3 allowed apps · exit hold 3 s + wait 60 s · cooldown 60 min ·
reward × 1.00 (alternative × 1.10) · uncovered budget 90 s · heartbeat 30 s · permission check 5 s · clock drift flag 60 s.
Strict+: Back at most once per 1 s · HOME after 2 Backs.

---

## 11. Decisions (approved 2026-11-10: the Recommended column is the answer in every row)

| # | Question | Options | Recommended |
|---|---|---|---|
| **Q1** | Focus Lock is an accessibility service today, and on 2026-11-03 you chose to keep it. The brief wants one accessibility service and usage + overlay blocking. | (a) Move Focus Lock onto the new watcher in Batch 3 and delete `FocusLockService`. One service, and the Play build has no accessibility at all (which also ends the UPI "an accessibility service is on" warnings there). Cost: the half-second gap of §1.2, a permanent notification during sessions, and the overlay permission · (b) Leave Focus Lock alone; the app keeps two accessibility services until later · (c) Fold Focus Lock into the Reel service; then Focus Lock disappears from the Play build with the flag | **(a)** |
| **Q2** | There is no Reel Counter build flag. Building one removes Reel Counter from the Play build. Intended? | yes / no | yes, it is what the brief describes |
| **Q3** | Lockdown reward multiplier | × 1.00 / × 1.10 | × 1.00 (§3.2) |
| **Q4** | May allowed apps in Lockdown be Distracting / social / video / game apps? | no (LD7) / yes but then that session pays 0 / yes | no |
| **Q5** | Is Settings covered during Lockdown? | (a) never covered: safe, but permissions can be revoked and the app force-stopped there (session then breaks, no XP) · (b) covered: harder to break, but blocks Wi-Fi, Bluetooth, and the path to uninstall, which runs into §5 | **(a)** |
| **Q6** | Phone switched off during a Lockdown | (a) off-time counts as zero: tamper-proof, but a phone off overnight resumes the session in the morning · (b) count off-time from the wall clock only when automatic time was on before and after | **(a)**; the emergency exit covers the morning case |
| **Q7** | Emergency exit placement | session screen only / also on the block overlay | session screen only |
| **Q8** | Study mode: the four decisions in §4.2 | — | (a) in each row |
| **Q9** | 3a, 3b, 3c: which get built? | — | 3a yes after ids are captured; 3b only after the inspector shows a stable id; **3c no** |
| **Q10** | Does Pause also fire during an ordinary focus gate when Focus Lock is off? | yes / no | yes (only skipped when something else already covers the app) |
