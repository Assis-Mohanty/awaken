#!/bin/bash
# Exports every frame as PNG (390x844) into ./png/<direction>/<mode>-<screen>.png. Needs the mockup server on :8099 and Edge.
cd "$(dirname "$0")"; EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
for D in A:A-ledger B:B-meridian C:C-moss; do id=${D%%:*}; dir=${D##*:}; mkdir -p png/$dir
  for m in light dark; do for s in today focus gym rest progress onboarding settings; do
    "$EDGE" --headless=new --disable-gpu --no-first-run --hide-scrollbars --window-size=390,844 --virtual-time-budget=7000 \
      --screenshot="$(cygpath -w "$PWD/png/$dir/$m-$s.png")" "http://127.0.0.1:8099/index.html?d=$id&m=$m&s=$s&solo=1" >/dev/null 2>&1
  done; done; done; echo done
