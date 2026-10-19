package com.companionstudio.nyx.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneBrandTest {

    @Test
    fun knownBrands_matchWhateverTheCase() {
        assertEquals(PhoneBrand.XIAOMI, phoneBrand("Xiaomi"))
        assertEquals(PhoneBrand.SAMSUNG, phoneBrand("samsung"))
        assertEquals(PhoneBrand.VIVO, phoneBrand("vivo"))
        assertEquals(PhoneBrand.OPPO, phoneBrand("OPPO"))
    }

    @Test
    fun otherBrands_getTheGenericSteps() {
        assertEquals(PhoneBrand.OTHER, phoneBrand("Google"))
        assertEquals(PhoneBrand.OTHER, phoneBrand("realme"))
        assertEquals(PhoneBrand.OTHER, phoneBrand(""))
    }
}
