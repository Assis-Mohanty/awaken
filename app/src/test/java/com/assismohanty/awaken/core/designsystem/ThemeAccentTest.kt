package com.assismohanty.awaken.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenColors
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.engine.model.Rank
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ENGINE_DECISIONS #45: a Vault theme re-tints rank accents only; crit/verified/ember and the crest keep their colours. */
@RunWith(AndroidJUnit4::class)
class ThemeAccentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val colors = AwakenColors()

    private fun accentsUnder(themeId: String): Pair<Color, Color> {
        var rankAccent = Color.Unspecified
        var crest = Color.Unspecified
        composeRule.setContent {
            AwakenTheme(themeId = themeId) {
                rankAccent = AwakenTheme.rankAccent(Rank.S)
                crest = AwakenTheme.colors.rank(Rank.S)
            }
        }
        composeRule.waitForIdle()
        return rankAccent to crest
    }

    @Test
    fun aThemeReplacesTheRankAccentButNotTheCrestColour() {
        val (accent, crest) = accentsUnder("theme_crimson_gate")
        assertThat(accent).isEqualTo(colors.themeCrimson)
        assertThat(crest).isEqualTo(colors.rankS)
    }

    @Test
    fun theDefaultThemeKeepsRankColours() {
        val (accent, _) = accentsUnder("default")
        assertThat(accent).isEqualTo(colors.rankS)
    }
}
