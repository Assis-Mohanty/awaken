package com.assismohanty.awaken.reelcounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp
import com.assismohanty.awaken.reelcounter.core.detect.ParseResult
import com.assismohanty.awaken.reelcounter.data.DetectorRepository
import com.assismohanty.awaken.reelcounter.data.DetectorSource
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DetectorRepositoryTest {
    private lateinit var context: Context
    private val override get() = File(context.filesDir, DetectorRepository.OVERRIDE_NAME)

    private val validOverride = """
        {"version":1,"apps":[{"app":"INSTAGRAM","verified":true,"screens":[
        {"containerIds":["com.instagram.android:id/real_pager"],"signal":"PAGER_PAGE"}]}]}
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        override.delete()
    }

    @After
    fun tearDown() {
        override.delete()
    }

    @Test
    fun usesTheBuiltInRulesByDefault() {
        val load = DetectorRepository(context).config.value
        assertThat(load.source).isEqualTo(DetectorSource.BUILT_IN)
        assertThat(load.error).isNull()
        assertThat(load.config.apps).hasSize(ShortVideoApp.entries.size)
    }

    @Test
    fun aValidOverrideOnDiskIsUsed() {
        override.writeText(validOverride)
        val load = DetectorRepository(context).config.value
        assertThat(load.source).isEqualTo(DetectorSource.CUSTOM)
        assertThat(load.config.apps.single().verified).isTrue()
    }

    @Test
    fun anInvalidOverrideFallsBackAndReportsTheError() {
        override.writeText("{ not json")
        val load = DetectorRepository(context).config.value
        assertThat(load.source).isEqualTo(DetectorSource.BUILT_IN)
        assertThat(load.error).startsWith("malformed JSON")
        assertThat(load.config.apps).hasSize(ShortVideoApp.entries.size)
    }

    @Test
    fun savingAValidOverrideActivatesIt() {
        val repo = DetectorRepository(context)
        assertThat(repo.saveOverride(validOverride)).isInstanceOf(ParseResult.Ok::class.java)
        assertThat(repo.config.value.source).isEqualTo(DetectorSource.CUSTOM)
        assertThat(override.exists()).isTrue()
    }

    @Test
    fun savingAnInvalidOverrideChangesNothing() {
        val repo = DetectorRepository(context)
        assertThat(repo.saveOverride("{}")).isInstanceOf(ParseResult.Error::class.java)
        assertThat(repo.config.value.source).isEqualTo(DetectorSource.BUILT_IN)
        assertThat(override.exists()).isFalse()
    }

    @Test
    fun resetDeletesTheOverride() {
        val repo = DetectorRepository(context)
        repo.saveOverride(validOverride)
        repo.resetOverride()
        assertThat(override.exists()).isFalse()
        assertThat(repo.config.value.source).isEqualTo(DetectorSource.BUILT_IN)
    }
}
