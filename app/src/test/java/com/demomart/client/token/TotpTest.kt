package com.demomart.client.token

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    @Test fun rfc6238Vectors() {
        val key = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        assertEquals("94287082", Totp.code(key, 59, digits = 8))
        assertEquals("07081804", Totp.code(key, 1111111109, digits = 8))
        assertEquals("89005924", Totp.code(key, 1234567890, digits = 8))
        assertEquals("279037", Totp.code(key, 2000000000))
    }

    @Test fun base32IgnoresCaseAndPadding() {
        assertArrayEquals("foo".toByteArray(), Base32.decode("mzxw6==="))
    }
}
