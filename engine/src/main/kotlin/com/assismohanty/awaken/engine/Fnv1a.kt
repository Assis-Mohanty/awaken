package com.assismohanty.awaken.engine

/** FNV-1a 64-bit over UTF-8 bytes. Stable across platforms so a future server can reproduce every roll. */
object Fnv1a {
    private const val OFFSET_BASIS: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME: Long = 0x100000001b3L

    fun hash64(input: String): Long {
        var hash = OFFSET_BASIS
        for (byte in input.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= PRIME
        }
        return hash
    }
}
