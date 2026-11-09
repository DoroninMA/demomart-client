package com.demomart.client.token

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(text: String): ByteArray {
        val encoded = text.trimEnd('=')
        val bytes = ByteArray(encoded.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in encoded) {
            val digit = ALPHABET.indexOf(char.uppercaseChar())
            require(digit >= 0) { "Invalid Base32 character" }
            buffer = (buffer shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bits -= 8
                bytes[index++] = (buffer ushr bits).toByte()
            }
        }
        return bytes
    }
}
