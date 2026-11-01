package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.domain.FocusLockStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether Focus Lock is active is set directly by a test. */
class FakeFocusLockStatus(var active: Boolean = false, var allowed: Set<String> = emptySet()) : FocusLockStatus {
    override fun isActive(): Boolean = active
    override val serviceConnected: StateFlow<Boolean> = MutableStateFlow(false)
    override fun alwaysAllowed(): Set<String> = allowed
}
