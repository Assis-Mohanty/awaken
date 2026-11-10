package com.assismohanty.awaken.engine.body

/** Product barcodes (Diet v2 plan section 6): validate the check digit and give one canonical 13-digit form. Pure. */
object Barcode {
    private const val EAN13 = 13
    private const val UPC_A = 12
    private const val SHORT = 8

    /**
     * The 13-digit form of [raw] (EAN-13; UPC-A gets one leading 0, EAN-8 five), or null when it is not a barcode or its
     * check digit is wrong. Eight digits can be an EAN-8 or a UPC-E with the same check digit, so the scanner's format
     * decides: pass [upcE] = true when it reported UPC-E.
     */
    fun canonical(raw: String, upcE: Boolean = false): String? {
        val digits = raw.trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        return when (digits.length) {
            EAN13 -> digits.takeIf { checkDigitOk(it) }
            UPC_A -> digits.takeIf { checkDigitOk(it) }?.let { "0$it" }
            SHORT -> if (upcE) expandUpcE(digits)?.let { "0$it" } else digits.takeIf { checkDigitOk(it) }?.let { "00000$it" }
            else -> null
        }
    }

    /** The last digit is the check digit: weights 3,1,3,1... counted from the digit next to it. */
    private fun checkDigitOk(code: String): Boolean {
        val body = code.dropLast(1)
        val sum = body.reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == code.last() - '0'
    }

    /** UPC-E (number system 0 or 1, six digits, check digit) to its 12-digit UPC-A, or null when it is not valid. */
    private fun expandUpcE(code: String): String? {
        val system = code[0]
        if (system != '0' && system != '1') return null
        val b = code.substring(1, 7)
        val body = when (b[5]) {
            '0', '1', '2' -> "${b.substring(0, 2)}${b[5]}0000${b.substring(2, 5)}"
            '3' -> "${b.substring(0, 3)}00000${b.substring(3, 5)}"
            '4' -> "${b.substring(0, 4)}00000${b[4]}"
            else -> "${b.substring(0, 5)}0000${b[5]}"
        }
        val upcA = "$system$body${code.last()}"
        return upcA.takeIf { checkDigitOk(it) }
    }
}
