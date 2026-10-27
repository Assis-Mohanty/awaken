package com.assismohanty.awaken.engine.body

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** GD §13.2 vectors. */
class TrainingMathTest {

    @Test
    fun `volume counts done sets only`() {
        val sets = listOf(LoggedSet(60.0, 10), LoggedSet(70.0, 8), LoggedSet(80.0, 5, done = false))

        assertThat(TrainingMath.volume(sets)).isWithin(1e-9).of(1_160.0)
    }

    @Test
    fun `Epley estimate for 2 to 12 reps, the weight itself for a single`() {
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(100.0, 1))).isEqualTo(100.0)
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(100.0, 5))).isWithin(1e-9).of(100.0 * (1 + 5 / 30.0))
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(60.0, 12))).isWithin(1e-9).of(84.0)
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(60.0, 13))).isNull()
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(0.0, 10))).isNull()
        assertThat(TrainingMath.estimatedOneRepMax(LoggedSet(100.0, 5, done = false))).isNull()
    }

    @Test
    fun `the best set has the highest estimate, not the heaviest weight`() {
        val sets = listOf(LoggedSet(100.0, 1), LoggedSet(90.0, 6), LoggedSet(120.0, 20))

        assertThat(TrainingMath.bestSet(sets)).isEqualTo(LoggedSet(90.0, 6))
        assertThat(TrainingMath.bestSet(listOf(LoggedSet(0.0, 20)))).isNull()
    }

    @Test
    fun `muscle load gives primaries 1 and secondaries half a set`() {
        val bench = MuscleTargets(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS, Muscle.TRICEPS))
        val dip = MuscleTargets(setOf(Muscle.TRICEPS), setOf(Muscle.CHEST, Muscle.TRICEPS))
        val work = listOf(
            bench to LoggedSet(60.0, 10),
            bench to LoggedSet(60.0, 8),
            bench to LoggedSet(60.0, 6, done = false),
            dip to LoggedSet(0.0, 12),
        )

        assertThat(TrainingMath.muscleLoad(work)).containsExactly(
            Muscle.CHEST, 2.5,
            Muscle.SHOULDERS, 1.0,
            Muscle.TRICEPS, 2.0,
        )
    }

    @Test
    fun `muscle volume counts each done set in full for every muscle it works`() {
        val bench = MuscleTargets(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS, Muscle.TRICEPS))
        val dip = MuscleTargets(setOf(Muscle.TRICEPS), setOf(Muscle.CHEST, Muscle.TRICEPS))
        val work = listOf(
            bench to LoggedSet(60.0, 10),
            bench to LoggedSet(60.0, 8),
            bench to LoggedSet(60.0, 6, done = false),
            dip to LoggedSet(20.0, 12),
        )

        assertThat(TrainingMath.muscleVolume(work)).containsExactly(
            Muscle.CHEST, 1_320.0,
            Muscle.SHOULDERS, 1_080.0,
            Muscle.TRICEPS, 1_320.0,
        )
    }

    @Test
    fun `weekly bands are 0, up to 5, 10, 15, then more`() {
        assertThat(listOf(0.0, 0.5, 5.0, 5.5, 10.0, 15.0, 15.5, 30.0).map(TrainingMath::band))
            .containsExactly(0, 1, 1, 2, 2, 3, 4, 4).inOrder()
    }

    @Test
    fun `weeks start on Monday`() {
        assertThat(TrainingMath.weekStart(LocalDate.of(2026, 10, 27))).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(TrainingMath.weekStart(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(TrainingMath.weekStart(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 26))
    }

    @Test
    fun `catalog keys round-trip and unknown equipment is other`() {
        Muscle.entries.forEach { assertThat(Muscle.fromKey(it.key)).isEqualTo(it) }
        Equipment.entries.forEach { assertThat(Equipment.fromKey(it.key)).isEqualTo(it) }
        assertThat(Equipment.fromKey(null)).isEqualTo(Equipment.OTHER)
        assertThat(Equipment.fromKey("trampoline")).isEqualTo(Equipment.OTHER)
    }
}
