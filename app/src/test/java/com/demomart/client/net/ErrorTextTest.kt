package com.demomart.client.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorTextTest {
    @Test fun customerErrors() {
        assertEquals("This email is already used by another account", errorText("email_taken"))
        assertEquals("Cashier accounts can't use the customer app", errorText("cashier_account"))
    }
}
