// Builds packaged.db: the packaged-food pack for AWAKEN's barcode scanner (docs/diet/PLAN.md section 6).
//
//   node build-packaged.mjs <en.openfoodfacts.org.products.csv.gz> [--out packaged.db] [--schema schema.json] [--report report.md]
//
// Input: the nightly Open Food Facts CSV export (a tab-separated file):
//   https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz
// Output: a SQLite file whose schema is exactly the Room schema in schema.json (AWAKEN's `PackagedDatabase`, version 1), so the
// app can copy it in as an asset. Needs Node 22.5+ (node:sqlite). Streams the file, so it does not need much memory.
//
// What is kept: products sold in India (tagged so, or with an Indian 890 barcode) with a valid barcode, a name, and calories, protein, carbohydrate and fat per 100 g.
// Everything else is dropped and counted in report.md. Data comes from volunteers and can be wrong; the app says so.
//
// Licence: the data is (c) Open Food Facts contributors, under the Open Database License (ODbL) 1.0; the individual contents
// under the Database Contents License 1.0. This script and its output are shared under the ODbL as well. See README.md.
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PACK_VERSION = 1;

// Data-quality limits. The Atwater limit is AWAKEN's own warning threshold for catalog rows (docs/diet/PLAN.md 3.6: more than
// max(20 kcal, 20 %) away from 4p + 4(c - fibre) + 2 fibre + 9f means the label is not trustworthy). The rest are physical limits.
const ATWATER = { kcal: 20, pct: 0.2 };
const KJ_PER_KCAL = 4.184;
const SALT_PER_SODIUM = 2.5;
const MAX_KCAL_PER_100G = 900;
const MACRO_SUM_TOLERANCE = 105; // protein + carbs + fat per 100 g can not honestly pass 100 g; a little slack for rounding
const PART_TOLERANCE = 0.5; // sugars may not exceed carbs, saturated fat may not exceed fat, fibre may not exceed carbs (grams of slack)
const MAX_NAME = 120;
const MAX_BRAND = 40;
const MAX_SERVING_GRAMS = 1000;

const COLUMNS = {
  code: 'code', name: 'product_name', brands: 'brands', countries: 'countries_tags', serving: 'serving_quantity',
  kcal: 'energy-kcal_100g', kj: 'energy_100g', protein: 'proteins_100g', carbs: 'carbohydrates_100g', fat: 'fat_100g',
  sugars: 'sugars_100g', satFat: 'saturated-fat_100g', fiber: 'fiber_100g', sodiumG: 'sodium_100g', saltG: 'salt_100g',
};

export function canonicalBarcode(raw) {
  const digits = (raw ?? '').trim();
  if (!/^\d+$/.test(digits)) return null;
  const ok = (code) => {
    const body = code.slice(0, -1);
    let sum = 0;
    [...body].reverse().forEach((c, i) => { sum += Number(c) * (i % 2 === 0 ? 3 : 1); });
    return (10 - (sum % 10)) % 10 === Number(code.at(-1));
  };
  if (digits.length === 13 && ok(digits)) return digits.startsWith('2') ? null : digits; // 2xx = store-internal codes, not one product
  if (digits.length === 12 && ok(digits)) return `0${digits}`;
  if (digits.length === 8 && ok(digits)) return `00000${digits}`;
  return null;
}

const num = (v) => {
  if (v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};
const round1 = (n) => (n === null ? null : Math.round(n * 10) / 10);

/** One CSV row (an object of column name to text) to a pack row, or the reason it was dropped. */
export function toProduct(row) {
  // Sold in India: tagged so, or an Indian barcode. GS1 India issues the 890 prefix, and many Indian products were added
  // without a country tag, so the tag alone would miss them.
  const taggedIndia = (row[COLUMNS.countries] ?? '').split(',').includes('en:india');
  const indianBarcode = /^890\d{10}$/.test((row[COLUMNS.code] ?? '').trim());
  if (!taggedIndia && !indianBarcode) return { drop: 'not sold in India' };
  const barcode = canonicalBarcode(row[COLUMNS.code]);
  if (!barcode) return { drop: 'barcode not valid' };
  const name = (row[COLUMNS.name] ?? '').replace(/[\u0000-\u001f]/g, ' ').replace(/\s+/g, ' ').trim();
  if (name.length < 2) return { drop: 'no name' };
  if (name.length > MAX_NAME) return { drop: 'name too long' };

  let kcal = num(row[COLUMNS.kcal]);
  if (kcal === null) {
    const kj = num(row[COLUMNS.kj]);
    if (kj !== null) kcal = kj / KJ_PER_KCAL;
  }
  const protein = num(row[COLUMNS.protein]);
  const carbs = num(row[COLUMNS.carbs]);
  const fat = num(row[COLUMNS.fat]);
  if (kcal === null || protein === null || carbs === null || fat === null) return { drop: 'missing calories or a macro' };
  if ([kcal, protein, carbs, fat].some((v) => v < 0)) return { drop: 'negative value' };
  if (kcal > MAX_KCAL_PER_100G || protein > 100 || carbs > 100 || fat > 100) return { drop: 'impossible value' };
  if (protein + carbs + fat > MACRO_SUM_TOLERANCE) return { drop: 'macros add to more than 100 g' };

  let fiber = num(row[COLUMNS.fiber]);
  let sugars = num(row[COLUMNS.sugars]);
  let satFat = num(row[COLUMNS.satFat]);
  if (fiber !== null && (fiber < 0 || fiber > carbs + PART_TOLERANCE)) fiber = null;
  if (sugars !== null && (sugars < 0 || sugars > carbs + PART_TOLERANCE)) sugars = null;
  if (satFat !== null && (satFat < 0 || satFat > fat + PART_TOLERANCE)) satFat = null;

  const expected = 4 * protein + 4 * (carbs - (fiber ?? 0)) + 2 * (fiber ?? 0) + 9 * fat;
  // Same rule as AWAKEN's engine (`LabelPlausibility`): the tolerance is measured against the label's own calories.
  if (Math.abs(kcal - expected) > Math.max(ATWATER.kcal, ATWATER.pct * kcal)) return { drop: 'calories do not match the macros' };

  let sodiumMg = num(row[COLUMNS.sodiumG]);
  if (sodiumMg === null) {
    const salt = num(row[COLUMNS.saltG]);
    sodiumMg = salt === null ? null : (salt / SALT_PER_SODIUM) * 1000;
  } else {
    sodiumMg *= 1000;
  }
  if (sodiumMg !== null && (sodiumMg < 0 || sodiumMg > 40000)) sodiumMg = null;

  const brand = (row[COLUMNS.brands] ?? '').split(',')[0].trim().slice(0, MAX_BRAND) || null;
  const serving = num(row[COLUMNS.serving]);
  return {
    product: {
      barcode, name, brand,
      servingGrams: serving !== null && serving > 0 && serving <= MAX_SERVING_GRAMS ? serving : null,
      kcal: round1(kcal), protein: round1(protein), carbs: round1(carbs), fat: round1(fat),
      sugars: round1(sugars), satFat: round1(satFat), fiber: round1(fiber), sodiumMg: sodiumMg === null ? null : Math.round(sodiumMg),
    },
  };
}

function args() {
  const a = process.argv.slice(2);
  const opt = (flag, fallback) => (a.includes(flag) ? a[a.indexOf(flag) + 1] : fallback);
  const input = a.find((x, i) => !x.startsWith('--') && !(i > 0 && a[i - 1].startsWith('--')));
  if (!input) {
    console.error('usage: node build-packaged.mjs <en.openfoodfacts.org.products.csv.gz> [--out packaged.db] [--schema schema.json] [--report report.md]');
    process.exit(2);
  }
  return { input, out: opt('--out', path.join(HERE, 'packaged.db')), schema: opt('--schema', path.join(HERE, 'schema.json')), report: opt('--report', path.join(HERE, 'report.md')) };
}

export async function build({ input, out, schema: schemaPath, report }) {
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8')).database;
  if (schema.version !== PACK_VERSION) throw new Error(`schema version ${schema.version} != PACK_VERSION ${PACK_VERSION}`);

  const hash = crypto.createHash('sha256');
  const raw = fs.createReadStream(input);
  raw.on('data', (chunk) => hash.update(chunk));
  const lines = readline.createInterface({ input: raw.pipe(zlib.createGunzip()), crlfDelay: Infinity });

  let header = null;
  const drops = new Map();
  const kept = [];
  let scanned = 0;
  let india = 0;
  for await (const line of lines) {
    if (header === null) {
      const names = line.split('\t');
      const index = {};
      for (const [key, column] of Object.entries(COLUMNS)) {
        index[key] = names.indexOf(column);
        if (index[key] < 0) throw new Error(`the export has no column "${column}"`);
      }
      header = { names, index };
      continue;
    }
    scanned += 1;
    const cells = line.split('\t');
    const row = {};
    for (const column of Object.values(COLUMNS)) row[column] = cells[header.names.indexOf(column)];
    const result = toProduct(row);
    if (result.drop) {
      if (result.drop !== 'not sold in India') { drops.set(result.drop, (drops.get(result.drop) ?? 0) + 1); india += 1; }
      continue;
    }
    india += 1;
    kept.push(result.product);
  }

  // One product per barcode (the export has one row per code, but keep the guarantee).
  const unique = new Map(kept.map((p) => [p.barcode, p]));
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.rmSync(out, { force: true });
  const db = new DatabaseSync(out);
  db.exec('PRAGMA journal_mode = DELETE');
  for (const entity of schema.entities) {
    db.exec(entity.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
    for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
  }
  for (const q of schema.setupQueries) db.exec(q);
  db.exec(`PRAGMA user_version = ${schema.version}`);
  db.exec('BEGIN');
  const add = db.prepare('INSERT INTO product (barcode, name, brand, serving_grams, kcal, protein, carbs, fat, sugars, sat_fat, fiber, sodium_mg) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
  for (const p of [...unique.values()].sort((a, b) => a.barcode.localeCompare(b.barcode))) {
    add.run(p.barcode, p.name, p.brand, p.servingGrams, p.kcal, p.protein, p.carbs, p.fat, p.sugars, p.satFat, p.fiber, p.sodiumMg);
  }
  db.exec('COMMIT');
  db.exec('VACUUM');
  db.close();

  const size = fs.statSync(out).size;
  const lastModified = fs.statSync(input).mtime.toISOString().slice(0, 10);
  const md = [
    '# Packaged-food pack report',
    '',
    `- Source file: ${path.basename(input)} (Open Food Facts nightly export, file date ${lastModified}), SHA-256 \`${hash.digest('hex')}\``,
    `- Rows in the export: ${scanned.toLocaleString('en')}`,
    `- Sold in India: ${india.toLocaleString('en')}`,
    `- Kept: **${unique.size.toLocaleString('en')}**`,
    `- Pack size on disk: ${(size / 1024 / 1024).toFixed(2)} MB`,
    '',
    '## Dropped (products sold in India)',
    '',
    ...[...drops.entries()].sort((a, b) => b[1] - a[1]).map(([reason, n]) => `- ${reason}: ${n.toLocaleString('en')}`),
    '',
    'Limits used: calories at most 900 per 100 g; each macro at most 100 g; protein + carbs + fat at most 105 g;',
    'calories within max(20 kcal, 20 % of the label calories) of 4 x protein + 4 x (carbs - fibre) + 2 x fibre + 9 x fat.',
    '',
  ].join('\n');
  fs.writeFileSync(report, md);
  return { scanned, india, kept: unique.size, size, drops };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  build(args()).then((r) => console.log(`kept ${r.kept} of ${r.india} Indian products (${(r.size / 1024 / 1024).toFixed(2)} MB)`)).catch((e) => { console.error(e.message); process.exit(1); });
}
