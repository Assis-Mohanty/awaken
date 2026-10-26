// Builds app/src/main/assets/nutrition/foods.json from USDA FoodData Central CSV downloads (public domain, CC0).
// Usage: node tools/catalog/build-foods.mjs <sr_legacy_csv_dir> <fndds_csv_dir>
// See tools/catalog/README.md for the pinned releases.
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';

const [srDir, fnddsDir] = process.argv.slice(2);
if (!srDir || !fnddsDir) {
  console.error('usage: node build-foods.mjs <sr_legacy_csv_dir> <fndds_csv_dir>');
  process.exit(1);
}
const OUT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '../../app/src/main/assets/nutrition/foods.json');

// Our nutrient keys (engine Nutrient.key, same order as the file header) → [FDC nutrient id, legacy nutrient number].
// SR Legacy rows use the id, FNDDS rows use the number.
const NUTRIENTS = [
  ['kcal', 1008, 208], ['protein', 1003, 203], ['carbs', 1005, 205], ['fat', 1004, 204], ['fiber', 1079, 291],
  ['sugars', 2000, 269], ['sat_fat', 1258, 606], ['mono_fat', 1292, 645], ['poly_fat', 1293, 646],
  ['trans_fat', 1257, 605], ['cholesterol', 1253, 601], ['sodium', 1093, 307], ['potassium', 1092, 306],
  ['calcium', 1087, 301], ['iron', 1089, 303], ['magnesium', 1090, 304], ['vitamin_a', 1106, 320],
  ['vitamin_c', 1162, 401], ['vitamin_d', 1114, 328], ['caffeine', 1057, 262], ['alcohol', 1018, 221],
];
const column = new Map();
NUTRIENTS.forEach(([, id, nbr], i) => { column.set(String(id), i); column.set(String(nbr), i); });
const MAX_PORTIONS = 4;

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; }
      else if (c === '"') quoted = false;
      else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { out.push(cur); cur = ''; }
    else cur += c;
  }
  out.push(cur);
  return out;
}

async function eachRow(file, fn) {
  const rl = readline.createInterface({ input: fs.createReadStream(file), crlfDelay: Infinity });
  let header = null;
  for await (const line of rl) {
    if (!line) continue;
    const cells = parseCsvLine(line);
    if (!header) { header = cells; continue; }
    const row = {};
    header.forEach((h, i) => { row[h] = cells[i] ?? ''; });
    fn(row);
  }
}

const round = (x) => Math.round(x * 100) / 100;

async function load(dir, source) {
  const foods = new Map();
  await eachRow(path.join(dir, 'food.csv'), (r) => {
    foods.set(r.fdc_id, { id: `usda:${r.fdc_id}`, name: r.description.trim(), source, values: new Array(NUTRIENTS.length).fill(null), portions: [] });
  });
  await eachRow(path.join(dir, 'food_nutrient.csv'), (r) => {
    const food = foods.get(r.fdc_id);
    const i = column.get(r.nutrient_id);
    if (!food || i === undefined || r.amount === '') return;
    food.values[i] = round(Number(r.amount));
  });
  const units = new Map();
  await eachRow(path.join(dir, 'measure_unit.csv'), (r) => units.set(r.id, r.name));
  await eachRow(path.join(dir, 'food_portion.csv'), (r) => {
    const food = foods.get(r.fdc_id);
    const grams = Number(r.gram_weight);
    if (!food || !(grams > 0) || food.portions.length >= MAX_PORTIONS) return;
    let label = r.portion_description.trim();
    if (!label) {
      const unit = units.get(r.measure_unit_id);
      const amount = r.amount ? `${Number(r.amount)} ` : '1 ';
      label = (amount + [unit && unit !== 'undetermined' ? unit : '', r.modifier].filter(Boolean).join(' ')).trim();
    }
    if (!label || /quantity not specified/i.test(label)) return;
    food.portions.push([label, round(grams)]);
  });
  // A food without energy can't be logged meaningfully.
  return [...foods.values()].filter((f) => f.values[0] !== null);
}

const sr = await load(srDir, 'S');
const fndds = await load(fnddsDir, 'F');
const all = [...fndds, ...sr].sort((a, b) => a.name.localeCompare(b.name));
const doc = {
  version: 1,
  sources: { S: 'USDA FoodData Central SR Legacy 2018-04', F: 'USDA FoodData Central FNDDS 2024-10-31' },
  nutrients: NUTRIENTS.map(([key]) => key),
  foods: all.map((f) => [f.id, f.name, f.source, f.values, f.portions]),
};
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(doc));
console.log(`wrote ${all.length} foods (${sr.length} SR Legacy, ${fndds.length} FNDDS), ${fs.statSync(OUT).size} bytes → ${OUT}`);
