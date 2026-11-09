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
