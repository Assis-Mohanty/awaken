package com.assismohanty.awaken.reelcounter.core.detect

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class DetectorConfigParserTest {

    private fun json(screens: String, app: String = "INSTAGRAM", version: Int = 1, extra: String = "") = """
        {"version": $version, $extra "apps": [{"app": "$app", "verified": false, "screens": [$screens]}]}
    """.trimIndent()

    private val okScreen = """{"containerIds": ["a.b:id/c"], "signal": "PAGER_PAGE"}"""

    private fun error(result: ParseResult): String = (result as ParseResult.Error).message

    @Test
    fun parsesShippedAsset() {
        val text = File("../reelcounter-android/src/main/assets/rc_detectors.json").readText()
        val result = DetectorConfigParser.parse(text)
        assertThat(result).isInstanceOf(ParseResult.Ok::class.java)
        val config = (result as ParseResult.Ok).config
        assertThat(config.apps.map { it.app }).containsExactlyElementsIn(ShortVideoApp.entries)
    }

    @Test
    fun unknownKeysIgnored() {
        val result = DetectorConfigParser.parse(json(okScreen, extra = "\"future\": 5,"))
        assertThat(result).isInstanceOf(ParseResult.Ok::class.java)
    }

    @Test
    fun unknownAppRejected() {
        assertThat(DetectorConfigParser.parse(json(okScreen, app = "MYSPACE"))).isInstanceOf(ParseResult.Error::class.java)
    }

    @Test
    fun duplicateAppRejected() {
        val text = """{"version":1,"apps":[
            {"app":"INSTAGRAM","verified":false,"screens":[$okScreen]},
            {"app":"INSTAGRAM","verified":false,"screens":[$okScreen]}]}"""
        assertThat(error(DetectorConfigParser.parse(text))).contains("duplicate")
    }

    @Test
    fun emptyContainerIdsRejected() {
        val screen = """{"containerIds": [], "signal": "PAGER_PAGE"}"""
        assertThat(error(DetectorConfigParser.parse(json(screen)))).contains("containerIds")
    }

    @Test
    fun invalidViewIdRejectedWithPath() {
        val screen = """{"containerIds": ["not-an-id"], "signal": "PAGER_PAGE"}"""
        assertThat(error(DetectorConfigParser.parse(json(screen)))).contains("apps[0].screens[0].containerIds[0]")
    }

    @Test
    fun coverageOutOfRangeRejected() {
        for (c in listOf("0.0", "1.1")) {
            val screen = """{"containerIds": ["a.b:id/c"], "signal": "PAGER_PAGE", "minCoverage": $c}"""
            assertThat(DetectorConfigParser.parse(json(screen))).isInstanceOf(ParseResult.Error::class.java)
        }
    }

    @Test
    fun wrongVersionRejected() {
        assertThat(DetectorConfigParser.parse(json(okScreen, version = 2))).isInstanceOf(ParseResult.Error::class.java)
    }

    @Test
    fun malformedJsonReturnsError() {
        assertThat(error(DetectorConfigParser.parse("{ nope"))).startsWith("malformed JSON")
    }

    @Test
    fun fallbackDefaultsToNotVerified() {
        val config = (DetectorConfigParser.parse(json(okScreen)) as ParseResult.Ok).config
        assertThat(config.apps.single().usesFallback()).isTrue()
        val verified = json(okScreen).replace("\"verified\": false", "\"verified\": true")
        val v = (DetectorConfigParser.parse(verified) as ParseResult.Ok).config
        assertThat(v.apps.single().usesFallback()).isFalse()
    }
}
