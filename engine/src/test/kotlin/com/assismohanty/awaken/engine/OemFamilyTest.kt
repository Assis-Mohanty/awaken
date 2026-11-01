package com.assismohanty.awaken.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OemFamilyTest {
    @Test
    fun `common makers map to their family`() {
        assertThat(OemFamily.of("Xiaomi")).isEqualTo(OemFamily.XIAOMI)
        assertThat(OemFamily.of("POCO")).isEqualTo(OemFamily.XIAOMI)
        assertThat(OemFamily.of("samsung")).isEqualTo(OemFamily.SAMSUNG)
        assertThat(OemFamily.of("realme")).isEqualTo(OemFamily.OPPO)
        assertThat(OemFamily.of("OPPO")).isEqualTo(OemFamily.OPPO)
        assertThat(OemFamily.of("OnePlus")).isEqualTo(OemFamily.ONEPLUS)
        assertThat(OemFamily.of("vivo")).isEqualTo(OemFamily.VIVO)
        assertThat(OemFamily.of("iQOO")).isEqualTo(OemFamily.VIVO)
        assertThat(OemFamily.of("HONOR")).isEqualTo(OemFamily.HUAWEI)
    }

    @Test
    fun `unknown or missing makers are OTHER`() {
        assertThat(OemFamily.of("Google")).isEqualTo(OemFamily.OTHER)
        assertThat(OemFamily.of(null)).isEqualTo(OemFamily.OTHER)
    }
}
