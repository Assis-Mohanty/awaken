# Declutter: open questions

Things that came up while building that the plan (`docs/DECLUTTER_PLAN.md`) did not settle. Nothing here blocks the build; each has the choice I made for now.

## Batch 1

1. **`completion.crit` column stays.** Dropping it means rebuilding the `completion` table, and it adds nothing. Old rows keep their value; new rows are always `false`. I can drop it in the Batch 2 migration (v27) with the identities column if you want it gone.
2. **Store entry point.** *Resolved in Batch 3:* the Store opens from the gold figure on Progress; the Today header icons are gone.
3. **Missed-day notice.** *Resolved in Batch 3:* the Comeback row on Today replaces the snackbar.
4. **Gate collapse notice.** The ember window is now a snackbar with the same reason text ("Background time … exceeded the … limit"). A session that ends early still gives no XP.
5. **Unused strings.** I pruned every string resource nothing references (about 330 including the `_clean` twins). Most belong to the removed features; a handful (`today_loading`, `level_word`, `stat_gain`, `quit_back`…) were already dead before this work.
6. **Dungeon quests in old data.** `QuestType.DUNGEON` rows from a dungeon that was running are not listed on Today any more, so they simply expire at the end of their day. No rows are deleted.
7. **Open `lint` baseline.** `./gradlew lint` is green; I did not compare the warning count against before.
8. **`PresentationMode.SYSTEM`.** *Resolved in Batch 2:* the whole System variant of the Lexicon is deleted (ENGINE_DECISIONS #156).
