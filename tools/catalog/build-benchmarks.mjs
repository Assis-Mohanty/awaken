// Builds app/src/main/assets/gym/benchmarks.json (GD §18.3).
//
//   node tools/catalog/build-benchmarks.mjs <openpowerlifting.csv> <dataset-label> [standards.json]
//
// Percentile part: OpenPowerlifting (public domain), raw full-power meets, one row per lifter (their best total), ages
// 18-59 or unknown, no disqualifications. For each lift and sex it stores the 99 values of (best lift / body weight) at
// the 1st..99th percentile: a quantile table, no fitted model. [standards.json] is the hand-entered military tables
// (tools/catalog/gym-standards.json, extracted from the official Army PDF); its rows are copied through unchanged.
import { createReadStream, readFileSync, writeFileSync } from 'node:fs';
import { createInterface } from 'node:readline';

const [csvPath, label, standardsPath] = process.argv.slice(2);
if (!csvPath || !label) {
  console.error('usage: node build-benchmarks.mjs <openpowerlifting.csv> <dataset-label> [standards.json]');
  process.exit(1);
}

const OUT = new URL('../../app/src/main/assets/gym/benchmarks.json', import.meta.url);
const LIFTS = { squat: 'Best3SquatKg', bench: 'Best3BenchKg', deadlift: 'Best3DeadliftKg' };
const POPULATION = 'people who compete in raw powerlifting meets, ages 18 to 59';
const LICENCE = 'Public domain: OpenPowerlifting contributes its data to the public domain (CC0-style waiver of copyright).';
const SOURCE = `OpenPowerlifting bulk data ${label}, https://www.openpowerlifting.org, https://gitlab.com/openpowerlifting/opl-data`;

// Catalog ids (free-exercise-db) mapped to the lift they are benchmarked as.
const EXERCISES = {
  'fedb:Barbell_Squat': 'squat',
  'fedb:Barbell_Full_Squat': 'squat',
  'fedb:Barbell_Bench_Press_-_Medium_Grip': 'bench',
  'fedb:Bench_Press_-_Powerlifting': 'bench',
  'fedb:Barbell_Deadlift': 'deadlift',
  'fedb:Sumo_Deadlift': 'deadlift',
  // Army test standards (a level, not a percentile): see gym-standards.json.
  'fedb:Pushups': 'pushup',
  'fedb:Plank': 'plank',
};

function parseLine(line) {
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

const rl = createInterface({ input: createReadStream(csvPath, { encoding: 'utf8' }), crlfDelay: Infinity });
let header = null;
let col = {};
const best = new Map(); // "Name|Sex" -> { total, bw, squat, bench, deadlift }
let rows = 0;
for await (const line of rl) {
  if (!header) {
    header = parseLine(line);
    header.forEach((h, i) => { col[h] = i; });
    continue;
  }
  rows++;
  const f = parseLine(line);
  if (f[col.Event] !== 'SBD' || f[col.Equipment] !== 'Raw') continue;
  const sex = f[col.Sex];
  if (sex !== 'M' && sex !== 'F') continue;
  if (['DQ', 'DD', 'NS', 'G'].includes(f[col.Place])) continue;
  const age = f[col.Age] === '' ? null : parseFloat(f[col.Age]);
  if (age !== null && (age < 18 || age >= 60)) continue;
  const bw = parseFloat(f[col.BodyweightKg]);
  const total = parseFloat(f[col.TotalKg]);
  if (!(bw > 30) || !(total > 0)) continue;
  const lifts = {};
  let ok = true;
  for (const [key, c] of Object.entries(LIFTS)) {
    lifts[key] = parseFloat(f[col[c]]);
    if (!(lifts[key] > 0)) ok = false;
  }
  if (!ok) continue;
  const id = `${f[col.Name]}|${sex}`;
  const held = best.get(id);
  if (!held || total > held.total) best.set(id, { total, bw, ...lifts, sex });
}

function quantiles(values) {
  values.sort((a, b) => a - b);
  const q = [];
  for (let p = 1; p <= 99; p++) {
    const pos = (values.length - 1) * (p / 100);
    const lo = Math.floor(pos);
    const hi = Math.ceil(pos);
    q.push(Math.round((values[lo] + (values[hi] - values[lo]) * (pos - lo)) * 10000) / 10000);
  }
  return q;
}

const percentile = [];
for (const sex of ['M', 'F']) {
  const own = [...best.values()].filter((r) => r.sex === sex);
  for (const key of Object.keys(LIFTS)) {
    percentile.push({
      key, sex, quantiles: quantiles(own.map((r) => r[key] / r.bw)), n: own.length,
      population: POPULATION, source: SOURCE, licence: LICENCE,
    });
  }
}

const standards = standardsPath ? JSON.parse(readFileSync(standardsPath, 'utf8')).standards : [];
writeFileSync(OUT, JSON.stringify({ version: 1, percentile, standards, exercises: EXERCISES }, null, 1) + '\n');
console.log(`read ${rows} rows, ${best.size} lifters; wrote ${percentile.length} quantile tables and ${standards.length} standards`);
