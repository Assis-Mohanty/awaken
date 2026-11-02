package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.AppRuleEntity
import com.assismohanty.awaken.core.data.db.entity.BedtimeRuleEntity
import kotlinx.coroutines.flow.Flow

/** Screen Discipline rules (GD §6.7). Versioned by `effective_from`; two edits on one day replace that day's version. */
@Dao
interface ScreenTimeDao {
    @Upsert
    suspend fun upsertRules(rules: List<AppRuleEntity>)

    @Query("SELECT * FROM app_rule")
    suspend fun allRules(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rule")
    fun observeRules(): Flow<List<AppRuleEntity>>

    @Query("SELECT DISTINCT package_name FROM app_rule")
    suspend fun ruledPackages(): List<String>

    // Phone-down nights (GD §16.12)

    @Upsert
    suspend fun upsertBedtime(rule: BedtimeRuleEntity)

    @Query("SELECT * FROM bedtime_rule ORDER BY effective_from")
    suspend fun allBedtime(): List<BedtimeRuleEntity>

    @Query("SELECT * FROM bedtime_rule ORDER BY effective_from")
    fun observeBedtime(): Flow<List<BedtimeRuleEntity>>

    /** Drops pending versions after [date] (turning the night off must not be undone by an earlier edit). */
    @Query("DELETE FROM bedtime_rule WHERE effective_from > :date")
    suspend fun deleteBedtimeAfter(date: java.time.LocalDate)
}
