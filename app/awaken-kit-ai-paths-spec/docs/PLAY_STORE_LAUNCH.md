# AWAKEN — Google Play Launch Plan

## 1. The constraint that decides your timeline

If your Play developer account is a **personal** account created after 13 Nov 2023, Google requires a **closed test with ≥ 12 testers opted in for 14 consecutive days** before you can even *apply* for production; that application review usually takes up to ~7 days. Organisation accounts (need a D-U-N-S number) are exempt but take longer to register.

So the earliest public date is **(day your closed test starts) + 14 days + review (~up to 7 days)**:

| Step | What is public |
|---|---|
| Closed test starts (after Phase 12 or Phase 16) | Build in **Internal testing** (instant, up to 100 testers) + **Closed testing** track live for your 12+ testers. Also a signed APK you can share directly (note: AI Paths won't pass Play Integrity on a sideloaded APK). |
| +0 → +14 days | Closed test runs. Ship updates (including AI Paths if not already in) from feedback. |
| +14 → ~+21 days | Apply for production → review → **public on Play Store**. |

Start recruiting testers **today**, not when the build is ready. A tester who opts out and rejoins restarts their own 14-day clock.

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
  - [ ] AI-generated content: confirm the in-app "Report this quest" flow works without leaving the app, and that the prompt content rules exclude restricted content (Play's AI-Generated Content policy; re-read the current policy page before submitting)
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

A PATH BUILT FOR YOUR GOAL
• Tell the System what you're leveling toward: a rating, a race, a language, a book list.
• It charts milestones and writes each week's quests from what you actually completed.
• Too easy? Too hard? Say so, and next week adapts.
• Rewards are calculated on your phone by fixed rules. The AI writes the quests, never the XP.

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
• No account. Works offline. Your progress stays on your phone.
• AI Paths are optional. Only your goal and quest results are sent to write your quests, and our server keeps none of it.
```

**Category:** Health & Fitness (primary competitor set) — alternative: Productivity. Pick Health & Fitness if Health Connect verification is in the build.
**Tags:** Habit tracker, Fitness, Productivity, Motivation.
**ASO keywords to weave naturally into the description:** habit tracker, level up, RPG, daily quests, hunter, rank, gamified, self improvement, AI coach, personalised plan, workout, study timer, pomodoro, streak, discipline.

## 4. Data safety form (v1 — offline game + opt-in AI Paths)

Because AI Paths send user-entered text off the device, the answer is no longer "No data collected". Draft answers below; **re-read Play's current Data safety definitions when you fill the form** and make the answers match what the app actually does.

| Data type (Play category) | Collected? | Shared? | Ephemeral? | Required/optional | Purpose |
|---|---|---|---|---|---|
| App activity → **Other user-generated content** (Path goal, current level, notes) | Yes | No (sent to our service provider on our behalf) | **Yes**, processed only to answer the request | Optional (AI Paths opt-in) | App functionality |
| App activity → **Other actions** (last week's Path quest titles, outcomes, feedback) | Yes | No | **Yes** | Optional | App functionality |
| Device or other IDs (random install ID; Play Integrity verdict) | Yes | No | Install ID hash kept in proxy logs 30 days | Optional | Fraud prevention, security |
| App activity → reports (only when the user taps "Report this quest") | Yes | No | No, kept 90 days | Optional | App functionality (content safety) |
| Health data, location, contacts, name, email | **No** | No | — | — | — |

- Encrypted in transit: **Yes** (HTTPS only, cleartext disabled).
- Users can request deletion: proxy keeps no user data; reports carry no identifier, so explain that in the policy. On-device data: Settings → Data → Wipe, or uninstall.
- Health Connect reads still happen on-device only and are never sent to the proxy. Keep it that way or this table changes.
- The LLM provider processes the request as your service provider. Check its current data-retention terms for API traffic and state them in the privacy policy.
Re-do this form whenever you add sync, analytics, or crash SDKs, or change what the Path request contains.

## 5. Health apps declaration — justification text (adapt)
- **Steps (read):** "Used to automatically mark user-chosen step-count quests as complete on-device and award in-app experience points. Not shared or uploaded."
- **Exercise sessions (read):** "Used to verify workout quests (e.g., 30 minutes of exercise) on-device."
- **Sleep sessions (read):** "Used to verify the user's opt-in sleep-duration quest (e.g., 7 hours) on-device."
Request nothing else. The privacy policy link shown inside Health Connect must be the same as the Play listing's.

## 6. Graphics
- App icon 512×512 (original portal/gate glyph — no series artwork).
- Feature graphic 1024×500: dark void, chamfered System Window with "LEVEL UP — 24", tagline "Level up in real life."
- Phone screenshots (min 2, use 6–8, 9:16, 1080×1920+). Use Screenshot mode (Phase 17). Order = story:
  1. Path with milestone ladder and this week's quests ("Tell the System your goal")
  2. Today directive with a Path quest and a verified quest ("The System can see your effort")
  3. Level-up ceremony ("Real effort. Real levels.")
  4. Reward sheet with formula breakdown + feedback chips ("Every XP explained")
  5. Focus Gate dial ("Focus Gates: deep work that counts")
  6. Status radar + heatmap ("Grow five stats")
  7. Penalty Zone comeback ("Hard, never cruel")
  8. Rank Trial ("Earn your rank: E → S")
  (Widget screenshot dropped to stay within 8; use it in the promo video.)
- Optional promo video later (30 s screen recording with captions).

## 7. Privacy policy template (host at `https://<you>.github.io/awaken/privacy-policy/`)
```
AWAKEN Privacy Policy — effective <date>

AWAKEN ("the app") is developed by <your name>, <city, India>. Contact: <email>.

What the app stores: your hunter profile, quests, habits, Paths, progress and settings are stored only on your device.
The app has no account system. We do not sell your data or use it for advertising.

AI Paths (optional): if you turn on AI Paths, the app sends the following to our server, which forwards it to our AI
provider, <provider name>, to write your Path quests: your Path goal, current level and notes as you typed them; your
chosen stats, active days and weekly time; your equipment and injury settings; and the titles, outcomes and
"too easy / right / too hard" feedback of last week's Path quests. It does not send your name, health data, XP, level
or any account information. Requests are checked with Google Play Integrity and identified only by a random install ID.
Our server does not store the content of requests or responses; it keeps a one-way hash of the install ID with request
counts for 30 days to prevent abuse. <Provider name> processes requests under its API terms: <state its current
retention period>. Rewards (XP, gold, levels) are always calculated on your device by fixed rules, never by the AI.
You can turn AI Paths off anytime in Settings; existing Paths stay on your device.
Reports: if you report a quest, its text and your chosen reason are sent and kept for up to 90 days to improve safety.
Reports contain no identifier.

Health Connect: if you choose to enable verified quests, the app reads Steps, Exercise sessions and Sleep sessions
from Health Connect on your device to mark matching quests complete. This data is processed on-device only, is never
uploaded (including to AI Paths), and is not used for advertising. You can revoke access anytime in Health Connect settings.
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
