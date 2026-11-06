# Plates components

Each maps to a Compose component in `core/designsystem`. **Swap** marks the parts that stay swappable for future looks (`to_build_next.md`). Every interactive part has default, pressed and disabled states, and 48dp targets.

## Signature

| Component | States | Used in |
|---|---|---|
| **DayBar** (Swap) | Empty, part loaded, full. Each quest is a soft-rimmed plate: width by reward, colour by stat; open quests are faint tone plates | Today, plan done, onboarding, empty states |
| **LoadBar** (Swap) | Real plates by kg, both sides, labelled | Active workout, plate calculator |
| **SessionDial** (Swap) | Running, paused, break, record. Deep stat colour, round white dial, white text | Focus session, rest, wind down, PR |
| **BigNumber** (Swap) | Value + unit; loading | Every hero number |
| **ProgressBar** | 0 to 100%. Smooth 8dp pill | XP, goals, limits, quit progress |

## Game layer

| Component | States | Used in |
|---|---|---|
| **LevelRing** | Small 76, large 136. Empty at 0 | Today, Progress, level-up |
| **RankBadge** | Round letter, E to S; large | Today, Progress, rank-up |
| **StreakLabel** | 0, running | Today, Progress |
| **LevelModule** | Ring + rank + XP + streak; streak broken shows best run | Today |

## Navigation and structure

| Component | States | Used in |
|---|---|---|
| **NavBar** | 5 tabs; selected fills a pill indicator | Tabs |
| **TopBar** | Tab, pushed with Back, with a text action | All screens |
| **BottomSheet** | Scrim, grab handle, title, one primary | All sheets |
| **ProgressSegments** | 5 groups, partial fill | Onboarding, weekly review |

## Actions

| Component | States | Used in |
|---|---|---|
| **PrimaryButton** | Ink fill; inverse on a field; loading; disabled | One per screen |
| **SecondaryButton** | Sunken tone | Fix, Start routine, Reel limits |
| **TextButton** | Muted weight | Skip, Finish, End session |
| **IconButton** | 48dp | Settings, Back |
| **Stepper** | Minus, plus; disabled at limits | Set weight and reps, food amount |
| **Chip** | Off, on with check | Filters, stat, days, plates owned |
| **Segmented** | 2 to 4 options | Units, theme, session type, diet goal |
| **Switch** | Off with dot, on with check | Settings rows |
| **PickTile** | Off, on, disabled with reason | Session length, onboarding choices |
| **TextField** | Filled, label above value, error line | Name, search, subject |

## Lists and data

| Component | States | Used in |
|---|---|---|
| **QuestRow** | Open, done, expired. Round check, title, subtitle, stat dot, XP | Today, comeback, programs, Rank-up test |
| **TimeBlockHeader** | Collapsed, open, Now | Today |
| **NavRow** | Icon, title, value, chevron; attention state; grouped in one card | Settings, Focus, Progress |
| **ShortcutTile** | Label + live value | Progress |
| **MetricTile** | Value, unit, note; no access prompt | Today |
| **KeyValueList** | Plain, total row | Reward, details, targets |
| **NoteCard** | Title, text, optional action | Permission asks, patterns |
| **ComebackCard** | One quest and the line | Today after a missed day |
| **MealCard** | Empty, with items | Diet |
| **CounterCard** | Running, paused | Quit counters |
| **DayGrid** | Done, missed, today, future | Programs |

## Charts (ChartKit)

| Chart | Rule | Used in |
|---|---|---|
| **PlateRadar** | Solid now, dashed last week; round stat markers; stat list below | Progress |
| **StackedBars** | XP by stat; total on the last bar | Progress |
| **DensityHeat** | Ink opacity, 18 weeks × 7 days | Progress |
| **WeekBars** | Today in full ink, others muted | Screen time, report, reels, history |
| **SplitBar** | Distracting, productive, neutral; values listed beside | Screen time, sleep |

All charts carry a text summary for screen readers.

## Shared states

| State | Treatment |
|---|---|
| **Empty** | A bare bar waiting for a plate, heading, line, one action |
| **Loading** | Tone blocks where content lands; no spinner for local reads |
| **Error** | Plain statement, reassurance, Try again |
| **Permission missing** | Says what it unlocks, one action; never a fake 0 |

## Still to design
- **Muscle map** in the sheet is a placeholder in the mockup. The existing body map stays and takes Plates colours in Step 5.
- **Exercises browse and plate calculator outside a workout** depend on the Gym rework.

## Small phones and large text

Checked at 360x640 and at 130% and 200% text. These rules apply to the Compose port.

- **Height:** never fix a screen to a height. Layout follows the available height, and content scrolls.
- **Primary action:** stays pinned above the bottom inset on onboarding, sessions and forms. Content scrolls behind it.
- **SessionDial:** diameter is the smaller of 296dp and 35% of screen height. The numeral scales with it, up to 76sp.
- **Filter chips in one row:** scroll sideways, bleeding to the screen edge. Never clip them.
- **Level module lines:** wrap to a second line. Never let two values touch.
- **Four-option segmented control:** scrolls sideways once labels no longer fit.
- **Nav labels:** already handled by the fix in `2be4952`. Keep it.
- **Targets:** the 48dp target can be larger than what's drawn. Chips draw 44, segments 42 in a 48 track, and switches 32, where the whole row is the target.
