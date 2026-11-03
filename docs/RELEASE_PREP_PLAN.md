# Release prep plan (branch `fix/release-prep`)

Two batches. After each one, run the gate, then commit. Leave the unrelated working-tree changes alone and do not stage them:
`.idea/gradle.xml`, `proxy/eval/run.ts`, `awaken-kit-ai-paths-spec/`.

Setup: `git switch -c fix/release-prep` from `main`.

---

## Batch 1: Focus Lock keeps accessibility but stays out of the way of UPI and bank apps

**What the user decided (2026-11-03):** Focus Lock stays an `AccessibilityService`. The block logic doesn't change: same
`TYPE_WINDOW_STATE_CHANGED` events, same `TYPE_ACCESSIBILITY_OVERLAY` cover, same `FocusLockRules.decide()`. Do NOT
replace it with UsageStats polling or a foreground service, and do NOT add `SYSTEM_ALERT_WINDOW`.

**The problem:** many Indian UPI and bank apps refuse to run, or warn the user, when an enabled accessibility service
watches *every* app (no `packageNames`). Reel Counter's service is scoped to 6 packages and doesn't trigger this.

**The fix: scope the service and never cover payment apps.**
1. **Idle: scoped to ourselves.** In `res/xml/focus_lock_service.xml`, add `android:packageNames="com.assismohanty.awaken"`.
   With no gate running, the enabled service watches only AWAKEN.
2. **During a gate: scoped to everything except payment apps.** While a gate runs (lock on, not paused, before its end),
   the service calls `setServiceInfo()` with `packageNames` = an explicit list: launcher apps, home apps, the
   keyboard, SystemUI/Settings/dialer/SMS and AWAKEN, **minus every payment app**. Allowed apps stay in the list so a
   jump to the launcher or SystemUI still uncovers the cover. When the gate pauses or ends, it goes back to `{own package}`.
   - Guard: an empty `packageNames` means "all apps", so never send an empty array. Fall back to `{own package}`.
   - Re-compute when packages are added, removed or replaced. Register a runtime `BroadcastReceiver`
     (`ACTION_PACKAGE_ADDED/REMOVED/REPLACED`, data scheme `package`) inside the service, only while a gate runs.
   - Payment apps never send events, so they are never covered. Every other app is decided exactly as today.
3. **How payment apps are found:** all three sources are combined and cached in `AlwaysAllowed` (5-minute cache as today).
   - **Every UPI app on the phone:** `queryIntentActivities(Intent(ACTION_VIEW, Uri.parse("upi://pay")))`. This finds
     GPay, PhonePe, Paytm, BHIM and any bank app that handles UPI, with no hard-coded list. Add a `<queries>` entry:
     `<intent><action VIEW/><data android:scheme="upi"/></intent>`.
   - **The NFC default payment app:** `Settings.Secure.getString(cr, "nfc_payment_default_component")`, then
     `ComponentName.unflattenFromString`, then its package. Wrap it in `runCatching`; it's null on phones without NFC.
   - **A backup list in `:engine`:** `FocusLockRules.PAYMENT_APPS`, with only well-known ids:
     `com.google.android.apps.nbu.paisa.user`, `com.phonepe.app`, `net.one97.paytm`, `in.org.npci.upiapp`,
     `com.dreamplug.androidapp`, `com.sbi.lyp.onlinesbi`, `com.csam.icici.bank.imobile`, `com.snapwork.hdfc`,
     `com.axis.mobile`, `com.msf.kbank.mobile`. `neverBlocked()` also returns true for them, so `decide()` is a backstop.
4. **Engine (pure, tested):** `FocusLockRules.watchScope(candidates, payment, ownPackage): Set<String>` returns
   `candidates − payment − PAYMENT_APPS + ownPackage`, never empty. Add tests to `FocusLockRulesTest`: payment apps are
   excluded, the result is never empty, the own package is always in it, and `decide()` returns ALLOW for every
   `PAYMENT_APPS` id. Log the rule in `docs/ENGINE_DECISIONS.md` (next number) and add one line to GD §8.2 if that
   section lists the never-blocked apps.
5. **App wiring** (`core/lock`):
   - `AlwaysAllowed`: add a `paymentApps(): Set<String>` (UPI handlers ∪ NFC default) and a
     `watchCandidates(): Set<String>` (launcher apps ∪ home apps ∪ keyboard ∪ default dialer/SMS ∪ own ∪ `NEVER_BLOCK`).
     Payment apps are also added to `current()`.
   - `FocusLockService`: add `applyScope(snapshot)`, called from the `state.snapshot.collect {}` block and from the
     package receiver. It builds from `serviceInfo` (copy flags, eventTypes, feedbackType, notificationTimeout) and
     sets `packageNames`. Skip the call when the set is unchanged. Keep everything else (cover, tick, keyguard, Back,
     End & open) as it is.
   - Update the KDoc privacy notes in `FocusLockService`/`focus_lock_service.xml` to say it is scoped and excludes payment apps.
6. **User-facing text:** add one line to the Focus Lock setup/settings note, e.g. "Payment and UPI apps are never watched or
   covered.", in `strings_focuslock.xml` with its `_clean` twin. Regenerate `LexiconPairs`. Re-record only the changed
   Focus Lock goldens and name them in the commit.
7. **Docs:** update the Focus Lock accessibility declaration text in `docs/PLAY_STORE_LAUNCH.md`: it says the service is
   scoped to AWAKEN while idle and to non-payment apps during a user-started focus session.

**Gate:** `./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug lint :app:verifyRoborazziDebug`
**Commit:** `fix(focuslock): scope the accessibility service and never watch payment/UPI apps`

**Known limit, tell the user honestly:** a few banking apps refuse to run with *any* third-party accessibility
service enabled, scoped or not. No app-side change can fix those. The phone test finds out which ones.

---

## Batch 2: AI Paths compiled out of release (dormant, not just unconsented)

Approach: **by build type, not product flavor**. Debug = AI on, release = AI off. This keeps every gate task name
(`testDebugUnitTest`, `assembleRelease`, `processReleaseManifest`) and the golden paths unchanged.

1. **Gradle (`app/build.gradle.kts`):**
   - `buildConfigField("boolean", "AI_PATHS_ENABLED", "true")` in debug and `"false"` in release.
   - Define `ORACLE_BASE_URL`, `ORACLE_DEBUG_KEY` and `PLAY_CLOUD_PROJECT_NUMBER` in **debug only**; remove all three from
     release, so `local.properties` oracle values are never read into it.
   - Move `ktor-client-core`, `ktor-client-okhttp` and `play-integrity` to `debugImplementation`. `play-services-fitness`
     stays on `implementation` (Recording API phone steps, ED #110).
   - Add `sourceSets.getByName("debug").java.directories.add("src/aiOn/java")`, using the same style as the existing
     `assets.directories.add`.
2. **Move the network code out of `src/main`** into `app/src/aiOn/java/.../core/data/ai/`: `KtorPathOracleClient`,
   `AiModule` (OracleConfig + HttpClient + Integrity binding), `IntegrityTokenProvider`, `OracleConfig`, and anything
   else that imports Ktor or Play Integrity. `PathOracleClient`, `OracleOutcome`, the DTOs and `AiPrefsRepository` stay in main if
   they don't import Ktor (check). Release: `app/src/release/.../core/data/ai/OracleModule.kt` provides
   `DisabledPathOracleClient`, which throws `IllegalStateException("AI Paths is not in this build")` on every method.
3. **Manifest:** remove `INTERNET` from `src/main/AndroidManifest.xml` and add `app/src/debug/AndroidManifest.xml` with
   only `<uses-permission INTERNET/>`. As a belt-and-braces measure, add `app/src/release/AndroidManifest.xml` with
   `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>`. The current release blame
   report shows INTERNET comes only from `src/main`, but this guards against a library adding it later.
4. **Hide every Path entry point when off.** Add `core/domain/AiFeature` (an injectable wrapper around
   `BuildConfig.AI_PATHS_ENABLED`, fakeable in tests):
   - `AwakenNavHost`: don't register `PathsRoute`/`PathDetailRoute` composables. Status screen: no Paths entry (`onOpenPaths`).
   - Settings: no AI Paths window and no consent sheet. Onboarding: skip step 6 "Your first Path".
   - `QuestDetailScreen`: hide any Path link or report action. `PathRequestUseCases` and any worker that calls the Oracle
     return early when off. Grep for `PathOracleClient`, `CreatePathUseCase`, `PathsRoute`, `AiPrefs`, and workers
     scheduling Path weeks.
   - Templates (`assets/quests/templates.json`) keep working as the no-AI path (invariant 5).
5. **Tests:** existing Path tests run on debug, so they are unaffected. Add a Robolectric/unit test that the screens hide Path
   UI when `AiFeature` is off, plus a manifest test (like `HealthConnectManifestTest`) asserting `src/main` declares no
   INTERNET.
6. **Docs:** update CLAUDE.md invariant 8 and the Modules/`core/data/ai` note to say "INTERNET and the Oracle client exist
   only in debug builds (`src/aiOn`, `src/debug`); release has no network". Update `ARCHITECTURE.md` §3.8,
   `PLAY_STORE_LAUNCH.md` (Data safety: release sends nothing; remove Play Integrity / install-ID rows for this release),
   and `docs/BACKLOG.md` (re-enable AI Paths in release).

**Gate:** the Batch 1 gate, plus
`./gradlew :app:assembleRelease -Pawaken.allowUnsignedRelease=true` (or signed if `keystore.properties` exists), then
`grep INTERNET app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` (expect
no match). Also check the release `BuildConfig.java` (`app/build/generated/source/buildConfig/release/...`): it must have no
`ORACLE_*` or `PLAY_CLOUD_PROJECT_NUMBER` field.
**Commit:** `feat(release): compile AI Paths out of the release build (no INTERNET)`

---

## Final report to the user
1. The merged **release** manifest audit: every `<service>` (expect `FocusLockService` with the scoped xml, Reel Counter's
   service, `FocusTileService`, plus WorkManager/library services) and every `<uses-permission>`. INTERNET must be absent.
   Paste the `focus_lock_service.xml` contents too.
2. **Phone test checklist:**
   - Start a gate, open GPay/PhonePe/Paytm/your bank app, and pay ₹1 over UPI. The app opens with no accessibility warning and isn't covered.
   - With no gate running, open the same apps. No warning appears. (The service is scoped to AWAKEN only.)
   - During a gate, open a distracting app. The cover appears; [Back to focus] and [End gate & open] both work.
   - Covered app → Home → cover gone. Covered app → notification shade → UPI notification → UPI app opens uncovered.
   - Install a new app during a gate. It is covered when opened (package receiver re-scope).
   - Pause or end the gate. Nothing is covered.
   - Reel Counter still counts in its 6 apps.
   - Release build: no Paths tab, entry, settings window or onboarding step; airplane mode changes nothing.
   - Name any payment/bank app that still complains. That is the "any accessibility service" limit, not a scoping bug.
