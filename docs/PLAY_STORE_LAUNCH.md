# AWAKEN — Google Play Launch Plan

## 1. The constraint that decides your timeline

If your Play developer account is a **personal** account created after 13 Nov 2023, Google requires a **closed test with ≥ 12 testers opted in for 14 consecutive days** before you can even *apply* for production; that application review usually takes up to ~7 days. Organisation accounts (need a D-U-N-S number) are exempt but take longer to register.

So "published in 2–3 days" realistically means:

| Day | What is public |
|---|---|
| Day 3 | Build in **Internal testing** (instant, up to 100 testers) + **Closed testing** track live for your 12+ testers. Also a signed APK you can share directly. |
| Day 3 → 17 | Closed test runs. You ship v1.1 updates to testers from feedback. |
| ~Day 17–24 | Apply for production → review → **public on Play Store**. |

Start recruiting testers **today**, not on Day 3. A tester who opts out and rejoins restarts their own 14-day clock.

### Getting 12+ real testers (in order of quality)
1. Friends/classmates with Android phones (ask for 20 — some won't opt in). Make a Google Group (personal Gmail-owned group, not Workspace) and add the group as the tester list.
2. Your college/coding communities, gym friends, anime/self-improvement Discords and subreddits (r/SoloLeveling fans are your exact audience — but post as "I built an original habit RPG", follow each sub's self-promo rules).
3. Tester-exchange communities (you test theirs, they test yours). Avoid paid tester farms — low engagement can lead Google to ask for more testing.
Ask testers to **open the app on several days**, not just install it.

## 2. Play Console checklist

- [ ] Developer account verified (identity, ₹/$25 one-time fee paid).
- [ ] App created: name "AWAKEN: Hunter System" (≤30 chars), default language en-US, App, Free.
- [ ] **Play App Signing** enabled; upload key = your keystore. **Back up the keystore + passwords in two places.** Losing it means a reset request.
- [ ] Target API 36 (required for new apps/updates as of 31 Aug 2026).
- [ ] App content:
  - [ ] Privacy policy URL (GitHub Pages)
  - [ ] Ads: No
  - [ ] App access: All functionality available without special access
  - [ ] Content rating questionnaire (expect Everyone/3+ — no violence/gambling; Relic Cache is earned-only, no purchase)
  - [ ] Target audience: 18+ (avoids Families policy obligations; set 13+ only if you're ready for those requirements)
  - [ ] Data safety (see §4)
  - [ ] **Health apps declaration**: select Activity & fitness / Sleep; justify each Health Connect data type (§5)
  - [ ] Government app: No; Financial features: No
- [ ] Store listing (§3), graphics (§6)
- [ ] Internal testing release (sanity-check install from Play) → Closed testing release with tester list + feedback email
- [ ] Day 14+: Production access application (Google asks about your testing; answer specifically: what testers found, what you fixed, why you're ready)

## 3. Store listing copy (draft — edit in your voice)

**Title (≤30):** `AWAKEN: Hunter System`
**Short description (≤80):** `Level up in real life. Quests, ranks and stats from real, verified effort.`

**Full description (≤4000):**
```
You have been chosen.

AWAKEN turns your real life into a hunter's progression system. Every workout, study session, early night and hard conversation becomes XP. Level up five stats, clear daily directives, break into Focus Gates, and earn your rank from E to S.

THE SYSTEM CAN SEE YOUR EFFORT
• Verified quests: steps, workouts and sleep complete automatically through Health Connect — and earn 30% more XP.
• Focus Gates: timed deep-work dungeons. Leave the app too long and the gate collapses.
• Every reward shows its formula. No mystery numbers.

A DAILY DIRECTIVE BUILT FOR YOU
• A main quest and side quests every day, tuned to your focus stats.
• The System recalibrates difficulty weekly based on what you actually complete.
• Add your own habits. Tell it what equipment you have. Injured? Recovery Mode adapts.

HARD, NEVER CRUEL
• Miss a day and the Penalty Zone gives you a comeback quest — not a wipe.
• Plan rest days. Use Streak Shields. Your progress is never deleted.

GROW EVERY STAT
• Strength, Intelligence, Willpower, Vitality, Charisma — watch your stat pentagon grow.
• Rank Trials to earn promotion. 30 achievements and titles. Story chapters unlock as you rise.
• Weekly reports you can share.

PRIVATE BY DESIGN
• No account. Works offline. Your data stays on your phone.
```

**Category:** Health & Fitness (primary competitor set) — alternative: Productivity. Pick Health & Fitness if Health Connect verification is in the build.
**Tags:** Habit tracker, Fitness, Productivity, Motivation.
**ASO keywords to weave naturally into the description:** habit tracker, level up, RPG, daily quests, hunter, rank, gamified, self improvement, workout, study timer, pomodoro, streak, discipline.

## 4. Data safety form (v1 — offline, no INTERNET permission)

- Does your app collect or share any of the required user data types? → **No.** (Data processed only on-device and never sent off the device is not "collected" under Play's definition.)
- Health Connect reads happen on-device; nothing is transmitted. Keep it that way in v1 or this answer changes.
- Encryption in transit: N/A. Deletion: users can wipe data in Settings and by uninstalling — mention it in the policy.
Re-do this form the moment you add sync, analytics, or crash SDKs.

## 5. Health apps declaration — justification text (adapt)
- **Steps (read):** "Used to automatically mark user-chosen step-count quests as complete on-device and award in-app experience points. Not shared or uploaded."
- **Exercise sessions (read):** "Used to verify workout quests (e.g., 30 minutes of exercise) on-device."
- **Sleep sessions (read):** "Used to verify the user's opt-in sleep-duration quest (e.g., 7 hours) on-device."
Request nothing else. The privacy policy link shown inside Health Connect must be the same as the Play listing's.

## 6. Graphics
- App icon 512×512 (original portal/gate glyph — no series artwork).
- Feature graphic 1024×500: dark void, chamfered System Window with "LEVEL UP — 24", tagline "Level up in real life."
- Phone screenshots (min 2, use 6–8, 9:16, 1080×1920+). Use Screenshot mode (Phase 14). Order = story:
  1. Today directive with a verified quest ("The System can see your effort")
  2. Level-up ceremony ("Real effort. Real levels.")
  3. Reward sheet with formula breakdown ("Every XP explained")
  4. Focus Gate dial ("Focus Gates: deep work that counts")
  5. Status radar + heatmap ("Grow five stats")
  6. Penalty Zone comeback ("Hard, never cruel")
  7. Rank Trial ("Earn your rank: E → S")
  8. Widget on home screen
- Optional promo video later (30 s screen recording with captions).

## 7. Privacy policy template (host at `https://<you>.github.io/awaken/privacy-policy/`)
```
AWAKEN Privacy Policy — effective <date>

AWAKEN ("the app") is developed by <your name>, <city, India>. Contact: <email>.

What the app stores: your hunter profile, quests, habits, progress and settings are stored only on your device.
The app has no account system and no internet access. We do not collect, transmit, sell, or share personal data.

Health Connect: if you choose to enable verified quests, the app reads Steps, Exercise sessions and Sleep sessions
from Health Connect on your device to mark matching quests complete. This data is processed on-device only, is never
uploaded, and is not used for advertising. You can revoke access anytime in Health Connect settings.
Use of information received from Health Connect adheres to the Health Connect Permissions policy, including the
Limited Use requirements.

Backups: Android may include app data in your Google account device backup according to your phone's backup settings.
You can export or delete all data from Settings → Data. Uninstalling the app deletes its data from the device.

Children: the app is not directed to children under 13.
Changes: we will update this page and the effective date if this policy changes.
```

## 8. After launch (week 1 routine)
- Reply to every review within 24 h (it lifts ratings and teaches you what to fix).
- Watch Android vitals for crashes/ANRs daily.
- Ship a small update weekly for the first month (Play favours actively maintained apps; testers see you care).
- v1.1: in-app review prompt (Play In-App Review API) **after** the 3rd cleared day + a level-up — never on first launch. Check the merged manifest for any permission it adds and update Data safety if needed.
