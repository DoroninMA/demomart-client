package com.demomart.client.hce

import com.demomart.client.testing.MemoryCredentials
import com.demomart.client.token.annaCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

private fun String.apdu() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class HceResponderTest {
    private val select = "00A4040007F0444D4C4F590100".apdu()
    private val now = 1_800_000_000L
    private val expected = "DM1:48210093:877905".toByteArray(Charsets.US_ASCII) + "9000".apdu()

    @Test fun selectReturnsTokenAnd9000() {
        assertArrayEquals(expected, HceResponder.respond(select, annaCredentials, now))
    }

    @Test fun selectWithoutLeAlsoWorks() {
        assertArrayEquals(expected, HceResponder.respond(select.copyOf(12), annaCredentials, now))
    }

    @Test fun notSignedInReturns6985() {
        assertArrayEquals("6985".apdu(), HceResponder.respond(select, null, now))
    }

    @Test fun otherAidReturns6D00() {
        val other = "00A4040007F0444D4C4F590200".apdu()
        assertArrayEquals("6D00".apdu(), HceResponder.respond(other, annaCredentials, now))
    }

    @Test fun garbageReturns6D00() {
        for (apdu in listOf(byteArrayOf(), select.copyOf(3), select.copyOf(14))) {
            assertArrayEquals("6D00".apdu(), HceResponder.respond(apdu, annaCredentials, now))
        }
    }

    @Test fun worksOfflineFromStoredCreds() = runTest {
        val store = MemoryCredentials()
        store.save(annaCredentials)
        assertArrayEquals(expected, HceResponder.respond(select, store.load(), now))
        store.clear()
        assertArrayEquals("6985".apdu(), HceResponder.respond(select, store.load(), now))
    }
}
