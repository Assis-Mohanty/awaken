package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.BuildConfig

/**
 * Whether AI Paths is part of this build. Debug builds have it; the release build for Play compiles the network client
 * out, has no INTERNET permission, and hides every Path entry point (screens take a flag and default to shown).
 */
object AiFeature {
    val enabled: Boolean get() = BuildConfig.AI_PATHS_ENABLED
}
