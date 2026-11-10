package com.demomart.client.hce

import com.demomart.client.token.CardCredentials
import com.demomart.client.token.CardToken

object HceResponder {
    private val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xF0.toByte(), 0x44, 0x4D, 0x4C, 0x4F, 0x59, 0x01)

    fun respond(apdu: ByteArray, creds: CardCredentials?, epochSeconds: Long): ByteArray {
        if (apdu.size !in select.size..select.size + 1 || !select.indices.all { apdu[it] == select[it] }) {
            return byteArrayOf(0x6D, 0x00)
        }
        if (creds == null) return byteArrayOf(0x69, 0x85.toByte())
        return CardToken.make(creds, epochSeconds).toByteArray(Charsets.US_ASCII) + byteArrayOf(0x90.toByte(), 0x00)
    }
}
