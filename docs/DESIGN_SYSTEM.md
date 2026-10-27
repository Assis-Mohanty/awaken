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

Only `core/designsystem/theme/Color.kt` holds hex values. `AwakenPalette` has two instances, **Light** and **Dark**. Feature code reads the semantic `AwakenColors` (§8.4), never a palette.

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
- **Vault themes and frames:** they re-tint small marks only.
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
| `LevelUpCeremony` | §2.5. |
| `BodyMap` | §4.11. |
| `SystemToast`, `SystemWarning`, `ChapterReveal`, `GateDial`, `PathLadder`, `PathQuestBadge`, `FeedbackChips`, `OracleStatus`, `ShareCard` | Wear the tokens; restyled with their screens (step 3). |
| ChartKit (`core/designsystem/charts`) | §8.5. Trend charts get a Week / Month / Year control, clean axes, a callout on tap and a dashed average line in step 3. |

**Quality bar for every screen:**
- Aligned to the 8dp grid, with consistent spacing.
- Numbers use tabular figures.
- No truncated text at 200 % font scale.
- Contrast passes.
- Works in light and dark.
- `@PhonePreviews` renders both looks.

## 4. Screens & flows

Navigation: bottom bar (§3) with 4 destinations — **Today**, **Gym**, **Diet**, **Progress** (the Status screen). **Gates** and **Vault** open from round icon buttons in Today's greeting header; **Paths** opens from the Progress header; Settings from Progress top-right. Every screen is on the v2 design (2026-10-27): a large title that collapses on scroll, an icon back button on pushed screens, flat cards; the sketches below show layout, not final copy.

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
│ Tuesday, 27 October       (◎)(▣) │  date over the large title; Gates / Vault actions
│ Good morning, Assis              │  34sp; collapses to a 17sp pinned title on scroll
│ ┌ Today ───────────────── ╭──╮ ┐ │  RingCard: one green ring
│ │ 3 of 10                 │  │ │ │
│ │ done today              ╰──╯ │ │
│ └──────────────────────────────┘ │
│ ┌ ⚡ Level ────┐ ┌ 🔥 Streak ───┐ │  LevelModule + StreakModule (metric cards)
│ │ 24 rank C   │ │ 12 days      │ │
│ │ ▬▬▬▬▬░░     │ │ Best 30      │ │
│ └─────────────┘ └──────────────┘ │
│ ┌ ✦ INT is falling behind…   › ┐ │  InsightCard (one sentence)
│ Morning            1 of 1 done › │  past block: collapsed
│ Day  [Now]         0 of 2 done ⌄ │
│ ┌ (♥) Take a short walk  ◯ ┐    │  penalty quest: normal row + coral "Comeback" chip
│ │ (♥) Call your family   ◯ │    │  list rows, hollow check
│ └──────────────────────────┘    │
│ Evening / Anytime …              │
│ ┌ (▦) Day plan              › ┐ │  quiet rows: plan, habits, campaign, screen limits
│ │ (☰) Habits                › │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```
- **The plan:** every directive item and habit, grouped by time-of-day block (GD §14.5) in the order Morning → Day → Evening → Anytime.
  - Each block is a section title over a card of rows; empty blocks are hidden.
  - The current block is marked "Now"; earlier blocks start collapsed (tap to expand; open quests stay completable).
  - The current block is recomputed whenever Today re-derives, not on a timer.
- **The penalty quest** (GD §7.3) leads the current block as a normal row with a coral "Comeback" chip. No red tint, no glow.
- **Rank Trial:** its card follows the insight.
- **Day cleared:** the ring card's label reads "Plan complete".
- **Closing rows:** Day plan, Habits (with "Add habit" when empty), Campaign and Screen limits close the screen as quiet rows with chevrons.
- **Debug:** the Debug entry lives in Settings → Developer (debug builds only), never floating over content.

### 4.3 Quest detail (long-press / tap title)
Description, target, stat split, verification method with explanation, [Begin] (starts min-duration timer) / [Complete], reroll (side and Path quests), "Why this quest?" line ("INT neglected for 3 days"; for Path quests, the model's `rationale`). GATE-mode Path quests replace [Begin]/[Complete] with [Enter C-Gate · 50 min]. Path quests have an overflow menu item "Report this quest".

### 4.4 Gates
Grid of 4 gate ranks (E/D/C/B) as portal tiles with duration; pick stat + optional label → `GateDial` full screen. Leaving the app shows the breach budget in the ongoing notification. Clear → RewardSheet. Collapse → `ember` window "GATE COLLAPSED" + "Background time 1:42 exceeded the 1:30 limit."

### 4.5 Status
HunterHeader (large) → RadarChart (this week vs last) → stat list (level + XP to next) → Heatmap → titles & achievements grid (locked ones silhouetted with condition text) → chapters list → Weekly Report card with [Share].

### 4.6 Vault
Gold balance, Streak Shields (owned/2), themes, frames, sigils; Relic Cache (fragments 7/10). Purchase = confirm sheet showing price and resulting balance. Owned items show "Equipped"/"Equip".

### 4.7 Settings
Profile (name, focus stats), Day starts at, Reminder times, Rest day (pick next), Recovery mode, Health Connect, **AI Paths** (on/off, "What we send", revoke), Equipment I own (jump rope, dumbbells, pull-up bar, gym access), Injury mode (excludes impact/legs/upper tags), Haptics, Reduce motion, Export/Import data, Privacy policy, About, (debug) Debug screen.

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
Opened from Settings → Training → "Screen time" and from the Today card. No tab.
- **No usage access:** a USAGE ACCESS window explains what is read (app times, on this phone, never sent, nothing blocked), then [Allow usage access] opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Returning to the screen re-reads.
- **TODAY:** total time as `displayXL`, a 6dp segmented bar (Distracting `ember` · Productive `verified` · Neutral `mist`; meaning, not decoration) and a legend with minutes per category.
- **SCREEN DISCIPLINE:** `+N XP` (`crit`) in the header; one status line (off / starts tomorrow / no limits / every limit holds / forfeited, "Your earned XP is untouched"); up to 4 limit bars (bar turns `ember` from 84 % of the limit); [Turn on] (with "Starts tomorrow" rules caption) or a quiet [Turn off].
- **APPS:** filter chips (All + 3 categories), rows with launcher icon, label, category · limit, minutes and a category pill; a `crit` caption "From tomorrow: …" when an edit is pending. Unused apps fold behind "Show N more apps". Tap → rule sheet: category chips, −5 / +5 min stepper (5–240), [Track without a limit], the caption "Applies from tomorrow, so today's limits stay fair.", [Save].
- **LAST 7 DAYS:** bars of total time (`edge`) with the distracting part (`ember`); each bar has a content description.
- **Today card:** a SCREEN DISCIPLINE window under the directive (`verified` accent while holding, `ember` once forfeited) with "Limits holding · +N XP at day end" or "Limit crossed · stake forfeited" and a [Screen time] link. The DISCIPLINE quest is never a quest row.

### 4.10 The 45-day Campaign (GD §6.8)
Opened from the Today card ("CAMPAIGN · Day 12 / 45 · Build" while one runs, otherwise a quiet "Begin a 45-day campaign" offer). One route, steps inside it:
- **Intro:** what it is, the three acts (Roman numerals in `system`), the rule "Clear 32 of the 45 days… Missed days never reset anything" in `crit`, [Begin the scan].
- **Scan:** one question per window, accent = the question's stat colour, "3 / 10" in the header, four full-width answers (the chosen one filled), [Back].
- **Forge:** "SYSTEM · FORGE", a `displayXL` percentage, a thin bar and three checks; ~2.4 s, instant under reduce-motion; moves on only once the plan is ready.
- **Result:** "CHALLENGER DETECTED" in the weakest stat's colour, the archetype as `display`, one line, the scan on the existing `RadarChart`, "Strength · weakest. We start here."; then YOUR CAMPAIGN with dates and the planned habits (stat chip, title, target, days). A problem (no room, already running) replaces [Take the oath] with the reason in `ember`.
- **Oath:** "THE OATH" in `crit`, the commitment with the hunter's name, "Day 45: <date>", and a ring the hunter holds for 1.5 s to seal (haptic confirm); TalkBack's tap action seals too. Caption: nothing is signed or saved but the start date.
- **Progress:** "Day N / 45" as `displayXL`, act name, cleared vs needed, an on-course / out-of-reach line, and a 45-cell calendar (9 per row): cleared = `system`, today = `crit` outline, missed = `fog` outline, upcoming = `edge`. CAMPAIGN HABITS lists the habits and offers a quiet [Abandon campaign] behind a confirm dialog. Finished: REFORGED (`crit`) / CAMPAIGN ENDED / ABANDONED with [Start a new campaign].
- Campaign habits appear as ordinary habit rows on Today; their editor refuses edits with "This habit belongs to your campaign…".

### 4.11 Gym (GD §13.2)
Bottom tab. No session: [Start workout], THIS WEEK (workouts · sets · volume in `numeric`, then the **body map** (v2): one stylised V-taper figure from the MIT react-native-body-highlighter paths (`core/designsystem/body`, regenerate with `tools/body`; the generator widens shoulders and lats and narrows the waist), front / back via a segmented control, body in the divider tone with a 1.25dp muted-ink outline and 0.75dp seams so it reads on any card, no face, no skin tone; trained muscles fill in the STR accent at the heat steps for their weekly band (GD §13.2: Mon–Sun, four lit steps), animating in 300ms; each muscle is its own TalkBack node; tapping one opens a sheet with its sets and volume this week (ENGINE_DECISIONS #101), caption "Brighter = more work"), ROUTINES ([Start], remove), PERSONAL RECORDS (`crit` accent; "80 kg × 5 · e1RM 93.3 kg"), HISTORY. Running session: a window with the name and an "elapsed" timer, one window per exercise with columns Set · Previous · kg · Reps · ✓ (compact `NumberField`s; ✓ disabled at 0 reps), [Add set] / [Remove last set], then [Add exercise], [Finish], [Save as routine], [Discard] behind a confirm. Exercise library sheet: search, equipment chips, the body map as a tap-to-filter control plus the same choice as chips, result rows with [Add to workout]; detail sheet with primary (full) and secondary (40 %) muscles lit, pills for equipment/level/mechanic and numbered how-to steps. A quiet caption says the tracker grants no XP yet.

### 4.12 Diet (GD §13.1)
Bottom tab. First open: CALORIE BUDGET window (goal, sex, birth year/height/weight, activity as full-width choices) with a live engine preview ("2,749 kcal / day", BMR and maintenance, macros); [Save budget] is enabled only when every field is valid. Then: a date stepper (no future days), DAILY ENERGY with a `ProgressRing` (remaining kcal in the middle; `ember` once over) beside Eaten / Goal and bars for protein, carbs, fat and fiber; one window per meal with its kcal, rows "2 × 1 piece · 404 kcal" (tap to edit) and [Add food]; NUTRIENTS with a bar per target, limits turning `ember` with "Over limit". Sheets: food search (recent foods when empty; "kcal · P · C · F per 100 g"), amount (grams or a household portion × count, live macro line, meal chips), targets (grouped like a label, defaults shown, empty = default), custom food (per-100 g values and an optional serving).

## 5. Copy voice
- System lines: terse, second person, present tense. "Quest cleared." "The System has recalibrated your STR trials." "You missed yesterday. One quest stands between you and redemption."
- Human UI text: plain verbs, sentence case. Button labels name the action: "Complete quest", "Start gate", "Buy shield". A button that says "Start gate" leads to a notification "Gate in progress".
- Errors say what happened + what to do: "Health Connect isn't installed. Install it from Play Store to verify steps automatically." No apologies, no jokes.
- Never shame. Penalty copy is stern, never insulting.

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
| `RecapCard(page)` | Full-bleed story card: period kicker, one `metricXL` hero number, 1–3 supporting rows, page dots; swipe or tap edges to move; reduce-motion = crossfade, never auto-advance. The last card hosts [Share] (ShareCard path, dark in every mode). |
| `IdentityChips(selected, max=3)` | Wrapping selectable pills "Someone who reads" with the stat sigil; a 4th tap shows "Pick up to 3"; read-only variant under the name on Status. |
| `ModeSwitch(mode)` | Two-segment control Clean / System with one line of explanation each ("Plain words, calm look." / "The full System: Gates, Hunters, glow."). Also used inside the one-time invite sheet. |

### 8.2 New and changed screens

- **Today:** `ConsistencyMeter` beside the streak; the Directive window grouped by `TimeBlockHeader` (Morning → Day → Evening → Anytime), penalty still pinned on top; `NeverMissTwiceLine` when applicable; a "Weekly review is open" row between Saturday and Monday until done.
- **Status:** identities under the name; INSIGHTS section (up to 3 `InsightCard`s) after the radar; consistency next to the heatmap; a RECAPS list (month/year rows) after the Weekly Report.
- **Weekly Review** (new route, from the Today row, the Sunday notification and Status): five steps in one window; finishing shows "Review saved" and any achievement toast. No XP anywhere on these screens.
- **Recap** (new route): auto-opens once per GD §14.6, else from Status → Recaps.
- **Onboarding:** new step "Who are you becoming?" (`IdentityChips`, [Skip]) before focus stats; the focus-stat step arrives pre-selected.
- **Settings:** Presentation (`ModeSwitch`), Identities, Time blocks ("Morning ends", "Day ends" hour pickers), Light/Dark debug toggle stays debug-only (Part 2).
- **Habit editor:** a block picker (Morning / Day / Evening / Anytime).
- **System invite sheet:** after the first level-up in Clean mode; [Try it] / [Keep clean].

### 8.3 Lexicon (Clean ↔ System)

`Lexicon` (interface in `core/designsystem/lexicon`) exposes one function per term key; `CleanLexicon` and `SystemLexicon` read string resources `term_<key>_clean` / `term_<key>_system`. Provided by `LocalLexicon` from the `presentationMode` DataStore pref. Whole sentences that speak *as* the System ("THE SYSTEM HAS RECALIBRATED") get a full Clean string rather than term substitution, because word-swapping breaks grammar. Window titles are UPPERCASE in System mode and sentence case in Clean mode (the case is copy, not the type style).

| Key | Clean | System |
|---|---|---|
| `hunter` | Profile | Hunter |
| `gate` / `gates` | Focus session / Focus sessions | Gate / Gates |
| `gate_collapsed` | Session ended early | Gate collapsed |
| `breach` | Time away | Breach |
| `dungeon` | Challenge | Dungeon *(reserved; no UI uses it yet)* |
| `directive` | Today's plan | Daily Directive |
| `directive_cleared` | Plan complete | Directive cleared |
| `penalty_zone` | Comeback | Penalty Zone |
| `penalty_quest` | Comeback quest | Penalty quest |
| `rank_trial` | Promotion challenge | Rank Trial |
| `relic_cache` | Reward chest | Relic Cache |
| `fragments` | Shards | Loot Fragments |
| `system_message` | Daily focus | System message |
| `awakening` | Getting started | Awakening |
| `vault` | Shop | Vault |
| `status` | Progress | Status |
| `sigil` | Badge | Sigil |
| `chapter` | Story | Chapter |
| `oath` | Commitment | Oath |
| `streak_shield` | Streak freeze | Streak Shield |
| `crit` | Bonus | Critical |
| `recalibrated` | Adjusted | Recalibrated |
| `oracle` | AI planner | Oracle |

Unchanged in both: ranks E–S, level, XP, gold, streak, stats, quest, title, Path, campaign. Gate tiles keep their rank letter and duration in both modes ("C · 50 min" Clean, "C-Gate · 50 min" System). Clean mode turns off the gate glow (`GateBackground`); nothing else visual changes in v1.1.

### 8.4 Token architecture (Part 2 refactor; no visual change)

Three layers. Only `core/designsystem/theme` may name layer 1.

1. **Palette** (raw values, today's §2.1 / §2.1b hex): `AwakenPalette.Dark` / `.Light`.
2. **Semantic tokens** (`AwakenTheme.colors`): `surface`, `surfaceElevated`, `surfaceSunken`, `textPrimary`, `textSecondary`, `textMuted`, `accent`, `accentMuted`, `positive`, `warning`, `critical`, `divider`, `chartSeries1..6`, `chartGrid`, plus the existing meaning colours (rank, stat, `crit`) behind semantic names. Mapping for v1.1 (identity, so screenshots match): `surface` = void, `surfaceElevated` = panel, `surfaceSunken` = depth, `textPrimary` = bone, `textSecondary` = mist, `textMuted` = fog, `accent` = system, `accentMuted` = edge, `positive` = verified, `warning` = crit, `critical` = ember, `divider` = edge, `chartGrid` = edge, `chartSeries1..5` = stat colours STR…CHA, `chartSeries6` = mist.
3. **Type roles** `metricXL` (= displayXL), `metricL` (= display), `title`, `body`, `label`, `caption` (+ `windowTitle`, `numeric` kept). **Spacing** `space.xs…xxl` (4…48), **radius** `radius.chip/card/sheet`, **elevation** `elevation.flat/raised` (both 0 today; depth stays hairlines).

Enforcement: a unit test scans `feature/**` and fails on raw palette names, `Color(0x…)`, `#hex`, or literal `.dp`/`.sp` outside layout-only modifiers (`padding`, `size`, `width`, `height`, `Spacer`) that use spacing tokens.

### 8.5 ChartKit (`core/designsystem/charts`)

`Chart(spec, modifier, content)` with sealed `ChartSpec`: `Radar`, `Heatmap`, `Ring` (content in the middle), `Progress` (square / chamfer / pill bar), `Segmented` (weighted segments), `Columns` (labelled bars in a fixed area), `StackedColumns` (base + highlighted part), `CalendarGrid` (day cells). The redesign adds `TrendLine`, `Sparkline` and `Comparison`. Specs that carry meaning take a caller-built `description`; decorative ones are hidden from TalkBack and the caller lists the numbers. The existing `RadarChart`, `Heatmap` and `ProgressRing` sit behind it unchanged. Feature code never touches `Canvas`, `draw*` or styling modifiers: section pieces that used to be drawn in screens live in `components/Sections.kt` (`HoldToConfirmRing`, `SelectableTile`, `MetricTile`, `LegendSwatch`, `IconPlaceholder`, `CosmeticSwatch`, `Modifier.penaltyTint`, `ShareCapture`).
