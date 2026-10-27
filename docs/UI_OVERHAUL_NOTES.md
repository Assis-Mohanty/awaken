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

## 5. Open decisions for the redesign

| Decision | Options to evaluate | Constraint |
|---|---|---|
| Typeface | Keep IBM Plex Sans for body; replace Saira Condensed numerals with a calmer grotesk (e.g. Inter, Manrope, Plus Jakarta Sans) or keep Saira for System mode only | OFL, subset to stay < 15 MB APK |
| Accent colour | One restrained hue (teal / blue / warm orange) vs monochrome + rank colours | ≥ 4.5:1 text contrast in both modes; not a competitor's brand colour |
| Card style | Radius (12/16/20dp), border vs soft shadow vs tonal surface | Works in dark without glow |
| Chart style | Smooth line + gradient fill, bar rounding, grid density, axis labels | ChartKit only; TalkBack descriptions |
| Icon set | Material Symbols Rounded vs Sharp vs our own | Apache/OFL; consistent stroke weight |
| Motion language | Softer easings, shorter reveals; what the level-up ceremony becomes in Clean mode | reduce-motion parity |
| Share image | Follow light theme or stay dark | Readable on social feeds |
| System mode layer | Exactly which tokens it overrides (accent, glow, uppercase titles, chamfer?) | Must be a token/Lexicon override, no forked components |
