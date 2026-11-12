"""Suggests catalog foods for each FoodBD class so a person can write labels.json.

    python tools/scanner/map_classes.py <FoodBD data.yaml> [--top 4]

The English search terms below are a human translation of FoodBD's Bengali class names (a wrong guess here becomes a wrong
food suggestion in the app, so check them). A class with no terms is left unmapped on purpose. Pure standard library.
"""
import argparse, re, sqlite3, sys
from pathlib import Path

FOODS_DB = Path(__file__).resolve().parents[2] / "app/src/main/assets/nutrition/foods.db"

# FoodBD class -> catalog search terms, best first. None = no confident English name: do not map.
TERMS = {
    "achar": ["pickle", "achar"], "apple": ["apple"], "badam": ["almonds"], "banana": ["banana"], "beef": ["beef curry", "beef"],
    "begun-vaji": ["brinjal fry", "eggplant fried", "baingan"], "beguni": ["brinjal pakora", "eggplant fritter", "pakora"],
    "biriyani": ["biryani"], "bread": ["bread"], "burinda": None, "cabbage": ["cabbage"], "carrot": ["carrot"],
    "cauliflower": ["cauliflower"], "cha": ["tea"], "chichinga-vaji": ["snake gourd"], "chicken": ["chicken curry", "chicken"],
    "chola": ["chickpea", "chana"], "chomchom": ["chomchom", "rasgulla", "indian sweet"], "chop-alu": ["aloo tikki", "potato cutlet", "potato"],
    "cucumber": ["cucumber"], "daal": ["dal", "lentil"], "dates": ["dates"], "dundol": None, "egg": ["egg fried", "omelette", "egg"],
    "egg-boiled": ["egg boiled", "egg, hard-boiled"], "egg-poached": ["egg poached", "egg fried"], "fish": ["fish curry", "fish"],
    "grapes": ["grapes"], "green-pea": ["green peas", "peas"], "guava": ["guava"], "jilapi": ["jalebi"], "juice": ["juice"],
    "kabab": ["kebab", "kabab"], "khichuri": ["khichdi", "khichuri"], "kochu": ["taro", "colocasia"], "korola-vaji": ["bitter gourd", "karela"],
    "kumra-vaji": ["pumpkin"], "lal-shak": ["amaranth", "spinach"], "lau": ["bottle gourd", "lauki"], "lemon": ["lemon"], "mango": ["mango"],
    "milk": ["milk"], "noodles": ["noodles"], "okra": ["okra", "bhindi"], "orange": ["orange"], "papaya": ["papaya"], "payesh": ["kheer", "rice pudding"],
    "peyaju": ["onion pakora", "onion fritter", "pakora"], "pineapple": ["pineapple"], "piyaju": ["onion pakora", "pakora"], "potato": ["potato"],
    "potato-mashed": ["mashed potato", "potato, mashed"], "potol": ["pointed gourd", "parwal"], "puffed-rice": ["puffed rice", "murmura"],
    "puri": ["puri", "poori"], "rice": ["rice, cooked", "rice"], "roll": ["roll", "kathi roll"], "ruti": ["roti", "chapati"], "salad": ["salad"],
    "shak": ["spinach", "palak", "leafy"], "shim": ["flat beans", "beans"], "sweets": ["indian sweet", "gulab jamun", "sweet"], "tomato": ["tomato"],
    "vaji": ["vegetable fry", "sabzi"], "vegetable": ["mixed vegetable", "vegetable curry", "sabzi"], "vorta": ["bharta", "mashed vegetable"],
    "watermelon": ["watermelon"],
}

def classes(yaml_path):
    m = re.search(r"names:\s*\[(.*?)\]", Path(yaml_path).read_text(encoding="utf-8"), re.S)
    return [n.strip().strip("'\"") for n in m.group(1).split(",")]

def candidates(db, term, top):
    like = f"%{term}%"
    rows = db.execute(
        """SELECT f.id, f.name, f.tier, f.source_id FROM food f
           WHERE f.name LIKE ? OR f.id IN (SELECT food_id FROM alias WHERE alias LIKE ?)
           ORDER BY CASE f.tier WHEN 'CURATED' THEN 0 WHEN 'DERIVED' THEN 1 ELSE 2 END, f.popularity DESC, length(f.name) LIMIT ?""",
        (like, like, top)).fetchall()
    return rows

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("yaml"); ap.add_argument("--top", type=int, default=4)
    a = ap.parse_args()
    db = sqlite3.connect(FOODS_DB)
    for name in classes(a.yaml):
        terms = TERMS.get(name, "MISSING")
        if terms == "MISSING": print(f"{name}: NO ENTRY in TERMS (add one, or None)"); continue
        if terms is None: print(f"{name}: left unmapped (no confident English name)"); continue
        print(f"{name}:")
        for t in terms[:2]:
            for fid, fname, tier, src in candidates(db, t, a.top):
                print(f"    [{t}] {fid:<18} {tier:<9} {fname[:70]}")

if __name__ == "__main__":
    sys.exit(main())
