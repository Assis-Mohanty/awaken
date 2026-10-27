# AWAKEN — UI overhaul notes (direction only; do not implement yet)

Written 2026-10-27 with the v1.1 brief (`docs/V1_1_FEATURES_PROMPT.md`, Part 4). This file sets the target so the Part 2 refactor (semantic tokens, ChartKit, Lexicon, screenshot tests; DESIGN_SYSTEM §8.3–8.5) serves it. Nothing here is built in v1.1.

## 1. Reference and limits

- **Reference:** Withings Health Mate (via Mobbin). **Inspiration only.** Do not copy its assets, icons, brand colours or layouts pixel for pixel. Reference screenshots will go in `docs/ui-reference/` (the user adds them).
- Our own rules still apply: OFL fonts, Material Symbols or our own glyphs, original art, no series references (PRD §8).

## 2. Direction

- A premium, calm health dashboard. **Light-first**, with an equally polished dark mode (both are first-class; today's premium black becomes the dark theme, not the identity).
- Generous whitespace; soft rounded cards on a quiet background; very large numerals for key metrics; smooth trend charts with subtle gradients; one restrained accent colour; refined typography; gentle motion.
- **Data is the hero.** Game elements (XP, level, rank, streak) become clean, compact modules on the same cards, not neon windows.
- **System mode is a theme layer on top of the new design**: accent + glow + copy (Lexicon + voice lines). It is not a separate UI and never a separate component tree. Clean mode is the default look.

## 3. How the refactor makes this cheap

| Layer | What the redesign changes | What it must not need to touch |
|---|---|---|
| Palette (`core/designsystem/theme`) | New light and dark palettes, accent | — |
| Semantic tokens | Possibly re-map (e.g. `surfaceElevated` becomes a white card on a grey page) | Feature code |
| Type roles / spacing / radius / elevation | New typeface, bigger `metricXL`, card radius, maybe soft elevation | Feature code |
| Components | `SystemWindow` → a `Card` with a System-mode variant; new headers | Screens' structure |
| ChartKit | New chart style (smooth lines, gradients) | Screens asking for charts |
| Lexicon | Nothing (terms already split) | — |
| Screenshot tests | Re-recorded deliberately, once, with a note | — |

## 4. Screens and components that will change

Every row must consume only semantic tokens, ChartKit and the Lexicon by the end of Part 2. The **Part 2 audit** column records the Phase A refactor (2026-10-27); `FeatureBoundaryTest` keeps it true. Anything that regresses is a bug, not a redesign task.

**Screens** (`feature/**`)

| Screen | Expected change | Part 2 audit |
|---|---|---|
| Today (`TodayScreen`) | Header becomes a metrics row (level, streak, consistency); Directive as cards grouped by time block | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Quest detail | Card layout, plain hierarchy | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Gates (`GatesScreen`, gate run) | Calm timer; glow only in System mode | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Trial detail | Compact module | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Status | Dashboard: big metrics, trend charts, insights, radar as one module | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Vault | Shop grid in cards | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Paths, Path detail (dormant AI) | Card ladder | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Campaign | Calendar and progress as dashboard modules | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Gym | Already data-first; re-skin cards and body map colours | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Diet | Ring + macro bars re-skinned | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Health | Trend charts via ChartKit | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Screen time | Bars via ChartKit | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Onboarding | Light, airy steps; System intro only in System mode | ✓ tokens, ChartKit, Lexicon (Phase A) |
| Settings | Grouped list cards | ✓ tokens, ChartKit, Lexicon (Phase A) |
| New in v1.1: Weekly Review, Recap, Insights section | Built on tokens from day one | n/a (new) |
| Debug screen, component catalog | Must render every component in both modes | ✓ tokens, ChartKit, Lexicon (Phase A) |

**Components** (`core/designsystem/components`)

`SystemWindow`, `HunterHeader`, `XpBar`, `QuestRow`, chips (`Chips.kt`), `Labels`, `Buttons`, `Fields`, `RewardSheet`, `RadarChart`, `Heatmap`, `ProgressRing`, `GateDial`, `GateBackground`, `RankCrest`, `Glyphs`, `BodyMap`/`BodyLabels`, `PathComponents`, `LevelUpCeremony`, `ChapterReveal`, `SystemWarning`, `SystemToast`, `TypeOnText`, `EmptyState`, `ShareCard`, `PlaceholderPane`, `MotionModifiers`, plus the v1.1 set (`ConsistencyMeter`, `InsightCard`, `TimeBlockHeader`, `ReviewFlow` steps, `RecapCard`, `IdentityChips`, `ModeSwitch`).

Not changing: the widget (Glance; separate pass, BACKLOG "Light-mode widget"), the share image (stays dark, DS §2.1b) unless decided otherwise below.

## 5. Decisions taken (2026-10-27, with the user)

| Decision | Chosen | Notes |
|---|---|---|
| Typeface | **Plus Jakarta Sans** (variable, OFL) everywhere | Saira Condensed and IBM Plex Sans removed (smaller APK). |
| Accent colour | **Ink-indigo** #3446C9 (light) / #9DAAFF (dark); electric #7FD8FF in System mode | Restrained; stat pastels carry the colour. |
| Card style | **20dp radius, warm hairline, no shadow**; tinted cards drop the hairline | System mode adds an accent edge and glow. |
| Chart style | **Smooth line, soft gradient fill, dotted grid, latest point marked**; rounded heatmap cells and bar ends | ChartKit only. |
| Icon set | **Our own line glyphs with rounded caps and joins** | No third-party icon set needed. |
| Motion language | **Fade and rise 8dp**; counting numbers; calm level-up (ring fills, no expanding neon ring); type-on only in System mode | reduce-motion = crossfades. |
| Share image | **Stays on the Legacy dark palette for now** | Redesigned with Progress. |
| System mode layer | **System palette (near-black, electric accent), card glow, tracked-out titles, rank hex crest, type-on, System words** | Token + Lexicon overrides only; same components. |
| Navigation | **Today · Gym · Diet · Progress** | Paths opens from Progress; Gates and Vault from Today's header. |
| Today | Greeting, hero ring, level + streak modules, insight card, timeline by time block (F31) | Phase B review on the phone. |

## 6. Rollout after the Phase B review (one commit per screen)

Progress (Status) · Gates (+ run dial) · Vault · Settings · Onboarding · Quest detail · Trial · Paths / Path detail · Gym · Diet · Health · Screen time · Campaign · share card. The reward sheet and level-up ceremony were restyled with the components in Phase B.
