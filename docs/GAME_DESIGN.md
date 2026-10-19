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

**Daily Directive** = Main + Path quests scheduled today + Side + Habits scheduled today. Generated at day start (or on first app open of the day — whichever comes first; idempotent per `(hunterId, gameDate)`).

### 6.2 Template library (deterministic generation)

Quests are generated from a **bundled JSON template library** (`assets/quests/templates.json`), not from an LLM. Target ≥ 150 templates at launch, ≥ 25 per stat, across 4 difficulties.

```json
{
  "id": "str_pushups",
  "stat": "STR",
  "secondaryStat": "WIL",
  "titles": ["Iron Arms", "Ground Assault", "The Hundred"],
  "description": "Complete {target} push-ups. Sets of any size.",
  "unit": "reps",
  "targets": { "EASY": 20, "NORMAL": 40, "HARD": 80, "EXTREME": 120 },
  "verification": "SELF",
  "equipment": [],
  "tags": ["bodyweight", "indoor", "no_impact_false"],
  "minDurationSec": 120
}
```

Verification options in templates: `SELF`, `TIMER` (completed by the Focus Gate engine), `HEALTH_STEPS`, `HEALTH_EXERCISE` (any exercise session ≥ target minutes), `HEALTH_SLEEP` (sleep session ≥ target hours).

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
- Do NOT use AccessibilityService or usage-stats app blocking in v1 (Play policy review risk + permission friction). The breach budget is enough.

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

## 10. Achievements & titles (launch set: 32)

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

> Note on vector 7: the level table in §4.4 lists the *step into* each level (i.e. `xpToNext(L−1)`), so "L10 step 504" = `xpToNext(9)`. Vector 7 is `xpToNext(10)` = 578. Tests are authoritative.
