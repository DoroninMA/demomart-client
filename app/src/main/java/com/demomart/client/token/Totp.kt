package com.demomart.client.token

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    fun code(secret: ByteArray, epochSeconds: Long, digits: Int = 6, step: Long = 30): String {
        require(digits in 1..9 && step > 0)
        val counter = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(Math.floorDiv(epochSeconds, step))
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val digest = mac.doFinal(counter.array())
        val offset = digest.last().toInt() and 0x0f
        val value = ByteBuffer.wrap(digest, offset, Int.SIZE_BYTES).int and Int.MAX_VALUE
        return value.toString().padStart(digits, '0').takeLast(digits)
    }
}
