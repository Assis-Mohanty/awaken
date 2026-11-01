package com.assismohanty.awaken.reelcounter.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class CoreIsAndroidFreeTest {
    @Test
    fun noAndroidImportsInCoreMain() {
        val root = File("src/main")
        assertThat(root.exists()).isTrue()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readLines().any { it.trim().startsWith("import android.") || it.trim().startsWith("import androidx.") } }
            .map { it.name }
            .toList()
        assertThat(offenders).isEmpty()
    }
}
