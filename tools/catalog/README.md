# Bundled catalogs (Gym + Diet)

The app ships two offline, public-domain datasets so Gym and Diet work without any network access (CLAUDE.md invariant 8).
These scripts run on a developer machine only; the app never downloads anything.

| Asset | Source | Pinned version | Licence |
|---|---|---|---|
| `app/src/main/assets/gym/exercises.json` | [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) `dist/exercises.json` | commit `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5` | The Unlicense (public domain) |
| `app/src/main/assets/nutrition/foods.db` | [USDA FoodData Central](https://fdc.nal.usda.gov/download-datasets) CSV: SR Legacy + FNDDS (Survey) | SR Legacy `2018-04`, FNDDS `2024-10-31` | Public domain / CC0 1.0 |
| | [UK CoFID 2021](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid) (McCance and Widdowson), Excel | `McCance_Widdowsons_Composition_of_Foods_Integrated_Dataset_2021..xlsx` | Open Government Licence v3.0 (attribution required, see `assets/licenses`) |
| | `indian-foods.json`, `indian-foods-staples.json` (ours) | this repo | Own work, computed from the sources above |

Exercise images are not bundled; the app draws its own body map. USDA and CoFID values are used as published (CoFID carbohydrate
gets its fibre added, see ENGINE_DECISIONS #181). **IFCT 2017 and the Indian Nutrient Databank are not used and not consulted**:
their licences do not allow it (docs/diet/PLAN.md section 2). USDA Foundation Foods is not used (its CSV has no food table).

## Rebuild the food catalog

Run `./gradlew :app:kspDebugKotlin` once (it exports `app/schemas/.../CatalogDatabase/1.json`, which the script reads). Download the
sources into a scratch folder outside the repo and unzip them (also unzip the CoFID `.xlsx`; Node has no zip reader). Needs Node 22.5+.

```
node tools/catalog/build-catalog.mjs <sr_legacy_csv_dir> <fndds_csv_dir> <cofid_unzipped_dir> [--old-ids <previous foods.json>]
node tools/catalog/build-catalog.mjs find <same three dirs> dal tadka        # look up a source food by name
node tools/catalog/build-exercises.mjs <scratch>/exercises.json
```

- USDA: `https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip` and `.../FoodData_Central_survey_food_csv_2024-10-31.zip`
- CoFID: the 2021 dataset link on the gov.uk page above.
- Exercises: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/<commit>/dist/exercises.json`

The script validates (negative values, kcal above 902, portions, the Atwater check, every old `usda:` id still present), writes `foods.db`
(at most 12 MB) and `tools/catalog/out/report.md`: counts, every warning, and for each Indian entry the source rows or ingredients it
resolved to. Read that list when you add a food. It exits 1 on any error and then writes no database.
`CatalogTest` in `:app` opens the asset through Room, checks the sources, searches and every search key against `FoodSearch.key`.

### Indian foods (`indian-foods*.json`)

Overlay: `{"from":"cofid:=Exact source name","n":"New name","v":"VEG","c":"Dal","pop":90,"p":["1 katori=150"],"a":["spelling"],"hi":["देवनागरी"]}`.
Recipe: `{"id":"slug","n":"Name","v":"VEG","r":[["sr:onions raw",30],["=cofid:Cheese, Paneer",100],["oil query",8,4,14]],"y":160,"p":[...]}`
where `y` is the cooked weight in grams and a fourth/third number pair is a low/high amount that makes a calorie range. Source rows are named by
their original names (`sr:`, `fndds:`, `cofid:`; `=` for an exact name). Write recipes from your own proportions: no cookbook, app or website.

## Gym benchmarks (GD section 18.3)

| Asset | Source | Pinned version | Licence |
|---|---|---|---|
| `app/src/main/assets/gym/benchmarks.json` (percentiles) | [OpenPowerlifting](https://www.openpowerlifting.org) bulk CSV, [opl-data](https://gitlab.com/openpowerlifting/opl-data) | `openpowerlifting-2026-11-07-f93a530c.csv` | Public domain (copyright waived) |
| `tools/catalog/gym-standards.json` (copied into `benchmarks.json`) | US Army Fitness Test Score Tables, [PDF](https://www.army.mil/e2/downloads/rv7/aft/AFT_Scoring_Scales_250601.pdf), approved 15 May 2025, effective 1 Jun 2025 | same PDF | US Government work, public domain |

```
node tools/catalog/build-benchmarks.mjs <scratch>/openpowerlifting-2026-11-07-f93a530c.csv "2026-11-07 (f93a530c)" tools/catalog/gym-standards.json
```

The CSV (about 830 MB) is not committed. The script keeps raw, full-power (`SBD`) results, one row per lifter (their best total),
ages 18 to 59 or unknown, no disqualifications, and writes the 99 percentile values of lift / body weight per lift and sex.
`gym-standards.json` was extracted from the PDF text (rows of 60 points and up, one column per sex and age band; the male
column is the Army male/combat column) and checked by `BenchmarkAssetsTest`. `plans.json` and `exercise_overlay.json` are
hand-written and checked by `GymAssetsTest`.
