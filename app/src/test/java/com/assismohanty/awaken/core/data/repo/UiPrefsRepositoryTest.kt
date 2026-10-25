package com.assismohanty.awaken.core.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
