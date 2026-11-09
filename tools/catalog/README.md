# Bundled catalogs (Gym + Diet)

The app ships two offline, public-domain datasets so Gym and Diet work without any network access (CLAUDE.md invariant 8).
These scripts run on a developer machine only; the app never downloads anything.

| Asset | Source | Pinned version | Licence |
|---|---|---|---|
| `app/src/main/assets/gym/exercises.json` | [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) `dist/exercises.json` | commit `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5` | The Unlicense (public domain) |
| `app/src/main/assets/nutrition/foods.json` | [USDA FoodData Central](https://fdc.nal.usda.gov/download-datasets) CSV: SR Legacy + FNDDS (Survey) | SR Legacy `2018-04`, FNDDS `2024-10-31` | Public domain / CC0 1.0 |

Exercise images are not bundled; the app draws its own body map. USDA values are used as published, with no edits.
If a food looks wrong (for example FNDDS "Cheese, paneer"), people can log a custom food instead.

## Rebuild

Download the sources into a scratch folder outside the repo, then:

```
node tools/catalog/build-exercises.mjs <scratch>/exercises.json
node tools/catalog/build-foods.mjs <scratch>/FoodData_Central_sr_legacy_food_csv_2018-04 <scratch>/FoodData_Central_survey_food_csv_2024-10-31
```

- Exercises: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/<commit>/dist/exercises.json`
- Foods: `https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip` and `.../FoodData_Central_survey_food_csv_2024-10-31.zip`

SR Legacy rows key nutrients by FDC nutrient id (1008 = kcal); FNDDS rows key them by the legacy nutrient number
(208 = kcal). `build-foods.mjs` maps both onto the engine's `Nutrient` keys. Foods without energy are dropped.
`CatalogTest` in `:app` checks that the files parse and that everyday searches still work.

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
