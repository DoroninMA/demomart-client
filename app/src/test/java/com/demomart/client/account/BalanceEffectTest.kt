package com.demomart.client.account

import com.demomart.client.net.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal val redeem = HistoryItem(127, "sale", "2026-11-09T12:00:00Z", 1800, -482, 18, 500)
internal val earn = redeem.copy(saleId = 128, pointsDelta = 22, earnedPoints = 22, redeemedPoints = 0)
internal val refund = redeem.copy(kind = "refund", pointsDelta = 482, earnedPoints = -18, redeemedPoints = -500)

class BalanceEffectTest {
    @Test fun firstLoadNeverAnimates() =
        assertNull(BalanceEffect.detect(null, listOf(earn)))

    @Test fun firstPurchaseOfEmptyAccount() =
        assertEquals(BalanceEffect.Earn(128, 22), BalanceEffect.detect("", listOf(earn)))

    @Test fun redeemSale() =
        assertEquals(BalanceEffect.Redeem(127, 500, 18, 500), BalanceEffect.detect("sale:126", listOf(redeem)))

    @Test fun earnSale() =
        assertEquals(BalanceEffect.Earn(128, 22), BalanceEffect.detect("sale:127", listOf(earn)))

    @Test fun refundItem() =
        assertEquals(BalanceEffect.Refund(127, 482), BalanceEffect.detect("sale:127", listOf(refund)))

    @Test fun sameTopTwiceNoEffect() =
        assertNull(BalanceEffect.detect("sale:127", listOf(redeem)))
}
