package com.assismohanty.awaken.feature.diet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.data.catalog.Food
import com.assismohanty.awaken.core.data.catalog.FoodSource
import com.assismohanty.awaken.core.data.catalog.Portion
import com.assismohanty.awaken.core.designsystem.components.ScanFrame
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.body.Nutrient
import com.assismohanty.awaken.engine.body.Nutrients
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The scanner screen in each state and the label form, without a camera: what shows, and which event each tap sends. */
@RunWith(AndroidJUnit4::class)
class DietScannerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<DietEvent>()
    private var asked = false
    private var openedSettings = false

    private fun screen(scan: ScanState, access: CameraAccess = CameraAccess.READY) {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                BarcodeScannerScreen(scan, access, viewfinder = { ScanFrame(it) }, onAskPermission = { asked = true }, onOpenSettings = { openedSettings = true }, onEvent = { events += it })
            }
        }
    }

    private val food = Food(
        "pack:1", "Digestive biscuits (Acme)", FoodSource.CUSTOM,
        Nutrients(mapOf(Nutrient.KCAL to 480.0, Nutrient.PROTEIN to 7.0, Nutrient.CARBS to 64.0, Nutrient.FAT to 21.0)),
        listOf(Portion("1 serving", 30.0)),
    )

    @Test
    fun theCameraIsAskedForWithItsReason() {
        screen(ScanState.Scanning, CameraAccess.ASK)
        composeRule.onNodeWithText("The camera reads the barcode on the phone. Nothing is saved or sent.").assertExists()
        composeRule.onNodeWithText("Allow the camera").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun aDeniedCameraOffersTheSettings() {
        screen(ScanState.Scanning, CameraAccess.DENIED)
        composeRule.onNodeWithText("Open settings").performClick()
        assertThat(openedSettings).isTrue()
    }

    @Test
    fun aPhoneWithoutACameraIsToldToAddByHand() {
        screen(ScanState.Scanning, CameraAccess.NO_CAMERA)
        composeRule.onNodeWithText("This phone has no camera to scan with. You can add the food by hand.").assertExists()
    }

    @Test
    fun aFoundProductShowsPer100GramsAndPerServingAndAddsInOneTap() {
        screen(ScanState.Found(food, fromPack = true))
        composeRule.onNodeWithText("Digestive biscuits (Acme)").assertExists()
        composeRule.onNodeWithText("480 kcal · P 7.0 · C 64 · F 21").assertExists()
        composeRule.onNodeWithText("Per serving, 30 g").assertExists()
        composeRule.onNodeWithText("144 kcal · P 2.1 · C 19 · F 6.3").assertExists()
        composeRule.onNodeWithText("Add").performClick()
        assertThat(events.single()).isEqualTo(DietEvent.ScanAccept)
    }

    @Test
    fun aMissOffersTheLabelFormAndScanningAgain() {
        screen(ScanState.NotFound("5901234123457"))
        composeRule.onNodeWithText("Code 5901234123457").assertExists()
        composeRule.onNodeWithText("Add it from the label").performClick()
        composeRule.onNodeWithText("Scan again").performClick()
        assertThat(events).containsExactly(DietEvent.ScanCreate, DietEvent.ScanAgain).inOrder()
    }

    @Test
    fun theLabelFormTurnsKilojoulesAndSaltIntoKilocaloriesAndSodium() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FoodSearchContent(FoodSearch("LUNCH", showCustomForm = true, prefillBarcode = "5901234123457"), previewDietState().slots, emptyList()) { events += it }
                }
            }
        }
        composeRule.onNodeWithText("5901234123457").assertExists()
        composeRule.onNodeWithText("Food name").performScrollTo().performTextInput("Digestives")
        composeRule.onNodeWithText("kJ").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Calories per 100 grams, in kJ").performScrollTo().performTextInput("2000")
        composeRule.onNodeWithContentDescription("Salt (instead of sodium)").performScrollTo().performTextInput("1")
        composeRule.onNodeWithText("Create and add").performScrollTo().performClick()

        val draft = (events.single() as DietEvent.CreateCustomFood).draft
        assertThat(draft.barcode).isEqualTo("5901234123457")
        assertThat(draft.per100[Nutrient.KCAL]).isWithin(0.01).of(478.01)
        assertThat(draft.per100[Nutrient.SODIUM]).isWithin(0.01).of(400.0)
    }
}
