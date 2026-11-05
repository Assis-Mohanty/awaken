# Onboarding rebuild: plan (Part A)

Status: **awaiting approval. No code written.** Replaces DESIGN_SYSTEM §4.1 once built.

`docs/reference/onboarding-video-notes.md` did not exist when this was written. The interaction specs below come from your brief alone. When you add the notes, compare them with §6 and §7 before Batch 1. Copy and images stay original either way.

---

## 1. Decisions needed from you

The brief conflicts with the code in these places, or asks for something the app has nowhere to put. Each has a recommendation, and I'll build the recommendation if you approve it. Q1–Q3 change what gets built the most.

| # | Problem | Recommendation |
|---|---|---|
| **Q1** | **Gender vs Diet.** Step 3 is "for illustrations only", but Age, Height and Weight are meant to feed Diet, and `nutrition_profile.sex` is required (`MALE`/`FEMALE`, used in the BMR formula). Without sex there can be no Diet profile row. | Keep gender illustrations-only. It stays in the draft and is **deleted at Start**. Age, height and weight go to new nullable `hunter_profile` columns and prefill the Diet setup form, which still asks sex itself. No `nutrition_profile` row is written. |
| **Q2** | **Body shape feeds nothing.** Your rule says each answer must feed a real setting, and no setting exists for it. It is also the most sensitive screen for body image, and the app accepts ages from 13. | Keep it, and use it only to prefill the Diet goal (`WeightGoal`): stages 1–2 gain, 3 maintain, 4–5 lose. The Diet form can still change it. Copy describes, never judges. Stage labels are neutral ("Stage 3 of 5"). Alternative: drop the step, which removes 15 images. |
| **Q3** | **The goal chart has no input.** Step 19 needs "today's value" and "the user's own target date", but no step asks for a target or a date. | Add an optional **Add a target** sheet on Your plan: target weight and date. The chart only appears once that is set. It also sets the Diet goal prefill. Guards in §5 (OB17–OB18). Hidden under 18. Alternative: cut the chart, which is the simpler option. |
| **Q4** | **`accent` and `buttonPrimary`.** `accent` is the primary ink today, the same colour as the primary button, and `buttonPrimary` doesn't exist. | Add `buttonPrimary` / `onButtonPrimary` mapped to the current ink and move `PrimaryButton` onto them. Leave `accent`'s value alone. Onboarding will look monochrome until the redesign gives `accent` a colour, then pick it up automatically. |
| **Q5** | **Light onboarding on a dark-mode phone.** The app otherwise follows the phone or the Appearance setting. | Force the light palette for this flow only, by wrapping it in `AwakenTheme(darkTheme = false)`, including status-bar icons. `@PhonePreviews` for these screens still render both palettes so tokens stay valid. |
| **Q6** | **Steps removed from today's flow.** Your list has no AI Path step, no calibration questions and no daily reminder time. | Drop all three from onboarding. AI Paths stay reachable from Progress in debug builds. The reminder toggle in step 15 uses the existing 07:00 default (OB14), editable in Settings. |
| **Q7** | **Length.** PRD F1 says ≤ 60 s and DS §4.1 says < 60 s. This flow has 22 screens, closer to 3–4 minutes. | Accept it and change F1 and §4.1. The per-step Skip is the fast path. |
| **Q8** | **All numbers in §5 are new.** CLAUDE.md forbids inventing them, so all are marked PROPOSED. | Approve or edit the table. On approval they move to `GAME_DESIGN.md` §17 as the first commit of Batch 1 and the build uses them from there. |

---

## 2. Findings that shape the design

- **The profile row is the switch to Today.** `AppViewModel` shows Today as soon as a hunter exists, so nothing can be written to Room until Start. Steps 18–19 therefore run on in-memory data (§3.3).
- **Focus stats must be exactly two.** `CreateHunterUseCase` enforces it, but step 8 is an open multi-select with seven options. A rule reduces the answers to two (OB1).
- **`hunterId` seeds the plan.** Generate it into the draft so the preview on Your plan is the same plan Today shows.
- **A wheel can't pick every bedtime.** GD §16.12 allows bedtime 20:00–02:00, wake 04:00–11:00, 15-minute steps, a 5–12 h window, and wake later than `dayStartHour`. Wheels use exactly these. People outside them (night shifts) skip the step.
- **Phone-down nights and Screen Discipline need usage access** and fail closed. Permissions come after those answers, so both are decided at Start.
- **Reuse rather than build:**
  - `BodyMap` already draws a token-coloured figure, so injuries need no artwork.
  - `gym_settings.rep_min/rep_max` already exists, so fitness level has a real gym setting.
  - `NutritionMath` already holds the age, height and weight ranges.
  - `SegmentedRing`/`ProgressRing`, `Haptics.tick()`, `NotificationAccess` and the Health connect flow already exist.
- **Two equipment vocabularies exist.** Quests use `owned_equipment` keys (`dumbbells`, `gym_access`, …) and the Gym catalog uses its `Equipment` enum. Step 13 writes only the quest keys. The Gym library is not filtered by it.
- **BMI colour bands are adult-only.** Adult cut-offs don't apply to teenagers, and the app allows 13+. Under 20 or with no age, the card shows the number with no scale (OB7).
- **"Light" art won't restyle.** Tokens restyle the UI, not the illustrations. A later redesign that changes the art direction needs new images.

---

## 3. Data model

### 3.1 Answers

```kotlin
// :engine (pure, no Android). Null = skipped.
data class OnboardingAnswers(
    val hunterId: String,                  // generated once, kept in the draft
    val name: String,
    val illustrationSet: IllustrationSet?, // MALE, FEMALE, NEUTRAL. UI only, never leaves the draft
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val heightUnit: LengthUnit, val weightUnit: MassUnit,
    val bodyStage: Int?,                   // 1..5
    val improve: List<ImproveGoal>,        // selection order is kept: it breaks ties
    val obstacles: Set<Obstacle>,          // NONE is exclusive
    val screenTime: ScreenTimeBand?,
    val bedtimeMinute: Int?, val wakeMinute: Int?,
    val fitness: FitnessLevel?,
    val equipment: EquipmentChoice?,
    val injuries: Set<InjuryRegion>,       // NO_INJURIES is exclusive
    val trainingDays: Set<DayOfWeek>, val trainingReminder: Boolean,
    val pace: Pace?,
    val target: WeightTarget?,             // Q3
)
```

UI state in `OnboardingViewModel`: `step`, `direction`, `answers`, permission results, `plan: OnboardingPlan?`, `edits`, `submitting`, `failed`.

### 3.2 Draft persistence

- New `OnboardingDraftStore` with its own DataStore file and `@OnboardingDraftPrefs` qualifier. It holds one JSON string (kotlinx.serialization) of `answers` + `step` + `schemaVersion`.
- Written after each step. An unreadable or older-schema draft is discarded and the flow restarts.
- Cleared after commit. If a crash leaves it behind, it is ignored because `LaunchState.Ready` never opens onboarding.
- Cloud backup and device transfer are include-only in `data_extraction_rules.xml`, so this file is already excluded. Batch 1 checks `backup_rules.xml` too.

### 3.3 Plan and commit

```
Step 18  OnboardingPlanner.plan(answers, today) → OnboardingPlan     pure, :engine, no writes
         DirectiveGenerator dry run with the draft's hunterId        for the quest preview
Step 19  edits change answers; plan is recomputed
Start    CompleteOnboardingUseCase(plan), NonCancellable:
           transaction { profile, tiers, focus goal, bedtime rule } → seed app rules + discipline
           → rollover → clear draft
```

- Hunter creation, tiers and focus goal share one transaction so a failure leaves no half-made profile.
- App rules and discipline run after it, because seeding reads the launcher app list. A failure there only leaves discipline off, which the user can turn on in Settings.
- Everything is `NonCancellable`: the profile row closes the ViewModel's screen while the work is still running.
- Failure shows the existing System-styled error on Your plan with Retry. Answers are kept.
- No ledger rows and no XP. The Easy starter quest (GD §6.3) still applies.

### 3.4 Room v28

| Table | Change | Default | Used by |
|---|---|---|---|
| `hunter_profile` | `birth_year INT NULL`, `height_cm REAL NULL`, `weight_kg REAL NULL`, `diet_goal_hint TEXT NULL` | NULL | Diet setup prefill |
| `hunter_profile` | `training_days INT NOT NULL` (Mon–Sun bitmask) | 0 | Training reminder, Gym line, Settings |
| `hunter_profile` | `training_reminder BOOL NOT NULL` | false | Training reminder |

`gym_settings.rep_min/rep_max` needs no schema change. `MIGRATION_27_28`, schema JSON, `MigrationTest`, and `ExportUpgrade` v27→28 are all required. These are health-adjacent values, so export and any data-reset path must include them, and `PrivacyPolicyActivity` must mention them. Nothing leaves the phone, so Play's Data safety answers stay "not collected".

---

## 4. Steps and the answer → setting mapping

Group is the segment of the progress bar (5 segments, each filling as its steps pass; 22 thin segments would be unreadable).

| # | Screen | Group | Input and validity | Skipped means | Feeds |
|---|---|---|---|---|---|
| 1 | Welcome | You | Continue only. No back, no skip | n/a | nothing |
| 2 | Name | You | `Awakening.isValidName` (3–20). **Cannot skip** | n/a | `hunter_profile.name` |
| 3 | Gender | You | Single choice | Neutral set | Illustration set only (Q1) |
| 4 | Age | You | Wheel 13–100 (`AGE_RANGE`) | null | `birth_year` for Diet prefill; BMI and chart gating |
| 5 | Height | You | Vertical ruler 120–230 cm; 1 cm or 1 in steps | null | `height_cm`; BMI |
| 6 | Weight | You | Horizontal ruler 30–300 kg; 0.5 kg or 1 lb steps; BMI card if height known | null | `weight_kg` |
| 7 | Body shape | You | 5-stop slider | null | `diet_goal_hint` (Q2) |
| 8 | Improve | Goals | Multi-select, ≥ 1 | Default stats | `focus_stat_1/2` (OB1) |
| 9 | Obstacles | Goals | Multi-select, ≥ 1, None exclusive | Empty | Scrolling → Reel counter suggestion row; Poor sleep → Phone-down nights on; Sitting → step goal (OB3); Procrastination → focus goal (OB4) |
| 10 | Screen time | Goals | Single choice | null | Limit on seeded Distracting apps; Screen Discipline on, **only with usage access** (OB5) |
| 11 | Sleep | Daily life | Two wheels, valid per GD §16.12 | null | VIT tier always; `bedtime_rule` if Poor sleep and usage access |
| 12 | Fitness | Daily life | Single choice | null | STR tier, step goal, `gym_settings` rep range (OB2, OB3, OB6) |
| 13 | Equipment | Daily life | Single choice | Bodyweight | `owned_equipment` (OB8) |
| 14 | Injuries | Daily life | Multi-select, None exclusive | None | `injury_mode`, `injury_tags` (OB9) |
| 15 | Training days | Daily life | Chips, reminder toggle | No days | `training_days`, `training_reminder` (toggle on also sets `reminder_time` 07:00) |
| 16 | Pace | Daily life | Single choice; selected card expands | Balanced | Tier shift, focus goal (OB2, OB4) |
| 17a | Health Connect | Phone | Connect / Not now | Off | `health_enabled`, existing flow |
| 17b | Usage access | Phone | Open settings / Not now; re-check on resume | Off | Gate for steps 9–11 |
| 17c | Notifications | Phone | Allow / Not now; hidden below Android 13 or if granted | Off | Sets `notifications_prompted` so the post-quest prompt doesn't repeat |
| 18 | Building | Plan | No input, no skip | n/a | Computes `OnboardingPlan` |
| 19 | Your plan | Plan | Cards with edit; Start | n/a | Applies edits |
| 20 | Start | n/a | Action on step 19, not a screen | n/a | Commit, then Today |

Rules that apply across steps:
- **Back from 19** goes to 17c or the last shown permission, skipping 18. Back during 18 cancels and returns there too.
- **Usage access refused:** the step 10 and bedtime choices aren't written. Your plan shows "Needs usage access" with a link, and the app default (30 min) applies if the user turns it on later.
- **Accessibility:** none in this flow. Reel counter and Focus Lock remain opt-in after Start through their own disclosure screens. Scrolling adds a suggestion row only.
- **Training days are not a generated gym plan.** The app has user-built routines and no plan generator. The days drive a reminder on those days and a "Training day" line on Gym. Add a generator to `BACKLOG.md` if you want one.
- **Also add to Settings:** a Training days row under Health and training, so every answer stays editable afterwards.

---

## 5. Proposed numbers (all PROPOSED until Q8)

| ID | Rule |
|---|---|
| **OB1 Focus stats** | Strength → STR · Fitness → STR · Focus → WIL · Discipline → WIL · Less screen time → WIL · Sleep → VIT · Learning → INT. Take the two stats with the most picks; ties go to the earliest selection. Fewer than two: fill from STR, INT, WIL, VIT, CHA, skipping any already chosen. Skipped: STR + INT. CHA is only reachable by editing on Your plan. |
| **OB2 Baseline tiers** | STR: Beginner Easy, Intermediate Normal, Advanced Hard. VIT: from the sleep window using the existing buckets (< 6 h Easy, 6 to < 7 h Normal, ≥ 7 h Hard). INT: Normal, since the study question is gone. WIL and CHA: Easy (unchanged). Pace shifts every stat: Steady −1, Balanced 0, Push hard +1, clamped Easy–Hard. Skipped fitness or sleep: Normal. |
| **OB3 Step goal** | Beginner 6,000 · Intermediate 8,000 (`N19`) · Advanced 10,000; Sitting all day −2,000. Skipped: 8,000. |
| **OB4 Focus goal** | Steady 60 · Balanced 120 (`N11`) · Push hard 180 min; Procrastination halves it (30 / 60 / 90). Skipped: 120. All are valid `N12` steps. |
| **OB5 App limit** | Under 2 h: 30 min (`DEFAULT_LIMIT_MINUTES`) · 2–4 h: 45 · 4–6 h: 60 · 6 h+: 90. Applied to each seeded Distracting app. Higher starts stop the first day being an automatic forfeit. |
| **OB6 Gym rep range** | Beginner 10–15 · Intermediate 8–12 (`N14`) · Advanced 6–10. Per-exercise overrides still win. |
| **OB7 BMI** | `kg / m²`, one decimal. Scale marks at 18.5, 25 and 30 (WHO adult). Scale shown only when age ≥ 20; otherwise the number and one neutral line. Labels: "Below the usual range", "Within the usual range", "Above the usual range". Band words appear only on the scale, never as a label about the person. |
| **OB8 Equipment** | Bodyweight → none · Dumbbells → `dumbbells` · Gym → `gym_access`, `dumbbells`, `kettlebell`, `pullup_bar`, `resistance_band`. |
| **OB9 Injury tags** | Shoulders, Wrist → `upper` · Back → `impact` · Knee, Hip → `legs`, `impact`. Any region sets `injury_mode` on. Batch 3 confirms `upper` is in `INJURY_TAGS`, and adds it with a test if not. |
| **OB10 Picker starts** | Age 25 · Height 170 cm · Weight 70 kg · Bedtime 23:00, wake 06:30 (`N21`). Same start for every gender. Unit defaults: ft/lb if the locale's country is US, else cm/kg. |
| **OB11 Unit conversion** | 1 in = 2.54 cm · 1 lb = 0.45359237 kg. Canonical cm and kg are stored; display rounds. Toggling units twice must not change the stored value. |
| **OB12 Body map regions** | Shoulders → `SHOULDERS` · Back → `LOWER_BACK`, `MIDDLE_BACK` · Wrist → `FOREARMS` · Knee → `QUADRICEPS` · Hip → `GLUTES`. Visual only. |
| **OB13 Stage → Diet goal** | 1–2 gain · 3 maintain · 4–5 lose. A target (Q3) overrides it: lower than current weight is lose, higher is gain. |
| **OB14 Reminder** | Toggle on: `reminder_time` 07:00 (the existing default). Training reminder uses the same time. |
| **OB15 Plan ring** | 3,000 ms total, split evenly across the lines shown. |
| **OB16 Summary quest line** | From the dry run: quest count and each quest's title. |
| **OB17 Target guard** | Date at least 4 weeks away. Target weight inside `WEIGHT_KG_RANGE` and at or above BMI 18.5. |
| **OB18 Chart** | Monotone curve between two points only. Labelled "Estimate based on your target". No projection beyond the date. Never shown under 18. |

---

## 6. Components

All live in `core/designsystem/components`, because `FeatureBoundaryTest` bans drawing, clipping and literals in `feature/**`. New sizes go in `AwakenDimens`, new durations in `AwakenMotion`.

| Component | Behaviour | Accessibility |
|---|---|---|
| `OnboardingShell` | Back, logo, `SegmentedProgressBar`, optional Skip, heading (2 lines max), subtitle, content slot, pinned `PrimaryButton`, IME-aware | Heading is a semantics heading; focus moves to it on each step |
| `SegmentedProgressBar` | N segments, each 0..1, animated | One progress node: "Step x of y" |
| `WheelPicker<T>` | `LazyColumn` + `rememberSnapFlingBehavior`; centred highlight band; neighbours fade by distance; tick per value | Adjustable: announces value; TalkBack increase/decrease actions |
| `RulerPicker` | Horizontal or vertical `LazyRow`/`LazyColumn` of ticks, drawn with `Canvas`, major tick every 10 units; snaps; big value above; tick per value | Same as wheel |
| `UnitToggle` | Wraps existing `SegmentedControl` | Inherited |
| `BmiCard` | Value, scale with moving badge, one neutral line | Value and line as text; colour never carries meaning alone |
| `StageSlider` | 5-stop slider driving a crossfade between images | Slider semantics: "Stage 3 of 5" |
| `ImageChoiceCard` | Image, label, selected ring with `accentWidth`, single or multi | `Role.Checkbox` / `Role.RadioButton`, selected state |
| `ExpandingChoiceCard` | Choice card; selected one reveals a line | Line added to the description |
| `BodyMapGrid` | Region cells + existing `BodyMap` with the region lit | Cells are the interactive nodes; figure hidden from TalkBack |
| `DayChips` | Seven `ChoiceChip`s, today marked; ≥ 48dp | "Tuesday, today, selected" |
| `PlanRing` | `ProgressRing` + percent + rotating line | Live region announces each line; ring is decorative |
| `PlanSummaryCard` | Title, value, edit action | Edit action has a label |
| `GoalCurve` | ChartKit `ChartSpec.GoalCurve` | `description` required, as with other ChartKit specs |
| `OnbIllustration` | Resolves `onb_<step>_<variant>_<gender>`, falling back to `_n` | Decorative: the label is the description |

Illustrations are data: `feature/onboarding` holds a table of drawable ids, and `core/designsystem` only receives a `painter`.

---

## 7. Animation specs

| What | Spec | Reduce motion |
|---|---|---|
| Next | Slide left + fade, 280 ms, `standardEasing` (new `AwakenMotion.stepMs`) | Crossfade `quickMs` |
| Back | Reverse | Crossfade `quickMs` |
| Progress bar | 300 ms ease-out (`revealMs`) | Jumps |
| Wheel, ruler | Native fling + snap | Unchanged: the user drives it |
| BMI badge | `animateFloat` `quickMs` | Jumps |
| Stage image | Crossfade `standardMs` | Crossfade `quickMs` (unchanged in kind) |
| Selected card expand | `animateContentSize` `standardMs` | No animation |
| Plan ring | 0→100 linear over OB15; lines swap with a fade | Static ring; lines update in place, same duration so they can be read |
| Haptics | `tick()` on every selection and picker value; `confirm()` at Start | Unaffected; follows the Haptics toggle |

`duration()` returns 0 under reduce motion, so the crossfades above pass `quickMs` directly.

**The ring is honest:** the plan is computed first, and every line shows a value from that plan. Lines for skipped steps don't appear. The 3 s pacing is for reading, not simulated work, so the wording stays in the present tense of what is being set ("Setting your step goal: 8,000") and nothing claims analysis.

---

## 8. Copy and policy rules

- Plain second person, sentence case, via the Lexicon, and `LexiconResourcesTest` applies. No "hunter" or game terms in any string.
- None of: user counts, ratings, testimonials, paywall, medical claims, XP for answering.
- Body screens describe and never judge. No "ideal", "healthy weight", "overweight" or "obese" anywhere. Each body screen says it is optional through its Skip.
- Injury screen note: "This only changes which quests you see. It isn't medical advice."
- All copy is original. Nothing is lifted from the reference app.

---

## 9. Migration for existing users

- Existing users have a profile, so `LaunchState.Ready` skips onboarding. Nothing runs and nothing is asked.
- v28 columns are NULL or default for them: Diet prefill is blank, no training days, no reminder change.
- Settings gains Training days for everyone.
- There is no old draft to migrate, since the previous flow kept state in memory only.
- Old backups import through `ExportUpgrade` v27→28 with the same defaults.
- `DemoSeeder` and `CreateHunterUseCase` defaults keep working.

---

## 10. Tests

**`:engine` (JUnit5), new `OnboardingPlannerTest`, `BodyMathTest`:** every OB rule, including OB1 ties, fill and skip; OB2 clamps; OB4 and OB3 staying inside `N12` and `N20`; OB11 round trip for every value in range (no drift); OB7 at the boundaries (18.4, 18.5, 24.9, 25.0, 29.9, 30.0) and age gating; OB17 guards; sleep windows against `BedtimeRules.isValidWindow`. Add vectors to GD §12 with the numbers.

**`:app` unit:**
- `OnboardingViewModelTest`: Continue gating per step, Skip defaults, exclusive None, Back keeps answers, resume after a recreated ViewModel on every step, name can't be skipped, editing re-plans.
- `OnboardingDraftStoreTest`: round trip, corrupt draft, old schema.
- `CompleteOnboardingUseCaseTest`: all settings written, one transaction, usage-access refused path, failure leaves no profile, draft cleared, no ledger rows.
- `OnboardingMappingTest`: table-driven, every row of §4.
- `OnboardingPreviewMatchesTodayTest`: the dry run equals the first real directive.
- `MigrationTest` 27→28, export round trip, `ExportUpgrade`.
- Rewrite `OnboardingFlowTest` and `AiPathsHiddenTest.onboardingSkipsThePathStepWithoutAiPaths`; rewrite `AwakeningTest` for the new tiers.

**Compose tests:** `WheelPicker`, `RulerPicker` (snap, TalkBack actions), `ImageChoiceCard`, Reduce-motion transitions, full happy path and all-skipped path.

**Asset test:** `OnboardingAssetsTest` asserts every name in the illustration table resolves, for all three sets.

**Screenshots:** `@PhonePreviews` for every step in default, selected and error states, plus 200% font. Components via `@ComponentPreview`. Wheels and rulers must be tested at the small-phone height with the button pinned.

**Enforced already:** `FeatureBoundaryTest`, `TokenSourceTest`, `ScreenLayeringTest`, `LexiconResourcesTest`.

---

## 11. Batches (Part B)

Each batch: tests, previews, goldens named in the commit, full gate (`:engine:test :app:testDebugUnitTest :app:assembleDebug lint :app:verifyRoborazziDebug`), commit, **STOP**. Work on a branch `feat/onboarding`. The old flow stays live until Batch 4 switches it over, so every batch leaves the app shippable.

**Batch 1: shell + pickers.** Numbers to GD §17 · tokens (`buttonPrimary`, `stepMs`, dimens) · engine: `OnboardingAnswers`, enums, `BodyMath` · `OnboardingShell`, `SegmentedProgressBar`, `WheelPicker`, `RulerPicker`, `UnitToggle`, `BmiCard` · `OnboardingDraftStore` · placeholder art.

**Batch 2: steps 1–9.** New `OnboardingV2ViewModel`, step host behind a debug-only entry · `StageSlider`, `ImageChoiceCard` · screens 1–9, draft resume · OB1, OB13.

**Batch 3: steps 10–17.** `DayChips`, `BodyMapGrid`, `ExpandingChoiceCard` · screens 10–16 · permission screens 17a–c with resume re-check · OB2–OB6, OB8, OB9.

**Batch 4: plan, summary, wiring.** `OnboardingPlanner`, dry run · `PlanRing`, `PlanSummaryCard`, target sheet, `GoalCurve` · Room v28, export, Diet prefill, Settings row, training reminder, Gym line · new `CompleteOnboardingUseCase` · switch `AwakenNavHost`, delete the old `OnboardingScreen` and `Awakening.Answer` · docs: DS §4.1, PRD F1, ARCH §3, final mapping in this file's §4 · `PHONE_TEST_onboarding.md`.

---

## 12. Risks

- **Screen count vs drop-off.** A 22-screen flow is long for a free app with nothing behind a paywall. Skip is the mitigation. Worth watching in your own phone test.
- **Asset load.** 46 images at 40–60 KB is about 2.5 MB. The list in `ASSET_LIST.md` is already trimmed by using gendered art only where a body is shown.
- **Sensitive data.** Age, height, weight and body stage sit on the device and in exports. Covered by §3.4. An export file carries these values, so sharing one shares them.
- **Bedtime defaults on.** Phone-down nights creates a daily quest the user might not expect. It is listed on Your plan with an off switch.
- **Wheel and ruler performance** on low-end minSdk 28 phones. If `LazyColumn` snapping stutters, the fallback is a `Canvas` picker driven by `Animatable` decay, behind the same component API.
