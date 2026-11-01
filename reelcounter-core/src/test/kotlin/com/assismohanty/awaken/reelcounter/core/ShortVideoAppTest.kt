package com.assismohanty.awaken.reelcounter.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ShortVideoAppTest {
    @Test
    fun fromPackageKnown() {
        assertThat(ShortVideoApp.fromPackage("com.instagram.android")).isEqualTo(ShortVideoApp.INSTAGRAM)
        assertThat(ShortVideoApp.fromPackage("com.snapchat.android")).isEqualTo(ShortVideoApp.SNAPCHAT)
    }

    @Test
    fun fromPackageUnknownOrNull() {
        assertThat(ShortVideoApp.fromPackage("com.example")).isNull()
        assertThat(ShortVideoApp.fromPackage(null)).isNull()
    }

    @Test
    fun tiktokBothPackages() {
        assertThat(ShortVideoApp.fromPackage("com.zhiliaoapp.musically")).isEqualTo(ShortVideoApp.TIKTOK)
        assertThat(ShortVideoApp.fromPackage("com.ss.android.ugc.trill")).isEqualTo(ShortVideoApp.TIKTOK)
        assertThat(ShortVideoApp.TIKTOK.primaryPackage).isEqualTo("com.zhiliaoapp.musically")
    }

    @Test
    fun allPackagesHasSix() {
        assertThat(ShortVideoApp.allPackages).hasSize(6)
    }
}
