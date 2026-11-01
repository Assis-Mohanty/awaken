package com.assismohanty.awaken.reelcounter

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class NoNetworkTest {

    @Test
    fun manifestsDoNotRequestInternet() {
        for (path in listOf("src/main/AndroidManifest.xml", "../demo/src/main/AndroidManifest.xml")) {
            val manifest = File(path)
            assertThat(manifest.exists()).isTrue()
            assertThat(manifest.readText()).doesNotContain("android.permission.INTERNET")
        }
    }

    @Test
    fun noSourceUsesANetworkLibrary() {
        val banned = listOf("import java.net.", "import okhttp3", "import io.ktor", "import android.net.http", "HttpURLConnection")
        val offenders = listOf("src/main/java", "src/debug/java", "src/release/java", "../demo/src/main/java")
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
            .filter { f -> f.readLines().any { line -> banned.any { b -> line.contains(b) } } }
            .map { it.name }
        assertThat(offenders).isEmpty()
    }
}
