package com.assismohanty.awaken.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.components.ChoiceChip
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Phone bug 2026-11-01: in the Gym picker's scrolling chip rows every label wrapped one letter per line ("A/n/y m/u/s…")
 * because the chip's weighted label got zero width under the LazyRow's unbounded constraints. A chip's label must lay
 * out on one line wherever the chip sits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceChipLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val labels = listOf("Any muscle", "Abdominals", "Medicine ball")

    private fun assertOneLine(label: String) {
        val size = composeRule.onNodeWithText(label).fetchSemanticsNode().size
        assertThat(size.width).isGreaterThan(size.height)
    }

    @Test
    fun chipsInAScrollingRowKeepTheirLabelOnOneLine() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(labels) { label -> ChoiceChip(text = label, selected = label == labels.first(), onClick = {}) }
                }
            }
        }
        labels.forEach(::assertOneLine)
    }

    @Test
    fun chipsInAWrappingRowStillFit() {
        composeRule.setContent {
            AwakenTheme(reduceMotion = true) {
                FlowRow(modifier = Modifier.width(320.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label -> ChoiceChip(text = label, selected = label == labels.first(), onClick = {}) }
                }
            }
        }
        labels.forEach(::assertOneLine)
    }
}
