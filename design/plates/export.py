"""Exports every Plates screen to png/<mode>/<group>-<id>.png at 390x844. Needs the server on :8765 and Edge."""
import re, subprocess, pathlib, os
here = pathlib.Path(__file__).parent
src = (here / 'flows.js').read_text(encoding='utf-8')
block = src[src.index('const G = ['):src.index('const q = new URLSearchParams')]
groups = re.findall(r"\['(\w+)', \[(.*?)\]\]\s*[,\]]?\n", block)
edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
n = 0
for g, items in groups:
    for sid in re.findall(r"\['(\w+)', '", items):
        for m in ('light', 'dark'):
            out = here / 'png' / m; out.mkdir(parents=True, exist_ok=True)
            subprocess.run([edge, '--headless=new', '--disable-gpu', '--no-first-run', '--hide-scrollbars', '--window-size=390,844', '--virtual-time-budget=6000',
                f'--screenshot={out / f"{g}-{sid}.png"}', f'http://127.0.0.1:8765/plates/index.html?s={sid}&m={m}&solo=1'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            n += 1
print('exported', n)
