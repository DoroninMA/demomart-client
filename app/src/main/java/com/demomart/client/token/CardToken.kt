package com.demomart.client.token

data class CardCredentials(
    val cardNumber: String,
    val cardDisplay: String,
    val displayName: String,
    val secretBase32: String,
) {
    override fun toString(): String = "CardCredentials(cardNumber=$cardNumber)"
}

object CardToken {
    private const val STEP = 30L

    fun make(creds: CardCredentials, epochSeconds: Long): String {
        val code = Totp.code(Base32.decode(creds.secretBase32), epochSeconds, step = STEP)
        return "DM1:${creds.cardNumber}:$code"
    }

    fun secondsLeft(epochSeconds: Long): Int = (STEP - Math.floorMod(epochSeconds, STEP)).toInt()
}
