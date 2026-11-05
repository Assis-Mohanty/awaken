# Phone test checklist — after the declutter (Batches 1–4)

Run on a real phone with a **debug** build (`./gradlew :app:assembleDebug`) over an **existing** install first (to prove the
v25 → v26 → v27 migrations keep your data), then once on a clean install. Both themes, and the starred checks with
**TalkBack** on. Tick the box, or write what happened next to it. The feature checks that still apply are in
`PHONE_TEST_v1_2.md`.

## Upgrade over existing data
- [ ] The app opens with no crash; level, streak, gold, habits and workout history are as before.
- [ ] Old Dungeon stage quests do not appear on Today and no error shows (they expire; no rows are deleted).
- [ ] An old rank, title or cosmetic you owned is simply gone: nothing broken, nothing blank where it was.
- [ ] Settings → export, then import on a clean install: it succeeds (same schema version).

## Today (five sections)
- [ ] The title is the date; no greeting and no header icons.
- [ ] Order: header row (level · rank, XP bar, streak) → Today's plan → Focus → Health & screen → Quit counters.
- [ ] Tapping the header row opens Progress.
- [ ] "Today's plan · N of M done" updates as you complete rows; *Plan day* and *Habits* work.
- [ ] Rows are grouped Morning / Day / Evening / Anytime; past groups are collapsed.
- [ ] Phone-down night: a row in Evening tonight and in Morning for last night (with the Wind down link).
- [ ] App limits row appears in Anytime only when a limited app exists.
- [ ] After a missed day (time travel): a Comeback row, no full-screen warning, no snackbar.
- [ ] Weekly review row appears only Sat–Mon.
- [ ] Quit counters section is hidden with no counters; with one it reads "Name · N days clean" and opens the counters.
- [ ] Health & screen: missing access shows the grant prompt, never a fake number.
- [ ] ★ Each row reads its name and state in TalkBack.

## Words and titles
- [ ] No game jargon on any screen (System, hunter, gate, directive, trial, oath, realm, fragment).
- [ ] Quest titles are plain and numbered (for example, a count or a duration), none cut off at 200% font size.
- [ ] Notifications (daily reminder, last call, focus) use the same plain wording.

## Progress
- [ ] Consistency score and streak card are here; the gold figure opens the Store.
- [ ] No achievements, titles or cosmetics anywhere (Progress, Store, Vault).
- [ ] Quit counters and Programs rows are still reachable.
- [ ] Radar, heatmap and weekly report render in both themes.

## Settings (grouped)
- [ ] Sections in order: Profile · Day & reminders · Goals · Health & training · Phone & focus · Habits · Appearance & sound · Your data.
- [ ] Phone & focus holds Screen time, Focus Lock, Phone-down nights and Reel counter; each opens its setup.
- [ ] Focus goal and step goal are under Goals.
- [ ] Profile has no Identities row.
- [ ] Theme System / Dark / Light all apply at once.

## Rewards
- [ ] Completing a quest pays the same XP every time (no variable or critical rewards).
- [ ] Undo within 10 minutes still reverses it; gold and XP return to the earlier values.
- [ ] A focus session that collapses (too much time away) shows the plain reason and pays nothing.

## Look
- [ ] Every screen sits on a flat page colour: no glow or tint behind Settings, Health, Screen time, Quit, Programs.
- [ ] Reel counter bubble and overlay still read clearly over a busy app.
