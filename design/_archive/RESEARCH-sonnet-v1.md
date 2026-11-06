# Visual research (Step 3)

## How this was done
- **UI UX Pro Max:** the automatic design-system search gave a poor match. For "premium personal growth, calm, data-first" it returned *Claymorphism* (chunky, thick borders, playful) on a web landing-page pattern. That is the opposite of calm and data-first, so I **rejected it** and used the tool only for what it's good at: font pairings, chart rules and the Compose checklist.
- **Chart rules taken from it:** a line chart needs at least 4 points. Never tell series apart by colour alone: use dash styles and labels written on the chart. Give every chart a text summary. Radar charts are fine for 5 stats but need a stat list beside them.
- **awesome-design-md:** I read six of the 84 files end to end (the Overview and Key Characteristics sections): Oura, Claude, Apple, Linear, Wise and Revolut. The reference library is in `../design-refs`, outside the repo.
- **Your own references** (`docs/ui-reference/`): good bones for the Gym screens, but the neon gradients and rainbow rings break your "never neon" rule, so they only inform layout.

---

## What makes each reference premium

| Reference | What to learn | What not to copy |
|---|---|---|
| **Oura** | Warm paper (#F7F1E8) is the resting colour, never white. A light, high-contrast serif for big statements and a quiet sans for reading. Earth tones belong to *data*, not to buttons. A single decisive action colour. | Full-bleed lifestyle photography. This app has none. |
| **Claude** | Cream canvas plus a muted coral accent used sparingly. Slab-serif headlines at weight 400 with tight tracking. Three surface tones (canvas, card, dark) instead of borders. | Marketing sections. |
| **Apple** | One accent for every interactive element. No borders, no decorative chrome. Alternating light and dark blocks act as dividers. Exactly one shadow in the whole system. Tight negative tracking on big type. | Product-render photography. |
| **Linear** | A **surface ladder**: four or five near-black steps carry hierarchy with no shadows and almost no borders. One chromatic accent. Display type tracks tight. | Hairline borders on every card. We separate by tone only. |
| **Wise** | Huge, heavy numerals as the brand voice. One bright CTA colour. 24px pill-rounded cards on a tinted sage canvas, white cards inside. Full semantic colour set for good, warning and bad. | The lime green itself. |
| **Revolut** | A white pill on black is the brightest pixel on the screen. Money-style numerals at 80px+ with 1.0 line height. Saturated colours live **only inside charts and illustrations**. | Marketing banding. |

### The common thread
1. **Tone, not outlines.** Premium surfaces separate by a step in colour, never by a border.
2. **One accent, with a job.** Action colour only on the primary action. Data gets its own muted family.
3. **Huge numerals.** The number is the hero, the label is small and quiet.
4. **Warm neutrals in light, deep tinted neutrals in dark.** Never pure white or pure black.
5. **Restraint is the luxury.** Few elements, wide spacing, one hero per screen.

---

## Three directions

Light and dark are two moods of one brand, not an inverse.

| | **A · Ledger** | **B · Meridian** | **C · Moss** |
|---|---|---|---|
| **Idea** | A printed journal of your progress | A precise instrument panel | A calm, living garden |
| **Leans on** | Oura, Claude | Linear, Revolut, Apple | Wise, Oura |
| **Type** | Fraunces (numerals, titles) + Schibsted Grotesk | Onest + DM Mono for labels | Bricolage Grotesque + Figtree |
| **Light mood** | Warm paper, soft tinted shadows, editorial | Cool porcelain, crisp tone steps, hairline-free | Cream with sage, big soft shapes |
| **Dark mood** | Deep ink, bone text, luminous data | Midnight blue, surface ladder, cool glow on data | Forest night, mint on moss |
| **Accent** | Vermilion | Cobalt | Deep leaf green (light) / mint (dark) |
| **Surfaces** | Open sections divided by space and tone, few cards | Tight tiles on a 2-column grid | Large 28px shapes, pill controls |
| **Charts** | Ink line, one highlighted point, hairline grid | Stepped bars and area with mono labels | Smooth curve, dotted heatmap |
| **Icons** | Thin 1.25px line | Geometric 1.75px | Rounded 2px, duo-tone fill |
| **Motion** | Slow, 300ms, ease-out | Fast, 180ms, sharp | Soft, 280ms, springy but never bouncing |

All fonts are open source (SIL OFL) and can be bundled in the app.

## Hard rules for all three
- No Material default look. No outlines: tone and space separate things.
- Accent only on the primary action and key numbers.
- One hero per screen, one primary action per screen, 48dp touch targets.
- Text at WCAG AA or better in both modes.
- The game layer is three compact modules: level ring, rank badge, XP bar. No neon windows.
- No emoji in the interface and no gradients as decoration. In dark mode, a restrained glow on data is allowed.
- Session names are "15-minute session"; rank letters are gone from the interface.
