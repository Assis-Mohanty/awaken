package com.assismohanty.awaken.reelcounter.core

import kotlinx.serialization.Serializable

/** The short-video surfaces Reel Counter knows about. */
@Serializable
enum class ShortVideoApp(val packages: List<String>, val monogram: Char) {
    INSTAGRAM(listOf("com.instagram.android"), 'I'),
    YOUTUBE(listOf("com.google.android.youtube"), 'Y'),
    TIKTOK(listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), 'T'),
    FACEBOOK(listOf("com.facebook.katana"), 'F'),
    SNAPCHAT(listOf("com.snapchat.android"), 'S');

    val primaryPackage: String get() = packages.first()

    companion object {
        val allPackages: Set<String> = entries.flatMap { it.packages }.toSet()

        fun fromPackage(pkg: String?): ShortVideoApp? =
            if (pkg == null) null else entries.firstOrNull { pkg in it.packages }
    }
}
