# AWAKEN — Game Design Spec (v1.0 MVP)

> Source of truth for every number in the game. If code disagrees with this file, the code is wrong.
> All formulas live in the pure-Kotlin `:engine` module and are covered by the test vectors in §12.
> AI never decides a number in this file. Ever. AI Paths (§6.6) may propose *what* to do (task wording, a target like "3 problems"), always clamped by the engine; every reward-relevant number — difficulty, verification, XP, gold, caps — is computed here.

---

## 1. Design pillars (in priority order)

1. **Effort is the only currency.** XP comes from real actions, never from taps, logins or purchases.
2. **Verified beats claimed.** Actions the phone can observe (Health Connect, in-app focus timer) earn more than self-reported ones. This is our moat — every competitor is honor-system only.
3. **Hard, never cruel.** Failure creates a *comeback quest*, not a wipe. No XP loss, no level loss, no public shaming. (Habitica's biggest complaint is its punishment death-spiral.)
4. **Every number is explainable.** The completion sheet shows the formula breakdown: `40 base × 1.5 Normal × 1.20 streak × 1.30 verified = 94 XP` (BASE is 40, §4.1).
5. **The phone should feel like a System window**, not a to-do list with a skin.

---

## 2. The Hunter

| Field | Notes |
|---|---|
| `hunterName` | 3–20 chars, set at Awakening (onboarding) |
| `level` | Derived from total XP (§4). Never stored as source of truth. |
| `rank` | E/D/C/B/A/S. Derived from level **and** passed Rank Trials (§5). |
| `title` | One equipped title (§10). |
| `stats` | Five stats, each with its own XP pool (§3). |
| `gold` | Earned currency. Derived from gold ledger. |
| `streak` | Consecutive "cleared days" (§7). |
| `focusStats` | 2 stats picked at Awakening; directive generation weights them 2×. |
| `baselineTier` | Starting difficulty per stat from the Awakening questionnaire. |
| `dayStartHour` | Default 4 (a "day" runs 04:00–03:59 local). Users who study at 1 a.m. are not penalised. |

---

## 3. Stats

Five stats → renders as a pentagon radar.

| Stat | Code | Real-world domain | Example quests |
|---|---|---|---|
| Strength | `STR` | Training, sport, movement | Push-ups, run, gym session, steps |
| Intelligence | `INT` | Study, reading, coding, skill practice | Read 20 pages, 2 DSA problems, 50-min focus |
| Willpower | `WIL` | Discipline, focus, mindfulness, avoiding vices | Meditate 10 min, no-phone hour, cold shower |
| Vitality | `VIT` | Sleep, nutrition, hydration, recovery | 7h sleep, 2L water, cook a meal, stretch |
| Charisma | `CHA` | Social, communication, relationships | Call family, compliment someone, post your work |

**Stat level** (shown as the stat's value on the radar):

```
statLevel(statXp) = floor( sqrt(statXp / 40) ) + 1
```

Stat level 10 ≈ 3,240 stat XP; 25 ≈ 23,040. Radar axis max = max(20, highestStatLevel + 5) so the shape stays readable.

A quest awards XP to one **primary** stat (100%) and optionally one **secondary** stat (30%). Total XP (for level) = the full quest XP once — the secondary split is a stat-only bonus and does **not** count toward total XP twice.

---

## 4. XP formula & level curve

### 4.1 Quest XP

```
questXp = round( BASE × D × S × V × C )

BASE = 40

D (difficulty)        Easy 1.0 | Normal 1.5 | Hard 2.25 | Extreme 3.0
S (streak)            1 + min(streakDays, 30) / 60          → 1.00 … 1.50
V (verification)      SELF 1.00 | TIMER 1.15 | HEALTH 1.30
C (critical)          2.0 if crit else 1.0  (§4.2)
```

Rounding: `kotlin.math.round` on the final product only (half-even is NOT used; use `Math.round` semantics = half-up). Intermediate values are `Double`.

**Why no level-scaling factor?** The old PRD applied `1/(1+0.05·level)` *and* an exponential level curve. That double-penalises long-term users — a level-60 hunter would earn a quarter of the XP for the same push-ups while needing 10× more per level. Effort should be worth the same at every level; the curve alone handles pacing.

### 4.2 Critical hits (variable reward, deterministic)

Crits are variable for the user but **reproducible** for the system:

```
seed = fnv1a64( questInstanceId )         // deterministic UUID derived from (hunterId, gameDate, slot) — §6.3
crit = (seed mod 10_000) < 800            // 8.00 %
```

- Because the seed is the instance id (fixed at generation, never the per-attempt completionId), un-completing and re-completing cannot re-roll a crit.
- Crit also grants +1 Loot Fragment (cosmetic progression, §9).
- Penalty quests and Rank Trial stages cannot crit.

### 4.3 Daily anti-farm caps (self-reported only)

| Source | Daily XP cap | Beyond cap |
|---|---|---|
| `SELF` (honor) | 600 XP / day | quest still completes, awards 25 % XP, 0 gold |
| `TIMER` | 900 XP / day | same rule |
| `HEALTH` | uncapped | — |

Caps are evaluated per game-day (§2 `dayStartHour`). The UI shows "Self-report limit reached — verified quests still count fully."

Custom (user-created) quests: max difficulty **Normal** unless verification is TIMER or HEALTH. Max 8 active custom quests.

### 4.4 Level curve

```
xpToNext(L) = round( 100 + 12 × L^1.6 )        // XP to go from level L to L+1
level(totalXp) = largest L such that cumulative(L) ≤ totalXp, max 100
```

Calibrated for ~350 XP/day (a normal day: 1 main + 3 side + 3 habits):

| Level | Step XP | Cumulative | Days at 350/day |
|---|---|---|---|
| 2 | 112 | 112 | <1 |
| 5 | 210 | 628 | ~2 |
| 10 | 504 | 2,505 | ~7 |
| 20 | 1,434 | 12,326 | ~35 |
| 35 | 3,485 | 49,372 | ~140 |
| 50 | 6,174 | 122,432 | ~350 |
| 70 | 10,604 | 290,924 | ~830 |
| 100 | — | 731,908 | ~2,100 |

Day 1 must produce ≥1 level-up (it does: first 2–3 quests). Week 1 reaches ~L10 and therefore the first Rank Trial — that's the D7 retention hook.

---

## 5. Ranks & Rank Trials

| Rank | Level band | Colour token |
|---|---|---|
| E | 1–9 | `rankE` grey |
| D | 10–19 | `rankD` green |
| C | 20–34 | `rankC` cyan |
| B | 35–49 | `rankB` blue |
| A | 50–69 | `rankA` violet |
| S | 70–100 | `rankS` gold |

Reaching the first level of a new band does **not** promote you. It unlocks the **Rank Trial**: a 3-day gate with one Hard quest per day in the hunter's two weakest stats + one focus stat. Clear all 3 stages (any order, within 5 days) → promotion cinematic + rank title + 500 gold.

- Fail (window expires) → trial can be retried after 48 h. Levels keep accruing; only the rank badge waits.
- Rank never decays in v1 (no leaderboard yet = decay has no purpose and is pure anxiety).

---

## 6. Quests

### 6.1 Types (v1)

| Type | Count / day | Source | Notes |
|---|---|---|---|
| **Main** | 1 | Generated | Hard, focus stat, rotates through focus stats |
| **Side** | 1–3 | Generated | Normal, weighted by focus (2×) and neglect (stat untouched for 3+ days gets 1.5×). Count = `max(1, 3 − pathQuestsToday)` (§6.6.6) |
| **Path** | 0–3 | AI Path (§6.6) | Personal quests from the hunter's goals. Validated and priced by the engine |
| **Habit** | user-defined, max 8 | User | Custom recurring quests (days of week) |
| **Penalty** | 0–1 | System | Appears after a failed day (§7.3) |
| **Trial** | 1 while active | System | Rank Trial stage (§5) |
| **Focus Gate** | unlimited | User-started | Timer dungeon (§8). Counts as a quest completion. |
| **Discipline** | 0–1 | System | Screen Discipline (§6.7): paid at rollover if every app limit held |

**Daily Directive** = Main + Path quests scheduled today + Side + Habits scheduled today. Generated at day start (or on first app open of the day — whichever comes first; idempotent per `(hunterId, gameDate)`).

### 6.2 Template library (deterministic generation)

Quests are generated from a **bundled JSON template library** (`assets/quests/templates.json`), not from an LLM. Target ≥ 150 templates at launch, ≥ 25 per stat, across 4 difficulties.

```json
{
  "id": "str_pushups",
  "stat": "STR",
  "secondaryStat": "WIL",
  "titles": ["Do push-ups", "Practise push-ups"],
  "description": "Complete {target} push-ups. Sets of any size.",
  "unit": "reps",
  "targets": { "EASY": 20, "NORMAL": 40, "HARD": 80, "EXTREME": 120 },
  "verification": "SELF",
  "equipment": [],
  "tags": ["bodyweight", "indoor", "no_impact_false"],
  "minDurationSec": 120
}
```

**Titles** say what to do, in plain words: action first, **2–5 words**, sentence case, **≤ 40 chars**, 1–3 variants per template. No numbers (the target line under the title shows them, and campaign habits keep their title while the target grows), no metaphors or fantasy words (forge, power, gate, realm, …: the full list is `TemplateLibrary.BANNED_TITLE_WORDS`). Good: "Do push-ups", "Walk your steps", "Eat protein-rich food". Bad: "Iron Arms", "Fuel the Forge". `TemplateLibrary.validate()` enforces this, so `ContentAssetsTest` fails on a bad title.

Verification options in templates: `SELF`, `TIMER` (completed by the Focus Gate engine), `HEALTH_STEPS`, `HEALTH_EXERCISE` (any exercise session ≥ target minutes), `HEALTH_SLEEP` (sleep session ≥ target hours), `HEALTH_DISTANCE` (walking + running distance for the game-day ≥ target km), `HEALTH_CALORIES` (active calories for the game-day ≥ target kcal).

Target ladders (Easy / Normal / Hard / Extreme): `HEALTH_DISTANCE` **2 / 4 / 7 / 12 km**; `HEALTH_CALORIES` **150 / 300 / 500 / 800 kcal** (active only, never resting/total). Calories are never an AI Path unit and the model cannot choose `HEALTH_*` (§6.6).

#### Health data trust (applies to every `HEALTH_*` quest)
- **Manual entries never count.** Records whose recording method is manual entry are dropped before any rule runs. Records with an unknown recording method are kept (older sources do not label themselves).
- **Sanity limits.** A day with more than **60,000 steps** counts as 0 steps. An exercise session whose average speed exceeds **25 km/h** is dropped. An exercise session with an average heart rate **below 60 bpm** is dropped; a session with no heart-rate data is never penalised.
- **No double counting.** Overlapping exercise sessions, and overlapping sleep sessions, from different apps are merged into one before matching. Steps, distance and calories use Health Connect's own cross-source aggregate.
- **Heart rate is display-only.** It is shown on workout summaries and used by the sanity limit above; it never changes XP, gold, crits or caps.
- Permissions: Steps, Exercise and Sleep are the core set; Distance, Active calories and Heart rate are optional and each enables only its own feature.

- **Phone step counter (approved 2026-11-01):** when no app writes steps to Health Connect for the day, steps counted by the phone itself (Recording API, opt-in) verify HEALTH_STEPS quests as HEALTH, with the same multiplier and step cap. No new number (ENGINE_DECISIONS #110).

### 6.3 Generation algorithm

```
generateDirective(hunter, gameDate):
  rng = SplittableRandom( hash(hunterId, gameDate) )        // deterministic per day
  pool = templates
         − equipment the hunter doesn't own (settings)
         − tags excluded by Injury Mode (e.g. "impact", "legs")
         − templates used in the last 7 days (novelty window)
  paths = PathScheduler.questsFor(gameDate)                  // §6.6.6, 0–3 items, already validated
  main  = promotedPathQuest(paths, focusStats[gameDay % 2])  // §6.6.6, may be null
          ?: weightedPick(pool where stat == focusStats[gameDay % 2], difficulty = HARD adjusted by tier)
  sides = max(1, 3 − paths.size) × weightedPick(pool − main.stat-duplicates-where-possible,
                                                weights = focus 2.0, neglected 1.5, else 1.0)
  title = template.titles[rng]   ;   target = template.targets[difficulty]
```

Each instance id is a deterministic name-based UUID: `UUID.nameUUIDFromBytes("hunterId|gameDate|slot")` with slot `main`, `side0..2`, `rerollN` or `penalty`. Regenerating the same day yields the same ids, so persistence is idempotent and crits (§4.2) can never be re-rolled. Ledger rows use a per-attempt `completionId` (§9.3).

**First directive** (the onboarding day only): one side slot becomes a starter quest — Easy, in a focus stat, SELF-verified, and not tagged `outdoor` — so the first clear is doable right after onboarding.

**Reroll**: 1 free reroll per day for one side or Path quest; further rerolls cost 40 gold (max 3 paid/day, so 4 rerolls total). Rerolling cannot target the main quest (including a promoted Path quest). Path quest rerolls follow §6.6.6.

### 6.4 Adaptive difficulty (per stat)

Every Monday (or first open after), per stat over the previous 7 days of *template-generated* quests in that stat (Path quests are excluded; they have their own signal, §6.6.5):

```
completionRate ≥ 0.85 and ≥ 4 quests   → tier +1   (max EXTREME)
completionRate ≤ 0.40 and ≥ 4 quests   → tier −1   (min EASY)
else                                   → unchanged
```

Side quests use the stat's tier; the main quest uses `tier + 1` (capped). Shown to the user as a System message: *"The System has recalibrated. STR trials will be harder."*

### 6.5 Completion rules

- `minDurationSec`: a SELF quest cannot be completed sooner than N seconds after it was *started* (tap "Begin"). If the user never tapped Begin, they can still complete, but `V` stays 1.0 and it counts toward the self cap. Keep it low-friction; this is a soft speed bump, not a jail.
- Completions are **undoable for 10 minutes** (writes a reversing ledger entry with the same idempotency group; never deletes). Undo never revokes achievements: unlocks and their gold are permanent. Undo is also refused once the game-day has ended, even within the 10 minutes.
- HEALTH and TIMER quests cannot be completed by hand. HEALTH quests complete only from Health Connect data, TIMER quests only by clearing a Focus Gate. Only SELF quests have a manual Complete button.
- **Set counter.** SELF quests with unit `reps` show a set counter (tap per set, reps per set, rest timer). It only records progress toward the target; it never completes the quest and never mints anything. The hunter taps Complete themselves, so the quest stays SELF (V 1.00, 600 XP cap).
- After the day ends, unfinished generated quests become `EXPIRED`. Habits become `MISSED`. Path quests follow the carry-over rule in §6.6.6.

### 6.6 AI Paths (personal quest ladders)

The template library handles universal habits (steps, sleep, push-ups, reading time). It cannot know that one hunter is chasing Codeforces 1900 and another is learning Japanese. **Paths** close that gap. The split of responsibility is the whole design:

| Decides | Who | Why |
|---|---|---|
| *What* to do: decompose a goal into milestones and concrete tasks | **LLM** (via `/proxy`) | Needs world knowledge; templates can't |
| *How much harder* next week: the progression signal | **Engine** (§6.6.5) | A formula over completion data, same thresholds as §6.4 |
| Difficulty, verification, XP, gold, caps | **Engine** (§6.6.4, §4) | Rewards must be deterministic and abuse-resistant |

AI Paths are **opt-in** (consent screen, ARCHITECTURE §3.7). With AI off, offline, or the proxy failing, the Directive is generated exactly as in §6.3 with `paths = []`.

#### 6.6.1 The Path

| Field | Rule |
|---|---|
| `goal` | User text, 3–120 chars. e.g. "Reach 1900 on Codeforces" |
| `currentLevel` | User text, 0–120 chars. e.g. "Rated 1400, weak at graphs and DP" |
| `notes` | Optional user text, 0–280 chars. Constraints: "no gym", "only evenings" |
| `primaryStat` | One of the 5 stats, chosen by the user (suggested from the goal by a keyword table in `:engine`, not by AI) |
| `secondaryStat` | Optional, ≠ primary |
| `activeDays` | Days-of-week mask, 1–7 days |
| `questsPerDay` | 1 or 2 |
| `weeklyMinutes` | 30–900, user-set time budget |
| `status` | `PLANNING` → `ACTIVE` ⇄ `PAUSED` → `ARCHIVED` |

Limits: **max 3 active Paths**; max 1 Path created per game-day (stops create/delete churn against the proxy).

#### 6.6.2 Two AI calls, both bounded

1. **`PLAN_PATH`** (on creation; user "Re-plan" allowed max 1× per ISO week per Path). Returns a **ladder** of 4–10 milestones, each `{title ≤ 48, summary ≤ 200, exitCriterion ≤ 140}`. Milestones are narrative structure only; they carry no rewards. Plan validation: V2 string cleaning on every field; drop milestones with an empty title; keep the first 10; fewer than 4 left → the plan request counts as failed (Path stays `PLANNING`, [Retry] shown).
2. **`WEEK_QUESTS`** (once per Path per ISO week, at the first rollover of the week, or right after `PLAN_PATH`). Input: the Path, the current milestone, the engine's `progressionSignal` (§6.6.5), and last week's per-quest outcomes. Output: up to `n = activeDays × questsPerDay` **blueprints** plus an optional `suggestAdvance: Boolean`.

The model never sees the hunter's XP, level, gold or streak; it doesn't need them and they would invite it to "balance" rewards.

#### 6.6.3 Blueprint schema (wire contract: `/contracts/path_week.response.schema.json`)

```json
{
  "title": "Graph Hunt",
  "description": "Solve 2 problems tagged 'graphs' rated 1500-1600 on Codeforces. Write one line on the key idea of each.",
  "stat": "INT",
  "unit": "PROBLEMS",
  "target": 2,
  "effort": "HARD",
  "estMinutes": 60,
  "mode": "GATE",
  "tags": ["quiet", "screen"],
  "rationale": "Your last two contests lost points on graph problems."
}
```

Enums: `unit ∈ {MINUTES, REPS, PAGES, PROBLEMS, ITEMS, SESSIONS, KM}` · `effort ∈ {EASY, NORMAL, HARD, EXTREME}` · `mode ∈ {SELF, GATE}` · `tags ⊆ {impact, legs, upper, outdoor, indoor, social, quiet, screen}`. There is deliberately no unit for calories, body weight, food or money, and the model cannot choose `HEALTH_*` verification.

#### 6.6.4 PathValidator (pure function in `:engine`, runs on every response)

Applied in this order; a blueprint that fails a **drop** rule is discarded, a **clamp** rule repairs it.

| # | Rule | Kind |
|---|---|---|
| V1 | JSON doesn't parse, or an enum value is unknown | drop |
| V2 | Strings: strip control chars and URLs (`https?://\S+`), collapse runs of whitespace to one space, trim, truncate to the limits above (title 48, description 280, rationale 140); title empty after cleaning | drop if empty, else clamp |
| V3 | `stat ∉ {primaryStat, secondaryStat}` → set to `primaryStat` | clamp |
| V4 | `target` clamped per unit: MINUTES 5–120 · REPS 5–300 · PAGES 2–100 · PROBLEMS 1–10 · ITEMS 1–20 · SESSIONS 1–3 · KM 1–21 | clamp |
| V5 | `estMinutes` clamped to 5–120 | clamp |
| V6 | Any tag excluded by Injury Mode, or title/description mentions equipment the hunter doesn't own (`EquipmentKeywords` table in `:engine`, e.g. "dumbbell", "pull-up bar", "gym", "jump rope") | drop |
| V7 | Normalised title (lowercase, alphanumerics only) duplicates another blueprint in the batch or any Path quest of this Path in the last 14 days | drop |
| V8 | Keep blueprints in order while cumulative `estMinutes ≤ round(weeklyMinutes × 1.2)`; drop the rest | drop |
| V9 | Keep at most `n` (§6.6.2) | drop |

**Reward pricing (the anti-abuse core):**

```
if mode == GATE:
    gate        = smallest of E(15) D(25) C(50) B(90) with duration ≥ estMinutes   (estMinutes > 90 → B)
    difficulty  = gate's base difficulty (§8)
    verification= TIMER          // completed only by clearing that gate, linked to this quest
else:  // SELF
    difficulty  = min(effort, NORMAL)     // same rule as custom quests, §4.3
    verification= SELF
    minDurationSec = clamp(estMinutes × 15, 60, 900)   // a quarter of the estimate; soft speed bump per §6.5
xp, gold = §4.1 / §9.1 unchanged; SELF/TIMER daily caps (§4.3) apply unchanged
```

The model's `effort` can only *lower* a SELF quest's difficulty, never raise it above Normal. Hard and Extreme Path quests exist only as Focus Gates, where the time is measured. A fake goal ("Goal: blink") produces SELF quests capped at Normal and the 600 XP/day self cap. Gaming the AI earns no more than honest self-report.

If **zero** blueprints survive, the week has no Path quests, the Path shows "The System could not chart this week" with a [Retry] action, and the Directive falls back to §6.3 with `paths = []`.

#### 6.6.5 Progression signal (engine → prompt)

Computed per Path over the previous ISO week's Path quests:

```
completionRate = completed / (completed + expired + skipped)   // carried-over quests count once, at their final outcome
feedbackBias   = (#"too easy" − #"too hard") / max(1, #feedback)
if completionRate ≥ 0.85 and total ≥ 4 and feedbackBias ≥ 0      → UP
if completionRate ≤ 0.40 and total ≥ 4  or  feedbackBias ≤ −0.5  → DOWN
else                                                             → HOLD
```

The prompt turns UP/HOLD/DOWN into instructions ("raise targets or complexity by about one step", "keep the level", "make tasks smaller and more concrete"). Feedback comes from three optional chips on the reward sheet of a Path quest: *Too easy · Right · Too hard*.

**Milestone advance:** the model may set `suggestAdvance`. The app shows *"The System judges you ready for: <next milestone>"* [Advance] [Stay] **only if** the engine also agrees: ≥ 4 Path quests completed since the current milestone began **and** last week's `completionRate ≥ 0.60`. Advancing a milestone grants no XP or gold (narrative only: a "MILESTONE CLEARED" window), so there is nothing to farm.

#### 6.6.6 Scheduling into the Directive

- When a week's blueprints are accepted, `PathScheduler` assigns them, in order, round-robin to the Path's active days of that ISO week (from today onward if the plan arrives mid-week). Assignment is stored, so it's stable.
- Each game-day, at most **3 Path quests in total** across all Paths. Overflow is ordered by Path creation time, then blueprint order, and moves to that Path's next active day.
- **Main promotion:** if a Path quest today has `stat == focusStats[gameDay % 2]`, the one with the highest priced difficulty (ties: earliest) becomes the Main quest. Otherwise Main comes from templates (§6.3).
- **Sides** = `max(1, 3 − pathQuestsToday)`. Total generated items stay ≤ 5 (Main + Paths + Sides), plus habits.
- **Carry-over:** an unfinished Path quest is re-offered once on the Path's next active day in the same ISO week, then becomes `SKIPPED`. The week boundary ends all carry-over.
- **Reroll** works on Path quests: it swaps in the next unscheduled blueprint of that Path for this week if one exists; else it falls back to a template side quest. Same reroll costs (§6.3).
- Idempotency: instance id = UUID created at scheduling; the scheduling key `(pathId, isoWeek, blueprintIndex)` is UNIQUE. Applying the same week response twice creates nothing new.

#### 6.6.7 Rate limits and budgets (enforced by the proxy; the app also enforces locally)

| Call | Limit |
|---|---|
| `PLAN_PATH` | 3 per install per game-day, 6 per ISO week |
| `WEEK_QUESTS` | 1 automatic per Path per ISO week + 2 manual retries per Path per ISO week |
| Request body | ≤ 8 KB; response capped at 2,000 output tokens |
| Global | Monthly spend cap; at 100% the proxy returns `503 budget_exhausted` and the app falls back silently to templates |

### 6.7 Screen Discipline (phone usage, opt-in)

The hunter tags apps **Distracting / Productive / Neutral** and sets a daily limit on distracting apps. Usage is read on the phone (Android usage access), never sent anywhere, and never pays anything by itself. The only reward is one daily quest.

- **Categories.** Defaults come from a bundled package list (Instagram, Facebook, TikTok, Snapchat, X/Twitter, Reddit, YouTube, Netflix, Prime Video, Hotstar, Threads, Pinterest, Discord → Distracting; a short list of work/learning apps → Productive), then Android's own category (Social, Video, Game → Distracting; Productivity → Productive; everything else Neutral). The hunter may change any app.
- **Limits.** Only Distracting apps carry a limit: default **30 min/day**, editable **5–240 min in 5-min steps**, or no limit (tracked only).
- **Edits apply from the next game-day.** Category and limit changes never affect today, so a limit can't be raised at 59/60 minutes. Turning Screen Discipline on also starts it tomorrow.
- **The DISCIPLINE quest.** Each game-day with ≥ 1 limited Distracting app gets one quest: stat **WIL**, **Normal**, verification `DEVICE_USAGE` (source `USAGE`, **V = 1.30**, uncapped like HEALTH). XP and gold follow §4.1/§9.1: **78 XP** at streak 0, up to 117 at streak 30; it can crit. It can't be rerolled and isn't "generated" for §6.4.
- **Held vs crossed.** Usage is floored to whole minutes; a limit is crossed when usage is strictly over it (exactly 30/30 holds). The first crossing **forfeits** the quest at once (status EXPIRED, "Forfeited"); nothing is ever deducted. If every limit held, the quest is completed at rollover for the finished day, before the day outcome (§7) is computed, and counts as one directive item.
- **Warnings.** One notification per app per day when ≤ **5 min** remain ("Cross it and you forfeit +78 XP"), and one notice when a limit is crossed. Checks are inexact (WorkManager), so a warning can lag; the verdict always comes from the usage events themselves.
- **Usage access revoked** mid-day → the quest expires unpaid; nothing else changes.
- **Notifications** use their own `screen_time` channel, outside the two-a-day reminder cap (ARCH §3.5), because the hunter opted in and each alert fires at most once per app per day.

### 6.8 The 45-day Campaign (opt-in)

A fixed-length program built from the hunter's own scan. Clean-room design: no content, art or code from any other app.

- **Scan.** 10 questions, two per stat, answered 0 (never) … 3 (almost always). Score per stat = sum (0–6). The weakest stat (ties → stat order STR, INT, WIL, VIT, CHA) is "where we start" and names the **archetype**: STR *The Rebuilder*, INT *The Scholar*, WIL *The Iron Will*, VIT *The Restorer*, CHA *The Envoy*.
- **Plan.** Up to **5 campaign habits** from the template library (SELF templates that support Easy, Normal and Hard; equipment and Injury Mode filters apply): weakest stat ×2 (daily + Mon/Wed/Fri), 2nd and 3rd weakest ×1 (Tue/Thu/Sat, Mon/Wed/Fri), 4th weakest ×1 (Tue/Thu/Sat). The strongest stat is left to the daily directive. Deterministic per hunter.
- **Habit slots.** Campaign habits count toward the 8 active habits (§4.3). With fewer than **3** free slots the campaign can't start; with 3–4 free it takes that many.
- **Length and acts.** **45 game-days**, day 1 = the day the oath is sealed. Acts of 15 days: **Foundation** (days 1–15, Easy), **Build** (16–30, Normal), **Ascend** (31–45, Hard). Each habit's difficulty is the act's tier, but never more than one step above that stat's adaptive tier (§6.4); its target is the template's target for that tier. Campaign habits are system quests from templates, so the custom-habit Normal cap (§4.3) does not apply to them; they are read-only in the habit editor and the SELF daily cap still applies.
- **Oath.** The hunter seals a short commitment (their name and the end date) with a long press. Stored locally; no signature is kept.
- **Progress.** A campaign day counts when the game-day is **cleared** (§7.1). Missed days never reset anything (pillar 3).
- **Completion.** When day 45 has been finalized: **≥ 32 cleared days (70 %)** → status COMPLETED and the **Reforged** achievement (+1,000 gold, title *Reforged*, §10); otherwise ENDED. Either way the campaign habits are archived. One active campaign at a time; the hunter may abandon it anytime (habits archived, nothing deducted).

---

## 7. Streak, rest, and the Penalty Zone

### 7.1 Cleared day

A game-day is **cleared** if the hunter completed the Main quest **or** at least 3 directive items (any type). Focus Gates count as items.

### 7.2 Streak

- Cleared day → `streak + 1`.
- **Rest Day**: user may mark up to 1 day per ISO week as rest *in advance* (before that day starts). Rest days keep the streak, don't increment it.
- **Recovery Mode** (sick/injured): pauses streak for up to 7 game-days per calendar month. Generation switches to `VIT`/`WIL` low-impact templates.
- **Streak Shield** (shop item): auto-consumed on the first uncleared day; streak survives. Hold max 2.
- Otherwise uncleared day → streak resets to 0 and the Penalty Zone triggers.

### 7.3 Penalty Zone (comeback, not punishment)

On the first open after a failed day:

1. Full-screen red "SYSTEM WARNING" moment (respect reduce-motion).
2. A **Penalty Quest** is added: Normal difficulty, from the `penalty` template set (short, doable today — e.g., "10-minute walk", "20 squats + 2 min plank").
3. Complete it the same day → `+1` "Comeback" counter and the badge progress for *The Redeemed* (§10). No XP loss ever.
4. While a penalty quest is open, the home screen is tinted (visual pressure only). Nothing is locked.

There is deliberately **no HP, no death, no gold loss**. Pressure comes from the streak and the visuals.

---

## 8. Focus Gates (timer dungeons)

The in-app-verified quest type. Turns study/deep-work into a dungeon run.

| Gate | Duration | Base difficulty |
|---|---|---|
| E-Gate | 15 min | Easy |
| D-Gate | 25 min | Normal |
| C-Gate | 50 min | Hard |
| B-Gate | 90 min | Extreme |

- User picks a gate + stat (INT default, or WIL/CHA/STR for practice sessions) + optional label ("DSA – graphs").
- Run = timestamps in Room + an ongoing notification with a system-drawn countdown (no foreground service; see ARCHITECTURE §3.3).
- **Breach rule**: if the app is in background for > 90 seconds cumulative *(phone calls excluded via call state where available; otherwise just the 90 s budget)*, the gate collapses → no XP, a "Gate collapsed" record. Up to 1 pause of ≤ 5 min allowed per run.
- Clear → `V = TIMER (1.15)`, quest XP via §4.1, + 1 Loot Fragment.
- **Gates launched from a GATE-mode Path quest** (§6.6.4) have their rank fixed by the quest and the stat fixed to the quest's stat. On clear, the reward is paid **once**, as the Path quest (`sourceType = QUEST`, `sourceId = questInstanceId`), never additionally as a standalone gate. A collapsed run leaves the Path quest open; the hunter may start the gate again the same day.
### 8.1 Custom and stopwatch gates, subjects

> **Approved 2026-11-01** (user: "complete all continuously") — numbers P1–P8 as proposed.

| # | Rule | Proposed value |
|---|---|---|
| P1 | Custom gate length | 10–180 min in 5-min steps (default 30) |
| P2 | Reward tier of a cleared custom/stopwatch run | by **counted minutes**: <15 → no reward (logged only); 15–24 → E; 25–49 → D; 50–89 → C; ≥90 → B. Beyond 90 min no extra tier. Same §4.1 formula, `V = TIMER (1.15)` |
| P3 | Stopwatch minimum to count | 15 min; stopping earlier records a "short run" with no XP and no collapse |
| P4 | Loot Fragment | 1 per cleared run of ≥ 25 min (D tier or higher); 0 for E |
| P5 | Daily cap | at most **4** custom + stopwatch runs per game-day pay XP (preset gates are unaffected); extras are logged |
| P6 | Breach rule | unchanged from §8 (90 s cumulative background, 1 pause ≤ 5 min); applies to stopwatch too |
| P7 | Subjects | max **12** active, name ≤ 24 chars; label only, no effect on rewards; archive instead of delete |
| P8 | Weekly focus report | no new numbers: totals per subject over the game-week from cleared runs |
| P9 | Stopwatch pauses (user request 2026-11-01) | unlimited, no 5-minute cap: tap to pause, tap to continue. Paused time is never counted. Presets and custom runs keep one pause of ≤ 5 min (§8). The breach budget is unchanged (time away while running counts, time away while paused does not) |

Test vectors (add to §12 on approval): custom 30 min → D, 1 fragment; stopwatch 14 min → no XP; stopwatch 120 min → B (not higher); 5th custom run in a game-day → 0 XP.

- Do NOT use AccessibilityService or block apps in v1 (Play policy review risk + permission friction). The breach budget is enough. (Screen Discipline, §6.7, only *reads* usage stats; it never blocks or interrupts an app.)

---

## 9. Economy (gold) — v1

### 9.1 Faucets

| Source | Gold |
|---|---|
| Quest completion | `round(questXp × 0.5)` (0 beyond daily cap) |
| Rank Trial cleared | 500 |
| Achievement unlocked | per achievement (10–200) |
| Day cleared | 20 (awarded when the day is finalized at rollover) |

### 9.2 Sinks

| Item | Price | Limit | Purpose |
|---|---|---|---|
| Streak Shield | 300 | hold ≤ 2 | loss insurance |
| Extra reroll | 40 | 3/day | autonomy |
| Theme: *Crimson Gate* | 1,500 | once | identity |
| Theme: *Abyssal Violet* | 1,500 | once | identity |
| Theme: *Sovereign Gold* (requires rank S) | 3,000 | once | status |
| Title frames (×4) | 600 / 800 / 1,000 / 1,200 | once | identity |
| Hunter sigils (×8 icon badges) | 400 | once | identity |

**No XP boosters for gold. No XP for gold. No real-money anything in v1.** A healthy economy needs sinks that don't touch progression; cosmetics + insurance do that.

**Loot Fragments** (from crits and Focus Gate clears): 10 fragments → open a **Relic Cache** containing a random cosmetic not yet owned (sigils/frames only). Earned only, never purchasable — keeps us clear of loot-box disclosure concerns.

### 9.3 Ledger rules (non-negotiable)

- Balances (XP, stat XP, gold, items, fragments) = `SUM(delta)` over an **append-only ledger**.
- Every ledger row has `(sourceType, sourceId, kind)` → UNIQUE. Re-applying the same event is a no-op (idempotency).
- Each completion attempt has its own `completionId = questInstanceId#attemptN` (N from 1); quest ledger rows use it as `sourceId`, so re-completing after an undo gets fresh keys. Re-completion is priced normally, including daily caps.
- Undo = insert a reversing row (`sourceType = UNDO`, `sourceId = completionId`, same kind/stat/item). Undoing the same completion twice is a no-op. Never `UPDATE`/`DELETE` ledger rows.
- A cached `balances` table may exist for speed, updated in the **same transaction**, and must be rebuildable from the ledger (`Settings → Advanced → Rebuild from ledger` dev option + a test).

---

## 10. Achievements & titles (launch set: 32, plus Reforged)

Each unlock: toast + haptic + gold reward. Titles are equippable; one shows under the hunter name.

| ID | Name | Condition | Gold | Title? |
|---|---|---|---|---|
| first_blood | First Clear | Complete 1 quest | 10 | — |
| awakened | Awakened | Finish onboarding | 10 | "The Awakened" |
| lvl5 / lvl10 / lvl25 / lvl50 | Level milestones | reach level | 25/50/100/200 | L10 "Rising Hunter", L50 "Veteran" |
| streak3/7/30/100 | Unbroken | streak days | 20/50/150/200 | 30 "Unbroken", 100 "The Relentless" |
| crit1 / crit25 | Lucky Strike / Fortune's Edge | crits | 15/100 | 25 "Fortune's Favoured" |
| gate_first / gate_hours10 / gate_hours100 | Gatebreaker | Focus Gate count / hours | 20/100/200 | 100h "Gate Sovereign" |
| verified10 / verified100 | Witnessed | HEALTH-verified quests | 30/150 | 100 "Proven" |
| redeemed | The Redeemed | Complete 3 penalty quests | 50 | "The Redeemed" |
| balanced | Pentagon | Every stat ≥ level 5 | 100 | "Balanced" |
| str20 … cha20 | Stat mastery | stat level 20 (×5) | 100 | e.g. "Ironborn", "Sage", "Stoic", "Evergreen", "Silver Tongue" |
| rank_d … rank_s | Promotions | clear Rank Trial | (trial reward) | "D-Rank Hunter" … "S-Rank Hunter" |
| early_bird | Dawn Raid | Complete a quest before 07:00 on 5 days | 50 | — |
| night_owl | Midnight Gate | Clear a Focus Gate after 23:00 | 20 | — |
| collector | Relic Hunter | Open 5 Relic Caches | 50 | — |
| reforged | Reforged | Complete a 45-day campaign (§6.8) | 1,000 | "Reforged" |

Achievements are evaluated by a pure function over ledger/state after each write — no ad-hoc counters scattered in UI code.

---

## 11. Narrative layer (cheap, high-impact)

- **System voice**: terse, second person, uppercase headers. *"QUEST CLEARED." "THE SYSTEM HAS NOTICED YOUR PERSISTENCE."* Store all strings in `strings.xml` (localisation-ready).
- **Chapters**: 6 short chapters (≤ 120 words each) unlocked at L1, L5, L10, L20, L35, L50. Written once, bundled. Each unlock = full-screen reveal.
- **Daily System Message**: picked from ~60 handwritten lines keyed by context (streak milestone, comeback, neglected stat, trial active). Deterministic by date.
- **Weekly Report** (Sunday): stats gained, best day, most-neglected stat, next-week recalibration. Shareable as an image card (§ DESIGN_SYSTEM share card).

Template quests, chapters and System lines stay hand-written and bundled. AI writes only Path content (§6.6), through the proxy. Never ship an API key in the APK.

---

## 12. Test vectors (must pass in `:engine` unit tests)

| # | Input | Expected |
|---|---|---|
| 1 | NORMAL, streak 0, SELF, no crit | `round(40×1.5×1.0×1.0×1)` = **60** |
| 2 | HARD, streak 12, HEALTH, no crit | `40×2.25×1.2×1.3` = 140.4 → **140** |
| 3 | EASY, streak 45 (capped 30), TIMER, crit | `40×1.0×1.5×1.15×2` = **138** |
| 4 | EXTREME, streak 30, HEALTH, crit | `40×3×1.5×1.3×2` = **468** |
| 5 | Gold for #2 | `round(140×0.5)` = **70** |
| 6 | `xpToNext(1)` | **112** |
| 7 | `xpToNext(10)` | `round(100+12×10^1.6)` = **578** |
| 8 | `level(0)` / `level(111)` / `level(112)` / `level(2505)` | **1 / 1 / 2 / 10** |
| 9 | `statLevel(0)` / `statLevel(160)` / `statLevel(3240)` | **1 / 3 / 10** |
| 10 | Self XP earned today 580, new SELF quest worth 60 | Rule is per-quest, not partial: `580 + 60 > 600`, so the whole quest is reduced → `round(60×0.25)` = **15 XP, 0 gold** |
| 11 | Same ledger event applied twice | balance unchanged on 2nd apply |
| 12 | Completion then undo | XP, gold, stat XP net 0; two ledger rows exist |
| 13 | Day with main incomplete but 3 sides done | **cleared** |
| 14 | Uncleared day, 1 shield held | streak kept, shields 0, no penalty quest |
| 15 | Same `(hunterId, gameDate)` generation called twice | identical directive, no duplicates |

**AI Paths (§6.6)** — fixtures live in `engine/src/test/resources/paths/`; the response JSON in each case is a hand-written fixture, not a live model call.

| # | Input | Expected |
|---|---|---|
| 16 | SELF blueprint, `effort = EXTREME`, streak 0, no crit | difficulty **NORMAL**, verification SELF → XP **60** (vector 1) |
| 17 | GATE blueprint, `estMinutes = 40` | requires **C-Gate (50 min)**, difficulty **HARD**, verification **TIMER** |
| 18 | GATE blueprint, `estMinutes = 5` / `= 120` (clamped from 300) | **E-Gate** / **B-Gate** |
| 19 | `unit = "CALORIES"` | blueprint **dropped** (V1) |
| 20 | `unit = REPS`, `target = 500` / `PROBLEMS`, `target = 0` | target **300** / **1** (V4) |
| 21 | Path primary INT, secondary WIL; blueprint `stat = STR` | stat **INT** (V3) |
| 22 | `weeklyMinutes = 300`, blueprints `estMinutes` = [120, 120, 90, 60] | keep first **3** (cumulative 330 ≤ 360; adding 60 → 390 > 360) (V8) |
| 23 | Title "Read: http://evil.example now" | stored title **"Read: now"** (V2; whitespace collapsed) |
| 24 | Injury Mode excludes `impact`; blueprint tags `[impact, outdoor]` | **dropped** (V6) |
| 25 | All blueprints dropped | Path has 0 quests this week; the Directive equals the §6.3 output for the same inputs with `paths = []` (3 sides) |
| 26 | Last week: 6 completed, 1 expired, feedback 1 "too easy" / 0 "too hard" | rate 0.857, total 7 → **UP** |
| 27 | Last week: 2 completed, 4 skipped | rate 0.333 → **DOWN** |
| 28 | Last week: 3 completed, 0 other | total 3 < 4 → **HOLD** |
| 29 | 6 blueprints, active days Mon/Wed/Fri, `questsPerDay = 2`, plan accepted Monday | Mon: #1,#4 · Wed: #2,#5 · Fri: #3,#6 (round-robin in order) |
| 30 | Today has 2 Path quests (stats INT, WIL), today's focus stat INT | Main = the INT Path quest; Sides = **1**; total generated items = 3 |
| 31 | Two Paths schedule 2 quests each on the same day | **3** shown (older Path first); the 4th moves to its Path's next active day |
| 32 | Same week response applied twice | identical scheduled instances; no duplicates (UNIQUE `(pathId, isoWeek, blueprintIndex)`) |
| 33 | Gate cleared from a GATE-mode Path quest (D-Gate, NORMAL, streak 0, no crit) | exactly **one** reward: `round(40×1.5×1.0×1.15)` = **69 XP**, `sourceType = QUEST`; no separate gate reward |
| 34 | `suggestAdvance = true`, 3 Path quests completed since milestone start | advance **not** offered (needs ≥ 4) |
| 35 | SELF blueprint `estMinutes = 2` → clamped 5 / `estMinutes = 120` | `minDurationSec` = **75** / **900** (clamp(5×15, 60, 900) = 75; clamp(1800, 60, 900) = 900) |

**Screen Discipline (§6.7)**

| # | Input | Expected |
|---|---|---|
| 36 | DISCIPLINE quest, streak 0 / streak 30, no crit, 10,000 SELF XP already today | **78** / **117** XP; no cap applies |
| 37 | Instagram limit 30 min; used 30 min 59 s / 31 min | **held** / **crossed** |
| 38 | Limit 30 since 3 days ago; today the hunter sets 120 | today **30**, tomorrow **120** |
| 39 | 600 min each on a Productive app, a Neutral app and a Distracting app with no limit | nothing crossed; **no stake** (no quest) |
| 40 | DISCIPLINE quest for the same (hunter, day) twice; next day | same instance id / different id; never rerollable; not "generated" |

**45-day Campaign (§6.8)**

| # | Input | Expected |
|---|---|---|
| 41 | Start 2026-10-25 | day 1 = start, day 45 = 2026-12-08, 2026-12-09 is past the end |
| 42 | Day 15 / 16 / 30 / 31 | Foundation / Build / Build / Ascend |
| 43 | Ascend with stat tier Normal / Easy | Hard / Normal (one above the adaptive tier at most) |
| 44 | 32 / 31 cleared days at the end | COMPLETED / ENDED |
| 45 | Campaign completed once | Reforged unlocks once: +1,000 gold, title "Reforged" |

> Note on vector 7: the level table in §4.4 lists the *step into* each level (i.e. `xpToNext(L−1)`), so "L10 step 504" = `xpToNext(9)`. Vector 7 is `xpToNext(10)` = 578. Tests are authoritative.

## 13. Body tracking — Gym and Diet (trackers, no XP)

Approved 2026-10-27. Gym and Diet are trackers: they mint **no XP, gold, stat XP or items** and write no ledger rows. Hooks into the RPG are in `BACKLOG.md` and need their own numbers here before any code. Rules live in `:engine` `body/` (`NutritionMath`, `TrainingMath`). Units are metric (kg, cm, g).

### 13.1 Diet — calorie budget and nutrient targets
- **BMR** (Mifflin–St Jeor): `10·kg + 6.25·cm − 5·age + 5` (male) / `− 161` (female). Age = game-date year − birth year.
- **TDEE** = BMR × activity: sedentary 1.2 · light 1.375 · moderate 1.55 · very active 1.725 · extra active 1.9.
- **Calorie budget** = round(TDEE) + goal delta: lose −500 · maintain 0 · gain +300; never below 1,200 (female) / 1,500 (male).
- **Protein** 2.0 g/kg (lose) or 1.6 g/kg (maintain, gain). **Fat** 25 % of kcal ÷ 9. **Carbs** = (kcal − 4·protein − 9·fat) ÷ 4, never below 0. **Fiber** 14 g per 1,000 kcal. **Saturated fat** limit 10 % of kcal ÷ 9.
- **Defaults from FDA Daily Values:** cholesterol ≤ 300 mg, sodium ≤ 2,300 mg, potassium 4,700 mg, calcium 1,300 mg, iron 18 mg, magnesium 420 mg, vitamin A 900 µg, vitamin C 90 mg, vitamin D 20 µg. Sugars, mono/poly/trans fat, caffeine and alcohol have no default.
- **Overrides:** every target is editable and an override wins. Overriding kcal alone rescales fat, carbs, fiber and saturated fat; overriding a macro sets it outright.
- **Logging:** a diary row stores grams and a snapshot of the food's per-100 g nutrients; amounts = per-100 g × grams ÷ 100. Rows belong to the game-day (§2) they're logged for.

### 13.2 Gym — volume, PRs and muscle load
- **Volume** = Σ weight × reps over done sets.
- **Estimated 1RM**: the weight for a single; Epley `w × (1 + reps/30)` for 2–12 reps; no estimate above 12 reps or for unweighted sets. An exercise's PR is its best estimate.
- **Muscle load** per week (Monday → Sunday): each done set adds 1 to its exercise's primary muscles and 0.5 to secondary ones (a muscle listed in both counts as primary).
- **Body-map bands:** 0 sets · ≤ 5 · ≤ 10 · ≤ 15 · more (bands 0–4).
- **Rest between sets (tracker only, no reward).** Each exercise carries an optional rest, stepped in 15 s from 15 s to 600 s; the first step up from "Off" is 60 s, and stepping down from 15 s turns it off. The value is remembered from the last session and saved with a routine. Ticking a set done starts the exercise's rest; the end time is stored on the workout, so it survives the app being killed. A rest that ended more than 10 s ago is cleared without a sound. These are input bounds for a tracker (like the weight and reps caps), not game numbers: they feed no XP, gold or cap.

| # | Input | Expected |
|---|---|---|
| 46 | Male, 25 y, 175 cm, 80 kg, moderate, maintain | BMR 1,774 · TDEE 2,749 · budget 2,749 kcal · protein 128 g |
| 47 | Female, 30 y, 160 cm, 60 kg, sedentary, lose | 1,547 − 500 = 1,047 → floor **1,200** |
| 48 | Sets 100×1 / 90×6 / 120×20 | best set 90×6 (e1RM 108); 120×20 has no estimate |
| 49 | 2 bench sets (chest; shoulders, triceps) + 1 dip (triceps; chest) | chest 2.5 · triceps 2 · shoulders 1 |

---

## 14. v1.1 growth layer — consistency, insights, review, blocks, recaps, identities, presentation

> **Approved 2026-10-27 (user: "approved as proposed").** Numbers new in this section were signed off together with the open-question resolutions; reused numbers cite their source section.
>
> Ground rules for the whole section: everything is computed on-device by `:engine` over Room data; no network, no AI (AI Paths stay dormant, §6.6 unchanged). **Nothing here grants XP.** Reviews, insights and recaps write no ledger rows; only the new achievements (§14.8) pay gold, through the existing achievement pipeline (§10). Nothing here changes generation (§6.3), pricing (§4), streaks (§7) or caps (§4.3).

### 14.1 Definitions shared by this section

- **Finalized day:** a `day_record` with `outcome` set (§7, ENGINE_DECISIONS #23). Today is never finalized, so it is never counted.
- **Missed day:** outcome `BROKEN` or `SHIELDED` (an uncleared day that rest/recovery did not cover; a shield saves the streak but the day was still missed).
- **Excluded day:** outcome `REST` or `RECOVERY`. A rest or recovery day that was cleared has outcome `CLEARED` (ENGINE_DECISIONS #12) and is an ordinary cleared day.
- **Analysable day:** a finalized day on or after `first_game_date` that is not excluded.
- **Day score** `q(d)`: active (not undone) completions on game-day `d`, all quest types (the heatmap count, ENGINE_DECISIONS #39, without the cap at 4).

### 14.2 Consistency score and "never miss twice" (PRD F28)

```
window      = the last 28 finalized game-days, ending yesterday
              (days before first_game_date are not in the window, so a new hunter has a shorter window)
eligible    = days in window that are not excluded
cleared     = days in window with outcome CLEARED
consistency = cleared / eligible          (eligible = 0 → no score; UI shows "—")
```

- Shown as **"{cleared} of the last {eligible} days"** plus a `ConsistencyMeter` filled to the ratio. The meter has no bands, no colour by value and no "good/bad" threshold. The streak (§7.2) is unchanged and shown beside it, not replaced.
- **"Never miss twice" line:** on game-day `D`, if `D−1` is a missed day, Today shows one calm line in place of the daily System line (§11): *"Missed yesterday. Don't miss twice."* If `D−2` was also missed it reads *"A fresh start today."* instead (missing twice is not repeated back at the hunter). No line after excluded days.
- **Recovery:** a finalized `CLEARED` day whose previous day is a missed day. Derived from `day_record` by replay (invariant 7); **nothing is stored**. Counted for the `never_twice` achievement (§14.8).
- The Penalty Zone (§7.3) is unchanged and still triggers on a `BROKEN` day; the line and the penalty quest coexist.

### 14.3 Personal insights (PRD F29)

`InsightEngine.evaluate(history, asOf) → List<Insight>`, pure and deterministic. `history` = analysable days among the **last 84 finalized game-days up to `asOf`**, with per-day `q(d)`, sleep/steps aggregates (when Health is on), quest blocks (§14.5) and cleared-gate start times.

**Guardrails** (apply to every rule unless the rule says otherwise):

| Guardrail | Value |
|---|---|
| Minimum analysable days before any insight is shown | **14** |
| Minimum days in each comparison bucket | **5** |
| Absolute difference of bucket means | **≥ 0.5 quests/day** |
| Relative difference, measured against the lower bucket: `(hi − lo) / lo` | **≥ 25 %**; `lo = 0` passes and the copy uses absolute numbers |
| Insights visible at once | **3** |

**Rules** (each yields at most one insight; rule order is the tie order):

| # | Rule | Buckets | Notes |
|---|---|---|---|
| I1 | Sleep | A: days whose sleep (longest merged session ending in the game-day, ENGINE_DECISIONS #50) is **≥ 7 h** · B: < 7 h | 7 h reuses the `vit_sleep` target (§6.2). Days without a sleep session are left out. Only with Health on and Sleep granted. |
| I2 | Weekday | One bucket per ISO weekday; compare the best and the worst weekday by mean `q` | Weekdays with < 5 days are ignored; needs ≥ 2 weekdays left. Ties → earlier weekday. |
| I3 | Time of day | One bucket per block Morning / Day / Evening (§14.5); metric = **completion rate** `completed / offered` of quests assigned to that block | Anytime is ignored. Offered = instances shown that day (not REROLLED/REPORTED). A block-day counts toward the bucket size when ≥ 1 quest was offered in it. Because this is a rate, the absolute guardrail is **≥ 15 percentage points** instead of 0.5 quests/day. Uses only quests that carry a stored block (after F31 ships). |
| I4 | Focus hour | Cleared Focus Gates in the window grouped by local start hour | Not a two-bucket comparison. Qualifies with **≥ 8 cleared gates** and one hour holding **≥ 25 %** of them (reuses the relative guardrail), with no tie for the top. |
| I5 | Steps | A: days with steps **≥ 7,000** · B: fewer | 7,000 reuses the Normal `HEALTH_STEPS` target (§6.2). Days with no step data (or 0 steps) are left out. Health on only. |
| I6 | Momentum | A: the **3** game-days after a game-day with a cleared Rank Trial stage · B: all other analysable days | There are no dungeons in the app, so only Trial stages count. |

**Selection and rotation (ISO week `W`).** The set is fixed for the whole week:

```
Q(W)  = evaluate(history, asOf = last game-day of W−1)
rot(X, W) = X in rule order, rotated by fnv1a64("hunterId|W|insights") mod |X|
S0(W) = first 3 of rot(Q(W), W)                                  // base selection, no look-back
S(W)  = first 3 of rot(Q(W) \ S0(W−1), W)  followed by  rot(Q(W) ∩ S0(W−1), W)
```

`S0(W−1)` is recomputed from history up to the end of `W−2`; nothing is stored, and the one-level look-back keeps it bounded. Status shows `S(W)`; the Weekly Review (§14.4) shows the first of `S(W)`.

**Copy rules.** Correlational only ("On days you sleep 7 h or more, you complete 38 % more quests."). Never causal ("because", "makes you"), never medical advice, never shaming, never about one bad day. Percent shown = `round((hi − lo) / lo × 100)`, half-up. **Empty state:** fewer than 14 analysable days → "{14 − n} more days and your patterns will show." (pluralised); 14 or more with nothing qualifying → "No clear patterns yet. Keep going."

### 14.4 Weekly review (PRD F30)

- **Reviewed week** = ISO week `W` (Monday–Sunday game-days). **Window:** game-days Saturday and Sunday of `W` and Monday of `W+1`. On Saturday the numbers are "so far".
- **One review per `W`**, stored in `weekly_review` with `iso_week` UNIQUE; a second submit is a no-op (`INSERT … IGNORE`). A submitted review is read-only (no edit, no redo).
- **Flow** (≤ 5 taps + optional text):
  1. This week: the Weekly Report numbers (§11, ENGINE_DECISIONS #40), consistency (§14.2) and the first insight of `S(W)` (or the empty-state line).
  2. "What went well?" optional, **≤ 280 chars**.
  3. "What slipped?" optional, **≤ 280 chars**.
  4. Next week's focus stats: **exactly 2** (focusStats is always two, §2), pre-filled with the current pair.
  5. Intention: optional one line, **≤ 120 chars**.
  Text is cleaned like Path text (ENGINE_DECISIONS #59: control/format chars → space, collapse whitespace, trim, truncate by code points). It never leaves the phone.
- **Focus stats** from step 4 are written to `hunter_profile` like a Settings change and apply from the **next generated directive** (today's is never regenerated). The generator uses its existing focus weighting (§6.3); no new numbers.
- **Identity line** (§14.7): if the hunter has identities, step 2 is headed "Did you act like someone who {identity}?", the identity picked by `fnv1a64("hunterId|W|identity") mod count`. Nothing is stored beyond the free text.
- **Rewards:** none. Achievements for 1 / 4 / 12 submitted reviews (§14.8).
- **Notification:** on game-day Sunday, if a reminder time is set and `W` has no review, the existing directive notification at the reminder time gains a second line ("Your weekly review is open.") and a review action. It stays one notification, so the 2-per-day cap (ARCH §3.5) is untouched.

### 14.5 Time-of-day blocks (PRD F31)

| Block | Default range (game-day local time) |
|---|---|
| Morning | `dayStartHour` → **12:00** |
| Day | 12:00 → **17:00** |
| Evening | 17:00 → next `dayStartHour` |
| Anytime | not time-bound |

- **Settings:** "Morning ends" **09:00–14:00** and "Day ends" **15:00–21:00**, whole hours. The ranges don't overlap, so Morning always ends before Day. Changes are presentation only and apply at once.
- **Assignment:** templates gain optional `timeOfDay ∈ {MORNING, DAY, EVENING}` (absent = Anytime; checked by `TemplateLibrary.validate()`); habits get a user-set block (default Anytime). Path, penalty, trial, discipline and gate quests are Anytime. The block is **copied onto `quest_instance` at generation**, so later template/habit edits never move past quests and Insight I3 reads history reliably.
- **Today:** the Directive renders grouped Morning → Day → Evening → **Anytime last**. The block containing "now" is highlighted; earlier blocks collapse to a header ("Morning · 2 of 3 done") that expands on tap, and their open quests stay completable until the day ends. Empty blocks are hidden. The penalty quest stays pinned on top (§7.3).
- No change to generation rules, counts, rewards, caps, rerolls or day clearing.

### 14.6 Monthly and yearly recaps (PRD F32)

- **Monthly:** period = the previous calendar month of game-dates. Offered on the first app open on or after the first game-day of the next month; only the immediately previous month is generated (an older month nobody opened the app after is skipped). Requires **≥ 7 finalized game-days** in the month.
- **Yearly:** period = the previous calendar year. Auto-offered on first open during game-days **Jan 1–14**; requires **≥ 30 finalized game-days** in the year. When both are due, the yearly recap opens and December's waits in the list.
- **Storage:** `recap(period_key PK 'M:2026-10' | 'Y:2026', generated_at, payload_version, payload_json)`. Generation is idempotent on `period_key`; the payload is a snapshot cache rebuildable from Room. Recaps stay reopenable from Status. Included in export/import.
- **Contents** (all derived; a field with no data is left out, never shown as 0):

| Field | Rule |
|---|---|
| Quests completed | active completions with `game_date` in the period |
| Focus Gate hours | Σ planned duration of cleared gates ÷ 60, one decimal (same basis as `gate_hours*`, §10) |
| Best streak | highest streak reached on a day in the period (`StreakRules.replay`) |
| Consistency | §14.2 formula over the **whole period** instead of 28 days |
| Top stat + growth | stat with the most net `XP_STAT` in the period (ties: stat order); growth = its stat level at period end − at period start |
| Levels gained | `level(XP at end) − level(XP at start)` |
| Distance, steps, sleep (Health on only) | total km; mean daily steps over days with data; mean sleep hours over nights with data |
| One insight | first result of `InsightEngine.evaluate` over the period's days with the §14.3 guardrails; omitted if none |
| Achievements earned | unlocks whose `unlocked_at` falls in the period |

- **Cards** (3–5, swipeable): ① the period, quests completed, levels gained · ② effort: gate hours, consistency, best streak · ③ growth: top stat and its growth · ④ body (only with Health data) · ⑤ summary with the insight and achievement count, exportable as a 1080×1350 share image through the existing `ShareCard` path (stays dark in every mode, DS §2.1b). Reduce motion: crossfade only, no auto-advance.

### 14.7 Identities (PRD F33)

- `assets/identities.json` = `{version, identities: [{id, stat}]}`; display text in `strings.xml` as `identity_<id>`. Launch list (12):

| id | "Someone who …" | Stat |
|---|---|---|
| trains | trains | STR |
| runs | runs | STR |
| reads | reads | INT |
| ships | ships | INT |
| learns | keeps learning | INT |
| focuses | focuses deeply | WIL |
| keeps_promises | keeps their promises | WIL |
| meditates | meditates | WIL |
| sleeps_well | sleeps well | VIT |
| eats_well | eats well | VIT |
| stays_in_touch | stays in touch | CHA |
| speaks_up | speaks up | CHA |

- Onboarding step "Who are you becoming?" before the focus-stat step: pick **1–3**, skippable. Stored as ordered ids in `hunter_profile.identities` (exported).
- **Pre-selection:** the distinct stats of the picks, in pick order; the first two become the pre-selected focus stats; a single distinct stat pre-selects only that one. The hunter can still change both.
- Shown under the name on Status ("Someone who trains · reads"), editable in Settings (0–3), used by the review line (§14.4). **No rewards.** Existing hunters have none until they set them.

### 14.8 New achievements (gold via §10)

| ID | Name | Condition | Gold | Title? |
|---|---|---|---|---|
| never_twice | Never Twice | 5 recoveries (§14.2) | 50 | — |
| review1 | First Look | 1 weekly review | 10 | — |
| review4 | Taking Stock | 4 weekly reviews | 50 | — |
| review12 | The Reflective | 12 weekly reviews | 150 | "The Reflective" |

All within the §10 range (10–200). Unlocks are permanent (§6.5). The set becomes 33 + 4 = 37. Insights, recaps, identities and opening the app grant nothing.

### 14.9 Presentation mode (Clean / System) (PRD F34)

- `presentationMode ∈ {CLEAN, SYSTEM}` in the UI DataStore (not exported, like other UI prefs, ENGINE_DECISIONS #48). **New installs: CLEAN. Installs upgrading from v1.0 (onboarding already done when the pref is first read): SYSTEM**, so nobody's app changes under them.
- **Invite:** after the first level-up ceremony (end of the reward chain, after any chapter and rank-up) while in CLEAN and never answered: "Want the full System experience?" [Try it] [Keep clean]. Either button records the answer and it never shows again; dismissing without a choice shows it again at the next level-up.
- **Same game in both modes:** ranks E–S, levels, XP, gold, streaks, caps and every number in this file are identical. Only labels, copy voice and the gate glow differ (term list in DESIGN_SYSTEM §8.3).
- **Voice:** `assets/narrative/system_lines_clean.json` mirrors `system_lines.json` (same contexts and placeholders; line counts may differ). Selection is unchanged (`fnv1a64(hunterId|date|context) mod lines`, ENGINE_DECISIONS #29) over the active file.

### 14.10 Test vectors (v1.1, continuing §12 numbering)

**Consistency (§14.2)**

| # | Input | Expected |
|---|---|---|
| 50 | 28 finalized days: 22 CLEARED, 6 BROKEN | 22 / 28 = **0.786**; "22 of the last 28 days" |
| 51 | 28 days: 20 CLEARED, 2 REST, 1 RECOVERY, 5 BROKEN | eligible **25**, cleared **20** → **0.80** |
| 52 | A rest day that was cleared (outcome CLEARED) | counts as eligible **and** cleared |
| 53 | Hunter started 6 days ago: 5 CLEARED, 1 BROKEN, today not finalized | **5 / 6** |
| 54 | Window holds only REST/RECOVERY days | eligible 0 → **no score** |
| 55 | Yesterday BROKEN (day before CLEARED) / yesterday SHIELDED / yesterday REST | "Missed yesterday…" / same line / **no line** |
| 56 | Yesterday and the day before both BROKEN | "A fresh start today." |
| 57 | Outcomes C, B, C, S, C, B, B, C (C cleared, B broken, S shielded) | recoveries **3** (the C after B, after S, after the second B) |
| 58 | 5th recovery finalized | `never_twice` unlocks once, +50 gold; a 6th changes nothing |

**Insights (§14.3)**

| # | Input | Expected |
|---|---|---|
| 59 | 13 analysable days | **no insights**; empty state "1 more day…" |
| 60 | Sleep: 6 days ≥ 7 h mean q 3.3; 4 days < 7 h mean 2.0 | B has 4 < 5 days → I1 **not shown** |
| 61 | Sleep: 8 days ≥ 7 h mean 3.3; 6 days < 7 h mean 2.4 | abs 0.9, rel 0.375 → **shown**, "38 % more" |
| 62 | Steps: A mean 2.2 (7 days), B mean 1.9 (7 days) | abs 0.3 < 0.5 → **not shown** |
| 63 | Steps: A mean 1.0 (6 days), B mean 0.4 (8 days) | abs 0.6, rel 1.5 → **shown**, "150 % more" |
| 64 | Weekdays: Tue 6 days mean 4.0, Sun 5 days mean 2.5, Wed 3 days mean 0.5, others in between with ≥ 5 days | Wed ignored (< 5 days); best **Tue**, worst **Sun**; abs 1.5, rel 0.6 → **shown** |
| 65 | Blocks: Morning 18/22 offered over 10 block-days, Evening 9/18 over 9 block-days, Day in between | 0.818 vs 0.500: 31.8 pts ≥ 15, rel 0.64 → **shown**, Morning best |
| 66 | Focus: 10 cleared gates, 4 started 09:xx, 3 at 21:xx, 3 elsewhere | 4/10 = 40 % ≥ 25 %, unique top → "around 9 am" |
| 67 | Focus: 7 cleared gates, all 09:xx | < 8 gates → **not shown** |
| 68 | 5 insights qualify in W; S0(W−1) = {I1, I2, I5} | S(W) = the 2 not in S0(W−1), then **1** of {I1, I2, I5} |
| 69 | Only I2 qualifies two weeks running | I2 shown both weeks |
| 70 | Same history and W evaluated twice | identical list and order |

**Weekly review, blocks, recaps, identities**

| # | Input | Expected |
|---|---|---|
| 71 | Game-day Fri / Sat / Sun of W, Mon / Tue of W+1 | review for W **closed / open / open / open / closed** |
| 72 | Review for W submitted twice | one row; second is a no-op; review achievements counted once |
| 73 | 4th review submitted | `review4` unlocks, +50 gold; **no XP** row exists for any review |
| 74 | dayStart 4, default blocks; "now" = 03:30, 11:59, 12:00, 16:59, 17:00 | Evening (of the previous game-day) / Morning / Day / Day / Evening |
| 75 | Identity picks [reads, trains, ships] | pre-selected focus stats **INT, STR** |
| 76 | Identity picks [reads, ships] | pre-selected **INT** only |
| 77 | Previous month had 6 finalized game-days | **no** monthly recap |
| 78 | Monthly recap `M:2026-10` generated twice | one row, same payload |
| 79 | First open on game-day Jan 15; last year had 200 finalized days | yearly recap **not auto-offered**; reopenable from Status only if already generated |
