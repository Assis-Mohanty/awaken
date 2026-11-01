package com.assismohanty.awaken.reelcounter

import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ServiceConfigTest {

    private val config = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/xml/rc_accessibility_service.xml"))
        .documentElement

    private fun attr(name: String): String = config.getAttribute("android:$name")

    @Test
    fun onlySupportedPackagesAreWatched() {
        assertThat(attr("packageNames").split(",").toSet()).isEqualTo(ShortVideoApp.allPackages)
    }

    @Test
    fun onlyTheThreeEventTypesAreRequested() {
        assertThat(attr("accessibilityEventTypes").split("|").toSet())
            .containsExactly("typeViewScrolled", "typeWindowStateChanged", "typeWindowContentChanged")
    }

    @Test
    fun reportsViewIdsAndCanRetrieveContent() {
        assertThat(attr("accessibilityFlags").split("|")).contains("flagReportViewIds")
        assertThat(attr("canRetrieveWindowContent")).isEqualTo("true")
    }
}
