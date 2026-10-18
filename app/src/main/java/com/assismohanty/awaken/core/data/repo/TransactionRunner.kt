package com.assismohanty.awaken.core.data.repo

import androidx.room.withTransaction
import com.assismohanty.awaken.core.data.db.AwakenDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** One transaction per game action (invariant 3). Nested calls join the outer transaction. */
@Singleton
class TransactionRunner @Inject constructor(private val db: AwakenDatabase) {
    suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
