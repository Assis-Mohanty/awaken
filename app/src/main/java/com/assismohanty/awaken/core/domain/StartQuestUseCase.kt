package com.assismohanty.awaken.core.domain

import com.assismohanty.awaken.core.data.clock.Clock
import com.assismohanty.awaken.core.data.db.dao.QuestDao
import javax.inject.Inject

/** "Begin" (GD §6.5): starts the minDurationSec speed bump. Returns false if the quest wasn't ACTIVE. */
class StartQuestUseCase @Inject constructor(
    private val questDao: QuestDao,
    private val clock: Clock,
) {
    suspend operator fun invoke(questInstanceId: String): Boolean = questDao.markStarted(questInstanceId, clock.now()) > 0
}
