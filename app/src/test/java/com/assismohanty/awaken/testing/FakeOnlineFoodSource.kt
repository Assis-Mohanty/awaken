package com.assismohanty.awaken.testing

import com.assismohanty.awaken.core.data.food.OnlineFoodSource
import com.assismohanty.awaken.core.data.food.OnlineLookup

/** An online list that answers what the test says and remembers every barcode it was asked about (nothing real leaves the test). */
class FakeOnlineFoodSource : OnlineFoodSource {
    var answer: OnlineLookup = OnlineLookup.NotListed
    val asked = mutableListOf<String>()

    override suspend fun lookup(barcode: String): OnlineLookup {
        asked += barcode
        return answer
    }
}
