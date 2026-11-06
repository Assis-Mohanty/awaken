# AWAKEN information architecture (Step 2)

Status: **APPROVED 2026-11-06.** Decisions (§8): session names "15-minute session" (Easy/Normal/Hard kept); Quit counters removed from Today; Widgets and shortcuts section added to Settings; muscle map moves into a sheet on Gym. Evidence is in `design/AUDIT.md`.

**Rule:** from any tab, a new person reaches any feature in 2 taps or fewer, and can see it's there without scrolling far. Rewards, rules and data don't change. These are moves and renames only, except the items marked **New** in §7.

---

## 1. Tab map

Five tabs, the same five as today.

| Tab | Its one job | Holds |
|---|---|---|
| **Today** | What to do now | Header, plan, 3 metric tiles |
| **Focus** | Protect your attention | Sessions, screen time, blocking, app limits, phone-down nights, reels, reports |
| **Gym** | Train | Start or resume, routines, week, records, history, exercises |
| **Diet** | Eat to your target | Diary, targets |
| **Progress** | See how far you've come | Level, rank, stats, trends, insights, and the long-running things: review, programs, quit counters, store, health |

**Why no change:** your proposed set is already right. A sixth tab for Habits or Health would break the limit. Moving Focus or Progress would only cost muscle memory.

---

## 2. Screen tree

`→` opens a pushed screen. `(sheet)` opens over the current screen. `[debug]` is debug builds only.

```
TODAY
├─ Header: greeting and date · level ring · rank · XP bar · streak · Settings gear
│    └─ tap level → Progress tab
├─ Plan  ("Today's plan", "2 of 6 done")  actions: Plan day · Habits
│    ├─ Comeback row (after a missed day)
│    ├─ Rank-up test (when available) → Rank-up test
│    ├─ Morning / Day / Evening / Anytime rows → Quest detail
│    │    ├─ Phone-down night row → Wind down (30 min before bedtime)
│    │    └─ App limits row → Screen time
│    ├─ Weekly review row (Sat–Mon) → Weekly review
│    ├─ Habits (sheet) → editor (sheet), options (sheet), archived habits
│    └─ Plan day (sheet)
└─ Metric tiles
     ├─ Focus today → Focus tab
     ├─ Screen time → Focus tab → Screen time
     └─ Steps → Progress tab → Health

FOCUS
├─ Hero: focused today · goal progress · streak · [Start a session]
│    └─ Start a session → Session setup → Session running → Reward sheet
│         (also: paused, break, stopwatch, pomodoro)
├─ Screen time card (today's total) → Screen time, with app limits inside
├─ Block distractions
│    ├─ Focus Lock → setup
│    ├─ Allowed apps
│    ├─ App limits → Screen time, limits section
│    └─ Phone-down nights → Phone-down nights setup
├─ Reels
│    ├─ Reel counter (today's count) → Reel stats, or setup if off
│    └─ Reel limits
└─ More
     ├─ Focus report
     └─ Permissions and battery  (shows "Needs attention" when something's off)

GYM
├─ [Start workout], or [Resume workout] when one is running
│    └─ Active workout (full screen, no bottom bar)
│         ├─ Full-screen rest timer
│         ├─ Plate calculator (sheet) · History (sheet) · Add exercise (sheet)
│         └─ Workout summary
├─ Routines (first thing under the button)
├─ This week: workouts, sets, volume → Muscle map (sheet)
├─ Records → History (sheet)
├─ History
└─ More: Exercises (browse) · Plate calculator · Gym settings

DIET
├─ First run: Diet setup
├─ Day switcher · energy · macros → Targets (sheet)
└─ Meals → Add food (sheet) → Amount (sheet)

PROGRESS
├─ Hero: level ring · rank · XP to next · streak
├─ Shortcuts (labelled, with a live value, above the fold)
│    ├─ Weekly review   "Open now" / "Next Saturday"
│    ├─ Programs        "Day 12 of 30" / "Start one"
│    ├─ Quit counters   "Smoking · 12 days" / "Add one"
│    ├─ Store           "1,240 gold"
│    ├─ Health          "8,412 steps"
│    └─ Paths [debug]
├─ Stat balance (radar) · stat list
├─ Trend · activity heatmap
└─ Insights · weekly report and share

SETTINGS  (gear on Today)
└─ Six sections, §5

FIRST RUN
└─ Onboarding quiz → Today
```

---

## 3. Today

In order:

1. **Header.** A greeting and the date, then a compact module: level ring, rank badge, XP bar, streak. The gear sits top right. This is the only header.
2. **Plan.** Starts on the first screen, with the current block open. Title and progress sit on two tidy lines so the actions don't wrap.
3. **Three tiles** that open the screen where each thing lives: Focus today, Screen time, Steps.

**Leaves Today:** the large screen time ring, the health card, the reels tile, and quit counter rows. Each still has a home one tap from a tile or a tab.

**This reverses two earlier decisions**, as your brief asks:
- `30c31f9` (2026-11-05) put live metrics first.
- `DECLUTTER_PLAN.md` replaced the greeting with the date as title.

`docs/DESIGN_SYSTEM.md` §4.2 needs updating in Step 5.

---

## 4. Names

Plain words, no behaviour change.

| Now | Proposed | Why |
|---|---|---|
| E / D / C / B session | **15-minute session**, and so on, keeping Easy, Normal, Hard | Rank letters mean nothing here. Running title shows the subject, or "Focus session". |
| Screen Discipline, Screen limits | **App limits** | One name for one thing |
| Daily limits (Focus row) | **Reel limits** | Stops the collision with app limits |
| INT, STR and other chips | **Intelligence**, **Strength** and so on | Settings already uses the full words |
| Plan day | **Rest day** (the sheet sets rest days and recovery mode) | Says what it does |
| Reroll | **Keep as is** | Short and understood; a quest word you've kept |
| Quest, XP, Rank, gold, Rank-up test | **Keep** | The light game layer you asked to keep |

---

## 5. Settings: six sections

Each row shows its current value and opens a sheet, so the screen stops being a long form. Hours become a picker in place of chips.

| # | Section | Contains |
|---|---|---|
| 1 | **Profile** | Name, focus stats, daily focus goal, step goal |
| 2 | **Day and reminders** | Day starts at, morning ends, evening starts, daily reminder, notifications |
| 3 | **Health and training** | Health Connect, equipment, injury mode, training days |
| 4 | **Appearance and sound** | Theme, timer sound, haptics, reduce motion |
| 5 | **Widgets and shortcuts** | How to add the three widgets and the Quick Settings tile (**New**, §7) |
| 6 | **Your data** | Export, import, privacy policy, licences, version, AI planner [debug], Developer [debug] |

**Moves out:** Phone and focus goes to the Focus tab. Archived habits go to the Habits sheet.

---

## 6. Before → after, every feature

Taps are from Today. "S" means scrolling was needed before, or is still needed.

| Feature | Before | Taps | After | Taps |
|---|---|---|---|---|
| Today's plan | Below metrics | 0, S | First screen | 0 |
| Complete a quest | Row circle | 1, S | Row circle | 1 |
| Quest detail | Row | 1, S | Row | 1 |
| Comeback, Rank-up test | Top of plan | 0–1, S | Top of plan | 0–1 |
| Habits | Quiet text button | 1 | Plan action, clearer | 1 |
| Add a habit | Habits → Add | 2 | Same | 2 |
| Archived habits | Settings | 2, S | Habits sheet | 2 |
| Plan day | Quiet text button | 1 | Plan action | 1 |
| Phone-down tonight | Plan row | 0, S | Plan row | 0, S |
| Phone-down nights setup | Settings only | 2, S | Focus → row | 2 |
| Wind down | Plan row, timed | 1 | Same | 1 |
| Start a session | Focus → Start | 2 | Same | 2 |
| Focus report | Focus → row | 2 | Same | 2 |
| Screen time | Today tile or Settings | 1 | Today tile, or Focus card | 1–2 |
| App limits | Inside Screen time | 2, S | Focus → row | 2 |
| Focus Lock | Focus, Settings | 2 | Focus | 2 |
| Allowed apps | Focus, Settings | 2 | Focus | 2 |
| Reel counter and stats | 4 places | 1 | Focus → row | 2 |
| Reel limits | Focus, Settings | 2 | Focus → row | 2 |
| Permissions and battery | Settings only | 2, S | Focus → row | 2, S |
| Start a workout | Gym → Start | 2 | Same | 2 |
| Routines | Below the body map | 1, S | Under Start | 1 |
| Week stats and muscle map | Gym top | 1 | Stats on Gym; map in a sheet | 1–2 |
| Records, history | Gym, far down | 1, S | Gym, higher | 1, S |
| Exercises | Inside a workout only | 3 | Gym → More | 2, S |
| Plate calculator | Inside a workout only | 3+ | Gym → More, and in workout | 2, S |
| Gym settings | Stranded text button | 2, S | Gym → More | 2, S |
| Diet diary | Tab | 1 | Same | 1 |
| Add food | Diet → meal | 2 | Same | 2 |
| Diet targets | Diet | 2 | Same | 2 |
| Level, rank, XP | Today, Progress | 0 | Today header, Progress hero | 0 |
| Stats, radar, heatmap | Progress | 1, S | Same, tidier | 1, S |
| Insights | Progress | 1, S | Same | 1, S |
| Weekly review | Today row, or Progress bottom | 1–2, S | Today row, or Progress shortcut | 1–2 |
| Programs | Progress bottom | 2, S | Progress shortcut | 2 |
| Quit counters | Today row if any; Progress bottom | 1–2, S | Progress shortcut | 2 |
| Store | Progress bottom | 2, S | Progress shortcut | 2 |
| Health | Today tile, Settings | 1 | Today tile, Progress shortcut | 1–2 |
| Paths [debug] | Unlabelled icon | 2 | Labelled shortcut | 2 |
| Settings | Progress → gear | 2 | Today → gear | 1 |
| Widgets, Quick Settings tile | Nowhere | n/a | Settings section | 2, S |

**Two things that get a tap further, on purpose:** Reel stats goes from 1 tap to 2, and quit counters lose their Today row. Both reduce noise on Today. The second is a real trade-off, so see decision 2.

**Still needing a scroll:** Exercises, Plate calculator, Gym settings and Permissions and battery are at the bottom of their tab. They're occasional tools, and the 2-tap rule still holds.

---

## 7. What goes beyond a pure move

| Item | Why it's more than a move |
|---|---|
| **Widgets and shortcuts** section | New content; widgets and the tile have never been mentioned in the app |
| **Exercises browse view** | The library only exists as a picker in a workout; belongs with the Gym rework |
| **Plate calculator outside a workout** | Same; belongs with the Gym rework |
| **Phone-down nights as its own screen** | The inline Settings block becomes a screen with no rule changes |
| **Settings sheets** | Same values; chips become rows and pickers |

---

## 8. Decisions for you

1. **Session names:** "15-minute session", keeping Easy, Normal and Hard? (Recommended.) These are the last rank letters on screen.
2. **Quit counters on Today:** remove the row? (Recommended.) It keeps Today short, but a daily reminder of a clean streak may be what some people want. The alternative is one line under the plan, only when a counter exists.
3. **Widgets and shortcuts section:** add it? (Recommended.) It's the only new content in this plan, and it's the only way those features become discoverable.
4. **Muscle map:** move it into a sheet from the "This week" card? (Recommended.) It's distinctive, but it pushes routines off the screen today. Step 3 can test a small version on the card.
