# START HERE — AWAKEN build kit (A → Z)

You're going to hand an Android app build to Claude Code. This kit is everything it needs. Read this file once, top to bottom (10 minutes), then follow it.

## The honest version first

1. **There's no deadline, but there is a gate.** If your developer account is a personal one made after Nov 2023, Google requires 12+ testers in a closed test for 14 straight days before you can go public. Start recruiting testers early and put a build in their hands as soon as the core loop (Phase 12) is solid. (Details: `docs/PLAY_STORE_LAUNCH.md`.)
2. **Don't build the microservices for this.** Your old design (13–29 services, Kafka, PvP, guilds) is a 6-month, 8-engineer plan. v1 is an offline-first Android app with the same *invariants* (ledger, idempotency, deterministic rewards) plus **one** small stateless proxy for AI Paths. Social comes in v1.2 on top of verified data.
3. **Don't call it Solo Leveling.** Name, art, and quotes from the series are a takedown waiting to happen. Working name here is **AWAKEN** — rename freely (one find-and-replace before Phase 1).
4. **Your GitHub repo has live API keys committed.** Rotate them before you do anything else. See `docs/REPO_AUDIT.md`.
5. "Not gonna code it on my own" is fine for speed — but you're the reviewer. Every phase: run it on your phone, read the diff of `:engine`, and paste the review prompt. Claude Code builds; you're accountable for what ships.

## What's in this kit

| File | What it is | Who reads it |
|---|---|---|
| `CLAUDE.md` | Project memory: stack, invariants, conventions, commands | Claude Code (auto-loaded) |
| `docs/PRD_MVP.md` | What we build in v1 and what we don't | You + Claude Code |
| `docs/GAME_DESIGN.md` | Every formula and number, with test vectors | Claude Code (source of truth) |
| `docs/ARCHITECTURE.md` | Modules, Room schema, core flows, testing | Claude Code |
| `docs/DESIGN_SYSTEM.md` | Visual language, tokens, components, screens, copy voice | Claude Code + you for review |
| `docs/PROMPTS.md` | 17 copy-paste phase prompts + review/debug prompts | You → Claude Code |
| `docs/PLAY_STORE_LAUNCH.md` | Console checklist, listing copy, data safety, Health Connect declaration, privacy policy | You |
| `docs/MARKET_ANALYSIS.md` | Competitors and the gaps AWAKEN fills | You |
| `docs/REPO_AUDIT.md` | Review of your existing backend repo + project docs | You |

## Decisions already made (so you don't burn time)

- **Kotlin + Jetpack Compose, native Android.** Best Health Connect integration, best animation/haptics control, smallest APK, and Claude Code is strong at it. Flutter would buy iOS later at the cost of native polish now; you said Android only.
- **Offline-first, no account.** All game state on the phone. No user database to run, secure or leak.
- **AI Paths are the headline feature, done safely.** The user states a goal; an LLM writes a personal milestone ladder and weekly quests; the deterministic engine validates and prices every quest. The LLM key lives only in a stateless Cloudflare Worker that authenticates the *install* with Play Integrity (no login), rate-limits, caps spend, and stores nothing. An API key in an APK is a stolen API key, so it never goes there. Templates stay as the offline fallback.
- **Differentiator:** personal Paths + verified quests (Health Connect + Focus Gates) + the best-designed UI in the niche. Competitors are generic *and* honor-system; AWAKEN is neither.

## Setup (Day 0 — tonight, ~1.5 h)

1. **Security first:** rotate the 3 API keys from the old repo. `git rm --cached crewai/.env`, commit.
2. Install **Android Studio** (latest stable) → SDK Platform 36, an emulator image (Pixel, API 36). Enable USB debugging on your real phone.
3. Install **Health Connect** on your phone if it's Android 13 or lower (built in on 14+). Install Claude Code and log in with your Pro account.
4. Android Studio → New Project → **Empty Activity** (Compose) → name `AWAKEN`, package `com.assismohanty.awaken`, minimum SDK API 28, Kotlin DSL. Run it once on your phone.
5. `git init` in the project, create a **new** GitHub repo (e.g. `awaken-android`, private for now), push.
6. Copy `CLAUDE.md` to the project root and the `docs/` folder into the project. Commit: `chore: add build kit`.
7. Play Console: check your account type and identity verification. Create the app entry. Create a Google Group for testers and start inviting people **now**.
8. Open a terminal in the project folder → `claude`. Ask: "Read CLAUDE.md and summarise the invariants and the phase plan in docs/PROMPTS.md." If its summary is wrong, fix the docs before building.
9. For AI Paths (before Phase 14): a Cloudflare account (free tier), an LLM provider API key with a **monthly spend limit set in the provider console**, and a Google Cloud project linked to your Play Console app for Play Integrity (service account with the Play Integrity API enabled).

## The plan (milestones, not deadlines)

| Milestone | Phases (`docs/PROMPTS.md`) | You, in parallel |
|---|---|---|
| **M1: the engine is right** | 1 Foundation → 2 Engine + tests → 3 Content → 4 Data layer → 5 Design system | Recruit testers; read every engine test; review the design catalog on your phone |
| **M2: the game is fun offline** | 6 Onboarding/Today/completion → 7 Days/streaks/penalty/trials → 8 Focus Gates → 9 Status → 10 Vault/settings → 11 Health Connect → 12 Notifications/widget | Use the app daily for a week before moving on; log bugs in `docs/BACKLOG.md` |
| **M3: the System knows your goal** | 13 Path engine → 14 Path Oracle proxy → 15 Paths in the app | Write 10 of your own real goals into the eval set; read every generated quest in `eval/out.md` before trusting the prompt |
| **M4: shippable** | 16 Hardening/release → 17 Store kit | Keystore, privacy policy on GitHub Pages, screenshots, Console forms, Internal → Closed testing; test Paths from a Play-installed build |
| **M5: public** | Fixes from testers, prompt v2 | 14 days of active testers → production application |

No deadline means no excuse to skip the reviews. Don't cut quality on the ledger, rollover, the validator, or the core loop.

## Rules for working with Claude Code (these matter more than the prompts)

- One phase per fresh context (`/clear`). Plan mode first. Approve plans only when they match the docs.
- **Never accept invented numbers.** If XP, prices, or thresholds appear that aren't in `GAME_DESIGN.md`, reject them.
- **Never let model output touch a reward.** If you see a diff where a value from the proxy response flows into XP, gold, difficulty or verification without going through `PathValidator` + `PathPricing`, that's the most serious bug this app can have.
- Tests green + app runs on your phone before every commit. Red → fix before moving on; bugs compound.
- When it's wrong twice on the same thing, stop, `/clear`, and give it a sharper prompt with the failing test.
- Your usage window is shared with the Claude app and resets every 5 hours — start heavy phases (2, 4, 6) right after a reset.
- Update `CLAUDE.md` whenever you or it learns a rule the hard way. That's how the next session stops repeating mistakes.

## Definition of done for the launch build
`docs/PRD_MVP.md` §9. Short version: all features work on a real phone, all tests pass, QA checklist ticked, signed AAB on Closed testing with all Console forms complete.

## What to do right now
Rotate the keys. Message 20 friends to be testers. Install Android Studio. Then Phase 1. Ask 10 of those friends what goal they'd give the System; those become your first eval set.
