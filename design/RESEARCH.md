# Visual research (Step 3, redone on Opus)

The Sonnet attempt is in `design/_archive/`. It landed on the default looks AI tools converge on: cream with a serif and a red accent, near-black with one bright accent, and Fraunces. This round starts from the audience instead.

## Inputs
- **You:** serious self-improvers; used at a table in the morning, on the gym floor, at a desk. It must not feel like a corporate dashboard, a children's game, a fitness bro app or a soft wellness app. Recorded in `PRODUCT.md`.
- **UI UX Pro Max:** its automatic design system is a landing-page recipe, so I used only its data: font checks, chart rules and the Android checklist. Line charts need 4+ points; never separate series by colour alone; every chart needs a text summary; 48dp targets; 4.5:1 text.
- **Impeccable:** the Operate rules. A world lends only type, palette, density and one signature move. It never supplies layout, navigation or controls. "A grey screen with one accent colour is the category default."
- **`../design-refs` (9 read):** below.
- **`docs/ui-reference/`:** the Gym layouts. One exercise per screen, a big rep number, a full-screen rest. Not the neon.

## What makes each reference premium

| Reference | Lesson taken | Left behind |
|---|---|---|
| **Oura** | Earth tones belong to data, never to buttons. One decisive action colour. | Cream paper and lifestyle photos |
| **Apple** | Colour change is the divider. Almost no chrome. | Product photography |
| **Linear** | A surface ladder carries hierarchy with no shadows. | Near-black with lavender |
| **Wise** | Heavy numerals are the brand voice. | Lime |
| **Revolut** | Saturated colours live inside data and illustration, never on buttons. A white pill on colour is the brightest pixel. | Marketing bands |
| **Claude** | Three surface tones instead of borders. | Cream and coral |
| **Nike** | Two-tone CTA hierarchy. Pure ink on anything light. | Campaign photography |
| **Ferrari** | One family, restrained; sharp corners read as precision. | Racing red as identity |
| **Spotify** | The accent is functional, never decorative. | Dark grey and one green |

**Common thread:** tone instead of outlines, huge numerals, and colour with a job.

---

## How the directions were chosen

Seven worlds from what this audience knows by heart, ordered by resonance:

1. Calibrated competition plates
2. OMR exam answer sheet
3. Tour stage profile and relief map
4. Indigo gi, belts and sashiko stitching
5. Athletics track
6. Railway station boards
7. Test-match scoreboard

Impeccable's seeded roll assigned **#7**, which is why it leads: the roll exists to stop me shipping my favourite. My own top pick (#1) joins as the alternative. The roll also dealt six outside challengers, each weighed on audience fit and product clarity:

| Challenger | Verdict | What I kept from it |
|---|---|---|
| **Star atlas** | **Competitive: full direction** (Magnitude) | Importance as dot size on one fixed scale |
| Metro tiles | Competitive, held out to keep the set at three | Colour fields as content, used in Plates' full-screen sessions |
| Vertical feed | Declined | One item owns the screen: the Gym screens |
| Centre-rail reference | Declined | Every state has its own mark, not just a colour |
| Cassette J-card | Declined | Running totals set tight on the right: the XP column |
| Exposure record | Declined | One tonal ramp as the only tones |

---

## The three directions

All fonts are SIL OFL, so they can be bundled. All charts use ChartKit-style drawing, and each signature is a custom Compose `Canvas` or layout piece.

### A · Innings (assigned by the roll)
| | |
|---|---|
| **Idea** | A Test-match scoreboard: effort is counted, honestly and in public |
| **Signature** | Every key number is a hand-hung plate, one tin per digit. A plate swap is the app's one big motion. A day's quests are an over, ball by ball |
| **Type** | Big Shoulders (numerals, titles), Libre Franklin (text) |
| **Palette** | Bottle-green board, sightscreen white, cherry ball. Dark: floodlit outfield green, white plates, pink ball (the real day-night Test ball) |
| **Surfaces** | A coloured board owns the top of each screen; tone-stepped rows below; 6px corners |
| **Charts** | Manhattan bars, square-noded radar, square heatmap |
| **Icons** | 2px, square ends, signwriter-plain |
| **Motion** | A plate drops and swaps on a number change; everything else is still |
| **Risk** | Heritage can read as old rather than sharp. Readers outside cricket countries get a scoreboard, not the reference |

### B · Plates (my top pick)
| | |
|---|---|
| **Idea** | Calibrated plates: the colour code every lifter knows |
| **Signature** | The five stats take the five plate colours. Finishing a quest loads a plate on today's bar. In Gym the bar shows the actual load (70 kg is 25 a side). Sessions fill the whole screen in their stat's colour and drain as time passes |
| **Type** | Archivo, one family; its width axis gives machined expanded numerals |
| **Palette** | Chalk white, steel, full plate colour. Dark: warm rubber-floor charcoal, chalk ink, brighter plates. The primary action is ink, so colour stays on data |
| **Surfaces** | Tone-stepped tiles with 3px gaps; 6 to 8px corners; a knurled texture on the XP bar |
| **Charts** | XP stacked by stat, radar with plate markers, ink-density heatmap |
| **Icons** | 2.25px, butt ends, machined |
| **Motion** | A plate slides onto the sleeve and seats with a short haptic; the colour field drains |
| **Risk** | Five colours are the closest of the three to a game, and it needs discipline to stay adult. Leans gym, though it holds up on study screens |

### C · Magnitude (challenger)
| | |
|---|---|
| **Idea** | A star atlas: a chart you read to find your way |
| **Signature** | Importance is dot size on one scale. The day is an arc of sky with each quest a star sized by its reward; the check circle itself is that star. Your 18 weeks are a star map |
| **Type** | Spectral (numerals, titles; italics for names), Hanken Grotesk (text) |
| **Palette** | Deep saturated navy, chalk stars, amber. Light: first light, pale periwinkle with ink-blue chart panels and copper |
| **Surfaces** | Soft 14px tone cards; dark chart panels in both modes |
| **Charts** | Constellation radar, sized-dot trend, dot-size heatmap |
| **Icons** | 1.5px, round, fine |
| **Motion** | A star ignites when completed; the session's minute dots burn down around the clock |
| **Risk** | The least legible on a gym floor: light serif numerals and fine lines. Calmest of the three, so it risks drifting toward wellness |

---

## Checks
- **Impeccable detector (real page scan):** clean apart from two accepted notes: Plates' knurl stripes (its signature) and a line-break note on my presentation page. It flagged Innings' secondary text as too grey on green, which I fixed.
- **Rendered:** all 42 frames at 390×844, plus full-length Progress in both modes for all three.
- **Not done yet:** small-phone and large-text checks, real-device review, the finish reviewer. Those belong to Step 4 on the chosen direction.
