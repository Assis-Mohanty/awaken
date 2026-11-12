# Phone test checklist: Diet v2 (Batches 1 to 8)

Run on a real phone with a **debug** build (`./gradlew :app:assembleDebug`). Multi-day checks use the Debug screen's
time travel; the game-day starts at 04:00. Do every section in **both themes** at least once, and the starred checks
with **TalkBack** on. Tick each box, or write what happened next to it. Numbers come from GAME_DESIGN 20 and
docs/diet/PLAN.md; if the phone disagrees with them, that is the finding.

Nothing has been run on a device yet: every check below is new.

## 1. Setup and targets (GD 20.2)

- [ ] First open of Diet shows the 3-step calculator; Save is enabled only when every field is valid.
- [ ] Each loss pace lowers the target; a pace bigger than 25 % of maintenance is capped and says so.
- [ ] A target never goes below the floor for the chosen sex, and the floor note offers a doctor or dietitian.
- [ ] Born this year minus 17 (or younger): logging works, but no calorie or macro targets appear anywhere (diary ring,
      report, reminders, fasting button).
- [ ] BMI under 18.5 from the height and weight entered: the same, targets hidden.
- [ ] The estimate disclaimer is shown with the targets.
- [ ] Weight unit lb and height in feet and inches (Settings, Diet): entry and display convert; the stored plan does not change.
- [ ] ★ Every calculator choice reads its label and state.

## 2. Diary (Batch 4)

- [ ] Search finds a food by its Indian name (try "chawal", "rajma", "poha") and ranks exact names first.
- [ ] Recent, Favourites, Meals and Recipes tabs fill as you use them.
- [ ] Amount sheet: household portions (katori, cup, roti) and grams both work; totals match grams times per-100 g.
- [ ] A food with a home-cooking spread shows the calorie range and Estimate, on the search row and on the diary row.
- [ ] Quick add, custom food (with brand, barcode, per serving), saved meal, copy a meal or a day to today, day note.
- [ ] Add a custom meal, rename any meal, remove a custom meal: its rows move to Snack.
- [ ] Edit and delete an entry.
- [ ] ★ Diary rows and the calorie ring read their numbers.

## 3. Trackers (Batch 5)

- [ ] Water: glass buttons, another amount, target, undo.
- [ ] Weight and measurements: log, delete, 7-day trend and chart. After enough weigh-ins that drift from the plan
      weight, the recalculation card offers new targets; Dismiss hides it for the day.
- [ ] Nutrients sheet: micronutrients with Daily Values; "Partial data" when some foods have no value.
- [ ] Weekly report: averages and days logged; targets shown only when visible.
- [ ] Fasting timer 12, 14 or 16 h: survives closing the app; ends with a notice.
- [ ] Meal reminders at the times set; they survive a reboot and an export plus import.
- [ ] Active calories line (Health Connect on): shown next to the budget, **not added** to it.

## 4. Recipes and meal plan (Batch 6)

- [ ] Recipe with ingredients, servings and cooked weight: per-serving numbers update live; scaling works.
- [ ] Log a recipe from the Recipes tab.
- [ ] Plan up to 14 days ahead; change a planned amount; Log it puts it in the diary once.

## 5. Barcode scanner (Batch 7)

- [ ] First Scan asks for the camera; deny, then the screen offers Settings; allow, then the viewfinder opens.
- [ ] A pack in the offline pack is found with no connection (airplane mode).
- [ ] A miss offers the online lookup **once**, off until allowed. Allowed: found products are saved to My foods, and
      the next scan finds them offline. Declined: never asked again on that scan, and nothing is sent.
- [ ] With the lookup off, no network request is made (check with the phone's data usage for AWAKEN: 0 B).
- [ ] A miss goes to "add it from the label" with the barcode filled in; kJ and salt convert.
- [ ] **Hit rate:** scan about 50 packs from your kitchen and fill in the table below.

| Packs scanned | Found offline | Found online | Missed | Wrong product or wrong numbers |
|---|---|---|---|---|
| | | | | |

## 6. Nutrition-label photo (ENGINE_DECISIONS 205)

- [ ] Photograph the nutrition table on 10 different packs (flat, curved, glossy, small print). For each, note which
      numbers were filled, which were marked "Check this one", and which were wrong.
- [ ] A photo with no table says so and shows what the reader saw; Try again returns to the camera.
- [ ] Nothing is saved: after a read, no new photo is in the gallery.

## 7. Photo food scanner (Batch 8): must stay hidden

- [ ] No "Photograph your meal" button anywhere in Add food (the flag is off and no model ships).
- [ ] Debug screen, **Photo scanner test**: says there is no model in this build. With a model, follow
      `docs/diet/SCANNER_TEST.md` instead.

## 8. Data and privacy

- [ ] Settings, Your data: an export contains the diet tables; a wipe then import brings the diary, weights, recipes,
      plans and reminders back.
- [ ] Settings, Food data: attribution for USDA, CoFID and Open Food Facts; the licences list includes ODbL, OGL,
      Tesseract and LiteRT.
- [ ] Privacy policy in the app names the Diet and body data, the camera and the online lookup, effective 12 November 2026.
- [ ] Diet pays no XP: logging a full day changes no XP, gold or streak.
