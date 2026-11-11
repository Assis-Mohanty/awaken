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

/** ARCH §3.4: Health Connect must find the privacy screen, and only the six read permissions are declared. */
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
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_HEART_RATE",
        )
        // INTERNET is declared in every build for the opt-in Open Food Facts barcode lookup (CLAUDE.md invariant 8, ENGINE_DECISIONS 204);
        // the Path Oracle itself still only exists in builds that have AI Paths.
        assertThat(requested.toList()).contains("android.permission.INTERNET")
        // The Diet barcode scanner (docs/diet/PLAN.md section 6) is the one use of the camera: it is declared, and the policy says so.
        assertThat(requested.toList()).contains("android.permission.CAMERA")
        assertThat(requested.toList()).containsNoneOf(
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO",
        )
    }

    @Test
    fun thePolicyNamesExactlyWhatIsRead() {
        composeRule.setContent { AwakenTheme { PrivacyPolicyScreen(onClose = {}) } }
        composeRule.onNodeWithText("Steps, Exercise sessions and Sleep sessions", substring = true).assertIsDisplayed()
    }

    @Test
    fun thePolicySaysOnlyTheBarcodeNumberIsSentAndOnlyWhenAllowed() {
        composeRule.setContent { AwakenTheme { PrivacyPolicyScreen(onClose = {}) } }
        composeRule.onNodeWithText("sends that barcode number, and nothing else", substring = true).assertExists()
        composeRule.onNodeWithText("Online food lookup (optional)", substring = true).assertExists()
    }

    @Test
    fun thePolicyMentionsTheCameraAndSaysNothingIsSaved() {
        composeRule.setContent { AwakenTheme { PrivacyPolicyScreen(onClose = {}) } }
        composeRule.onNodeWithText("No picture or video is saved or sent anywhere", substring = true).assertExists()
    }
}
