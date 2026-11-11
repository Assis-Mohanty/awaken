# Packaged-food pack report

- Source file: en.openfoodfacts.org.products.csv.gz (Open Food Facts nightly export, file date 2026-11-11), SHA-256 `818d0e7478d1a4fac4a7c572c184733c21c02e20a70917a0d815634b109655b1`
- Rows in the export: 4,906,702
- Sold in India: 36,127
- Kept: **14,575**
- Pack size on disk: 1.59 MB

## Dropped (products sold in India)

- missing calories or a macro: 15,309
- no name: 4,616
- barcode not valid: 872
- calories do not match the macros: 650
- impossible value: 53
- macros add to more than 100 g: 27
- name too long: 25

Limits used: calories at most 900 per 100 g; each macro at most 100 g; protein + carbs + fat at most 105 g;
calories within max(20 kcal, 20 % of the label calories) of 4 x protein + 4 x (carbs - fibre) + 2 x fibre + 9 x fat.
