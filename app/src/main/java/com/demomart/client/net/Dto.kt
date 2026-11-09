package com.demomart.client.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeSession(
    @SerialName("card_number") val cardNumber: String,
    @SerialName("card_display") val cardDisplay: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val balance: Long,
    @SerialName("totp_secret") val totpSecret: String,
) {
    override fun toString(): String = "MeSession(cardNumber=$cardNumber, role=$role, balance=$balance)"
}

@Serializable
data class Me(
    @SerialName("card_number") val cardNumber: String,
    @SerialName("card_display") val cardDisplay: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val balance: Long,
)

@Serializable
data class HistoryItem(
    @SerialName("sale_id") val saleId: Long,
    val kind: String,
    val at: String,
    @SerialName("paid_cents") val paidCents: Long,
    @SerialName("points_delta") val pointsDelta: Long,
    @SerialName("earned_points") val earnedPoints: Long,
    @SerialName("redeemed_points") val redeemedPoints: Long,
)

@Serializable data class FcmToken(val token: String)
@Serializable internal data class ErrorBody(val error: String, val message: String? = null)
