package com.assismohanty.awaken.core.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.assismohanty.awaken.core.audio.ChimeChoice
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UiPrefsRepositoryTest {

    @Test
    fun themeModeDefaultsToSystemAndRoundTrips() = runTest {
        val dir = Files.createTempDirectory("ui-prefs").toFile()
        val repo = UiPrefsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "ui.preferences_pb") })

        assertThat(repo.prefs.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
        repo.setThemeMode(ThemeMode.LIGHT)
        assertThat(repo.prefs.first().themeMode).isEqualTo(ThemeMode.LIGHT)
        repo.setThemeMode(ThemeMode.DARK)
        assertThat(repo.prefs.first().themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun chimeDefaultsToSoftBellAndRoundTrips() = runTest {
        val dir = Files.createTempDirectory("ui-prefs").toFile()
        val repo = UiPrefsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "ui.preferences_pb") })

        assertThat(repo.prefs.first().chime).isEqualTo(ChimeChoice.SOFT_BELL)
        repo.setChime(ChimeChoice.OFF)
        assertThat(repo.prefs.first().chime).isEqualTo(ChimeChoice.OFF)
        repo.setChime(ChimeChoice.WOOD_BLOCK)
        assertThat(repo.prefs.first().chime).isEqualTo(ChimeChoice.WOOD_BLOCK)
    }
}
