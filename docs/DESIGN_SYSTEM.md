# AWAKEN — Design System & UX Spec (v2 redesign)

## 1. Direction in one paragraph

**Premium, Apple-quality, calm** (v2, 2026-10-27; references in `docs/ui-reference/`, inspiration only). Apple HIG spirit:
- **Clarity:** one primary thing per screen, a big number or a single question. Hierarchy comes from type size and weight, not colour.
- **Deference:** content first. No decoration that doesn't carry information.
- **Restraint:** if in doubt, remove it. Whitespace is a feature.

The page is a warm neutral and cards are flat, separated from it by colour alone. Accents are small and few. The app follows the phone's light or dark setting (or the Appearance choice), and both looks are equally finished. The old **System mode** (dark palette, glow, System words) was **retired in v2**: the app always speaks Clean words (the Lexicon stays as infrastructure, §8.3).

**Banned everywhere:**
- Effects and colour: gradients on backgrounds or cards, glows, neon, navy or purple tints, coloured card borders, more than one accent colour per card.
- Shapes and icons: outlined coloured icon circles, emoji in UI, mixed icon sets.
- Type: uppercase headers.
- Motion: pulsing or bouncing animations.

What we are explicitly not: Habitica's pixel-art toybox, a generic Material app, an anime fan-page, or a copy of any app's assets, icons or colours.

## 2. Tokens

### 2.1 Colour — two looks, one set of tokens

Only `core/designsystem/theme/Color.kt` holds hex values, and `TokenSourceTest` fails the build if a `Color(0x…)` appears anywhere else. `AwakenPalette` has two instances, **Light** and **Dark**. Feature code reads the semantic `AwakenColors` (§8.4), never a palette. Every screen sits on `ScreenBackground`, a flat page-colour fill with no glow.

| Role (semantic token) | Light | Dark |
|---|---|---|
| Page (`surface`) | `#F5F4F1` | `#0F0F0F` |
| Card (`surfaceElevated`) | `#FFFFFF` | `#1F1E1C` |
| Card pressed, wells, selected nav pill, body map (`surfaceOverlay`) | `#EFEDE8` | `#2A2927` |
| Divider, ring tracks (`divider`) | `#E6E4DF` | `#343330` |
| Text primary / secondary / muted | `#111111` / `#6B6965` / `#9A978F` | `#F5F4F1` / `#A8A6A1` / `#75736E` |
| Primary button fill (`accent`) / its text (`onAccent`) | the primary ink / the page colour | same |

**Accents** are the same in both looks. They appear only on small icons, chips, chart lines and progress fills. They never colour a card background or body text.

| STR | INT | VIT | WIL | CHA | positive | negative |
|---|---|---|---|---|---|---|
| `#E8B931` | `#A99BF0` | `#5FBF8F` | `#6FA8F5` | `#F08A7A` | `#4CC38A` | `#E5675A` |

How the accents are used:
- **Stat icon wash:** `statTint` is the accent at 15 %, the circle behind a stat icon.
- **Reward:** reward gold = STR.
- **Ranks:** E is secondary ink; D, C, B, A and S take VIT, WIL, INT, CHA and STR.
- **Fixed looks:** the share image uses Dark. The widget follows the phone setting (Glance day/night colours from the Light and Dark palettes).

`PaletteContrastTest` keeps these floors on cards:
- Ink ≥ 7:1.
- Secondary ≥ 5:1 on cards, ≥ 4.5:1 (AA) on the page; the light page measures 4.98:1.
- Button text on its fill ≥ 4.5:1.

The accents are fixed by the brief and are decorative: several (STR, VIT, CHA, positive) fall under 3:1 on white. They therefore never carry meaning alone: every stat icon and chip carries text. Colour is never the only signal.

### 2.2 Typography — Inter (SIL OFL, `res/font/inter.ttf`, variable)

One family, weights pinned via `FontVariation`. Every style has **tabular figures** (`tnum`) so numbers never jitter while counting. Large roles tighten tracking.

| Style | Weight | Size (line) | Use |
|---|---|---|---|
| `metricXL` | 600, −2 % | 56 (60) | The one big number on a screen |
| `metricUnit` | 500 | 20 (24) | Unit beside a big number ("kg", "of 10"), secondary ink |
| `metricL` | 600, −2 % | 34 (40) | A value in a metric card |
| `headline` | 700, −2 % | 34 (41) | Large title; collapses to `title` on scroll |
| `section` | 600 | 22 (28) | Section headings ("Morning") |
| `windowTitle` | 500 | 17 (22) | Card titles, list-row titles |
| `title` | 600 | 17 (22) | Collapsed screen title, buttons |
| `numeric` / `numericSmall` | 600 / 500 | 20 / 15 | Values in rows |
| `body` | 400 | 15 (21) | Text |
| `label` / `overline` | 500 | 13 (18) | Chips, small labels over values |
| `caption` | 400 | 13 (18) | Secondary lines, hints |

Sentence case everywhere. No truncation at 200 % font scale: text wraps, rows grow.

### 2.3 Shape, space, elevation

- **Grid:** 8dp. Screen margin 16dp (`Spacing.gutter`), 12dp between cards (`Spacing.cardGap`), 32dp between sections, content max width 560dp.
- **Cards** (`shapes.window`): 24dp radius, card colour, **no border, no shadow**, 20dp padding. Cards that hold rows use 16dp. A tappable card turns the pressed colour while held.
- **Inner wells** 16dp radius; tiles 10dp; **sheets** 28dp top corners.
- **Buttons:** pills, 52dp tall.
  - Primary: solid primary ink with page-coloured text. Destructive uses negative.
  - Secondary: the pressed-card colour.
  - Quiet: ink text.
- **Chips:** 24dp pills, ink text on a 15 % wash of their accent.
- **Choice chips:** 44dp. Selected is solid ink with a check.
- **XP bar:** an 8dp pill, divider track, solid gold fill.

### 2.4 Iconography — one hand-drawn set (`AwakenGlyphs`)

All icons are drawn in `components/Glyphs.kt`, after the solid, friendly glyphs in the references. No icon library is used.
- **Grid:** 24 units, 2-unit safe area.
- **Line icons** (chrome, unselected nav): 1.75 stroke, round caps and joins.
- **Solid icons** (selected nav, stats, streak flame, insights): filled shapes rounded by a 1.5 stroke of the same colour.
- **Stat glyphs:** STR bolt, INT open book, WIL target, VIT heart, CHA two people.
- **Nav icons:** home, dumbbell, bowl with a leaf, rising bars, each with a solid twin.
- **Catalog:** `AwakenGlyphs.all` lists every icon and the catalog shows them as a grid.

### 2.5 Motion — 200–300ms ease-out, nothing pulses or bounces

| Token | Value | Use |
|---|---|---|
| `quick` | 200 ms | Press, toggles, nav pill, card press colour |
| `standard` | 250 ms | Sheets, list changes, stagger items |
| `reveal` | 300 ms | Cards fade in and rise 8dp; body-map fill |
| `countUp` | 900 ms | Numbers count up; rings close |
| `stagger` | 30 ms step | Screen entrance (max 6 steps) |
| press | `quick`, scale 0.98 | Buttons settle slightly while held |
| `ceremony` | ~1.6 s | Level-up / rank-up only |

- **Easing:** ease-out `cubic(0.2, 0, 0, 1)` for all of them.
- **Reward sheet and level-up:** restrained and celebratory. The number counts up, a thin ring closes, one haptic lands, and the formula shows as clean rows. No explosions, no type-on.
- **Reduce motion** (system setting or in-app toggle): crossfades only, no counting, no rising; ceremonies are a still final state.

### 2.6 Haptics
The `Haptics` wrapper respects the in-app toggle (ENGINE_DECISIONS #26):
- `tick`: checkbox, chip, swipe threshold, nav.
- `confirm`: quest completed.
- `crit`: two quick clicks.
- `levelUp`: when the level lands.
- `reject`: gate collapse, penalty.

### 2.7 Sound
Off by default (toggle ready, assets later). Never ship a sound you didn't license.

## 3. Components (`core/designsystem/components`)

| Component | Spec |
|---|---|
| `AwakenCard(title?, onClick?)` | The card (§2.3). `SystemWindow` is its v1.0 name, kept so older screens compile; `accent`/`tint` are ignored. |
| `MetricCard` | Icon + label top-left, timestamp + chevron top-right, big value + unit, a secondary line or `DeltaPill`, optional `Spark` (line or mini-bars) on the right. One TalkBack node. |
| `RingCard` / `Rings` | Apple Fitness rings: concentric, 10dp stroke, round caps, divider-coloured track, closing from 0 (`countUp`). The card puts label, big value + unit and a secondary line left, rings right. `HeroRing` is the single ring with the number inside (level-up). |
| `InsightCard(text)` | "Highlights": one sentence, a small accent icon in a 15 % circle, chevron when it opens something. |
| `ListRow` / `IconCircle` | 36dp filled circle at 15 % of the stat colour with the icon, title (17sp), secondary line, trailing control. `QuestRow` is built the same way with a hollow 24dp check circle (filled ink + check when done), a small `Main` chip and a coral `Comeback` chip on the penalty quest; rows split by an inset hairline. Swipe right completes (SELF only). |
| `DeltaPill` (`TrendPill`), `Pill`, `StatChip`, `DifficultyChip`, `VerifiedBadge`, `ChoiceChip` | §2.3. |
| `SegmentedControl` (`SegmentedTabs`) | Pressed-colour track; the selected segment in the card colour. Week / Month / Year, Front / Back. |
| `AwakenBottomSheet` / `ValueRow` | Card colour, 28dp corners, short divider handle, 22sp title, 24dp padding; label / value rows. |
| `EmptyState`, `SkeletonCard` | One line + one action; static pressed-colour blocks (never pulsing). |
| `LargeTitleScaffold` / `GreetingHeader` / `HeaderIconButton` | Date over the 34sp large title with header actions (ink icons on a card-coloured circle); a 17sp title fades in, pinned with a hairline, once the large title scrolls away. |
| `FloatingNavBar` | A floating pill bar on the card colour, 16dp from the edges. The selected item sits in a lighter inner pill with its solid icon + label; the others show their line icon. |
| `LevelModule` / `StreakModule` | Level and streak as two metric cards (Today). |
| `TimeBlockHeader` | A block as a section heading: name (22sp), "Now" chip on the current block, "2 of 3 done", chevron. |
| `XpBar`, `RankCrest` | §2.3; rank letter in ink on a 15 % disc of its colour. |
| `RewardSheet` | Ring closing around a check, "Quest complete", **+94 XP** counting up, gains as list rows, the formula as label / value rows (Base, difficulty, Streak ×1.20, Verified ×1.30 … Total 94 XP), Done. |
| `BodyMap` | §4.11. |
| `ScreenTimeTile`, `HealthTile` | Today's half-width summary cards above the quests (equal height in one row). Screen time: `ChartSpec.SegmentedRing` (calorie-ring style, `tileRing`) by category + a minutes-first legend; Health: steps / distance / active / last night as a 2×2 `FlowRow` that drops to one column under `tileFigureMinWidth`. Empty states offer their fix ("Grant access", "Set up a source"); unknown is "—", never 0. |
| `ConfirmDialog` | Yes/no before a regrettable action: title, one plain sentence, the action (critical red when destructive) and Cancel; on the card colour. Used by habit Archive/Delete (an Undo snackbar follows). |
| `QuestRow(onLongPress)` | Optional long-press action that replaces long-press-to-open, also offered to TalkBack (habits: "Habit options"). |
| `SystemToast`, `GateDial`, `PathLadder`, `PathQuestBadge`, `FeedbackChips`, `OracleStatus`, `ShareCard` | Wear the tokens; restyled with their screens (step 3). |
| ChartKit (`core/designsystem/charts`) | §8.5. Trend charts get a Week / Month / Year control, clean axes, a callout on tap and a dashed average line in step 3. |

**Quality bar for every screen:**
- Aligned to the 8dp grid, with consistent spacing.
- Numbers use tabular figures.
- No truncated text at 200 % font scale.
- Contrast passes.
- Works in light and dark.
- `@PhonePreviews` renders both looks.

## 4. Screens & flows

Navigation: bottom bar (§3) with 5 destinations — **Today**, **Focus**, **Gym**, **Diet**, **Progress** (the Status screen). **Gates** and **Vault** open from round icon buttons in Today's greeting header; **Paths** opens from the Progress header; Settings from Progress top-right. Every screen is on the v2 design (2026-10-27): a large title that collapses on scroll, an icon back button on pushed screens, flat cards; the sketches below show layout, not final copy.

### 4.1 Awakening (onboarding) — target < 60 seconds without a Path, < 2 minutes with one; no account

```
[1] Black screen. System window types on:
    "YOU HAVE BEEN CHOSEN."  → body: "Real effort becomes power here. Ready?"  [Accept]
[2] Name your hunter            [text field]                       [Continue]
[3] Pick two focus stats        (5 stat tiles, sigil + one-line meaning)  select 2
[4] Calibration (3 taps)        "How often do you train?"  Rarely / Sometimes / Often
                                "Hours of focused study on a normal day?" <1 / 1–3 / 3+
                                "Sleep most nights?" <6h / 6–7h / 7h+
                                → sets baseline tier per stat (GD §2)
[5] Verified quests (optional)  "Let the System see your steps, workouts and sleep
                                 from Health Connect. Verified quests earn 30% more XP.
                                 Data never leaves this phone."   [Connect] [Not now]
[6] Your first Path (optional) "What are you leveling toward?"  goal field with example chips
                                ("Reach 1900 on Codeforces", "Run a 10K", "Read 12 books", "Learn Japanese")
                                → current level field → [Continue] → AI consent sheet (§4.8) → [Enable] / [Not now]
                                [Skip] is always visible and equal weight to Continue.
[7] Daily directive time        time picker (default 07:00)
[8] "AWAKENING COMPLETE" window → first directive slides in on Today. If a Path was created, its window on
                                Paths shows "Charting your path…" and its quests join the Directive when ready.
```
First quest is guaranteed Easy and doable now (e.g., "Drink a glass of water" VIT / "10 push-ups" STR by focus) → first level-up within 2 minutes of install. This never waits for the AI.

### 4.2 Today
```
┌──────────────────────────────────┐
│ Tuesday 3 November               │  the date is the title; no greeting, no header icons
│ [Screen time ◔] [Steps ◔ · sleep]│  metrics first: screen-time and health tiles, reels under them
│ ┌ Level 12 · Rank D   14-day … ┐ │  2. Header: thin XP bar; tap → Progress
│ │ ▬▬▬▬▬▬░░░  1,240 / 1,800 XP  │ │
│ └──────────────────────────────┘ │
│ Today's plan · 3 of 7 done  Plan day  Habits │  3. Today's plan
│ ┌ Comeback ────────────────────┐ │  only after a missed day: quest + "never miss twice" line
│ Morning            1 of 1 done › │  past block: collapsed
│ Day  [Now]         0 of 2 done ⌄ │
│ ┌ (♥) Call your family      ◯ ┐  │  list rows, hollow check
│ Evening: … + Phone-down night row│
│ Anytime: … + App limits row      │
│ Your weekly review is open     › │  Sat–Mon only
│ Quit counters                    │  4. only if any exist: "Smoking · 12 days clean"
└──────────────────────────────────┘
```
- **Metrics first, then at most four sections:** the live metrics (screen time, steps and health, reels) sit directly under the title and load before the plan; then Header, Today's plan, Quit counters. Focus is its own tab (§4.9). The plan's header carries the two quiet actions *Plan day* and *Habits*.
- **The plan:** every directive item and habit, grouped by time-of-day block (GD §14.5) in the order Morning → Day → Evening → Anytime.
  - Each block is a section title over a card of rows; empty blocks are hidden.
  - The current block is marked "Now"; earlier blocks start collapsed (tap to expand; open quests stay completable).
  - The current block is recomputed whenever Today re-derives, not on a timer.
- **Comeback row** (GD §7.3): after a missed day the comeback quest and the calm "never miss twice" line share one card at the top of the plan. No red tint, no glow.
- **Rank-up test:** while one is available or running, its group follows the comeback row.
- **Phone-down night** is a plan row: tonight's window in Evening (with a *Wind down* link from 30 minutes before bedtime), last night's result in Morning. **App limits** is a row in Anytime.
- **Empty habits:** the "Add habit" empty state stays inside the plan.
- **Metrics:** each tile opens its detail; missing access shows the existing grant or set-up prompt, never a 0.
- **Debug:** the Debug entry lives in Settings → Developer (debug builds only), never floating over content.

### 4.3 Quest detail (long-press / tap title)
Description, target, stat split, verification method with explanation, [Begin] (starts min-duration timer) / [Complete], reroll (side and Path quests), "Why this quest?" line ("INT neglected for 3 days"; for Path quests, the model's `rationale`). GATE-mode Path quests replace [Begin]/[Complete] with [Enter C-Gate · 50 min]. Path quests have an overflow menu item "Report this quest".

### 4.4 Gates
Grid of 4 gate ranks (E/D/C/B) as portal tiles with duration; pick stat + optional label → `GateDial` full screen. Leaving the app shows the breach budget in the ongoing notification. Clear → RewardSheet. Collapse → `ember` window "GATE COLLAPSED" + "Background time 1:42 exceeded the 1:30 limit."

### 4.5 Status
HunterHeader (large) → RadarChart (this week vs last) → stat list (level + XP to next) → Heatmap → Weekly Report card with [Share].

### 4.6 Vault
Gold balance and Streak Shields (owned/2), one streak freeze item and a note on rerolls. Purchase = confirm sheet showing price and resulting balance.

### 4.7 Settings
Plain sentence-case section titles on the page colour, in this order:
1. **Profile:** name, focus stats.
2. **Day and reminders:** day starts at, morning ends, evening starts, daily reminder, notifications-blocked notice.
3. **Goals:** daily focus goal, daily step goal.
4. **Health and training:** Health Connect (connect, view, disconnect), equipment, injury mode.
5. **Phone and focus:** screen time and app limits, Focus Lock (setup, allowed apps), phone-down nights, Reel counter (setup, limits, stats, permissions; the inspector row only in debug builds).
6. **Habits:** archived habits.
7. **AI Paths:** debug and internal builds only.
8. **Appearance and sound:** theme, timer sound, haptics, reduce motion.
9. **Your data:** export, import, privacy policy, licences, version.
10. **Developer:** debug builds only.

### 4.8 Paths
- **Paths tab:** up to 3 Path windows, each: goal as window title, primary-stat sigil, `PathLadder` (collapsed to the current milestone), this week's quests with outcomes, `OracleStatus`. Empty state: "Name what you're leveling toward. The System will chart the way." [Create path]. Create is disabled with a reason at 3 active Paths.
- **Create / edit Path sheet:** goal (example chips), current level, notes ("anything the System should avoid?"), primary stat (pre-selected by keyword suggestion, user confirms), optional secondary, active days, quests per day (1/2), weekly time (slider 30–900 min with a live "≈ 45 min per active day" hint). Validation messages inline.
- **AI consent sheet** (shown once, before the first request):
  > **The System needs to consult the Oracle.**
  > To write your Path, AWAKEN sends your goal, current level, notes, and last week's quest results to our AI provider. No name, no account, no health data. Our server doesn't store it.
  > Rewards are always calculated on your phone by fixed rules. The AI only writes the quests.
  > [Enable AI Paths]  [Not now]  ·  [What exactly is sent?] → expandable list matching ARCHITECTURE §3.7
- **Milestone advance moment:** `SystemWindow` "MILESTONE CLEARED" with the next milestone title; [Advance] / [Stay]. No reward numbers on this screen (there are none).
- **Weekly recalibration line** on the Path window: "The System has raised the bar." (UP) / "Holding steady." (HOLD) / "The System has eased your path." (DOWN).
- **Report flow:** bottom sheet with reasons (Unsafe · Not relevant · Impossible for me · Offensive · Other) → "Reported. This quest has been removed." The slot leaves today's Directive immediately.

### 4.9 Screen time (GD §6.7)
Opened from Settings → Phone and focus → "Screen time" and from the Today screen. No tab.
- **No usage access:** a USAGE ACCESS window explains what is read (app times, on this phone, never sent, nothing blocked), then [Allow usage access] opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Returning to the screen re-reads.
- **TODAY:** total time as `displayXL`, a 6dp segmented bar (Distracting `ember` · Productive `verified` · Neutral `mist`; meaning, not decoration) and a legend with minutes per category.
- **SCREEN DISCIPLINE:** `+N XP` (`crit`) in the header; one status line (off / starts tomorrow / no limits / every limit holds / forfeited, "Your earned XP is untouched"); up to 4 limit bars (bar turns `ember` from 84 % of the limit); [Turn on] (with "Starts tomorrow" rules caption) or a quiet [Turn off].
- **APPS:** filter chips (All + 3 categories), rows with launcher icon, label, category · limit, minutes and a category pill; a `crit` caption "From tomorrow: …" when an edit is pending. Unused apps fold behind "Show N more apps". Tap → rule sheet: category chips, −5 / +5 min stepper (5–240), [Track without a limit], the caption "Applies from tomorrow, so today's limits stay fair.", [Save].
- **LAST 7 DAYS:** bars of total time (`edge`) with the distracting part (`ember`); each bar has a content description.
- **Today row:** an "App limits" row in the Anytime group (`positive` icon while holding, `critical` once forfeited) with "Limits holding · +N XP at day end" or "Limit crossed · stake forfeited". The DISCIPLINE quest is never a quest row.

### 4.10 (removed)

The 45-day Campaign screen was removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`).

### 4.11 Gym (GD §13.2)
Bottom tab. No session: [Start workout], THIS WEEK (workouts · sets · volume in `numeric`, then the **body map** (v2): one stylised V-taper figure from the MIT react-native-body-highlighter paths (`core/designsystem/body`, regenerate with `tools/body`; the generator widens shoulders and lats and narrows the waist), front / back via a segmented control, body in the divider tone with a 1.25dp muted-ink outline and 0.75dp seams so it reads on any card, no face, no skin tone; trained muscles fill in the STR accent at the heat steps for their weekly band (GD §13.2: Mon–Sun, four lit steps), animating in 300ms; each muscle is its own TalkBack node; tapping one opens a sheet with its sets and volume this week (ENGINE_DECISIONS #101), caption "Brighter = more work"), ROUTINES ([Start], remove), PERSONAL RECORDS (`crit` accent; "80 kg × 5 · e1RM 93.3 kg"), HISTORY. Running session: a window with the name and an "elapsed" timer, one window per exercise with columns Set · Previous · kg · Reps · ✓ (compact `NumberField`s; ✓ disabled at 0 reps), [Add set] / [Remove last set], then [Add exercise], [Finish], [Save as routine], [Discard] behind a confirm. Exercise library sheet: search, equipment chips, the body map as a tap-to-filter control plus the same choice as chips, result rows with [Add to workout]; detail sheet with primary (full) and secondary (40 %) muscles lit, pills for equipment/level/mechanic and numbered how-to steps. A quiet caption says the tracker grants no XP yet.

### 4.12 Diet (GD §13.1)
Bottom tab. First open: CALORIE BUDGET window (goal, sex, birth year/height/weight, activity as full-width choices) with a live engine preview ("2,749 kcal / day", BMR and maintenance, macros); [Save budget] is enabled only when every field is valid. Then: a date stepper (no future days), DAILY ENERGY with a `ProgressRing` (remaining kcal in the middle; `ember` once over) beside Eaten / Goal and bars for protein, carbs, fat and fiber; one window per meal with its kcal, rows "2 × 1 piece · 404 kcal" (tap to edit) and [Add food]; NUTRIENTS with a bar per target, limits turning `ember` with "Over limit". Sheets: food search (recent foods when empty; "kcal · P · C · F per 100 g"), amount (grams or a household portion × count, live macro line, meal chips), targets (grouped like a label, defaults shown, empty = default), custom food (per-100 g values and an optional serving).

## 5. Copy voice
- Plain, second person, sentence case. No story, no game jargon (hunter, gate, directive, trial, dungeon, oath, realm, sigil, relic, shard, chapter), no capitals for emphasis. `LexiconResourcesTest` scans every string for the jargon list.
- Button labels name the action: "Complete quest", "Start focus session", "Buy streak freeze".
- Errors say what happened + what to do: "Health Connect isn't installed. Install it from Play Store to verify steps automatically." No apologies, no jokes.
- Never shame. A missed day is stated plainly ("Your streak reset. Finish the comeback quest today."), never insulted.

## 6. Accessibility floor (release blockers)
TalkBack labels on every interactive element and chart (radar exposes "Strength level 12, Intelligence level 9…"); 48dp targets; font scale 200% doesn't clip; reduce-motion honoured; contrast table above respected; no information conveyed by colour alone.

## 7. Design prompt (historical)

The v1.0 premium-black prompt was retired with the v1.1 redesign; §1–3 above are the brief now.

## 8. v1.1 growth layer: components, screens, tokens and lexicon

Rules for everything below: semantic tokens only (§8.4), charts only through ChartKit (§8.5), every game term through the Lexicon (§8.3), strings in `strings.xml`, `@PhonePreviews` for every state (dark + light), 48dp targets, TalkBack text on every chart and meter, reduce-motion = crossfade only. Numbers come from GD §14.

### 8.1 New components (`core/designsystem/components`)

| Component | Spec |
|---|---|
| `ConsistencyMeter(cleared, eligible)` | A 6dp horizontal track (`surfaceSunken`) filled to the ratio in `accent`, with "22 of the last 28 days" in `label` and the count as `metricL`. No colour by value, no bands. `eligible = 0` → "—" and an empty track. Content description: "Cleared 22 of the last 28 days." Sits beside the streak in `HunterHeader`'s row on Today and on Status. |
| `InsightCard(insight)` | One card: a ChartKit mini comparison (two bars A/B, or the weekday bars, or a focus-hour sparkline), one correlational sentence in `body`, and a `caption` "Based on your last 84 days. A pattern, not a cause." Card states: insight, empty ("9 more days and your patterns will show."), no-pattern. The chart's content description reads the two values. |
| `NeverMissTwiceLine` | Replaces the daily System line on Today the day after a missed day: `textSecondary`, no icon, no `ember`. |
| `TimeBlockHeader(block, done, total, state)` | Section header inside the Directive window: block name, "2 of 3 done", chevron. States: current (accent rule on the left, expanded), past (collapsed, `textMuted`, tap to expand), upcoming (expanded, `textSecondary`). 48dp row; state announced to TalkBack ("Morning, past, 2 of 3 done, collapsed"). |
| `ReviewFlow` steps | `ReviewStepNumbers`, `ReviewTextStep(prompt, maxChars=280)` with a live counter, `ReviewFocusStep` (5 stat tiles, exactly 2 selected), `ReviewIntentionStep(maxChars=120)`; a step indicator "2 / 5"; [Skip] on every optional step with the same weight as [Next]. |
| `IdentityChips(selected, max=3)` | Wrapping selectable pills "Someone who reads" with the stat sigil; a 4th tap shows "Pick up to 3"; read-only variant under the name on Status. |
| ~~`ModeSwitch(mode)`~~ | **Not built (F34 superseded by the v2 redesign).** Two-segment control Clean / System with one line of explanation each ("Plain words, calm look." / "The full System: Gates, Hunters, glow."). Also used inside the one-time invite sheet. |

### 8.2 New and changed screens

- **Today:** `ConsistencyMeter` beside the streak; the Directive window grouped by `TimeBlockHeader` (Morning → Day → Evening → Anytime), penalty still pinned on top; `NeverMissTwiceLine` when applicable; a "Weekly review is open" row between Saturday and Monday until done.
- **Status:** identities under the name; INSIGHTS section (up to 3 `InsightCard`s) after the radar; consistency next to the heatmap; a RECAPS list (month/year rows) after the Weekly Report.
- **Weekly Review** (new route, from the Today row, the Sunday notification and Status): five steps in one window; finishing shows "Review saved" and any achievement toast. No XP anywhere on these screens.
- **Onboarding:** new step "Who are you becoming?" (`IdentityChips`, [Skip]) before focus stats; the focus-stat step arrives pre-selected.
- **Settings:** Identities, Time blocks ("Morning ends", "Day ends" hour pickers), Light/Dark debug toggle stays debug-only (Part 2).
- **Habit editor:** a block picker (Morning / Day / Evening / Anytime).
- ~~**System invite sheet**~~: not built (F34 superseded).

### 8.3 Words (one vocabulary)

There is one set of strings (`res/values/strings*.xml`) in plain words. `core/designsystem/lexicon/Lexicon.kt` keeps only the entry points `lexString` / `lexPlural` (composition) and `LexResources` (snackbars, notifications via `LexiconSource`); `FeatureBoundaryTest` makes feature code use them instead of `stringResource`. There is no presentation mode, no `_clean` twin and no `term_*` resource.

| Old game word | Plain word |
|---|---|
| Hunter | Profile |
| Gate | Focus session |
| Gate collapsed / Breach | Session ended early / Time away |
| Daily Directive / Directive cleared | Today's plan / Plan done |
| Penalty Zone / Penalty quest | Comeback / Comeback quest |
| Rank Trial | Rank-up test |
| Awakening | Getting started |
| Vault | Store |
| Status | Progress |
| Streak Shield | Streak freeze |
| Recalibrated | Difficulty adjusted |
| Oracle | AI planner (debug and internal builds only) |

Ranks E–S, level, XP, gold, streak, stats, quest and Path keep their names.

### 8.4 Token architecture (Part 2 refactor; no visual change)

Three layers. Only `core/designsystem/theme` may name layer 1.

1. **Palette** (raw values, today's §2.1 / §2.1b hex): `AwakenPalette.Dark` / `.Light`.
2. **Semantic tokens** (`AwakenTheme.colors`): `surface`, `surfaceElevated`, `surfaceSunken`, `textPrimary`, `textSecondary`, `textMuted`, `accent`, `accentMuted`, `positive`, `warning`, `critical`, `divider`, `chartSeries1..6`, `chartGrid`, plus the existing meaning colours (rank, stat, `crit`) behind semantic names. Mapping for v1.1 (identity, so screenshots match): `surface` = void, `surfaceElevated` = panel, `surfaceSunken` = depth, `textPrimary` = bone, `textSecondary` = mist, `textMuted` = fog, `accent` = system, `accentMuted` = edge, `positive` = verified, `warning` = crit, `critical` = ember, `divider` = edge, `chartGrid` = edge, `chartSeries1..5` = stat colours STR…CHA, `chartSeries6` = mist.
3. **Type roles** `metricXL` (= displayXL), `metricL` (= display), `title`, `body`, `label`, `caption` (+ `windowTitle`, `numeric` kept). **Spacing** `space.xs…xxl` (4…48), **radius** `radius.chip/card/sheet`, **elevation** `elevation.flat/raised` (both 0 today; depth stays hairlines).

Enforcement: a unit test scans `feature/**` and fails on raw palette names, `Color(0x…)`, `#hex`, or literal `.dp`/`.sp` outside layout-only modifiers (`padding`, `size`, `width`, `height`, `Spacer`) that use spacing tokens.

### 8.5 ChartKit (`core/designsystem/charts`)

`Chart(spec, modifier, content)` with sealed `ChartSpec`: `Radar`, `Heatmap`, `Ring` (content in the middle), `Progress` (square / chamfer / pill bar), `Segmented` (weighted segments), `Columns` (labelled bars in a fixed area), `StackedColumns` (base + highlighted part), `CalendarGrid` (day cells). The redesign adds `TrendLine`, `Sparkline` and `Comparison`. Specs that carry meaning take a caller-built `description`; decorative ones are hidden from TalkBack and the caller lists the numbers. The existing `RadarChart`, `Heatmap` and `ProgressRing` sit behind it unchanged. Feature code never touches `Canvas`, `draw*` or styling modifiers: section pieces that used to be drawn in screens live in `components/Sections.kt` (`HoldToConfirmRing`, `SelectableTile`, `MetricTile`, `LegendSwatch`, `IconPlaceholder`, `CosmeticSwatch`, `Modifier.penaltyTint`, `ShareCapture`).

## 9. v1.2 feature batch: components, screens and states (PROPOSED with GD §16)

Same rules as §8: semantic tokens only, drawing only inside `core/designsystem` (ChartKit for charts), every word through the Lexicon (`lexString` / `lexPlural`; plain words only (`LexiconResourcesTest`)), `@PhonePreviews` (or `@ComponentPreview` for components) for **every state listed below**, dark + light, 48dp targets, a TalkBack description on every ring, crystal, chart and counter, reduce motion = no growth/celebration animation (instant state). No screen shows an XP number that GD §16 does not pay.

### 9.1 New components (`core/designsystem/components`)

| Component | Spec | States (each a preview) |
|---|---|---|
| `QuantityStepper(progress, target, unit)` | − / + round buttons (48dp) around "5 / 8 glasses" in `numeric`; a thin `ChartSpec.Progress` under it. − is disabled at 0. | 0 / partial / complete (accent check) / disabled (skipped) |
| `HabitTypeChip(type, frequency)` | small pill on a habit row: "8 glasses", "20 min", "Holding", "2 of 3 this week" | one per type + flexible |
| `NegativeHabitRow` | row with "Holding" in `textSecondary`, [I slipped] text button, [Kept it today] button enabled only in the evening window; slip shows "Slipped today. Tomorrow is a new day." | holding (before evening) / ready to confirm / kept / slipped |
| `PomodoroTrack(round, rounds, phase)` | row of R dots (done = filled accent, current = ring, upcoming = divider) and "Round 2 of 4 · Break" | focus / short break / long break / finished / collapsed |
| `FocusGoalRing(minutes, goal, streak)` | `ChartSpec.Ring` in `accent`, "80 / 120 min" inside, "Focus streak 4" under it; full ring at or over the goal | 0 / partial / met / no goal history |
| `ProgressionHint(text)` | one `caption` line with an up-arrow glyph above set 1: "Last: 50 kg × 12, 12, 12 → try 52.5 kg × 8" | weight up / reps up / add-load hint / none |
| `PrChip` | "New record" pill in `reward` with a short scale-in (none under reduce motion) and a light haptic | shown / hidden |
| `PlateDiagram(platesPerSide, unit)` | one bar with plate blocks sized by weight (both sides mirrored), labels under each plate | exact / short by N / below bar |
| `StepGoalRing(steps, goal)` | `ChartSpec.Ring` on the Health tile: "6,200 / 8,000"; "Goal reached" at ≥ goal | none / partial / reached |
| `QuitCounterCard(counter)` | name, "12 days" as `metricL`, "Best 30 days" `caption`, next milestone bar, optional "₹1,800 saved · 6 h back"; neutral colours only (no red, no `critical`) | running / day 0 (hours) / paused (doomscrolling, no data) / archived |
| `MilestoneLadder(milestones, reached)` | horizontal row 1·3·7·14·30·90·180·365, reached ones filled | none / some / all |
| `ProgramCalendar(days)` | `ChartSpec.CalendarGrid`: kept = accent, rest = muted, broken = outlined `warning`, today = outline, upcoming = divider | early / mid with marks / completed / finished with marks |
| `BreathingGuide(phase, secondsLeft)` | a circle that grows over 4 s and shrinks over 6 s; under reduce motion only the words "Breathe in" / "Breathe out" change | in / out / done |

ChartKit gains **`ChartSpec.TrendLine(points, description)`** (C7): one series, dots on points, min/max labels; the caller passes the description ("Top weight, 60 to 80 kg over 3 months, best 82.5 kg").

### 9.2 Copy rules for quit counters (D4)

Supportive, factual, never shaming. Yes: "Day 12", "Best run: 30 days. It's still yours.", "Every day counts again from here." No: "failed", "relapsed" in headings (the log is "Restarts"), no streak-loss alarms, no red. No medical or health-recovery claims ("your lungs…", "detox"), no diet advice. A one-line footer on the counters screen: "Need support? Talk to someone you trust or a local helpline." with no link and no phone number (no network, no regional data).

### 9.3 Screens and changes

- **Habit editor (A1/A2):** a type picker (Check · Count · Timed · Avoid) as a segmented control; Count shows target + unit, Timed a minutes stepper (15–180, 5s) and the derived difficulty as read-only text ("Normal, from 30 minutes"), Avoid hides target. Frequency: "On these days" (weekday chips) or "Times per week" (1–6 stepper). Reminder: off / time picker; disabled with the reason when two habits already have one. States: each type, limit reached, campaign/program habit (read-only).
- **Today habit rows:** CHECK unchanged; QUANTITY uses `QuantityStepper` inline; TIMED shows [Start · 20 min] (opens the focus session); NEGATIVE uses `NegativeHabitRow`; flexible rows show "2 of 3 this week". Long-press menu gains **Skip today** (with "1 skip left this week") and, when skipped, **Undo skip**. States: each type × active / done / skipped / missed.
- **Gates screen (B1/B2/B3):** `FocusGoalRing` at the top; a fourth tile **Pomodoro** next to presets/custom/stopwatch opening a setup sheet (four steppers, subject, stat, [Start]); a **Crystals** row opening the gallery screen. The running gate screen (`GateDial`) shows the `GateCrystal` growing behind the dial and, in Pomodoro, `PomodoroTrack`. Break screen: big countdown, [Skip break], [End session]. States: no goal history / goal met / Pomodoro focus / break / long break / session finished / collapsed.
- **Gym (C1/C2/C3/C7):** running session: `ProgressionHint` above set 1 of each exercise; `PrChip` on a set row that is a record; the finish summary lists records. Exercise row → **Exercise history** route: segmented range (Month · 3 months · Year · All), three `TrendLine` cards (Top weight, Best e1RM, Volume), then a session list; states: none / one session (no chart) / many. A plate icon on the weight field opens the **Plate calculator** sheet (`PlateDiagram`, kg/lb switch, [Edit bar & plates] → Gym settings). Gym settings add: bar weight, owned plates (chips), default rep range, per-exercise overrides (from the history screen's overflow).
- **Screen time (D1):** a PICKUPS card under TODAY: pickups (or "Screen-ons"), first pickup, last use yesterday, longest stretch; states: data / no access / unavailable.
- **Today Health tile (D2):** `StepGoalRing` replaces the steps number; Settings → Health gets "Daily step goal".
- **Phone-down nights (D3):** Settings → Phone and focus → "Phone-down nights" window (explanation, bedtime/wake pickers, "Changes apply from tomorrow night", allowed apps listed in words). Today plan row: Evening group "Phone down tonight 23:00–06:30 · +78 XP if kept" with [Wind down] from 30 minutes before; Morning group "Kept · +78 XP" / "Phone used at night · no XP" / "No usage data · no XP". Wind-down screen with `BreathingGuide` and [Done]. States: off / no access / evening / kept / broken / no data.
- **Quit counters (D4):** new route from Progress ("Quit counters" row) and the habits sheet. List of `QuitCounterCard`s, [Add counter] (preset chips, start date "Today / Earlier…", optional cost and minutes per day); counter detail: big day count, `MilestoneLadder`, money and time, **Restarts** log (date, trigger chip, note), [Log a restart] behind a calm confirm ("Restart the count? Your best run stays."). States: empty / one / several / detail with and without restarts / doomscrolling paused / no usage access.
- **Programs (F6):** entry from Progress ("Programs" row) next to Campaign. List of three programs (name, days, rules); detail with [Start] (reason shown when slots are short or a campaign runs); running: "Day 12 / 66", `ProgramCalendar`, today's rules; the break choice is a sheet on next open: "Yesterday wasn't kept. Restart from day 1, or continue with the day marked?" [Restart] [Continue] (equal weight). Finished: COMPLETED (achievement) / FINISHED (marks shown, no blame). States: list / detail / running / break choice / completed / finished / abandoned.
- **Quick Settings tile (A4):** label "Start focus"; subtitle the last-used session ("D · 25 min · Maths"); while running "18:24 left" (stopwatch: "32 min"); inactive look when nothing was ever started (tap opens Gates). Icon = the existing focus glyph as a monochrome vector.
- **Start focus widget (A5):** Glance 2×1: "Start focus" + last-used session line; running: "Running · ends 18:55" (stopwatch "since 18:30"); tap starts the last-used session in the app (or opens the running one); empty: "Pick a session" → Gates. Widget colours via `AwakenPalette` like the existing widgets (allowed for widgets, CLAUDE.md).

### 4.9 Focus tab
The home of everything about attention; new focus tools get a row here. Order: today's focus as the biggest number on the tab (`metricXL`) with progress to the daily goal, the 7-day total, the streak and **Start a session**; then *Block distractions* (Focus Lock, Allowed apps, Reel counter with today's count or its set-up, Daily limits); then *Reports* (the weekly focus report). Every row opens an existing screen; the tab holds no rules.

### 4.10 Onboarding components (docs/onboarding/PLAN.md)
Built on semantic tokens only, so the flow restyles with the app. All live in `core/designsystem/components`.
- **`OnboardingShell`**: back, logo, `SegmentedProgressBar` (5 segments, 300 ms ease-out); heading (two lines at most) and subtitle; content; a Continue button pinned to the bottom. Steps slide left and fade over 280 ms (`AwakenMotion.stepMs`), reversed on Back; reduce motion crossfades.
- **`WheelPicker`**: snapping list with a centred highlight band and fading neighbours; ticks on every new value; next / previous accessibility actions.
- **`RulerPicker`** (+ `BigValue`): ticks under a fixed needle, horizontal or vertical (grows upwards); every `majorEvery`th tick is taller.
- **`ImageChoiceCard` / `ImageChoiceGrid`**: a picture over a label (or the label alone), accent outline and check when selected; checkbox semantics for multi-select, radio otherwise. **`StageSlider`**: a 5-stop slider that crossfades the picture. **`OnbIllustration`**: a decorative picture. **`OnboardingTheme`**: forces the light palette for the flow.
- **`UnitToggle`**: a `SegmentedControl` that ticks. **`BmiCard`**: number, three-band scale with a badge from age 20, otherwise one neutral line; always information only.
- **Tokens added:** `AwakenColors.buttonPrimary` / `onButtonPrimary` (the primary button; today the same ink as `accent`), `AwakenMotion.stepMs`, and the `onb*` entries in `AwakenDimens`.
