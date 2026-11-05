# Phone test checklist — v1.2 (F35–F51, minus the removed Crystals and Dungeons)

Run on a real phone with a **debug** build (`./gradlew :app:assembleDebug`). Multi-day checks use the Debug screen's
time travel (`DebugClock`); the game-day starts at 04:00. Do every check in **both themes** at least once, and the
starred ones with **TalkBack** on. Each feature: tick the box, or write what happened next to it.

Before you start: Health Connect installed, usage access granted, notifications allowed. Keep one run with usage access
**off** for the fail-closed checks.

## Habits

**F35 Habit types** (GD §16.1)
- [ ] Check habit: tap completes, pays as before.
- [ ] Count habit (target 5): +1 four times shows 4/5 and pays nothing; the fifth +1 completes and pays once; −1 on a completed one undoes it.
- [ ] Timed habit (15 min): "Start" opens a focus session of that length; finishing it completes the habit (TIMER); it cannot be ticked by hand.
- [ ] Avoid habit: holds all day; evening "Kept" pays; "I slipped" marks it missed and can be taken back until the day ends; an unconfirmed day is missed at rollover.
- [ ] "3 times per week" habit: due every day until 3 are done that ISO week, then not offered again that week.
- [ ] ★ Each habit control reads its name and progress in TalkBack.

**F36 Reminders + skip today** (GD §16.2)
- [ ] Set a reminder on 2 habits; a third is refused.
- [ ] Reminder fires at the set time. Note whether it still fires when the habit is already done.
- [ ] Skip today: the habit disappears for today, not counted as a miss; a second skip the same week is refused; skip can be undone until rollover.

## Focus

**F37 Quick Settings tile** · **F38 Start focus widget**
- [ ] Add the tile; tap it starts the last-used session; the tile shows time left while running.
- [ ] Add the 2×1 widget; one tap starts the last-used session. Note what it does on a fresh install with no last session.

**F39 Pomodoro** (GD §16.3)
- [ ] Start a 4-round Pomodoro: each focus round is an ordinary session; breaks never count as a breach.
- [ ] The break-over notification offers the next round; the next round starts only on tap.
- [ ] After the last round the session shows as complete. Note what happens when you leave mid-round.

**F40 Daily focus goal + streak** (GD §16.4)
- [ ] Set a goal; the ring on Gates fills with today's focus minutes.
- [ ] Meet the goal on 2 days in a row (time travel): focus streak 2, separate from the main streak.
- [ ] Missing a day resets the focus streak; no XP is ever paid for the streak.

## Gym

**F42 Progression hints** (GD §16.6)
- [ ] Hitting the top of the rep range on set 1 suggests the increment next session; below the range it suggests holding weight.

**F43 Personal records** (GD §16.7)
- [ ] Beat a heaviest weight mid-session: "New record" appears at once.
- [ ] One event per record type per exercise per session (no double counting).

**F44 Plate calculator** (GD §16.8)
- [ ] Plates per side for a target weight with your owned plates; switching kg/lb in the calculator converts.
- [ ] Try a weight your plates can't make exactly; note what it shows.

**F45 Exercise history + charts** (GD §16.9)
- [ ] Past sessions are listed; top weight, e1RM and volume charts match the logged sets.

## Screen & health

**F46 Pickups, first and last use** (GD §16.10)
- [ ] Screen time shows pickups and first/last use for today.
- [ ] With usage access **off**: no numbers, only the access prompt (vector #115).

**F47 Daily step goal ring** (GD §16.11)
- [ ] Health tile shows "6,200 / 8,000" style text and a partial ring; at the goal, a full ring and "Goal reached". No ledger rows.

**F48 Phone-down nights** (GD §16.12)
- [ ] Turn it on in the evening: tonight counts; the quest belongs to the wake day.
- [ ] Night kept (under the grace): paid on the wake day; a clock/alarm app never breaks it.
- [ ] Night broken (Instagram 6+ min): expired, nothing deducted; the morning card says broken.
- [ ] Usage access removed before morning: expired unpaid, morning card says no data (fails closed).
- [ ] Editing the window at 22:00 only applies from the night after.

**F49 Quit counters** (GD §16.13)
- [ ] Add a counter: days clean, money/time saved; a restart keeps the best run; restart log entries keep trigger and note.
- [ ] A back-dated start shows days, but milestones count only the in-app run.
- [ ] Auto doomscrolling: a day over the limit restarts the counter automatically; with usage unreadable the day pauses (fails closed). Never XP.

## Long arcs

**F51 Programs** (GD §16.15) — entry: "Programs" row on Status
- [ ] Start Steady 30 with Health on: 3 read-only habits (editing or removing is refused); they appear today.
- [ ] Turn Health **off** next day: the walking rule appears as "Walk for 30 minutes" (self-reported) for that day; turning Health back on restores the steps rule (ENGINE_DECISIONS #148).
- [ ] Leave a day undone: next open asks Restart / Continue (bottom sheet, swipe away allowed). Restart → today is day 1; Continue → day marked broken, count goes on; no answer by the next rollover → Continue.
- [ ] A rest day shows as rest, not broken.
- [ ] Ironclad with phone-down nights on: a night with usage unreadable does not break the day; a broken night does (ENGINE_DECISIONS #147).
- [ ] ★ The program calendar reads kept / rest / broken per day in TalkBack.
- [ ] (Optional, long) 30 kept days → COMPLETED (+150 gold); habits archived.

## After the run

- [ ] Settings → export, then import on a clean install: programs and marks survive.
- [ ] Nothing in the app reached a host other than the Path Oracle (check with a network monitor if you have one).
