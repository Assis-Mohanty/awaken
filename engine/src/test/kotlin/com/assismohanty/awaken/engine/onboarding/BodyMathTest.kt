package com.assismohanty.awaken.engine.onboarding

import com.assismohanty.awaken.engine.BedtimeRules
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class BodyMathTest {

    @Test
    fun `vector 135 snapping to a tick and reading it back never drifts, in every unit`() {
        for (unit in LengthUnit.entries) {
            val scale = BodyMath.heightScale(unit)
            for (tick in scale.first..scale.last) assertThat(scale.tickOf(scale.valueAt(tick))).isEqualTo(tick)
        }
        for (unit in MassUnit.entries) {
            val scale = BodyMath.weightScale(unit)
            for (tick in scale.first..scale.last) assertThat(scale.tickOf(scale.valueAt(tick))).isEqualTo(tick)
        }
    }

    @Test
    fun `scales stay inside the Diet ranges`() {
        for (unit in LengthUnit.entries) {
            val scale = BodyMath.heightScale(unit)
            assertThat(scale.valueAt(scale.first)).isAtLeast(BodyMath.HEIGHT_CM_MIN.toDouble())
            assertThat(scale.valueAt(scale.last)).isAtMost(BodyMath.HEIGHT_CM_MAX.toDouble())
        }
        for (unit in MassUnit.entries) {
            val scale = BodyMath.weightScale(unit)
            assertThat(scale.valueAt(scale.first)).isAtLeast(BodyMath.WEIGHT_KG_MIN)
            assertThat(scale.valueAt(scale.last)).isAtMost(BodyMath.WEIGHT_KG_MAX)
        }
    }

    @Test
    fun `every whole centimetre is a tick`() {
        val cm = BodyMath.heightScale(LengthUnit.CM)
        assertThat(cm.count).isEqualTo(111)
        assertThat(cm.valueAt(170)).isEqualTo(170.0)
    }

    @Test
    fun `vector 136 display conversions round to whole units`() {
        val inches = BodyMath.heightScale(LengthUnit.FEET_INCHES)
        val tick = inches.tickOf(175.0)
        assertThat(tick).isEqualTo(69)
        assertThat(BodyMath.feetAndInches(tick)).isEqualTo(5 to 9)
        assertThat(BodyMath.weightScale(MassUnit.LB).tickOf(80.0)).isEqualTo(176)
    }

    @Test
    fun `kilogram labels show a half only when needed`() {
        assertThat(BodyMath.weightTickLabel(MassUnit.KG, 140)).isEqualTo("70")
        assertThat(BodyMath.weightTickLabel(MassUnit.KG, 141)).isEqualTo("70.5")
        assertThat(BodyMath.weightTickLabel(MassUnit.LB, 154)).isEqualTo("154")
    }

    @Test
    fun `vector 137 BMI at 70 kg and 170 cm`() {
        val bmi = BodyMath.bmi(170.0, 70.0, age = 25)
        assertThat(bmi.value).isEqualTo(24.2)
        assertThat(bmi.band).isEqualTo(BmiBand.WITHIN)
    }

    @Test
    fun `vector 138 band edges follow the rounded value`() {
        // 100 cm tall so the BMI is weight / 1.0, which makes each value easy to set.
        assertThat(BodyMath.bmi(100.0, 18.4, 30).band).isEqualTo(BmiBand.BELOW)
        assertThat(BodyMath.bmi(100.0, 18.5, 30).band).isEqualTo(BmiBand.WITHIN)
        assertThat(BodyMath.bmi(100.0, 24.9, 30).band).isEqualTo(BmiBand.WITHIN)
        assertThat(BodyMath.bmi(100.0, 25.0, 30).band).isEqualTo(BmiBand.ABOVE)
        assertThat(BodyMath.bmi(100.0, 29.9, 30).band).isEqualTo(BmiBand.ABOVE)
        assertThat(BodyMath.bmi(100.0, 30.0, 30).band).isEqualTo(BmiBand.ABOVE)
    }

    @Test
    fun `vectors 139 and 140 the scale needs age 20 or more`() {
        assertThat(BodyMath.bmi(170.0, 70.0, age = 19).showScale).isFalse()
        assertThat(BodyMath.bmi(170.0, 70.0, age = null).showScale).isFalse()
        assertThat(BodyMath.bmi(170.0, 70.0, age = 20).showScale).isTrue()
    }

    @Test
    fun `the badge fraction stays on the scale`() {
        assertThat(BodyMath.bmi(100.0, 5.0, 30).scaleFraction).isEqualTo(0.0)
        assertThat(BodyMath.bmi(100.0, 90.0, 30).scaleFraction).isEqualTo(1.0)
        assertThat(BodyMath.bmi(100.0, 27.5, 30).scaleFraction).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `vector 141 units default by country`() {
        assertThat(BodyMath.defaultUnits("US")).isEqualTo(LengthUnit.FEET_INCHES to MassUnit.LB)
        assertThat(BodyMath.defaultUnits("us")).isEqualTo(LengthUnit.FEET_INCHES to MassUnit.LB)
        assertThat(BodyMath.defaultUnits("IN")).isEqualTo(LengthUnit.CM to MassUnit.KG)
        assertThat(BodyMath.defaultUnits(null)).isEqualTo(LengthUnit.CM to MassUnit.KG)
    }

    @Test
    fun `target floor is BMI 18_5 for the height`() {
        assertThat(BodyMath.minTargetKg(170.0)).isEqualTo(53.5)
    }

    @Test
    fun `vector 142 sleep wheels only offer values the phone-down night accepts`() {
        assertThat(SleepWheels.bedtimes.first()).isEqualTo(20 * 60)
        assertThat(SleepWheels.bedtimes.last()).isEqualTo(2 * 60)
        assertThat(SleepWheels.bedtimes).doesNotContain(19 * 60 + 45)
        assertThat(SleepWheels.bedtimes).doesNotContain(2 * 60 + 15)
        assertThat(SleepWheels.bedtimes.all(BedtimeRules::isValidBedtime)).isTrue()
        for (bed in SleepWheels.bedtimes) {
            val wakes = SleepWheels.wakeTimesFor(bed, dayStartHour = 4)
            assertThat(wakes).isNotEmpty()
            assertThat(wakes.all { BedtimeRules.isValidWindow(bed, it, 4) }).isTrue()
        }
        assertThat(SleepWheels.wakeTimesFor(BedtimeRules.DEFAULT_BEDTIME, 4)).contains(BedtimeRules.DEFAULT_WAKE)
    }

    @Test
    fun `a wake time that no longer fits moves to the nearest one that does`() {
        // 02:00 bedtime: a 04:00 wake is 2 h, too short; the nearest valid wake is 07:00 (5 h).
        assertThat(SleepWheels.fitWake(2 * 60, 4 * 60, 4)).isEqualTo(7 * 60)
        assertThat(SleepWheels.fitWake(BedtimeRules.DEFAULT_BEDTIME, BedtimeRules.DEFAULT_WAKE, 4)).isEqualTo(BedtimeRules.DEFAULT_WAKE)
    }

    @Test
    fun `answers survive the draft round trip`() {
        val answers = OnboardingAnswers(
            hunterId = "h", name = "Sam", illustrationSet = IllustrationSet.NEUTRAL, age = 30, heightCm = 172.72,
            weightKg = 70.5, heightUnit = LengthUnit.FEET_INCHES, weightUnit = MassUnit.LB, bodyStage = 3,
            improve = listOf(ImproveGoal.SLEEP, ImproveGoal.FOCUS), obstacles = setOf(Obstacle.POOR_SLEEP),
            screenTime = ScreenTimeBand.H2_TO_4, bedtimeMinute = 1380, wakeMinute = 390, fitness = FitnessLevel.BEGINNER,
            equipment = EquipmentChoice.DUMBBELLS, injuries = setOf(InjuryRegion.KNEE), trainingDays = setOf(1, 3, 5),
            trainingReminder = true, pace = Pace.BALANCED, target = WeightTarget(65.0, "2027-03-01"),
        )
        val json = Json.encodeToString(answers)
        assertThat(Json.decodeFromString<OnboardingAnswers>(json)).isEqualTo(answers)
    }
}
