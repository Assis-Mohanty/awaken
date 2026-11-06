# AWAKEN UX audit (Step 1)

Date: 2026-11-06. Branch: `feat/onboarding`. No code changed.

**Sources:** `AwakenNavHost.kt`, `AwakenRoutes.kt`, the screen source files, `res/values`, `docs/DESIGN_SYSTEM.md` §3–4, `docs/DECLUTTER_PLAN.md`, the Roborazzi goldens and `docs/ui-reference/`.

**Screenshots:** copies of the phone goldens are in `design/audit/`, named `<PreviewName>-light.png`. Goldens show the first 891dp of a screen, so anything lower is not pictured.

**Tap counts:** taps from Today, read from the nav wiring and screen code. "+ scroll" means the entry is below the first screen, or was judged to be from the source order where no golden shows it. Nothing here was measured on a phone.

---

## 1. Main findings

1. **Today answers "what do I do now?" last.** Screen time, health and level take the first 60% of the screen. The first open task starts about 80% of the way down (`TodayScreenPreview-light.png`).
2. **Gym logging is the weakest screen.** The session is a form, every exercise is open at once, and rest is one small line of text. Your references do the opposite: one exercise, a big rep number, a full-screen rest.
3. **Rank letters are the last game words on screen.** Sessions are called "E session", "D session", "C session" and "B session". The running timer's title is "C session".
4. **Progress hides three features at the very bottom.** Store, Quit counters and Programs are the last rows, under a long scroll.
5. **Settings is a long form.** It has ten sections, with inline chips for every hour. Phone-down nights and Permissions and battery can only be reached from here.
6. **There are two different "limits".** "Daily limits" on Focus means reels. "Screen limits" on Screen time means apps, and it goes by "Screen Discipline" there. A person can't tell them apart.
7. **Widgets and the Quick Settings tile have no mention anywhere in the app.**
8. **Everything has the same visual weight.** White card, grey well, black pill. Nothing leads the eye, and that's the visual problem to solve in Step 3.

### How your list held up

| Your suspicion | Finding |
|---|---|
| Borders everywhere | **Mostly no.** Cards are flat fills with no border. Outlines appear on text fields and gym set cells. The real problem is the reverse: everything is the same white card. |
| Flat greys | **Yes.** Wells, chips and the Focus report and Reel stats bars are all grey. |
| One accent on everything | **Yes, in light mode.** Near-black carries selection, primary buttons and progress. Gold is used for XP. |
| Spreadsheet workout logging | **Yes.** Columns of Set, Previous, kg, Reps and a checkbox. |
| 8 equal-weight buttons | **Yes, exactly eight in the session.** Per exercise: Add set, Remove last set, Plate calculator, History. Per session: Add exercise, Finish, Save routine, Discard. Finish sits below every exercise. |
| Weak hierarchy | **Yes**, except Focus, Store and Screen time, which do lead with one big number. |
| Things below the fold | **Yes.** Today's plan, Gym routines, and Progress's Store, Quit counters and Programs. |
| Gates and Vault behind unlabelled icons | **No longer true.** Those icons are gone. Store is a labelled row on Progress, and sessions start from Focus. `DESIGN_SYSTEM.md` still describes the old icons. |

---

## 2. Which game names are live

Visible strings in `res/values` only.

| Name | Status | Where |
|---|---|---|
| Gates | **Not live** as a word | Code only. Shown as "Focus sessions". |
| Vault | **Not live** | Code only. Shown as "Store". |
| Rank Trial | **Not live** | Shown as "Rank-up test". |
| Hunter, the System, Directive, Awakening, Dungeon | **Not live** | Code, comments and old docs only. |
| **E / D / C / B session** | **LIVE** | `gate_name` ("%1$s session"): session tiles, running title and the finished notification. |
| **Screen Discipline** | **LIVE** | Turn on/off button, quest title, notification channel, messages. The card beside it says "Screen limits". |
| **STR / INT / WIL / VIT / CHA** | **LIVE** | Chips on the running session and quest detail. Settings already uses full names. |
| Quest, Reroll, gold, XP, Rank | Live, kept on purpose | `DECLUTTER_PLAN.md` doesn't remove them. "Quest" appears in about 100 strings. |

**Code and doc debt, not user-visible:**
- `DESIGN_SYSTEM.md` line 160 (Gates and Vault icons) and §4.4 ("portal tiles", "GATE COLLAPSED") are out of date.
- **151 orphan goldens** remain for code that no longer exists: old onboarding 50, dungeon 30, gallery 15, recap 15, campaign 10, and 31 removed components. They're harmless but misleading, and should go in a separate cleanup.

---

## 3. Screen inventory

Main route is the composable or nav route. Tabs show no back arrow and everything else hides the bottom bar.

### Tabs

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| Today | App open | 0 | Metrics before the plan; first task at 80% height; plan header wraps to two lines | `TodayScreenPreview-light.png` |
| Focus | Tab | 1 | Good hero. Rows are flat and all equal; Screen time is missing, though it belongs here | `FocusHubPreview-dark.png` |
| Gym | Tab | 1 | A body map fills the screen; routines start below it | `GymHomePreview-light.png` |
| Diet | Tab | 1 | Strongest tab, but "Add food" repeats per meal and macros are four equal bars | `DietDiaryPreview-light.png` |
| Progress | Tab | 1 | Two equal cards then a pale radar; no single hero; unlabelled Paths and Settings icons; features hidden at the bottom | `StatusScreenPreview-light.png` |

### From Today

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| Quest detail | Tap a row | 1 | Three stacked full-width buttons; a disabled "Complete quest" sits beside Begin | `QuestDetailPreview-light.png` |
| Rank-up test | Plan group, only when available | 1 | Fine; stats in pale wells | `TrialDetailPreview-light.png` |
| Weekly review | Today row Sat–Mon; Progress | 1–2 + scroll | Plain form; low reward for a weekly ritual | `ReviewNumbersPreview-light.png` |
| Habits list (sheet) | "Habits" text button | 1 | Quiet button in a wrapped header; easy to miss | `HabitOverviewPreview-light.png` |
| Habit editor (sheet) | Habits → Add habit | 2 | Long form with every option open | `HabitEditorPreview-light.png` |
| Day plan (sheet) | "Plan day" text button | 1 | The name doesn't say what it does | `DayPlanPreview` golden |
| Wind down | Plan link, only 30 min before bedtime | 1 | Time-limited; no other way in | `WindDownBreathingPreview-light.png` |
| Reward sheet | After completing a quest | 0 | Not reviewed visually in this pass | Golden not copied |

### Focus and phone

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| Focus sessions (setup) | Focus → Start a session | 2 | Rank-letter names; shows Presets, Custom, Stopwatch and Pomodoro at once; extra setup before starting | `GatesPickerPreview-light.png` |
| Session running | Setup → start | 3 | Title "C session"; "INT" chip; two rings with no labels; the screen's lower half is empty | `GateRunPreview-dark.png` |
| Screen time | Today tile; Today plan row; Settings | 1 | Not in Focus. "Screen limits" and "Screen Discipline" name one thing | `ScreenTimeScreenPreview-light.png` |
| Focus report | Focus → row | 2 | Grey bars; the data doesn't feel rewarding | `FocusReportPreview-light.png` |
| Focus Lock setup | Focus → row | 2 | Wall of text, though it's an honest disclosure | `SetupExplainPreview-light.png` |
| Allowed apps | Focus → row | 2 | Fine | `AllowedAppsPreview-light.png` |
| Reel counter setup | Focus → row, while off | 2 | Fine | `ReelSetupExplainPreview-light.png` |
| Reel stats | Today tile; Focus; Settings; Screen time | 1 | Four entry points; grey bars | `ReelStatsWeekPreview-light.png` |
| Reel limits | Focus → "Daily limits" | 2 | Name collides with app limits; two big buttons per app | `ReelLimitsSomeOnPreview-light.png` |
| Permissions and battery | Settings only | 2 + scroll | Needed when things break, but far from Focus | `FocusAccessPreview-light.png` |
| Phone-down nights setup | Settings only (inline) | 2 + scroll | A focus tool buried in Settings | `BedtimeSettings` goldens |
| Overlays (Focus Lock, reel nudge) | System windows | n/a | Must follow the new theme | `FocusLockOverlay*`, `ReelNudge*` goldens |

### Gym and Diet

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| Active workout | Gym → Start workout | 2 | Spreadsheet; eight text buttons; every exercise open at once; no focus on the current set | `GymSessionPreview-dark.png` |
| Rest timer | Inline in session | n/a | One line of text at the top. Not a screen | `GymSessionPreview-dark.png` |
| Workout summary | Finish | n/a | Small numbers, then the Gym home appears right under it | `GymSummaryPreview-light.png` |
| Exercise picker (sheet) | Session → Add exercise | 3 | Body map takes the sheet; results start at the bottom | `ExercisePickerPreview-light.png` |
| Exercise library | **No browse entry** | n/a | Only reachable inside a workout | n/a |
| Gym settings | Quiet button on Gym | 2 + scroll | Text button stranded between sections | `GymSettingsPreview-light.png` |
| Plate calculator | Session → exercise | 3+ | Only inside a workout | `PlatesExactPreview-light.png` |
| Records, history | Gym, lower sections | 1 + scroll | Below the map and routines | `GymHomePreview-light.png` |
| Diet setup | First Diet open | 1 | Long form, but only once | `DietSetupPreview-light.png` |
| Food search, amount, targets (sheets) | Diet | 2–3 | Search results are plain text lines | `FoodSearchPreview-light.png` |

### Progress and beyond

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| Store | Progress → bottom row | 2 + long scroll | Hidden; reroll note says buy from Today | `VaultScreenPreview-light.png` |
| Quit counters | Today row if any exist; else Progress bottom | 1–2 + long scroll | **No entry on Today until you have one**, so it's hard to discover | `QuitListPreview-light.png` |
| Programs | Progress → bottom row | 2 + long scroll | Hidden; the list is three plain rows | `ProgramsListPreview-light.png` |
| Paths, Path detail | Progress → unlabelled icon | 2 | Debug builds only; icon has no text | `PathsScreenPreview-light.png` |
| Health | Today tile; Settings | 1 | No home of its own; dense wells | `HealthScreenPreview-light.png` |
| Settings | Progress → gear | 2 | Long form; hour chips; two grey "Save" buttons; not reachable from Today | `SettingsScreenPreview-light.png` |
| Archived habits | Settings → section | 2 + scroll | Far from Habits | `ArchivedHabitsPreview` golden |
| Debug, Reel inspector, onboarding preview | Settings → Developer | 3+ | Debug builds only | `DebugScreenPreview` golden |

### Onboarding

| Screen | How you reach it | Taps | What's wrong | Picture |
|---|---|---|---|---|
| 21-step quiz | First run | n/a | Newest and clearest flow. Placeholder art; the plan is a stack of equal cards | `OnboardingHeightPreview-light.png`, `OnboardingPlanPreview-light.png` |

### Outside the app

| Surface | How you reach it | What's wrong |
|---|---|---|
| Today widget, Focus widget, Start focus widget | Launcher widget picker | **No mention in the app** |
| Quick Settings tile "Start focus" | Android tile editor | **No mention in the app** |
| Notifications | System | Finished session reads "C session finished" |

---

## 4. Features with no obvious way in

| Feature | Problem |
|---|---|
| Quit counters | Bottom of Progress, under a long scroll, until one exists |
| Programs | Bottom of Progress |
| Store | Bottom of Progress |
| Screen time | Fine from Today, but absent from the Focus tab where people would look |
| App limits (Screen Discipline) | Inside Screen time only, under a confusing name |
| Phone-down nights | Setup is in Settings only |
| Permissions and battery | Settings only |
| Wind down | Appears only 30 minutes before bedtime |
| Exercise library, plate calculator | Only inside a running workout |
| Widgets, Quick Settings tile | Never mentioned |
| Habits | Present, but a quiet text button |
| Settings | Only from a gear on Progress |

## 5. Duplicated entry points

| Destination | Entries | Keep? |
|---|---|---|
| Reel stats | 4: Today tile, Focus, Settings, Screen time | Too many |
| Screen time | 3: Today tile, Today row, Settings | Tile and row are fine; Settings can go |
| Focus Lock setup | 3: Focus, Settings, session failure fix | Keep Focus and the fix link |
| Reel setup, Reel limits, Allowed apps | 2 each: Focus, Settings | Focus only |
| Health | 2: Today tile, Settings | Fine: one view, one connection setting |
| Weekly review | 3: Today row, Progress, notification | Fine: time-based prompts to one home |
| Quit counters | 2: Today row, Progress | Today row can go |

## 6. Your references

`docs/ui-reference/` has 52 phone images, 4 refero images and two videos (one is a copy). I viewed the newest set as a contact sheet, plus the 4 refero images and 9 of the older images. I did not watch the videos.

**Worth taking:**
- One exercise per screen: name, big rep number, three actions, "Next up".
- Full-screen Active Rest with a large countdown and "Next up".
- A swap sheet ("Harder", "Easier") instead of a form.
- A strength log with big last-best numbers per lift.

**Leave behind:** neon teal, rainbow rest rings, heavy dark gradients and emoji. They break your "never neon" rule. Exercise photos are also a content question: the app has no such library, so the mockups should not assume one.
