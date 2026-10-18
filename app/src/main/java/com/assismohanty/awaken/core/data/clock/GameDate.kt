package com.assismohanty.awaken.core.data.clock

import com.assismohanty.awaken.engine.GameDay
import java.time.LocalDate

/** Current game-day for a hunter's `dayStartHour` (GD §2). */
fun Clock.gameDate(dayStartHour: Int): LocalDate = GameDay.of(now(), zone(), dayStartHour)
