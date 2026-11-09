# Gym redesign: open questions

Raised while building. Each says what I did meanwhile, so nothing is blocked.

## Q11. The Army pass mark leaves the Novice band empty (GD §18.3, G9)

G9 maps points to levels: below a pass = Beginner, pass to 59 = Novice, 60-79 = Intermediate, 80-99 = Advanced, 100 = Elite. That
was written for a pass mark under 60. The Army Fitness Test passes at **60 points** per event, so a plank or push-up that just
passes is already Intermediate and **Novice can never be shown** for those two moves.

- **Built meanwhile:** G9 exactly as approved (`Benchmarks.standardLevel`), covered by a test.
- **Recommendation:** for standards with a pass of 60, use Beginner below 60, Novice 60-69, Intermediate 70-84, Advanced 85-99,
  Elite 100. One extra constant; no other number moves.

## Q12. Push-up is a single-set count, the Army test is 2 minutes of hand-release push-ups

`fedb:Pushups` is mapped to the Army hand-release push-up table (reps in 2 minutes). A hunter's max single set is a different
effort, so the level is approximate. Options: keep it with the caption "Army test standard (2-minute hand-release push-ups)";
or drop push-up and keep only the plank, which matches the Army hold exactly. Pull-up has no table yet: the Marine Corps tables
were not available from an official source in this session.

- **Built meanwhile:** push-up and plank are both in `benchmarks.json`; the UI (Batch 4) decides what to show.

## Q13. Bench and deadlift percentiles are harsh for ordinary lifters

The percentile is against raw powerlifting competitors (median bench about 1.44 x body weight for men). An 80 kg man benching
60 kg lands near the 1st percentile. That is honest to the data and the caption says so, but the Beginner band will be common.
Approved as Q2; noting the consequence so the copy in Batch 4 stays encouraging (show progress to the next level, not just the band).

## Q14. Routine duration needs one number (new `G20`, display only)

The Gym home and plan sheets show "about 32 min" and each section's minutes. That needs a time per set, which the approved list does
not have. Built meanwhile: `RoutineMath.SET_WORK_SEC = 40` seconds per set plus the exercise's rest (a superset partner followed by
another does not rest). It feeds no XP, cap or rule. **Please approve 40 s or give another value**; it moves to GD §18.1 once approved.

## Q15. Shortcuts taken in Batch 2

- **Week strip** shows the current week only (no swiping back to earlier weeks). History still lists every workout.
- **Reordering** routines and routine exercises uses move up / move down buttons, not drag handles.
- **Timed exercises** (plank) show their target but their hold time is edited by removing and re-adding the exercise, not in the editor.
- **Plan weekdays:** a multi-day plan keeps its own weekdays (for example Mon/Wed/Fri); it does not remap to your training days. Change them under "Choose days".

## Q16. Shortcuts taken in Batches 4 and 5

- **Plank has no standing yet.** The Army plank table is in `benchmarks.json`, but a logged set keeps its hold seconds only on `workout_set.duration_sec`, and the exercise history rows do not carry it, so Batch 4 shows no level for plank. Push-up works from best reps.
- **The tracker lists weighted exercises only** (it reads the personal records, which need an estimated 1RM). Bodyweight moves show their standing in the exercise's history.
- **Strength tracker and exercise detail are bottom sheets**, not routes, like History and the muscle map.
- **No level-up celebration after Finish** (the summary shows the XP line only). The existing completion flow shows one for quests.
- **Earlier sets of the day** for the 10-per-exercise limit are re-judged from the finished workouts, so a workout whose XP was held back by a cap still counts its sets toward that limit.
