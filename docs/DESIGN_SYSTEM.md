# AWAKEN — Design System & UX Spec (v1.1 redesign)

## 1. Direction in one paragraph

A **premium, calm, light-first health app that plays like a game** (v1.1 redesign; references in `docs/ui-reference/`, Withings Health Mate as inspiration only). Data is the hero: one very large number per screen inside a ring, small uppercase labels over values, smooth trend lines. The page is a warm off-white with soft rounded white cards and hairline borders instead of shadows; each stat has a soft pastel used for card tints and washes behind the hero. Game elements (level, XP, rank, streak) are compact, elegant modules, never glowing windows. Dark mode is an equally polished soft charcoal. **System mode** is the same layout on near-black with an electric accent, a glow around cards and the System's own words (Lexicon, §8.3); it is a theme layer, not a separate UI.

What we are explicitly not: Habitica's pixel-art toybox, a generic Material app with a purple FAB, an anime fan-page, or a copy of any health app's assets, icons or colours.

## 2. Tokens

### 2.1 Colour — three palettes, one set of tokens

`AwakenPalette` (only `core/designsystem/theme/Color.kt` holds hex values) has three instances: **Light** (default), **Dark** and **System**, plus a frozen **Legacy** premium-black palette used only by the share image and the widget until their own redesign. Feature code reads the semantic `AwakenColors` (§8.4) and never a palette. Clean mode picks Light or Dark from Settings → Appearance; System mode always uses System.

| Role (palette field) | Light | Dark | System |
|---|---|---|---|
| Page (`void`) | `#F6F4EF` warm off-white | `#131416` | `#09090B` |
| Sunken (`depth`) | `#EFECE6` | `#0F1012` | `#050506` |
| Card (`panel`) | `#FFFFFF` | `#1C1D20` | `#111115` |
| Well / sheet (`panelRaised`) | `#F4F1EC` | `#25272B` | `#1A1A20` |
| Hairline (`edge`) | `#E8E3DA` | `#2E3034` | `#26262E` |
| Ink (`bone`) | `#1B1A18` | `#F1EFEA` | `#EDEDED` |
| Secondary ink (`mist`) | `#67635C` | `#A7A39C` | `#9A9AA3` |
| Muted (`fog`) | `#ABA69D` | `#6F6C66` | `#5A5A63` |
| Accent (`system`) | `#3446C9` ink-indigo | `#9DAAFF` | `#7FD8FF` electric |
| On accent | `#FFFFFF` | `#141625` | `#041219` |
| Critical (`ember`) | `#C63D36` | `#FF7D73` | `#FF5468` |
| Reward / gold (`crit`) | `#8F6200` | `#F2C35C` | `#FFC94D` |
| Positive (`verified`) | `#1D7C51` | `#62D4A2` | `#5BE3B0` |
| Hero wash A / B | `#EDE6FF` lavender / `#FFEFD9` peach | `#262238` / `#2B2519` | `#0E2230` / `#1B1430` |

**Stat pastels** (as the user asked: STR warm yellow, INT lavender, WIL blue, VIT mint, CHA pink). Each stat has an **ink** (text and icons, ≥ 4.5:1 on cards and on its own tint), a **tint** (card and chip backgrounds) and a **fill** (rings, bars, chart lines and dots):

| Stat | Light ink / tint / fill | Dark ink / tint / fill |
|---|---|---|
| STR | `#7F5600` / `#FFF3D1` / `#F2BC45` | `#F4CB68` / `#2F2918` / `#E9B547` |
| INT | `#5640BA` / `#EEE9FF` / `#9F8BF5` | `#BBACFF` / `#26233A` / `#9F8CF5` |
| WIL | `#2B5DB5` / `#E5EEFF` / `#75A1F1` | `#96B7FF` / `#1E2739` / `#7EA6F5` |
| VIT | `#1A6F4D` / `#DFF4E9` / `#5CC598` | `#77DBAF` / `#1A2D25` / `#58C795` |
| CHA | `#A6385D` / `#FFE6EE` / `#EF8BAE` | `#FFA6C4` / `#34222B` / `#EE8BAE` |

Ranks E–S keep their meaning colours, darkened for text on white in Light and brightened in Dark (`Color.kt`). Colour is never the only signal: chips carry text, verification carries an icon and a word. `PaletteContrastTest` enforces the floors (ink ≥ 7:1, secondary ≥ 5:1, every hue and stat ink ≥ 4.5:1 on cards and on its tint) for Light, Dark and System.

Stay on Legacy: the share image (an image that leaves the app) and the Glance widget (BACKLOG "Light-mode widget").

### 2.2 Typography — Plus Jakarta Sans (SIL OFL, `res/font/plus_jakarta_sans.ttf`, variable 200–800)

One modern family for everything; weights pinned via `FontVariation`. Numbers are big and bold with tabular figures; words are calm.

| Style | Weight | Size (line) | Use |
|---|---|---|---|
| `metricXL` | 800, −3 % tracking | 60 (64) | The hero number in a ring |
| `metricL` | 700, −2 % | 34 (40) | A large value in a card |
| `headline` | 700 | 26 (32) | Greetings, screen titles |
| `section` | 700 | 20 (26) | Section headings ("Today's plan") |
| `windowTitle` | 700 | 17 (22) | Card titles (System mode: 16, +8 % tracking, UPPERCASE strings) |
| `title` | 600 | 17 (22) | Quest titles, module titles |
| `overline` | 600, +10 % tracking | 11 (14) | Small uppercase labels over values (RESULT, TREND) |
| `numeric` / `numericSmall` | 600, tabular | 20 (24) / 14 (18) | XP, gold, timers |
| `body` | 400 | 15 (22) | Descriptions |
| `label` | 500 | 13 (18) | Chips, metadata |
| `caption` | 400 | 12 (16) | Hints |

Sentence case everywhere in Clean mode; UPPERCASE belongs to System-mode window titles and `overline` labels. Font scaling up to 200 % must reflow.

### 2.3 Shape, space, elevation

- **Cards** (`shapes.window`): 20dp radius, `panel` fill, 1dp `edge` hairline (none on tinted cards), 20dp padding (`Spacing.card`). No shadows. System mode adds a 1dp accent edge at 35 % and a soft accent glow (`dimens.glow`).
- **Inner wells and tiles** (`shapes.inner`): 14dp; small swatches and calendar cells (`shapes.tile`): 8dp; sheets: 28dp top corners.
- **Buttons and chips**: fully rounded pills. Primary button 52dp tall, accent fill, on-accent text; secondary = hairline pill on card white; quiet = accent text.
- Spacing scale 4, 8, 12, 16, 24, 32, 48; screen gutter 20dp; card padding 20dp; section gap 28dp; content max width 560dp.
- XP bar 8dp pill with an accent → `aether` gradient.

### 2.4 Iconography

Custom line glyphs (`AwakenGlyphs`), 24-unit grid, 1.75 stroke, **rounded caps and joins**. Stat sigils sit on their pastel disc (`StatWell`). Header actions are icons on a 40dp round well with a hairline (`HeaderIconButton`). Time blocks use sunrise / sun / moon / clock; the bottom bar uses home, dumbbell, bowl and rising bars.

### 2.5 Motion — gentle

| Token | Value | Use |
|---|---|---|
| `quick` | 120 ms, `FastOutSlowIn` | Press, toggles |
| `standard` | 240 ms, emphasized decelerate | Sheets, list changes |
| `reveal` | 420 ms | Cards fade in and rise 8dp; the hero ring and numbers count to their value |
| `stagger` | 40 ms step | Screen entrance: top-level items rise 8dp and fade in (max 6 steps) |
| press | `quick`, scale 0.98 | Buttons settle slightly while held |
| `ceremony` | ~2.2 s | Level-up / rank-up only |

- **Level-up**: the page veils over, the hero ring fills while the level number rolls up, the title appears, stat gains fade in as pastel pills, heavy haptic when the number lands. No expanding neon ring.
- **Type-on** is the System's voice: System mode only, and only for important event titles.
- `reduceMotion` (system setting or in-app toggle) → crossfades only, no counting, no rising; the ceremony is a still final state.

### 2.6 Haptics
`Haptics` wrapper (respects in-app toggle): `tick` (checkbox, chip, swipe threshold), `confirm` (quest completed), `crit` (two quick clicks), `levelUp`, `reject` (gate collapse / penalty). Implementation in ENGINE_DECISIONS #26.

### 2.7 Sound
Off by default (toggle ready, assets later). Never ship a sound you didn't license.

## 3. Components (`core/designsystem/components`)

| Component | Spec |
|---|---|
| `AwakenCard(title?, accent, tint?)` | The card (§2.3). `SystemWindow` is kept as its v1.0 name so screens not yet redesigned compile unchanged. |
| `HeroRing(value, progress, unitLine?, kicker?, style)` | One hero number per screen: `RING` = a thick track with a stat-pastel sweep gradient and round caps; `DOTS` = two rings of soft pastel dots ("61 out of 100"). One TalkBack node. System mode glows. |
| `Modifier.heroWash()` | The lavender-to-peach wash behind a hero area. |
| `GreetingHeader` / `HeaderIconButton` | "Good afternoon, Assis" + date, with round header actions. |
| `LevelModule` / `StreakModule` | Level + rank crest + XP bar, and the streak with its best, as compact cards. |
| `InsightCard` | An insight or the daily line on the wash, with a sparkle. |
| `MetricLabel` / `TrendPill` | Uppercase label over a value; a green / ember / grey pill with an arrow. |
| `SegmentedTabs` | Week / Month / Quarter / Year: a soft pill track with the selected option on card white. |
| `StatTintCard` | A stat's pastel card: glyph well, name, short status. |
| `TimeBlockHeader` / `TimelineQuestRow` | Today's timeline (GD §14.5): block header with glyph, "2 of 3 done", "Now" pill and chevron; a node on a rail (filled check when done, ring in the stat's fill when open) and the quest as its own soft card (the main quest on its stat's tint). |
| `QuestRow` | A quest inside a card: stat glyph on its pastel disc, title, target line, badges, round check. Swipe right completes (SELF only). |
| `StatChip`, `DifficultyChip`, `VerifiedBadge`, `Pill`, `ChoiceChip` | Soft filled pills; choice chips are 48dp pills with an accent wash, edge and check when selected. |
| `PrimaryButton`, `SecondaryButton`, `QuietButton` | §2.3. |
| `XpBar`, `RankCrest` | Slim gradient pill; rank letter on a soft disc of its colour (System mode keeps the hex). |
| `RewardSheet` | Check on a soft disc, the XP as a hero number counting up, gains as pastel pills, the formula in a quiet well, Done. |
| `LevelUpCeremony` | §2.5. |
| `SystemToast`, `SystemWarning`, `ChapterReveal`, `EmptyState`, `GateDial`, `PathLadder`, `PathQuestBadge`, `FeedbackChips`, `OracleStatus`, `BodyMap`, `ShareCard` | Restyled through the tokens; their per-screen redesign lands with each screen (UI_OVERHAUL_NOTES). |
| ChartKit (`core/designsystem/charts`) | §8.5: `TrendLine` (smooth line, soft gradient, dotted grid, marked latest point), `Sparkline`, `Comparison`, `Ring`, `Progress`, `Segmented`, `Columns`, `StackedColumns`, `CalendarGrid`, `Radar`, `Heatmap` (rounded cells). |
| Bottom bar | Four items (Today · Gym · Diet · Progress), icon + label, card-white bar with a top hairline; the selected item gets an accent icon on a soft accent pill. |

## 4. Screens & flows

Navigation: bottom bar (§3) with 4 destinations — **Today**, **Gym**, **Diet**, **Progress** (the Status screen; "Status" in System mode). **Gates** and **Vault** open from round icon buttons in Today's greeting header; **Paths** opens from the Progress header; Settings from Progress top-right. Screens below are redesigned one by one (UI_OVERHAUL_NOTES §4); until then they wear the new tokens through the shared components.

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
┌──────────────────────────────────┐   soft lavender → peach wash behind the hero
│ Good afternoon,           (◎)(▣) │  GreetingHeader + Gates / Vault actions
│ Assis                            │
│ Tuesday, October 27, 2026        │
│            ╭─────────╮           │
│           │  Today   │           │  HeroRing: quests done today
│           │    2     │           │
│           │ of 6 done│           │
│            ╰─────────╯           │
│ ┌ (C) Level 24 ──────┐ ┌ 🔥 12 ─┐ │  LevelModule + StreakModule
│ │ ▬▬▬▬▬▬▬▬▬▬▬░░░     │ │ Best 30│ │
│ └────────────────────┘ └────────┘ │
│ ┌ ✦ Daily focus ─────────────────┐│  InsightCard (the daily line until insights, F29)
│ └────────────────────────────────┘│
│ Today's plan            Day plan │
│ ☀ Morning          1 of 1 done › │  past block: collapsed
│ ☼ Day  [Now]       0 of 1 done ⌄ │  current block
│ ●─┌ Call your family       ◯ ┐  │  TimelineQuestRow
│ ☾ Evening          0 of 1 done ⌄ │
│ ◷ Anytime          1 of 3 done ⌄ │
│ Habits             Manage habits │
└──────────────────────────────────┘
```
- The plan is every directive item and habit, grouped by time-of-day block (GD §14.5) in the order Morning → Day → Evening → Anytime; empty blocks are hidden. The current block is marked "Now"; earlier blocks start collapsed (tap to expand; open quests stay completable). The current block is recomputed whenever Today re-derives (opening the app, completing a quest, rollover, changing the boundaries), not on a timer.
- A penalty quest sits in a card under the insight and the screen carries a faint ember tint; the Rank Trial card follows.
- Day cleared → the hero's kicker reads "Plan complete" (System: "DIRECTIVE CLEARED").
- Campaign and Screen Discipline cards close the screen.

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
Bottom tab. No session: [Start workout], THIS WEEK (workouts · sets · volume in `numeric`, then the **body map**: front and back figures of angular plates, each muscle lit in `system` at the heatmap alpha steps for its weekly band, caption "Brighter = more work"), ROUTINES ([Start], remove), PERSONAL RECORDS (`crit` accent; "80 kg × 5 · e1RM 93.3 kg"), HISTORY. Running session: a window with the name and an "elapsed" timer, one window per exercise with columns Set · Previous · kg · Reps · ✓ (compact `NumberField`s; ✓ disabled at 0 reps), [Add set] / [Remove last set], then [Add exercise], [Finish], [Save as routine], [Discard] behind a confirm. Exercise library sheet: search, equipment chips, the body map as a tap-to-filter control plus the same choice as chips (the map is one TalkBack node), result rows with [Add to workout]; detail sheet with primary (full) and secondary (40 %) muscles lit, pills for equipment/level/mechanic and numbered how-to steps. A quiet caption says the tracker grants no XP yet.

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
