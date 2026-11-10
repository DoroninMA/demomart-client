package com.demomart.client.push

import com.demomart.client.net.ApiResult
import com.demomart.client.net.MeRepository
import com.demomart.client.testing.FakeMeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRegistrarTest {
    private val tokens = mutableListOf<String>()
    private var outcome: ApiResult<Unit> = ApiResult.Ok(Unit)
    private val me = object : MeRepository by FakeMeRepository() {
        override suspend fun putFcmToken(token: String): ApiResult<Unit> {
            tokens += token
            return outcome
        }
    }

    @Test fun registerSendsToken() = runTest {
        PushRegistrar(me, FcmTokenSource { "tok" }).register()
        assertEquals(listOf("tok"), tokens)
    }

    @Test fun noopSourceSendsNothing() = runTest {
        PushRegistrar(me, NoopFcmTokenSource).register()
        assertTrue(tokens.isEmpty())
    }

    @Test fun registerFailureIsSwallowed() = runTest {
        outcome = ApiResult.Err("network")
        PushRegistrar(me, FcmTokenSource { "tok" }).register()
        assertEquals(listOf("tok"), tokens)
    }
}
