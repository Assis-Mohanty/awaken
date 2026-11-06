---
version: 1.1
name: AWAKEN Plates
description: Calibrated competition plates as a design system. Five stats wear the five plate colours, finished quests load a bar, and everything else stays chalk and steel.
colors:
  light: { bg: "#F2F2ED", surface: "#FDFDFB", surface-sunken: "#E6E7E1", ink: "#16171B", ink-muted: "#50545B", primary: "#16171B", on-primary: "#FDFDFB" }
  dark: { bg: "#131417", surface: "#1E1F23", surface-sunken: "#2B2C31", ink: "#F2F2EC", ink-muted: "#B6B8BD", primary: "#F2F2EC", on-primary: "#16171B" }
  stat-light: { strength: "#C4382F", intelligence: "#2C63C2", willpower: "#DDA92C", vitality: "#2A8E66", charisma: "#FFFFFF" }
  stat-dark: { strength: "#E5635C", intelligence: "#6E9CEE", willpower: "#E9C25C", vitality: "#57BF90", charisma: "#E9E8E0" }
typography:
  family: Archivo
  display: { size: 76, weight: 600, width: 100 }
  metric-m: { size: 54, weight: 600, width: 104 }
  title-l: { size: 24, weight: 650, width: 100 }
  section: { size: 19, weight: 650, width: 100 }
  body: { size: 16, weight: 400, width: 100 }
  caption: { size: 13.5, weight: 400, width: 100 }
rounded: { plate: 9, bar: 4, tile: 20, card: 22, sheet: 28, button: 28 }
spacing: { gutter: 20, tile-gap: 8, section-gap: 28, min-touch: 48 }
---

# AWAKEN Plates

Full values are in `tokens.json`. Components are in `components.md`. Every screen is mocked in `plates/`.

## Overview

Plates borrows one thing every lifter knows: the colour code on competition plates. Those five colours become the five stats, so colour always means something. Finishing a quest loads a plate on today's bar. In Gym the bar shows the real load. Everything that is not data stays chalk, steel and ink, which keeps the app adult.

**Key characteristics**
- Colour lives on data, never on chrome. The primary action is ink.
- One family, Archivo, at confident medium weights. Large numbers are tight and light enough to feel refined, never chunky.
- Huge numerals. One hero per screen.
- One soft card per group, with no outlines. Soft shadow in light, tone only in dark.
- Round forms: 20 to 28dp cards, pill buttons, circular checks, badges and markers. Plates have soft rims.
- Sessions take over the screen in a deep version of their stat's colour, with a round plate dial.

**Light (chalk)** is a soft warm white with gentle shadows and rich, slightly muted plates. Made for the morning table and the gym floor.

**Dark (rubber floor)** is deep charcoal with chalk ink and softened, lighter plates. Session colours go deeper so they don't glare at a desk.

## Colors

| Role | Light | Dark | Job |
|---|---|---|---|
| `bg` | `#F2F2ED` | `#131417` | Screen ground |
| `surface` | `#FDFDFB` | `#1E1F23` | Rows and cards |
| `surfaceSunken` | `#E6E7E1` | `#2B2C31` | Tracks, secondary buttons, chips |
| `ink` | `#16171B` | `#F2F2EC` | Text, icons, charts |
| `inkMuted` | `#50545B` | `#B6B8BD` | Secondary text, AA on all surfaces |
| `primary` / `onPrimary` | ink / chalk | chalk / ink | The one primary action |
| `critical` | `#B81B21` | `#E5635C` | Needs attention, always with words |

**Stats:** Strength red, Intelligence blue, Willpower yellow, Vitality green, Charisma white with an edge. The colours are slightly muted, like anodised metal, because pure primaries read as a toy. They appear only as plates, small round markers, chart series and session backgrounds.

**Rule:** a stat colour is never the only signal. The stat's name or icon is always beside it. Yellow and white on chalk fall below 3:1, which is acceptable only because they are decorative next to text.

**Plate calculator exception:** real plate colours follow the kg value (25 red, 20 blue, 15 yellow, 10 green, 5 white), since that is what is physically on the bar.

## Typography

Archivo is variable, so `Font(..., variationSettings)` sets width and weight from one file. Requires API 26; the app's minimum is 28.

| Style | Size | Weight | Width | Use |
|---|---|---|---|---|
| display | 76 | 600 | 100 | Clocks |
| metricL | 72 | 600 | 104 | Current set |
| metricM | 54 | 600 | 104 | Screen hero |
| titleL | 24 | 650 | 100 | Tab titles |
| section | 19 | 650 | 100 | Section headings |
| body | 16 | 400 | 100 | Text |
| caption | 13.5 | 400 | 100 | Subtitles |
| micro | 12 | 500 | 100 | Nav and axes; the floor |

Sentence case everywhere. Tabular figures wherever a number can change. Sizes are `sp`.

## Layout

- 20dp gutter. Rows sit together in one card, spaced by air with no dividers. 8dp between tiles. 28dp above a section.
- Standard Material structure: navigation bar with five tabs, top bar, bottom sheets, system Back.
- Today: header, level, plan, tiles. The plan starts on the first screen.
- Full-screen moments hide the nav: session, rest, active workout, onboarding, PR.
- Layout follows the screen height, never a fixed one. The primary action stays pinned on onboarding, sessions and forms. Rules are in `components.md`.

## Elevation

Light: one soft, low shadow on cards. Dark: tone only. Sheets sit over a scrim.

## Motion

- **Plate seat:** 320ms, fast then settling, one haptic. The app's single signature move.
- **Session dial:** runs down with the clock.
- **Standard:** 240ms. Sheets 280ms. Press feedback 90ms.
- **Reduce motion:** plates appear in place, the dial steps once a minute, sheets crossfade.
- Nothing bounces and nothing loops for decoration.

## Game layer

Compact only: level ring, round rank badge, smooth XP bar, streak. Level-up is a line in the reward sheet with a ring, and rank-up a plain sheet, as the declutter decided. No full-screen ceremony.

## Do

- Use a stat colour only for that stat.
- Lead each screen with one big number or one question.
- Keep one primary button per screen.
- Show empty states as a bare bar waiting for its first plate.

## Don't

- Don't put stat colours on buttons or headers. Backgrounds only on session screens, and only the deep versions.
- Don't add outlines to cards, or shadows in dark mode.
- Don't use square corners on controls. Nothing boxy.
- Don't use heavy expanded type for headings or numbers.
- Don't use a second typeface, or go below 12sp.
- Don't use all caps, emoji, gradients, or neon.
- Don't show rank letters as session names.
