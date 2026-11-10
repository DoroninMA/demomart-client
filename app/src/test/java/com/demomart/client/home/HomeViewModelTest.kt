package com.demomart.client.home

import com.demomart.client.account.AccountRepository
import com.demomart.client.testing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun refreshLoadsAccount() = runTest {
        val account = AccountRepository(FakeMeRepository(), MemoryCredentials(), serverClock())
        val home = HomeViewModel(account)
        home.refresh()
        assertTrue(home.refreshing.value)
        advanceUntilIdle()
        assertFalse(home.refreshing.value)
        assertEquals(1355L, home.account.value?.balance)
        assertNull(home.error.value)
    }
}
