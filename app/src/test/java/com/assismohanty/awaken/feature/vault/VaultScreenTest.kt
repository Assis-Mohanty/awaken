package com.assismohanty.awaken.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assismohanty.awaken.core.designsystem.theme.AwakenTheme
import com.assismohanty.awaken.core.domain.VaultItemState
import com.assismohanty.awaken.engine.BuyCheck
import com.assismohanty.awaken.engine.ShopKind
import com.assismohanty.awaken.engine.ShopRules
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DESIGN_SYSTEM §4.6: balances, cache, shop sections, owned/equipped states, the confirm sheet. */
@RunWith(AndroidJUnit4::class)
class VaultScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: VaultUiState, events: MutableList<VaultEvent> = mutableListOf()) {
        composeRule.setContent { AwakenTheme(reduceMotion = true) { VaultScreen(state = state, onEvent = { events += it }) } }
    }

    @Test
    fun showsBalancesAndEverySection() {
        show(previewVaultState())
        composeRule.onNodeWithText("1,240 gold").assertIsDisplayed()
        composeRule.onNodeWithText("Streak Shields 1 / 2").assertIsDisplayed()
        composeRule.onNodeWithText("Fragments 10 / 10").assertIsDisplayed()
        listOf("RELIC CACHE", "INSURANCE", "THEMES", "TITLE FRAMES", "HUNTER SIGILS").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun cacheOpensOnlyWithEnoughFragments() {
        val events = mutableListOf<VaultEvent>()
        show(previewVaultState(rich = false), events)
        composeRule.onNodeWithText("Open cache").performScrollTo().assertIsNotEnabled()

    }

    @Test
    fun anAffordableItemStartsAPurchase() {
        val events = mutableListOf<VaultEvent>()
        show(previewVaultState(), events)
        composeRule.onNodeWithText("Streak Shield").performScrollTo()
        composeRule.onAllNodesWithText("Buy")[0].assertIsEnabled().performClick()
        assertThat(events).containsExactly(VaultEvent.Buy("streak_shield"))
    }

    @Test
    fun anUnaffordableItemShowsWhatIsMissingAndCannotBeBought() {
        show(previewVaultState(rich = false))
        composeRule.onNodeWithText("180 more gold needed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sovereignGoldIsLockedBehindRankS() {
        show(previewVaultState())
        composeRule.onNodeWithText("Requires rank S").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ownedItemsOfferEquipAndTheEquippedOneOffersUnequip() {
        val events = mutableListOf<VaultEvent>()
        show(previewVaultState(), events)
        composeRule.onNodeWithText("Unequip").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Equip")[0].performScrollTo().performClick()
        assertThat(events.map { it::class }).containsExactly(VaultEvent.Equip::class, VaultEvent.Equip::class).inOrder()
        assertThat((events[0] as VaultEvent.Equip).itemId).isNull()
        assertThat((events[1] as VaultEvent.Equip).kind).isEqualTo(ShopKind.SIGIL)
    }

    @Test
    fun theConfirmSheetShowsThePriceAndTheResultingBalance() {
        val events = mutableListOf<VaultEvent>()
        val shield = ShopRules.byId.getValue("streak_shield")
        val state = previewVaultState().copy(pending = PendingPurchase(VaultItemState(shield, BuyCheck.Buyable, false, false), "purchase-1"))
        show(state, events)
        composeRule.onNodeWithText("Streak Shield for 300 gold. Balance after: 940 gold.").assertIsDisplayed()
        composeRule.onNodeWithText("Buy for 300 gold").performClick()
        assertThat(events).containsExactly(VaultEvent.ConfirmBuy)
    }
}
