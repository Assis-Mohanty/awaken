package com.assismohanty.awaken.reelcounter.core.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportDocument(
    val format: String = "reelcounter-export",
    val version: Int = 1,
    val exportedAt: String,
    val zone: String,
    val dayStart: String,
    val limits: Map<String, Int>,
    val dailyTotals: List<ExportDailyTotal>,
    val events: List<ExportEvent>,
)

@Serializable
data class ExportDailyTotal(
    val date: String,
    val app: String,
    val pkg: String,
    val count: Int,
    val seconds: Long,
    val snoozes: Int,
)

@Serializable
data class ExportEvent(
    val app: String,
    val pkg: String,
    val ts: String,
    val day: String,
    val itemIndex: Int,
    val sessionId: Long,
)

object ExportJson {
    private val encoder = Json { prettyPrint = true; encodeDefaults = true }
    private val decoder = Json { ignoreUnknownKeys = true }

    fun encode(doc: ExportDocument): String = encoder.encodeToString(ExportDocument.serializer(), doc)
    fun decode(json: String): ExportDocument = decoder.decodeFromString(ExportDocument.serializer(), json)
}
