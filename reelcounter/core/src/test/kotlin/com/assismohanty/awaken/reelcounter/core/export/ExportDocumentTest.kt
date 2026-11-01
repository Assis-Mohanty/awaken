package com.assismohanty.awaken.reelcounter.core.export

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExportDocumentTest {
    private val doc = ExportDocument(
        exportedAt = "2026-11-01T18:30:00Z",
        zone = "Asia/Kolkata",
        dayStart = "04:00",
        limits = mapOf("INSTAGRAM" to 50),
        dailyTotals = listOf(
            ExportDailyTotal("2026-11-01", "INSTAGRAM", "com.instagram.android", 42, 2520, 1),
        ),
        events = listOf(
            ExportEvent("INSTAGRAM", "com.instagram.android", "2026-11-01T12:01:05.120Z", "2026-11-01", 12, 1762000000000),
        ),
    )

    @Test
    fun roundTrip() {
        assertThat(ExportJson.decode(ExportJson.encode(doc))).isEqualTo(doc)
    }

    @Test
    fun matchesDocumentedExampleShape() {
        val json = ExportJson.encode(doc)
        for (key in listOf("format", "version", "exportedAt", "zone", "dayStart", "limits", "dailyTotals", "events", "itemIndex", "sessionId")) {
            assertThat(json).contains("\"$key\"")
        }
        assertThat(json).contains("\"reelcounter-export\"")
    }

    @Test
    fun encodesDefaults() {
        val json = ExportJson.encode(doc)
        assertThat(json).contains("\"format\": \"reelcounter-export\"")
        assertThat(json).contains("\"version\": 1")
    }
}
