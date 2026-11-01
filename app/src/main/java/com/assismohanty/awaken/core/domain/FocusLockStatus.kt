package com.assismohanty.awaken.core.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * What the Focus Gate rules need to know about Focus Lock (GD 8.2), as an interface so `core/domain` does not depend on
 * the Android service and tests can fake it.
 */
interface FocusLockStatus {
    /** Focus Lock is switched on in AWAKEN and its Accessibility Service is connected right now. */
    fun isActive(): Boolean

    /** The Accessibility Service is connected (whether or not Focus Lock is switched on in AWAKEN). */
    val serviceConnected: StateFlow<Boolean>

    /** Packages Focus Lock never covers on this phone (AWAKEN, launcher, keyboard, dialer, ...). */
    fun alwaysAllowed(): Set<String>
}
