# Reel Counter — SPEC (verbatim brief)

Build "Reel Counter" as a self-contained Gradle project INSIDE this repo, in its own folder.
Another Claude Code session is working on the main app at the same time — do not conflict with it.

TWO-MODEL WORKFLOW
- PART A (planning) runs on Opus. It writes a plan detailed enough that a different model can
  implement it without making design decisions. Then it STOPS.
- I will then switch to Sonnet with /model and say "go".
- PART B (implementation) runs on Sonnet. It follows PLAN.md exactly, one phase at a time, and
  STOPS after each phase until I say "next".
- If you are in PART B and something in PLAN.md is ambiguous or wrong, do NOT redesign it:
  write the question in /reelcounter/PLAN_QUESTIONS.md, STOP, and tell me.

ISOLATION (strict)
- Create a git worktree on a new branch first:  git worktree add ../awaken-reelcounter -b feature/reel-counter
  and work only there.
- All code lives under /reelcounter/ with its OWN settings.gradle.kts, gradle wrapper and
  libs.versions.toml (copy versions from the root catalog so they match).
- Do NOT edit any file outside /reelcounter/ (no root settings.gradle.kts, no :app, no :engine,
  no CLAUDE.md, no docs/). If you think you need to, stop and ask me.
- Package: com.assismohanty.awaken.reelcounter (demo app id: com.assismohanty.awaken.reelcounter.demo)

CLEAN ROOM
- Do NOT decompile, download or reference any third-party APK, its code, strings, icons or screens.
  Build from this spec and public Android docs only. All names, icons and UI are original.

MODULES (inside /reelcounter)
  :core     pure Kotlin, zero Android imports — detection rules model, counting state machine,
            time-on-screen, day rollover, limits. Full unit tests.
  :android  AccessibilityService, overlay bubble, Room, DataStore, setup/stats UI (Compose).
            Android LIBRARY so it can later drop into AWAKEN as a feature module.
  :demo     thin app shell hosting :android so I can install and test on my phone.

FEATURES
1. Supported: Instagram Reels, YouTube Shorts, TikTok, Facebook Reels, Snapchat Spotlight.
2. Floating counter bubble on short-video screens only: count for this app today; draggable, snaps to
   edge, remembers position; tap → per-app counts + time spent; long-press → hide for this session;
   auto-hides outside short-video screens.
3. +1 per NEW short video brought into view by a full swipe. No recount on scroll-back, likes,
   comments, pause, or returning to the same video.
4. Time spent on short-video screens per app per day.
5. Daily limits per app (off by default). At limit: full-screen nudge "50 reels today" with
   [Close app] (HOME) and [5 more] (max 2 snoozes/day).
6. Stats: today hero number, per-app breakdown, 7-day and 30-day bar charts, streak of days under
   limit, "equivalent to X minutes" line.
7. Home-screen widget (Glance): today's total + per-app.
8. Day reset at configurable time (default 04:00). Room: reel_event (pkg, ts, itemIndex),
   daily_total (date, pkg, count, seconds). JSON export.
9. Setup flow: explanation screen (reads only on-screen element IDs of supported apps to detect
   short-video screens; reads no text; nothing leaves the phone) → [Continue] → Accessibility settings
   deep link → verify → overlay permission → battery optimisation + OEM autostart guide
   (Xiaomi/POCO, Oppo/Realme, Vivo/iQOO, OnePlus, Samsung) with deep links + fallbacks.
10. Status screen: each permission ✓/✗ with fix button; warning if the service was killed.

DETECTION
- AccessibilityService: packageNames = supported packages only;
  eventTypes = typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged;
  canRetrieveWindowContent = true. Never read or log node text.
- Rules in assets/detectors.json per package: container view-ids for the short-video screen +
  "new item" signal (scroll fromIndex change / pager page change / child swap). Editable without code.
- DEBUG-only Detector Inspector: toggle that dumps package + view-id tree (ids only) of the current
  screen to a local file and shows it in-app. Document the `adb shell uiautomator dump` method too.
- Counting = pure state machine in :core fed by abstract events
  (ScreenEntered, ItemChanged(index), ScreenLeft, Tick, DayRolled). Debounce.

DESIGN (original, premium, AWAKEN direction)
- Calm Apple/Withings-style: light-first + polished dark, whitespace, rounded cards, one restrained
  accent, very large numerals, no gradients/neon/glow.
- Semantic tokens (surface, textPrimary, accent, …) in :android/designsystem so they can be swapped
  for AWAKEN's on merge. Feature code uses tokens only.
- Bubble: 44dp circle, accent ring filling toward the daily limit, tabular numerals.
- ORIGINAL adaptive icon as vectors (foreground + background + monochrome): a simple stack of vertical
  cards with a small counter badge, geometric, max 2 colours, readable at 48px. Wordmark for setup
  screen. 512×512 PNG into /reelcounter/branding/.

═══════════════════════════════════════════════
PART A — PLAN  (Opus, plan mode, NO code)
═══════════════════════════════════════════════
1. Create the worktree/branch. Save this whole prompt verbatim to /reelcounter/SPEC.md.
2. Write /reelcounter/PLAN.md containing ALL of the following, concrete enough that the implementer
   makes no design decisions:
   a. Module + package layout and every file to create, with a one-line purpose each.
   b. :core public API as actual Kotlin signatures (sealed event classes, state data classes,
      reducer/engine functions, outputs/effects, Clock interface injected, debounce rule with exact ms).
   c. Counting state machine: states, transitions table, and how "same video" vs "new video" is decided.
   d. EVERY edge case as a named test with input event sequence → expected count/time/outputs
      (rapid duplicates, scroll back, leave/return, return to same video, app switch mid-video,
      screen off, rollover mid-session, limit reached, snooze cap, limit changed mid-day, service restart).
   e. detectors.json schema + one example entry per supported app (ids marked UNVERIFIED) and how the
      adapter maps raw AccessibilityEvents → :core events.
   f. Room entities, DAOs, queries for each stats view; DataStore keys; JSON export format.
   g. Every screen and component with its states (for previews), and the token list with light/dark values.
   h. Icon/wordmark construction spec (shapes, grid, colours).
   i. Implementation phases 1–5 below, each with a file checklist, acceptance criteria, and its gate command.
   j. Risks + what can only be verified on a real phone.
3. Show me a short summary of PLAN.md and STOP. Tell me: "Switch to Sonnet and say go".

═══════════════════════════════════════════════
PART B — IMPLEMENT  (Sonnet, one phase per "next")
═══════════════════════════════════════════════
Rules: read SPEC.md + PLAN.md at the start of every phase. Follow PLAN.md signatures and file list
exactly. Tick off the phase checklist in PLAN.md as you go. Each phase: run gate, commit, summarise, STOP.

Phase 1 — Core engine
  Gradle skeleton (all 3 modules compile). Write ALL PLAN.md tests first, then implement :core.
  Gate: cd reelcounter && ./gradlew :core:test
  Commit "feat(reelcounter): core counting engine".

Phase 2 — Detection + inspector
  AccessibilityService, event→core adapter, debounce, detectors.json (UNVERIFIED ids), Detector
  Inspector, Room + repository, :demo installable.
  Gate: ./gradlew :android:testDebugUnitTest :demo:assembleDebug
  Commit. Then give me step-by-step instructions to run the inspector in each supported app.
  → I will paste inspector dumps back; update detectors.json and retest until I confirm counting is
    correct on my phone. Only then wait for "next".

Phase 3 — Design system, icon, bubble
  Tokens (light+dark), typography, icon/wordmark/PNG, full bubble behaviour. Previews for every state.
  Gate + commit.

Phase 4 — Setup + status
  Features 9 and 10, previews per state. Gate + commit.

Phase 5 — Limits, stats, widget, export
  Features 4–8. Full gate:
  cd reelcounter && ./gradlew :core:test :android:testDebugUnitTest :demo:assembleDebug lint
  Commit. Write /reelcounter/README.md (build, install, inspector, updating detectors.json).
  Give me a phone test checklist. Do NOT merge. Then tell me: "Optional: switch to Opus and say review".

═══════════════════════════════════════════════
PART C — REVIEW  (optional, Opus, when I say "review")
═══════════════════════════════════════════════
Review the whole diff vs SPEC.md + PLAN.md: node text read/logged? any network? files outside
/reelcounter touched? :core Android-free? every PLAN.md edge case tested? battery cost per event,
overlay stuck states, crash on permission revoke, service leaks. Fix issues. Write INTEGRATION.md
(include as modules in AWAKEN, swap tokens, merge its AccessibilityService with Focus Lock into ONE
service, feed daily_total into screen-time tiles/quests). Full gate. Do NOT merge.
