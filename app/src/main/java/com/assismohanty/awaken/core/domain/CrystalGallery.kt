package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.FocusDao
import com.assismohanty.awaken.core.data.db.dao.GateRunDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.FocusSubjectEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunEntity
import com.assismohanty.awaken.core.data.db.entity.GateRunStatus
import com.assismohanty.awaken.engine.CrystalForm
import com.assismohanty.awaken.engine.CrystalOutcome
import com.assismohanty.awaken.engine.CrystalRules
import com.assismohanty.awaken.engine.GameDay
import com.assismohanty.awaken.engine.GateMode
import com.assismohanty.awaken.engine.GateRunMath
import com.assismohanty.awaken.engine.model.GateRank
import com.assismohanty.awaken.engine.model.Stat
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One finished run in the gallery. Derived from `gate_run`; nothing is stored for it (GD §16.5). */
data class CrystalEntry(
    val runId: String,
    val startedAt: Instant,
    val gameDate: LocalDate,
    val form: CrystalForm,
    val outcome: CrystalOutcome,
    /** Stage reached: 5 for a cleared run, where it stopped for a collapsed one. */
    val stage: Int,
    val tier: GateRank,
    val minutes: Int,
    val subjectId: String?,
    val subjectName: String?,
    val stat: Stat,
    val colourIndex: Int?,
)

internal fun GateRunEntity.crystalOutcome(): CrystalOutcome? = when (status) {
    GateRunStatus.CLEARED -> CrystalOutcome.CLEARED
    GateRunStatus.COLLAPSED -> CrystalOutcome.COLLAPSED
    GateRunStatus.LOGGED -> CrystalOutcome.LOGGED
    else -> null
}

/** The gallery: one crystal per finished run, newest first (GD §16.5). Read-only, no economy. */
class ObserveCrystalGalleryUseCase @Inject constructor(
    private val gateRunDao: GateRunDao,
    private val focusDao: FocusDao,
    private val hunterDao: HunterDao,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<CrystalEntry>> = combine(gateRunDao.observeFinished(), focusDao.observeAll()) { runs, subjects ->
        val dayStart = hunterDao.getProfile()?.dayStartHour ?: GameDay.DEFAULT_DAY_START_HOUR
        val names = subjects.associate { it.id to it.name }
        runs.mapNotNull { run -> run.toEntry(names, dayStart) }
    }

    private fun GateRunEntity.toEntry(names: Map<String, String>, dayStart: Int): CrystalEntry? {
        val outcome = crystalOutcome() ?: return null
        val planned = CrystalRules.plannedFor(mode, plannedSec)
        val elapsed = GateRunMath.elapsed(timing, endedAt ?: startedAt)
        val minutes = when (outcome) {
            CrystalOutcome.COLLAPSED -> elapsed.toMinutes().toInt()
            else -> plannedSec / SECONDS_PER_MINUTE
        }
        return CrystalEntry(
            runId = id,
            startedAt = startedAt,
            gameDate = GameDay.of(startedAt, clock.zone(), dayStart),
            form = CrystalRules.form(outcome),
            outcome = outcome,
            stage = if (outcome == CrystalOutcome.CLEARED) CrystalRules.MAX_STAGE else CrystalRules.stage(elapsed, planned),
            // A preset gate keeps its own rank; custom and stopwatch runs take the focus tier of their length.
            tier = if (mode == GateMode.PRESET) gateRank else CrystalRules.tier(plannedSec / SECONDS_PER_MINUTE),
            minutes = minutes,
            subjectId = subjectId,
            subjectName = subjectId?.let(names::get),
            stat = stat,
            colourIndex = CrystalRules.colourIndex(subjectId),
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
