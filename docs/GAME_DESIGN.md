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
5. **Calm and clear.** Every screen shows what matters; nothing is decoration, and no screen speaks in story or game voice.

---

## 2. The Hunter

| Field | Notes |
|---|---|
| `hunterName` | 3–20 chars, set at Awakening (onboarding) |
| `level` | Derived from total XP (§4). Never stored as source of truth. |
| `rank` | E/D/C/B/A/S. Derived from level **and** passed Rank Trials (§5). |
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
questXp = round( BASE × D × S × V )

BASE = 40

D (difficulty)        Easy 1.0 | Normal 1.5 | Hard 2.25 | Extreme 3.0
S (streak)            1 + min(streakDays, 30) / 60          → 1.00 … 1.50
V (verification)      SELF 1.00 | TIMER 1.15 | HEALTH 1.30
```

Rounding: `kotlin.math.round` on the final product only (half-even is NOT used; use `Math.round` semantics = half-up). Intermediate values are `Double`.

**Why no level-scaling factor?** The old PRD applied `1/(1+0.05·level)` *and* an exponential level curve. That double-penalises long-term users — a level-60 hunter would earn a quarter of the XP for the same push-ups while needing 10× more per level. Effort should be worth the same at every level; the curve alone handles pacing.

### 4.2 (removed)

Critical hits were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). XP is always the formula in §4.1; there is no variable reward.

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

Reaching the first level of a new band does **not** promote you. It unlocks the **Rank Trial**: a 3-day gate with one Hard quest per day in the hunter's two weakest stats + one focus stat. Clear all 3 stages (any order, within 5 days) → promotion + 500 gold.

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
  "title": "Do {n} push-ups",
  "description": "Complete {target} push-ups. Sets of any size.",
  "unit": "reps",
  "targets": { "EASY": 20, "NORMAL": 40, "HARD": 80, "EXTREME": 120 },
  "verification": "SELF",
  "equipment": [],
  "tags": ["bodyweight", "indoor", "no_impact_false"],
  "minDurationSec": 120
}
```

**Titles** say what to do, in plain words, and carry the target: `title` is one pattern per template, rendered with the quest's target when it is generated ("Walk {n} steps" becomes "Walk 7,000 steps"), so the quest row has no separate target line. Action first, sentence case, at least 2 words, **≤ 44 chars once rendered at any difficulty**, no random variants. Tokens: `{n}` (the target, thousands separators), `{dur}` for `sec` targets ("30 seconds", "1 minute", "90 seconds", "3 minutes"), and `{one|many}` for a word that agrees with a target of 1 ("Compliment {n} {person|people}"). Exactly one of `{n}` / `{dur}` per pattern; no other digits. No metaphors or fantasy words (forge, power, gate, realm, …: the full list is `TemplateLibrary.BANNED_TITLE_WORDS`). Good: "Do 25 push-ups", "Walk 7,000 steps". Bad: "Iron Arms", "Fuel the Forge". `TemplateLibrary.validate()` enforces this, so `ContentAssetsTest` fails on a bad title. Already generated quests keep the title they were created with. Path (AI) titles are untouched; the proxy prompt already asks for plain titles.

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

### 6.8 (removed)

The 45-day Campaign (scan, archetypes, oath, acts, Reforged) was removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). Programs (§16.15) cover fixed-length plans.

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
- Clear → `V = TIMER (1.15)`, quest XP via §4.1.
- **Gates launched from a GATE-mode Path quest** (§6.6.4) have their rank fixed by the quest and the stat fixed to the quest's stat. On clear, the reward is paid **once**, as the Path quest (`sourceType = QUEST`, `sourceId = questInstanceId`), never additionally as a standalone gate. A collapsed run leaves the Path quest open; the hunter may start the gate again the same day.
### 8.1 Custom and stopwatch gates, subjects

> **Approved 2026-11-01** (user: "complete all continuously") — numbers P1–P8 as proposed.

| # | Rule | Proposed value |
|---|---|---|
| P1 | Custom gate length | 10–180 min in 5-min steps (default 30) |
| P2 | Reward tier of a cleared custom/stopwatch run | by **counted minutes**: <15 → no reward (logged only); 15–24 → E; 25–49 → D; 50–89 → C; ≥90 → B. Beyond 90 min no extra tier. Same §4.1 formula, `V = TIMER (1.15)` |
| P3 | Stopwatch minimum to count | 15 min; stopping earlier records a "short run" with no XP and no collapse |
| P5 | Daily cap | at most **4** custom + stopwatch runs per game-day pay XP (preset gates are unaffected); extras are logged |
| P6 | Breach rule | unchanged from §8 (90 s cumulative background, 1 pause ≤ 5 min); applies to stopwatch too |
| P7 | Subjects | max **12** active, name ≤ 24 chars; label only, no effect on rewards; archive instead of delete |
| P8 | Weekly focus report | no new numbers: totals per subject over the game-week from cleared runs |
| P9 | Stopwatch pauses (user request 2026-11-01) | unlimited, no 5-minute cap: tap to pause, tap to continue. Paused time is never counted. Presets and custom runs keep one pause of ≤ 5 min (§8). The breach budget is unchanged (time away while running counts, time away while paused does not) |

Test vectors (add to §12 on approval): custom 30 min → D; stopwatch 14 min → no XP; stopwatch 120 min → B (not higher); 5th custom run in a game-day → 0 XP.

- Blocking other apps is the opt-in Focus Lock, §8.2 (an Accessibility Service the hunter switches on). Without it the breach budget alone applies. Screen Discipline, §6.7, only reads usage stats; it never blocks or interrupts an app.


### 8.2 Focus Lock

> **Decided by the user 2026-11-01.** No new reward numbers; this only changes what counts as leaving.

While a Focus Gate is RUNNING (preset, custom or stopwatch) and Focus Lock is on, any app that is not allowed is covered by a full-screen cover showing the time left (stopwatch: time counted), with [Back to focus] and [End session and open].

| # | Rule |
|---|---|
| L1 | Cover decision: no cover if the gate is not running, is paused, or a timed gate is past its planned end; no cover for AWAKEN, launchers, the keyboard, the default dialer and messages app, system UI, Android settings, package installers, emergency and telecom apps (never-block list, in `:engine`); no cover for apps the hunter ticked in Allowed apps. Anything else is covered. |
| L2 | **Lock replaces the breach budget.** Time away from AWAKEN while Focus Lock was on at the moment the app left, and its service is still connected on return, adds **0** to the 90 s breach budget (§8). Service down at either end: the 90 s rule applies as before. |
| L3 | **Fallback.** When a stretch away was not covered (lock off or service down), usage data on return that shows any Distracting app (Screen Time category, §6.7) came to the front during it collapses the run with reason DISTRACTING_APP and no reward. Fails closed: no usage access or an unreadable log leaves only the 90 s rule. Time inside the pause, and after a timed gate planned end, is not looked at. |
| L4 | [End session and open] collapses the run (reason ENDED_EARLY): no XP or gold, nothing deducted. |
| L5 | Privacy: the service listens to window-change events only, reads only the package name, retrieves no screen content, stores nothing, and uses no overlay permission (accessibility overlay window). |

Test vectors: lock on, 120 s away → still running; lock off, 91 s away → BREACH; lock off, 20 s away with a Distracting app opened → DISTRACTING_APP; same with no usage access → running; End session and open → ENDED_EARLY, ledger unchanged.
---

## 9. Economy (gold) — v1

### 9.1 Faucets

| Source | Gold |
|---|---|
| Quest completion | `round(questXp × 0.5)` (0 beyond daily cap) |
| Rank Trial cleared | 500 |
| Day cleared | 20 (awarded when the day is finalized at rollover) |

### 9.2 Sinks

| Item | Price | Limit | Purpose |
|---|---|---|---|
| Streak Shield | 300 | hold ≤ 2 | loss insurance |
| Extra reroll | 40 | 3/day | autonomy |

**No XP boosters for gold. No XP for gold. No real-money anything in v1.** The Store sells only items that change how you play. Themes, title frames, badges and the Relic Cache (with Loot Fragments) were removed on 2026-11-03; past purchases stay in the ledger (`sourceType = SHOP`, `CACHE_OPEN`, `ITEM`) and nothing writes new ones.


### 9.3 Ledger rules (non-negotiable)

- Balances (XP, stat XP, gold, items, fragments) = `SUM(delta)` over an **append-only ledger**.
- Every ledger row has `(sourceType, sourceId, kind)` → UNIQUE. Re-applying the same event is a no-op (idempotency).
- Each completion attempt has its own `completionId = questInstanceId#attemptN` (N from 1); quest ledger rows use it as `sourceId`, so re-completing after an undo gets fresh keys. Re-completion is priced normally, including daily caps.
- Undo = insert a reversing row (`sourceType = UNDO`, `sourceId = completionId`, same kind/stat/item). Undoing the same completion twice is a no-op. Never `UPDATE`/`DELETE` ledger rows.
- A cached `balances` table may exist for speed, updated in the **same transaction**, and must be rebuildable from the ledger (`Settings → Advanced → Rebuild from ledger` dev option + a test).

---

## 10. (removed)

Achievements and titles were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). Past achievement gold stays in the ledger (`sourceType = ACHIEVEMENT`); nothing writes new rows.

---

## 11. Copy voice and the Weekly Report

- **Voice**: plain, second person, sentence case. No story, no chapters, no daily messages, no game jargon.
- **Weekly Report** (Sunday): stats gained, best day, most-neglected stat, next-week recalibration. Shareable as an image card (§ DESIGN_SYSTEM share card).

Template quests stay hand-written and bundled. AI writes only Path content (§6.6), through the proxy. Never ship an API key in the APK.


---

## 12. Test vectors (must pass in `:engine` unit tests)

| # | Input | Expected |
|---|---|---|
| 1 | NORMAL, streak 0, SELF | `round(40×1.5×1.0×1.0×1)` = **60** |
| 2 | HARD, streak 12, HEALTH | `40×2.25×1.2×1.3` = 140.4 → **140** |
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
| 16 | SELF blueprint, `effort = EXTREME`, streak 0 | difficulty **NORMAL**, verification SELF → XP **60** (vector 1) |
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
| 33 | Gate cleared from a GATE-mode Path quest (D-Gate, NORMAL, streak 0) | exactly **one** reward: `round(40×1.5×1.0×1.15)` = **69 XP**, `sourceType = QUEST`; no separate gate reward |
| 34 | `suggestAdvance = true`, 3 Path quests completed since milestone start | advance **not** offered (needs ≥ 4) |
| 35 | SELF blueprint `estMinutes = 2` → clamped 5 / `estMinutes = 120` | `minDurationSec` = **75** / **900** (clamp(5×15, 60, 900) = 75; clamp(1800, 60, 900) = 900) |

**Screen Discipline (§6.7)**

| # | Input | Expected |
|---|---|---|
| 36 | DISCIPLINE quest, streak 0 / streak 30, 10,000 SELF XP already today | **78** / **117** XP; no cap applies |
| 37 | Instagram limit 30 min; used 30 min 59 s / 31 min | **held** / **crossed** |
| 38 | Limit 30 since 3 days ago; today the hunter sets 120 | today **30**, tomorrow **120** |
| 39 | 600 min each on a Productive app, a Neutral app and a Distracting app with no limit | nothing crossed; **no stake** (no quest) |
| 40 | DISCIPLINE quest for the same (hunter, day) twice; next day | same instance id / different id; never rerollable; not "generated" |

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
- **Rewards:** none. No XP, no gold.
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

### 14.6 (removed)

Monthly and yearly recaps were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). The weekly report, heatmap and insights cover the same ground.

### 14.7 (removed)

Identities ("Who are you becoming?") were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). The onboarding step, the Status line, the Settings row and the review line are gone; `hunter_profile.identities` was dropped in Room v27.

### 14.8 (removed)

The v1.1 achievements were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`).

### 14.9 (removed)

There is one vocabulary: plain words, no System voice, no presentation mode (see ENGINE_DECISIONS #109 and #156). The Lexicon survives only as the `lexString` / `lexPlural` entry point.

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
| 72 | Review for W submitted twice | one row; second is a no-op |
| 73 | Any review submitted | **no XP and no gold** row exists for any review |
| 74 | dayStart 4, default blocks; "now" = 03:30, 11:59, 12:00, 16:59, 17:00 | Evening (of the previous game-day) / Morning / Day / Day / Evening |
| 75 | Identity picks [reads, trains, ships] | pre-selected focus stats **INT, STR** |
| 76 | Identity picks [reads, ships] | pre-selected **INT** only |
| 80 | Focus Lock on, covered at stop and on return, 120 s away | gate still RUNNING, breach 0 s (GD 8.2 L2) |
| 81 | Focus Lock off, 91 s away | COLLAPSED, reason BREACH |
| 82 | Lock not covering, 20 s away, a Distracting app came to the front | COLLAPSED, reason DISTRACTING_APP, no ledger rows |
| 83 | Same as 82 with no usage access | still RUNNING (only the 90 s rule) |
| 84 | End session and open | COLLAPSED, reason ENDED_EARLY, ledger unchanged |

## 15. (removed)

The Reel Counter mascot (Lumi) was removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). Reels show as a plain count against the limit.

## 16. v1.2 feature batch (approved 2026-11-02)

> **Approved 2026-11-02** (user: "approved as proposed, go with your recommendations"). Every number tagged **`N<k>`** (list in §16.18) is approved as written, and open questions Q1–Q10 are resolved with the recommendations in `docs/BUILD_PLAN.md` §0. Source: `docs/NEW_IDEAS_TO_IMPLEMENT.md` (feature IDs A1, A2, A4, A5, B1, B2, B3, C1, C2, C3, C7, D1, D2, D3, D4, F3, F6).
>
> Ground rules for the whole section: on-device only, no AI, no network, no new money flows, no Accessibility Service (Focus Lock §8.2 is unchanged and stays the only one). §4.1 (BASE, D, S, V, crits), §4.3 caps, §7 streak, §9 prices and §9.3 ledger rules are **unchanged**. Nothing pays XP for opening the app, reflecting or self-reported abstinence; the only new gold comes from one-time achievements (§16.16) and the dungeon clear reward (§16.14). Anything rewarded for *not* doing something fails closed (§16.12, §16.13).
>
> **Already built, skipped here:** G3 consistency score = §14.2 (PRD F28, built). H1 Clean/System mode = §14.9 (PRD F34) was **superseded** by the user on 2026-10-27 (ENGINE_DECISIONS #109); it is not re-specified here until the user decides (open question Q1, §16.18).

### 16.1 Habit types (A1)

A habit has a **type** and a **frequency**. Existing habits migrate to `CHECK` + `DAYS_OF_WEEK` with their current mask, so nothing changes for them. Max **8** active habits is unchanged (§4.3, `N1` confirms it stays 8). Campaign and program habits (§6.8, §16.15) are always `CHECK` + `DAYS_OF_WEEK`.

| Type | What the hunter does | Completes when | Verification · difficulty |
|---|---|---|---|
| `CHECK` | taps the row (today's behaviour) | the tap | SELF · Easy/Normal (§4.3) |
| `QUANTITY` | taps +1 / −1 through the day ("8 glasses") | progress reaches the target, automatically, through `CompleteQuest` | SELF · Easy/Normal |
| `TIMED` | taps Start: a CUSTOM-mode focus session of the target length runs | the session clears | TIMER · **derived from the length**, not chosen |
| `NEGATIVE` | nothing while holding; taps **I slipped** on a slip; taps **Kept it today** in the evening | the "Kept it today" tap | SELF · Easy/Normal |

**QUANTITY.** Target **2–100** (`N2`; 1 would be a CHECK habit). Progress is stored on the day's quest instance (`quest_instance.progress`), clamped to 0…target. Partial progress pays nothing. Reaching the target completes the quest at once (SELF, V 1.00, SELF cap §4.3); −1 on a completed quantity habit is an ordinary Undo (§6.5: 10-minute window, same game-day) and puts progress back to target − 1. Unfinished at rollover → MISSED, like any habit.

**TIMED.** Target **15–180 minutes in 5-minute steps** (`N3`: §8.1 P1's range with the minimum raised to 15, because a run under 15 minutes pays nothing under P2). Difficulty is the §8.1 P2 tier of the target: 15–24 → Easy, 25–49 → Normal, 50–89 → Hard, ≥ 90 → Extreme (TIMER habits may exceed Normal, §4.3). Start runs a CUSTOM gate of exactly the target length with the habit's stat, linked to the habit's quest instance, and the §8 breach rule, §8.1 pause rule and §8.2 Focus Lock apply. **Paid once, as the habit quest** (`sourceType = QUEST`, V = TIMER), exactly like a GATE-mode Path quest (§8); the run is never also paid as a standalone gate and **does not use one of the four §8.1 P5 paid custom runs**. The TIMER daily cap (900 XP, §4.3) still applies. A collapsed run leaves the habit open; it may be started again the same game-day. A TIMED habit has no manual Complete.

**NEGATIVE.** Frequency must be `DAYS_OF_WEEK`. The row reads "Holding" all day. **I slipped** → the instance becomes MISSED at once (undoable until the game-day ends, which puts it back to ACTIVE). **Kept it today** is enabled only from the start of the Evening block (§14.5 "Day ends", default 17:00) until the game-day ends, and completes the quest (SELF, SELF cap). **Fail closed:** an instance nobody confirmed by the end of the game-day is MISSED and pays nothing (no XP for an unseen day; open question Q4 offers the alternative).

**Frequency `TIMES_PER_WEEK n`**, `n` = **1–6** (`N4`; 7 is "every day" and uses `DAYS_OF_WEEK`). Works with CHECK, QUANTITY and TIMED. Week = ISO week of game-days (Mon–Sun). While completions this week are below `n`, the habit gets one instance **every** game-day; once `n` is reached it gets none for the rest of the week. At most one completion per game-day. An unfinished flexible instance becomes **EXPIRED (not MISSED)** at rollover: a flexible habit never records a miss. Each completion counts as one directive item for clearing the day (§7.1).

### 16.2 Habit reminders and "skip today" (A2)

- **Reminder:** optional `reminder_time` per habit (whole minutes, local time). At most **2 habits may have a reminder** (`N5`); the editor refuses a third with "Two habits can have a reminder." A reminder fires only on a game-day the habit has an instance that is still ACTIVE or STARTED (not completed, skipped or missed), on its own `habit_reminder` channel. Delivery is inexact (WorkManager one-shot, like the directive reminder, ARCH §3.5) and may be a few minutes late. **Budget (open question Q2):** proposed as a separate allowance of at most 2 habit reminders per day, outside the existing 2-a-day directive/last-call cap, because the hunter chose each one.
- **Skip today:** **1 skip per habit per ISO week** (`N6`). A skipped instance gets the new status `SKIPPED`: it is not a miss, not an item for §7.1, never paid and hidden from the miss counts in §14 insights. Undo skip is allowed until the game-day ends and gives the skip back. A completed instance cannot be skipped. Flexible habits have no skip (every day is already optional). Negative habits may be skipped.

### 16.3 Pomodoro cycles (B1)

| Setting | Default | Range | |
|---|---|---|---|
| Focus round | 25 min | 10–60, 5-min steps | `N7` |
| Short break | 5 min | 1–15, 1-min steps | `N8` |
| Long break | 15 min | 5–30, 5-min steps | `N9` |
| Rounds per cycle | 4 | 2–6 | `N10` |

- A **Pomodoro session** = R focus rounds with a short break after each round except the last, then the long break, then the session ends (finish screen: "Another cycle?" starts a new session).
- **Each focus round is an ordinary CUSTOM gate run** of the focus length with the session's stat and subject: §8.1 P2 tier by minutes (25 → D, Normal), P4 fragment rule, **P5 cap counts every round** (four paid custom/stopwatch runs per game-day), §8 breach and pause rules, §8.2 Focus Lock. No new reward number.
- **Breaks** are not gates: no breach budget, the app may be left, Focus Lock does not cover. A break can be skipped. At the end of a break the soft chime plays (if sound is on) and a notification on the existing `gate` channel says the break is over.
- **Next round starts on a tap** (in the app, or the notification action that opens it), never by itself, because a round that started while the hunter was away would spend their breach budget (open question Q3).
- A collapsed round ends the session (`COLLAPSED`); rounds already cleared keep their rewards. Ending the session early from a break stores `ENDED`.

### 16.4 Daily focus goal ring and focus streak (B2)

```
focusMinutes(d) = Σ counted minutes of gate runs that started on game-day d and ended CLEARED or LOGGED
                  (preset/custom/Pomodoro/TIMED-habit/Path GATE: planned minutes; stopwatch: counted time floored to whole minutes)
goal(d)         = the focus goal in effect on d (versioned; an edit applies from today, earlier days keep theirs)
met(d)          = focusMinutes(d) ≥ goal(d)
```

- Goal default **120 min** (`N11`), range **15–600 in 15-minute steps** (`N12`). Collapsed and abandoned runs never count. LOGGED runs (§8.1 short or over-cap runs) count, because this is time focused, not a reward.
- **Focus streak** = consecutive game-days with `met`, counting back from today if today is met, else from yesterday. A finalized REST or RECOVERY day (§7.2) is skipped over: it neither extends nor breaks the focus streak. Any other day that is not met ends it. Derived, never stored (invariant 7).
- **No XP.** No gold either.

### 16.5 (removed)

The gate crystal and its gallery were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). A running session shows the plain progress dial.

### 16.6 Progression hints, double progression (C1)

`ProgressionRules.suggest(lastSession, range, increment)` in `:engine` `body/`, over the exercise's **last finished session**. A hint on set 1, never forced, no reward.

```
working sets = done sets at the session's heaviest weight w (weight > 0)
if every working set has reps ≥ range.max      → suggest weight w + increment, reps = range.min
else                                            → suggest weight w, reps per set = min(previous reps + 1, range.max)
unweighted exercise (all done sets weight 0):
   every set ≥ range.max → hint "Add weight or a harder variation" (no number) ; else reps = min(previous + 1, range.max)
no finished session of this exercise            → no hint
```

- Default rep range **8–12** (`N14`); per-exercise override 1–30 reps, min ≤ max.
- Increment **+2.5 kg** upper body, **+5 kg** lower body (`N15`); per-exercise override 0.5–20 kg in 0.5 steps. Lower body = any primary muscle in {QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, ADDUCTORS, ABDUCTORS} (`Muscle.isLowerBody`).

### 16.7 Personal records (C2)

Per exercise, compared with every **earlier** finished session of it (§13.2 data):

| Record | Rule |
|---|---|
| Heaviest weight | max weight of a done set (reps ≥ 1) |
| Best e1RM | §13.2 (weight for a single; Epley for 2–12 reps; nothing above 12 or unweighted) |
| Most reps at a weight | for each exact weight, max reps of a done set |
| Best session volume | max §13.2 volume of the exercise in one session |

- A set is a PR when it beats the record held **before this session** (ties are not PRs). An exercise's first session sets records but flags no PR. Ticking a set done that is a PR shows a small celebration (haptic + "New record" chip, reduce-motion respected); the finished-workout summary lists the session's PRs (one line per record type per exercise).
- **Rewards:** no XP and no gold (weights are self-logged). PR events are one per record type per exercise per session.

### 16.8 Plate calculator (C3)

- Per side = `(target − bar) / 2`, greedy over owned plate sizes from heaviest to lightest, each size usable as many times as needed (`N16`: unlimited count of each owned size). If the exact load can't be built, show the heaviest buildable load below the target and the shortfall. Target below the bar → "Below the bar's 20 kg".
- Defaults (`N17`): **kg** bar 20, plates 25, 20, 15, 10, 5, 2.5, 1.25. **lb** bar 45, plates 45, 35, 25, 10, 5, 2.5. The kg/lb switch belongs to the calculator only; workouts stay in kg (open question Q7). Bar and owned plates are editable in Gym settings.

### 16.9 Exercise history and charts (C7)

- Per exercise: every finished session (date, sets "80 kg × 5, 5, 4"), newest first, and three series: **top weight** (heaviest done set), **best e1RM** (§13.2) and **volume** (§13.2), one point per session.
- Ranges **Month / 3 months / Year / All** (`N18`; the ideas file said Week/Month/Year, but a week holds 1–3 sessions, too few for a line). Year and All plot the best value per ISO week. Empty state below 2 sessions. ChartKit `TrendLine` with a content description of first, last and best values.

### 16.10 Pickups, first and last use (D1)

From usage events (`UsageStatsManager.queryEvents`, existing usage access), shown on the Screen time screen. No reward; display only.
- **Pickups today** = `KEYGUARD_HIDDEN` events in the game-day so far. If the window has no keyguard event of any kind but has `SCREEN_INTERACTIVE` events (a phone without a lock screen), count those and label the number "Screen-ons".
- **First pickup** = the first such event in today's game-day.
- **Last use (yesterday)** = the last foreground app event of the previous game-day (today is not over).
- **Longest stretch without the phone** = the longest gap today between a screen-off (`SCREEN_NON_INTERACTIVE`) and the next pickup, counting only from the first pickup to now (the night is not counted).
- `NoAccess` / `Unavailable` (ENGINE_DECISIONS #111) → the section shows the usage-access prompt or "No data yet", never zeros.

### 16.11 Daily step goal ring (D2)

- Goal default **8,000** (`N19`), range **1,000–30,000 in 500 steps** (`N20`). Stored on `hunter_profile` (exported). Steps = the trusted steps already shown (§6.2 trust; phone step counter fallback, ENGINE_DECISIONS #110). Ring = `min(steps / goal, 1)`; at or over the goal the label reads "Goal reached". **No reward**; HEALTH_STEPS quests are unchanged.

### 16.12 Verified phone-down bedtime (D3)

Opt-in "Phone-down nights". Needs usage access.
- **Window:** bedtime **23:00** → wake **06:30** by default (`N21`); bedtime 20:00–02:00 and wake 04:00–11:00 in 15-minute steps; the window is 5–12 hours long; wake must be later than `dayStartHour`, so the window ends inside one game-day (the **wake day**).
- **The BEDTIME quest** belongs to the wake day: stat **VIT**, **Normal**, verification `DEVICE_USAGE` (source USAGE, V 1.30, uncapped, like DISCIPLINE §6.7): **78 XP** at streak 0. It can crit, can't be rerolled, isn't "generated" (§6.4) and counts as one directive item. Shown as a card (evening: "Phone down tonight 23:00–06:30"; morning: the result), never as a quest row.
- **Kept** if the total foreground time of non-allowed apps inside the window is **≤ 5 minutes** (`N22`, grace for an alarm, a call or a glance). **Allowed apps:** AWAKEN, the default dialer and telecom apps, and the clock/alarm app(s) (handlers of `AlarmClock.ACTION_SHOW_ALARMS`). Everything else, including the launcher, counts.
- **Settled** after wake time (app foreground or the 15-minute `ScreenTimeWorker`): kept → completed through `CompleteQuestUseCase.completeVerified(id, USAGE)`; over the grace → EXPIRED ("Phone used at night"), nothing deducted.
- **Fail closed:** usage `NoAccess` or `Unavailable` at settlement → EXPIRED unpaid. No usage access when the directive is generated → no BEDTIME quest that day.
- **Edits apply from the next night** (versioned like `app_rule`, §6.7), so tonight's window can't be moved at 23:59.
- **Wind-down (optional):** a [Wind down] button on the evening card from 30 minutes before bedtime opens a 2-minute breathing guide: inhale 4 s, exhale 6 s, 12 cycles (`N23`). No reward, no notification.

### 16.13 Quit counters (D4)

"Days clean" for something the hunter wants to stop. Presets: Smoking, Porn, Junk food, Doomscrolling, Alcohol, Custom (name ≤ 24 chars). Max **6** active counters (`N24`). Wording is supportive, never shaming; no medical or health-recovery claims (copy rules in DESIGN_SYSTEM §9.2).

- **Time-based counters** (every preset but Doomscrolling): `start` (may be in the past, "I stopped on …") and relapses (`at`, optional trigger chip, optional note ≤ 140 chars). Current run = now − (last relapse, else start). Best run = the longest run between consecutive events (start, relapses, now), **kept forever**: a relapse never deletes history. Shown as days (whole 24-hour periods) and hours under one day.
- **Money saved** = cost per day × whole days of the current run; **time reclaimed** = minutes per day × whole days. Both optional, entered by the hunter, display only (no real money is involved).
- **Milestones** shown: 1, 3, 7, 14, 30, 90, 180, 365 days (`N25`).
- **Doomscrolling (auto, fails closed):** day-based. A finalized game-day is **clean** when Screen Time reads `Tracked` and total Distracting-app minutes ≤ **60** (`N26`); **over** → a relapse is logged automatically for that day ("74 min on distracting apps"); `NoAccess`/`Unavailable` → the day is **paused** (neither counts nor breaks). Run = consecutive clean days.
- **Rewards:** **no XP** for any counter, ever. No gold either. Milestones are display only and count the **in-app run**: time from `max(start, created_at)`, so a back-dated start never counts; for Doomscrolling, clean days.
- **Privacy:** counters and relapse notes never leave the phone except in the hunter's own export. App lock is open question Q5.

### 16.14 (removed)

Dungeons were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`). A dungeon that was running keeps its spent fee; its open stage quests expire like any other quest.

### 16.15 Programs: 30 / 66 / 75 days (F6)

Long, fixed daily rule sets with a day counter. Original names only.

| Program | Days | Daily rules (each becomes a read-only program habit) |
|---|---|---|
| **Steady 30** | 30 | Walk 7,000 steps (HEALTH_STEPS; SELF "Walk for 30 minutes" when Health is off) · Read 10 pages (SELF, INT) · 15-minute focus session (TIMED, INT) |
| **Groundwork 66** | 66 | the three rules of Steady 30 · Plan tomorrow in 2 minutes (SELF, WIL) |
| **Ironclad 75** | 75 | 45-minute workout (HEALTH_EXERCISE 45 min; SELF "Train for 45 minutes" when Health is off) · Read 10 pages (SELF, INT) · 10,000 steps (HEALTH_STEPS) · 25-minute focus session (TIMED, INT) · Phone-down night (§16.12, only if Phone-down nights is on) |

(`N30`: the three programs, their lengths, rules and targets.) Rules are priced like habits (§4.1, SELF cap); there is no diet, weight, fasting or substance rule.
- **Slots:** each rule is a habit counting toward the 8 active habits; a program can't start without enough free slots. **One long arc at a time:** a program and a 45-day Campaign can't run together (open question Q8).
- **Day k** = game-days since the (re)start + 1. A day is **kept** when every program rule instance of that game-day was completed; a finalized REST/RECOVERY day is **rest** (not broken). Otherwise it is **broken**.
- **After a broken day**, the next app open asks: **Restart** (a new run, day 1 = today) or **Continue** (the day stays marked broken, the counter goes on). No answer by the next rollover = Continue. Nothing is ever deducted.
- **End:** after the last day is finalized: no broken day in the run → **COMPLETED**; otherwise **FINISHED** (shown with its marks). Program habits are archived either way. Abandon anytime: habits archived, nothing deducted.

### 16.16 (removed)

The v1.2 achievements were removed on 2026-11-03 (see `docs/DECLUTTER_PLAN.md`).

### 16.17 Test vectors (continuing §12 / §14.10 numbering)

**Habits (§16.1–16.2)**

| # | Input | Expected |
|---|---|---|
| 85 | QUANTITY target 8, Normal, streak 0; progress 7 → +1 | at 7: ACTIVE, 0 XP; at 8: COMPLETED, **60 XP, 30 gold** (SELF) |
| 86 | QUANTITY target 101 / 1 | rejected / rejected (2–100) |
| 87 | TIMED 30 min, streak 0, run cleared | difficulty **Normal**; `round(40×1.5×1.0×1.15)` = **69 XP**, one QUEST reward, no GATE reward, P5 count unchanged |
| 88 | TIMED 14 min / 95 min | rejected / difficulty **Extreme**, `40×3.0×1.15` = **138 XP** at streak 0 |
| 89 | TIMED habit run collapses | habit instance still ACTIVE, no ledger rows; a new run may start the same game-day |
| 90 | NEGATIVE, "Day ends" 17:00: Kept it at 16:59 / at 21:00 / never | refused / COMPLETED 60 XP / MISSED at rollover, 0 XP |
| 91 | NEGATIVE: I slipped at 14:00, undo at 15:00 | MISSED → back to ACTIVE |
| 92 | 3×/week CHECK: Mon done, Tue done, Wed nothing, Thu done | Wed **EXPIRED** (not MISSED); Fri–Sun **no instance**; next Monday an instance again |
| 93 | 2 habits have reminders; set a 3rd | refused ("Two habits can have a reminder.") |
| 94 | Habit with a reminder completed before the reminder time | no notification |
| 95 | Skip Tue (1 per week) then skip Thu; undo Tue's skip on Tue | Tue SKIPPED, not missed, not an item; Thu refused; after undo Tue ACTIVE and Thu's skip allowed |

**Focus (§16.3–16.5)**

| # | Input | Expected |
|---|---|---|
| 98 | Cleared D preset (25), custom 40, stopwatch 47 min 30 s, collapsed C | focusMinutes **112**; goal 120 → not met; a cleared E (15) later → **127**, met |
| 99 | Goal met Mon, Tue; Wed finalized REST, no focus; Thu met | focus streak **3** |
| 100 | Thu: goal changed 120 → 60 at 15:00 with 70 min done | Thu **met**; Wed keeps goal 120 |

**Gym (§16.6–16.9)**

| # | Input | Expected |
|---|---|---|
| 103 | Bench (upper), range 8–12, last 50 kg × 12, 12, 12 | **52.5 kg × 8** |
| 104 | Bench, last 50 kg × 12, 12, 10 | **50 kg**, reps **12, 12, 11** |
| 105 | Squat (lower), last 100 kg × 12, 12, 12 | **105 kg × 8** |
| 106 | Pull-up, unweighted, last 12, 12, 12 | hint "Add weight or a harder variation", no number |
| 107 | Exercise never finished before | no hint; no PR flags in this session |
| 108 | History 100 × 3 (e1RM 110), 80 × 8; today 102.5 × 1 and 80 × 9 | heaviest-weight PR (102.5); e1RM **not** a PR (102.5 < 110); reps-at-80 PR (9) |
| 109 | Ties: today 100 × 3 again | **no** PR |
| 111 | Plates kg, bar 20: 100 / 62.5 / 61 / 15 | 25 + 15 per side / 20 + 1.25 / **20** per side (60 kg, 1 kg short) / "Below the bar's 20 kg" |
| 112 | Plates lb, bar 45: 225 / 135 | 45 + 45 per side / 45 |

**Screen and health (§16.10–16.12)**

| # | Input | Expected |
|---|---|---|
| 113 | Today: unlocks 07:10, 08:00, 12:30; screen off 08:05; yesterday's last app event 23:48; now 13:00 | pickups **3**, first **07:10**, last use **23:48**, longest stretch **4 h 25 min** |
| 114 | No keyguard events, 5 `SCREEN_INTERACTIVE` | **5**, labelled "Screen-ons" |
| 115 | Usage `NoAccess` | no numbers; usage-access prompt |
| 116 | Step goal 8,000; steps 6,200 / 9,100 | ring 0.775, "6,200 / 8,000" / full ring, "Goal reached"; no ledger rows |
| 117 | Window 23:00–06:30, Instagram 3 min at 01:00, streak 0 | kept → BEDTIME completed on the wake day, **78 XP** |
| 118 | Same with Instagram 6 min | EXPIRED, 0 XP, nothing deducted |
| 119 | Clock app 12 min at 06:00, nothing else | kept |
| 120 | Usage `Unavailable` at settlement | EXPIRED unpaid |
| 121 | At 22:00 the bedtime is changed to 00:00 | tonight 23:00; from tomorrow night 00:00 |

**Quit counters (§16.13)**

| # | Input | Expected |
|---|---|---|
| 122 | Start 2026-11-01 08:00; relapse 2026-11-05 20:00; now 2026-11-08 09:00 | current run **2 days** (2 d 13 h); best run **4 days** (4 d 12 h) |
| 124 | Cost 150 per day, current run 2 d 13 h | money saved **300** |
| 125 | Doomscrolling, limit 60: Mon 40, Tue NoAccess, Wed 55, Thu 75 | run Mon–Wed = **2** clean days (Tue paused); Thu auto-relapse, run 0, best 2 |

**Dungeons and programs (§16.14–16.15)**

| # | Input | Expected |
|---|---|---|
| 131 | Steady 30 from Mon; day 5 broken; next open Restart / Continue | day 1 = that day / day 6 next, day 5 marked broken |
| 132 | Last day finalized with 0 broken days / with 1 continued break | COMPLETED / FINISHED |
| 133 | Break question unanswered by the next rollover | Continue recorded |
| 134 | A REST day inside a program | rest (not broken); the counter advances |

### 16.18 Proposed numbers and open questions

**Approved numbers (2026-11-02):** `N1` max 8 habits (unchanged) · `N2` quantity target 2–100 · `N3` timed habit 15–180 min, 5-min steps · `N4` flexible 1–6 per week · `N5` max 2 habits with a reminder · `N6` 1 skip per habit per ISO week · `N7`–`N10` Pomodoro 25 (10–60) / 5 (1–15) / 15 (5–30) / 4 rounds (2–6) · `N11` focus goal 120 min · `N12` focus goal 15–600 in 15s · `N13` crystal stages 0/20/40/60/80/100 %, stopwatch full at 90 min · `N14` rep range 8–12 (override 1–30) · `N15` increments +2.5 / +5 kg (override 0.5–20) · `N16` unlimited plates of each owned size · `N17` bars 20 kg / 45 lb and plate sets · `N18` history ranges Month / 3 months / Year / All · `N19` step goal 8,000 · `N20` step goal 1,000–30,000 in 500s · `N21` bedtime 23:00 → 06:30, ranges, 5–12 h window · `N22` night grace 5 min · `N23` breathing 4 s / 6 s × 12 · `N24` max 6 quit counters · `N25` milestones 1, 3, 7, 14, 30, 90, 180, 365 · `N26` doomscrolling clean ≤ 60 min · `N27` dungeons unlock at level 10 · `N28` dungeon 3/5/7 days for 100/200/300 gold · `N29` clear reward 1.5 × fee + 1 fragment · `N30` program content · `N31` the 15 achievements.

**Resolved questions (2026-11-02, the recommendations):** Q1 H1 stays dropped · Q2 habit reminders are a separate allowance (max 2) · Q3 next Pomodoro round on a tap · Q4 negative habits need the evening tap · Q5 D4 ships without app lock, H3 to the backlog · Q6 PR achievements pay gold (one-time) · Q7 lb only in the plate calculator · Q8 program and campaign are mutually exclusive · Q9 a FINISHED program earns nothing · Q10 dungeon reward 1.5 × fee. Originally asked: Q1 H1 System mode back? · Q2 habit reminders outside the 2/day cap? · Q3 Pomodoro next round on tap (vs automatic)? · Q4 negative habits need the evening "Kept it" tap (vs auto-complete)? · Q5 D4 without app lock (H3 not in scope)? · Q6 PR achievements pay gold? · Q7 lb only in the plate calculator? · Q8 program and campaign mutually exclusive? · Q9 a FINISHED program earns nothing? · Q10 dungeon reward 1.5 × fee? Details and recommendations in `docs/BUILD_PLAN.md` §0.

---

## 17. Onboarding answers (approved 2026-11-05)

Source: `docs/onboarding/PLAN.md` (§4, §5). The onboarding questions set starting values only. They award no XP and no gold, and write no ledger rows. Where a rule below names an existing number (`N11`, `N14`, `N19`, `N21`) it reuses it unchanged.

| ID | Rule |
|---|---|
| **OB1 Focus stats** | Strength → STR · Fitness → STR · Focus → WIL · Discipline → WIL · Less screen time → WIL · Sleep → VIT · Learning → INT. The two stats with the most picks; ties go to the earliest selection. Fewer than two: fill from STR, INT, WIL, VIT, CHA in that order, skipping any already chosen. Step skipped: STR + INT. |
| **OB2 Baseline tiers** | STR: Beginner Easy, Intermediate Normal, Advanced Hard. VIT from the sleep window: under 6 h Easy, 6 to under 7 h Normal, 7 h or more Hard. INT Normal. WIL and CHA Easy. Pace shifts every stat: Steady −1, Balanced 0, Push hard +1, clamped Easy–Hard. Skipped fitness or sleep: that stat Normal. |
| **OB3 Step goal** | Beginner 6,000 · Intermediate 8,000 (`N19`) · Advanced 10,000; "Sitting all day" subtracts 2,000. Skipped: 8,000. Always a valid `N20` value. |
| **OB4 Focus goal** | Steady 60 · Balanced 120 (`N11`) · Push hard 180 min; "Procrastination" halves it (30 / 60 / 90). Skipped: 120. Always a valid `N12` value. |
| **OB5 App limit** | Daily screen time under 2 h: 30 min (§6.7) · 2–4 h: 45 · 4–6 h: 60 · 6 h+: 90. Applied to each seeded Distracting app. Written only when usage access is granted. |
| **OB6 Gym rep range** | Beginner 10–15 · Intermediate 8–12 (`N14`) · Advanced 6–10. Per-exercise overrides still win. |
| **OB7 BMI** | `kg / m²`, one decimal. Scale marks at 18.5, 25 and 30. The scale shows only at age 20 or more; under 20, or with no age, only the number and a neutral line. Labels: "Below the usual range" (< 18.5), "Within the usual range" (18.5 to < 25), "Above the usual range" (25 and over). Information only. |
| **OB8 Equipment** | Bodyweight → none · Dumbbells → `dumbbells` · Gym → `gym_access`, `dumbbells`, `kettlebell`, `pullup_bar`, `resistance_band`. |
| **OB9 Injury tags** | Shoulders, Wrist → `upper` · Back → `impact` · Knee, Hip → `legs`, `impact`. Any region turns Injury Mode on. |
| **OB10 Picker starts** | Age 25 · Height 170 cm · Weight 70 kg · Bedtime 23:00 and wake 06:30 (`N21`), the same for every gender. Units default to ft/lb when the locale's country is US, else cm/kg. |
| **OB11 Units** | 1 in = 2.54 cm; 1 lb = 0.45359237 kg. cm and kg are stored; display rounds. Toggling units back and forth never changes the stored value. Ranges: age 13–100, height 120–230 cm, weight 30–300 kg (the Diet ranges). Steps: 1 cm / 1 in; 0.5 kg / 1 lb. |
| **OB12 Body map regions** | Shoulders → `SHOULDERS` · Back → `LOWER_BACK`, `MIDDLE_BACK` · Wrist → `FOREARMS` · Knee → `QUADRICEPS` · Hip → `GLUTES`. Display only. |
| **OB13 Stage → Diet goal** | Body shape stages 1–2 → gain · 3 → maintain · 4–5 → lose. A target weight (OB17) overrides it: below the current weight is lose, above is gain. Prefills the Diet form only. |
| **OB14 Reminder** | Reminder toggle on: `reminder_time` 07:00 (the existing default). |
| **OB15 Plan ring** | Runs 3,000 ms in total, split evenly across the lines shown. |
| **OB16 Summary quest line** | Quest count and titles come from a dry run of the directive generator with the new hunter's id. |
| **OB17 Target guard** | Target date at least 4 weeks away. Target weight inside the 30–300 kg range and at or above BMI 18.5 for the entered height. Not offered under 18. |
| **OB18 Goal chart** | A monotone curve between today's weight and the target, ending at the target date. Labelled "Estimate based on your target". No projection beyond the date. Never shown under 18. |

### 17.1 Test vectors (continuing §16.17)

| # | Input | Expected |
|---|---|---|
| 135 | Every tick of each ruler (cm, in, kg, lb) snapped, read back and snapped again | the same tick; no drift. Toggling units never rewrites the stored value |
| 136 | 175 cm = 68.9 in; 80 kg = 176.4 lb | display rounds to 69 in (5′9″) and 176 lb |
| 137 | BMI at 70 kg, 170 cm | 24.2, "Within the usual range" |
| 138 | BMI of 18.4 / 18.5 / 24.9 / 25.0 / 29.9 / 30.0 | below / within / within / above / above / above |
| 139 | Age 19 or no age | BMI value and neutral line, no scale |
| 140 | Age 20 | scale shown |
| 141 | Locale US / any other | ft and lb / cm and kg |
| 142 | Sleep wheels: bedtime 19:45 or 02:15, wake 03:45 or 11:15, or a window under 5 h or over 12 h | all invalid (§16.12) |
