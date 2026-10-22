# AWAKEN — Release readiness report

Snapshot after Phase 17 (commit `67b1754`). Gates green: `:engine:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `lint`.

## Done
- Engine, app, Health Connect, notifications, widget, AI Paths (engine, proxy, Android) all committed.
- Adaptive icon + monochrome layer, splash, backup rules, signing guard (unsigned release fails unless `-Pawaken.allowUnsignedRelease=true`).
- Store kit: `privacy-policy/index.html`, `store/en-US/*`, `store/graphics/feature-graphic.svg`, debug Screenshot mode.
- In-app privacy text matches the hosted page (AI Paths + Reports sections added).

## Merged release manifest (permissions)
| Permission | Source |
|---|---|
| INTERNET | App (Path Oracle only; cleartext disabled) |
| POST_NOTIFICATIONS | App (reminders) |
| health.READ_STEPS / READ_EXERCISE / READ_SLEEP | App (Health Connect, on-device only) |
| WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE | WorkManager |
| `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX (internal) |

Release `BuildConfig`: `DEBUG=false`, `ORACLE_DEBUG_KEY=""`.

## Risks (fix before upload)
1. **Release `ORACLE_BASE_URL` is empty** in the current build: `oracle.productionUrl` is not set in `local.properties`. Deploy the proxy to production, set the key, rebuild. Until then AI Paths can't work in release.
2. **Privacy policy: filled in (22 October 2026).** Developer, city, email and the Workers AI statement (zero data retention for standard inference, no training; Cloudflare gives no retention period) are in both the hosted page and `strings_play.xml`. Still to do: publish the page and use that URL in Play and Health Connect.
3. **Play Integrity** only passes for Play-installed builds: the production proxy path is untestable until an Internal testing install.
4. **No keystore yet** (`keystore.properties` absent). Back it up; losing it blocks updates unless Play App Signing is enabled (recommended).
5. **`docs/QA_CHECKLIST.md` is written but not yet run.** Run it on a real phone and record the results.
6. **Screenshot mode is untested on a device**; the seeded data was compiled but not viewed.
7. `ReminderWorkTest.bothRemindersAreArmed` is a known ~25% flake (RetryRule applied); don't read a red run there as a regression.
8. `allowBackup="true"`: confirm the backup rules exclude what you don't want in Google backups, and that the policy's Backups paragraph stays true.
9. Proxy: eval set (`npm run eval`) drop rate against staging has not been recorded here.

## Dependency scan (releaseRuntimeClasspath)
Only Ktor 3.6.0 + OkHttp 5.5.0 (our Path Oracle client) and Play Integrity 1.6.0 with play-services basement/tasks. No Firebase, analytics, crash, ads or other networking SDKs.

## Not verified in this report
Process-death on every screen, timezone/clock-back behaviour on a device. Phase 16 asked for these; the commit log shows lint/accessibility/icon/signing work but I did not re-audit them.

## Cut candidates if time is short
- Baseline Profile module (optional per Phase 16).
- Promo video, widget screenshot.
- Anything in the 8-screenshot list beyond the first 6.

## Recommended order
1. Deploy proxy to production; set `oracle.productionUrl`, `oracle.cloudProjectNumber`.
2. Fill privacy placeholders; publish via GitHub Pages; use that URL in Play and Health Connect.
3. Create keystore (`keytool`, then `keystore.properties`); build `:app:bundleRelease`.
4. Run and tick `QA_CHECKLIST.md` on a real phone (debug build against staging).
5. Upload to Internal testing; create a Path from the Play-installed build.
6. Start the 12-tester closed test clock (PLAY_STORE_LAUNCH §1).
