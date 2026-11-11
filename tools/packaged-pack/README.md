# AWAKEN packaged-food pack

The barcode list that [AWAKEN](https://github.com/Assis-Mohanty/awaken) uses to recognise packaged foods offline: about a
product per barcode, with calories, protein, carbohydrate and fat per 100 g, and some extra label values. It is built from
[Open Food Facts](https://world.openfoodfacts.org) and offered here because the Open Database License asks for that.

This folder is meant to be published as its own public repository (suggested name `awaken-packaged-foods`), holding only
these files:

| File | What it is |
|---|---|
| `packaged.db` | The pack: a SQLite file with one table, `product`, in the schema of `schema.json`. |
| `build-packaged.mjs` | The script that makes `packaged.db` from the Open Food Facts export. |
| `build-packaged.test.mjs` | Tests for the script (`node --test`). |
| `schema.json` | The Room schema the app expects, so the file can be dropped into the app as an asset. |
| `report.md` | What went in and what was dropped, with the size and the SHA-256 of the export used. |
| `LICENSE` | The Open Database License (ODbL) 1.0, which covers `packaged.db` and this repository. |
| `DATABASE_CONTENTS_LICENSE.txt` | The Database Contents License 1.0, which covers the individual facts in the data. |

## What is in the pack

Products that Open Food Facts lists as sold in India (tagged so, or carrying an Indian `890` barcode, since many Indian products were added without the tag) and that have **all** of: a valid barcode (EAN-13, UPC-A or EAN-8, stored
as 13 digits), a name, and calories, protein, carbohydrate and fat per 100 g. A product is dropped when its numbers cannot
be right: calories above 900, a macro above 100 g, protein + carbohydrate + fat above 105 g, or calories more than
max(20 kcal, 20 %) away from what the macros add up to. Store-internal codes (starting with 2) are dropped. `report.md`
counts every reason.

Values come from product labels entered by volunteers and may be wrong. AWAKEN tells the user to check them against the pack.
Fix a wrong product at Open Food Facts, not here; the next build picks it up.

## Build it again

```
curl -L -O https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz
node build-packaged.mjs en.openfoodfacts.org.products.csv.gz
node --test
```

Needs Node 22.5 or newer (`node:sqlite`). The export is about 1.5 GB; the script streams it. The result depends on the date of
the export, which `report.md` records.

## Licence and attribution

Contains information from Open Food Facts, which is made available under the
[Open Database License](https://opendatacommons.org/licenses/odbl/1-0/) (ODbL). Individual contents are under the
[Database Contents License](https://opendatacommons.org/licenses/dbcl/1-0/). © Open Food Facts contributors.
openfoodfacts.org.

`packaged.db` is a derivative database of Open Food Facts and is shared under the ODbL, as is this repository. Product photos
are not included. This project is not affiliated with or endorsed by Open Food Facts.
