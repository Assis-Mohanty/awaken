// Builds app/src/main/assets/gym/exercises.json from free-exercise-db's dist/exercises.json (The Unlicense).
// Usage: node tools/catalog/build-exercises.mjs <path/to/exercises.json>
// See tools/catalog/README.md for the pinned commit. Images are dropped: the app shows its own body map instead.
import fs from 'node:fs';
import path from 'node:path';

const [input] = process.argv.slice(2);
if (!input) {
  console.error('usage: node build-exercises.mjs <exercises.json>');
  process.exit(1);
}
const OUT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '../../app/src/main/assets/gym/exercises.json');

const source = JSON.parse(fs.readFileSync(input, 'utf8'));
const exercises = source
  .map((e) => ({
    id: `fedb:${e.id}`,
    name: e.name.trim(),
    category: e.category ?? null,
    equipment: e.equipment ?? null,
    level: e.level ?? null,
    force: e.force ?? null,
    mechanic: e.mechanic ?? null,
    primary: e.primaryMuscles ?? [],
    secondary: e.secondaryMuscles ?? [],
    instructions: (e.instructions ?? []).map((s) => s.trim()).filter(Boolean),
  }))
  .sort((a, b) => a.name.localeCompare(b.name));

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify({ version: 1, source: 'yuhonas/free-exercise-db', exercises }));
console.log(`wrote ${exercises.length} exercises, ${fs.statSync(OUT).size} bytes → ${OUT}`);
