// Builds app/src/main/assets/nutrition/foods.db (the bundled food catalog, Diet v2 plan section 3).
//   node tools/catalog/build-catalog.mjs <sr_legacy_csv_dir> <fndds_csv_dir> <cofid_unzipped_xlsx_dir> [--old-ids <file>]
//   node tools/catalog/build-catalog.mjs find <same three dirs> <words...>     (list foods whose name has every word)
// Sources and licences: tools/catalog/README.md. Needs Node 22.5+ (node:sqlite) and the Room schema export of
// CatalogDatabase (run ./gradlew :app:kspDebugKotlin first). Writes tools/catalog/out/report.md. Exits 1 on any error.
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { readWorkbook } from './xlsx.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const OUT_DB = path.join(ROOT, 'app/src/main/assets/nutrition/foods.db');
const OUT_REPORT = path.join(HERE, 'out/report.md');
const SCHEMA = path.join(ROOT, 'app/schemas/com.assismohanty.awaken.core.data.catalog.db.CatalogDatabase/1.json');
const INDIAN_FILES = fs.readdirSync(HERE).filter((f) => /^indian-foods.*\.json$/.test(f)).sort().map((f) => path.join(HERE, f));
const CATALOG_VERSION = 1;

// Plan section 13 numbers (PROPOSED, GAME_DESIGN section 20).
const ATWATER_WARN = { kcal: 20, pct: 0.2 };
const ATWATER_ERROR_OWN = { kcal: 10, pct: 0.1 };
const FIBRE_KCAL = 2; // fibre counts 2 kcal/g in the check; carbs include fibre as in USDA
const RANGE_SHOW = 0.15;
const DUPLICATE_PCT = 0.02;
const SIZE_BUDGET_BYTES = 12 * 1024 * 1024;
const MAX_PORTIONS_USDA = 4;
const MAX_PORTION_GRAMS = 2000;
let skippedBigPortions = 0;

// engine Nutrient.key, FDC nutrient id (SR Legacy), legacy nutrient number (FNDDS)
const NUTRIENTS = [
  ['kcal', 1008, 208], ['protein', 1003, 203], ['carbs', 1005, 205], ['fat', 1004, 204], ['fiber', 1079, 291],
  ['sugars', 2000, 269], ['sat_fat', 1258, 606], ['mono_fat', 1292, 645], ['poly_fat', 1293, 646],
  ['trans_fat', 1257, 605], ['cholesterol', 1253, 601], ['sodium', 1093, 307], ['potassium', 1092, 306],
  ['calcium', 1087, 301], ['iron', 1089, 303], ['magnesium', 1090, 304], ['vitamin_a', 1106, 320],
  ['vitamin_c', 1162, 401], ['vitamin_d', 1114, 328], ['caffeine', 1057, 262], ['alcohol', 1018, 221],
  ['zinc', 1095, 309], ['vitamin_b12', 1178, 418], ['folate', 1177, 417],
];
const KEYS = NUTRIENTS.map(([k]) => k);
const MACROS = ['kcal', 'protein', 'carbs', 'fat'];
const column = new Map();
NUTRIENTS.forEach(([key, id, nbr]) => { column.set(String(id), key); column.set(String(nbr), key); });

// CoFID: sheet -> [our key, mnemonic in the second header row]
const COFID_COLUMNS = {
  '1.3 Proximates': [['kcal', 'KCALS'], ['protein', 'PROT'], ['carbs', 'CHO'], ['fat', 'FAT'], ['fiber', 'AOACFIB'], ['sugars', 'TOTSUG'],
    ['sat_fat', 'SATFOD'], ['mono_fat', 'MONOFOD'], ['poly_fat', 'POLYFOD'], ['trans_fat', 'FODTRANS'], ['cholesterol', 'CHOL'], ['alcohol', 'ALCO']],
  '1.4 Inorganics': [['sodium', 'NA'], ['potassium', 'K'], ['calcium', 'CA'], ['magnesium', 'MG'], ['iron', 'FE'], ['zinc', 'ZN']],
  '1.5 Vitamins': [['vitamin_a', 'RETEQU'], ['vitamin_d', 'VITD'], ['vitamin_c', 'VITC'], ['vitamin_b12', 'VITB12'], ['folate', 'FOLT']],
};

const SOURCES = [
  { id: 'usda_sr', name: 'USDA FoodData Central, SR Legacy (April 2018)', licence: 'CC0 1.0 Universal (public domain)', url: 'https://fdc.nal.usda.gov/', version: '2018-04',
    attribution: 'U.S. Department of Agriculture, Agricultural Research Service, Beltsville Human Nutrition Research Center. FoodData Central. fdc.nal.usda.gov' },
  { id: 'usda_fndds', name: 'USDA FoodData Central, FNDDS 2021-2023', licence: 'CC0 1.0 Universal (public domain)', url: 'https://fdc.nal.usda.gov/', version: '2024-10-31',
    attribution: 'U.S. Department of Agriculture, Agricultural Research Service, Beltsville Human Nutrition Research Center. FoodData Central. fdc.nal.usda.gov' },
  { id: 'cofid', name: 'UK Composition of Foods Integrated Dataset (CoFID) 2021', licence: 'Open Government Licence v3.0',
    url: 'https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid', version: '2021',
    attribution: "Contains public sector information licensed under the Open Government Licence v3.0. Source: Public Health England, McCance and Widdowson's The Composition of Foods Integrated Dataset." },
  { id: 'awaken', name: 'AWAKEN recipes', licence: 'Own work, calculated from the sources above', url: '', version: String(CATALOG_VERSION),
    attribution: 'Calculated by AWAKEN from its own recipes and the ingredient data above. Home cooking varies, so treat these values as a guide.' },
];
const SOURCE_RANK = { usda_sr: 0, cofid: 1, usda_fndds: 2, awaken: 3 };

// The same rules as engine FoodSearch.key (CatalogTest compares them).
export function searchKey(text) {
  return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9ऀ-ॿ]+/g, ' ').trim()
    .split(' ').filter(Boolean).map((token) => {
      const t = token.length > 3 && token.endsWith('s') && !token.endsWith('ss') ? token.slice(0, -1) : token;
      return t.replace(/ph/g, 'f').replace(/w/g, 'v').replace(/aa/g, 'a').replace(/ee/g, 'i').replace(/oo/g, 'u')
        .replace(/([bcdgjkpt])h/g, '$1').replace(/ah/g, 'a').replace(/(.)\1+/g, '$1');
    }).join(' ');
}

const round = (x, places = 2) => { const f = 10 ** places; return Math.round(x * f) / f; };
const errors = [];
const warnings = [];

// ---------------------------------------------------------------- loading

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') quoted = false; else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { out.push(cur); cur = ''; } else cur += c;
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

const unitOf = (label) => {
  const l = label.toLowerCase();
  if (/katori|bowl/.test(l)) return 'KATORI';
  if (/\bcup\b/.test(l)) return 'CUP';
  if (/tbsp|tablespoon/.test(l)) return 'TBSP';
  if (/tsp|teaspoon/.test(l)) return 'TSP';
  if (/glass/.test(l)) return 'GLASS';
  if (/slice/.test(l)) return 'SLICE';
  if (/plate|thali/.test(l)) return 'PLATE';
  if (/roti|chapati|chapatti|phulka|paratha|naan|puri|bhatura|kulcha/.test(l)) return 'ROTI';
  if (/piece|medium|small|large|each|\bpc\b/.test(l)) return 'PIECE';
  return 'OTHER';
};

const DIET_NONVEG = /\b(chicken|beef|pork|lamb|mutton|fish|salmon|tuna|shrimp|prawn|turkey|bacon|ham|sausage|crab|lobster|veal|duck|goat|liver|anchov|sardine|mackerel|oyster|squid|cod|haddock|herring|trout|clam|mussel|meat|kebab|salami|pepperoni)\b/i;
const DIET_EGG = /\b(egg|eggs|omelet|omelette)\b/i;
const dietOf = (name) => (DIET_NONVEG.test(name) ? 'NONVEG' : DIET_EGG.test(name) && !/eggplant|egg noodle|eggnog/i.test(name) ? 'EGG' : null);

async function loadUsda(dir, source) {
  const foods = new Map();
  await eachRow(path.join(dir, 'food.csv'), (r) => {
    foods.set(r.fdc_id, { id: `usda:${r.fdc_id}`, name: r.description.trim(), origName: r.description.trim(), source, tier: source === 'usda_fndds' ? 'DERIVED' : 'CURATED', values: {}, portions: [], aliases: [], popularity: 0 });
  });
  await eachRow(path.join(dir, 'food_nutrient.csv'), (r) => {
    const food = foods.get(r.fdc_id);
    const key = column.get(r.nutrient_id);
    if (!food || !key || r.amount === '') return;
    food.values[key] = round(Number(r.amount));
  });
  const units = new Map();
  await eachRow(path.join(dir, 'measure_unit.csv'), (r) => units.set(r.id, r.name));
  await eachRow(path.join(dir, 'food_portion.csv'), (r) => {
    const food = foods.get(r.fdc_id);
    const grams = Number(r.gram_weight);
    if (!food || !(grams > 0) || food.portions.length >= MAX_PORTIONS_USDA) return;
    if (grams > MAX_PORTION_GRAMS) { skippedBigPortions++; return; } // whole roasts, turkeys: not a serving
    let label = r.portion_description.trim();
    if (!label) {
      const unit = units.get(r.measure_unit_id);
      const amount = r.amount ? `${Number(r.amount)} ` : '1 ';
      label = (amount + [unit && unit !== 'undetermined' ? unit : '', r.modifier].filter(Boolean).join(' ')).trim();
    }
    if (!label || /quantity not specified/i.test(label)) return;
    food.portions.push({ label, grams: round(grams), unit: unitOf(label) });
  });
  return [...foods.values()].filter((f) => f.values.kcal !== undefined);
}

function cofidNumber(x) {
  if (x === undefined || x === null) return null;
  const s = String(x).trim().replace(/^[[(]|[\])]$/g, '');
  if (s === '' || s === 'N') return null;
  if (s === 'Tr') return 0;
  const v = Number(s);
  return Number.isFinite(v) ? v : null;
}

function loadCofid(dir) {
  const wb = readWorkbook(dir);
  const byCode = new Map();
  for (const [sheet, columns] of Object.entries(COFID_COLUMNS)) {
    const rows = wb[sheet];
    if (!rows) throw new Error(`CoFID sheet missing: ${sheet}`);
    const mnemonic = rows[1];
    const index = columns.map(([key, code]) => {
      const i = mnemonic.indexOf(code);
      if (i < 0) throw new Error(`CoFID column ${code} missing in ${sheet}`);
      return [key, i];
    });
    for (const row of rows.slice(3)) {
      const code = row[0];
      if (!code) continue;
      let food = byCode.get(code);
      if (!food) {
        food = { id: `cofid:${code}`, name: (row[1] ?? '').trim(), origName: (row[1] ?? '').trim(), source: 'cofid', tier: 'CURATED', values: {}, portions: [], aliases: [], popularity: 0 };
        byCode.set(code, food);
      }
      for (const [key, i] of index) { const v = cofidNumber(row[i]); if (v !== null) food.values[key] = round(v); }
    }
  }
  for (const food of byCode.values()) {
    // CoFID carbohydrate is available carbohydrate; add AOAC fibre so "carbs" means total carbohydrate as in USDA.
    if (food.values.carbs !== undefined && food.values.fiber !== undefined) food.values.carbs = round(food.values.carbs + food.values.fiber);
  }
  return [...byCode.values()].filter((f) => f.name && f.values.kcal !== undefined);
}

// ---------------------------------------------------------------- Indian foods

const nameWords = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().split(' ').filter(Boolean);

function makeFinder(all) {
  const bySourceName = new Map(all.map((f) => [`${f.source}:${f.origName.toLowerCase()}`, f])); // recipes name sources by their ORIGINAL name
  const poolOf = (prefix) => all.filter((f) => (prefix ? f.source === prefix : f.source === 'usda_sr' || f.source === 'cofid'));
  const prefixSource = { sr: 'usda_sr', fndds: 'usda_fndds', cofid: 'cofid' };
  return (query) => {
    let q = query.trim();
    let src = null;
    let exact = false;
    if (q.startsWith('=')) { exact = true; q = q.slice(1).trim(); } // "=sr:Name" and "sr:=Name" both mean exact
    const m = /^(sr|fndds|cofid):/.exec(q);
    if (m) { src = prefixSource[m[1]]; q = q.slice(m[0].length).trim(); }
    if (q.startsWith('=')) { exact = true; q = q.slice(1).trim(); }
    if (exact) {
      const hit = src ? bySourceName.get(`${src}:${q.toLowerCase()}`) : null;
      if (hit) return hit;
      const hits = poolOf(src).filter((f) => f.origName.toLowerCase() === q.toLowerCase());
      if (hits.length === 1) return hits[0];
      throw new Error(`no unique exact food "${query}" (${hits.length} found)`);
    }
    const words = nameWords(q);
    const hits = poolOf(src).filter((f) => { const n = nameWords(f.origName).join(' '); return words.every((w) => n.includes(w)); });
    if (hits.length === 0) throw new Error(`no food matches "${query}"`);
    hits.sort((a, b) => a.origName.length - b.origName.length || SOURCE_RANK[a.source] - SOURCE_RANK[b.source]);
    if (hits.length > 1 && hits[0].origName.length === hits[1].origName.length && hits[0].source === hits[1].source && hits[0].values.kcal !== hits[1].values.kcal) {
      throw new Error(`"${query}" is ambiguous: ${hits.slice(0, 4).map((h) => h.origName).join(' | ')}`);
    }
    return hits[0];
  };
}

function parsePortion(text) {
  const i = text.lastIndexOf('=');
  const grams = Number(text.slice(i + 1));
  const label = text.slice(0, i).trim();
  if (i < 1 || !(grams > 0) || grams > MAX_PORTION_GRAMS) throw new Error(`bad portion "${text}"`);
  return { label, grams, unit: unitOf(label) };
}

function recipeNutrients(items, cooked) {
  // items: [{food, grams}]. A nutrient is known only when every ingredient has it; otherwise it is left out.
  const out = {};
  for (const key of KEYS) {
    if (items.every((it) => it.food.values[key] !== undefined)) {
      out[key] = items.reduce((s, it) => s + (it.food.values[key] * it.grams) / 100, 0) * (100 / cooked);
    }
  }
  return out;
}

function applyIndian(all, finder) {
  const spec = { foods: INDIAN_FILES.flatMap((f) => JSON.parse(fs.readFileSync(f, 'utf8')).foods) };
  const resolved = [];
  const created = [];
  const seenIds = new Set();
  for (const e of spec.foods) {
    const label = e.id ?? e.from;
    try {
      const portions = (e.p ?? []).map(parsePortion);
      const common = { dietTag: e.v ?? null, category: e.c ?? null, popularity: e.pop ?? 60 };
      const aliases = [...(e.a ?? []).map((a) => [a, 'hi-Latn']), ...(e.e ?? []).map((a) => [a, 'en']), ...(e.hi ?? []).map((a) => [a, 'hi'])];
      if (e.from) {
        const food = finder(e.from);
        if (food.curated) throw new Error(`already curated as "${food.name}"`);
        if (e.n && e.n !== food.name) aliases.push([food.name, 'en']);
        Object.assign(food, { name: e.n ?? food.name, tier: e.tier ?? 'CURATED', curated: true, ...common, dietTag: common.dietTag ?? food.dietTag ?? null });
        food.portions = portions.length ? portions : food.portions;
        food.aliases = aliases;
        resolved.push(`${food.id} "${food.name}" <- ${e.from}`);
      } else {
        const id = `in:${e.id}`;
        if (seenIds.has(id)) throw new Error('duplicate id');
        seenIds.add(id);
        if (!e.r || !e.r.length) throw new Error('recipe needs ingredients (r) or a source (from)');
        if (!portions.length) throw new Error('recipe needs at least one portion (p)');
        if (!common.dietTag) throw new Error('recipe needs a diet tag (v)');
        const items = e.r.map(([q, g]) => ({ q, grams: g, food: finder(q) }));
        const raw = items.reduce((s, it) => s + it.grams, 0);
        const cooked = e.y ?? raw;
        if (!(cooked > 0)) throw new Error('bad cooked weight');
        const values = recipeNutrients(items, cooked);
        const food = { id, name: e.n, origName: e.n, source: 'awaken', tier: e.tier ?? 'DERIVED', values: {}, portions, aliases, ...common, curated: true };
        for (const [k, v] of Object.entries(values)) food.values[k] = round(v);
        const ranged = e.r.map((r, i) => [r, i]).filter(([r]) => r.length === 4);
        for (const [r, i] of ranged) {
          const at = (grams) => recipeNutrients(items.map((it, j) => ({ ...it, grams: j === i ? grams : it.grams })), cooked).kcal;
          const lo = at(r[2]);
          const hi = at(r[3]);
          if (lo !== undefined && hi !== undefined && (hi - lo) / food.values.kcal >= RANGE_SHOW) { food.kcalLow = round(lo, 1); food.kcalHigh = round(hi, 1); }
        }
        food.recipe = items.map((it) => `${it.grams} g ${it.food.name}`).join('; ') + ` -> ${cooked} g`;
        created.push(food);
        resolved.push(`${id} "${food.name}" = ${food.recipe}`);
      }
    } catch (err) {
      errors.push(`indian-foods*.json "${label}": ${err.message}`);
    }
  }
  return { created, resolved, count: spec.foods.length };
}

// ---------------------------------------------------------------- validation

function atwater(food) {
  const v = food.values;
  if (v.protein === undefined || v.carbs === undefined || v.fat === undefined) return null;
  const fibre = v.fiber ?? 0;
  return 4 * v.protein + 4 * (v.carbs - fibre) + FIBRE_KCAL * fibre + 9 * v.fat + 7 * (v.alcohol ?? 0);
}

function validate(all, oldIds) {
  const stats = { atwaterWarn: 0, atwaterError: 0 };
  for (const f of all) {
    const v = f.values;
    for (const [k, x] of Object.entries(v)) if (x < 0) errors.push(`${f.id}: negative ${k}`);
    if (v.kcal > 902) errors.push(`${f.id} "${f.name}": ${v.kcal} kcal per 100 g is above 902`);
    if ((v.protein ?? 0) + (v.carbs ?? 0) + (v.fat ?? 0) > 105) warnings.push(`${f.id} "${f.name}": protein + carbs + fat above 105 g`);
    for (const p of f.portions) if (!(p.grams > 0 && p.grams <= MAX_PORTION_GRAMS)) errors.push(`${f.id}: portion "${p.label}" ${p.grams} g`);
    const est = atwater(f);
    if (est !== null) {
      const diff = Math.abs(v.kcal - est);
      const own = f.source === 'awaken';
      const limit = own ? ATWATER_ERROR_OWN : ATWATER_WARN;
      if (diff > Math.max(limit.kcal, limit.pct * v.kcal)) {
        if (own) { errors.push(`${f.id} "${f.name}": Atwater ${round(est, 0)} vs ${v.kcal} kcal`); stats.atwaterError++; }
        else { stats.atwaterWarn++; if (stats.atwaterWarn <= 25) warnings.push(`Atwater ${f.id} "${f.name}": ${round(est, 0)} estimated vs ${v.kcal} kcal`); }
      }
    }
  }
  if (oldIds) {
    const have = new Set(all.map((f) => f.id));
    const lost = [...oldIds].filter((id) => !have.has(id));
    if (lost.length) errors.push(`${lost.length} usda ids from the previous catalog are gone, e.g. ${lost.slice(0, 5).join(', ')}`);
  }
  return stats;
}

function dropCofidDuplicates(all) {
  // A CoFID row that repeats a USDA row (same search key, macros within 2 %) is dropped; USDA ids never are.
  const usda = new Map();
  for (const f of all) if (f.source !== 'cofid' && f.source !== 'awaken') { const k = searchKey(f.name); (usda.get(k) ?? usda.set(k, []).get(k)).push(f); }
  const close = (a, b) => MACROS.every((m) => Math.abs((a.values[m] ?? 0) - (b.values[m] ?? 0)) <= DUPLICATE_PCT * Math.max(a.values[m] ?? 0, b.values[m] ?? 0, 1));
  const dropped = [];
  const kept = all.filter((f) => {
    if (f.source !== 'cofid' || f.curated) return true;
    const twin = (usda.get(searchKey(f.name)) ?? []).find((u) => close(u, f));
    if (twin) { dropped.push(`${f.id} "${f.name}" = ${twin.id}`); return false; }
    return true;
  });
  return { kept, dropped };
}

// ---------------------------------------------------------------- writing

function writeDb(all, stats) {
  const schema = JSON.parse(fs.readFileSync(SCHEMA, 'utf8')).database;
  if (schema.version !== CATALOG_VERSION) throw new Error(`schema version ${schema.version} != CATALOG_VERSION ${CATALOG_VERSION}`);
  fs.mkdirSync(path.dirname(OUT_DB), { recursive: true });
  fs.rmSync(OUT_DB, { force: true });
  const db = new DatabaseSync(OUT_DB);
  db.exec('PRAGMA journal_mode = DELETE');
  for (const entity of schema.entities) {
    db.exec(entity.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
    for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
  }
  for (const q of schema.setupQueries) db.exec(q);
  db.exec(`PRAGMA user_version = ${schema.version}`);

  db.exec('BEGIN');
  const addSource = db.prepare('INSERT INTO source (id, name, licence, url, version, attribution) VALUES (?, ?, ?, ?, ?, ?)');
  for (const s of SOURCES) addSource.run(s.id, s.name, s.licence, s.url, s.version, s.attribution);
  const addFood = db.prepare('INSERT INTO food (id, name, source_id, tier, diet_tag, category, kcal, protein, carbs, fat, nutrients_json, kcal_low, kcal_high, popularity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
  const addPortion = db.prepare('INSERT INTO portion (food_id, position, unit, label, grams) VALUES (?, ?, ?, ?, ?)');
  const addAlias = db.prepare('INSERT OR IGNORE INTO alias (food_id, alias, lang) VALUES (?, ?, ?)');
  const addFts = db.prepare('INSERT INTO food_fts (rowid, name, aliases, keys) VALUES (?, ?, ?, ?)');
  for (const f of all) {
    const rest = {};
    for (const k of KEYS) if (!MACROS.includes(k) && f.values[k] !== undefined) rest[k] = f.values[k];
    if (f.recipe) rest._from = f.recipe;
    const result = addFood.run(f.id, f.name, f.source, f.tier, f.dietTag ?? dietOf(f.name), f.category ?? null,
      f.values.kcal, f.values.protein ?? null, f.values.carbs ?? null, f.values.fat ?? null, JSON.stringify(rest),
      f.kcalLow ?? null, f.kcalHigh ?? null, f.popularity ?? 0);
    f.portions.forEach((p, i) => addPortion.run(f.id, i, p.unit, p.label, p.grams));
    for (const [alias, lang] of f.aliases) addAlias.run(f.id, alias, lang);
    const keys = [...new Set([searchKey(f.name), ...f.aliases.map(([a]) => searchKey(a))].join(' ').split(' ').filter(Boolean))].join(' ');
    addFts.run(Number(result.lastInsertRowid), f.name, f.aliases.map(([a]) => a).join(' | '), keys);
  }
  const addMeta = db.prepare('INSERT INTO meta (key, value) VALUES (?, ?)');
  addMeta.run('catalog_version', String(CATALOG_VERSION));
  addMeta.run('food_count', String(all.length));
  addMeta.run('built_at', new Date().toISOString().slice(0, 10));
  db.exec('COMMIT');
  db.exec('VACUUM');
  db.close();
  return fs.statSync(OUT_DB).size;
}

// ---------------------------------------------------------------- main

const args = process.argv.slice(2);
const oldIdsAt = args.indexOf('--old-ids');
const oldIdsFile = oldIdsAt >= 0 ? args.splice(oldIdsAt, 2)[1] : null;
const findMode = args[0] === 'find';
if (findMode) args.shift();
const [srDir, fnddsDir, cofidDir, ...rest] = args;
if (!srDir || !fnddsDir || !cofidDir) {
  console.error('usage: node build-catalog.mjs [find] <sr_legacy_csv_dir> <fndds_csv_dir> <cofid_xlsx_dir> [words...] [--old-ids file]');
  process.exit(1);
}

const sr = await loadUsda(srDir, 'usda_sr');
const fndds = await loadUsda(fnddsDir, 'usda_fndds');
const cofid = loadCofid(cofidDir);
let all = [...sr, ...fndds, ...cofid];

if (findMode) {
  const words = nameWords(rest.join(' '));
  for (const f of all.filter((x) => words.every((w) => nameWords(x.origName).join(' ').includes(w)))) {
    console.log(`${f.source.padEnd(10)} ${f.id.padEnd(14)} ${String(f.values.kcal).padStart(5)} kcal  P${f.values.protein ?? '-'} C${f.values.carbs ?? '-'} F${f.values.fat ?? '-'}  ${f.name}`);
  }
  process.exit(0);
}

const oldIds = oldIdsFile ? new Set(JSON.parse(fs.readFileSync(oldIdsFile, 'utf8')).foods.map((f) => f[0])) : null;
const finder = makeFinder(all);
const indian = applyIndian(all, finder);
const dedupe = dropCofidDuplicates(all);
all = [...dedupe.kept, ...indian.created].sort((a, b) => a.name.localeCompare(b.name) || a.id.localeCompare(b.id));
for (const f of all) {
  f.dietTag ??= dietOf(f.name);
  if (f.source === 'awaken' || f.curated) {
    if (!f.portions.length && f.curated && f.source !== 'usda_sr' && f.source !== 'usda_fndds') warnings.push(`${f.id} "${f.name}": curated row has no portions`);
  }
}
const stats = validate(all, oldIds);

let size = 0;
if (errors.length === 0) size = writeDb(all, stats);
if (size > SIZE_BUDGET_BYTES) errors.push(`foods.db is ${size} bytes, over the ${SIZE_BUDGET_BYTES} budget`);

const count = (src) => all.filter((f) => f.source === src).length;
const microCoverage = KEYS.filter((k) => !MACROS.includes(k)).map((k) => `${k} ${Math.round((100 * all.filter((f) => f.values[k] !== undefined).length) / all.length)}%`).join(' · ');
const curatedIndian = all.filter((f) => f.curated);
const report = [
  '# Catalog build report', '',
  `Foods: ${all.length} (SR Legacy ${count('usda_sr')}, FNDDS ${count('usda_fndds')}, CoFID ${count('cofid')}, AWAKEN recipes ${count('awaken')}).`,
  `Indian entries in indian-foods*.json: ${indian.count} (${indian.created.length} recipes, ${curatedIndian.length - indian.created.length} overlays on source rows).`,
  `foods.db: ${size ? `${(size / 1048576).toFixed(2)} MB` : 'not written (errors)'} (budget ${SIZE_BUDGET_BYTES / 1048576} MB).`,
  `Atwater warnings (USDA, CoFID): ${stats.atwaterWarn}. Atwater errors (own rows): ${stats.atwaterError}.`,
  `CoFID rows dropped as duplicates of USDA rows: ${dedupe.dropped.length}. USDA portions over ${MAX_PORTION_GRAMS} g skipped: ${skippedBigPortions}.`, '',
  '## Errors', errors.length ? errors.map((e) => `- ${e}`).join('\n') : 'none', '',
  '## Warnings', warnings.length ? warnings.map((w) => `- ${w}`).join('\n') : 'none', '',
  '## Share of foods with each micronutrient', microCoverage, '',
  '## Indian entries as resolved (review these)', indian.resolved.map((r) => `- ${r}`).join('\n'), '',
  '## CoFID duplicates dropped', dedupe.dropped.slice(0, 200).map((d) => `- ${d}`).join('\n') || 'none', '',
].join('\n');
fs.mkdirSync(path.dirname(OUT_REPORT), { recursive: true });
fs.writeFileSync(OUT_REPORT, report);
console.log(report.split('\n').slice(0, 12).join('\n'));
console.log(`full report: ${OUT_REPORT}`);
if (errors.length) { console.error(`${errors.length} error(s); foods.db not written`); process.exit(1); }
