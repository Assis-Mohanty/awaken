package com.assismohanty.awaken.architecture

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Assets are stored compressed inside the app, and `AssetManager.openFd` throws for a compressed asset on a real phone while it works
 * in tests, so code that uses it can ship broken without any test noticing (the nutrition-label reader did). Read assets as streams.
 */
class CompressedAssetsTest {
    @Test
    fun noMainCodeOpensAnAssetByFileDescriptor() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { line -> ".openFd(" in line && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") } }
            .map { it.relativeTo(root).path }.toList()
        assertWithMessage("openFd fails for compressed assets on a phone; use assets.open(): $offenders").that(offenders).isEmpty()
    }
}
