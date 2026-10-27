package com.assismohanty.awaken.engine.model

import kotlinx.serialization.Serializable

/** A quest's time-of-day block (GD §14.5). No block (null) means Anytime. */
@Serializable
enum class TimeOfDay { MORNING, DAY, EVENING }
