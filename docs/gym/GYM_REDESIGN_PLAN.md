# Gym redesign — plan (Part A)

> Status: **APPROVED 2026-11-09** ("approve all, next"); now in GD §18. Built batch by batch (Batch 1: engine, assets, schema 29). Ledger undo uses `UNDO` rows, not a separate `GYM_WORKOUT_UNDO` type. Every number tagged **`G<n>`** is a proposal that needs your
> approval before Batch 1 (list in §8). Approved numbers then move into `docs/GAME_DESIGN.md` (new §18) and decisions
> into `docs/ENGINE_DECISIONS.md`; until then GD wins.
>
> Reference screenshots (`docs/reference/gym/`, a coached gym app) are used for **interaction patterns only**: the day
> strip, the one-exercise-at-a-time card, the full-screen rest with a next-up card, the semicircle level gauge. No text,
> photos, data, names or "better than X% of members" claims are copied. The only figure is our own `BodyMap`.

---

## 0. Audit of the current Gym tab

### 0.1 What exists (keep and reuse)

| Piece | Where | Reuse in the redesign |
|---|---|---|
| Exercise library, 876 entries (free-exercise-db, Unlicense) with `category`, `level` (beginner 525 / intermediate 294 / expert 57), `mechanic` (compound 491 / isolation 298 / none 87), `equipment`, `primary`, `secondary`, `instructions` | `assets/gym/exercises.json`, `ExerciseCatalog`, `CatalogSearch` | Plans reference its ids. `level` + `mechanic` + `primary` drive the Replace sheet (§2.5). |
| Custom exercises | `custom_exercise`, `GymTracker.createCustomExercise` | Unchanged. |
| Session model: `workout` (one open at a time, `rest_ends_at` stored) and `workout_set` (name and muscles snapshotted) | `BodyEntities.kt`, `GymDao`, `GymTracker` | Extended (§5.1), not replaced. |
| Routines (`routine`, `routine_exercise` with set count and rest) | same | Become **My routines** with weekdays, targets and supersets. |
| e1RM (Epley, 2–12 reps), volume, weekly muscle load and bands | `engine/body/Training.kt` (GD §13.2) | Unchanged; benchmarks and XP call `TrainingMath.estimatedOneRepMax`. |
| Progression hints (double progression) | `engine/body/ProgressionRules.kt` (GD §16.6) | Feeds the prefilled target in coached mode. |
| Personal records (4 types), PR achievements pr1/pr25/pr100 (gold only) | `engine/body/RecordRules.kt` (GD §16.7, ED #128) | Unchanged; the PR badge and celebration reuse `RecordRules.setFlags`. |
| Plate calculator | `engine/body/PlateMath.kt` (GD §16.8) | Moves into the ⋯ menu; its owned plates drive the stepper increment (§2.2). |
| Exercise history and trend series | `engine/body/ExerciseHistory.kt` (GD §16.9), `GymProgressSheets.kt` | Becomes the history chart on Exercise detail. |
| Rest rules (15 s steps, 15–600 s, stored end, silent after 10 s) | `engine/body/RestRules.kt` (GD §13.2) | Extended with MIN_REST (§2.3). |
| End-of-rest job | `core/work/RestEndWorker.kt` (WorkManager one-shot) | Replaced for the screen-off case (§2.4). |
| Body map | `BodyMap`, `BodyGeometry` (designsystem) | Muscle highlight on the coached card and Exercise detail. |
| Gym settings: bars and owned plates (kg and lb), calculator unit, default rep range | `gym_settings` | Extended (§1.5). |

### 0.2 Problems found (with causes)

| # | Problem | Cause in code |
|---|---|---|
| P1 | Spreadsheet logging, identical rows, tiny ticks | `GymScreen.ExerciseCard` / `SetRow`: Set · Previous · kg · Reps · `RoundCheck` per row. |
| P2 | **"Previous" always empty** | Reading the code, `GymDao.previousSets` (newest *finished* workout containing the exercise) and its wiring (`GymViewModel.init` → `flow.previous` → `SetRow`) look correct, and `GymTrackerTest.theNextSessionIsPrefilledFromThePreviousOne` passes, so the gap is in how sessions *end*. There are three concrete defects, any of which leaves no finished history: **(a)** a session that is never pressed Finish stays open forever, because nothing closes it at rollover; `GymTracker.start()` then silently **returns the old open workout**, so no finished session ever exists. **(b)** `updateSet` writes `set.copy(…)` from the **composition's snapshot** on every keystroke (`GymEvent.UpdateSet(set, …, set.done)`), in separate unordered coroutines. A late keystroke write can put `done = false` back after the tick, or reset `rest_sec` after a rest change. `finish()` then **deletes every not-done set** (`sets.filterNot { it.done }.forEach(deleteSet)`), and an exercise with no done sets leaves nothing to look back at. **(c)** If every set is lost that way, the whole session becomes `DiscardedEmpty`. **Batch 3 first reproduces it** with a ViewModel test (finish A with exercise X → start B with X → "Last time" must be non-empty) and checks the device DB in the Debug screen, then fixes all three: writes become field-level commands (`SetWeight`, `SetReps`, `CompleteSet`) that never write a snapshot; "Last time" is derived from `ExerciseHistory` over finished sessions; and an open session from an earlier game-day is offered as "Finish yesterday's workout?" instead of being resumed silently (open question Q9). |
| P3 | Retyping every session; no weekly routine | Prefill exists but depends on P2. Routines have no weekdays, no rep targets and no "repeat". |
| P4 | 8 equal-weight text actions per exercise; Finish brightest; Discard not styled as danger | `ActiveSession` + `ExerciseCard`: `QuietButton` ×6 per card, `PrimaryButton(Finish)` always, `QuietButton(Discard)`. |
| P5 | Rest is a strip in a card; sets ticked seconds apart | `ActiveSession` shows `gym_rest_running`. No minimum rest exists in `updateSet`. |
| P6 | "1 sets done" | `gym_session_summary`, `gym_history_meta` and `gym_routine_line` are `<string>`s with `%d sets`, not plurals. All three move to `lexPlural`. |
| P7 | Onboarding gym answers not editable in Gym | Fitness level, equipment choice, injury regions and sex are **not stored**: onboarding stores only derived values (`owned_equipment` quest keys, `injury_tags`, `training_days`, rep range in `gym_settings`). The illustration set is never stored (onboarding PLAN Q1), and sex exists only in `nutrition_profile` if Diet was set up. |
| P8 | Library tags | `Pullups`, `Chin-Up` and `Dips_-_Triceps_Version` are "body only" but need a bar. Planks have no time measure (sets are weight × reps only). Both are handled by an overlay (§5.2). |
| P9 | Rest end with the screen off is late | WorkManager one-shot delays are inexact under Doze, so the chime can arrive late. |

### 0.3 Rule conflicts this brief creates (must be decided, not guessed)

1. **Gym XP.** GD §13 (approved 2026-10-27) says Gym mints **no XP** and ED #95 says nothing in Gym writes the ledger. GD §16.7 says PRs pay no XP "because weights are self-logged". Part 4 of the brief reverses that, so §4 below is a proposal for a **new GD §18** and needs explicit approval. (Q1)
2. **Benchmark population.** The only legally usable open dataset for barbell percentiles is OpenPowerlifting: **competitive raw lifters**, not the "general lifting population". Copy has to name the population honestly. (Q2)
3. **"Plans" vs "Programs".** GD §16.15 Programs (fixed-length plans) already exist under Progress. Two features both called "plan" will confuse. Proposed name: **"Plans"** in Gym only, and Programs keep their name. (Q8)

---

## 1. Information architecture

### 1.1 Gym home (one screen, top to bottom)

1. **Top bar:** "Gym" · a subtitle line from settings (e.g. "Build muscle · Intermediate · 4 days") · settings icon (opens Gym settings, §1.5).
2. **Week strip** Mon → Sun of the current ISO week (`TrainingMath.weekStart`). Today is marked with an underline and bold weight. A day with a finished workout gets a dot. Selected day defaults to today. Swipe changes week (back only, so you can browse history; next week is shown read-only).
3. **Selected day body:**
   - **Workout day** (a routine is scheduled or swapped in): routine name, duration estimate, two collapsible sections **Warm-up** (optional, collapsed by default) and **Workout**, each with its total time on the right. Exercise rows: name, "3 × 8–10", muscle chips. Supersets are framed as a group with the label "Superset: do these back to back". Unilateral pairs read "Left and right". Bottom bar: **Edit** (secondary) · **Start** (primary).
   - **Rest day:** a calm card ("Rest day. Recovery is part of training.", own copy) with **"Swap with today"** when the selected day is not today and today is a rest day. This moves the workout for this week only (§1.4).
   - **Past day:** the finished workout summary, or "No workout logged".
   - **Nothing scheduled this week:** an empty state with two actions, "Pick a plan" and "Create a routine".
4. **Plans** (horizontal cards, filtered and ranked, §1.3): name, days per week, level, equipment icons. Tap → plan detail → **"Add to my week"**.
5. **My routines** (list): name, weekdays chips, exercise count. Each has Start, and a ⋯ menu with Edit · Duplicate · Assign days · Delete (confirm). "New routine" row at the end. Drag handle to reorder.
6. **Strength** (entry row) → Strength tracker (§3).
7. **This week** card (kept): workouts · sets · volume · "Muscle map".
8. A small row at the bottom: Exercise library · Plate calculator · History.

### 1.2 Plans — `assets/gym/plans.json` (max 20, `G1`)

Original, hand-written by us from general training principles (compound → isolation; 10–20 weekly sets per muscle for
hypertrophy, Schoenfeld et al. 2017 meta-analysis; longer rest on heavy compounds, Schoenfeld et al. 2016). No plan is
copied from any app.

**Proposed list (16):**

| id | Name | Days | Level | Equipment | Notes |
|---|---|---|---|---|---|
| `ppl6` | Push / Pull / Legs | 6 | Intermediate+ | gym | P-P-L-P-P-L, Sun rest |
| `ppl3` | Push / Pull / Legs (3 days) | 3 | Beginner+ | gym | Mon/Wed/Fri |
| `ul4` | Upper / Lower | 4 | Beginner+ | gym | Mon/Tue/Thu/Fri |
| `fb3` | Full body ×3 | 3 | Intermediate | gym | A/B/A then B/A/B |
| `fb3_beg` | Beginner full body | 3 | Beginner | gym | Machines and dumbbells, 2–3 sets |
| `chest` | Chest | 1 | any | gym | Single day, assign a weekday |
| `chest_tri` | Chest + triceps | 1 | any | gym | e.g. Incline DB press → Bench → Cable crossover → Pushdown/Skullcrusher superset |
| `chest_sho` | Chest + shoulders | 1 | any | gym | |
| `back` | Back | 1 | any | gym | |
| `back_bi` | Back + biceps | 1 | any | gym | |
| `sho_arms` | Shoulders + arms | 1 | any | gym | Bicep/triceps supersets |
| `legs` | Legs | 1 | any | gym | |
| `bw_home3` | Bodyweight at home | 3 | Beginner+ | body only (+ optional bar) | Pull-up variants only if the bar is owned |
| `db_fb3` | Dumbbell-only full body | 3 | Beginner+ | dumbbells | |
| `db_ul4` | Dumbbell upper / lower | 4 | Intermediate | dumbbells | |
| `core` | Core and carry | 1 | any | mixed | Planks (time), Pallof press, Dead bug, Farmer's walk |

**Shape of one entry:**

```json
{
  "id": "chest_tri", "name": "Chest + triceps", "level": "any", "goal": ["muscle", "general"],
  "equipment": ["barbell", "dumbbell", "cable"], "requires": ["bench"],
  "days": [{
    "weekday": null, "name": "Chest + triceps",
    "warmup": [{ "exerciseId": "fedb:Band_Pull_Apart", "sets": 2, "reps": [15, 15], "restSec": 30 }],
    "work": [
      { "exerciseId": "fedb:Incline_Dumbbell_Press",              "sets": 3, "reps": [8, 10],  "restSec": 120 },
      { "exerciseId": "fedb:Barbell_Bench_Press_-_Medium_Grip",   "sets": 3, "reps": [6, 8],   "restSec": 150 },
      { "exerciseId": "fedb:Cable_Crossover",                     "sets": 3, "reps": [12, 15], "restSec": 60 },
      { "exerciseId": "fedb:Triceps_Pushdown_-_Rope_Attachment",  "sets": 3, "reps": [10, 12], "restSec": 75, "superset": "A" },
      { "exerciseId": "fedb:EZ-Bar_Skullcrusher",                 "sets": 3, "reps": [10, 12], "restSec": 75, "superset": "A" }
    ]
  }]
}
```

`weekday` is 1–7 for multi-day plans and `null` for single-day plans, which ask which weekday on Add. `reps` is a target
range `[min, max]`. `"measure": "time"` entries use `seconds: [min, max]` instead.

**`PlanCatalogTest` (in `:app` unit tests, catalog JSON) checks:**
- ≤ 20 plans (`G1`), unique plan ids, every `exerciseId` exists in `exercises.json`;
- every exercise's catalog `equipment` ∈ the plan's `equipment` (or `body only`); `requires` covers bar/dip/bench needs from the overlay (§5.2);
- no empty day; 1–8 work exercises per day (`G2`); sets 1–6; reps 1–30 with min ≤ max; rest 15–600 in steps of 15 (`RestRules`);
- superset groups are contiguous, 2–3 exercises, and share set count;
- within a day's work section, compound exercises come before isolation ones (catalog `mechanic`; supersets exempt);
- per plan week, sets per primary muscle ≤ 20 (`G3`), a sanity ceiling only.

### 1.3 Filtering and ranking by your answers (`:engine` `body/PlanRules`)

| Answer (now stored in Gym settings, §1.5) | Effect |
|---|---|
| **Equipment** (multi-select: body only · pull-up bar · dip bars · bench · dumbbells · barbell + rack · cables · machines · kettlebells · bands) | A plan is **shown** only if all its `equipment` + `requires` are owned. An exercise needing unowned equipment is swapped via the Replace rule (§2.5) or dropped. A day that empties hides the plan. Prefill from onboarding: BODYWEIGHT → body only; DUMBBELLS → body only + dumbbells + bench; GYM → everything. |
| **Injuries** (shoulders · back · wrist · knee · hip) | **Hides exercises that load the area** using muscle rules plus an explicit id overlay (§5.2): shoulders → primary SHOULDERS, overhead presses, dips, upright rows; back → primary LOWER_BACK, deadlifts, good mornings, barbell bent-over rows; wrist → primary FOREARMS, clean-grip front squats, push-up/dip variants; knee → primary QUADRICEPS compounds, lunges, jumps (category plyometrics); hip → primary GLUTES/ADDUCTORS/ABDUCTORS compounds, deep squats, lunges. Hidden exercises are swapped (Replace rule, easier first) or dropped. Copy: "Hidden because of your knee. Change in Gym settings." Not medical advice; the existing injury disclaimer applies. |
| **Fitness level** | Beginner: plans tagged `beginner`/`any` first; `ppl6` hidden; exercises with catalog `level = expert` swapped down. Intermediate: all. Advanced: all, `ppl6`/`ul4` first. Rep targets follow OB6 (existing `OnboardingMapping.repRange`) unless the plan sets its own. |
| **Training days** | Plans with exactly that many days first, then ±1 day. Single-day plans always listed (they fill a chosen weekday). |
| **Goal** (strength · muscle · general fitness) | Ranks plans by `goal` tag. Strength: the first compound of each day uses **4–6** reps (`G4`); others unchanged. |

Ranking order = goal match → day-count distance → level match → name. Deterministic, tested.

### 1.4 My routines and the week

- A **routine** is one workout day (a name, ordered exercises with sets × rep range, rest, supersets, unilateral flag, warm-up section). "Monday Chest + Triceps" is just a name.
- **Weekly schedule:** each weekday holds at most one routine (`week_slot`). Assigning repeats every week until changed. A routine may hold several weekdays.
- **Add a plan to my week** = copy each plan day into a new routine (`source_plan_id` set) + fill the slots. Your copies are editable; plans never change under you. If a slot is taken: "Replace Monday's Chest?" confirm.
- **Swap with today** = a one-off `day_override` row for this week's two dates. The weekly schedule is untouched.
- **Save as routine** from the finish summary (existing `saveAsRoutine`, now also saving the rep range used and supersets).
- Routine editor: name, reorder (drag), add exercise (picker), sets stepper, rep range, rest, superset (select 2–3 → "Make superset"), unilateral toggle, warm-up toggle, delete with confirm, duplicate.

### 1.5 Gym settings (inside Gym, top-bar icon)

Sections: **You** (fitness level, goal, body weight, sex — "Used only to compare your lifts with published data. Optional."),
**Equipment** (multi-select, §1.3), **Injuries** (body-map picker, same as onboarding), **Training days** (writes
`hunter_profile.training_days`, single source), **Units** (kg / lb display; storage stays kg; supersedes GD §16.8
Q7 "workouts stay in kg"), **Rest** (default rest compound / isolation `G5`, sound on/off, vibration on/off), **Logging**
(coached / quick list), **Plates** (existing bar and plates), **Rep range** (existing).

Body weight: one source, `hunter_profile.weight_kg`. Saving here also updates `nutrition_profile.weight_kg` if it exists,
so Diet doesn't drift (Q5). Sex: `gym_settings.sex` (nullable), prefilled from `nutrition_profile.sex` when present.

---

## 2. Active workout

### 2.1 Coached mode (default)

One exercise at a time, full screen:
- Top bar: back (minimises to a "Workout running" bar), elapsed time, **Finish** as a small text action, ⋯ menu.
- Progress: a thin segmented bar, one segment per exercise.
- **Name** clean ("Bench press"), the variant as subtitle ("Barbell · medium grip"). Clean names come from an overlay table for plan exercises (§5.2), and fall back to the catalog name.
- **"Set 2 of 3"**, `BodyMap` highlighting primary (full) and secondary (half) muscles, front and back.
- **Target**: big numbers `40 kg × 10`, each tappable. Tap opens a number pad sheet. ± steppers sit beside them (§2.2).
- **"Last time: 40 kg × 10, 10, 9"** always visible. With no history it reads "First time — pick a weight you can do for {min} reps". The progression hint replaces the target when it applies: "Try 42.5 kg × 8 (you hit 12, 12, 12)".
- **Complete set**: the one primary button, full width, ≥ 56 dp.
- Done sets collapse above as quiet lines: "1 · 40 kg × 10 ✓". Tap one to edit it while the workout is running.
- Time-measured exercises (plank) show a big stopwatch with Start/Stop instead of reps.
- Supersets: the card shows "Superset A · 1 of 2" and **Complete set** advances to the partner with no rest. Rest starts after the last exercise of the group.
- Unilateral: "Left" then "Right" as two steps of one set, no rest between sides.
- ⋯ menu: Replace exercise · Plate calculator · History · Add set · Remove set · Notes · Skip exercise · Discard workout (danger, confirm).
- After the last exercise: "Add exercise" small row, then **Finish workout** becomes the primary button.

### 2.2 Prefill and steppers

- Weight/reps prefill order: progression hint (GD §16.6) → last session's set *i* → routine target min reps with the last weight → empty.
- Stepper increment (`G6`) by catalog equipment: barbell = 2 × smallest owned plate (default 2.5 kg); dumbbell 2.5 kg; cable/machine 5 kg; kettlebells 4 kg; body only (added load) 2.5 kg. The per-exercise override (GD §16.6 increment) wins. Reps step 1. Time step 5 s.
- lb display steps: barbell 2 × smallest lb plate (5 lb), dumbbell 5 lb, cable/machine 10 lb.

### 2.3 Rest and MIN_REST (`:engine` `body/RestRules`)

- **Complete set** starts the rest automatically (exercise rest; superset → after the group). Completing the next set restarts it.
- **Full-screen rest:** a large calm countdown (`DESIGN_SYSTEM` motion tokens, reduce-motion respected), −15 s · +15 s · Skip, "Log last set" (edit the set just done), a Next-up card (exercise, set n of m, target). Back minimises it to a bar on the coached card.
- **MIN_REST** (`G7`): `minRest = clamp(ceil(0.25 × plannedRest), 10, 30)` seconds. Planned rest off → 10 s.
  - Planned 60 → 15 s · 90 → 23 s · 120 → 30 s · 180 → 30 s · 30 → 10 s.
- Until MIN_REST has elapsed since the previous set **of the same exercise** was completed: **Complete set** is disabled, and **Skip** is hidden. A line explains it in place: "Rest at least 23 s between sets. It keeps the log honest and counts for XP."
- MIN_REST does not apply between superset partners or between the left and right sides of a set; it applies between rounds.
- Every completed set stores `completed_at`, so the engine can check MIN_REST afterwards (§4).

### 2.4 Timer with the screen off

- While a rest runs, a **foreground service** shows an ongoing notification with a live countdown (`setUsesChronometer(true)` + `setChronometerCountDown(true)`), Skip and +15 s actions. At 0 it plays the existing chime (if Sound is on) and vibrates (if Vibration is on), then posts "Rest over — set 2 of 3". The service stops when no rest runs.
- Service type: `specialUse` with subtype "Workout rest timer" (Play Console declaration needed). `shortService` is ruled out because rests run up to 600 s. Alternative: exact alarms, which need `SCHEDULE_EXACT_ALARM` user consent on Android 14+. **Decision needed (Q6).** No network; invariant 8 unaffected.
- `RestEndWorker` stays as the fallback when the service can't start (e.g. notification permission off).

### 2.5 Replace sheet ("Swap with")

Pure engine function `ExerciseAlternatives.of(exercise, owned, injuries)` over catalog data only:
- Candidates: same **first primary muscle**, same `mechanic` (compound/isolation; null-mechanic exercises have no alternatives), equipment owned, not hidden by injuries, category in {strength, powerlifting}.
- **Easier** = catalog `level` one step down, **Harder** = one step up, **Similar** = same level. Each list holds up to 5 items, ranked by shared secondary muscles then name.
- An empty list shows no option. If all three are empty, "Replace" is greyed with "No alternatives in the library".
- Swapping keeps the set count, rep range and rest. It doesn't change the routine unless you choose "Also change the routine".

### 2.6 Finish, discard, summary

- **Finish** (top-bar action before the last set, primary button after). If sets are still open: "Finish with 2 sets not done?" and those sets are dropped (current behaviour, kept).
- **Discard**: only in ⋯, styled with the `critical` token, behind a confirm dialog with a danger confirm button.
- **Summary** (full screen): duration · sets · volume · PRs (one line per record type per exercise, existing) · **XP earned** (§4, with the breakdown line, Pillar 4) · unconfirmed-lift note if flagged · **Save as routine** (if not started from one) · Done.

### 2.7 Quick-list mode

A toggle in Gym settings, and in ⋯ during a workout. All exercises and sets are visible, but each set row is a full-width
card: "40 kg × 10" as two tappable chips with a ≥ 48 dp **Complete** button. No spreadsheet columns. "Last time" sits under
the exercise name. The same rest overlay and MIN_REST rules apply.

---

## 3. Strength tracker and benchmarks ("Where do I stand?")

### 3.1 Screens

- **Strength tracker:** exercises you have logged, grouped by primary muscle, each row "Bench press · 60 kg × 5 · 3 Nov", with a small level chip if the exercise is benchmarked.
- **Exercise detail:** name + `BodyMap` · best set and date · estimated 1RM (Epley, GD §13.2) · PR badge (small celebration on a new PR: haptic + scale-in, reduce-motion → none) · history chart (ChartKit `TrendLine`, existing ranges) · **Where do I stand?** (when benchmarked and body weight + sex are set):
  - **Level gauge**: a semicircle in 5 bands (Beginner · Novice · Intermediate · Advanced · Elite) with your marker. New ChartKit spec `LevelGauge`. Band colours come from semantic tokens.
  - **Distribution curve**: a smooth density with your marker. Drag a pointer along it to read "At 80 kg → about the 62nd percentile". The y-axis is unlabelled and the peak value is never shown. New ChartKit spec `DistributionCurve` with a TalkBack slider action.
  - Caption: "Estimated against {population}. Your 1RM is estimated from your best set." See Q2 on the population wording.
  - Without body weight or sex: "Add your body weight and sex in Gym settings to compare." + button. PRs and charts still show.
- Without a credible source for that exercise, the section doesn't appear.

### 3.2 Benchmark sources (research)

| Source | What it gives | Licence | Usable for |
|---|---|---|---|
| **OpenPowerlifting** (openpowerlifting.org, opl-data on GitLab) | Every recorded meet result: sex, age, body weight, equipment, best squat / bench / deadlift | Data released to the **public domain** (copyright waived, CC0 mechanism); code is AGPL (not used). Attribution optional; we'll credit it in `DATA_SOURCES.txt`. | **Percentile curves** for squat, bench, deadlift. Population = people who competed raw in powerlifting. |
| **US Army Fitness Test (AFT) scoring scales**, effective 1 Jun 2025 (army.mil PDF) | Points by age and sex for 3-rep-max deadlift, hand-release push-ups, plank time | US government work, **public domain** (17 U.S.C. §105) | **Level gauge only** (standards, not population percentiles) for deadlift, push-ups, plank. |
| **US Marine Corps PFT tables** (MCO 6100.13A, current admin change) | Points by age and sex for pull-ups, push-ups, plank | US government work, public domain | **Level gauge only** for pull-ups (and cross-check push-ups/plank). The official table must be sourced from marines.mil before use; third-party calculators are not acceptable sources. |
| Strength Level, Symmetric Strength, ExRx/Kilgore tables, ACSM/NSCA norm tables, Cult | Strength standards / norms | Proprietary or copyrighted compilations | **Not used** (per brief). |

**Method for OpenPowerlifting (build script `tools/catalog/build-benchmarks.mjs`, offline, pinned commit):** filter
`Event = SBD`, `Equipment = Raw`, `Place` not DQ/DD, `Age` 18–59 or missing, body weight present. One entry per lifter (their
best total). Compute `ratio = BestLiftKg / BodyweightKg` per lift and sex. Store the **quantile table** p1…p99 per lift per
sex (198 numbers per lift) in `assets/gym/benchmarks.json`, with no parametric fit, so nothing is invented. The engine
interpolates linearly between quantiles for a percentile, and draws the curve as a kernel density over the stored
quantiles. Each entry carries `source`, `licence`, `commit`, `filters`, `n`.

**Level bands from percentile (`G8`):** Beginner < p20 · Novice p20–49 · Intermediate p50–79 · Advanced p80–94 · Elite ≥ p95.
**Military-standard levels (`G9`):** below minimum passing = Beginner · passing–59 pts = Novice · 60–79 = Intermediate ·
80–99 = Advanced · 100 = Elite, using the tester's age band and sex. Labelled "Level against the US Army test standard",
never as a percentile, and with no distribution curve.

### 3.3 The ~50 core exercises (proposed list and what each gets)

Chosen because they are the most-logged compound and staple isolation moves across the plans in §1.2. Each catalog variant
of the same movement maps to one benchmark key (e.g. `Barbell_Squat`, `Barbell_Full_Squat` → `squat`; `Bench_Press_-_Powerlifting` → `bench`; `Sumo_Deadlift` → `deadlift`).

| Tier | Exercises | Shown |
|---|---|---|
| **A — percentile + gauge** (OpenPowerlifting) | Back squat, Bench press, Deadlift (conventional and sumo) | Gauge + draggable curve |
| **B — level gauge only** (US military standards, public domain) | Deadlift (AFT 3RM, cross-check), Push-up (hand-release), Plank (time), Pull-up | Gauge, no curve, no percentile |
| **C — PRs and charts only** (no credible open source found) | Front squat, Romanian deadlift, Overhead press, Push press, Incline bench, Dumbbell bench, Incline DB press, Close-grip bench, Dips, Chin-up, Barbell row, Pendlay/T-bar row, One-arm DB row, Seated cable row, Lat pulldown, Face pull, Barbell curl, DB curl, Hammer curl, Preacher curl, Triceps pushdown, Skullcrusher, Lateral raise, Rear-delt fly, Shrug, Hip thrust, Glute bridge, Leg press, Hack squat, Bulgarian/split squat, Lunge, Leg extension, Leg curl, Standing calf raise, Seated calf raise, Good morning, Back extension, Hanging leg raise, Ab roller, Cable crunch, Pallof press, Farmer's walk, Goblet squat, Kettlebell swing, Inverted row, Arnold press, Cable crossover | Best set, e1RM, PR badge, history chart |

That is 4 Tier A/B movements (plus the deadlift cross-check), and ~45 exercises in Tier C. **Under the source rule in the
brief, most of the ~50 get no percentile.** Deriving them from ratios to the big three (e.g. "incline ≈ 0.8 × bench")
would need a source we don't have, so it is not proposed. See Q3.

### 3.4 Engine (`:engine` `body/Benchmarks`)

- `ratio(e1rmKg, bodyWeightKg)`; reps-based for bodyweight moves; seconds for plank.
- `percentile(ratio, quantiles)`: linear interpolation, clamped to 1–99 (never "100th").
- `level(percentile)` (`G8`), `militaryLevel(points)` (`G9`), `points(value, table, ageBand, sex)`.
- Inputs use the **best confirmed** e1RM (plausibility, §4.3). Bodyweight moves use best reps in one set.
- Age for the AFT/PFT age band comes from `hunter_profile.birth_year`. Without it → the 22–26 band (`G10`), labelled "age not set".

---

## 4. STR XP from training (proposal for new GD §18; reverses GD §13 "no XP", Q1)

### 4.1 Formula (`:engine` `body/TrainingXp`, pure)

```
counted set   = WORK set (not warm-up), completed while the workout was open, reps ≥ 1 (or time ≥ 10 s),
                completed_at − previous same-exercise completed_at ≥ minRest (first set of an exercise always passes),
                not flagged implausible (§4.3)
effort E      = 1.0  normal set
              = 0.5  light set: weighted, and load < 50 % of the exercise's best confirmed e1RM before this workout  (G11)
                     or bodyweight with < 5 reps / < 20 s  (G12)
per exercise  = at most 10 counted sets per exercise per game-day  (G13)
gymXp(workout)= round( 5 × Σ E )                                   (G14: 5 XP per full set)
daily cap     = 150 gym XP per game-day                             (G15: 30 full sets)
```

- **Source and caps:** verification `SELF`, so gym XP also counts toward the existing **SELF cap of 600 XP/day** (GD §4.3). Beyond either cap the XP is 0, with no 25 % tail; that is simpler and gym already has its own cap. (`G16`, Q4)
- **Stat:** 100 % to `STR`, no secondary. It counts toward total XP and level like quest XP. No gold. No streak multiplier (S is for quests; gym effort shouldn't compound with streak, Pillar 4 keeps the breakdown simple).
- **Scale check:** a 20-set session = 100 XP ≈ 1⅔ Normal SELF quests (60 XP). A max day is 150 XP ≈ 43 % of the 350 XP/day calibration (GD §4.4). It moves the level curve but doesn't dominate it.
- **No XP for:** warm-ups, sets edited or added after the workout ends (edits after end are allowed for the log but never re-price; the ledger row is already written), sets in a discarded workout.

### 4.2 Ledger (invariants 1–3)

- Paid **once, at Finish**, inside the same `withTransaction` as `endedAt`, PR evaluation and achievement unlocks.
- Rows: `(sourceType = GYM_WORKOUT, sourceId = workoutId, kind = XP)` and `(…, kind = STAT_XP_STR)`. `INSERT IGNORE` + check, so finishing twice is a no-op.
- **Deleting a finished workout** writes reversing rows `(GYM_WORKOUT_UNDO, workoutId, …)` and leaves the original rows in place. Cap usage for that day is recomputed from the ledger.
- Import: reversals and caps rebuild from the ledger (existing `GameDataPorter` path).

### 4.3 Plausibility flag

- A set is **implausible** if its e1RM > **1.20 ×** the exercise's best confirmed e1RM (`G17`) **and** that best comes from ≥ 2 earlier sessions, or if its `ratio` > **3.5 × body weight** for squat/deadlift, **2.5 ×** for bench and **2.0 ×** for any other lift (`G18`).
- An implausible set is saved and shown with "Unconfirmed — repeat it in a later session to confirm". It earns **no XP** and doesn't move benchmarks. It still counts toward volume and charts, and the PR shows as "unconfirmed".
- **Confirmed** when a set on a later game-day reaches ≥ **95 %** of the flagged e1RM (`G19`). From then on the benchmark uses it. XP is never paid retroactively.
- Derived by replaying finished sessions (invariant 7). Nothing is stored.

### 4.4 Test vectors (to add to GD §12/§18; all in `:engine`)

| # | Input | Expected |
|---|---|---|
| G-1 | 3 work sets bench 60×8, rest 120 → minRest 30 s, completed 0 s / 95 s / 190 s | 3 counted, E 1.0 each → 15 XP |
| G-2 | Same, 2nd set at +20 s | 2nd not counted → 10 XP |
| G-3 | Warm-up 2 sets + 3 work sets | warm-ups 0 → 15 XP |
| G-4 | Best e1RM 100; set 40×10 (e1RM 53.3, load 40 < 50) | E 0.5 → 3 XP (round 2.5 half-up) |
| G-5 | 14 counted sets of one exercise | 10 counted → 50 XP |
| G-6 | Two workouts same day, 25 + 15 full sets | 125 then 25 (cap 150) |
| G-7 | SELF quests already 580 XP today; workout 20 sets | gym 20 XP (SELF cap 600) |
| G-8 | Finish called twice | one set of ledger rows |
| G-9 | Delete finished workout | reversing rows; day's gym cap usage back to 0 |
| G-10 | Best 100 (from 3 sessions); new set e1RM 125 | flagged, 0 XP for it, benchmark stays 100 |
| G-11 | Next game-day set e1RM 120 (≥ 118.75) | confirmed, benchmark 125 |
| G-12 | Superset A1/A2 completed 5 s apart, then A1 round 2 at +40 s, minRest 15 | all counted |
| G-13 | minRest: planned 60/90/120/30/off | 15/23/30/10/10 |
| G-14 | Percentile: ratio exactly p50 value; below p1; above p99 | 50 / 1 / 99 |
| G-15 | Replace: bench (beginner, compound, chest), owned barbell+dumbbell | easier list empty or lower level only; harder list only `intermediate` chest compounds with owned equipment |

---

## 5. Data and tech

### 5.1 Room (schema 28 → 29, one migration, `MigrationTest.v28To29…`)

| Table | Change |
|---|---|
| `routine` | + `sort_order INTEGER NOT NULL DEFAULT 0`, `source_plan_id TEXT NULL` |
| `routine_exercise` | + `rep_min INTEGER NULL`, `rep_max INTEGER NULL`, `superset TEXT NULL`, `unilateral INTEGER NOT NULL DEFAULT 0`, `section TEXT NOT NULL DEFAULT 'WORK'` (WORK/WARMUP), `measure TEXT NOT NULL DEFAULT 'REPS'` |
| **new** `week_slot` | `weekday INTEGER PK (1–7)`, `routine_id TEXT NOT NULL` |
| **new** `day_override` | `game_date TEXT PK`, `routine_id TEXT NULL` (null = rest) |
| `workout` | + `routine_id TEXT NULL`, `mode TEXT NOT NULL DEFAULT 'LIST'` (old rows are LIST) |
| `workout_set` | + `completed_at INTEGER NULL`, `section TEXT NOT NULL DEFAULT 'WORK'`, `duration_sec INTEGER NULL`, `side TEXT NULL` (LEFT/RIGHT), `superset TEXT NULL`, `rep_min/rep_max INTEGER NULL` |
| `gym_settings` | + `fitness_level`, `goal`, `equipment` (csv), `injuries` (csv), `sex` (nullable), `rest_compound_sec`, `rest_isolation_sec`, `rest_sound`, `rest_vibrate`, `coached` (bool), `display_unit` |
| ledger | new `sourceType` values `GYM_WORKOUT`, `GYM_WORKOUT_UNDO` (enum, no column change) |

Old workouts, sets, routines and PRs survive unchanged (all new columns default or null). Legacy sets have no
`completed_at`, so they never pay XP; PRs and history still replay from them. Migration test: insert v28 rows with a
finished workout, sets, a routine and gym settings, migrate, and assert every row, every PR and the history are identical.
**Export/import:** `ExportDocument.TABLES` + `week_slot`, `day_override`, export version bump; an older backup imports with
defaults (`GameDataPorterTest`).

### 5.2 Assets

- `assets/gym/plans.json` (§1.2).
- `assets/gym/benchmarks.json` (§3.2), built by `tools/catalog/build-benchmarks.mjs` from a pinned OpenPowerlifting commit (raw CSV not shipped), plus hand-entered AFT/PFT tables with page references.
- `assets/gym/exercise_overlay.json`: per-id `cleanName`/`variant`, `requires` (pull-up bar, dip bars, bench, rack), `measure: time`, `injury` loads, benchmark key. Small and reviewed. The catalog itself stays untouched (Unlicense, pinned commit).
- `assets/licenses/DATA_SOURCES.txt` + OpenPowerlifting (public domain) and US Army/USMC tables (US government works).

### 5.3 Engine (new, all pure, JUnit5)

`body/PlanRules` (filter, rank, materialise plan → routines) · `body/WeekSchedule` (slot + override → routine for a date) ·
`body/RestRules.minRest` · `body/StepIncrement` (uses `PlateMath` owned plates) · `body/ExerciseAlternatives` ·
`body/Benchmarks` · `body/Plausibility` · `body/TrainingXp`. Reused unchanged: `TrainingMath`, `ProgressionRules`,
`RecordRules`, `PlateMath`, `ExerciseHistory`.

### 5.4 App

- `GymTracker` → split into `GymSessionRepository` (session commands: `completeSet`, `setWeight`, `setReps`, `replace`, `finish`), `RoutineRepository` (routines, week, overrides, plans) and `GymTracker` (overview; kept as a facade so other callers don't break). `FinishWorkoutUseCase` owns the single transaction (end + PRs + XP rows + achievements).
- Screens (feature/gym): `GymHomeScreen`, `PlanDetailScreen`, `RoutineEditorScreen`, `CoachedWorkoutScreen`, `RestScreen`, `QuickListWorkoutScreen`, `WorkoutSummaryScreen`, `StrengthTrackerScreen`, `ExerciseDetailScreen`, `GymSettingsScreen`, plus sheets: Replace, Number pad, Plate calculator, ⋯ menu. Each has a `*Route` + stateless `*Screen(state, onEvent)` + `@PhonePreviews` for every state (`ScreenLayeringTest`).
- Designsystem: `WeekStrip`, `TargetNumber` (big tappable number + stepper), `CompleteSetButton`, `RestCountdown`, `NextUpCard`, `DangerButton` (critical token), `MuscleHighlight` (wraps `BodyMap`), ChartKit `LevelGauge`, `DistributionCurve`. No raw palette in feature code (`FeatureBoundaryTest`); all copy via Lexicon (`LexiconResourcesTest`).
- `core/work/RestTimerService` (foreground, §2.4); `RestEndWorker` stays as fallback.
- Navigation: type-safe routes under the Gym tab for the full-screen flows. Sheets stay sheets.

---

## 6. Tests and quality

- **Engine:** every vector in §4.4 + `PlanRules` (equipment/injury/level filters, ranking order), `WeekSchedule` (slot, override, swap, two weekdays one routine, week boundary at `dayStartHour`), `minRest`, `StepIncrement`, `ExerciseAlternatives`, `Benchmarks` (interpolation, clamps, military bands), `Plausibility` (flag, confirm, never retro-pay).
- **App unit (Robolectric, JUnit4):** `PlanCatalogTest` (§1.2 rules); `BenchmarkCatalogTest` (every entry has source + licence, quantiles monotonic, keys map to existing ids); `MigrationTest.v28To29`; `GameDataPorterTest` (round trip + old backup); `FinishWorkoutUseCaseTest` (one transaction, idempotent, caps, reversal on delete).
- **ViewModel:** coached flow (prefill order, complete → rest starts, MIN_REST disables Complete/Skip then enables, superset no-rest, unilateral L/R, replace keeps sets), **"Last time" regression for P2**, quick-list parity, finish summary XP line, discard confirm, weekly scheduling (assign, repeat next week, swap with today), settings edits round-trip and re-filter plans.
- **Compose / Roborazzi:** previews for every state (home: workout day / rest day / past day / empty week; coached: first time / with history / hint / time exercise / superset / unilateral / MIN_REST lock; rest: running / overdue; summary with PRs, XP, unconfirmed; tracker; detail with/without benchmark, missing body data; settings) at phone + small phone, dark + light. Goldens re-recorded per batch, and the changed screens named in the commit.
- **Per batch:** `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint` and `:app:verifyRoborazziDebug` green → commit → summary → stop.

---

## 7. Build order (Part B)

| Batch | Contents | Done when |
|---|---|---|
| 1 | Engine rules (§5.3) + vectors; `plans.json`, `exercise_overlay.json`, `benchmarks.json` + build script + licences; schema 29 + migration + export; GD §18 / ED entries for approved numbers | engine + migration + catalog tests green |
| 2 | Gym home (week strip, day body, rest day + swap), Plans list/detail/add, My routines + editor + weekly repeat, Gym settings | VM + previews green |
| 3 | Fix P2 (repro test first), field-level set commands, coached mode, rest screen + MIN_REST, replace sheet, supersets/unilateral, foreground rest service, finish summary, quick-list, plurals (P6), danger discard | VM + previews green; phone check of screen-off chime |
| 4 | Strength tracker, Exercise detail, `LevelGauge`, draggable `DistributionCurve` | VM + previews green |
| 5 | `TrainingXp` wiring in `FinishWorkoutUseCase`, plausibility UI, full review (correctness, no raw tokens, every `G` number approved and in GD) | full suite + lint + Roborazzi green. **Done** |

---

## 8. Proposed numbers (all need approval)

| Tag | Value | Section |
|---|---|---|
| G1 | ≤ 20 plans (16 proposed) | §1.2 |
| G2 | 1–8 work exercises per plan day | §1.2 |
| G3 | ≤ 20 weekly sets per primary muscle in a plan (sanity check) | §1.2 |
| G4 | Strength goal: first compound of a day 4–6 reps | §1.3 |
| G5 | Default rest: compound 120 s, isolation 60 s | §1.5 |
| G6 | Stepper: barbell 2 × smallest owned plate (2.5 kg), dumbbell 2.5 kg, cable/machine 5 kg, kettlebell 4 kg, added bodyweight 2.5 kg; lb 5 / 5 / 10 | §2.2 |
| G7 | `minRest = clamp(ceil(0.25 × plannedRest), 10, 30)` s; rest off → 10 s | §2.3 |
| G8 | Levels: < p20 · p20–49 · p50–79 · p80–94 · ≥ p95 | §3.2 |
| G9 | Military levels: < pass · pass–59 · 60–79 · 80–99 · 100 points | §3.2 |
| G10 | Missing birth year → 22–26 age band | §3.4 |
| G11 | Light weighted set: load < 50 % of best confirmed e1RM → E 0.5 | §4.1 |
| G12 | Light bodyweight set: < 5 reps or < 20 s → E 0.5; time sets count from 10 s | §4.1 |
| G13 | ≤ 10 counted sets per exercise per game-day | §4.1 |
| G14 | 5 XP per full set | §4.1 |
| G15 | Gym XP cap 150 / game-day | §4.1 |
| G16 | Gym XP counts toward the SELF 600 cap; beyond a cap → 0 (no 25 % tail) | §4.1 |
| G17 | Plausibility: e1RM > 1.20 × best confirmed (best from ≥ 2 sessions) | §4.3 |
| G18 | Plausibility ceiling: ratio > 3.5 × BW (squat, deadlift), 2.5 × (bench), 2.0 × (other) | §4.3 |
| G19 | Confirmation: later game-day set ≥ 95 % of the flagged e1RM | §4.3 |

## 9. Open questions

- **Q1 — Gym XP at all.** This reverses GD §13 / ED #95 / GD §16.7 ("self-logged, so no XP"). Approve §4 as a new GD §18, or keep Gym a tracker and drop Batch 5?
- **Q2 — Benchmark population wording.** OpenPowerlifting is competitive raw lifters, so the honest caption is "estimated against people who compete in raw powerlifting", not "general lifting population". Its percentiles run harsh for gym-goers, which matters for the labels (a regular lifter may read "Beginner"). Accept that wording, or show the gauge only without percentile numbers?
- **Q3 — Coverage.** Under "no credible source → no percentile", only squat/bench/deadlift get a curve and 3–4 bodyweight moves get a military-standard level. The other ~45 get PRs and charts only. OK, or do you have a licensable source in mind?
- **Q4 — Cap shape.** Beyond-cap gym XP = 0 (proposed) vs. the GD §4.3 25 % tail for consistency.
- **Q5 — Body weight source.** Single `hunter_profile.weight_kg`, with Gym settings writing through to `nutrition_profile` (proposed)? Or keep them separate?
- **Q6 — Screen-off rest timer.** Foreground service `specialUse` (needs a Play Console declaration; recommended) vs. exact alarm (needs the user to grant "Alarms & reminders").
- **Q7 — Double counting.** A `HEALTH_EXERCISE` STR quest (e.g. "45 min exercise") can be completed by the same gym session that also earns gym XP. Allow both (proposed: yes, different evidence), or pause gym XP on days that quest pays?
- **Q8 — Naming.** "Plans" in Gym vs. "Programs" under Progress. OK to keep both names?
- **Q9 — Stale open session.** On opening Gym with a workout open from an earlier game-day: offer "Finish it" / "Discard" (proposed) instead of resuming silently.
- **Q10 — Warm-up content.** Plans ship a short warm-up (bands/bodyweight, no XP). Keep it, or keep warm-up as an empty section you fill yourself?
