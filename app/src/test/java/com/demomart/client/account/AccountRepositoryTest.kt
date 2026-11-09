package com.demomart.client.account

import com.demomart.client.net.ApiResult
import com.demomart.client.testing.FakeMeRepository
import com.demomart.client.testing.MemoryCredentials
import com.demomart.client.testing.serverClock
import com.demomart.client.token.annaCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountRepositoryTest {
    private val me = FakeMeRepository()
    private val store = MemoryCredentials()
    private val account = AccountRepository(me, store, serverClock())

    @Test fun sessionSavesCredentials() = runTest {
        assertEquals(ApiResult.Ok(Unit), account.openSession())
        assertEquals(annaCredentials, store.load())
        assertEquals(1355L, account.account.value?.balance)
    }

    @Test fun cashierAccountRejected() = runTest {
        me.profile = me.profile.copy(role = "cashier")
        assertEquals("cashier_account", (account.openSession() as ApiResult.Err).code)
        assertNull(store.load())
    }

    @Test fun missingCredentialsReopenSession() = runTest {
        account.ensureCredentials()
        assertEquals(1, me.sessions)
        assertEquals(annaCredentials, store.load())
    }
}
