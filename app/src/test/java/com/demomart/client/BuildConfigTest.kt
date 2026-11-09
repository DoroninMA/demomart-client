package com.demomart.client

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test fun debugDefaults() {
        assertEquals("http://10.0.2.2:8000/", BuildConfig.SERVER_URL)
        assertEquals("fake", BuildConfig.AUTH_MODE)
    }
}
