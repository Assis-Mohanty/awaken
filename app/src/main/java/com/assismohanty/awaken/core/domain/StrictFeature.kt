package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.BuildConfig

/**
 * Whether the accessibility features (Reel Counter now, Strict+ later) are part of this build. Debug has them; the Play
 * release removes the accessibility service from its manifest and every entry opens the Coming soon page instead.
 * Turning them on for a release is this one flag plus the service entry in `reelcounter-android`.
 */
object StrictFeature {
    val enabled: Boolean get() = BuildConfig.STRICT_PLUS_ENABLED
}
