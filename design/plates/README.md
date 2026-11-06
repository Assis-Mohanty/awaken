# Plates: full screen set (Step 4)

78 screens in light and dark. Run `python -m http.server 8765 --bind 0.0.0.0` from `design/`, then open `/plates/index.html`.

- Tabs along the top filter by area; Light and Dark switch mode.
- One frame: `index.html?s=gymRun&m=dark&solo=1`.
- PNGs: `python export.py` writes `png/<mode>/<group>-<id>.png`.

Spec: `design/DESIGN.md`, `design/tokens.json`, `design/components.md`.
