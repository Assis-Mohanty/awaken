# AWAKEN — Design System & UX Spec (v1.0)

## 1. Direction in one paragraph

The app is **the System** from a hunter's world: you don't open a to-do list, you open a window that the System projects in front of you. Dark, cold, precise, a little ominous — and then it *rewards* you with light. Spend the boldness in one place: the **System Window**, a chamfered translucent slab with a lit edge whose colour is your rank. Everything around it (lists, settings, nav) stays quiet and disciplined so the windows and the level-up moment hit hard.

What we are explicitly not: Habitica's pixel-art toybox, a generic Material "productivity" app with a purple FAB, or an anime fan-page with screenshots/characters from Solo Leveling (legal risk, and it looks cheap).

## 2. Tokens

### 2.1 Colour (dark-only in v1)

| Token | Hex | Use |
|---|---|---|
| `void` | `#05080F` | App background |
| `depth` | `#0A1220` | Background radial-gradient centre (the "gate glow" sits on top) |
| `panel` | `#0F1A2B` @ 86% alpha | System Window fill |
| `panelRaised` | `#15233A` | Sheets, pressed states |
| `edge` | `#27405F` | Hairline borders on quiet surfaces |
| `bone` | `#E7EEF6` | Primary text |
| `mist` | `#93A4BA` | Secondary text |
| `fog` | `#5B6B80` | Disabled / tertiary |
| `system` | `#5CC2FF` | Primary accent: System edges, primary buttons, XP bar fill |
| `aether` | `#B9E4FF` | XP bar leading glow, highlights |
| `ember` | `#FF5468` | Penalty Zone, destructive, breach |
| `crit` | `#FFC94D` | Crits, gold currency, S-rank |
| `verified` | `#5BE3B0` | Verified badge (Health/Timer) |

Rank edge colours (the window edge + gate glow tint follow the hunter's *current* rank):

| E | D | C | B | A | S |
|---|---|---|---|---|---|
| `#8A96A8` steel | `#5BD08A` jade | `#4FD6E8` cyan | `#5C8DFF` cobalt | `#A97BFF` violet | `#FFC94D` gold |

Stat colours (radar, chips, quest stat marks): STR `#FF7A59`, INT `#5CC2FF`, WIL `#A97BFF`, VIT `#5BE3B0`, CHA `#FFB3D1`.

Contrast: `bone` on `panel` ≥ 12:1; `mist` on `panel` ≥ 5:1; never put body text in `system`. Colour is never the only signal (stat chips carry the 3-letter code; verified has an icon + word).

### 2.2 Typography — two families, clearly distinct (both SIL OFL, bundle in `res/font`)

- **Saira Condensed** (SemiBold 600, Bold 700) — the System's voice: window titles, level/XP numerals, rank letters, big counters. Tall condensed numerals make "LV 24" and "+144 XP" feel like a HUD.
- **IBM Plex Sans** (Regular 400, Medium 500, SemiBold 600) — everything humans read: quest descriptions, settings, body.

Scale (sp; line-height in parentheses):

| Style | Family | Size | Use |
|---|---|---|---|
| `displayXL` | Saira Cond 700 | 64 (64) | Level-up number |
| `display` | Saira Cond 700 | 40 (44) | Home level, rank letter |
| `windowTitle` | Saira Cond 600, letterSpacing 0.06em, UPPERCASE | 18 (22) | System Window titles only ("QUEST CLEARED", "DAILY DIRECTIVE") |
| `numeric` | Saira Cond 600, tabular | 22 (26) | XP, gold, timers |
| `title` | Plex 600 | 18 (24) | Quest titles, section headers (sentence case) |
| `body` | Plex 400 | 15 (22) | Descriptions |
| `label` | Plex 500 | 13 (18) | Chips, metadata (sentence case) |
| `caption` | Plex 400 | 12 (16) | Hints |

Rules: UPPERCASE is reserved for `windowTitle` — that's the System speaking. Everything else is sentence case. No monospace anywhere. Respect font scaling up to 200% (layouts must reflow, numbers may wrap to a second line).

### 2.3 Shape, space, elevation

- **System Window**: `CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)` (two opposite chamfers — asymmetry is the signature). 1dp edge in rank colour @ 70% + 8dp outer glow (rank colour @ 18%, drawn with `drawBehind` blur or layered strokes — no heavy `Modifier.blur` on API < 31).
- **Quest rows**: no card per row. A single window contains the list; rows are separated by 1dp `edge` hairlines with 16dp vertical padding. (Avoid the "identical rounded cards" look.)
- **Chips/badges**: fully rounded pills (stat, difficulty, verified).
- **Buttons**: primary = filled `system`, chamfered 8dp one corner; secondary = outlined `edge`; text buttons for low-emphasis.
- Spacing scale: 4, 8, 12, 16, 24, 32, 48. Screen gutters 20dp. Content max width 560dp (tablets centre it).
- No drop shadows. Depth = glow + alpha, consistent with a projected interface.

### 2.4 Iconography
Material Symbols *Sharp* (outlined, weight 300) — matches the chamfered geometry. Custom vector glyphs (draw in Compose `ImageVector`) for: 5 stat sigils, rank crest (hex frame with letter), gate portal, shield, loot fragment.

### 2.5 Motion
| Token | Value | Use |
|---|---|---|
| `quick` | 120 ms, `FastOutSlowIn` | Press, toggles |
| `standard` | 240 ms, emphasized decelerate | Sheets, list changes |
| `reveal` | 420 ms | Window open (scale 0.96→1 + edge "draws" around the perimeter) |
| `ceremony` | ~2.2 s choreographed | Level-up / rank-up only |

- **One orchestrated moment per event.** Completing a quest: row check → the XP number counts up inside the reward sheet → bar fills with an `aether` leading glow. That's it — no confetti on every tap.
- **Level-up ceremony** (the memorable thing): background dims to 90% `void` → a ring (gate) expands from the XP bar → `displayXL` level number rolls from old to new → windowTitle "LEVEL UP" types on (30 ms/char) → stat increases list in → heavy haptic on the number landing. Tap anywhere to skip after 600 ms.
- **System text type-on**: only for windowTitles of important events (level-up, penalty, trial, chapter). Never for body text.
- `reduceMotion` (system setting `ANIMATOR_DURATION_SCALE == 0` OR in-app toggle) → crossfades only, no type-on, no scaling, ceremony becomes a static window.

### 2.6 Haptics
`Haptics` wrapper (respects in-app toggle):
- `tick` — checkbox, chip select (`CLOCK_TICK`/`SEGMENT_TICK`).
- `confirm` — quest completed (`CONFIRM`).
- `crit` — two quick clicks (composition `PRIMITIVE_CLICK ×2`, fallback `EFFECT_DOUBLE_CLICK`).
- `levelUp` — `PRIMITIVE_THUD` (fallback `EFFECT_HEAVY_CLICK`) when number lands.
- `reject` — gate collapse / penalty (`REJECT`).

### 2.7 Sound
Off by default in v1 (toggle in settings ready, assets in v1.1). Never ship a sound you didn't license.

## 3. Components (build these in `core/designsystem/components`)

| Component | Spec |
|---|---|
| `SystemWindow(title, rankColor, content)` | The signature slab. Optional `headerTrailing` slot. Opening uses `reveal`. |
| `HunterHeader` | Rank crest (hex + letter), hunter name, equipped title, level `display`, XP bar, streak flame + count, gold. |
| `XpBar` | 10dp tall, chamfered ends, track `edge`, fill `system`, leading 24dp gradient to `aether`. Animates delta. Shows "1,240 / 1,434 XP" in `numeric` small. |
| `QuestRow` | Leading stat sigil (stat colour), title, target line ("40 reps · Normal"), trailing verified badge if applicable + check control. Swipe-right = complete (with haptic tick at threshold), long-press = details. Expired/missed rows dim to `fog` with strike. |
| `StatChip`, `DifficultyChip`, `VerifiedBadge` | Pills. Verified = `verified` icon + "Verified" / "Timed". |
| `RewardSheet` | Bottom sheet: windowTitle "QUEST CLEARED" (or "CRITICAL CLEAR" in `crit`), big `+XP` numeric, stat split, gold, the **formula breakdown** row (`60 × 1.5 Normal × 1.20 streak × 1.30 verified`), Done button. |
| `RadarChart` | Canvas pentagon, 5 axes with stat sigils; filled polygon `system` @ 25% with 1.5dp edge; previous-week polygon as dashed `mist` for comparison. |
| `Heatmap` | 18 weeks × 7 days, cleared intensity 0–4 using `system` alpha steps; rest days outlined; tap shows day summary. |
| `GateDial` | Circular countdown for Focus Gate: thick ring in gate rank colour, remaining time `displayXL`, breach budget as a thin inner ring turning `ember`. |
| `SystemToast` | Top-anchored mini window for achievements ("Title unlocked: Unbroken"). |
| `EmptyState` | Short System line + one action. E.g., "No habits yet. Add one the System should track." [Add habit] |
| `ShareCard` | 1080×1350 bitmap: hunter name, rank crest, level, radar, week stats, app name. Generated with `GraphicsLayer.toImageBitmap()` and shared via `ACTION_SEND`. |

## 4. Screens & flows

Navigation: bottom bar with 4 destinations — **Today**, **Gates**, **Status**, **Vault**. Settings from Status top-right.

### 4.1 Awakening (onboarding) — target < 60 seconds, no account

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
[6] Daily directive time        time picker (default 07:00)
[7] "AWAKENING COMPLETE" window → first directive slides in on Today.
```
First quest is guaranteed Easy and doable now (e.g., "Drink a glass of water" VIT / "10 push-ups" STR by focus) → first level-up within 2 minutes of install.

### 4.2 Today
```
┌──────────────────────────────────┐
│ [crest C]  Assis        🔥 12    │  HunterHeader
│ "Unbroken"  LV 24   ◈ 1,920      │
│ [████████████░░░░]  1,240/1,434  │
├──────────────────────────────────┤
│ ◤ DAILY DIRECTIVE        3 / 5 ◢│  SystemWindow (rank edge)
│  STR  Iron Arms   80 reps · Hard │  ← main quest, larger row, "Main" chip
│  INT  Deep Dive   25 min · Timed │  ✓
│  VIT  Ground Walk 7,000 steps ✔V │  auto-completes from Health Connect
│  WIL  Still Mind  10 min         │
│  ─ habits ─                      │
│  CHA  Call home                  │
├──────────────────────────────────┤
│ System: "INT is falling behind." │  one daily System line (mist)
└──────────────────────────────────┘
```
- Penalty quest pinned at top inside an `ember`-edged window when active.
- Trial banner when a Rank Trial is available/active.
- Day cleared → window title becomes "DIRECTIVE CLEARED", edge pulses once.

### 4.3 Quest detail (long-press / tap title)
Description, target, stat split, verification method with explanation, [Begin] (starts min-duration timer) / [Complete], reroll (side quests), "Why this quest?" line ("INT neglected for 3 days").

### 4.4 Gates
Grid of 4 gate ranks (E/D/C/B) as portal tiles with duration; pick stat + optional label → `GateDial` full screen. Leaving the app shows the breach budget in the ongoing notification. Clear → RewardSheet. Collapse → `ember` window "GATE COLLAPSED" + "Background time 1:42 exceeded the 1:30 limit."

### 4.5 Status
HunterHeader (large) → RadarChart (this week vs last) → stat list (level + XP to next) → Heatmap → titles & achievements grid (locked ones silhouetted with condition text) → chapters list → Weekly Report card with [Share].

### 4.6 Vault
Gold balance, Streak Shields (owned/2), themes, frames, sigils; Relic Cache (fragments 7/10). Purchase = confirm sheet showing price and resulting balance. Owned items show "Equipped"/"Equip".

### 4.7 Settings
Profile (name, focus stats), Day starts at, Reminder times, Rest day (pick next), Recovery mode, Health Connect, Equipment I own (jump rope, dumbbells, pull-up bar, gym access), Injury mode (excludes impact/legs/upper tags), Haptics, Reduce motion, Export/Import data, Privacy policy, About, (debug) Debug screen.

## 5. Copy voice
- System lines: terse, second person, present tense. "Quest cleared." "The System has recalibrated your STR trials." "You missed yesterday. One quest stands between you and redemption."
- Human UI text: plain verbs, sentence case. Button labels name the action: "Complete quest", "Start gate", "Buy shield". A button that says "Start gate" leads to a notification "Gate in progress".
- Errors say what happened + what to do: "Health Connect isn't installed. Install it from Play Store to verify steps automatically." No apologies, no jokes.
- Never shame. Penalty copy is stern, never insulting.

## 6. Accessibility floor (release blockers)
TalkBack labels on every interactive element and chart (radar exposes "Strength level 12, Intelligence level 9…"); 48dp targets; font scale 200% doesn't clip; reduce-motion honoured; contrast table above respected; no information conveyed by colour alone.

## 7. Design prompt to give Claude Code (paste with Phase 2)

> Implement the AWAKEN design system from `docs/DESIGN_SYSTEM.md` in `core/designsystem`. The look is a projected "System" interface: deep `void` background with a soft radial gate-glow tinted by the hunter's rank, translucent chamfered `SystemWindow` slabs with a lit edge in the rank colour, Saira Condensed for the System's voice and numerals, IBM Plex Sans for everything people read. Quiet everywhere except windows and the level-up ceremony. No cards-per-row, no drop shadows, no Material default purple, no monospace, uppercase only for window titles. Build the components in §3 with `@Preview`s for each state (default, pressed, disabled, crit, penalty, reduce-motion). Implement `AwakenTheme` exposing `AwakenColors`, `AwakenType`, `AwakenMotion`, `Haptics` via CompositionLocals; screens must never reference raw hex/dp values. Then build a `DesignSystemCatalog` debug screen that shows every component so I can review them on device.
