package com.assismohanty.awaken.feature.today

import com.assismohanty.awaken.core.designsystem.components.TimeBlock
import com.assismohanty.awaken.engine.model.TimeOfDay

/** Engine block (null = Anytime) → the design system's timeline block. */
internal fun TimeOfDay?.toTimeBlock(): TimeBlock = when (this) {
    TimeOfDay.MORNING -> TimeBlock.MORNING
    TimeOfDay.DAY -> TimeBlock.DAY
    TimeOfDay.EVENING -> TimeBlock.EVENING
    null -> TimeBlock.ANYTIME
}
