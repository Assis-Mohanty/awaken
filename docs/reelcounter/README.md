# Reel Counter: build, run, tune

Reel Counter is part of the AWAKEN app. There is one Gradle build and one installable app.

```
./gradlew :reelcounter-core:test          # counting engine (fast)
./gradlew :reelcounter-android:testDebugUnitTest
./gradlew :app:assembleDebug              # AWAKEN, with Reel Counter inside
./gradlew :app:installDebug
```

## Turn it on (on the phone)

1. AWAKEN → Settings → Focus → **Reel counter** → switch on. Read the disclosure and tap **Continue**.
2. Tap **Open accessibility settings**, find **Reel counter**, switch it on. If Android says the setting is
   restricted: AWAKEN app info → ⋮ → **Allow restricted settings**, then try again.
3. Follow the battery steps (they show the guide for your phone maker).
4. Open a short-video screen in a supported app. A small counter bubble appears. Drag it to an edge, tap it for today's
   per-app counts, long-press it to hide it until your next session.

## Detector rules and the inspector

The rules that say which screens are short-video screens, and how to tell a new video from the same one, are
`reelcounter-android/src/main/assets/rc_detectors.json`. Format and validation: `PLAN.md` §5. Ids marked UNVERIFIED are
placeholders.

To fix an app, use a **debug build**:

1. Settings → Focus → Reel counter → **Detector inspector**, switch the chip on.
2. In the app, open its short-video screen and swipe 3 videos. Tap the **Dump** chip. Repeat on the app's home feed.
3. In the inspector, open each file and copy it. Dumps contain view ids, class names, bounds and the scroll events
   only, never text.
4. Update `rc_detectors.json` (set `"verified": true` and the real ids), add a test fixture, rebuild.

You can also paste edited rules into the inspector's **Custom detector rules** box to try them on the phone without
rebuilding. The release build has no inspector.

Without the inspector, `adb shell uiautomator dump /sdcard/rc.xml && adb pull /sdcard/rc.xml` shows the same ids. It
often fails while a video plays, so pause first, and strip text before sharing:
`sed -E 's/ (text|content-desc|hint)="[^"]*"//g'`.

## Day start

A game day starts at the hunter's day-start hour (default 04:00, AWAKEN settings). Counts and snoozes reset then.
