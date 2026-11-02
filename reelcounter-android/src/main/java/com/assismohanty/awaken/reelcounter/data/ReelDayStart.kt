package com.assismohanty.awaken.reelcounter.data

import kotlinx.coroutines.flow.Flow

/** The hour a game day starts (AWAKEN's `dayStartHour`). The host app supplies it, so Reel Counter never keeps its own. */
interface ReelDayStart {
    val dayStartHour: Flow<Int>
}
