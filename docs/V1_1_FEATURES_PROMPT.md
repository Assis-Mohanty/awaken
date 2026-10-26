# v1.1 build brief: mainstream balance, insights, review loop, UI-overhaul readiness

Read this whole file before planning. Then read CLAUDE.md and the current docs/ (PRD_MVP, GAME_DESIGN,
ARCHITECTURE, DESIGN_SYSTEM, PLAY_STORE_LAUNCH, BACKLOG). The docs on disk are the source of truth for
anything already built; this file adds new scope.

## Context (why this exists)

The app currently reads as a niche anime/RPG app. We want it to read like a serious personal-growth app
that *plays* like a game (the Duolingo model): outcomes and data first, game mechanics as the engine,
the "System" flavour as an optional layer. A full visual redesign is coming later (see Part 4); this work
must leave the codebase ready for it, not do it.

## Hard constraints for all of this work

1. **No AI and no new network calls.** AI Paths stays dormant exactly as it is (do not delete it, do not
   call the proxy, do not add any endpoint). Everything below is computed on-device by `:engine` + Room.
2. All existing CLAUDE.md invariants still hold: ledger-only economy, idempotent writes, one transaction
   per game action, rules in `:engine`, injected Clock, derived-not-stored.
3. **No invented numbers in code.** Every threshold, reward or window below marked `PROPOSED` must first be
   written into GAME_DESIGN.md and approved by me in Part 1 before it is used.
4. **No reward for reflection or for opening the app.** Weekly reviews, insights and recaps grant no XP.
   Achievements may grant gold per GAME_DESIGN §10 rules (amounts PROPOSED).
5. Health Connect data stays on the phone. Insights are a new *use* of already-granted data, so Part 1
   must also update the Health apps declaration text and the privacy policy wording in PLAY_STORE_LAUNCH.md.
6. Strings in strings.xml, previews for every new state, 48dp targets, TalkBack labels on every chart,
   reduce-motion respected.

---

## Part 1: Spec first (NO code)

Write the specs below into the docs, with every number marked `PROPOSED`, then STOP and show me a summary
of every PROPOSED number and every open question. I approve before Part 2 starts.

- PRD_MVP.md: add F22–F28 (the seven features in Part 3) with one-line specs and links.
- GAME_DESIGN.md: new sections for consistency score, insights engine, weekly review, time-of-day blocks,
  recaps, identities, and the Clean/System lexicon. Add test vectors for every formula (continue the §12
  numbering).
- DESIGN_SYSTEM.md: new screens/components (InsightCard, ConsistencyMeter, ReviewFlow, TimeBlockHeader,
  RecapCard, IdentityChips, ModeSwitch) and the token/lexicon architecture from Part 2.
- Add docs/UI_OVERHAUL_NOTES.md (Part 4).
- PLAY_STORE_LAUNCH.md: Health Connect justification + privacy policy lines for on-device insights.

---

## Part 2: UI-overhaul readiness refactor (before any new screens)

Goal: a later redesign should touch `core/designsystem` and the lexicon, not 40 feature files.
No visual change is intended in this part. Screenshots before and after must match.

1. **Semantic tokens.** Add a semantic layer over the current palette: `surface`, `surfaceElevated`,
   `surfaceSunken`, `textPrimary`, `textSecondary`, `textMuted`, `accent`, `accentMuted`, `positive`,
   `warning`, `critical`, `divider`, `chartSeries1..6`, `chartGrid`, plus semantic type roles
   (`metricXL`, `metricL`, `title`, `body`, `label`, `caption`) and spacing/radius/elevation scales.
   Feature code may reference ONLY semantic tokens. Grep-enforce it with a lint check or unit test that
   fails if `feature/**` references raw palette names, hex, or literal dp/sp.
2. **Light + dark schemes.** The theme must support both, even though dark ships now. Add a light scheme
   that is complete but unpolished, behind a debug-only toggle.
3. **Layering.** Route → stateless Screen(state) → section components from `core/designsystem`.
   No Modifier styling chains or colours inside `feature/**` beyond layout.
4. **ChartKit.** One interface in `core/designsystem/charts` for every chart type we use or will use
   (trend line, bar, ring/progress, radar, heatmap, sparkline). Existing radar and heatmap move behind it.
   Screens ask for a chart by type + data, never draw on Canvas themselves.
5. **Lexicon.** A `Lexicon` interface providing every user-visible game term (see Part 3, F28), with
   `CleanLexicon` and `SystemLexicon`, backed by string resources (`term_<key>_clean`,
   `term_<key>_system`). Replace every hard-coded game term in UI with lexicon lookups. Provided via
   CompositionLocal from a DataStore preference.
6. **Screenshot tests.** Add Roborazzi (latest stable) screenshot tests for every screen's main states,
   committed as golden images. These are the safety net for the redesign.

Gates + commit: `refactor(designsystem): semantic tokens, lexicon, chartkit, screenshot tests`.

---

## Part 3: Features (engine first, then data, then UI; commit per feature)

### F22 Consistency score + "never miss twice"
- `consistency = clearedDays / eligibleDays` over the last 28 game-days (PROPOSED window), where rest
  and recovery days are excluded from `eligibleDays`. Shown as "22 of the last 28 days" + a meter.
- The streak stays exactly as it is. Consistency is shown next to it, not instead of it.
- Day after a missed day: Today shows a calm "Missed yesterday. Don't miss twice." line. If that day is
  cleared, record a "recovered" event; achievement for N recoveries (PROPOSED).
- Pure function in `:engine` over DayRecords; test vectors incl. rest/recovery exclusion and <28 days of
  history.

### F23 Personal insights (on-device correlations)
Engine: `InsightEngine.evaluate(history) → List<Insight>`, deterministic, pure.
Candidate insights (each a separate rule with its own test vectors):
1. Sleep: completion rate on days after ≥7h sleep vs <7h (Health Connect sleep; skip if HC off).
2. Weekday: strongest and weakest weekday by completion rate.
3. Time of day: block (F25) with highest completion rate.
4. Focus: hour-of-day with the most cleared Focus Gates.
5. Steps: completion rate on days with ≥ X steps vs fewer (PROPOSED X).
6. Momentum: completion rate in the 3 days after a cleared Rank Trial / Dungeon stage, if data exists.
Statistical guardrails (all PROPOSED, all in GAME_DESIGN):
- Minimum 14 game-days of history before any insight is shown.
- Each comparison bucket needs ≥ 5 days.
- Show only if relative difference ≥ 25% AND absolute difference ≥ 0.5 quests/day.
- Max 3 insights visible; rotate deterministically per ISO week; never repeat the same insight 2 weeks in
  a row unless no other qualifies.
Copy rules: correlational language only ("On days you sleep 7h+, you complete 38% more quests."),
never causal, never medical advice, never shaming. Empty state explains how many more days are needed.
UI: an Insights section on Status + one insight in the Weekly Review.

### F24 Weekly review ritual
- Available from Saturday 00:00 to Monday end-of-day (game-day boundaries; PROPOSED window), once per ISO
  week, idempotent per `(isoWeek)`.
- Flow (≤ 5 taps + optional text): (1) this week's numbers (existing Weekly Report data + consistency +
  one insight), (2) "What went well?" optional ≤ 280 chars, (3) "What slipped?" optional ≤ 280 chars,
  (4) pick next week's focus stat(s), (5) optional one-line intention.
- Next week's chosen focus stats feed the Directive generator (existing focus weighting; no new numbers).
- Stored in Room `weekly_review`; included in export/import.
- No XP. Achievements for 1 / 4 / 12 reviews (gold PROPOSED).
- One notification on Sunday at the reminder time, counted inside the existing 2/day cap.

### F25 Time-of-day blocks
- Blocks: Morning, Day, Evening, Anytime. Default boundaries (PROPOSED): Morning dayStart–12:00,
  Day 12:00–17:00, Evening 17:00–dayStart. Editable in Settings.
- Templates gain optional `timeOfDay` (update templates.json + its validation test); habits get a
  user-set block; quests with no block go to Anytime.
- Today's Directive renders grouped by block in chronological order; the current block is highlighted;
  past blocks collapse. No change to generation rules or rewards.

### F26 Monthly and yearly recap ("Wrapped")
- Monthly recap generated on the first open of a new month for the previous month; yearly recap for the
  previous year available Jan 1–14 (PROPOSED). Idempotent; stored so it can be reopened.
- Contents: quests completed, Focus Gate hours, best streak, consistency, top stat + growth, km/steps
  and sleep average (only if HC on), levels gained, one insight, achievements earned.
- 3–5 swipeable story-style cards; final card exports as a 1080×1350 share image via the existing
  ShareCard path. Reduce-motion variant.

### F27 Identity framing
- Onboarding gains "Who are you becoming?" before focus-stat selection: pick 1–3 from a list of ~12
  identity statements (e.g. "someone who trains", "someone who reads", "someone who ships") in
  `assets/identities.json`, each mapped to a primary stat. Skippable.
- Picks pre-select the focus stats (user can still change them). Shown under the name on Status;
  editable in Settings; referenced in the Weekly Review ("Did you act like someone who reads?").
- No rewards attached.

### F28 Clean / System presentation mode
- Setting: Presentation → Clean | System. New installs default to Clean.
- After the first level-up, show a one-time invite: "Want the full System experience?" [Try it] [Keep
  clean]. Never shown again after a choice.
- Lexicon mapping (Clean → System), add any terms you find in the UI that aren't listed:
  Profile → Hunter · Focus session → Gate · Challenge → Dungeon · Today's plan → Daily Directive ·
  Comeback → Penalty Zone · Promotion challenge → Rank Trial · Reward chest → Relic Cache ·
  Shards → Loot Fragments · "Daily focus" → "System message".
- Ranks E–S, levels, XP and streaks are the same in both modes.
- System mode keeps the current System voice lines; Clean mode uses plain variants
  (add `system_lines_clean` alongside the existing set).
- Visuals: in this release only the labels and copy voice change; System mode may keep the gate glow,
  Clean mode turns it off. Do NOT redesign visuals here (Part 4 is separate).

---

## Part 4: UI overhaul notes (document only, do not implement)

Create docs/UI_OVERHAUL_NOTES.md capturing the target direction so the refactor in Part 2 serves it:
- Reference: Withings Health Mate (Mobbin). Inspiration only: do not copy their assets, icons, brand
  colours, or layouts pixel-for-pixel. I will add reference screenshots to docs/ui-reference/ later.
- Direction: premium, calm, health-dashboard feel. Light-first with an equally polished dark mode.
  Generous whitespace, soft rounded cards on a quiet background, very large numerals for key metrics,
  smooth trend charts with subtle gradients, restrained accent colour, refined typography, gentle motion.
  Data is the hero; game elements (XP, rank) appear as clean, compact modules, not neon windows.
- System mode becomes a theme layer on top of the new design (accent + glow + copy), not a separate UI.
- List every screen and component that will change, and confirm each consumes only semantic tokens,
  ChartKit and the Lexicon (anything that doesn't is a Part 2 bug; fix it).
- Note the open decisions for the redesign: typeface, accent colour, card style, chart style, icon set,
  motion language.

---

## Workflow

- Plan mode first for each part; list the files you will touch.
- After each part/feature: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint` green,
  screenshot tests updated deliberately (never blanket-re-record without telling me why), then commit
  with a conventional message.
- Run the review prompt from PROMPTS.md after each feature, adding: "check no new network call, no XP
  for reflection, no raw tokens in feature/**, every new term goes through the Lexicon".
- Anything ambiguous: list it with a proposed resolution and stop. Do not guess numbers.
