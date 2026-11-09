package com.demomart.client.token

import org.junit.Assert.assertEquals
import org.junit.Test

internal val annaCredentials = CardCredentials(
    "48210093", "DM 4821 0093", "Anna K.", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
)

class CardTokenTest {
    @Test fun crossLanguageVectorMatchesServer() {
        assertEquals("DM1:48210093:877905", CardToken.make(annaCredentials, 1_800_000_000))
        assertEquals("DM1:48210093:877905", CardToken.make(annaCredentials, 1_800_000_029))
        assertEquals("DM1:48210093:866818", CardToken.make(annaCredentials, 1_800_000_030))
    }

    @Test fun secondsLeft() {
        assertEquals(30, CardToken.secondsLeft(1_800_000_000))
        assertEquals(1, CardToken.secondsLeft(1_800_000_029))
    }
}
