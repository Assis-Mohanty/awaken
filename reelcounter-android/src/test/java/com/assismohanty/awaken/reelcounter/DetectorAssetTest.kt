package com.assismohanty.awaken.reelcounter

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.DetectorConfigParser
import com.assismohanty.awaken.reelcounter.core.detect.ParseResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class DetectorAssetTest {

    private val result = DetectorConfigParser.parse(File("src/main/assets/rc_detectors.json").readText())

    @Test
    fun assetParses() {
        assertThat(result).isInstanceOf(ParseResult.Ok::class.java)
    }

    @Test
    fun coversAllFiveAppsAndEveryEntryIsMarkedUnverifiedUntilConfirmed() {
        val config = (result as ParseResult.Ok).config
        assertThat(config.apps.map { it.app }).containsExactlyElementsIn(ShortVideoApp.entries)
        config.apps.forEach {
            if (!it.verified) assertThat(it.note).contains("UNVERIFIED")
        }
    }
}
