package com.demomart.client.card

import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.testing.*
import com.demomart.client.token.annaCredentials
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = MemoryCredentials()
    private val me = FakeMeRepository()
    private var seconds = 1_800_000_000L
    private val deviceClock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
        override fun instant(): Instant = Instant.ofEpochSecond(seconds)
    }
    private val clock = serverClock(deviceClock)
    private val account = AccountRepository(me, store, clock)
    private val viewModel by lazy { CardViewModel(store, clock, account) }

    private fun runCardTest(body: suspend TestScope.() -> Unit) = runTest {
        val model = viewModel
        try {
            body()
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test fun tokenRotatesEvery30s() = runCardTest {
        store.save(annaCredentials)
        runCurrent()
        assertEquals("DM1:48210093:877905", viewModel.state.value.token)
        assertEquals(30, viewModel.state.value.secondsLeft)
        seconds += 30
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("DM1:48210093:866818", viewModel.state.value.token)
    }

    @Test fun noCredentialsNoToken() = runCardTest {
        runCurrent()
        assertNull(viewModel.state.value.token)
    }

    @Test fun pollsOnlyWhileVisible() = runCardTest {
        account.openSession()
        val baseline = me.profiles
        viewModel.onVisible()
        advanceTimeBy(6000)
        runCurrent()
        assertEquals(baseline + 3, me.profiles)
        viewModel.onHidden()
        advanceTimeBy(6000)
        runCurrent()
        assertEquals(baseline + 3, me.profiles)
    }
}
