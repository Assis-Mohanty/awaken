# AWAKEN — Declutter plan (Part A, 2026-11-03)

> Goal: a calm, serious personal-growth app. The game layer that stays is **XP, levels, ranks E–S, gold, the five stats
> (STR INT WIL VIT CHA) with their radar, streaks, and a store of useful items**. Everything else that is game flavour or
> clutter goes. No visual redesign in this work; Batch 4 only prepares for it.
>
> Status: **PLAN — awaiting decisions on the ASK ME items (§0).** Nothing in the code has changed yet.
> Builds after approval follow this file exactly (Part B, Batches 1–4). Anything unclear goes into `docs/DECLUTTER_QUESTIONS.md`.

Legend: **REMOVE** · **RENAME** (plain words, same function) · **KEEP** · **ASK ME** (your call, my recommendation given).
"Data" = Room tables/columns, ledger, export. Ledger rows are **never** edited or deleted; removed features only stop
writing new rows.

---

## 0. ASK ME — decisions needed before Batch 1

| # | Item | Options | My recommendation |
|---|---|---|---|
| Q0 | **Branch.** `fix/release-prep` has uncommitted release-prep work (AI Paths compiled out of release, 20+ files). `chore/declutter` would carry it along. | a) commit release-prep on its branch first, then branch `chore/declutter` from it · b) branch now and carry it | **a** — keeps the declutter commits clean and reviewable. |
| Q1 | **Critical hits** ("Bonus" XP, GD §4.2). Not on your keep list; a variable reward, and the main source of shards. | REMOVE (XP is always the formula, no ×1.5/×2 rolls; §4.2 and its §12 vectors go) · KEEP as "Bonus XP" without shards | **REMOVE.** Calm, explainable XP; the reward sheet shows one formula line. |
| Q2 | **45-day Campaign** (scan, archetypes "The Rebuilder"…, oath sealed by long press, acts Foundation/Build/Ascend, Reforged +1,000 gold). | REMOVE · KEEP stripped ("45-day plan", no archetype/oath/acts/Reforged) | **REMOVE.** Programs (30/66/75) already cover a fixed-length plan; the two are mutually exclusive anyway. Active campaign habits become ordinary habits (kept, editable). |
| Q3 | **Dungeons** (3/5/7-day Hard stages, gold entry fee, 1.5× reward + shard). | REMOVE · KEEP as "Challenge" (fee and reward stay, shard dropped) | **REMOVE.** It is a gold wager on top of the Rank-up test and Programs. An active dungeon at upgrade time: its open stage quests expire normally; the fee stays spent (no ledger edit). |
| Q4 | **Programs** names (Steady 30, Groundwork 66, Ironclad 75). | KEEP names · RENAME to "30-day program", "66-day program", "75-day program" | **RENAME** (plain). Rules unchanged. |
| Q5 | **Gate crystal + gallery** (crystal grows during a session; finished runs fill a gallery). | REMOVE · KEEP | **REMOVE.** The running session shows the existing plain progress dial; the gallery screen goes. No data (derived from `gate_run`). |
| Q6 | **Lumi** (Reel Counter mascot, 6 stages; never approved for release, GD §15). | REMOVE · KEEP | **REMOVE.** Reels show as a plain count vs the limit (bubble, tile, widget). |
| Q7 | **Level-up ceremony / rank-up cinematic** (full-screen, number animation, chips). | REMOVE (a line in the reward sheet: "Level 12 reached", one haptic) · SHORTEN (≤ 1 s sheet) | **REMOVE.** Rank-up: one plain sheet "You reached rank D" after the Rank-up test, no animation. |
| Q8 | **Wind-down** (2-minute breathing guide from the phone-down card). | KEEP · REMOVE | **KEEP.** It is a tool, not decoration; reached from the phone-down row only. |
| Q9 | **Monthly/yearly recaps** (3–5 "story cards", share image). You listed "story cards" for removal. | REMOVE · KEEP as a plain monthly summary list | **REMOVE.** The weekly report, heatmap and insights cover it. `recap` table dropped. |
| Q10 | **Code identifiers** (`Hunter*`, `Gate*`, `Directive*`, tables `hunter_profile`, `gate_run`). | User-visible words only · also rename code + tables | **Visible words only.** Renaming tables changes the export format for no user benefit; code renames can be a later chore. Docs say "profile" / "focus session". |
| Q11 | **Cosmetics already bought** (themes, frames, badges). | Gone, gold stays spent · refund with new ledger rows | **No refund** (closed-test users only; never editing ledger rows either way). |
| Q12 | **Quest titles with numbers.** You asked for "Walk 6,000 steps". GD §6.2 forbids numbers in titles today (the target line shows them). | Number in the title (template `title` becomes a pattern rendered with the target; the row's separate target line goes) · keep titles number-free | **Number in the title**, one title per template (no random variants), rendered length ≤ **44** chars at the Extreme target (today: 40 without numbers). Approve 44 or give another number. |
| Q13 | **Today order.** You proposed Header → Plan → Focus → Health & screen → Quit. | as proposed · alternative | **As proposed**, with one change: **Phone-down night and the app-limit (Discipline) quest become rows in Today's plan** (they *are* quests that count toward the day), so tonight's phone-down row sits in the Evening group near the top, never at the bottom. Section 4 then holds steps, sleep, screen time and reels only. |
| Q14 | Your examples **"Tower ascent", "Measured air", "Arch of power", "Gate breaker"** are not in the current source ("Arch of Power" was fixed earlier; "Gatebreaker" is an achievement name). If your phone still shows them it runs an older build, or they came from an AI Path plan. | — | No action beyond this plan; Batch 2 adds a test that every template title passes the banned-word list *and* the new pattern rule. |

---

## 1. Inventory — every game / flavour element

Paths are relative to `app/src/main/java/com/assismohanty/awaken/` unless they start with `engine/`, `docs/` or `app/src/main/res|assets`.

### 1.1 Achievements and titles → REMOVE (Batch 1)

| Element | Where | Data impact |
|---|---|---|
| 52 achievements (GD §10, §14.8, §16.16), gold 10–1,000 | `engine/…/AchievementEvaluator.kt`, `core/domain/AchievementUnlocker.kt`; called from `CompleteQuestUseCase`, `CompletionCenter`, `DayRolloverUseCase`, `UndoCompletionUseCase`, `CompleteOnboardingUseCase`, `CampaignUseCases`, `DungeonUseCases`, `ProgramUseCases`, `QuitUseCases`, `GymTracker`, `WeeklyReviewUseCases`; `GameStateReader` | table `achievement_unlock` **dropped**; past `ACHIEVEMENT` gold rows stay; `SourceType.ACHIEVEMENT` stays in the enum so old rows and exports still read |
| Achievement toast "Achievement unlocked · +50 gold" | `feature/quest/RewardHost.kt` (`queueAchievements`, `CompletionEvent.Achievements`), `SystemToast` | — |
| Status "Titles & achievements" window, equip/unequip title | `feature/status/StatusScreen.kt` (`AchievementsWindow`, `AchievementCell`), `StatusViewModel`, `StatusUseCases` | column `hunter_profile.equipped_title_id` **dropped** |
| Title under the name ("Rising Hunter", rank titles "D-Rank Hunter"…) | `HunterHeader.kt`, `StatusScreen` header, `titleNameRes` | as above |
| Strings `achievement_*`, `condition_*`, `title_*`, `status_window_achievements`, `status_title_*`, `action_(un)equip_title`, `system_title_achievement`, `system_title_title_unlocked`, `recap_achievements`, `quit_back_dated_note` (rewrite: no "Achievements count from today") | `strings_play.xml`, `strings_clean.xml`, `strings_quit.xml` | — |
| Rank-up test reward "rank title + 500 gold" | `trial_rule` string, GD §5 | 500 gold **stays**; title dropped |
| Focus-streak / PR / quit / program / review / dungeon achievements | GD §14.8, §16.16 | the features stay; they simply grant nothing (they were "achievements only") |
| Tests | `DaysAndTrialsTest`, `StatusUseCasesTest`, `UseCasesTest`, `OnboardingFlowTest`, `StatusScreenTest`, `TodayMappingTest`, engine `RulesTest`, `GrowthVectorsTest`, `FocusGoalRulesTest`, `GymProgressionTest`, `LongArcRulesTest`, `QuitRulesTest`, `CampaignTest` | achievement assertions deleted; §12/§14.10/§16.17 achievement vectors deleted |

### 1.2 Store junk → REMOVE (Batch 1). Store list in §2.

| Element | Where | Data impact |
|---|---|---|
| Themes Crimson Gate / Abyssal Violet / Sovereign Gold, theme tint | `engine/…/ShopRules.kt`, `core/designsystem/theme/Color.kt` (`theme(themeId)`), `Theme.kt` (`LocalThemeTint`), `AwakenTheme.rankAccent` tint | column `hunter_profile.theme_id` **dropped** |
| Title frames ×4 (iron, ember, umbral, regal) | `ShopRules`, `VaultScreen`, `HunterHeader`, `StatusViewModel` (`frameId`) | `equipped_frame_id` **dropped** |
| Badges/sigils ×8 (blade, flame, eye, wolf, crown, hourglass, star, portal) | `ShopRules`, `VaultScreen`, `Glyphs.kt` sigil glyphs, `StatusViewModel` (`sigilId`) | `equipped_sigil_id` **dropped**; table `owned_cosmetic` **dropped** |
| Relic Cache ("Reward chest"), open 10 shards | `VaultUseCases.OpenRelicCacheUseCase`, `ShopRules.openCache/cachePool`, `VaultScreen` cache window | past `CACHE_OPEN` / `ITEM` rows stay; `SourceType.CACHE_OPEN` stays in the enum |
| Loot fragments ("shards") from crits, focus sessions ≥ 25 min, dungeon clears | `XpCalculator`, `FocusRules` (P4), `GateUseCases`, `CompleteQuestUseCase`, `DungeonRules`, `LedgerMath`, `RewardSheet` (`fragment_gain`), `UserMessages`, Debug screen fragment tools, `DemoSeeder` | no new `FRAGMENT` rows; `LedgerKind.FRAGMENT` stays for history; balance cache stops tracking it (rebuildable) |
| `ShopKind.THEME/FRAME/SIGIL`, `EquipCosmeticUseCase` | `ShopRules`, `VaultUseCases` | — |
| Tests | `ShopRulesTest`, `VaultUseCasesTest`, `VaultScreenTest`, `GateUseCasesTest`, `LongArcUseCasesTest`, `GameDataPorterTest` | rewritten for the two-item store |

### 1.3 Story / narrative → REMOVE (Batch 1)

| Element | Where | Data impact |
|---|---|---|
| 6 chapters ("The Window", "The First Gate", "The Sealed Door"…) + full-screen reveal | `assets/narrative/chapters.json`, `core/data/repo/NarrativeRepository.kt`, `ChapterReveal.kt`, `TypeOnText.kt`, `RewardHost` (`newlyUnlockedChapters`), Status `ChaptersWindow` | none (derived from level) |
| Daily System line (~60 lines: "The System is watching…") shown as the Today insight card | `assets/narrative/system_lines.json`, `engine/…/SystemLines.kt`, `ObserveTodayUseCase` (`systemLine`), `TodayScreen` `InsightCard` | none |
| Campaign story: archetypes, oath, acts, Reforged | see Q2 | `campaign` table, `habit.campaign_id` (if Q2 = REMOVE) |
| "SYSTEM WARNING" full-screen penalty moment + red home tint | `SystemWarning.kt`, `RewardHost` (`Notice.PenaltyWarning`), Today tint | — → replaced by the Comeback row (§4) |
| "Session collapsed" warning dialog | `RewardHost` (`CompletionEvent.GateCollapsed`) | — → plain snackbar "Session ended early" |
| Uppercase System titles (`SETTINGS`, `YOU HAVE BEEN CHOSEN.`, `QUEST CLEARED`, `RANK TRIAL · D`…) | every `strings*.xml` System variant (254 twins) | — (Batch 2: the plain twin becomes the only string) |
| `GateBackground` rank glow behind screens (incl. Settings, Health, Screen time) | 20 files (list in §6 Batch 4) | — (Batch 4: neutral background, same colour, no glow) |
| Recaps "story cards" | see Q9 | `recap` table |
| Today greeting "Good morning, Assis" | `TodayScreen.greeting` | — → date as title (§4) |
| Level-up ceremony / rank-up cinematic | see Q7; `LevelUpCeremony.kt`, `RankCrest.kt` (crest stays as a small badge) | — |
| PRD tagline "Real-life Hunter System", pitch "The System knows your goal" | `docs/PRD_MVP.md`, `docs/PLAY_STORE_LAUNCH.md` listing copy | docs only (Batch 4 docs pass) |

### 1.4 Identity framing → REMOVE (Batch 2)

| Element | Where | Data impact |
|---|---|---|
| "Who are you becoming?" onboarding step (12 "Someone who…") | `OnboardingViewModel` (`OnboardingStep.IDENTITY`), `OnboardingScreen`, `CompleteOnboardingUseCase` | column `hunter_profile.identities` **dropped** |
| Status line "Someone who trains · reads" | `StatusUseCases`, `StatusScreen`, `Identities.kt` component | as above |
| Settings "Identities" row | `SettingsScreen` `ProfileWindow`, `SettingsViewModel`, `SettingsUseCases` | — |
| Weekly review line "Did you act like someone who…?" | `review_identity_prompt`, `WeeklyReviewUseCases` | review rows keep their other fields |
| Assets / engine | `assets/identities.json`, `core/data/repo/IdentityRepository.kt`, `engine/…/Identities.kt`, `ContentAssetsTest`, `GrowthVectorsTest` (§14.7 vectors), `MigrationTest` | — |
| Focus-stat pre-selection from identities | `OnboardingViewModel` | focus step starts with nothing selected (as before v1.1) |

### 1.5 Renames — one plain vocabulary (Batch 2)

The app already shows the Clean words (System mode was dropped in the v2 redesign). Batch 2 makes the Clean text **the
only text**: every `<name>` gets its `<name>_clean` value, `strings_clean.xml`, `LexiconPairs.kt`, `PresentationMode`,
`Lexicon.System`, the `Term` enum's System column and the `_clean` rule in `LexiconResourcesTest` are deleted.
`lexString`/`lexPlural`/`LexResources` stay as thin wrappers (so `FeatureBoundaryTest` keeps enforcing one entry
point) — or are replaced by `stringResource` with the test updated; **I keep the wrappers** (smaller diff).

| Game term | Shown today (Clean) | New plain word | Notes |
|---|---|---|---|
| Hunter | Profile | **you / Profile** | onboarding "What should we call you?"; no "hunter" in any string |
| Gate / Gates | Focus session(s) | **Focus session(s)** | |
| Gate collapsed / Breach | Session ended early / Time away | same | |
| Dungeon | Challenge | **Challenge** (only if Q3 = KEEP) | |
| Daily Directive / Directive cleared | Today's plan / Plan complete | **Today's plan / Plan done** | |
| Penalty Zone / Penalty quest | Comeback / Comeback quest | **Comeback / Comeback quest** | |
| Rank Trial | Promotion challenge | **Rank-up test** | `notice_trial_failed_*`: "Rank-up test closed. You can try again in 48 hours." |
| Relic Cache / Loot Fragments / Sigil | Reward chest / Shards / Badge | — | removed (§1.2) |
| System message | Daily focus | — | removed (§1.3) |
| Awakening | Getting started | **Getting started** | |
| Vault | Shop | **Store** | |
| Status | Progress | **Progress** | bottom-bar label already "Progress"? (Batch 2 checks `nav_status`) |
| Chapter / Oath | Story / Commitment | — | removed |
| Streak Shield | Streak freeze | **Streak freeze** | |
| Crit | Bonus | — / "Bonus XP" | per Q1 |
| Recalibrated | Adjusted | **Difficulty adjusted** | |
| Oracle | AI planner | **AI planner** | debug/internal builds only |
| "The System …" in base strings with no twin | e.g. `msg_error` "The System hit an error" | "Something went wrong. Try again." | full sweep: every string matching `System|hunter|Gate|directive|trial|oath|realm…` reviewed; list kept in the Batch 2 commit |
| Paths copy "chart another", "milestone ladder" | `strings_paths.xml` | "create another", "milestones" | |
| Program names | Steady 30 / Groundwork 66 / Ironclad 75 | per Q4 | |
| Notification channels / widget text | `LexiconSource`, `TodayWidget`, `GateNotifications` | same plain words | |

### 1.6 Quest titles → RENAME (Batch 2). Full list in §3.

`assets/quests/templates.json`: `titles: [...]` (1–3 variants, no numbers) becomes one `title` pattern containing
`{n}`. `DirectiveGenerator` renders it with the instance's target at generation (stored in `quest_instance.title` as
today; old instances keep their old titles). `TemplateLibrary.validate()` changes: exactly one `{n}`, banned words
still banned, rendered ≤ 44 chars at the Extreme target (Q12), sentence case. `TemplateTitleTest`, `ContentAssetsTest`
updated. GD §6.2 rewritten. The quest row drops its separate target line where the title already carries it (quest
detail still shows target and progress).

### 1.7 Everything else found

| Element | Where | Action | Plain replacement / note |
|---|---|---|---|
| XP, levels, level curve, verification multipliers, daily caps | engine | **KEEP** | |
| Ranks E–S and rank colours | engine `RankRules`, tokens `rankE…rankS` | **KEEP** | |
| Rank Trial (3 Hard stages, 500 gold) | `TrialUseCases`, `feature/trial`, `TodayTrial.kt` | **RENAME** → Rank-up test | |
| Gold faucets: quest ×0.5, day cleared 20, Rank-up test 500 | engine | **KEEP** | achievement gold and dungeon reward end |
| Streak, rest day, recovery mode, streak freeze | engine `StreakRules` | **KEEP** | |
| Comeback (penalty) quest | engine, `penalty_*` templates | **KEEP + RENAME** | shown as a calm row, no full-screen warning |
| "Never miss twice" line, consistency "22 of last 28 days" | `ObserveTodayUseCase`, `Consistency.kt` | **KEEP, moved** | miss line merges into the Comeback row; consistency moves to Progress (streak card) |
| Critical hits | `CritRoller`, `XpCalculator` | **ASK ME** (Q1) | |
| Adaptive difficulty | `DifficultyAdapter` | **KEEP** | notice copy plain |
| Stats + radar + 18-week heatmap + weekly report + share card | Status | **KEEP** | share card loses the rank crest flourish only if it reads as decoration (Batch 4 check) |
| Personal insights | `InsightEngine` | **KEEP** | |
| Weekly review | `feature/review` | **KEEP** (minus identity line) | |
| Recaps | `feature/recap`, `RecapRules`, `Recap.kt` | **ASK ME** (Q9) | |
| Campaign | `feature/campaign`, `engine/campaign` | **ASK ME** (Q2) | |
| Programs | `feature/program`, `ProgramRules` | **KEEP**, names per Q4 | |
| Dungeons | `feature/dungeon`, `DungeonRules` | **ASK ME** (Q3) | |
| Focus sessions: presets, custom, stopwatch, Pomodoro, subjects, focus goal + focus streak, Focus Lock, Quick Settings tile, Start-focus widget | `feature/gate`, `feature/focus`, `feature/focuslock`, `core/widget` | **KEEP** | preset rank letters (E/D/C/B) are not shown as names; keep lengths only |
| Gate crystal + gallery | `GateCrystal.kt`, `CrystalRules`, `CrystalGallery*` | **ASK ME** (Q5) | |
| Level-up ceremony | `LevelUpCeremony.kt` | **ASK ME** (Q7) | |
| Reward sheet with formula breakdown | `RewardSheet.kt` | **KEEP** | loses crit/shard lines |
| Health, Gym, Diet, Screen time, pickups, step goal ring, phone-down nights, quit counters, Reel Counter | features | **KEEP** | |
| Lumi | `LumiView.kt`, `reelcounter-core/…/lumi` | **ASK ME** (Q6) | |
| Wind-down | `WindDownScreen.kt` | **ASK ME** (Q8) | |
| AI Paths | `feature/paths` (debug/internal only) | **KEEP** | plain copy only |
| Sounds (timer chime), haptics | `TimerChime`, `Haptics` | **KEEP** | |
| `SystemWindow`, `SystemToast` component names | `core/designsystem/components` | **KEEP** names (Q10), plain titles | |
| Bottom bar Today · Gym · Diet · Progress | `TopLevelDestination` | **KEEP** | |
| Today header icons (Focus "portal", Store "vault") | `TodayScreen` | **REMOVE** | Focus has its own section; Store opens from the gold figure on Progress |
| Debug screen: fragment/cosmetic/achievement tools, `DemoSeeder` | `app/src/debug/…` | **REMOVE** those tools | ledger viewer, time travel, rebuild balances stay |

---

## 2. Store — every item

| Item | Price | Action | Reason |
|---|---|---|---|
| Streak freeze (was Streak Shield) | 300 gold, hold ≤ 2 | **KEEP** | protects the streak |
| Extra reroll | 40 gold, 3/day after the free one | **KEEP** | bought from the quest's Reroll button; the Store lists it with that note (no inventory) |
| Theme: Crimson Gate | 1,500 | REMOVE | cosmetic |
| Theme: Abyssal Violet | 1,500 | REMOVE | cosmetic |
| Theme: Sovereign Gold (rank S) | 3,000 | REMOVE | cosmetic |
| Frame: Iron / Ember / Umbral / Regal | 600 / 800 / 1,000 / 1,200 | REMOVE | cosmetic |
| Badges: Blade, Flame, Eye, Wolf, Crown, Hourglass, Star, Portal | 400 each | REMOVE | cosmetic |
| Relic Cache (10 shards) | — | REMOVE | loot box |
| Dungeon entry fee (not a Store item) | 100/200/300 | per Q3 | |

**What remains:** Streak freeze and Extra reroll. Store stays a small screen opened from the gold amount on Progress.
Gold then has two sinks; that is fine for now (more sinks → `docs/BACKLOG.md`, not built).

---

## 3. Quest templates — every title (Batch 2)

Rules: `{n}` is the target with thousands separators; the word after it agrees with `{n}` (1 page / 20 pages,
1 person / 2 people); minutes are written "minutes"; for `sec` targets `{n} seconds` becomes "30 seconds",
"1 minute", "2 minutes", "90 seconds" (whole minutes when divisible by 60). Example renders use the Normal target.

| id | Current titles | New title pattern | Normal render |
|---|---|---|---|
| str_pushups | Do push-ups · Practise push-ups | Do {n} push-ups | Do 25 push-ups |
| str_squats | Do bodyweight squats · Do squats | Do {n} squats | Do 40 squats |
| str_plank | Hold a plank · Do plank holds | Hold a plank for {n} seconds | Hold a plank for 1 minute |
| str_lunges | Do lunges · Do alternating lunges | Do {n} lunges | Do 20 lunges |
| str_run | Go for a run · Run or jog | Run {n} km | Run 3 km |
| str_intervals | Do run/walk intervals · Run fast, walk easy | Do {n} minutes of run/walk intervals | Do 15 minutes of run/walk intervals |
| str_burpees | Do burpees | Do {n} burpees | Do 20 burpees |
| str_jumping_jacks | Do jumping jacks | Do {n} jumping jacks | Do 100 jumping jacks |
| str_pullups | Do pull-ups · Do pull-ups or chin-ups | Do {n} pull-ups | Do 8 pull-ups |
| str_dumbbell_rows | Do dumbbell rows | Do {n} dumbbell rows | Do 40 dumbbell rows |
| str_dumbbell_press | Do dumbbell presses · Press the dumbbells | Do {n} dumbbell presses | Do 40 dumbbell presses |
| str_goblet_squats | Do goblet squats | Do {n} goblet squats | Do 30 goblet squats |
| str_kettlebell_swings | Do kettlebell swings · Swing a kettlebell | Do {n} kettlebell swings | Do 60 kettlebell swings |
| str_band_pullaparts | Do band pull-aparts | Do {n} band pull-aparts | Do 60 band pull-aparts |
| str_jump_rope | Skip rope · Jump rope | Skip rope for {n} minutes | Skip rope for 6 minutes |
| str_gym_session | Train at the gym · Go to the gym | Train at the gym for {n} minutes | Train at the gym for 45 minutes |
| str_cycle | Go cycling · Ride your bike | Cycle {n} km | Cycle 10 km |
| str_stair_climb | Climb the stairs · Take the stairs | Climb {n} floors of stairs | Climb 10 floors of stairs |
| str_wall_sit | Hold a wall sit · Do a wall sit | Hold a wall sit for {n} seconds | Hold a wall sit for 1 minute |
| str_glute_bridges | Do glute bridges | Do {n} glute bridges | Do 40 glute bridges |
| str_pike_pushups | Do pike push-ups | Do {n} pike push-ups | Do 10 pike push-ups |
| str_chair_dips | Do chair dips · Do bench dips | Do {n} chair dips | Do 15 chair dips |
| str_superman | Do superman raises | Do {n} superman raises | Do 20 superman raises |
| str_mountain_climbers | Do mountain climbers | Do {n} mountain climbers | Do 40 mountain climbers |
| str_hike | Go for a hike · Take a brisk walk outside | Hike or walk briskly for {n} minutes | Hike or walk briskly for 60 minutes |
| str_steps | Walk your steps · Get your steps in | Walk {n} steps | Walk 7,000 steps |
| str_exercise_session | Log a workout · Record an exercise session | Work out for {n} minutes | Work out for 30 minutes |
| str_circuit | Do a bodyweight circuit · Do circuit rounds | Do {n} bodyweight circuit rounds | Do 2 bodyweight circuit rounds |
| str_sprints | Run short sprints · Do sprint repeats | Run {n} short sprints | Run 6 short sprints |
| str_dance | Dance it out · Put on music and dance | Dance for {n} minutes | Dance for 20 minutes |
| str_active_burn | Burn active calories · Move to burn calories | Burn {n} active kcal | Burn 300 active kcal |
| int_read_pages | Read a book · Read some pages | Read {n} pages | Read 20 pages |
| int_deep_work | Do deep work · Focus on your key work | Do {n} minutes of deep work | Do 25 minutes of deep work |
| int_practice_problems | Solve practice problems · Work through problems | Solve {n} practice problems | Solve 2 practice problems |
| int_flashcards | Review flashcards · Study your flashcards | Review {n} flashcards | Review 50 flashcards |
| int_language | Practise a language · Study your new language | Practise a language for {n} minutes | Practise a language for 20 minutes |
| int_study_block | Study for a course · Study for your exam | Study for {n} minutes | Study for 25 minutes |
| int_learning_summary | Sum up what you learned · Write a learning summary | Write a {n}-word learning summary | Write a 200-word learning summary |
| int_course_lessons | Complete course lessons · Do your course lessons | Complete {n} course lessons | Complete 2 course lessons |
| int_lecture | Watch a lecture · Watch an educational talk | Watch a lecture for {n} minutes | Watch a lecture for 30 minutes |
| int_teach_back | Explain it like a teacher · Teach a concept back | Explain a concept aloud for {n} minutes | Explain a concept aloud for 10 minutes |
| int_build_session | Build your project · Work on a project | Work on your project for {n} minutes | Work on your project for 25 minutes |
| int_mental_math | Do mental math · Solve sums in your head | Solve {n} sums in your head | Solve 20 sums in your head |
| int_chess_puzzles | Solve chess puzzles · Solve logic puzzles | Solve {n} chess puzzles | Solve 10 chess puzzles |
| int_vocabulary | Learn new words · Grow your vocabulary | Learn {n} new words | Learn 10 new words |
| int_research | Research a question · Answer a question you have | Research a question for {n} minutes | Research a question for 25 minutes |
| int_review_notes | Review your notes · Go over your notes | Review your notes for {n} minutes | Review your notes for 20 minutes |
| int_podcast | Listen to a learning podcast · Listen to an audiobook | Listen to a learning podcast for {n} minutes | Listen to a learning podcast for 30 minutes (45 chars → shortened to "Listen and learn for {n} minutes") |
| int_til | Note what you learned today · List today's new facts | Write down {n} things you learned | Write down 2 things you learned |
| int_memorize | Memorise a list · Practise recall | Memorise {n} items | Memorise 10 items |
| int_writing_session | Do a writing session · Write an essay or story | Write for {n} minutes | Write for 25 minutes |
| int_skill_practice | Practise a skill · Practise an instrument or craft | Practise a skill for {n} minutes | Practise a skill for 20 minutes |
| int_mock_test | Take a practice test · Answer timed test questions | Answer {n} practice-test questions | Answer 10 practice-test questions |
| int_longform | Read long-form articles · Read a long article | Read {n} long articles | Read 2 long articles |
| int_mind_map | Make a mind map · Map out a topic | Make a mind map of {n} ideas | Make a mind map of 20 ideas |
| int_code_kata | Solve coding exercises · Do a coding kata | Solve {n} coding exercises | Solve 2 coding exercises |
| int_typing | Practise typing · Do typing drills | Practise typing for {n} minutes | Practise typing for 10 minutes |
| int_self_quiz | Quiz yourself · Write and answer quiz questions | Answer {n} quiz questions | Answer 10 quiz questions |
| int_logic_grid | Solve sudoku puzzles · Solve a logic grid | Solve {n} sudoku puzzles | Solve 2 sudoku puzzles |
| int_reading_gate | Read with no distractions · Read with other apps closed | Read without distractions for {n} minutes | Read without distractions for 25 minutes |
| int_new_topic | Explore a new topic · Learn about something new | Learn {n} facts on a new topic | Learn 5 facts on a new topic |
| wil_meditate | Sit and meditate · Do a meditation session | Meditate for {n} minutes | Meditate for 10 minutes |
| wil_phone_free | Put your phone away · Spend time phone-free | Spend {n} minutes phone-free | Spend 60 minutes phone-free |
| wil_cold_shower | Finish your shower cold · Take a cold rinse | End your shower with {n} seconds cold | End your shower with 1 minute cold |
| wil_breathwork | Do slow breathing · Breathe slowly | Breathe slowly for {n} minutes | Breathe slowly for 5 minutes |
| wil_journal | Write in your journal · Journal about today | Journal {n} words | Journal 150 words |
| wil_no_sugar | Skip added sugar · Go without added sugar | No added sugar for {n} hours | No added sugar for 8 hours |
| wil_no_social | Stay off social media · Skip social media | No social media for {n} hours | No social media for 4 hours |
| wil_hardest_first | Do your hardest task first · Start the task you avoid | Do your hardest task first for {n} minutes | (45 chars → "Start your hardest task for {n} minutes") |
| wil_digital_sunset | Put screens away before bed · Switch off screens early | No screens for {n} minutes before bed | No screens for 30 minutes before bed |
| wil_gratitude | List what you're grateful for · Write a gratitude list | List {n} things you're grateful for | List 5 things you're grateful for |
| wil_silence | Sit with no input · Think in silence | Sit in silence for {n} minutes | Sit in silence for 20 minutes |
| wil_declutter | Declutter your space · Clear out clutter | Clear out {n} items | Clear out 20 items |
| wil_single_task | Work on one task only · Single-task with no switching | Work on one task for {n} minutes | Work on one task for 25 minutes |
| wil_silent_walk | Walk without your phone · Take a quiet walk | Walk without your phone for {n} minutes | Walk without your phone for 20 minutes |
| wil_plan_tomorrow | Plan tomorrow's tasks · Prioritise tomorrow's tasks | Plan tomorrow's top {n} tasks | Plan tomorrow's top 5 tasks |
| wil_phone_free_morning | Delay your phone after waking · Wake up without your phone | No phone for {n} minutes after waking | No phone for 30 minutes after waking |
| wil_body_scan | Do a body scan | Do a {n}-minute body scan | Do a 10-minute body scan |
| wil_chores | Do overdue chores · Tackle put-off chores | Do overdue chores for {n} minutes | Do overdue chores for 20 minutes |
| wil_no_complaints | Go without complaining · Skip the complaints | No complaining for {n} hours | No complaining for 4 hours |
| wil_reflect_mistake | Reflect on a mistake · Write about a mistake | Write {n} words about a mistake | Write 100 words about a mistake |
| wil_spending_review | Review your spending · Check where money went | Review your spending for {n} minutes | Review your spending for 10 minutes |
| wil_urge_log | Write down your urges · Notice and log urges | Write down {n} urges | Write down 5 urges |
| wil_morning_routine | Do your morning routine · Follow your morning routine | Do {n} steps of your morning routine | Do 5 steps of your morning routine |
| wil_news_fast | Skip the news · Take a break from news | No news for {n} hours | No news for 8 hours |
| wil_visualize | Picture tomorrow going well · Visualise a hard moment | Picture tomorrow going well for {n} minutes | (45 chars → "Visualise tomorrow for {n} minutes") |
| wil_close_loops | Finish half-done tasks · Close open loops | Finish {n} half-done tasks | Finish 2 half-done tasks |
| wil_candle_focus | Hold your gaze steady · Practise a fixed gaze | Hold a steady gaze for {n} minutes | Hold a steady gaze for 5 minutes |
| wil_promise_kept | Keep a promise to yourself · Keep your own promises | Keep {n} promises to yourself | Keep 2 promises to yourself |
| wil_inbox_purge | Clear your inbox · Answer waiting messages | Clear {n} inbox messages | Clear 25 inbox messages |
| wil_screen_free_meals | Eat without a screen · Have a screen-free meal | Eat {n} meals without a screen | Eat 2 meals without a screen |
| vit_water | Drink enough water · Drink glasses of water | Drink {n} glasses of water | Drink 8 glasses of water |
| vit_sleep | Get a full night's sleep · Sleep a full night | Sleep {n} hours | Sleep 7 hours |
| vit_stretch | Do a stretch session · Stretch your body | Stretch for {n} minutes | Stretch for 10 minutes |
| vit_cook | Cook at home · Cook a meal from scratch | Cook {n} meals at home | Cook 2 meals at home |
| vit_vegetables | Eat vegetables or fruit · Eat your vegetables | Eat {n} servings of fruit or veg | Eat 3 servings of fruit or veg |
| vit_walk | Take an easy walk · Go for a walk | Walk for {n} minutes | Walk for 20 minutes |
| vit_steps | Reach your step goal · Hit your step count | Reach {n} steps | Reach 7,000 steps |
| vit_yoga | Do some yoga · Follow a yoga video | Do {n} minutes of yoga | Do 20 minutes of yoga |
| vit_active_session | Log an active session · Record some light exercise | Do {n} minutes of light exercise | Do 30 minutes of light exercise |
| vit_distance | Cover distance on foot · Walk or run your distance | Walk or run {n} km | Walk or run 4 km |
| vit_sunlight | Get outside in daylight · Spend time in daylight | Spend {n} minutes in daylight | Spend 15 minutes in daylight |
| vit_caffeine_cutoff | Cut caffeine before bed · Stop caffeine early | No caffeine {n} hours before bed | No caffeine 8 hours before bed |
| vit_protein | Eat protein-rich food · Get enough protein | Eat {n} servings of protein | Eat 3 servings of protein |
| vit_wind_down | Wind down before bed · Dim the lights and relax | Wind down for {n} minutes before bed | Wind down for 20 minutes before bed |
| vit_eye_breaks | Take eye breaks · Rest your eyes | Take {n} eye breaks | Take 6 eye breaks |
| vit_meal_prep | Prep meals ahead · Prepare healthy portions | Prep {n} healthy portions | Prep 4 healthy portions |
| vit_mobility | Do mobility drills · Loosen your joints | Do mobility drills for {n} minutes | Do mobility drills for 10 minutes |
| vit_stand_breaks | Take standing breaks · Stand up and move | Take {n} standing breaks | Take 6 standing breaks |
| vit_clean_meals | Eat unprocessed meals · Skip fried and junk food | Eat {n} unprocessed meals | Eat 2 unprocessed meals |
| vit_neck_release | Stretch your neck and shoulders · Release neck tension | Stretch neck and shoulders for {n} minutes | Stretch neck and shoulders for 5 minutes |
| vit_easy_ride | Take an easy bike ride · Cycle at an easy pace | Ride a bike easy for {n} minutes | Ride a bike easy for 30 minutes |
| vit_post_meal_walk | Walk after meals · Take a walk after eating | Walk after {n} meals | Walk after 2 meals |
| vit_legs_up | Put your legs up · Rest legs up a wall | Rest legs up a wall for {n} minutes | Rest legs up a wall for 5 minutes |
| vit_fresh_air | Air out your room · Tidy and air your room | Air out your room for {n} minutes | Air out your room for 20 minutes |
| vit_healthy_shop | Buy whole foods · Shop for whole foods | Buy {n} whole-food items | Buy 5 whole-food items |
| vit_balance | Balance on one leg · Practise your balance | Balance on one leg for {n} seconds | Balance on one leg for 1 minute |
| vit_hip_opener | Stretch your hips · Do hip openers | Stretch your hips for {n} minutes | Stretch your hips for 10 minutes |
| vit_variety | Eat a variety of produce · Eat different fruits and vegetables | Eat {n} different fruits or veg | Eat 5 different fruits or veg |
| vit_sun_salutations | Do sun salutations | Do {n} sun salutations | Do 6 sun salutations |
| vit_fibre | Eat fibre-rich food · Eat whole grains or pulses | Eat {n} servings of fibre-rich food | Eat 2 servings of fibre-rich food |
| vit_snack_swap | Swap junk snacks · Choose a healthier snack | Swap {n} junk snacks for healthy ones | Swap 2 junk snacks for healthy ones |
| cha_call_family | Call a family member · Call your family | Call family for {n} minutes | Call family for 10 minutes |
| cha_compliment | Give genuine compliments · Compliment someone | Compliment {n} people | Compliment 2 people |
| cha_message_friend | Message a friend · Write to a friend | Message {n} friends | Message 2 friends |
| cha_thank_you | Send thank-you notes · Thank someone who helped | Send {n} thank-you notes | Send 2 thank-you notes |
| cha_share_work | Share something you made · Post your work | Share {n} things you made | Share 2 things you made |
| cha_new_conversation | Talk to someone new · Start a new conversation | Talk to {n} new people | Talk to 2 new people |
| cha_rehearsal | Rehearse a talk aloud · Practise a presentation | Rehearse a talk aloud for {n} minutes | Rehearse a talk aloud for 10 minutes |
| cha_listen_deeply | Ask follow-up questions · Listen before you talk | Ask {n} follow-up questions | Ask 4 follow-up questions |
| cha_help_someone | Help someone out · Offer help unasked | Help {n} people out | Help 2 people out |
| cha_reconnect | Reconnect with someone · Message an old friend | Message {n} old friends | Message 2 old friends |
| cha_greet_by_name | Greet people by name | Greet {n} people by name | Greet 4 people by name |
| cha_write_post | Write a helpful post · Answer a question online | Write a {n}-word helpful post | Write a 150-word helpful post |
| cha_storytelling | Tell a personal story · Shape a story to share | Practise telling a story for {n} minutes | Practise telling a story for 10 minutes |
| cha_community | Help in an online community · Reply helpfully online | Write {n} helpful replies online | Write 2 helpful replies online |
| cha_ask_feedback | Ask for honest feedback · Get feedback on your work | Ask {n} people for feedback | Ask 2 people for feedback |
| cha_voice_note | Send a voice note · Send voice notes, not texts | Send {n} voice notes | Send 2 voice notes |
| cha_plan_meetup | Invite people to meet · Plan a meetup | Invite {n} people to meet up | Invite 2 people to meet up |
| cha_shared_meal | Share a meal · Eat with others, phones away | Eat with others for {n} minutes | Eat with others for 30 minutes |
| cha_mirror_intro | Practise introducing yourself · Rehearse your introduction | Practise your introduction for {n} minutes | Practise your introduction for 5 minutes |
| cha_professional_reach | Send a professional message · Reach out professionally | Send {n} professional messages | Send 2 professional messages |
| cha_family_time | Spend time with family · Be present at home | Spend {n} minutes with family | Spend 30 minutes with family |
| cha_celebrate_others | Congratulate someone · Encourage others | Congratulate {n} people | Congratulate 4 people |
| cha_read_aloud | Read aloud · Practise reading aloud | Read aloud for {n} minutes | Read aloud for 10 minutes |
| cha_teach | Teach someone something · Share what you know | Teach someone for {n} minutes | Teach someone for 20 minutes |
| cha_check_in | Ask how someone's doing · Check in on people | Check in on {n} people | Check in on 2 people |
| cha_volunteer | Volunteer your time · Do volunteer work | Volunteer for {n} minutes | Volunteer for 60 minutes |
| cha_small_talk | Make small talk · Chat with someone briefly | Make small talk with {n} people | Make small talk with 2 people |
| cha_letter | Write a letter · Write to someone important | Write a {n}-word letter | Write a 200-word letter |
| cha_group_activity | Join a group activity · Take part in a club | Join a group activity for {n} minutes | Join a group activity for 60 minutes |
| cha_what_you_mean | Tell someone they matter · Show your appreciation | Tell {n} people they matter | Tell 2 people they matter |
| penalty_walk | Take a short walk · Walk anywhere | Walk for {n} minutes | Walk for 10 minutes |
| penalty_squats_plank | Do squats and a plank | Do {n} squats and a plank | Do 20 squats and a plank |
| penalty_pushups | Do a few push-ups | Do {n} push-ups | Do 10 push-ups |
| penalty_read | Read a few pages · Pick up a book | Read {n} pages | Read 10 pages |
| penalty_tidy | Tidy your space · Tidy up a little | Tidy up for {n} minutes | Tidy up for 10 minutes |
| penalty_breathe | Breathe slowly to reset · Take slow breaths | Breathe slowly for {n} minutes | Breathe slowly for 5 minutes |
| penalty_water | Drink some water · Rehydrate now | Drink {n} glasses of water | Drink 3 glasses of water |
| penalty_message | Message someone you care about · Reach out to someone | Message {n} people you care about | Message 2 people you care about |
| penalty_plan | Make tomorrow's plan · List tomorrow's tasks | List tomorrow's top {n} tasks | List tomorrow's top 5 tasks |
| penalty_stretch | Do a quick stretch · Loosen up with stretches | Stretch for {n} minutes | Stretch for 5 minutes |
| penalty_flashcards | Review a few flashcards · Revise your notes | Review {n} flashcards | Review 20 flashcards |
| penalty_wall_sit | Do a short wall sit | Hold a wall sit for {n} seconds | Hold a wall sit for 1 minute |
| penalty_reflect | Write about yesterday's obstacle · Reflect on yesterday | Write {n} words about yesterday | Write 60 words about yesterday |
| penalty_thanks | Thank someone specific · Say thanks to someone | Thank {n} people | Thank 2 people |
| penalty_focus | Work on one task · Do a short focus block | Focus on one task for {n} minutes | Focus on one task for 15 minutes |

Where a title exceeds 44 chars at the Extreme target the build swaps in the shorter form noted in the row; Batch 2's
validator test lists any remaining over-length title instead of guessing. Program rule titles (GD §16.15) are already
numbered ("Walk 7,000 steps", "Read 10 pages") and stay. Path (AI) titles are untouched (the proxy prompt already
requires plain titles).

---

## 4. Today — new structure (Batch 3)

Title row = the date ("Tuesday 3 November"); no greeting, no header icons. Five sections, max:

1. **Header** — one compact row: `Level 12 · Rank D` + thin XP bar (`1,240 / 1,800 XP`) + streak (`14-day streak`). Tap → Progress.
2. **Today's plan** — section header "Today's plan · 3 of 7 done" with two quiet actions *Plan day* and *Habits*. Then:
   - **Comeback row** (only after a missed day): the comeback quest + the "never miss twice" line, one row.
   - **Rank-up test** (only while active): its stages as a compact group.
   - **Morning / Day / Evening / Anytime** groups (past groups collapsed, as today): main, side, Path, habit, program-habit and challenge rows together. **Phone-down night** is a row in Evening (tonight's window) and in Morning (last night's result, with *Wind down* link per Q8). **App limits** (Discipline) is a row in Anytime ("Under your app limits · 34 of 60 min").
   - **Weekly review** row at the end, Sat–Mon only.
3. **Focus** — *Start a session* button, today's focus minutes vs goal ring, focus streak, link to the focus report.
4. **Health & screen** — steps ring vs step goal, last night's sleep, screen time today (distracting minutes), reels today (count vs limit). Each opens its detail screen; missing access shows the existing grant/set-up prompt.
5. **Quit counters** — only if any exist: one line per counter ("Smoking · 12 days clean"); tap → Quit counters.

| Current Today element | Goes to |
|---|---|
| Greeting large title + date subtitle | date becomes the title; greeting removed |
| Header icons Focus ("portal") / Store ("vault") | removed (Focus section; Store via Progress gold) |
| Ring card "3 of 7 done" | Today's plan section header |
| Level module + Streak module (+ consistency) | Header row; consistency → Progress |
| Never-miss-twice line | Comeback row |
| Daily System line (insight card) | **removed** |
| Rank Trial window | Today's plan, "Rank-up test" group |
| Screen time tile + Health tile | Health & screen section |
| Reels tile (+ Lumi) | Health & screen (plain count) |
| Focus card | Focus section |
| Quest timeline by time block | Today's plan groups |
| Weekly review row | end of Today's plan (Sat–Mon) |
| Plan card (Plan day / Habits / add habit empty state) | Today's plan header actions; empty state stays inside the plan |
| Campaign row | removed (Q2) |
| Discipline row | Today's plan, Anytime row |
| Bedtime card (bottom of the screen) | Today's plan, Evening / Morning row |
| Penalty home tint + full-screen warning | removed; Comeback row instead |
| (new) Quit counters | section 5 |

## 5. Settings — new structure (Batch 3)

Plain sentence-case titles, no rank glow background, one section per topic:

| # | Section | Rows |
|---|---|---|
| 1 | **Profile** | Name · Focus stats |
| 2 | **Day & reminders** | Day starts at · Morning ends · Evening starts · Daily reminder · (notifications blocked notice) |
| 3 | **Goals** | Focus goal · Step goal |
| 4 | **Health & training** | Health Connect (connect / view / disconnect) · Equipment · Injury mode |
| 5 | **Phone & focus** | Screen time & app limits · Focus Lock (setup, allowed apps) · Phone-down nights · Reel counter (setup, limits, stats, permissions) |
| 6 | **Habits** | Archived habits |
| 7 | **AI Paths** | (debug/internal builds only, as now) |
| 8 | **Appearance & sound** | Theme (System / Dark / Light) · Timer sound · Haptics · Reduce motion |
| 9 | **Your data** | Export · Import · Privacy policy · Licences · Version |
| 10 | Developer | debug builds only |

| Current row | Goes to |
|---|---|
| Profile: name, focus stats | 1 |
| Profile: focus goal | 3 |
| Profile: Identities | **removed** |
| Day: day start, morning end, day end, reminder, notifications blocked | 2 |
| Training: Screen time link | 5 |
| Training: Equipment, Injury mode, Health Connect | 4 |
| Training: step goal | 3 |
| Focus window: Focus Lock, Phone-down nights, Reel counter | 5 (Reel "Inspector" row: debug builds only) |
| Archived habits | 6 |
| AI Paths | 7 |
| Comfort: appearance, chime, haptics, reduce motion | 8 |
| Your data | 9 |
| Developer | 10 |

---

## 6. Migration plan and batch checklists

### 6.1 Data rules
- **Never** `UPDATE`/`DELETE` on `ledger_entry`. Enum values that old rows use stay in Kotlin: `SourceType.ACHIEVEMENT, CACHE_OPEN, DUNGEON`, `LedgerKind.FRAGMENT, ITEM`, `QuestType.DUNGEON` (old quest/completion rows). Code never writes them again; a test asserts no new rows of those kinds after a full game day.
- Room **v25 → v26** (Batch 1) and **v26 → v27** (Batch 2), one manual `Migration` each (the existing style). minSdk 28's SQLite has no `DROP COLUMN`, so column drops rebuild the table (create `_new`, copy, drop, rename, recreate indices).
  - v26: drop tables `achievement_unlock`, `owned_cosmetic`; `campaign` (Q2), `dungeon` (Q3), `recap` (Q9) if removed; rebuild `hunter_profile` without `equipped_title_id, theme_id, equipped_frame_id, equipped_sigil_id`; rebuild `habit` without `campaign_id` (Q2; campaign habits become ordinary habits) and `quest_instance` without `dungeon_id, dungeon_stage` (Q3). Balance cache row for fragments deleted (cache, rebuildable).
  - v27: rebuild `hunter_profile` without `identities`.
- **Export/import:** `ExportUpgrade` gains v25→v26 and v26→v27 steps that drop the removed tables from `doc.tables` and strip the removed keys from every row of the affected tables, so *every* old export (v4…v25) still imports; the importer stays strict on unknown columns. `ExportDocument.TABLES` and `ExportValidator.enumColumns` updated (historic enum values still accepted).
- **Tests:** `MigrationTest` 25→26, 26→27 and 1→27 chain (rows survive, ledger row count and balances identical before/after); `GameDataPorterTest` imports a v25 fixture with achievements, cosmetics, identities, campaign, dungeon and recap rows → succeeds, ledger identical, removed data absent; export → import round trip at v27; schema JSON exported to `app/schemas/`.
- Docs: ENGINE_DECISIONS entries from #150 record each removal and Q answer.

### 6.2 Batch 1 — achievements, titles, store junk, story/narrative (+ Q1–Q3, Q5–Q7, Q9 removals)
- [ ] `engine`: delete `AchievementEvaluator`, `SystemLines`, `CrystalRules`*, `DungeonRules`*, `campaign/Campaign`*, `RecapRules`*; `ShopRules` → shield only (reroll price stays in `QuestRules`); `XpCalculator`/`CritRoller`* and `FocusRules` P4 without fragments; tests and GD §12/§14.10/§16.17 vectors updated. (*per ASK answer)
- [ ] `core/domain`: delete `AchievementUnlocker`, `CrystalGallery`*, `CampaignUseCases`*, `DungeonUseCases`*, `RecapUseCases`*; strip achievement/fragment calls from `CompleteQuestUseCase`, `CompletionCenter`, `DayRolloverUseCase`, `UndoCompletionUseCase`, `CompleteOnboardingUseCase`, `GateUseCases`, `ProgramUseCases`, `QuitUseCases`, `GymTracker`, `WeeklyReviewUseCases`, `GenerateDirectiveUseCase`, `ObserveTodayUseCase` (system line), `StatusUseCases` (achievements, chapters), `VaultUseCases` (cache, equip).
- [ ] `core/data`: entities/DAOs/`AwakenDatabase` (v26), `Migrations.kt`, `DatabaseModule`, `GameStateReader`, delete `NarrativeRepository`; `export/*` upgrade step; `assets/narrative/` deleted.
- [ ] `core/designsystem`: delete `ChapterReveal`, `TypeOnText`, `SystemWarning`, `LevelUpCeremony`*, `GateCrystal`*, `LumiView`* (+ `reelcounter-core` `lumi/`*), `Recap`*, `LongArcComponents` dungeon parts*; sigil/frame glyphs; `Color.theme()`/`LocalThemeTint`; `HunterHeader` without title/frame/sigil; `RewardSheet` without crit/shard lines.
- [ ] Features: `feature/vault` (two items), `feature/status` (no achievements/chapters/title; gold opens Store), `feature/quest/RewardHost` (no toasts for achievements, no chapter, no warning dialogs → snackbars), delete `feature/campaign`*, `feature/dungeon`*, `feature/recap`*, `feature/gate/CrystalGalleryScreen`*; routes in `AwakenRoutes`/`AwakenNavHost`.
- [ ] Debug: `DebugScreen`, `DebugViewModel`, `DemoSeeder` without fragment/cosmetic/achievement tools.
- [ ] Strings removed (achievement_*, title_*, shop_* cosmetics, vault_cache_*, chapter, campaign*, dungeon*, crystals*, recap*, lumi*) + their `_clean` twins; `LexiconPairs` regenerated.
- [ ] Docs: GD §2, §4.2*, §8 (fragment), §9, §10, §11, §6.8*, §14.6*, §14.8, §15*, §16.5*, §16.14*, §16.16; PRD F2, F6, F12, F13, F25*, F32*, F41*, F50*; ARCH §2, §3.9, §3.10, §3.12*; DESIGN_SYSTEM §4.5, §4.6, §4.10*; BACKLOG (removed ideas noted).
- [ ] Gate: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint`; `:app:recordRoborazziDebug` for the removed/changed screens (named in the commit). Commit `chore(declutter): remove achievements, titles, cosmetics and story layer`.

### 6.3 Batch 2 — renames, plain quest titles, identity removal, single Lexicon
- [ ] Every base string set to its plain text; `strings_clean.xml`, `LexiconPairs.kt`, `PresentationMode`, `Lexicon.System`, `Term.system` deleted; `Lexicon.kt` reduced to the wrappers; `LexiconResourcesTest` rewritten (no `_clean` names; a banned-jargon test over all strings: `System`, `hunter`, `gate`, `directive`, `trial`, `dungeon`, `oath`, `realm`, `sigil`, `relic`, `shard`, `chapter`, `awaken` (except the app name), case-insensitive, with a short allow-list).
- [ ] `LexiconSource` (notifications), widget, Quick Settings tile strings.
- [ ] Rank Trial → "Rank-up test" everywhere visible; Program names (Q4).
- [ ] `templates.json` → `title` patterns (§3); `engine/model/Quest.kt`, `TemplateLibrary` validation, `DirectiveGenerator` rendering, quest row target line; `TemplateTitleTest`, `ContentAssetsTest`, directive tests. GD §6.2.
- [ ] Identity removal (§1.4) + Room v27 + export step; GD §14.7 deleted, §14.4 review line.
- [ ] Docs: GD voice/§11 rewritten as "Copy voice: plain, second person, sentence case"; DESIGN_SYSTEM §5 and §8.3; ARCH lexicon notes; CLAUDE.md conventions line about `_clean` twins.
- [ ] Gate as above; goldens re-recorded for screens whose words changed. Commit `chore(declutter): one plain vocabulary and numbered quest titles`.

### 6.4 Batch 3 — Today restructure, Settings cleanup
- [ ] `feature/today`: `TodayScreen` (5 sections, §4), `TodayViewModel`/`TodaySummaryViewModel`/`ObserveTodayUseCase` (header model, plan groups incl. phone-down and app-limit rows, quit summary), `BedtimeCard` → plan row, `TodayTrial` → plan group; new design-system rows if needed (`SectionHeader` with actions, `ProgressHeaderRow`, `QuitSummaryRow`) in `core/designsystem/components`.
- [ ] `feature/status` → Progress: consistency + streak card, gold → Store, Quit/Programs rows stay.
- [ ] `feature/settings`: sections per §5; `BedtimeSettings`, `FocusLockWindow`, `ReelCounterWindow` placed in "Phone & focus"; step/focus goal into "Goals".
- [ ] Tests: `TodayMappingTest`, Today/Settings screen tests (section order; phone-down row in the Evening group; quit section hidden with no counters).
- [ ] Docs: DESIGN_SYSTEM §4.2, §4.7; PRD F3/F17 wording.
- [ ] Gate; goldens for Today and Settings. Commit `feat(today): five-section Today and grouped Settings`.

### 6.5 Batch 4 — redesign readiness
- [x] **Layering:** every screen is `XRoute` (ViewModel wiring only) → stateless `XScreen(state, onEvent)`. New `ScreenLayeringTest`: in `feature/**`, `hiltViewModel()`/`collectAsStateWithLifecycle()` only inside `*Route`/`*Entry` functions; every `*Screen` has a `@PhonePreviews` preview. Known gaps to fix first: `feature/program/ProgramScreen.kt` and (if kept) `feature/dungeon/DungeonScreen.kt` (no separate ViewModel file), `GatesScreen`, `HealthScreen`, `ScreenTimeScreen`, reel-counter windows — audited and split where needed.
- [ ] **Tokens:** `FeatureBoundaryTest` already bans hex, raw `Color.*`, `dp/sp` literals, drawing and styling modifiers in `feature/**` (0 violations today). Extend it to `core/widget/**` and `core/reelcounter/**`, and add `TokenSourceTest`: `Color(0x…)` allowed only in `core/designsystem/theme/Color.kt` (today `ReelBubble.kt` also has raw colours → moved into the palette).
- [ ] **One palette file:** the navy/blue values live only in `Color.kt` (`AwakenPalette` dark + light); `Theme.kt` maps them to semantic tokens; rank colours stay tokens. `GateBackground` replaced by a neutral `ScreenBackground` (no rank glow) in the 20 files that use it.
- [ ] **Light + dark:** every `@PhonePreviews` already renders both; add a test that each semantic token differs between schemes where contrast requires it (text on surface ≥ 4.5:1, from DESIGN_SYSTEM §6).
- [ ] **Charts:** confirm all charts go through ChartKit (radar, heatmap, trend lines, rings) — `RadarChart`, `Heatmap` moved behind `Chart(ChartSpec)` if they are not yet.
- [ ] **Screenshots:** Roborazzi goldens for each screen's main states (empty, typical, error/no-access, light + dark), re-recorded after the cleanup; stale goldens of deleted screens removed.
- [ ] Docs: DESIGN_SYSTEM §2.1/§8.4 (one palette file, token test), ARCHITECTURE §1 (layering rule), CLAUDE.md conventions; final pass so PRD, GAME_DESIGN, DESIGN_SYSTEM and ARCHITECTURE describe the app that exists (removed sections deleted, not struck through).
- [ ] Gate incl. `:app:verifyRoborazziDebug`. Commit `chore(designsystem): redesign readiness (layering, tokens, goldens)`.

Each batch stops after its commit with a short summary; nothing is merged.
