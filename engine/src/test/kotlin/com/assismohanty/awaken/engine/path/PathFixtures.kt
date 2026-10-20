package com.assismohanty.awaken.engine.path

import com.assismohanty.awaken.engine.model.Stat
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Shared Path test data. Fixture responses live in `src/test/resources/paths/` (GD §12, vectors 16–35). */
object PathFixtures {
    /** A Monday (ISO week 2026-W43). */
    val MONDAY: LocalDate = LocalDate.of(2026, 10, 19)

    val path = Path(
        id = "path-a",
        goal = "Reach 1900 on Codeforces",
        currentLevel = "Rated 1400, weak at graphs and DP",
        notes = "only evenings",
        primaryStat = Stat.INT,
        secondaryStat = Stat.WIL,
        activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        questsPerDay = 2,
        weeklyMinutes = 600,
        status = PathStatus.ACTIVE,
        createdAt = Instant.parse("2026-10-01T08:00:00Z"),
    )

    fun context(
        path: Path = this.path,
        ownedEquipment: Set<String> = emptySet(),
        injuryTags: Set<String> = emptySet(),
        recentTitles: List<String> = emptyList(),
    ) = ValidationContext(path, ownedEquipment, injuryTags, recentTitles)

    fun text(name: String): String = requireNotNull(javaClass.getResource("/paths/$name")) { "missing fixture $name" }.readText()

    /** Every fixture file name, so contract tests can't silently skip one. */
    fun all(): List<String> {
        val dir = File(requireNotNull(javaClass.getResource("/paths")).toURI())
        return dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".json") }.sorted()
    }

    fun week(name: String, context: ValidationContext = context()): WeekValidation =
        PathValidator.validateWeek(text(name), context)

    fun priced(name: String, context: ValidationContext = context()): List<PricedBlueprint> =
        PathPricing.price(week(name, context).kept)
}
