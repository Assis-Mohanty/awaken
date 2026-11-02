# Reel Counter inside AWAKEN

Reel Counter counts the short videos watched in Instagram, YouTube, TikTok, Facebook and Snapchat. It is a feature of
the AWAKEN app, not a separate app. `SPEC.md` is the original brief, `PLAN.md` the original build plan (its file
layout and Phase 3 to 5 UI sections are superseded by this document), `README.md` explains how to build and tune it.

## Modules

| Module | What it holds |
|---|---|
| `:reelcounter-core` | Pure Kotlin, no Android imports. Counting state machine, screen detector, item tracker, game-day maths, limits, stats, JSON export. 140 tests. |
| `:reelcounter-android` | Android library, **no visual design**. The `AccessibilityService`, event adapter, counter runner, Room (`reelcounter.db`), DataStore settings, detector rules, the overlay window host, and the debug-only Detector Inspector. |
| `:app` | Everything the hunter sees. `feature/reelcounter` (setup, Settings window, limits, stats, overlay layouts), `core/reelcounter` (overlay windows, host bindings, system access), `core/designsystem/components` (`ReelBubble`, `ReelsTile`), strings in `strings_reelcounter.xml`. |

A library cannot depend on `:app`, and AWAKEN's design system, Lexicon and ChartKit live in `:app`. So the module
declares small interfaces and `:app` implements them:

- `ReelOverlayUi` (bubble, panel, limit nudge) is implemented by `core/reelcounter/ReelOverlayWindows` on AWAKEN's tokens.
- `ReelClock` is AWAKEN's `Clock`, so debug time travel moves Reel Counter too.
- `ReelDayStart` is the hunter's `dayStartHour`. Reel Counter has no day-start setting of its own.

`FeatureBoundaryTest` and `LexiconResourcesTest` therefore cover all Reel Counter UI. Strings are in a neutral voice,
so none need a `_clean` twin.

## Where it lives in the app

- **Settings → Focus → Reel counter**: the switch, status rows (accessibility, running, battery), bubble toggle, Daily limits, Reel stats, Permissions and battery.
- **Setup** (`ReelCounterSetupRoute`): disclosure first, then Android's accessibility switch, then the battery steps.
- **Permissions and battery** (`FocusAccessRoute`): one screen for both accessibility services (Focus Lock and Reel counter), the shared battery setting and the phone-maker autostart guide.
- **Today**: a "Reels today" tile, only while the feature is on.
- **Screen time**: a "Short video" window with the per-app counts, linking to Reel stats.
- **Today widget**: one extra line, "Reels today: N", only while the feature is on. There is no separate widget.
- **Debug builds**: Settings → Focus → Reel counter → Detector inspector (`ReelInspectorRoute`).

## Two services, on purpose

Reel Counter keeps its own `AccessibilityService` (`ReelAccessibilityService`, `rc_accessibility_service.xml`). It is
not merged with Focus Lock: Reel Counter needs `canRetrieveWindowContent` to read view ids, Focus Lock must never have
it (it listens to window changes only). Merging would give Focus Lock a capability it must not have, and would make
disabling one feature disable the other.

The service reads only view ids, class names, bounds and flags. It never reads node text, and
`NoNodeTextAccessTest` scans the code to keep it that way. Neither module requests `INTERNET`
(`NoNetworkTest`); the app's single allowed network destination is unchanged.

**Off by default.** The service does nothing until the hunter has read the disclosure and switched Reel counter on in
AWAKEN. If someone turns the Android accessibility switch on without that, the service switches itself off again.

## Database: a separate file (`reelcounter.db`), not AWAKEN's DB

Chosen on purpose, not as a shortcut:

1. **Nothing to migrate.** Reel Counter has never shipped. The standalone demo app had a different application id,
   so no phone holds AWAKEN-side reel data. A migration would move zero rows.
2. **A different write path.** The service writes from its own thread while the app may be closed. AWAKEN's database
   follows the ledger invariants (insert-only ledger, one transaction per game action). Reel events are neither
   game actions nor ledger rows, and must not be able to block or be blocked by them.
3. **Independent schema.** Adding tables to `AwakenDatabase` means a schema bump and a migration that every future
   AWAKEN change has to carry. Reel Counter's schema (version 1, exported to `reelcounter-android/schemas`) evolves on
   its own.

The cost: AWAKEN's backup export does not include reel history. It uses Hilt and Room like the rest of the app, and the
module has its own JSON export format (see `ExportDocument`) when that is wanted.

## Detection status (read this before trusting the numbers)

The detector rules in `assets/rc_detectors.json` are **UNVERIFIED**. The view ids are placeholders, and a structural
fallback (a full-screen scrolling list whose items are full-screen) does the real detecting until each app's ids are
confirmed from real screens. Counting accuracy on the five apps has not been verified on a phone. See `README.md` for
the inspector workflow that fixes this, and `PLAN.md` §5 for the rule format.

## Known gaps

- Detector ids unverified (above).
- The JSON export of reel history has no screen in AWAKEN yet.
- The AccessibilityService is declared in the app manifest for everyone; Google Play's policy on accessibility use
  needs a review before this ships to the store (prominent disclosure exists; the declaration form is still to do).
