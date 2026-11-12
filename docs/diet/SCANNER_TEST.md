# Photo food scanner: go / no-go test

The photo scanner ships **off** (`DIET_PHOTO_SCAN_ENABLED = false` in every build) and with **no model**. It is turned
on only when a model passes this test. The limits are GAME_DESIGN 20.6; the reasons are docs/diet/PLAN.md 5.3 to 5.5.

## 1. What you need

**Status 2026-11-12:** a first model (FoodBD, ENGINE_DECISIONS 207) is already in `app/src/debug/assets/scanner/`, so a debug build can run
this test today. It is not in the release assets: it moves there only after it passes. The steps below are for any new model.

- A model: `app/src/main/assets/scanner/dishes.tflite` (EfficientNet-Lite0, int8, 224 x 224 is the plan; any image
  classifier with one input `[1, h, w, 3]` and one output of label scores works).
- Its labels: `app/src/main/assets/scanner/labels.json`, the labels in the model's output order and each label's foods
  in our catalog (`assets/nutrition/foods.db` ids, best match first):

  ```json
  { "labels": ["dal", "rice_plain"], "foods": { "dal": ["in:dal_tadka", "in:dal_fry"], "rice_plain": ["sr:20045"] } }
  ```

  A label with no foods is never shown. The app refuses a `labels.json` whose label count differs from the model's outputs.
- **200 test photos** of real meals, taken on your phone as you eat them, in normal light. Never use these for
  training. For each, the right food id from our catalog (find it with the Diet search; the id is in the food's row in
  an exported backup, or ask for a lookup script).

Training data is a separate track (plan 5.3): no Indian food photo set is cleared for an app, so a model has to be
trained on your own photos plus openly licensed ones, each recorded in `tools/scanner/ATTRIBUTION.csv`.

## 2. Run it

1. Build and install the debug APK with the model in `assets/scanner/` (the flag can stay off: the test does not need it).
2. Copy the photos and `expected.csv` to the phone:

   ```
   adb shell mkdir -p /sdcard/Android/data/com.assismohanty.awaken/files/scanner-test
   adb push photos/. /sdcard/Android/data/com.assismohanty.awaken/files/scanner-test/
   ```

   `expected.csv`, one line per photo, a header line is allowed:

   ```
   file,food_id
   IMG_0001.jpg,in:dal_tadka
   IMG_0002.jpg,in:poha
   ```

3. In the app: Debug screen, **Photo scanner test (go / no-go)**. It shows the verdict and writes `results.csv`
   (file, expected, top-3 labels, scores, suggested foods, milliseconds) beside the photos.
4. Pull the results: `adb pull /sdcard/Android/data/com.assismohanty.awaken/files/scanner-test/results.csv`

## 3. The limits (GD 20.6): all must hold

| Check | Limit |
|---|---|
| Right food in the 3 suggestions, photos of dishes in the label set | at least 70 % |
| Right food in the 3 suggestions, all 200 photos | at least 55 % |
| Median time per photo | under 300 ms |
| Slowest photo | under 1 s |
| Photos of dishes **outside** the label set that still get suggestions (top score 0.15 or more) | at most 30 % |

A "hit" is the right food among the suggestions the hunter would actually see, after the 0.15 score floor.

The screen also prints a 95 % range for each hit rate. With 200 photos a measured 70 % means the true rate is roughly
63 to 76 %; plan 5.5 asks that a result inside that range of a limit be treated as a no-go. That is your call: the
verdict line uses the limits only.

## 4. If it passes

1. Set `DIET_PHOTO_SCAN_ENABLED` to `true` in `app/build.gradle.kts` (`defaultConfig`).
2. Check the APK size: LiteRT adds about 4.5 MB per install on a 64-bit phone (measured 2026-11-12), plus the model.
3. Add the meal photo to the privacy policy (`privacy_camera` in `strings_play.xml` and `privacy-policy/index.html`
   name only the barcode and the nutrition table today): looked at on the phone, from the camera or the system photo
   picker, never saved or sent. Data Safety does not change, since nothing leaves the phone.
4. Run `docs/diet/PHONE_TEST.md` section on the photo scanner.

If it fails, nothing changes: the flag stays off and the button stays hidden.

## 5. Scanner use on a phone

Every photo scan writes one `scan_feedback` row (foods shown, food chosen or none; never the photo). The test screen
prints how many scans ended on a suggested food. The rows stay on the phone and go only into your own backup file.
