package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodCatalog
import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.BodyLogDao
import com.assismohanty.awaken.core.data.db.dao.DietDao
import com.assismohanty.awaken.core.data.db.dao.HunterDao
import com.assismohanty.awaken.core.data.db.entity.BodyMeasureLogEntity
import com.assismohanty.awaken.core.data.db.entity.DiaryNoteEntity
import com.assismohanty.awaken.core.data.db.entity.EntrySource
import com.assismohanty.awaken.core.data.db.entity.FastingSessionEntity
import com.assismohanty.awaken.core.data.db.entity.FoodLogEntity
import com.assismohanty.awaken.core.data.db.entity.MealPlanEntryEntity
import com.assismohanty.awaken.core.data.db.entity.MeasureKind
import com.assismohanty.awaken.core.data.db.entity.WaterLogEntity
import com.assismohanty.awaken.core.data.db.entity.WeightLogEntity
import com.assismohanty.awaken.core.data.repo.TransactionRunner
import com.assismohanty.awaken.engine.body.DietGuard
import com.assismohanty.awaken.engine.body.NutritionMath
import com.assismohanty.awaken.engine.body.WeighIn
import com.assismohanty.awaken.engine.body.WeightTrend
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The trend and the weight it was planned from, when the gap is big enough to offer new targets (GD 20.2). */
data class TrendOffer(val trendKg: Double, val planWeightKg: Double)

/**
 * Water, weight, measurements, fasting, day notes and the meal plan (Diet v2). Trackers: nothing here pays XP or touches the
 * ledger (ENGINE_DECISIONS #95, #186). Rules (the trend, the guard) come from `:engine`; this class stores and wires.
 */
@Singleton
class BodyLog @Inject constructor(
    private val dao: BodyLogDao,
    private val dietDao: DietDao,
    private val hunterDao: HunterDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    // Water

    fun observeWaterMl(date: LocalDate): Flow<Int> = dao.observeWaterMl(date)

    /** Adds [ml] of water; ignored when it is not between 1 and [MAX_WATER_ML] (input sanity, not a target). */
    suspend fun addWater(date: LocalDate, ml: Int): String? {
        if (ml !in 1..MAX_WATER_ML) return null
        val id = UUID.randomUUID().toString()
        dao.upsertWater(WaterLogEntity(id, date, ml, clock.now()))
        return id
    }

    suspend fun removeWater(id: String) = dao.deleteWater(id)

    // Weight

    fun observeWeights(since: LocalDate): Flow<List<WeightLogEntity>> = dao.observeWeights(since)

    /**
     * Logs a weigh-in. The newest weigh-in is also the profile weight (Gym and the Diet setup read it), written in the same
     * transaction. The plan weight does not move: only accepting new targets does that (GD 20.2). Returns the id, or null for a
     * weight outside the input range.
     */
    suspend fun logWeight(date: LocalDate, kg: Double, note: String? = null): String? {
        if (kg !in NutritionMath.WEIGHT_KG_RANGE) return null
        val id = UUID.randomUUID().toString()
        transactions.run {
            dao.upsertWeight(WeightLogEntity(id, date, kg, clock.now(), note?.trim()?.takeIf { it.isNotEmpty() }))
            syncProfileWeight()
        }
        return id
    }

    suspend fun deleteWeight(id: String) = transactions.run {
        dao.deleteWeight(id)
        syncProfileWeight()
    }

    private suspend fun syncProfileWeight() {
        val latest = dao.latestWeight() ?: return
        hunterDao.setWeightKg(latest.weightKg)
        dietDao.profile()?.let { dietDao.upsertProfile(it.copy(weightKg = latest.weightKg, updatedAt = clock.now())) }
    }

    /** Offers new targets when the 7-day trend is 1.0 kg or more from the plan weight (and targets are visible, and it was not dismissed lately). */
    suspend fun trendOffer(today: LocalDate): TrendOffer? {
        val profile = dietDao.profile() ?: return null
        if (!DietGuard.targetsVisible(profile.birthYear, today, profile.heightCm, profile.weightKg)) return null
        val trend = WeightTrend.trend(dao.weightsSince(today.minusDays(WeightTrend.WINDOW_DAYS)).map { WeighIn(it.gameDate, it.weightKg) }, today)
        val plan = profile.planWeightKg ?: profile.weightKg
        return if (WeightTrend.shouldPrompt(trend, plan, profile.trendDismissedOn, today)) TrendOffer(requireNotNull(trend), plan) else null
    }

    /** The hunter took the offer: today's trend becomes the weight the targets are planned from. */
    suspend fun acceptTrend(offer: TrendOffer) {
        dietDao.profile()?.let { dietDao.upsertProfile(it.copy(planWeightKg = offer.trendKg, trendDismissedOn = null, updatedAt = clock.now())) }
    }

    suspend fun dismissTrend(today: LocalDate) {
        dietDao.profile()?.let { dietDao.upsertProfile(it.copy(trendDismissedOn = today, updatedAt = clock.now())) }
    }

    // Measurements

    fun observeMeasures(kind: MeasureKind): Flow<List<BodyMeasureLogEntity>> = dao.observeMeasures(kind)

    suspend fun logMeasure(date: LocalDate, kind: MeasureKind, cm: Double): String? {
        if (cm !in MEASURE_CM_RANGE) return null
        val id = UUID.randomUUID().toString()
        dao.upsertMeasure(BodyMeasureLogEntity(id, date, kind, cm))
        return id
    }

    suspend fun deleteMeasure(id: String) = dao.deleteMeasure(id)

    // Fasting (the full rules arrive with the fasting screen)

    fun observeActiveFast(): Flow<FastingSessionEntity?> = dao.observeActiveFast()

    /** Starts a fast of 12, 14 or 16 hours (nothing longer, GD 20.4); an already running fast is returned unchanged. Null for any other length. */
    suspend fun startFast(targetMinutes: Int): FastingSessionEntity? = transactions.run {
        if (targetMinutes !in FASTING_TARGET_MINUTES) return@run null
        dao.activeFast() ?: FastingSessionEntity(UUID.randomUUID().toString(), clock.now(), targetMinutes, null).also { dao.upsertFast(it) }
    }

    suspend fun endFast() {
        dao.activeFast()?.let { dao.upsertFast(it.copy(endedAt = clock.now())) }
    }

    // Day note

    fun observeNote(date: LocalDate): Flow<DiaryNoteEntity?> = dao.observeNote(date)

    /** Saves the day's note; a blank note removes it. */
    suspend fun setNote(date: LocalDate, text: String) {
        val clean = text.trim().take(MAX_NOTE)
        if (clean.isEmpty()) dao.deleteNote(date) else dao.upsertNote(DiaryNoteEntity(date, clean))
    }

    // Meal plan

    fun observePlan(date: LocalDate): Flow<List<MealPlanEntryEntity>> = dao.observePlan(date)

    fun observePlanBetween(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntryEntity>> = dao.observePlanBetween(from, to)

    /** Plans [grams] of [food] for [meal] on [date]; null when [date] is not between [today] and [MAX_PLAN_DAYS] days ahead. */
    suspend fun plan(today: LocalDate, date: LocalDate, meal: String, food: Food, grams: Double, portionLabel: String?): String? {
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_PLAN_DAYS)) || grams <= 0.0) return null
        val id = UUID.randomUUID().toString()
        dao.upsertPlanEntry(
            MealPlanEntryEntity(id, date, meal, food.id, food.name, grams.coerceAtMost(DietTracker.MAX_GRAMS), portionLabel, FoodCatalog.nutrientsToJson(food.per100), logged = false),
        )
        return id
    }

    suspend fun removePlanned(id: String) = dao.deletePlanEntry(id)

    /** "Log it": copies a planned food into the diary on [date] and marks the plan done, in one transaction. */
    suspend fun logPlanned(id: String, date: LocalDate): Boolean = transactions.run {
        val entry = dao.planEntry(id) ?: return@run false
        dietDao.upsertLog(
            FoodLogEntity(
                id = UUID.randomUUID().toString(), gameDate = date, meal = entry.meal, foodId = entry.foodId, name = entry.name, grams = entry.grams,
                portionLabel = entry.portionLabel, nutrientsJson = entry.nutrientsJson, loggedAt = clock.now(), entrySource = EntrySource.PLAN,
            ),
        )
        dao.upsertPlanEntry(entry.copy(logged = true))
        true
    }

    companion object {
        // Input sanity, not game numbers.
        const val MAX_WATER_ML = 5_000
        val MEASURE_CM_RANGE = 10.0..300.0
        const val MAX_NOTE = 500
        const val MAX_PLAN_DAYS = 14L

        /** GD 20.4: fasts of 12, 14 or 16 hours, nothing longer. */
        val FASTING_TARGET_MINUTES = listOf(12 * 60, 14 * 60, 16 * 60)
    }
}
