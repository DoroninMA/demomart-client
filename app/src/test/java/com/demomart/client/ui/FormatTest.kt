package com.demomart.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun formatsPoints() {
        assertEquals("1\u2009355 pts", formatPoints(1355))
        assertEquals("0 pts", formatPoints(0))
    }

    @Test fun formatsDelta() {
        assertEquals("+18 pts", formatPointsDelta(18))
        assertEquals("−500 pts", formatPointsDelta(-500))
    }

    @Test fun formatsEuros() {
        assertEquals("€13.55", formatEur(1355))
        assertEquals("−€8.40", formatEur(-840))
    }
}
