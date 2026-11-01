package com.assismohanty.awaken.core.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Which packages Focus Lock may cover (GD 8.2): real launcher apps only, never the phone's own windows. */
@RunWith(AndroidJUnit4::class)
class CoverableAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadowPm = shadowOf(context.packageManager)

    private fun install(packageName: String, flags: Int, launcherIcon: Boolean) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            this.flags = flags
        }
        shadowPm.installPackage(PackageInfo().apply { this.packageName = packageName; applicationInfo = app })
        if (launcherIcon) {
            val component = ComponentName(packageName, "$packageName.Main")
            shadowPm.addOrUpdateActivity(
                ActivityInfo().apply {
                    this.packageName = packageName
                    name = component.className
                    applicationInfo = app
                },
            )
            shadowPm.addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    private fun coverable() = CoverableApps(context)

    @Test
    fun anInstalledAppWithALauncherIconIsCoverable() {
        install("com.example.social", flags = 0, launcherIcon = true)
        assertThat(coverable().isCoverable("com.example.social")).isTrue()
    }

    @Test
    fun anUpdatedPreInstalledAppIsCoverable() {
        install("com.example.video", flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP, launcherIcon = true)
        assertThat(coverable().isCoverable("com.example.video")).isTrue()
    }

    @Test
    fun aPhoneMakerSystemAppIsNotCoverableEvenWithALauncherIcon() {
        install("com.vivo.manager", flags = ApplicationInfo.FLAG_SYSTEM, launcherIcon = true)
        assertThat(coverable().isCoverable("com.vivo.manager")).isFalse()
    }

    @Test
    fun aSystemWindowWithoutALauncherIconIsNotCoverable() {
        install("com.vivo.fingerprint", flags = ApplicationInfo.FLAG_SYSTEM, launcherIcon = false)
        assertThat(coverable().isCoverable("com.vivo.fingerprint")).isFalse()
    }

    @Test
    fun anInstalledAppWithoutALauncherIconIsNotCoverable() {
        install("com.example.service", flags = 0, launcherIcon = false)
        assertThat(coverable().isCoverable("com.example.service")).isFalse()
    }

    @Test
    fun anUnknownPackageIsLeftAlone() {
        assertThat(coverable().isCoverable("com.not.installed")).isFalse()
    }

    @Test
    fun invalidateForgetsWhatWasLearned() {
        val apps = coverable()
        assertThat(apps.isCoverable("com.example.later")).isFalse()
        install("com.example.later", flags = 0, launcherIcon = true)

        assertThat(apps.isCoverable("com.example.later")).isFalse()
        apps.invalidate()
        assertThat(apps.isCoverable("com.example.later")).isTrue()
    }
}
