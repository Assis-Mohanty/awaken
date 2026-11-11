# Diet v2: plan

Status: **PROPOSED 2026-11-10. Not approved. No code is written.** Part B starts only after the decisions in §14.
Branch: `feature/diet-v2`, cut from `feature/focus-protection` at `3dc75d3e` (see R7).

In short: the diary, search, goals and trackers are all buildable offline with what the repo already has.
Three things are not as the brief hoped, and each needs a decision:

1. **No Indian source is usable today.** IFCT 2017 needs written permission; INDB has no licence at all and is built
   on IFCT. The plan gets Indian foods a different way: our own recipes computed from USDA and UK ingredients (§2.3).
2. **No Indian food photo dataset is cleared for commercial use.** The photo scanner is built end to end, but ships
   OFF with no real model until we have training photos we are allowed to use (§5.3).
3. **ML Kit sends metrics to Google**, which breaks invariant 8. Barcodes use ZXing instead (§6.1).

Every number marked **PROPOSED** is a proposal. On approval, Batch 1 copies the approved numbers into
`GAME_DESIGN.md` §20 before any code uses them (§12 holds the draft).

Research was done on 2026-11-10 against live pages. Where a page could not be read, the text says **not verified**
and nothing was filled in from memory.

---

## 0. What the repo actually has (read this first)

| # | The brief assumes | The repo has | Effect |
|---|---|---|---|
| R1 | A diet section to rebuild | A working tracker (PRD F27): `feature/diet` (3 files, about 1,100 lines), `DietTracker`, `DietDao`, `NutritionMath`, `FoodCatalog`. 13k USDA foods in one 2.6 MB JSON, loaded into memory, searched by substring (`CatalogSearch`). Diary rows snapshot the food's per-100 g nutrients | Extend, do not restart. Snapshots mean a catalog change never rewrites history |
| R2 | Health Connect for nutrition, weight, steps "if already integrated" | Health Connect is **read-only** for steps, exercise, sleep, distance, active calories, heart rate. No nutrition, no weight, no write permission | Nutrition and weight sync is **later**, not v1 (new permissions and a new Play declaration). Active calories are already readable |
| R3 | A calculator to design | GD §13.1 already has approved numbers: Mifflin-St Jeor, activity 1.2 to 1.9, lose −500, gain +300, floor 1,200 / 1,500, protein 2.0 / 1.6 g per kg, fat 25 % | Keep them as the defaults. New numbers only add options around them (§4) |
| R4 | An under-18 and BMI guard to build | `BodyMath.BMI_BELOW = 18.5`, `TARGET_MIN_AGE = 18` and `WeightTargetRules` already guard the onboarding weight target (OB17). Diet setup itself has no guard: age range is 13 to 100 | Reuse the constants; add the guard to Diet (§4.5) |
| R5 | One body weight | Two stored copies: `hunter_profile.weight_kg` and `nutrition_profile.weight_kg`, kept in step by `GymPlanner` | The new weight log writes both in one transaction (§4.6) |
| R6 | FTS search | No FTS anywhere. Room is at schema **v31** | New read-only catalog database plus a v32 migration (§3.5, §3.7) |
| R7 | Branch `feature/diet-v2` | `main` is 23 commits behind `feature/focus-protection` and has no gym redesign or schema v31 | The branch is cut from `feature/focus-protection`. Q1 |
| R8 | Diet may pay a small XP reward | GD §13 and ENGINE_DECISIONS #95: "Diet mints no XP", and Diet tables are outside the ledger | The reward is a reversal of an approved rule and needs its own approval (§9) |
| R9 | Custom meals | `Meal` is an enum of 4, stored as text in `food_log.meal` | A `meal_slot` table whose built-in ids are the 4 enum names, so old rows need no rewrite (§3.7) |
| R10 | Camera | No `CAMERA` permission, no CameraX, no ML runtime in `libs.versions.toml` | New dependencies in Batches 7 and 8 (§5, §6) |
| R11 | Scripts | `tools/catalog/*.mjs` are Node (Python is not installed; Node is v24) | The data pipeline stays Node. Model training needs Python somewhere else. Q9 |

---

## 1. Feature list

| Feature | When | Notes |
|---|---|---|
| Diary by meal (breakfast, lunch, snack, dinner) | **v1** | exists; restyled |
| Custom meals (rename, add, reorder, max 8 PROPOSED) | **v1** | `meal_slot` |
| Search, recents, favourites | **v1** | FTS (§3.5) |
| Custom foods (per 100 g or per serving, optional barcode) | **v1** | exists; extended |
| Saved meals (re-add a group in one tap) | **v1** | |
| Recipes (ingredients, servings, cooked weight, scale) | **v1** | Batch 6 |
| Quick-add calories (optional macros) | **v1** | |
| Copy a meal or a whole day to another day | **v1** | |
| Edit serving and units | **v1** | exists; Indian units first |
| Notes (per day and per entry) | **v1** | |
| Water tracker | **v1** | no XP |
| Weight log with trend | **v1** | |
| Measurements log (waist, hip, chest, arm, thigh) | **v1** | cm only; a list and one trend line each |
| Macro and micronutrient breakdown | **v1** | shows how much of the day has data (§7.4) |
| Daily and weekly reports | **v1** | |
| Goal setting: calories, macro split, optional micro targets | **v1** | micro overrides already exist |
| Meal reminders (max 4 PROPOSED) | **v1** | reuses the reminder workers |
| Fasting timer | **v1, see Q7** | capped and guarded (§8.3). The repo has a standing "no fasting rule" line for Programs and AI Paths |
| Simple meal planner | **v1** | plan a day, then "Log it" |
| Barcode scanner, offline pack | **v1 after Q3** | Batch 7 |
| Photo scanner | **built, ships OFF** | Batch 8; needs the go/no-go (§5.5) |
| Exercise calories | **v1, display only** | Health Connect active calories shown next to the budget, **not added to it**: the activity multiplier already counts them. That is the double count to avoid |
| Imperial food units (oz, fl oz) | later | body weight already follows the kg/lb setting |
| Health Connect read/write for nutrition and weight | later | R2 |
| "Add exercise back to the budget" switch | later | needs a rule that does not double count |
| Glycaemic index, net carbs, added-sugar targets | later | sources lack the data |
| ICMR-NIN reference values in place of FDA Daily Values | later | values not verified, licence unknown (§7.4) |
| Online barcode lookup | **blocked on Q3** | no plan to change `INTERNET` |
| Social feed, coaches, paid tiers, cloud lookups for everything | skip | brief |
| AI meal suggestions, "what should I eat" | skip | invariant 5 and the "never diet advice" prompt rule |
| Calories "earned" by exercise, burn-it-off prompts | skip | safety rule |

---

## 2. Data sources and licence audit

### 2.1 Source table

| Source | Licence found (quote, link) | Attribution duty | Share-alike | Inside an APK | Verdict |
|---|---|---|---|---|---|
| **USDA FoodData Central** (Foundation, SR Legacy, FNDDS) | "USDA FoodData Central data are in the public domain and they are not copyrighted." "They are published under CC0 1.0 Universal (CC0 1.0)" ([fdc.nal.usda.gov](https://fdc.nal.usda.gov/), Licensing) | None in law. Requested: "we request that users list FoodData Central as the source of the data" | None | Yes | **Allowed.** Already bundled. Latest files: Foundation 2026-10-29, SR Legacy 2018-04 (final), FNDDS 2024-10-31 |
| **UK CoFID 2021** (McCance and Widdowson) | "All content is available under the Open Government Licence v3.0, except where otherwise stated" ([gov.uk CoFID page](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid)). OGL: free to "exploit the Information commercially and non-commercially for example, by combining it with other Information" ([OGL v3](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/)) | Yes: "acknowledge the source of the Information in your product or application". No CoFID-specific statement found, so the default applies: "Contains public sector information licensed under the Open Government Licence v3.0." | None | Yes | **Allowed.** Must not suggest official endorsement. Number of foods: not found on the page |
| **Open Food Facts** | "The Open Food Facts database is available under the Open Database License." "Individual contents of the database are available under the Database Contents License." "Products images are available under the Creative Commons Attribution ShareAlike licence." ([terms of use](https://world.openfoodfacts.org/terms-of-use)) | Yes: "have to mention the licence and to attribute the authorship to Open Food Facts with a link to https://openfoodfacts.org". Suggested text: "Contains data from Open Food Facts, available under the Open Database License" ([data page](https://world.openfoodfacts.org/data)) | **Yes**, for the database: "Any Derivative Database that You Publicly Use must be only under the terms of" the ODbL, and we must offer "The entire Derivative Database" or "A file containing all of the alterations made to the Database or the method of making the alterations" ([ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) §4.4, §4.6) | Yes, with the conditions in §6.3 | **Allowed with conditions.** Images are not used. Accuracy: "there are no assurances that the data is accurate, complete, or reliable" |
| **ICMR-NIN IFCT 2017** | The book's copyright page could **not** be read directly (the PDF is over the fetch limit). A search result quotes it as: material may not be stored or reproduced "in any electronic format for creating a product without the prior written permission of the National Institute of Nutrition". The NIN site says: "No part of this website may be reproduced, distributed, or transmitted in any form or by any means ... without the prior written permission of Institute." ([nin.res.in](https://www.nin.res.in/)) | n/a | n/a | **No, not without written permission** | **Not allowed today.** Missing: (1) a first-hand read of the copyright page (open the PDF and check page ii); (2) any published data licence; (3) a permissions contact (only `director.nin@icmr.gov.in` is on the site). The GitHub and npm copies (`nodef/ifct2017`, AGPL-3.0) license their code and claim no permission from NIN |
| **Indian Nutrient Databank (INDB)** | **No licence anywhere.** The Anuvaad page says only "an open-access database" and "© 2021 Anuvaad Solutions" ([anuvaad.org.in](https://www.anuvaad.org.in/indian-nutrient-databank/)). The GitHub repo has no LICENSE file and no licence text in the README ([repo](https://github.com/lindsayjaacks/Indian-Nutrient-Databank-INDB-)). The paper is CC BY 4.0, but that covers the article ([PMC11277795](https://pmc.ncbi.nlm.nih.gov/articles/PMC11277795)) | Unknown | Unknown | **Unknown** | **Unclear, treat as not allowed.** Missing: (1) a licence for the data files; (2) whether NIN allowed IFCT values to be redistributed inside it (528 raw items come from IFCT 2017, 369 from IFCT 2004; the README says the IFCT tables "must be requested from the original source"); (3) the status of the 1,014 recipes, taken from two cookbooks and 148 websites. "Open access" with no licence means default copyright |

### 2.2 What this means

- Bundle: **USDA** (Foundation + SR Legacy + FNDDS) and **CoFID 2021**. Both allow it outright.
- Bundle separately, if Q3 says so: an **Open Food Facts** India pack for barcodes (§6).
- **Do not use IFCT or INDB values in any form** until a written answer arrives. That includes not using them to
  "check" or "tune" our own rows. If permission comes later, it is a data-only update.

### 2.3 Indian foods without IFCT or INDB

About 300 everyday Indian foods, built by us:

- **Direct rows.** Where USDA or CoFID already has the food as eaten (plain rice, chapati, dal, samosa, paneer, curd,
  ghee, many curries in CoFID), the row points at that source food. Tier **curated**.
  CoFID's coverage of South Asian dishes is expected but **not verified**; Batch 1 counts it before relying on it.
- **Recipe rows.** Everything else is a recipe we write ourselves: ingredient grams from USDA or CoFID raw foods, a
  cooked weight, and the same recipe maths the app uses (§8.1). Tier **derived**. No cookbook, app or website is
  copied; proportions are our own, written fresh, and stored in `tools/catalog/indian-foods.json`.
- Split (PROPOSED): breads 20 · rice dishes 25 · dals and legumes 30 · vegetable dishes 45 · non-veg curries 30 ·
  South Indian breakfast 30 · snacks and chaat 35 · sweets 30 · drinks 20 · dairy and basics 15 · other 20.
- Honest limit: recipe rows are estimates. The UI says so with an "Estimate" chip and a range (§3.4). They are only
  as good as the recipe and the cooked weight we assume. Q8 asks how they get reviewed.

### 2.4 Attribution screen text (Settings, "Food data")

> **Food data**
>
> **USDA FoodData Central.** U.S. Department of Agriculture, Agricultural Research Service, Beltsville Human Nutrition
> Research Center. FoodData Central. fdc.nal.usda.gov. Public domain (CC0 1.0). Foundation Foods (October 2026),
> SR Legacy (April 2018), FNDDS 2021-2023.
>
> **UK Composition of Foods Integrated Dataset 2021.** Contains public sector information licensed under the Open
> Government Licence v3.0. Source: Public Health England, McCance and Widdowson's The Composition of Foods Integrated
> Dataset. This app is not endorsed by the UK Government.
>
> **Open Food Facts** (packaged foods). Contains data from Open Food Facts, available under the Open Database License
> (ODbL). openfoodfacts.org. The subset in this app and the method used to build it are available at [link, Q4].
> Values come from product labels entered by volunteers and may be wrong.
>
> **Indian dishes.** Values marked "Estimate" are calculated by this app from its own recipes using the ingredient data
> above. Home cooking varies, so treat them as a guide.
>
> Values are for information only. See "Not medical advice".

The same text goes into `assets/licenses/DATA_SOURCES.txt`. The Open Food Facts paragraph appears only if the pack ships.

### 2.5 Permission emails (drafts; send from your own address)

**To NIN** (`director.nin@icmr.gov.in`; ask them to forward to whoever handles publications):

> Subject: Request for permission to use IFCT 2017 nutrient values in a mobile app
>
> Dear Director,
>
> I am an independent developer in India building AWAKEN, an Android habit and fitness app with an offline food
> diary. I would like to include nutrient values (per 100 g) for foods from the Indian Food Composition Tables 2017
> (Longvah, Ananthan, Bhaskarachary and Venkaiah) so that people can log Indian foods accurately.
>
> What I am asking for:
> 1. Written permission to store IFCT 2017 values for [number] foods inside the app, on the user's device. The app
>    has no server; the data is not offered for download or resale.
> 2. The attribution wording you require. I would show the full citation and "Used with permission of ICMR-National
>    Institute of Nutrition" on the app's data sources screen.
> 3. Whether a fee or a licence agreement applies, and whether the permission would cover a paid or ad-free
>    commercial release on Google Play.
> 4. Whether the same applies to Nutritive Value of Indian Foods (Gopalan et al.).
>
> If permission is not possible, a short reply saying so would help me avoid any misuse. Thank you for your time.
>
> [your name, city, email, phone]

**To the INDB authors** (`aswathy@anuvaad.org.in`, cc `Lindsay.Jaacks@ed.ac.uk`, `awasthi@anuvaad.org.in`):

> Subject: Licence for the Indian Nutrient Databank (INDB) data files
>
> Dear Ms Vijayakumar,
>
> Thank you for publishing the INDB. I am an independent developer building an offline Android food diary and would
> like to use INDB recipe values in it. The paper is CC BY 4.0, but I could not find a licence for the data itself:
> the GitHub repository has no LICENSE file and the Anuvaad page describes it only as open access.
>
> Could you tell me:
> 1. Under what licence INDB.xlsx and the recipe files are released, and whether commercial use inside an app is
>    allowed? If it is CC BY 4.0, would you be willing to add a LICENSE file to the repository so this is on record?
> 2. Whether that licence covers the raw-food values taken from IFCT 2017 and IFCT 2004, or whether I need separate
>    permission from ICMR-NIN for those.
> 3. Whether the recipes taken from the two cookbooks and the listed websites can be redistributed as recipes, or
>    only as the calculated nutrient values.
> 4. The attribution you would like shown in the app.
>
> [your name, city, email]

---

## 3. Offline data pipeline (design)

### 3.1 Shape

```
sources (downloaded to a scratch folder, never committed)
  USDA Foundation + SR Legacy + FNDDS csv        CoFID 2021 xlsx (saved as csv)
  tools/catalog/indian-foods.json (ours)         tools/catalog/portions.json (ours)   aliases.json (ours)
        │
        ▼  node tools/catalog/build-catalog.mjs <scratch>
  validate → merge → de-duplicate → write
        │
        ▼
  app/src/main/assets/nutrition/foods.db      (SQLite, read-only in the app)
  tools/catalog/out/report.md                 (counts, every warning, size)   not shipped

  separate, only if Q3 = (a) or (c):
  node tools/catalog/build-packaged.mjs <off-export>  →  assets/nutrition/packaged.db
```

- `foods.db` replaces `foods.json`. `build-foods.mjs` is deleted in the same batch.
- The script uses Node's built-in `node:sqlite` (Node 24 is installed). No new dependency.
- The tables are created from the `createSql` in Room's exported schema (`app/schemas/...CatalogDatabase/1.json`),
  so the asset always matches what Room expects.
- **One implementation of the rules.** Recipe sums and the search key exist in `:engine`. The script repeats a few
  lines of the same arithmetic, and `CatalogTest` in `:app` proves they agree: it recomputes every derived row and
  every search key with the engine and compares.

### 3.2 Catalog schema (`CatalogDatabase`, file `foods.db`, version 1)

```
source(id PK, name, licence, url, version, attribution)            -- 'usda_fnd','usda_sr','usda_fndds','cofid','awaken'
food(id PK,                -- 'usda:<fdc_id>' (unchanged), 'cofid:<code>', 'in:<slug>'
     name, source_id FK, tier,            -- CURATED | DERIVED | ESTIMATED
     diet_tag NULL,                       -- VEG | EGG | NONVEG | NULL (unknown)
     category NULL,
     kcal REAL, protein REAL, carbs REAL, fat REAL,     -- per 100 g, the four everyone needs, as columns for sorting
     nutrients_json,                       -- every other nutrient per 100 g; a missing key = unknown
     kcal_low REAL NULL, kcal_high REAL NULL,           -- only where §3.4 applies
     popularity INT)                       -- 0 default; the ~300 Indian rows and staples rank higher
portion(food_id FK, position, unit,       -- KATORI | ROTI | PIECE | CUP | TBSP | TSP | GLASS | SLICE | PLATE | OTHER
        label, grams REAL)                -- "1 katori", 150.0
alias(food_id FK, alias, lang)            -- 'en' | 'hi' (Devanagari) | 'hi-Latn' (transliteration)
food_fts  FTS4(name, aliases, keys; tokenize=unicode61)     -- one row per food; keys = search keys of name + aliases
meta(key PK, value)                       -- catalog_version, built_at, counts
```

Every row carries its licence through `source_id`. `in:` rows are ours (`awaken`) and list the ingredient sources in
`nutrients_json._from`.

Nutrients: the 21 the engine has now, plus **zinc, vitamin B12, folate** (24). All three have FDA Daily Values (§7.4).

### 3.3 Serving presets

Volumes are fixed (PROPOSED): katori 150 ml · cup 240 ml · glass 250 ml · tbsp 15 ml · tsp 5 ml.
Grams per food = volume × that food's density, written by hand in `portions.json` for the Indian set and taken from
USDA `food_portion` for USDA rows. Counted units are per food (PROPOSED examples: roti 40 g, paratha 80 g, idli 40 g,
dosa 85 g, samosa 100 g). Every one of these gram weights is our own figure and is listed in `report.md` for review.
Order in the picker: Indian units first, then grams.

### 3.4 Ranges for home cooking

A recipe row can carry a low and a high oil or ghee amount. The pipeline computes kcal for both.
If `(high − low) / mid ≥ 15 %` (PROPOSED) the row stores `kcal_low` and `kcal_high`; the app shows "180 to 240 kcal"
and logs the middle value. No range is shown for curated rows.

### 3.5 Search

- FTS4, not FTS5 (Q2). Room supports FTS4 natively and it exists on every API 28 phone. FTS5 would need a bundled
  SQLite (about 1 MB per ABI) for a ranking function we can do in Kotlin.
- **Search key** (`FoodSearch.key`, in `:engine`), applied to names, aliases and the query: lowercase, strip accents and
  punctuation, then fold common transliteration variants: `aa→a`, `ee→i`, `oo→u`, `w→v`, `ph→f`, drop `h` after
  `b c d g j k p t`, collapse double letters, drop a plural `s`. So "chapathi", "chapati", "chappati" and "chapatti"
  give one key. Devanagari aliases are matched as typed.
- **Query**: every token must match; the last token is a prefix (`dal ma*`).
- **Typos**: if FTS returns fewer than 5 rows (PROPOSED), tokens are also matched within edit distance 1 against the
  alias keys of the Indian set only (a few thousand strings, in memory).
- **Ranking** (`FoodSearch.rank`, pure, in `:engine`), highest first: favourite · logged in the last 30 days (more
  often first) · exact name or alias · name starts with the query · `popularity` · tier (curated, derived, estimated) ·
  shorter name. The user's own foods and recipes are searched with the same key and ranked in the same list.
- A blank query shows recents, as today.

### 3.6 Validations (the script fails on an error and lists every warning in `report.md`)

| Check | Rule |
|---|---|
| Atwater | `est = 4·protein + 4·carbs + 9·fat + 7·alcohol`. Warning when `|kcal − est| > max(20 kcal, 20 %)` (PROPOSED). **Error** for our own `in:` rows above `max(10 kcal, 10 %)` (PROPOSED). USDA and CoFID rows only warn, because they use food-specific factors and carbs include fibre |
| Range | kcal 0 to 902 per 100 g; protein + carbs + fat + water ≤ 105 g; no negative values |
| Units | every nutrient in the unit the engine expects (kcal, g, mg, µg); kJ-only rows are converted (÷ 4.184) and marked |
| Null micros | missing stays missing (never 0). `report.md` gives the share of rows with each micro |
| Duplicates | same search key and macros within 2 % (PROPOSED): keep the better tier (Foundation > SR Legacy > CoFID > FNDDS), move the other's name into `alias` |
| Portions | grams > 0 and ≤ 2,000; at least one portion on every `in:` row |
| Tags | every `in:` row has a `diet_tag`; USDA and CoFID rows get one from keyword rules or stay unknown |
| Ids | every `usda:` id already in `foods.json` still exists, so old diary rows still find their portions |

### 3.7 Size budget, versioning, updates

- Budget (PROPOSED): `foods.db` ≤ 12 MB on disk and ≤ 6 MB added to the download · `packaged.db` ≤ 3 MB · photo model
  ≤ 6 MB. Today's `foods.json` is 2.6 MB. The script prints the size and fails over budget.
- Room copies the asset on first open (`createFromAsset`), so on-device use is about twice the file.
- `meta.catalog_version` goes up by one per rebuild; `CatalogDatabase.version` goes up with it and the old copy is
  dropped and re-copied (`fallbackToDestructiveMigration` is safe here: the file holds no user data).
- Updates ship **only inside an app update**. Nothing is downloaded.

### 3.8 User data: Room migration v31 → v32 (no data loss)

All additive. No existing column changes type; no row is deleted.

| Table | Change |
|---|---|
| `food_log` | + `note` NULL, + `portion_count` REAL NULL, + `entry_source` TEXT NOT NULL DEFAULT 'SEARCH' (SEARCH, BARCODE, PHOTO, QUICK, MEAL, RECIPE, PLAN). `meal` stays text and now holds a `meal_slot.id` |
| `meal_slot` (new) | `id PK, name NULL, position, built_in`. Seeded with `BREAKFAST, LUNCH, DINNER, SNACK`, the exact strings already in `food_log.meal` |
| `custom_food` | + `brand` NULL, + `barcode` NULL (indexed), + `serving_grams` REAL NULL, + `updated_at` |
| `nutrition_profile` | + `kcal_delta` INT NULL (null = the goal's default), + `macro_preset` TEXT NOT NULL DEFAULT 'BALANCED', + `protein_per_kg` REAL NULL, + `plan_weight_kg` REAL NULL (filled from `weight_kg`), + `water_target_ml` INT NULL |
| `favourite_food` (new) | `food_id PK, added_at` |
| `saved_meal`, `saved_meal_item` (new) | items use the same snapshot columns as `food_log` |
| `recipe`, `recipe_ingredient` (new) | `recipe(id PK 'recipe:<uuid>', name, servings REAL, cooked_grams REAL NULL, note, created_at, updated_at)` |
| `water_log` (new) | `id PK, game_date, ml, logged_at` |
| `weight_log` (new) | `id PK, game_date, weight_kg, logged_at, note NULL`. Seeded with one row from `nutrition_profile` when it exists |
| `body_measure_log` (new) | `id PK, game_date, kind, value_cm` |
| `fasting_session` (new) | `id PK, started_at, target_minutes, ended_at NULL` |
| `meal_plan_entry` (new) | `id PK, game_date, meal, food snapshot columns, grams, logged INT` |
| `diary_note` (new) | `game_date PK, text` |
| `scan_feedback` (new) | `id PK, at, kind (PHOTO, BARCODE), shown_json, chosen_food_id NULL`. Local only |

Quick-add needs no table: a row with `food_id = 'quick'`, `grams = 100` and the typed totals as its snapshot.
`MigrationTest` 31 → 32 loads a v31 database with diary rows, custom foods and a profile, migrates, and checks every
old row, the day's totals and the plan are identical.

Diet tables stay outside the ledger (ENGINE_DECISIONS #95). The one exception is the daily reward (§9).

---

## 4. Calorie and goal calculator (`:engine`, pure)

### 4.1 What stays (GD §13.1, already approved)

BMR Mifflin-St Jeor · activity 1.2 / 1.375 / 1.55 / 1.725 / 1.9 · floor 1,200 (female) / 1,500 (male) ·
protein 2.0 g/kg (lose) or 1.6 g/kg · fat 25 % · fibre 14 g per 1,000 kcal · saturated fat ≤ 10 %.

### 4.2 Sources

| Number | Source | Status |
|---|---|---|
| Floor 1,200 / 1,500 | Harvard Health, 2024: "calorie intake should not fall below 1,200 a day in women or 1,500 a day in men", "except under the supervision of a health professional" ([link](https://www.harvardhealth.org/staying-healthy/calorie-counting-made-easy)) | verified. NIH's Body Weight Planner uses a lower floor ("at least 1000 calories/day"); we keep the higher one |
| Rate of loss | CDC, 2025: "about 1 to 2 pounds a week" ([link](https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html)). Harvard: 500 to 1,000 kcal below maintenance | verified |
| BMI 18.5 | WHO indicator "Underweight among adults, BMI < 18.5"; NHLBI: healthy weight "between 18.5 and 24.9" | verified as a label |
| Mifflin-St Jeor | Mifflin MD, St Jeor ST, et al. Am J Clin Nutr 1990;51:241-7 | **not verified today** (PubMed blocked the fetch). Check before it goes into GD |
| Activity multipliers | commonly published with the Harris-Benedict equation | **no primary source found.** Stated as a convention, not as a citation |
| Protein 1.4 to 2.0 g/kg (ISSN), 0.83 g/kg (ICMR/WHO) | — | **not verified today** (login wall, 404) |
| Macro ranges 45-65 / 20-35 / 10-35 % | US Dietary Reference Intakes | **not verified today** (blocked) |
| 7,700 kcal per kg | — | **not verified, and known to be rough.** Used only for the "about N kg a week" label, never for a promise |
| Daily Values for micros | FDA, current as of 2024-03-05 ([link](https://www.fda.gov/food/nutrition-facts-label/daily-value-nutrition-and-supplement-facts-labels)) | verified |
| Water 2.0 L / 2.5 L | EFSA 2010 | **not verified today** (403) |

Batch 2 does not start until every "not verified" row above is either read first-hand or its number is dropped. Q10.

### 4.3 New options (all PROPOSED)

| Id | Rule |
|---|---|
| DG1 | **Pace.** Lose: gentle −250 · steady **−500** (default, unchanged) · faster −750. Gain: steady **+300** (default, unchanged) · faster +500. Maintain: 0 |
| DG2 | **Rate cap.** The deficit actually used = `min(chosen, 25 % of round(TDEE))`, rounded down to a whole kcal. A surplus is capped at +500 |
| DG3 | **Floor** applies after the cap: `target = max(floor, round(TDEE) + delta)` |
| DG4 | **Label**: "about N kg a week" = `delta × 7 ÷ 7,700`, one decimal, always with "about" |
| DG5 | **Macro presets.** Balanced (default) = today's rule. High protein = 2.2 g/kg, fat 25 %, carbs the rest. Lower carb = protein as Balanced, fat 35 %, carbs the rest. Custom = three percentages that must add to 100, each within protein 10-35, carbs 20-65, fat 20-40 |
| DG6 | **Protein per kg** option: 0.8 to 2.2 in steps of 0.1; replaces the preset's protein; fat and carbs follow |
| DG7 | A manual calorie override may not be set below the floor. The field stops at the floor with one plain line: "The lowest target this app sets is 1,200 kcal." |
| DG8 | Carbs never go below 0; if protein + fat exceed the calories, carbs are 0 and the screen says the split does not fit |

### 4.4 Recalculation when weight changes

- Targets are computed from `plan_weight_kg`, not from the latest weigh-in, so a noisy scale never moves the budget.
- **Trend weight** = mean of the weigh-ins of the last 7 game-days, when there are at least 3 (PROPOSED DG9).
- When `|trend − plan weight| ≥ 1.0 kg` (PROPOSED DG10), the diary shows one card: "Your weight has changed. Update
  your targets?" with the old and new numbers. Accept sets `plan_weight_kg = trend`. Dismiss hides it for 7 days
  (PROPOSED DG11). Nothing changes silently, and the hunter's own overrides are kept.
- The guard in §4.5 is the exception: it applies at once, with no prompt.
- Age changes on 1 January (age = game-date year − birth year, as today).

### 4.5 Safety guard (`DietGuard`, `:engine`)

`targetsVisible = (gameDateYear − birthYear) > 18 AND BMI(plan height, latest weight) ≥ 18.5`

The age test is strict on purpose: only a birth year is stored, so "year difference 18" can be a 17-year-old.

When targets are not visible:
- hidden: calorie target and "remaining", the ring's target, pace and goal, weight target, macro targets, fasting
  timer, the "over or under" parts of reports, the recalculation card, the daily reward (§9);
- still there: logging, the day's totals, what is in each food, micros against reference values, water, the weight
  log (as a list and line, with no goal line);
- one neutral line where the target would be: "Targets are off for your profile. You can still log what you eat."
  No reason, no advice, no link.

### 4.6 Weight log

A weigh-in writes `weight_log`, `hunter_profile.weight_kg` and `nutrition_profile.weight_kg` in one transaction (the
latest by time wins). `plan_weight_kg` moves only through §4.4. Gym keeps reading `hunter_profile`.

### 4.7 Disclaimer

On the calculator, the goals screen and the weekly report: "This is an estimate for general information, not medical
advice. Talk to a doctor or dietitian before changing how you eat, especially if you are pregnant, under 18, or have
a health condition."

---

## 5. Photo scanner (suggest only)

### 5.1 Flow

camera or photo picker → bitmap in memory, scaled to 224 × 224 → `DishClassifier.top(bitmap, 3)` → three dish chips
plus "None of these" → pick one → serving picker (presets and a slider) → `:engine` computes from the food database →
add. "Add another from this photo" keeps the same photo and suggestions for the next item on the plate.

- The model outputs **label ids and scores only**. A label maps to one or more `food.id`s in a bundled
  `labels.json`. No number from the model reaches the diary.
- If the top score is under 0.15 (PROPOSED): "Not sure what this is" and the search box, no chips.
- The photo is never written to disk and is dropped when the sheet closes. No photo is stored with the entry in v1
  (so nothing to save, back up or leak). Gallery uses the system photo picker: no storage permission.
- "Wrong guess": tapping "None of these", or picking chip 2 or 3, writes one `scan_feedback` row (labels shown, food
  chosen, no image). It stays in Room and feeds a small "Scanner accuracy" figure on the debug screen. It is never sent.

### 5.2 Runtime options

| | LiteRT (TFLite) interpreter | MediaPipe Tasks image classifier | ML Kit custom image labelling |
|---|---|---|---|
| Artifact | `com.google.ai.edge.litert:litert` (1.4.2 for the Interpreter API; 2.3.2 is the newer line, released 2026-10-26) | `com.google.mediapipe:tasks-vision` (the docs give only `latest.release`) | `com.google.mlkit:image-labeling-custom` |
| Phones home | nothing stated on the page; standalone runtime, no Play services | nothing stated on the page | **Yes.** "The ML Kit APIs also send metrics about the performance and utilization of the APIs in your app to Google." and "You are responsible for informing users of your app about Google's processing of ML Kit metrics data" ([terms](https://developers.google.com/ml-kit/terms)). No opt-out found |
| Work for us | resize, bytes in, scores out: about 60 lines | less code | least code |
| Size | runtime a few MB per ABI (**measure in Batch 8**) | larger | 2 to 3 MB plus Google transport libraries |
| Verdict | **Recommended** | fallback if LiteRT pre-processing proves fiddly | **Rejected**: breaks invariant 8 |

Model (PROPOSED): EfficientNet-Lite0, int8, 224 × 224, fine-tuned from the public ImageNet weights. Google's figure
is 10 ms per image on a Pixel 6 CPU (23.5 ms for float32). File size is **not verified** (about 5 MB expected) and is
measured in Batch 8 against the 6 MB budget. Works on any 64-bit phone at minSdk 28; CPU only, no GPU delegate.

### 5.3 Training data: what was checked

| Dataset | Size | Licence found | Usable in a shipped app? |
|---|---|---|---|
| Khana (Indian, 2025) | 131k images, 80 dishes | "non-commercial research and/or educational purposes only"; "Khana does not own the copyright of the images" | **No** |
| FoodX-251 | 158k, 251 classes | "You will use the data only for non-commercial research and educational purposes." | **No** |
| Food-101 | 101k, 101 classes | none on the ETH page; Hugging Face lists the licence as "unknown"; images came from Foodspotting | **Unclear, treat as no** |
| IndianFood10 / 20 (IIIT Delhi) | 11.5k / 17.8k | none found; images scraped from Instagram | **No** |
| Indian Food Images, Indian Food Classification (Kaggle) | — | pages could not be read | **Not verified** |
| IndianFoodNet (Roboflow) | 5.5k, 30 classes | page blocked | **Not verified** |
| UEC FOOD-256, Food2K, ISIA-500 | — | sites unreachable | **Not verified** |
| RASOI (Indian thali) | — | search result says CC BY-NC-SA 4.0 | **No** |
| Nutrition5k | 5k plates | "under the Creative Commons V4.0 license ... even commercially" | Licence is fine, but "only collected in a few select cafeterias in California": **no Indian food** |
| Open Images V7 | 9M images | annotations CC BY 4.0; images "listed as having a CC BY 2.0 license ... you should verify the license for each image yourself" | **Yes, image by image**, with attribution. Few Indian dish classes |

Whether a model trained on non-commercial images may be shipped is unsettled law. The brief says do not guess, so
those datasets are out.

**Result: there is no cleared Indian dataset.** The only clean route is our own set: photos you take, plus
openly licensed photos (Open Images, Wikimedia Commons) recorded one by one with author and licence in
`tools/scanner/ATTRIBUTION.csv`. A useful model needs roughly 100 or more photos per dish; for 50 dishes that is
5,000 photos. That is weeks of collecting, not a build batch.

### 5.4 So what Batch 8 builds

- The whole path with a `DishClassifier` interface, a `FakeDishClassifier` for tests and previews, and the LiteRT
  implementation that loads `assets/scanner/dishes.tflite` **if the file exists**.
- `DIET_PHOTO_SCAN_ENABLED` (BuildConfig, like `StrictFeature`): **false in every build** until §5.5 passes. With it
  off, or with no model file, the Photo button is not shown at all and search, barcode and quick add carry the sheet.
- No model is trained in Part B. Collecting photos and training is a separate track (Q9).

### 5.5 Go / no-go test

- You photograph **200 real meals** as you eat them, on your phone, in normal light. For each: the photo and the
  right food from our database. These 200 are test-only and are never used for training.
- A debug screen runs the model over the folder and writes a CSV: file, expected, top 3, scores, milliseconds.
- **Ship only if all hold** (PROPOSED): top-3 hit rate ≥ 70 % on photos whose dish is in the label set ·
  ≥ 55 % over all 200 · median time ≤ 300 ms, slowest ≤ 1 s · of the photos with no dish in the label set, at most
  30 % show confident chips (score ≥ 0.15).
- With 200 photos a measured 70 % means the true rate is roughly 63 to 76 %. A result inside that band of the
  threshold is a no-go.
- Fail = the flag stays off and nothing else changes.

---

## 6. Barcode scanner (decision gate, Q3)

### 6.1 Decoder

| | ZXing core | ML Kit barcode scanning |
|---|---|---|
| Artifact | `com.google.zxing:core` (Apache 2.0, plain Java) | `com.google.mlkit:barcode-scanning:17.3.0` (bundled) or the Play services one |
| Size | about 0.5 MB | "About 2.4 MB" bundled; "About 200 KB" unbundled, with the model "downloaded the first time you run the scanner" |
| Network | none, no permissions | sends usage metrics to Google (§5.2); the unbundled one also downloads its model |
| Formats | EAN-13, EAN-8, UPC-A, UPC-E | same |
| Quality | weaker on blurred or curled codes | better |
| Verdict | **Recommended.** The only one that fits invariant 8 | rejected |

Camera: CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`, stable) with an `ImageAnalysis` use case; the
frame's luminance plane goes straight to ZXing. `CAMERA` is asked for on first use, with a one-line reason. The
manifest marks the camera as not required, so phones without one still install.

`:engine` `Barcode`: check-digit validation for EAN-13, EAN-8 and UPC-A, UPC-E expansion, and one canonical form
(13 digits, UPC-A gets a leading 0). A code that fails its check digit is ignored and scanning continues.

### 6.2 Lookup options

| | (a) Offline pack | (b) Opt-in online lookup | (c) Both |
|---|---|---|---|
| What | `packaged.db`: Open Food Facts products sold in India that have a barcode, a name, and kcal + protein + carbs + fat per 100 g | one HTTPS GET per scan to Open Food Facts | pack first, then online |
| Coverage | Open Food Facts lists **26,050** products for India. How many have full nutrition is **not measured**; expect a minority. Local brands will often miss | everything Open Food Facts has (3.7M products worldwide) | best |
| Size | 26k rows at about 100 bytes is under 3 MB; measured by the script | 0 | as (a) |
| Updates | only with an app update (nightly exports exist; we pin a date) | live | both |
| Permissions | `CAMERA` only | adds `INTERNET` to the Play build, which today has none | as (b) |
| Play and privacy | Data Safety unchanged: "User data ... only processed locally on the user's device and not sent off device does not need to be disclosed" | Data Safety changes (a barcode and an IP address leave the device), a consent screen, a second network destination against invariant 8, a `User-Agent` in the form `AppName/Version (ContactEmail)`, limit "15 req/min/IP address", "1 API call = 1 real scan by a user", a cache table, offline and error states | as (b) |
| Share-alike | §6.3 | none for lookups shown to one user, attribution still required | §6.3 |

**Recommendation: (a) now.** It keeps the app's main promise (no `INTERNET` in the Play build) and the manual label
form covers misses, and each miss the user fills in is found by the next scan. Measure the real hit rate on 50 packs
from your kitchen in Batch 7. If it is poor, (c) is the upgrade and is a separate decision with its own plan; nothing
here touches the `INTERNET` permission.

### 6.3 What the ODbL asks of option (a)

- Our filtered pack is a **Derivative Database** and the app shows it to the public, so: keep the pack under the ODbL,
  show the attribution (§2.4), include the licence text in `assets/licenses/`, and offer the pack or the method. We
  offer both: `build-packaged.mjs` plus the output file, at a public link (Q4).
- The pack is its **own file**, never merged into `foods.db`. The ODbL: "You are not required to license Collective
  Databases under this License". USDA, CoFID, our Indian rows and the user's own data stay outside the ODbL.
- The user's custom foods are their own entries, typed from a label. They are not Open Food Facts data and are not
  uploaded. Open Food Facts asks apps to send new products back; that would need the network, so it is not done.
- Product photos (CC BY-SA) are not used.
- This is a reading of the licence text, not legal advice. If the app is ever sold, have it checked.

### 6.4 Flow

Scan button → camera with a frame → code read (a short vibration) → **found**: a sheet with the product, per serving
and per 100 g side by side, macros, and any micros the label had; serving preselected (label serving, else 100 g);
meal preselected by time of day → **[Add]**. Two taps: Scan, Add.

**Not found**: "This one isn't in the list yet. Add it from the label?" → a form shaped like a nutrition label: name,
brand, "per 100 g" or "per serving of N g", energy (kcal, or kJ converted), protein, carbs, sugars, fat, saturated
fat, fibre, sodium or salt (salt ÷ 2.5) → saved as a custom food with the barcode → next scan finds it.

Lookup order: the user's custom foods by barcode, then the pack.

---

## 7. UX

### 7.1 Information architecture

```
Diet tab = Diary
├─ day header: date, previous / next, copy day, day note
├─ calorie ring (eaten, target, remaining) + macro bars          (ring without a target when §4.5 hides it)
├─ active calories line (Health Connect, display only)
├─ meal cards: entries, meal total, [Add], overflow: copy meal, save as meal
├─ water row: today's total, + glass
├─ tiles: Weight · Nutrients · Report · Plan · Fast
└─ top-right: Goals
Add food (one sheet)
├─ search field, with Scan barcode · Photo (flag) · Quick add beside it
└─ tabs: Recent · Favourites · My foods · Meals · Recipes        (typing switches to results)
Serving picker → Food details
Goals: Calculator (about you → goal and pace → your numbers) · Macro split · Nutrient targets
Weight and measurements · Water · Nutrients · Weekly report · Planner · Fasting · Recipes · Saved meals
Settings → Diet: meals, reminders, units, Food data (§2.4)
```

### 7.2 Screen list (each with phone, small phone, dark and light previews)

| Screen | States to preview |
|---|---|
| Diary | not set up · empty day · part-filled · over target · targets hidden · past day · recalculation card |
| Add food | recents empty · recents · typing · results · no results (offers custom food and quick add) · loading catalog · catalog failed |
| Serving picker | Indian units · grams only · range food ("180 to 240 kcal") · estimate chip · editing an entry |
| Food details | full data · micros missing |
| Quick add | calories only · with macros |
| Custom food / label form | per 100 g · per serving · with barcode · validation errors |
| Barcode scanner | permission ask · denied · scanning · found · not found · no camera |
| Photo scanner | permission ask · analysing · three guesses · not sure · second item from one photo · model missing |
| Saved meals, Recipes list | empty · list |
| Recipe editor | empty · ingredients · cooked weight set · scaled |
| Calculator | step 1, 2, 3 · floor reached · rate cap reached · targets hidden |
| Macro split | each preset · custom not adding to 100 |
| Nutrients | full day · partial data · nothing logged |
| Water | empty · part · target met |
| Weight and measurements | empty · one point · trend · targets hidden |
| Weekly report | no data · partial week · full week · targets hidden |
| Planner | empty · planned · some logged |
| Fasting | idle · running · reached · hidden by the guard |
| Meal reminders, Meals, Food data | list |

Mockups: `docs/diet/mocks/` (HTML plates in the existing `design/plates` style), made in Batch 4 before Compose, for
the Diary, Add food, Serving picker and Calculator. No mock is in this plan.

### 7.3 Rules of the surface

- Layering, tokens, Lexicon, ChartKit and 48 dp targets as CLAUDE.md. Camera preview and its frame live in
  `core/designsystem` (`ScanViewfinder`), not in the feature.
- Over target is shown with the neutral text colour and the words "N over". Never `critical`, never red, no warning icon.
- Copy: plain, second person. Banned in `strings_diet.xml`, enforced by `LexiconResourcesTest`: burn, earn, cheat,
  guilt, bad food, good food, clean, junk, sin, deserve, allowed, blew, failed.
- No "you have N left, go for a walk". No before/after imagery. No congratulation for being under target.
- Unknown is "—", never 0.
- TalkBack: the ring reads "1,420 of 2,200 kcal eaten"; each macro bar is one node; scanner results are announced.

### 7.4 Nutrients screen

- Reference values: **FDA Daily Values** (verified today): calcium 1,300 mg · iron 18 mg · magnesium 420 mg ·
  zinc 11 mg · vitamin A 900 µg · vitamin C 90 mg · vitamin D 20 µg · vitamin B12 2.4 µg · folate 400 µg ·
  potassium 4,700 mg · sodium 2,300 mg (limit). The first nine are already in GD §13.1; zinc, B12 and folate are new.
- ICMR-NIN values would suit Indian users better, but they could not be read today and their licence is unknown.
  Listed as "later".
- **Coverage.** Many foods have no micro data. For each nutrient the screen shows the share of the day's calories
  that came from foods with a value for it. Under 80 % (PROPOSED) the row says "Partial data" and shows no bar
  against the reference, so a gap in the data never reads as a gap in the diet.

---

## 8. Architecture

### 8.1 Modules and packages (no new Gradle module)

```
:engine  body/
  Nutrition.kt          (exists)  + ZINC, VITAMIN_B12, FOLATE
  GoalCalculator.kt     pace, rate cap, floor, presets, protein per kg      (wraps NutritionMath.plan)
  DietGuard.kt          targetsVisible, floor for overrides
  Portions.kt           PortionUnit, grams = count × portion grams, kJ → kcal, salt → sodium, per-serving → per-100 g
  RecipeMath.kt         totals, per serving, per 100 g cooked, scale
  Atwater.kt            estimate and tolerance check
  FoodSearch.kt         key(), rank()
  Barcode.kt            check digits, UPC-E expansion, canonical form
  WeightTrend.kt        trend weight, recalculation prompt rule
  WaterMath.kt, FastingRules.kt, DietReport.kt
:app
  core/data/catalog/    CatalogDatabase (foods.db), PackagedDatabase (packaged.db), FoodCatalog becomes a thin wrapper
  core/data/db/         entities, DietDao split into DiaryDao, FoodLibraryDao, BodyLogDao; MIGRATION_31_32
  core/scan/            BarcodeDecoder (ZXing), DishClassifier, LiteRtDishClassifier, FakeDishClassifier
  core/domain/          DietTracker (kept), FoodSearchUseCase, RecipeBook, WeightLog, LogDietDayUseCase, ...
  core/designsystem/    MacroBars, ServingStepper, RangeLabel, EstimateChip, ScanViewfinder, NutrientRow
  feature/diet/         diary/, add/, serving/, goals/, body/, report/, plan/, recipes/, scan/
```

Flag: `DietScanFeature.photoEnabled` (`DIET_PHOTO_SCAN_ENABLED`), false everywhere. Barcode has no flag.

### 8.2 Engine APIs (signatures indicative)

```kotlin
GoalCalculator.plan(profile, pace, preset, proteinPerKg?, overrides): NutritionPlan
GoalCalculator.appliedDelta(tdee: Int, chosen: Int): Int                  // DG2
DietGuard.targetsVisible(birthYear, gameDate, heightCm, weightKg): Boolean
Portions.grams(count: Double, portionGrams: Double): Double
Portions.per100FromServing(perServing: Nutrients, servingGrams: Double): Nutrients
RecipeMath.totals(ingredients): Nutrients ; perServing(totals, servings) ; per100Cooked(totals, cookedGrams) ; scale(recipe, factor)
Atwater.estimateKcal(n: Nutrients): Double ; Atwater.check(n, toleranceKcal, tolerancePct): AtwaterResult
FoodSearch.key(text): String ; FoodSearch.rank(candidates, query, signals): List<Ranked>
Barcode.canonical(raw: String): String?                                    // null when the check digit fails
WeightTrend.trend(entries, today): Double? ; WeightTrend.shouldPrompt(trend, planWeight, lastDismissed, today): Boolean
NutrientCoverage.of(entries): Map<Nutrient, Double>                        // §7.4
FastingRules.presets, FastingRules.state(session, now)
DietReport.week(days, plan?): WeekReport
```

### 8.3 Small trackers (all PROPOSED)

- **Water**: target 2,000 ml (female) / 2,500 ml (male), editable 500 to 6,000; glass 250 ml; buttons +250, +500,
  custom; no XP, no streak.
- **Fasting**: presets 12, 14, 16 hours; nothing longer; one session at a time; a quiet notification at the target;
  ending early is just "Ended". No streak, no XP, no "longer is better" copy. Hidden by §4.5.
- **Reminders**: up to 4, one per meal, off by default; text "Time to log lunch?"; never sent twice for one meal,
  never a follow-up for a missed one.
- **Planner**: entries for any day from today to 14 days ahead; "Log it" copies them into the diary.
- **Logging streak**: days in a row with at least one entry, shown on the weekly report only. No reward, no alert
  when it ends, no shield.

### 8.4 Tests

| Area | Test |
|---|---|
| Unit conversion | katori, roti, cup, tbsp to grams; kJ to kcal; salt to sodium; per-serving to per-100 g; kg/lb reuse `BodyMath` |
| Recipe maths | totals, per serving, per 100 g cooked, scale leaves per-serving unchanged, zero servings rejected |
| Atwater | pass, warn, error, alcohol term, high-fibre food |
| Goal calculator | vectors 198 to 211 (§12), floor, cap, presets, override below floor refused |
| Guard | age boundary by birth year, BMI 18.49 and 18.5 |
| Search | key folding ("chapathi" = "chapati"), prefix, typo fallback, ranking order with favourites and recents |
| Catalog | `CatalogTest`: opens `foods.db`; everyday searches (dal, roti, idli, paneer, chai) return an Indian row first; engine recomputes every derived row and every key; every row has a source |
| Migration | 31 → 32 with real v31 data, totals identical |
| Photo path | fake classifier → pick → portion → engine numbers; "not sure" branch; second item; nothing written to disk |
| Barcode | check digits, UPC-E expansion, UPC-A to EAN-13, bad digit ignored; found, not found, custom food by barcode |
| UI | Robolectric Compose tests per screen; new goldens named in each batch's commit |
| Boundaries | existing `FeatureBoundaryTest`, `ScreenLayeringTest`, `LexiconResourcesTest` (plus the banned words of §7.3) |

### 8.5 Docs to change

`GAME_DESIGN.md` new §20 and a pointer from §13.1 · `ENGINE_DECISIONS.md` from #179 · `PRD_MVP.md` F53 onward and
F27 marked replaced · `ARCHITECTURE.md` §2 schema and §3.13 · `DESIGN_SYSTEM.md` §4.12 and new components ·
`PLAY_STORE_LAUNCH.md` (§10 below) · `BACKLOG.md` (remove what is now built, add the "later" rows of §1) ·
`CLAUDE.md` (flag, `core/scan`, two more database files) · `tools/catalog/README.md` ·
`assets/licenses/DATA_SOURCES.txt` + `OGL-3.0.txt` + `ODbL-1.0.txt` · `docs/diet/PHONE_TEST.md` · `docs/diet/SCANNER_TEST.md`.

---

## 9. XP: none (Q6 answered "no XP on food", 2026-11-10; the rows below are the dropped proposal)

| Id | Rule |
|---|---|
| DX1 | **20 XP**, once per game-day, 100 % VIT. Half an Easy quest (BASE 40). No gold, no streak multiplier, no verification multiplier |
| DX2 | Paid the moment the day's diary has entries in **at least 2 different meals** |
| DX3 | Only for the current game-day. Filling in past days pays nothing |
| DX4 | Counts toward the SELF cap of 600 (§4.3). Past the cap it pays 0, no 25 % tail (same as gym, §18.4) |
| DX5 | Ledger: `(DIET_DAY, <game date>, XP_TOTAL)` and `(DIET_DAY, <game date>, XP_STAT, VIT)`, in one transaction with nothing else. A second trigger inserts nothing. Deleting entries later does not reverse it |
| DX6 | Pays nothing while §4.5 hides targets |
| DX7 | What you ate never matters: not calories, not being under target, not protein. No reward for water, weight, fasting, scanning or streak length |

Most one person can farm: 20 XP a day, inside a cap they could already fill by ticking habits.

---

## 10. Play Store (drafts for `PLAY_STORE_LAUNCH.md`, written in Batch 9)

- **Health apps declaration**: add "Nutrition and weight management" ("Tools for tracking dietary intake, planning
  meals, managing diets, and supporting weight loss or weight management goals.").
- **Store description** must carry: "not a medical device and does not diagnose, treat, cure, or prevent any medical
  condition", and a reminder to "consult a healthcare professional for medical advice, diagnosis, or treatment".
- **Camera**: "Apps that use device sensors (e.g., camera) for health functions must clearly state device
  compatibility information in the app description." Draft: "Barcode scanning needs a rear camera with autofocus."
- **Permissions**: `CAMERA` only, asked at first scan. No `READ_MEDIA_IMAGES` (system photo picker).
- **Data Safety**: no change for option (a). Food, weight and photos are processed on the device and never sent.
- **Claims to avoid**: any promised weight change, "burn", "detox", "doctor recommended", accuracy claims for the
  scanner. The scanner is described as "suggests dishes for you to confirm".
- **Privacy policy**: add the food diary, body measurements and camera use, all on-device.
- **Attribution**: §2.4 in the app; a short "Food data: USDA FoodData Central, UK CoFID (OGL v3.0), Open Food Facts
  (ODbL)" line in the description.

---

## 11. Build batches (Part B)

Each batch: `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint` green, `verifyRoborazziDebug`
with only the named new goldens, commit, short summary, STOP. Unclear plan → `docs/diet/QUESTIONS.md` and STOP.

| Batch | Contents | Stops early if |
|---|---|---|
| **1** | `GAME_DESIGN` §20 with approved numbers; `CatalogDatabase` entities (schema only, so the script has a target); `build-catalog.mjs`; `indian-foods.json`, `portions.json`, `aliases.json`; `foods.db`; `report.md`; licence files; `CatalogTest`. `build-packaged.mjs` and `packaged.db` only if Q3 allows. `foods.json` and `build-foods.mjs` removed, `FoodCatalog` reads the database so the app still works | CoFID lacks the expected dishes; size over budget; a source file's terms differ from §2.1 |
| **2** | Engine files of §8.1 with tests and vectors | any "not verified" source of §4.2 is still open |
| **3** | Room v32, migration and test, DAOs, repositories, FTS search use case | |
| **4** | Mocks, then Diary, Add food, Serving picker, recents, favourites, custom foods, saved meals, quick add, copy, notes, custom meals | |
| **5** | Goals and calculator, guard, water, weight and measurements, reports, nutrients, reminders, fasting (if Q7), recalculation card | |
| **6** | Recipes, planner | |
| **7** | Barcode: ZXing, CameraX, label form, pack lookup. Only after Q3 | 50-pack hit rate reported, not gated |
| **8** | Photo path behind the flag, fake classifier, LiteRT loader, debug eval screen, `SCANNER_TEST.md`. No model | |
| **9** | Copy and guard audit, Play docs, `PHONE_TEST.md`, review pass (no reward wiring: Q6 = no XP) | |

---

## 12. Draft for `GAME_DESIGN.md` §20 (written on approval)

> ## 20. Diet v2 (PROPOSED 2026-11-10)
> **20.1 Goals**: DG1 to DG11 (plan §4.3, §4.4). §13.1 stays the base.
> **20.2 Guard**: plan §4.5.
> **20.3 Catalog**: tiers, the range rule 15 %, Atwater tolerances, portion volumes (plan §3).
> **20.4 Trackers**: water, fasting, reminders, planner (plan §8.3). They pay nothing.
> **20.5 Reward**: DX1 to DX7.
> **20.6 Scanner**: thresholds of plan §5.5; score floor 0.15.

Test vectors (numbering continues from 197). "Man" = male, 25 y, 175 cm, 80 kg, moderate: BMR 1,774, TDEE 2,749
(vector 46). "Woman" = female, 30 y, 160 cm, 60 kg, sedentary: BMR 1,289, TDEE 1,547 (vector 47).

| # | Input | Expected |
|---|---|---|
| 198 | Man, lose steady (−500), Balanced | 2,249 kcal · protein 160 g · fat 62.5 g · carbs 261.7 g |
| 199 | Man, lose faster (−750) | cap = 25 % of 2,749 = 687 → **2,062 kcal** |
| 200 | Man, gain faster (+500) | 3,249 kcal |
| 201 | Man, maintain, High protein | protein 176 g · fat 76.4 g · carbs 339.4 g |
| 202 | Woman, lose gentle (−250) | 1,297 kcal |
| 203 | Woman, lose faster (−750) | cap 386 → 1,161 → floor **1,200** |
| 204 | Woman types a 1,000 kcal override | refused; field stays at 1,200 |
| 205 | Born 2008, game date in 2026 (difference 18) / born 2007 (19) | targets hidden / shown |
| 206 | 170 cm at 53.4 kg (BMI 18.48) / 53.5 kg (BMI 18.51) | hidden / shown |
| 207 | Plan weight 80.0; weigh-ins over 7 days 79.2, 79.0, 78.8 (mean 79.0) / 79.4, 79.1, 78.9 (mean 79.13) | prompt / no prompt |
| 208 | Two weigh-ins in 7 days | no trend, no prompt |
| 209 | Recipe: 200 g at 130 kcal/100 g + 50 g at 900 kcal/100 g, 4 servings, cooked 500 g | total 710 · 177.5 per serving · 142 per 100 g |
| 210 | Recipe 209 scaled × 1.5 | 6 servings, total 1,065, still 177.5 per serving |
| 211 | Food: protein 10, carbs 20, fat 5, label 165 kcal / label 210 kcal | estimate 165: pass / off by 45 (27 %): warning, error for our own rows |
| 212 | 2 katori at 150 g each of a food with 110 kcal/100 g | 300 g · 330 kcal |
| 213 | Label per serving of 30 g: 150 kcal, salt 0.5 g | per 100 g: 500 kcal, sodium 667 mg |
| 214 | "chapathi", "chappati", "chapatti", "Chapati" | one search key |
| 215 | UPC-A `036000291452` / EAN-13 `8901030865278` with last digit changed | `0036000291452` / rejected |
| 216 | Entries in breakfast only / breakfast and lunch / the same day again | 0 XP / 20 XP VIT / nothing inserted |
| 217 | Second meal logged with 590 self-report XP already earned today / with 600 | 10 XP / 0 XP |
| 218 | Second meal logged for yesterday; or with targets hidden | 0 XP |
| 219 | Day: 1,000 kcal from foods with an iron value, 500 kcal from foods without | iron coverage 67 % → "Partial data" |
| 220 | Recipe row with oil 5 g to 15 g giving 190 and 280 kcal per 100 g | range shown (38 %), logs 235 |

(Vector 215's sample codes are checked for valid check digits in Batch 2 before they go into GD.)

---

## 13. Every PROPOSED number in one place

Goals: pace −250 / −500 / −750 and +300 / +500 · rate cap 25 % of TDEE · surplus cap +500 · High protein 2.2 g/kg ·
Lower carb fat 35 % · custom split bounds 10-35 / 20-65 / 20-40 · protein per kg 0.8 to 2.2 · trend = 7 days, at
least 3 weigh-ins · prompt at 1.0 kg · dismiss for 7 days · guard: year difference > 18, BMI ≥ 18.5.
Catalog: 300 Indian foods and the split in §2.3 · katori 150 ml, cup 240, glass 250, tbsp 15, tsp 5 · sample piece
weights in §3.3 · range shown at 15 % · Atwater warn max(20 kcal, 20 %), error max(10 kcal, 10 %) · duplicate at 2 % ·
typo fallback under 5 results · size: foods 12 MB on disk / 6 MB download, pack 3 MB, model 6 MB · 3 new nutrients.
Surface: 8 meals · 4 reminders · planner 14 days ahead · micro coverage 80 %.
Trackers: water 2,000 / 2,500 ml, range 500 to 6,000, glass 250 · fasting 12 / 14 / 16 h.
Reward: 20 XP VIT · 2 meals · counts in the 600 cap.
Scanner: score floor 0.15 · go/no-go 70 % in-set, 55 % overall, median 300 ms, max 1 s, 30 % false confidence · 200 photos.
Barcode: 50-pack check (reported, not a gate).

---

## 14. Decisions you must make

| # | Question | Options | Recommended |
|---|---|---|---|
| **Q1** | Branch base. `main` lacks the gym redesign, focus protection and schema v31 | (a) keep `feature/diet-v2` on `feature/focus-protection` · (b) merge the older branches to `main` first, then rebase | **(a)**; merge order is your call later |
| **Q2** | You asked for FTS5 | (a) FTS4: built into Room and every phone, ranking in Kotlin · (b) FTS5 with a bundled SQLite, about 1 MB per ABI | **(a)** |
| **Q3** | Barcode lookup | (a) offline pack · (b) opt-in online, adds `INTERNET` to the Play build · (c) both | **(a)**. Nothing about `INTERNET` is planned until you say otherwise |
| **Q4** | The ODbL needs the pack and its build script offered publicly | (a) a small public GitHub repo holding only `build-packaged.mjs` and the pack · (b) a public download link you host elsewhere · (c) no pack, manual label entry only | **(a)** |
| **Q5** | IFCT and INDB | (a) send both emails, build without them · (b) do not ask, build without them · (c) wait for answers before Batch 1 | **(a)** |
| **Q6 (answered: none)** | "Log your day" reward, which reverses "Diet mints no XP" | (a) 20 XP VIT as §9 · (b) 10 XP · (c) no XP, Diet stays a pure tracker | **(a)**; (c) is the safest and costs nothing to choose |
| **Q7** | Fasting timer. The repo deliberately has no fasting rule in Programs or AI Paths | (a) v1, capped at 16 h, hidden by the guard · (b) later · (c) skip | **(a)**, with (b) if you would rather see the rest first |
| **Q8** | Who checks the 300 Indian recipe rows | (a) the build drafts all 300, the script validates, you spot-check a random 30 and the piece weights · (b) you review all 300 · (c) start with 100 | **(a)** |
| **Q9** | Photo model. No cleared dataset exists | (a) Batch 8 builds the path with the flag off; you collect photos over time; training is a later, separate task on a machine with Python · (b) drop the photo scanner · (c) train on a non-commercial dataset for your own phone only, never shipped | **(a)** |
| **Q10** | Sources that could not be read today (§4.2) | (a) Batch 2 reads them first-hand and stops if one fails · (b) you confirm them yourself · (c) drop the numbers that depend on them | **(a)** |
| **Q11** | Under-18 test with only a birth year | (a) hide when the year difference is 18 or less (some 18-year-olds are hidden for part of a year) · (b) hide only at 17 or less (some 17-year-olds get targets) · (c) ask for a full birth date | **(a)** |
| **Q12** | Exercise calories | (a) show Health Connect active calories, never add them to the budget · (b) also offer "add to budget" | **(a)** |
| **Q13** | Reference values on the Nutrients screen | (a) FDA Daily Values now, ICMR-NIN later if readable and permitted · (b) wait for ICMR-NIN | **(a)** |

## 15. Open questions (no recommendation without your input)

1. Do you want the diary entry to keep the photo? The plan says no (nothing is stored). Keeping it needs storage,
   backup rules and a delete path.
2. Is a public GitHub repo under your name acceptable for Q4? It would show that AWAKEN uses Open Food Facts.
3. Should "veg / egg / non-veg" be a search filter people can leave on, or only a mark on each row?
4. Hindi: the plan covers searching in Devanagari and transliteration. The app's own text stays English. Confirm.
5. The app accepts ages 13 to 17 today. With the guard they get a diary with no targets. Should Diet setup be closed
   to them completely instead?


---

## Part B status

**Batch 1 (2026-11-10), taken on the plan's recommended answers ("continue").** Done: `tools/catalog/build-catalog.mjs`, `xlsx.mjs`, `foods.db` (16,161 foods, 10.9 MB), `CatalogDatabase` + `FoodCatalog` over it, `FoodSearch.key` in `:engine`, zinc / B12 / folate, licence and attribution files, GD section 20, ENGINE_DECISIONS 179-185, PRD F53, `CatalogTest`. Deviations from the plan above:

- **Foundation Foods dropped**: the 2026-10-29 CSV has no food table. SR Legacy + FNDDS + CoFID cover the same ground.
- **One file instead of three** for the Indian data: aliases and serving sizes sit beside each food in `indian-foods*.json`, not in separate `portions.json` / `aliases.json`.
- **213 Indian entries, not 300** (91 recipes, 122 overlays on USDA/CoFID rows). CoFID's South Asian coverage meant fewer recipes were needed, but categories are still thin (see `report.md`). The rest is a follow-up, not a blocker for Batch 2.
- **`packaged.db` (barcode pack) is not built here.** It is only needed in Batch 7, and the ODbL offer (Q4) needs the public repo first.
- **Atwater check counts fibre at 2 kcal/g** (ENGINE_DECISIONS 182) and **CoFID carbs include fibre** (181).
- **Q6 answered: no XP on food** (2026-11-10). GD 20.5 says so; the reward design (section 9) and `DietReward` are dropped.
- Not yet verified (plan 4.2): Mifflin-St Jeor citation, activity multipliers, protein and macro ranges, 7,700 kcal/kg, water figures. Batch 2 reads them first-hand before the numbers go into code.

**Batch 2 (2026-11-10), changes from sections 4.3 and 12 after reading the sources first-hand.** Verified: Mifflin-St Jeor (Europe PMC abstract), ISSN protein 1.4 to 2.0 g/kg, Harvard floor and 500 to 1,000 kcal range, CDC 1 to 2 lb a week. Not verifiable today (blocked or not on the page): AMDR ranges, 7,700 kcal/kg, a safe gain rate, EFSA water, the activity multipliers' origin. So dropped: the kg-a-week label (DG4), the +500 gain pace, the lower-carb preset, bounds on custom splits; High protein is 2.0 g/kg, not 2.2; protein per kg is 0.8 to 2.0 (0.8 unverified). Water, fasting, nutrient coverage and the report stay for Batch 5. New Daily Values for zinc, B12 and folate are added with the Nutrients screen in Batch 5 (adding them now would change the diary goldens).

**Batch 3 (2026-11-10).** Done: Room v32 (13 new tables, new columns on `custom_food`, `food_log`, `nutrition_profile`), `MIGRATION_31_32` with a data-preservation test, export and import wiring, `FoodLibraryDao` / `BodyLogDao`, `FoodLibrary`, `RecipeBook`, `FoodSearchUseCase` (ranked across catalog, own foods and recipes), `BodyLog`, `FoodSearch.rank` in `:engine`. Changes from the plan: the profile columns follow the Batch 2 calculator (`loss_pace`, `macro_preset`, `protein_per_kg`, custom split, no `kcal_delta`); fasting is stored with the 12/14/16 h limit only (rules in Batch 5); `food_log.meal` keeps its `Meal` type until Batch 4 (ENGINE_DECISIONS 189).

**Batch 4 (2026-11-11, commits 878a1641 mocks, 2d40243c, 9daf12b6).** Done: mocks in `docs/diet/mocks/`; diary ring, ranked search with Recent / Favourites / Meals tabs, quick add, custom food form (brand, barcode, per serving), saved meals, copy to today, day note, custom meals (removing one moves its rows to Snack), serving range and estimate note, favourite toggle. `food_log.meal` is now a slot id string (ENGINE_DECISIONS 194). Not built: recipes tab in Add food and the planner (Batch 6), active-calories line, rename of a custom meal, food details screen, a Compose test for each new sheet.

**Batch 5 (2026-11-11, commits fc751223, c310c56e, 0cce8cda).** Done: 3-step calculator with pace and macro presets, guard, recalculation card, water row with target and undo, weight and measurements sheet with 7-day trend, nutrients sheet with Partial data rule, weekly report, fasting timer, meal reminders and fasting notice (ENGINE_DECISIONS 195-197). Deviations: reminders are a Diet panel, not under Settings; reminder times are in DataStore and not in the backup; protein-per-kg and weight goals are not exposed in the UI; no Settings "Food data" screen or units setting yet.

**Batch 6 (2026-11-11, commit 0ab2aef6).** Done: recipe list and editor (ingredients through the diary search, cooked weight, live per-serving numbers, scaling), Recipes tab in Add food, 14-day meal planner with Log it (ENGINE_DECISIONS 198), `DietRecipePlanTest`. Finished 2026-11-11: previews for every recipe and plan state, and a planned amount can be changed until it is logged.

**Gap pass (2026-11-11).** Done: Settings, Diet (meal reminders moved there, weight unit kg or lb, Food data row), rename of any meal, other water amount, protein per kg in the calculator, Estimate and range on search rows, an all-nutrients list in the amount sheet, weight chart, active calories line, interaction tests for the new sheets, mocks for recipes, plan, nutrients, report, fasting, weight and Settings. Still open: meal reminder times are not in the backup file (DataStore, not Room); no height unit; no diary-row estimate flag (rows keep a snapshot without it); nothing run on a device.

**Second gap pass (2026-11-11).** Done: meal reminders moved into Room and the backup (schema 33, ENGINE_DECISIONS 200), Estimate and calorie range kept on diary rows, feet and inches (201). Still open: saved meal items and plan entries do not carry the estimate flag, so a row made from them shows no tag; nothing run on a device. Note: an earlier verification of the schema 33 commit ran Gradle from the wrong folder; it was re-run from the repo root and passes (tests, build, lint, screenshots).

**Batch 7 (2026-11-11).** Done: scanner (ZXing + CameraX in `ScanViewfinder`), the six scanner states, found card per 100 g and per serving, add as a scan, not found then label form with the code filled in, kJ and salt entry, lookup in own foods then the optional pack, camera permission in the manifest and privacy policy, tests and previews. **Not done: the packaged-food pack and its build script, and the 50-pack hit rate.** The pack needs Q4 (a public repo or link, because the ODbL asks for it); the hit rate needs real packs and a phone. Without a pack the scanner finds your own foods only.

**Batch 7 pack (2026-11-11, Q4 = a).** Done: `tools/packaged-pack` (script, tests, schema, report, README, licences), `packaged.db` in the app assets, the Open Food Facts notice and licence texts in the app, pack tests. **Before release: publish `tools/packaged-pack` as a public repository at the address named in `DATA_SOURCES.txt` (or change that address), and measure the real hit rate on about 50 packs from your kitchen (38 % of Indian listings have full macros, so expect misses).**
