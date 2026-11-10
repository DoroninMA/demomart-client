package com.demomart.client.anim

import com.demomart.client.account.BalanceEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceAnimationSpecTest {
    private val redeem = BalanceEffect.Redeem(127, 500, 18, 500)
    private val earn = BalanceEffect.Earn(128, 22)

    @Test fun redeemLabels() {
        assertEquals(listOf("−500 pts · €5.00 off", "+18 pts earned"), effectLabel(redeem))
    }

    @Test fun earnLabel() {
        assertEquals(listOf("+22 pts"), effectLabel(earn))
    }

    @Test fun refundLabel() {
        assertEquals(listOf("Refund · +482 pts"), effectLabel(BalanceEffect.Refund(127, 482)))
    }

    @Test fun durations() {
        assertEquals(1200, countDurationMs(redeem))
        assertEquals(800, countDurationMs(earn))
    }
}
