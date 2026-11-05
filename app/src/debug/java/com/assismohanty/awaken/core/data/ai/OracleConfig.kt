package com.assismohanty.awaken.core.data.ai

/**
 * Where the Oracle lives and how debug builds authenticate. Values come from BuildConfig (local.properties), never from
 * source; [debugKey] is empty in release builds, so release always uses Play Integrity.
 */
data class OracleConfig(
    val baseUrl: String,
    val debugKey: String,
    val cloudProjectNumber: Long,
) {
    val isConfigured: Boolean get() = baseUrl.startsWith("https://")
}
