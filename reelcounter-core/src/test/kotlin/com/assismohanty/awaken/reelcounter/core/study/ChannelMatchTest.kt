package com.assismohanty.awaken.reelcounter.core.study

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ChannelMatchTest {
    private val list = setOf("@3blue1brown", "Veritasium")

    @Test
    fun handleMatchesDisplayName() {
        assertThat(ChannelMatch.decide("3Blue1Brown", list)).isEqualTo(StudyDecision.ALLOW)
    }

    @Test
    fun caseSpacesAndPunctuationDoNotMatter() {
        assertThat(ChannelMatch.decide("  VERITASIUM! ", list)).isEqualTo(StudyDecision.ALLOW)
    }

    @Test
    fun fullWidthLettersAreFoldedByNfkc() {
        assertThat(ChannelMatch.decide("Ｖｅｒｉｔａｓｉｕｍ", list)).isEqualTo(StudyDecision.ALLOW)
    }

    @Test
    fun anOtherChannelIsBlocked() {
        assertThat(ChannelMatch.decide("Some Gaming Channel", list)).isEqualTo(StudyDecision.BLOCK)
    }

    @Test
    fun aPartialNameDoesNotMatch() {
        assertThat(ChannelMatch.decide("Veritasium Shorts", list)).isEqualTo(StudyDecision.BLOCK)
    }

    @Test
    fun unreadableOrEmptyFailsOpen() {
        assertThat(ChannelMatch.decide(null, list)).isEqualTo(StudyDecision.UNKNOWN)
        assertThat(ChannelMatch.decide("  !! ", list)).isEqualTo(StudyDecision.UNKNOWN)
    }

    @Test
    fun anEmptyListBlocksAnyReadableChannel() {
        assertThat(ChannelMatch.decide("Veritasium", emptySet())).isEqualTo(StudyDecision.BLOCK)
    }
}
