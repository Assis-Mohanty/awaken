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
