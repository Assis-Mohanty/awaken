package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StandingRulesTest {
    private val data = BenchmarkData.parse(
        requireNotNull(javaClass.classLoader.getResource("gym/benchmarks.json")).readText(),
    )
    private val benchId = data.exercises.entries.first { it.value == "bench" }.key
    private val pushupId = data.exercises.entries.first { it.value == "pushup" }.key

    private fun standing(id: String, e1rm: Double? = null, reps: Int? = null, sex: String? = "M", weight: Double? = 80.0, age: Int? = 25) =
        StandingRules.standing(data, id, e1rm, reps, null, sex, weight, age)

    @Test
    fun `an exercise without a benchmark has no standing`() {
        assertThat(standing("not-an-exercise", e1rm = 100.0)).isNull()
    }

    @Test
    fun `a lift needs body weight and sex`() {
        assertThat(standing(benchId, e1rm = 100.0, weight = null)).isEqualTo(Standing.NeedsProfile)
        assertThat(standing(benchId, e1rm = 100.0, sex = null)).isEqualTo(Standing.NeedsProfile)
    }

    @Test
    fun `a lift gets a percentile between 1 and 99 and a level`() {
        val light = standing(benchId, e1rm = 20.0) as Standing.Percentile
        val heavy = standing(benchId, e1rm = 300.0) as Standing.Percentile
        assertThat(light.percentile).isEqualTo(1.0)
        assertThat(light.level).isEqualTo(StrengthLevel.BEGINNER)
        assertThat(heavy.percentile).isEqualTo(99.0)
        assertThat(heavy.level).isEqualTo(StrengthLevel.ELITE)
        assertThat(heavy.curve).isNotEmpty()
    }

    @Test
    fun `no estimate means no standing for a lift`() {
        assertThat(standing(benchId, e1rm = null)).isNull()
    }

    @Test
    fun `push-ups read a level from the army table and flag a missing age`() {
        val aged = standing(pushupId, reps = 70) as Standing.Standard
        val unaged = standing(pushupId, reps = 70, age = null) as Standing.Standard
        assertThat(aged.points).isGreaterThan(0)
        assertThat(aged.ageNotSet).isFalse()
        assertThat(unaged.ageNotSet).isTrue()
        assertThat((standing(pushupId, reps = 0) as Standing.Standard).level).isEqualTo(StrengthLevel.BEGINNER)
    }

    @Test
    fun `the gauge marker rises with the percentile and stays inside its band`() {
        assertThat(StandingRules.gaugePosition(1.0)).isEqualTo(0f)
        assertThat(StandingRules.gaugePosition(20.0)).isWithin(1e-6f).of(0.2f)
        assertThat(StandingRules.gaugePosition(65.0)).isWithin(1e-6f).of(0.5f)
        assertThat(StandingRules.gaugePosition(99.0)).isGreaterThan(0.8f)
        assertThat(StandingRules.gaugePosition(99.0)).isAtMost(1f)
    }

    @Test
    fun `a pointer reads the lift and percentile along the curve and the marker sits at the hunter's own ratio`() {
        val own = standing(benchId, e1rm = 100.0) as Standing.Percentile
        val (kg, percentile) = StandingRules.atPointer(own, StandingRules.markerFraction(own))
        assertThat(kg).isWithin(0.01).of(100.0)
        assertThat(percentile).isWithin(0.01).of(own.percentile)
        assertThat(StandingRules.atPointer(own, 0f).second).isEqualTo(1.0)
        assertThat(StandingRules.atPointer(own, 1f).second).isEqualTo(99.0)
    }
}
