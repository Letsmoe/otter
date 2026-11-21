package com.letsmoe.otter.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNetworkTest {

    private val home = HomeNetwork<Int>()

    @Test
    fun losingTheNetworkTheTvIsOnIsLeavingHome() {
        home.rememberAs(7)

        assertTrue(home.isLost(7))
    }

    @Test
    fun losingAnotherNetworkIsNotLeavingHome() {
        home.rememberAs(7)

        assertFalse(home.isLost(8))
    }

    @Test
    fun withoutAHomeNetworkNothingIsLeft() {
        assertFalse(home.isLost(7))

        home.rememberAs(7)
        home.rememberAs(null)

        assertFalse(home.isLost(7))
    }
}
