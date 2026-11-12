# Photo food scanner: training (Diet Batch 8, track A)

Trains the dish classifier the app loads from `app/src/main/assets/scanner/dishes.tflite` (+ `labels.json`). The app side is built
and switched off (`DIET_PHOTO_SCAN_ENABLED`); this folder is the offline training side. Nothing here runs on the phone.

**Only datasets in `ATTRIBUTION.csv` may be used** (CC BY or owner-owned). A model trained on a dataset with no licence or a
non-commercial one must never be committed or shipped (plan 5.3); keep any such experiment outside this repo.

## Where things live

Data and the Python environment are **outside the repo**: `C:\Users\<you>\awaken-training\` (`foodbd\`, `env\`, `work\`).
Never commit images, zips or weights from there. Only the finished, licence-clean `dishes.tflite` and `labels.json` go into
`app/src/main/assets/scanner/`, together with the credit lines (Settings, Food data; `assets/licenses/DATA_SOURCES.txt`).

## Steps

1. `python -m uv venv --python 3.12 env` then install PyTorch (CUDA 12.4 wheels) and `timm`.
2. Download FoodBD: `https://data.mendeley.com/public-api/zip/xh3ghf3jbg/download/2` into `foodbd\`.
3. `map_classes.py`: list FoodBD's classes and suggest catalog foods for each; a human edits the result into `labels.json`.
4. Train, export to int8 TFLite, copy to `assets/scanner/`.
5. Run the go / no-go test in `docs/diet/SCANNER_TEST.md` on 200 of your own meals. The flag flips only if it passes.
