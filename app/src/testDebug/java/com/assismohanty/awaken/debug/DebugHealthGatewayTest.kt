package com.assismohanty.awaken.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.health.HealthConnectGateway
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Debug builds read the real Health Connect unless "Simulate 9,000 steps" was explicitly switched on. */
@RunWith(AndroidJUnit4::class)
class DebugHealthGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aFreshInstallUsesTheRealGatewayAndSaysSo() = runTest {
        val settings = DebugSettings(context, MainScope())
        val gateway = DebugHealthGateway(HealthConnectGateway(context, Dispatchers.Unconfined), settings)

        assertThat(settings.simulateSteps.value).isFalse()
        assertThat(gateway.isSimulated).isFalse()
    }
}
