# AWAKEN — Game Design Spec (v1.0 MVP)

> Source of truth for every number in the game. If code disagrees with this file, the code is wrong.
> All formulas live in the pure-Kotlin `:engine` module and are covered by the test vectors in §12.
> AI never decides a number in this file. Ever.

---

## 1. Design pillars (in priority order)

1. **Effort is the only currency.** XP comes from real actions, never from taps, logins or purchases.
2. **Verified beats claimed.** Actions the phone can observe (Health Connect, in-app focus timer) earn more than self-reported ones. This is our moat — every competitor is honor-system only.
3. **Hard, never cruel.** Failure creates a *comeback quest*, not a wipe. No XP loss, no level loss, no public shaming. (Habitica's biggest complaint is its punishment death-spiral.)
4. **Every number is explainable.** The completion sheet shows the formula breakdown: `60 base × 1.5 Normal × 1.23 streak × 1.30 verified = 144 XP`.
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
seed = fnv1a64( questInstanceId )         // instance id is a UUID created when the quest is generated
crit = (seed mod 10_000) < 800            // 8.00 %
```

- Because the seed is the instance id (fixed at generation), un-completing and re-completing cannot re-roll a crit.
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
| **Side** | 3 | Generated | Normal, weighted by focus (2×) and neglect (stat untouched for 3+ days gets 1.5×) |
| **Habit** | user-defined, max 8 | User | Custom recurring quests (days of week) |
| **Penalty** | 0–1 | System | Appears after a failed day (§7.3) |
| **Trial** | 1 while active | System | Rank Trial stage (§5) |
| **Focus Gate** | unlimited | User-started | Timer dungeon (§8). Counts as a quest completion. |

**Daily Directive** = Main + Side + Habits scheduled today. Generated at day start (or on first app open of the day — whichever comes first; idempotent per `(hunterId, gameDate)`).

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
  main  = weightedPick(pool where stat == focusStats[gameDay % 2], difficulty = HARD adjusted by tier)
  sides = 3 × weightedPick(pool − main.stat-duplicates-where-possible, weights = focus 2.0, neglected 1.5, else 1.0)
  title = template.titles[rng]   ;   target = template.targets[difficulty]
```

Each instance gets a fresh UUID → used as crit seed and ledger idempotency key.

**Reroll**: 1 free reroll per day for one side quest; further rerolls cost 40 gold (max 3/day). Rerolling cannot target the main quest.

### 6.4 Adaptive difficulty (per stat)

Every Monday (or first open after), per stat over the previous 7 days of *generated* quests in that stat:

```
completionRate ≥ 0.85 and ≥ 4 quests   → tier +1   (max EXTREME)
completionRate ≤ 0.40 and ≥ 4 quests   → tier −1   (min EASY)
else                                   → unchanged
```

Side quests use the stat's tier; the main quest uses `tier + 1` (capped). Shown to the user as a System message: *"The System has recalibrated. STR trials will be harder."*

### 6.5 Completion rules

- `minDurationSec`: a SELF quest cannot be completed sooner than N seconds after it was *started* (tap "Begin"). If the user never tapped Begin, they can still complete, but `V` stays 1.0 and it counts toward the self cap. Keep it low-friction; this is a soft speed bump, not a jail.
- Completions are **undoable for 10 minutes** (writes a reversing ledger entry with the same idempotency group; never deletes).
- After the day ends, unfinished generated quests become `EXPIRED`. Habits become `MISSED`.

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
- Do NOT use AccessibilityService or usage-stats app blocking in v1 (Play policy review risk + permission friction). The breach budget is enough.

---

## 9. Economy (gold) — v1

### 9.1 Faucets

| Source | Gold |
|---|---|
| Quest completion | `round(questXp × 0.5)` (0 beyond daily cap) |
| Rank Trial cleared | 500 |
| Achievement unlocked | per achievement (10–200) |
| Day cleared | 20 |

### 9.2 Sinks

| Item | Price | Limit | Purpose |
|---|---|---|---|
| Streak Shield | 300 | hold ≤ 2 | loss insurance |
| Extra reroll | 40 | 3/day | autonomy |
| Theme: *Crimson Gate* | 1,500 | once | identity |
| Theme: *Abyssal Violet* | 1,500 | once | identity |
| Theme: *Sovereign Gold* (requires rank S) | 3,000 | once | status |
| Title frames (×4) | 600–1,200 | once | identity |
| Hunter sigils (×8 icon badges) | 400 | once | identity |

**No XP boosters for gold. No XP for gold. No real-money anything in v1.** A healthy economy needs sinks that don't touch progression; cosmetics + insurance do that.

**Loot Fragments** (from crits and Focus Gate clears): 10 fragments → open a **Relic Cache** containing a random cosmetic not yet owned (sigils/frames only). Earned only, never purchasable — keeps us clear of loot-box disclosure concerns.

### 9.3 Ledger rules (non-negotiable)

- Balances (XP, stat XP, gold, items, fragments) = `SUM(delta)` over an **append-only ledger**.
- Every ledger row has `(sourceType, sourceId, kind)` → UNIQUE. Re-applying the same event is a no-op (idempotency).
- Undo = insert a reversing row (`sourceType = UNDO`, `sourceId = original row id`). Never `UPDATE`/`DELETE` ledger rows.
- A cached `balances` table may exist for speed, updated in the **same transaction**, and must be rebuildable from the ledger (`Settings → Advanced → Rebuild from ledger` dev option + a test).

---

## 10. Achievements & titles (launch set: 30)

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

AI-written flavor (LLM) is **v1.2+ only**, behind a server you control. Never ship an API key in the APK.

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

> Note on vector 7: the level table in §4.4 lists the *step into* each level (i.e. `xpToNext(L−1)`), so "L10 step 504" = `xpToNext(9)`. Vector 7 is `xpToNext(10)` = 578. Tests are authoritative.
