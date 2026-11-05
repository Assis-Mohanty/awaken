package com.assismohanty.awaken.feature.onboarding

import com.assismohanty.awaken.engine.onboarding.IllustrationSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** docs/onboarding/ASSET_LIST.md: 46 images, named `onb_<step>_<variant>_<gender>`, every gendered image with a neutral twin. */
class OnboardingAssetsTest {

    private val names = OnboardingArt.all.keys

    @Test
    fun theTableHasEveryImageInTheAssetList() {
        assertThat(names).hasSize(46)
        val perStep = names.groupingBy { it.split("_")[1] }.eachCount()
        assertThat(perStep).containsExactly(
            "welcome", 1, "height", 3, "shape", 15, "improve", 7, "obstacle", 5, "fitness", 9, "equipment", 3, "permission", 3,
        )
    }

    @Test
    fun everyNameFollowsTheConvention() {
        val pattern = Regex("onb_[a-z]+_[a-z0-9]+_[mfn]")
        names.forEach { assertThat(pattern.matches(it)).isTrue() }
    }

    @Test
    fun everyGenderedImageHasANeutralTwin() {
        names.filter { it.endsWith("_m") || it.endsWith("_f") }.forEach { name ->
            assertThat(names).contains(name.dropLast(1) + "n")
        }
    }

    @Test
    fun everyDrawableIdResolvesToSomething() {
        OnboardingArt.all.values.forEach { assertThat(it).isNotEqualTo(0) }
    }

    @Test
    fun aMissingGenderFallsBackToTheNeutralImage() {
        val neutral = OnboardingArt.all.getValue("onb_improve_sleep_n")
        // Card art has only a neutral version, whichever set is chosen.
        assertThat(OnboardingArt.resolve("improve", "sleep", IllustrationSet.MALE)).isEqualTo(neutral)
        assertThat(OnboardingArt.resolve("improve", "sleep", IllustrationSet.FEMALE)).isEqualTo(neutral)
        assertThat(OnboardingArt.resolve("improve", "sleep", null)).isEqualTo(neutral)
    }

    @Test
    fun theChosenSetWinsWhereItHasArt() {
        assertThat(OnboardingArt.resolve("shape", "3", IllustrationSet.FEMALE)).isEqualTo(OnboardingArt.all.getValue("onb_shape_3_f"))
        assertThat(OnboardingArt.resolve("shape", "3", IllustrationSet.NEUTRAL)).isEqualTo(OnboardingArt.all.getValue("onb_shape_3_n"))
    }

    @Test
    fun anUnknownImageResolvesToZero() {
        assertThat(OnboardingArt.resolve("nothing", "here", IllustrationSet.MALE)).isEqualTo(0)
    }
}
