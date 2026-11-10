# Open questions (Batch 4 and 5)

Nothing below has been checked on a phone. Everything ships inert until it is.

1. **Detector ids (Batch 4).** Every entry in `reelcounter-android/src/main/assets/rc_detectors.json` is `"verified": false`,
   so short-video blocking acts on nothing. Capture each app's short-video screen with Settings > Reel inspector, then set
   that app's `verified` to true with the real ids.
2. **YouTube channel-name id (Batch 5, PLAN 4.2).** `channelNameIds` is empty and `channelVerified` is false, so Study mode
   reads nothing and blocks nothing. Open a normal YouTube watch page with the inspector on. If the channel name has a
   stable view id (a resource id such as `com.google.android.youtube:id/...`), put it in `channelNameIds` and set
   `channelVerified` to true. **If it has no stable id, stop: Study mode is not possible without reading more of the
   screen, and the plan says not to.** Then delete Study mode (`ReelStudy.kt`, `StudyGuard`, `ChannelMatch`).
3. **Play listing.** Strict+ and Study mode need the accessibility declaration, an in-app disclosure video and a listing
   mention before they can be switched on for a Play build (PLAN section 5). Batch 7 covers the wording.
