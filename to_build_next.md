# To build next

Decisions from the premium redesign, 2026-11-06. Details are in `design/`.

## Now: Plates is the look (rounded version, v1.1)
- Plates is the default and only look for launch.
- Steps 4 and 5 of the redesign run on it. Step 4 needs Opus; Step 5 (the Compose port) is Sonnet, in batches.

## Build it so more looks can be added later
Step 5 builds one layout with these parts swappable. It costs little now and keeps the option open.

- **Tokens:** colours, fonts, corner radii, icon stroke.
- **Big number:** plain numerals in Plates; hung plates in Innings.
- **Today's progress:** the loaded bar in Plates; the over in Innings; the sky arc in Magnitude.
- **Rest and focus screen:** the round plate dial in Plates; the scoreboard or dot ring in the others.
- **Chart style:** set per look in ChartKit.

Feature screens never name a look. They only use these parts.

## After launch, only if still wanted
Add one at a time, in Settings under Appearance and sound.

1. **Innings** first: it is the most different from Plates.
2. **Magnitude** second.

**Before adding one, weigh:**
- App size is not the issue: about 1 to 3 MB for fonts (an estimate, not measured).
- The cost is upkeep. Each look adds a light and a dark version to check on every screen, and screenshot goldens multiply (1,244 today).
- It partly reverses the declutter decision that removed purchasable themes. If added, keep it a free choice in Settings, never a Store item.
- Store screenshots still show one face.

The other two directions are kept in `design/directions/` (`innings/`, `magnitude/`).
