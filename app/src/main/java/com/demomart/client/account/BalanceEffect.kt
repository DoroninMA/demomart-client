package com.demomart.client.account

import com.demomart.client.net.HistoryItem

internal val HistoryItem.key: String get() = "$kind:$saleId"

sealed interface BalanceEffect {
    val saleId: Long

    val key: String
        get() = if (this is Refund) "refund:$saleId" else "sale:$saleId"

    data class Earn(override val saleId: Long, val earned: Long) : BalanceEffect
    data class Redeem(
        override val saleId: Long,
        val redeemed: Long,
        val earned: Long,
        val discountCents: Long,
    ) : BalanceEffect
    data class Refund(override val saleId: Long, val delta: Long) : BalanceEffect

    companion object {
        fun detect(lastSeenTopKey: String?, items: List<HistoryItem>): BalanceEffect? {
            val newest = items.firstOrNull() ?: return null
            if (lastSeenTopKey == null || lastSeenTopKey == newest.key) return null
            return when {
                newest.kind == "refund" -> Refund(newest.saleId, newest.pointsDelta)
                newest.redeemedPoints > 0 -> Redeem(
                    newest.saleId, newest.redeemedPoints, newest.earnedPoints, newest.redeemedPoints,
                )
                else -> Earn(newest.saleId, newest.earnedPoints)
            }
        }
    }
}
