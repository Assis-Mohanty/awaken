package com.assismohanty.awaken.reelcounter.core.stats

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import java.time.LocalDate

data class DayTotal(val day: LocalDate, val app: ShortVideoApp, val count: Int, val seconds: Long, val snoozes: Int = 0)

data class DayBar(val day: LocalDate, val total: Int, val perApp: Map<ShortVideoApp, Int>, val seconds: Long)
