package com.assismohanty.awaken.core.lock

import com.assismohanty.awaken.engine.OemFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Focus Lock setup: which phone-maker settings pages are tried for each family (GD 8.2). */
class OemSettingsTargetsTest {
    @Test
    fun everyMakerWithAnAutostartPageHasCandidatesWithTheMakersOwnPackage() {
        val expectedPrefix = mapOf(
            OemFamily.XIAOMI to "com.miui",
            OemFamily.OPPO to "com.",
            OemFamily.ONEPLUS to "com.oneplus",
            OemFamily.VIVO to "com.",
            OemFamily.HUAWEI to "com.huawei",
            OemFamily.SAMSUNG to "com.samsung",
        )
        expectedPrefix.forEach { (family, prefix) ->
            val targets = OemSettingsTargets.autostart(family)
            assertThat(targets).isNotEmpty()
            targets.forEach {
                assertThat(it.packageName).startsWith(prefix)
                assertThat(it.className).startsWith("com.")
            }
        }
    }

    @Test
    fun otherPhonesHaveNoCandidatesSoTheCallerFallsBackToAppInfo() {
        assertThat(OemSettingsTargets.autostart(OemFamily.OTHER)).isEmpty()
    }

    @Test
    fun everyFamilyIsHandled() {
        OemFamily.entries.forEach { OemSettingsTargets.autostart(it) }
    }
}
