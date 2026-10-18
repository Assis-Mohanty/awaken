package com.assismohanty.awaken.engine

/**
 * Pure-Kotlin rules engine for AWAKEN. All game rules (GAME_DESIGN.md) live in this module.
 */
object EngineInfo {
    /** Bumped whenever a rule change alters outputs for existing inputs. */
    const val RULES_VERSION: Int = 1
}
