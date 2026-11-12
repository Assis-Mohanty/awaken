"""Writes the app's labels.json from class_foods.json, in the model's output order, and checks every food id exists.

    python tools/scanner/make_labels.py <classes.json from training> <out labels.json>
"""
import json, sqlite3, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
classes = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
mapping = {k: v for k, v in json.loads((ROOT / "tools/scanner/class_foods.json").read_text(encoding="utf-8")).items() if not k.startswith("_")}
db = sqlite3.connect(ROOT / "app/src/main/assets/nutrition/foods.db")
missing = [(k, i) for k, ids in mapping.items() for i in ids if db.execute("select 1 from food where id=?", (i,)).fetchone() is None]
unknown = [k for k in mapping if k not in classes]
if missing or unknown:
    sys.exit(f"bad mapping: ids not in the catalog {missing}; classes not in the model {unknown}")
labels = {"labels": classes, "foods": {c: mapping[c] for c in classes if mapping.get(c)}}
Path(sys.argv[2]).write_text(json.dumps(labels, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"{len(classes)} labels, {len(labels['foods'])} with foods, {len(classes) - len(labels['foods'])} never suggested:",
      [c for c in classes if not mapping.get(c)])
