// Run with: node --test
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { build, canonicalBarcode, toProduct } from './build-packaged.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const row = (over = {}) => ({
  code: '5901234123457', product_name: 'Digestive biscuits', brands: 'Acme, Other', countries_tags: 'en:india,en:nepal', serving_quantity: '30',
  'energy-kcal_100g': '480', energy_100g: '2008', proteins_100g: '7', carbohydrates_100g: '64', fat_100g: '21',
  sugars_100g: '20', 'saturated-fat_100g': '10', fiber_100g: '3', sodium_100g: '0.4', salt_100g: '1', ...over,
});

test('barcodes: check digits, one 13-digit form, store codes dropped', () => {
  assert.equal(canonicalBarcode('5901234123457'), '5901234123457');
  assert.equal(canonicalBarcode('036000291452'), '0036000291452'); // UPC-A gets a leading 0
  assert.equal(canonicalBarcode('96385074'), '0000096385074'); // EAN-8
  assert.equal(canonicalBarcode('5901234123458'), null); // wrong check digit
  assert.equal(canonicalBarcode('abc'), null);
  assert.equal(canonicalBarcode('2000000000008'), null); // 2xx: in-store code
});

test('a complete Indian product is kept with grams converted to milligrams', () => {
  const { product } = toProduct(row());
  assert.equal(product.barcode, '5901234123457');
  assert.equal(product.brand, 'Acme');
  assert.equal(product.servingGrams, 30);
  assert.equal(product.sodiumMg, 400);
  assert.equal(product.kcal, 480);
});

test('salt stands in for a missing sodium, and kilojoules for missing kilocalories', () => {
  const { product } = toProduct(row({ 'sodium_100g': '', 'energy-kcal_100g': '', energy_100g: '2008' }));
  assert.equal(product.sodiumMg, 400); // salt 1 g / 2.5 = 0.4 g sodium
  assert.equal(product.kcal, 479.9); // 2008 kJ / 4.184
});

test('products are dropped for the right reason', () => {
  assert.equal(toProduct(row({ countries_tags: 'en:france' })).drop, 'not sold in India');
  assert.equal(toProduct(row({ code: '5901234123458' })).drop, 'barcode not valid');
  assert.equal(toProduct(row({ product_name: ' ' })).drop, 'no name');
  assert.equal(toProduct(row({ proteins_100g: '' })).drop, 'missing calories or a macro');
  assert.equal(toProduct(row({ 'energy-kcal_100g': '950' })).drop, 'impossible value');
  assert.equal(toProduct(row({ proteins_100g: '50', carbohydrates_100g: '50', fat_100g: '20' })).drop, 'macros add to more than 100 g');
  assert.equal(toProduct(row({ 'energy-kcal_100g': '900' })).drop, 'calories do not match the macros');
});

test('a bad sugar, saturated fat or fibre value is dropped from the row but the product stays', () => {
  const { product } = toProduct(row({ sugars_100g: '90', 'saturated-fat_100g': '40', fiber_100g: '80' }));
  assert.equal(product.sugars, null);
  assert.equal(product.satFat, null);
  assert.equal(product.fiber, null);
  assert.equal(product.kcal, 480);
});

test('the build writes a database in the Room schema with only the kept products', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pack-'));
  const columns = ['code', 'product_name', 'brands', 'countries_tags', 'serving_quantity', 'energy-kcal_100g', 'energy_100g', 'proteins_100g', 'carbohydrates_100g', 'fat_100g', 'sugars_100g', 'saturated-fat_100g', 'fiber_100g', 'sodium_100g', 'salt_100g'];
  const lines = [
    columns.join('\t'),
    ...[row(), row({ code: '036000291452', product_name: 'Protein bar', countries_tags: 'en:india' }), row({ code: '3017620422003', countries_tags: 'en:france' }), row({ code: '5901234123458' })]
      .map((r) => columns.map((c) => r[c] ?? '').join('\t')),
  ];
  const input = path.join(dir, 'export.csv.gz');
  fs.writeFileSync(input, zlib.gzipSync(lines.join('\n')));
  const out = path.join(dir, 'packaged.db');

  const result = await build({ input, out, schema: path.join(HERE, 'schema.json'), report: path.join(dir, 'report.md') });

  assert.equal(result.kept, 2);
  const db = new DatabaseSync(out);
  assert.deepEqual(db.prepare('SELECT barcode, name FROM product ORDER BY barcode').all().map((r) => [r.barcode, r.name]), [['0036000291452', 'Protein bar'], ['5901234123457', 'Digestive biscuits']]);
  assert.equal(db.prepare('PRAGMA user_version').get().user_version, 1);
  const schema = JSON.parse(fs.readFileSync(path.join(HERE, 'schema.json'), 'utf8')).database;
  assert.equal(db.prepare('SELECT identity_hash AS h FROM room_master_table').get().h, schema.identityHash);
  db.close();
  assert.match(fs.readFileSync(path.join(dir, 'report.md'), 'utf8'), /Kept: \*\*2\*\*/);
});
