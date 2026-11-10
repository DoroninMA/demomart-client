package com.demomart.client.account

import com.demomart.client.net.ApiResult
import com.demomart.client.testing.FakeMeRepository
import com.demomart.client.testing.MemoryCredentials
import com.demomart.client.testing.serverClock
import com.demomart.client.token.annaCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test fun refreshTwiceAnimatesOnce() = runTest {
        account.openSession()
        me.historyItems = listOf(redeem)
        me.profile = me.profile.copy(balance = 873)
        account.refresh()
        val effect = BalanceEffect.Redeem(127, 500, 18, 500)
        assertEquals(effect, account.account.value?.lastEffect)
        account.refresh()
        assertEquals(effect, account.account.value?.lastEffect)
        account.consumeEffect(effect.key)
        account.refresh()
        assertNull(account.account.value?.lastEffect)
    }

    @Test fun concurrentRefreshSerialized() = runTest {
        account.openSession()
        val baselineProfiles = me.profiles
        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
        me.beforeMe = { firstStarted.complete(Unit); finishFirst.await() }
        me.historyItems = listOf(earn)
        val first = launch { account.refresh() }
        firstStarted.await()
        val second = launch { account.refresh() }
        runCurrent()
        assertEquals(baselineProfiles + 1, me.profiles)
        finishFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(baselineProfiles + 2, me.profiles)
        assertEquals(BalanceEffect.Earn(128, 22), account.account.value?.lastEffect)
    }

    @Test fun sessionSetsBaseline() = runTest {
        me.historyItems = listOf(redeem)
        account.openSession()
        assertNull(account.account.value?.lastEffect)
        me.historyItems = listOf(earn, redeem)
        account.refresh()
        assertEquals(BalanceEffect.Earn(128, 22), account.account.value?.lastEffect)
    }

    @Test fun pollFetchesHistoryOnlyOnChange() = runTest {
        account.openSession()
        val profiles = me.profiles
        val histories = me.histories
        account.poll()
        assertEquals(profiles + 1, me.profiles)
        assertEquals(histories, me.histories)
        me.profile = me.profile.copy(balance = 873)
        account.poll()
        assertEquals(histories + 1, me.histories)
        assertEquals(873L, account.account.value?.balance)
    }

    @Test fun coldPushOnlyAnimatesMatchingAccountHistory() = runTest {
        me.historyItems = listOf(redeem)
        account.refreshFromPush(redeem.saleId + 1, redeem.pointsDelta)
        assertNull(account.account.value?.lastEffect)

        account.clear()
        account.refreshFromPush(redeem.saleId, redeem.pointsDelta)
        assertEquals(BalanceEffect.Redeem(127, 500, 18, 500), account.account.value?.lastEffect)
    }
}
