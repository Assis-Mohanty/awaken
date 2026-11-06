# AWAKEN visual directions (Step 3)

Three directions, 7 screens each, light and dark: Today, Focus session, Gym active workout, Gym rest, Progress, Onboarding, Settings.

- **A · Innings:** `innings/`
- **B · Plates:** `plates/`
- **C · Magnitude:** `magnitude/`

**View:** run `python -m http.server 8765 --bind 0.0.0.0` here, then open `http://<this-pc-ip>:8765/` on a phone on the same Wi-Fi. Fonts come from Google Fonts, so it needs internet.

**One frame:** `index.html?d=plates&m=dark&s=gym&solo=1`. Add `&tall=1` for full length.

**PNGs:** `./export.sh` writes `png/<direction>/<mode>-<screen>.png` at 390×844 (needs the server and Edge). `compare.png` is the side-by-side.

Reasoning is in `design/RESEARCH.md`. The earlier Sonnet attempt is in `design/_archive/`.
