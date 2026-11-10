package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import kotlinx.serialization.Serializable

@Serializable
data class DetectorConfig(val version: Int, val apps: List<AppDetector>)

@Serializable
data class AppDetector(
    val app: ShortVideoApp,
    val verified: Boolean,
    /** null means "on while the ids are unverified". */
    val structuralFallback: Boolean? = null,
    val screens: List<ScreenRule>,
    val note: String = "",
) {
    fun usesFallback(): Boolean = structuralFallback ?: !verified
}

@Serializable
data class ScreenRule(
    val containerIds: List<String>,
    val excludeIds: List<String> = emptyList(),
    val signal: ItemSignal,
    /** Empty = any scroll source in the package. */
    val signalSourceIds: List<String> = emptyList(),
    val minCoverage: Double = 0.85,
    /** The container is one item (a recycled view per reel), so its own key tells items apart; its children may not be on screen. */
    val itemIsContainer: Boolean = false,
)

@Serializable
enum class ItemSignal { SCROLL_INDEX, PAGER_PAGE, CHILD_SWAP }
