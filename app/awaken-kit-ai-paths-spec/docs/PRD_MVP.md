# AWAKEN — MVP PRD (v1.0, Android)

**Working name:** AWAKEN — *Real-life Hunter System*. (Do **not** ship as "Solo Leveling" — see §8.)
**Platform:** Android only, phones (tablets render centred). Offline-first, no account. One network feature: opt-in AI Paths through our own stateless proxy.
**Build window:** no fixed deadline; quality over speed. **Store:** closed test as soon as the core loop is solid → production after Google's testing gate (see `PLAY_STORE_LAUNCH.md`).

## 1. Problem & positioning

Habit/RPG apps are a crowded but low-quality niche on Play: Habitica (aging, punishing, no charts), and a wave of "Solo Leveling"-style clones with 10K–50K installs and ~3.9–4.2 ratings whose reviews complain about rigid workouts, no offline mode, and broken polish. Every one of them is **honor-system** (you tap a box, you get XP) and **generic** (the same "do 20 push-ups" list for a competitive programmer and a marathoner).

**AWAKEN's wedge — "The System knows your goal and can see your effort":**
1. **AI Paths: quests built for you.** Tell the System your goal ("reach 1900 on Codeforces", "read 12 books this year", "run a 10K"). It charts a milestone ladder and writes each week's quests from what you actually completed and how hard they felt. The engine, not the AI, prices every reward, so personalisation can't be gamed into XP.
2. **Verified quests.** Steps, workouts and sleep auto-complete from Health Connect; study and deep work are verified by in-app Focus Gates. Verified XP earns 1.15–1.30×. Hard Path quests run as Focus Gates, so the big rewards always mean measured effort.
3. **Hard, never cruel.** Penalty Zone = comeback quest, plus Rest Days, Recovery Mode, Injury Mode and equipment filters. No HP death, no wipes.
4. **Feels like a System, not a checklist.** The most polished UI in the category, a level-up ceremony people screenshot, and transparent formulas ("why did I get 144 XP?").
5. **Private by design.** No sign-up, works offline. AI Paths are opt-in, and our server stores nothing about you.

**One-line pitch:** *Tell the System your goal. It builds your path. It only counts effort it can trust.*

## 2. Target user (v1)
18–28, anime/gaming-literate, self-improvement curious (gym, study, coding, discipline content), Android, India + global English. Has tried a habit app and quit within two weeks.

## 3. Goals & success metrics (first 30 days after production)
| Metric | Target | How measured |
|---|---|---|
| D1 / D7 / D30 retention | 40 % / 18 % / 8 % | Play Console → Statistics (retained installers) |
| Rating | ≥ 4.5 | Play Console |
| Crash-free users | ≥ 99.5 % | Android vitals |
| AI Paths adoption | ≥ 40 % of weekly active installs request a week plan | Proxy aggregate counters (distinct install-ID hashes per week; no user data) ÷ Play weekly actives |
| Path plan quality | ≥ 90 % of blueprints survive PathValidator; < 1 report per 500 Path quests | Proxy metrics |

(No analytics SDK in v1 is a deliberate trade-off for the privacy promise. Add privacy-respecting, opt-in analytics in v1.1 if you need funnels.)

## 4. In scope (v1.0) — must ship

| # | Feature | Spec |
|---|---|---|
| F1 | Awakening onboarding (≤ 60 s, no account) | DESIGN_SYSTEM §4.1 |
| F2 | Hunter profile: level, 5 stats, rank, title, gold, streak | GAME_DESIGN §2–5 |
| F3 | Daily Directive: 1 main + Path quests + 1–3 side quests (template library), deterministic per day, novelty window, equipment/injury filters, 1 free reroll | GD §6 |
| F4 | Custom habits (max 8, days-of-week) | GD §6.1 |
| F5 | Quest completion pipeline w/ ledger, caps, crits, undo (10 min), reward sheet with formula breakdown | GD §4, §9.3; ARCH §3.1 |
| F6 | Level-up ceremony; rank-trial unlock | GD §4.4, §5; DS §2.5 |
| F7 | Streak, Rest Day, Recovery Mode, Streak Shield, Penalty Zone | GD §7 |
| F8 | Focus Gates (E/D/C/B timer dungeons, breach rule) | GD §8; ARCH §3.3 |
| F9 | Health Connect verified quests (Steps, ExerciseSession, SleepSession; read-only; optional) | ARCH §3.4 |
| F10 | Rank Trials (3-stage gate to promote) | GD §5 |
| F11 | Adaptive difficulty per stat (weekly) | GD §6.4 |
| F12 | Status: radar (this vs last week), stat list, 18-week heatmap, achievements & titles (30), chapters (6) | GD §10–11; DS §4.5 |
| F13 | Vault: gold shop (shields, rerolls, themes, frames, sigils), Relic Cache from fragments | GD §9 |
| F14 | Notifications (max 2/day + gate), asked after first completion | ARCH §3.5 |
| F15 | Home-screen widget (Glance) | ARCH §3.6 |
| F16 | Weekly Report + shareable image card | GD §11; DS §3 |
| F17 | Settings incl. export/import JSON, reduce motion, haptics | DS §4.7 |
| F18 | Debug screen (debug builds): ledger viewer, time travel, rebuild balances, component catalog, Path inspector + fake oracle | ARCH §7 |
| F19 | **AI Paths**: opt-in consent; up to 3 Paths (goal, level, notes, stats, days, time budget); AI milestone ladder; weekly AI quest plan validated and priced by the engine; Path quests in the Directive; SELF ≤ Normal, harder work as linked Focus Gates; weekly progression from completion + "too easy/right/too hard" feedback; engine-gated milestone advance; offline/failed fallback to templates | GD §6.6; ARCH §3.7; DS §4.8 |
| F20 | **Path Oracle proxy**: Cloudflare Worker with Play Integrity, rate limits, budget cap, structured output, no data retention; staging + production | ARCH §3.8 |
| F21 | **AI content safety**: in-app "Report this quest" (hides it immediately, sends report), prompt content rules, eval set passing before each prompt release | ARCH §3.7–3.8, §6; PLAY_STORE_LAUNCH §2 |

## 5. Explicitly OUT of scope for v1.0 (put in `BACKLOG.md`)
Accounts/sync · global/friends leaderboards · guilds · PvP duels & wagers · premium currency / IAP / ads · AI mentor chat (free-form conversation) · AI deciding any reward · on-device LLM · photo/video proof · multi-day dungeons · hardcore/permadeath · prestige · territory wars · iOS · web · Wear OS · localisation beyond English (strings are externalised though; Path goals may be typed in any language).

**Why:** each of these either needs a real backend with accounts (social, sync), invites cheating that can't be verified yet (leaderboards on honor-system XP are worthless), is a much larger AI safety surface (open-ended chat), or is a monetisation/policy surface (IAP, loot boxes, wagers of currency) that slows store review. AI Paths are in scope because they need only a stateless proxy and their output is bounded by a schema and the engine.

## 6. Post-launch roadmap (during the 14-day closed test and after)
| Version | Theme | Highlights |
|---|---|---|
| v1.1 (closed-test week 1–2) | Fix & delight | Tester bug fixes, sounds, more templates (→ 250), more chapters, onboarding polish, Path prompt v2 from eval + tester feedback, opt-in analytics |
| v1.2 | Social, carefully | Accounts (Google sign-in) + cloud backup via **your** backend (reuse userService ideas), Paths sync, **friends** leaderboard of *verified XP only*, weekly friend challenge |
| v1.3 | AI mentor | Weekly mentor letter written from your week's results; Path templates shared between friends; numbers stay deterministic |
| v1.4 | Monetisation | "Hunter Pass" subscription: cosmetic themes, extra habits, advanced analytics. No pay-to-win, ever. |
| v2.0 | Guilds & seasons | Guild quests, seasonal ranked boards (verified only), multi-day dungeons |

## 7. Non-functional requirements
- Cold start < 1.2 s on a mid-range phone (Baseline Profile in v1.1).
- All screens 60 fps on mid-range; no jank on list scroll.
- APK/AAB download < 15 MB (fonts subset; no Lottie; vector art).
- Works fully offline (AI Paths degrade to templates); survives process death mid-gate and day rollover after days without opening.
- Accessibility floor per DESIGN_SYSTEM §6.
- `INTERNET` used only for the Path Oracle; no other hosts, no third-party networking SDKs.
- Path week plan: p95 end-to-end ≤ 30 s; the UI never blocks on it (it arrives in the background).
- Proxy: no request/response bodies stored or logged; monthly spend capped.

## 8. Legal / IP guardrails (release blockers)
- Name, icon, screenshots, and copy must not use "Solo Leveling", character names, artwork, logos, or quotes from the series. "Hunter", "System", "Gate", "rank E–S" are genre vocabulary and fine. Several competitors already carry an "independent, not affiliated" disclaimer — do the same in the description if you mention inspiration at all (better: don't mention it).
- Fonts are OFL; icons Material Symbols (Apache 2.0); all art original.
- Privacy policy hosted (GitHub Pages is fine) — required because of Health Connect and AI Paths. It must name the AI provider and say what is sent.
- Play's AI-generated content policy: in-app reporting of AI output (F21) and content rules that prevent restricted content.

## 9. Acceptance — "Done" for v1.0
- Every F1–F21 item works on a physical Android 10+ phone and the latest Android emulator (AI Paths against the production proxy from a Play-installed build).
- All GAME_DESIGN §12 vectors pass; ARCH §6 tests green (incl. proxy tests and the contract check); prompt eval passes its bar; lint clean.
- `docs/QA_CHECKLIST.md` executed and ticked.
- Signed AAB uploaded to Closed testing with listing, Data safety, Health apps declaration, content rating, privacy policy.
