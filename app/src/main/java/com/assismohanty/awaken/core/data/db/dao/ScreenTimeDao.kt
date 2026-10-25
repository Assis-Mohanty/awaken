package com.assismohanty.awaken.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.assismohanty.awaken.core.data.db.entity.AppRuleEntity
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
}
