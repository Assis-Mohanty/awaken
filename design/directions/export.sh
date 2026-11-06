#!/bin/bash
# Exports every frame to png/<direction>/<mode>-<screen>.png at 390x844. Needs the server on :8765 and Edge.
cd "$(dirname "$0")"; EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
for d in ${1:-innings plates magnitude}; do mkdir -p png/$d; for m in light dark; do for s in ${2:-today focus gym rest progress onboarding settings}; do
"$EDGE" --headless=new --disable-gpu --no-first-run --hide-scrollbars --window-size=390,844 --virtual-time-budget=15000 --screenshot="$(cygpath -w "$PWD/png/$d/$m-$s.png")" "http://127.0.0.1:8765/index.html?d=$d&m=$m&s=$s&solo=1" 2>&1 | grep -c written >/dev/null
done; done; done
