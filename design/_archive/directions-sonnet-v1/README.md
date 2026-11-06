# AWAKEN visual directions (Step 3)

Three directions, each with 7 screens in light and dark: Today, Focus session, Gym active workout, Gym rest, Progress, Onboarding, Settings.

| Direction | Folder |
|---|---|
| A · Ledger | `A-ledger/` |
| B · Meridian | `B-meridian/` |
| C · Moss | `C-moss/` |

**View:** `npx http-server . -p 8099`, then open `http://<this-pc-ip>:8099/` on a phone on the same Wi-Fi. Needs internet for the Google Fonts.

**Query options:** `?d=A|B|C` direction, `?m=light|dark` mode, `?s=today|focus|gym|rest|progress|onboarding|settings` screen, `&solo=1` one bare frame, `&tall=1` full-length frame.

**PNGs:** `./export.sh` writes 42 files to `png/<direction>/<mode>-<screen>.png` at 390×844 (server must be running; uses Edge).

**Contact sheets:** `compare-today-gym.png`, `compare-gym.png`, `compare-focus-rest.png`.

All fonts are SIL OFL and can be bundled in the app.
