package com.assismohanty.awaken.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ARCH §3.4: Health Connect must find the privacy screen, and only the three read permissions are declared. */
@RunWith(AndroidJUnit4::class)
class HealthConnectManifestTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theRationaleIntentOpensThePrivacyPolicy() {
        val intent = Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE").setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(intent, 0)
        assertThat(matches.map { it.activityInfo.name }).contains(PrivacyPolicyActivity::class.java.name)
    }

    @Test
    fun onlyTheThreeHealthReadPermissionsAreDeclared() {
        @Suppress("DEPRECATION")
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertThat(requested.filter { it.startsWith("android.permission.health.") }).containsExactly(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_SLEEP",
        )
        assertThat(requested.toList()).doesNotContain("android.permission.INTERNET")
    }

    @Test
    fun thePolicyNamesExactlyWhatIsRead() {
        composeRule.setContent { AwakenTheme { PrivacyPolicyScreen(onClose = {}) } }
        composeRule.onNodeWithText("Steps, Exercise sessions and Sleep sessions", substring = true).assertIsDisplayed()
    }
}
