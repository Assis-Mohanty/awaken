# New ideas to implement

A backlog of features that are **not in the app yet**, collected from competitor research (Hevy, Strong,
Alarmy, Habitify, Brick, Daylio, QuitNow, Forest, Streaks, Life Reset, Withings and others) and from
earlier planning.

Not included: the fixes already being built from the latest fix prompt (habit delete, Today tiles,
custom focus and stopwatch, subjects, timer chime, gym rest timers), and the premium UI redesign.

## Ground rules for every feature here

- **Offline-first. No AI. No servers of our own.** Internet only where Google Play itself delivers
  something (e.g. an on-demand module download). Nothing sends user data anywhere.
- **No new money flows.** No subscriptions, no real-money stakes.
- **Every number marked `PROPOSED`** must be written into GAME_DESIGN.md and approved before code uses it.
- **Existing invariants still apply:** ledger-only economy, idempotent writes, one transaction per game
  action, rules in `:engine`, injected Clock.
- **Rewards only for things the phone can observe or the economy can bound.** No XP for opening the app,
  reflecting, or logging moods. Anything rewarded for *not* doing something must **fail closed** when the
  app can't see the data.

**Legend.** Status: 🆕 new idea · 💡 suggested earlier, not built. Priority: P1 (next) · P2 · P3 (later).
Effort: S ≈ 1 day · M ≈ 2–4 days · L ≈ 1–2 weeks.

---

## Summary

| ID | Feature | Area | Status | Priority | Effort | Verified by |
|---|---|---|---|---|---|---|
| A1 | Habit types: quantity, timed, negative, flexible frequency | Habits | 🆕 | P1 | M | Self / timer |
| A2 | Per-habit reminders + "skip today" | Habits | 🆕 | P1 | S | — |
| A3 | Morning / Day / Evening blocks | Today | 💡 | P2 | S | — |
| A4 | Quick Settings tile: start a Focus Gate | Today | 🆕 | P2 | S | Timer |
| A5 | More widgets: Start focus, steps ring, today's progress | Today | 🆕 | P2 | M | — |
| A6 | Habit chains | Habits | 💡 | P3 | S | Self |
| B1 | Pomodoro cycles | Focus | 🆕 | P1 | S | Timer |
| B2 | Daily focus goal ring + focus streak | Focus | 🆕 | P1 | S | Timer |
| B3 | Gate crystal that grows while you focus + gallery | Focus | 🆕 | P2 | M | Timer |
| B4 | Ambient sounds (rain, café, brown noise) | Focus | 🆕 | P2 | S | — |
| B5 | Distraction log on gate collapse | Focus | 🆕 | P2 | S | — |
| B6 | Gate Key: NFC/QR tap to start and end focus | Focus | 💡 | P1 | M | Scan + usage data |
| B7 | One-line session note at gate end | Focus | 💡 | P3 | S | — |
| C1 | Progression suggestions (double progression) | Gym | 🆕 | P1 | S | — |
| C2 | Personal records + estimated 1RM | Gym | 💡 | P1 | S | Logged numbers |
| C3 | Plate calculator | Gym | 🆕 | P1 | S | — |
| C4 | Warm-up set calculator | Gym | 🆕 | P2 | S | — |
| C5 | RPE / RIR per set | Gym | 🆕 | P2 | S | — |
| C6 | Supersets and circuits | Gym | 🆕 | P2 | M | — |
| C7 | Exercise history and charts | Gym | 🆕 | P1 | M | — |
| C8 | Bundled programs (PPL, Upper/Lower, Full body) | Gym | 🆕 | P2 | M | — |
| C9 | Exercise library with form cues + substitutions | Gym | 🆕 | P2 | M | — |
| C10 | Body weight and measurements log | Gym | 🆕 | P2 | S | Self |
| C11 | Progress photos with alignment overlay | Gym | 💡 | P2 | M | Camera-only, timestamped |
| C12 | Arena: camera rep counting | Gym | 💡 | P3 | L | On-device pose tracking |
| D1 | Phone pickups, first and last use | Screen | 🆕 | P1 | S | Usage data |
| D2 | Daily step goal ring | Health | 🆕 | P1 | S | Health Connect |
| D3 | Verified phone-down bedtime | Screen | 💡 | P2 | M | Usage data |
| D4 | Quit counters | Habits | 💡 | P1 | M | Self / usage data |
| E1 | QR/NFC place check-ins | Proof | 💡 | P1 | M | Scan |
| E2 | Wake-up missions | Proof | 💡 | P2 | M | Scan / steps / squats |
| E3 | Gym check-in by location | Proof | 💡 | P2 | M | Foreground GPS |
| E4 | "Verified %" score + proof history | Proof | 🆕 | P1 | S | — |
| F1 | Measurable goals | Progression | 💡 | P2 | M | Self, clamped |
| F2 | Archetypes + plateau detection | Progression | 💡 | P3 | S | — |
| F3 | Dungeons (multi-day challenges) | Progression | 💡 | P2 | M | Built from verified quests |
| F4 | Shadow: race your past self | Progression | 💡 | P2 | S | Ledger |
| F5 | Classes from stat profile | Progression | 💡 | P3 | S | — |
| F6 | Programs (30 / 66 / 75 days) | Progression | 💡 | P2 | M | Built from verified quests |
| F7 | Pledges with gold stakes | Progression | 💡 | P3 | M | Verified quests only |
| G1 | Personal insights | Reflection | 💡 | P2 | M | — |
| G2 | Weekly review | Reflection | 💡 | P2 | M | — |
| G3 | Consistency score ("never miss twice") | Reflection | 💡 | P1 | S | — |
| G4 | Mood + energy check-in, Year in Pixels | Reflection | 💡 | P2 | S | — |
| G5 | Monthly / yearly Wrapped | Reflection | 💡 | P3 | M | — |
| G6 | Identity onboarding ("Who are you becoming?") | Reflection | 💡 | P3 | S | — |
| G7 | Reading log and bookshelf | Reflection | 🆕 | P3 | S | Self / timer |
| H1 | Clean / System presentation mode | Look & feel | 💡 | P2 | M | — |
| H2 | Data-driven wallpapers | Look & feel | 💡 | P3 | S | — |
| H3 | App lock (PIN / biometric) | Look & feel | 🆕 | P2 | S | — |
| H4 | CSV export | Look & feel | 🆕 | P3 | S | — |
| H5 | Focus Home launcher | Look & feel | 💡 | P3 | L | — |

Note: G1, G2, G3, G5, G6, A3 and H1 are already specified in `docs/prompts/V1_1_FEATURES_PROMPT.md`. If
that brief has been run, skip them here.

---

## A. Habits and Today

### A1. Habit types beyond yes/no 🆕 · P1 · M
**What.** Four habit types besides the current tick-box:
- **Quantity:** "Drink 8 glasses", tap +1 through the day, partial progress ring.
- **Timed:** "Read 20 minutes", runs a timer and completes when it ends.
- **Negative:** "No sugar today", completes at day end unless the user marks a slip.
- **Flexible frequency:** "Gym 3× a week", any 3 days count, no fixed weekdays.

**Why.** This is the biggest fix for the "to-do app" feel. Streaks is praised for exactly these types.

**How.** Extend the `habit` table with `type` (BOOLEAN | QUANTITY | TIMED | NEGATIVE), `target`,
`unit`, `frequency` (DAYS_OF_WEEK | TIMES_PER_WEEK). Negative habits are evaluated at day rollover.
Timed habits reuse the Focus Gate timer and breach rule, so they count as TIMER-verified.

**Rewards.** Same XP formula. Negative habits are self-reported (SELF cap). Partial quantity gives no XP
until the target is met. Flexible habits count once per completion up to the weekly target.

**Open numbers (PROPOSED).** Max habits stays 8? Max quantity target per unit.

### A2. Per-habit reminders + "skip today" 🆕 · P1 · S
**What.** Each habit gets an optional reminder time. "Skip today" removes it from today without counting
as a miss (limited per week).

**How.** WorkManager or AlarmManager per habit, counted inside the existing 2 non-gate notifications/day
cap. Users choose which habits deserve a reminder slot.

**Open numbers (PROPOSED).** Skips per habit per week.

### A3. Morning / Day / Evening blocks 💡 · P2 · S
Group the Directive into time blocks; current block highlighted, past blocks collapsed. See V1_1 brief F25.

### A4. Quick Settings tile 🆕 · P2 · S
**What.** A tile in the notification shade: "Start focus". One tap starts the last-used gate and subject.

**How.** Android `TileService`. Shows remaining time as the tile label while a gate runs.

### A5. More home-screen widgets 🆕 · P2 · M
**What.** Three Glance widgets: Start focus (one tap), Steps ring, Today's progress ring + next quest.

**How.** Extend the existing Glance setup. Refresh on ledger writes, rollover and Health sync.

### A6. Habit chains 💡 · P3 · S
"After I wake up → meditate → read." Completing the whole chain the same day unlocks a small bonus
(PROPOSED) and an achievement.

---

## B. Focus

### B1. Pomodoro cycles 🆕 · P1 · S
**What.** A Pomodoro mode that chains focus and break periods automatically: focus → short break → focus
… → long break after N rounds.

**How.** A sequence of gate runs. Each focus block is a normal gate (breach rule applies). Breaks don't
breach. The soft chime plays at each switch.

**Open numbers (PROPOSED).** 25 / 5 / 15 minutes, long break after 4 rounds (the classic technique);
user-editable.

### B2. Daily focus goal ring + focus streak 🆕 · P1 · S
**What.** A ring on the Focus card: "1 h 20 of 2 h today". A separate focus streak for days the goal is met.

**How.** Sum cleared gate minutes per game-day. The goal is set in Settings.

**Open numbers (PROPOSED).** Default goal 120 minutes; streak achievement thresholds.

### B3. Gate crystal that grows while you focus 🆕 · P2 · M
**What.** During a gate, a crystal (or sigil) visibly grows. A cleared gate adds a finished crystal to a
**gallery** (one per session, coloured by subject). A collapsed gate leaves a cracked one. This is the
same idea that makes Forest work.

**How.** Vector drawing driven by elapsed/planned time; no image assets. Gallery is a grid over `gate_run`.

**Rewards.** None extra. It's motivation, not economy.

### B4. Ambient sounds 🆕 · P2 · S
**What.** Optional background loops during focus: rain, café, brown noise, forest.

**How.** 3–4 short CC0 loops bundled as OGG (keep total under ~1 MB to protect APK size), ExoPlayer or
MediaPlayer with gapless looping, volume slider. List sources and licences on the licences screen.

### B5. Distraction log 🆕 · P2 · S
**What.** When a gate collapses, one tap on why: Phone call · Social media · Message · Tired · Other.

**How.** Store on `gate_run`. Feeds insights ("most collapses happen after 9 pm, from social media").

### B6. Gate Key: NFC/QR tap to start and end focus 💡 · P1 · M
**What.** Like the Brick device: tap a sticker (NFC) or scan a printed QR to start a session; tap again to
end it. Put the sticker in another room so quitting early means walking there.

**How.**
- NFC via `NfcAdapter` reader mode (normal `NFC` permission, no prompt). NTAG213 stickers.
- QR via CameraX + **ZXing** (Apache-2.0, fully offline) to avoid any SDK that might send diagnostics.
- The app can generate and print the QR sticker.
- During the gate, read usage data: if a distracting app came to the foreground, the gate collapses.
  **Verify, don't block.** No Accessibility Service.

**Policy.** Camera permission only when the user first scans. Phones without NFC use QR.

### B7. One-line session note 💡 · P3 · S
At gate end: "What did you work on?" (optional, ≤ 140 chars). Shown in the weekly focus report.

---

## C. Gym

### C1. Progression suggestions 🆕 · P1 · S
**What.** "Last time you hit 3 × 12 at 50 kg → try 52.5 kg today." A fixed **double progression** rule:
work within a rep range; when every set reaches the top of the range, increase weight.

**How.** Pure function in `:engine` over the exercise's last session. Shown as a hint on set 1, never
forced.

**Open numbers (PROPOSED).** Default rep range 8–12; increment +2.5 kg upper body, +5 kg lower body;
user-overridable per exercise.

### C2. Personal records + estimated 1RM 💡 · P1 · S
**What.** PR badges per exercise: heaviest weight, most reps at a weight, best estimated 1RM, best session
volume. A small celebration when a PR is hit.

**How.** Estimated 1RM by the Epley formula: `weight × (1 + reps / 30)`. Computed in `:engine`.

**Rewards.** Optional achievement gold for PRs (PROPOSED). No XP multiplier.

### C3. Plate calculator 🆕 · P1 · S
**What.** Tap a weight → see which plates go on each side.

**How.** Greedy algorithm over available plates. Settings: bar weight and owned plates.

**Open numbers (PROPOSED).** Default bar 20 kg; plates 25, 20, 15, 10, 5, 2.5, 1.25 kg. kg / lb toggle.

### C4. Warm-up set calculator 🆕 · P2 · S
**What.** Before the first working set, suggest warm-up sets.

**Open numbers (PROPOSED).** 40% × 5, 60% × 3, 80% × 1 of the working weight, rounded to available
plates. Warm-up sets don't count toward volume or PRs.

### C5. RPE / RIR per set 🆕 · P2 · S
Optional "how hard was that?" per set: RPE 6–10 or reps in reserve. Used by progression hints (don't add
weight if the last sets were RPE 10).

### C6. Supersets and circuits 🆕 · P2 · M
**What.** Link two or more exercises; rest starts only after the last exercise of the group.

**How.** `group_id` on plan exercises. The rest timer uses the group's rest value.

### C7. Exercise history and charts 🆕 · P1 · M
**What.** Per exercise: every past session, plus charts for top weight, estimated 1RM and volume over
time (Week / Month / Year).

**How.** Queries over logged sets; charts through ChartKit.

### C8. Bundled programs 🆕 · P2 · M
**What.** Ready-made plans: Push/Pull/Legs, Upper/Lower, Full-body beginner (3 days), each with
exercises, sets, rep ranges and rest times. One tap to copy into the user's plans.

**How.** `assets/gym/programs.json`, validated by a test (like templates.json). Original names; no
trademarked programs.

### C9. Exercise library with form cues + substitutions 🆕 · P2 · M
**What.** 80–150 exercises, each with target muscles (feeds the muscle map), equipment, 3–4 short form
cues, and swaps for missing equipment ("no cable? → dumbbell fly").

**How.** `assets/gym/exercises.json`. Simple vector illustrations or none; no video (APK size).
Equipment filter reuses Settings → Equipment.

### C10. Body weight and measurements log 🆕 · P2 · S
**What.** Log weight, waist, chest, arms. A smoothed trend line (a 7-day moving average) so daily water
swings don't discourage people.

**How.** New `body_measurement` table, ChartKit trend.

**Rewards.** None. Weight must never earn XP.

### C11. Progress photos 💡 · P2 · M
**What.** In-app camera only, with a ghost overlay of the previous photo so poses line up. Side-by-side
compare, monthly time-lapse.

**How.** CameraX; photos stored in app-private storage (never the public gallery unless the user exports).
Requires H3 app lock. Excluded from cloud auto-backup by default (`dataExtractionRules`).

**Policy.** Camera permission on first use. Data never leaves the phone, so Data Safety is unchanged.

### C12. Arena: camera rep counting 💡 · P3 · L
**What.** Push-ups, squats and sit-ups counted by the camera, only full reps.

**How.** MediaPipe Pose Landmarker (Apache-2.0) on-device; counting rules (joint angles + hysteresis) in
`:engine` with recorded-landmark test vectors. Deliver the model as a Play on-demand module to keep the
base APK small.

**First step.** A one-day push-ups-only test of accuracy and size, with clips from 3–5 people in
different lighting.

**Policy.** Camera permission; frames processed on-device, never stored or uploaded. Update the PRD
(currently lists photo/video proof out of scope; reword to "on-device camera verification").

---

## D. Health and screen time

### D1. Phone pickups, first and last use 🆕 · P1 · S
**What.** On the screen-time detail: unlocks today, time of first pickup in the morning, last use before
sleep, longest stretch without the phone.

**How.** `UsageStatsManager.queryEvents`, counting `KEYGUARD_HIDDEN` events (API 28+) and foreground
events. Uses the existing Usage access permission.

**Rewards.** None directly; feeds insights and D3.

### D2. Daily step goal ring 🆕 · P1 · S
**What.** A ring on the Health tile: "6,200 / 8,000". Goal set by the user.

**Open numbers (PROPOSED).** Default 8,000.

### D3. Verified phone-down bedtime 💡 · P2 · M
**What.** "No phone after 11 pm" is completed automatically if no app was used from bedtime until a set
morning time. Optional guided breathing as a wind-down before bed.

**How.** Usage events in the window. **Fail closed:** no Usage access → quest not available, no XP.

**Open numbers (PROPOSED).** Grace minutes (e.g. alarm dismissal, a call).

### D4. Quit counters 💡 · P1 · M
**What.** "Days clean" for things users want to stop: smoking, porn, junk food, doomscrolling, alcohol.
Money saved (cost per day × days), time reclaimed, milestones (1, 3, 7, 14, 30, 90, 180, 365 days), a
relapse log with optional trigger note, and **best run kept forever** (no shame on reset).

**How.** New `quit_counter` and `relapse` tables. Doomscrolling can be auto-checked against usage data.

**Rewards.** Milestone achievements only (gold PROPOSED); no daily XP for self-reported abstinence beyond
the SELF cap.

**Care.** Wording is supportive, never shaming. No medical or health-recovery claims.

---

## E. Proof and verification

### E1. QR/NFC place check-ins 💡 · P1 · M
**What.** Link a habit to a sticker placed where the habit happens (gym bag, desk, bookshelf, bathroom).
Scanning it completes the habit as **scan-verified**.

**How.** Shares the scanner with B6. Each sticker has a random ID stored locally; one sticker ↔ one habit.

**Rewards.** A new verification level `SCAN`. Its multiplier is PROPOSED; it must sit between SELF and
TIMER, because stickers can be photocopied.

### E2. Wake-up missions 💡 · P2 · M
**What.** An alarm that only stops after a mission: scan a QR in the bathroom, take N steps, do N
squats, or solve a maths problem. Completes a "Wake up by 6:30" quest as verified.

**Policy (check before building).** Exact alarms and full-screen alarm notifications are restricted on
recent Android versions; Play limits full-screen intents to apps whose core function is an alarm. Read
the current policy and design within it.

### E3. Gym check-in by location 💡 · P2 · M
**What.** Save the gym's location once. Check in on arrival and check out when leaving; the visit counts
if both are within the radius and the stay is long enough.

**How.** Foreground location only, at check-in and check-out. No background location. GPS works without
internet.

**Open numbers (PROPOSED).** Radius 150 m; minimum stay 30 minutes.

### E4. "Verified %" score + proof history 🆕 · P1 · S
**What.** On Status: "71% of this week's XP was verified." Each verified completion keeps its evidence
(scan time, gate duration, step count, location check-in), viewable in quest history. A tick-box leaves
nothing; proof leaves a record.

**How.** Ledger already has `breakdown_json`; add an `evidence_json` field on completions.

---

## F. Progression and long arcs

### F1. Measurable goals 💡 · P2 · M
A goal with a number: "CF rating 1400 → 1900", "5K time 32:00 → 25:00". User logs the number weekly; the
engine shows a clamped weekly step (±15% rule, PROPOSED) and a progress bar on Status.

### F2. Archetypes + plateau detection 💡 · P3 · S
Replace UP/HOLD/DOWN with PROGRESSION / CHALLENGE / COMEBACK / MASTERY / PLATEAU_BREAKER, computed from
completion rate and the metric trend. Changes which templates and targets are served.

### F3. Dungeons 💡 · P2 · M
A 3–7 day staged challenge built from Hard template quests, with a gold entry fee (a healthy sink). Miss
a stage → lose the fee once; no escalating penalty. Clear → lump reward + loot fragment.

### F4. Shadow: race your past self 💡 · P2 · S
"Today: 340 XP. Your shadow (this day last week): 290 XP. +50 ahead." A query over the ledger.

### F5. Classes 💡 · P3 · S
At rank D, a class from the dominant stats: Warrior (STR), Scholar (INT), Monk (WIL), Warden (VIT),
Envoy (CHA). Shown next to the name.

### F6. Programs (30 / 66 / 75 days) 💡 · P2 · M
Long structured programs with fixed daily rules and a day counter; break a rule → choose restart or
continue (with the day marked). Like Life Reset's 66-day plan. Original names only ("75 Hard" is
trademarked).

### F7. Pledges with gold stakes 💡 · P3 · M
Morning: stake gold or a shield on a **verified** quest. Completed → stake returned; failed → lost
(a sink). Opt-in, capped, in-game currency only. Research shows stakes help short-term, but only with
verification.

---

## G. Reflection and insights

### G1. Personal insights 💡 · P2 · M
On-device correlations with guardrails: "On days you sleep 7 h+, you complete 38% more quests." Minimum
data before showing anything, correlational wording, never medical advice. Each insight has an action
button ("make Tuesdays lighter"). See V1_1 brief F23.

### G2. Weekly review 💡 · P2 · M
Sunday, 5 taps: this week's numbers, what went well, what slipped, next week's focus. No XP.

### G3. Consistency score 💡 · P1 · S
"22 of the last 28 days" next to the streak; "Missed yesterday. Don't miss twice." on the next day.

### G4. Mood + energy check-in, Year in Pixels 🆕/💡 · P2 · S
Two taps a day (mood 1–5, energy 1–5), a colour grid of the year. Feeds G1. No XP.

### G5. Monthly / yearly Wrapped 💡 · P3 · M
Story cards: quests, focus hours, best streak, top stat, PRs, steps. Final card shares as an image.

### G6. Identity onboarding 💡 · P3 · S
"Who are you becoming?" Pick 1–3 identities that pre-select focus stats and appear in the weekly review.

### G7. Reading log and bookshelf 🆕 · P3 · S
**What.** Add books manually (title, author, pages), log pages or reading sessions (timer), a shelf of
finished books, pages per week.

**How.** No ISBN lookup (that would need an API); optionally scan the barcode just to store the number.

---

## H. Look and feel

### H1. Clean / System presentation mode 💡 · P2 · M
One setting that swaps game vocabulary (Focus session ↔ Gate, Challenge ↔ Dungeon …) and System voice.
Default Clean. See V1_1 brief F28.

### H2. Data-driven wallpapers 💡 · P3 · S
In-app generated wallpapers from the user's data (year in dots, consistency grid, level ring, identity
line, focus black), applied with `WallpaperManager`. Daily refresh is opt-in.

### H3. App lock (PIN / biometric) 🆕 · P2 · S
**What.** Optional lock on opening the app, required before C11 progress photos and D4 relapse logs ship.

**How.** `BiometricPrompt` with device-credential fallback. Lock after N minutes in background
(PROPOSED).

### H4. CSV export 🆕 · P3 · S
Export habits, gym sets, focus sessions and body measurements as CSV for spreadsheets (alongside the
existing JSON backup). Storage Access Framework, no permission needed.

### H5. Focus Home launcher 💡 · P3 · L
Optional minimalist home screen: clock, next quest, streak, 6–8 favourite apps as text, search, and a
pause screen before distracting apps. Component disabled by default; no Accessibility Service, no
QUERY_ALL_PACKAGES. Build after launch as a major update.

---

## Recommended build order

1. **A1 + A2:** habit types and reminders (fixes the to-do-app feel at its root).
2. **C1, C2, C3, C7:** gym depth (progression hints, PRs, plate calculator, charts).
3. **B1, B2:** Pomodoro cycles and the daily focus ring.
4. **D1, D2, G3, E4:** cheap wins that reuse data you already have.
5. **E1 + B6:** QR/NFC check-ins and Gate Key (one scanner, two features).
6. **D4:** quit counters.
7. **H3** (app lock), then **C11** (progress photos) and **C10** (measurements).
8. **F3, F4, F6:** dungeons, shadow, programs.
9. Everything else, then **C12** (Arena) and **H5** (launcher) as headline updates.

## Explicitly out of scope (needs servers, AI or money)

Accounts and sync, friends, leaderboards, guilds, PvP, AI Paths (dormant), AI chronicle or mentor,
real-money stakes, subscriptions, ads, Google Fit APIs (shut down end of 2026), app blocking via
Accessibility Service.
