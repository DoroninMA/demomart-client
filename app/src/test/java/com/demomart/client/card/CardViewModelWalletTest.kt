package com.demomart.client.card

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.net.ApiResult
import com.demomart.client.testing.FakeMeRepository
import com.demomart.client.testing.MainDispatcherRule
import com.demomart.client.testing.MemoryCredentials
import com.demomart.client.testing.serverClock
import com.demomart.client.token.annaCredentials
import com.demomart.client.wallet.PayResults
import com.demomart.client.wallet.WalletAddedStore
import com.demomart.client.wallet.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModelWalletTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = MemoryCredentials()
    private val clock = serverClock()
    private val results = PayResults()
    private var enabled = true
    private var saveResult: ApiResult<String> = ApiResult.Ok("signed-jwt")
    private var saves = 0
    private val added = mutableSetOf<String>()
    private val repository = object : WalletRepository {
        override suspend fun enabled() = enabled
        override suspend fun saveJwt(): ApiResult<String> {
            saves++
            return saveResult
        }
    }
    private val flags = object : WalletAddedStore {
        override suspend fun contains(card: String) = card in added
        override suspend fun markAdded(card: String) { added.add(card) }
    }
    private val model by lazy {
        CardViewModel(store, clock, AccountRepository(FakeMeRepository(), store, clock), repository, results, flags)
    }

    private fun walletTest(body: suspend TestScope.() -> Unit) = runTest {
        store.save(annaCredentials)
        try {
            body()
        } finally {
            model.viewModelScope.cancel()
        }
    }

    private fun TestScope.ready() {
        model.onPayAvailability(true)
        runCurrent()
        assertEquals(WalletUiState.Ready, model.wallet.value)
    }

    @Test fun hiddenWhenServerDisabled() = walletTest {
        enabled = false
        model.onPayAvailability(true)
        runCurrent()
        assertEquals(WalletUiState.Hidden, model.wallet.value)
    }

    @Test fun hiddenWhenPayApiUnavailable() = walletTest {
        model.onPayAvailability(false)
        runCurrent()
        assertEquals(WalletUiState.Hidden, model.wallet.value)
    }

    @Test fun clickFetchesJwtAndLaunches() = walletTest {
        ready()
        model.onWalletClick()
        model.onWalletClick()
        assertEquals(WalletUiState.Saving, model.wallet.value)
        assertEquals("signed-jwt", model.launchSave.first())
        assertEquals(1, saves)
    }

    @Test fun resultOkMarksAdded() = walletTest {
        ready()
        model.onWalletClick()
        model.launchSave.first()
        results.emit(Activity.RESULT_OK)
        runCurrent()
        assertEquals(WalletUiState.Added, model.wallet.value)
        assertEquals(setOf(annaCredentials.cardNumber), added)
    }

    @Test fun cancelBackToReady() = walletTest {
        ready()
        model.onWalletClick()
        model.launchSave.first()
        results.emit(Activity.RESULT_CANCELED)
        runCurrent()
        assertEquals(WalletUiState.Ready, model.wallet.value)
    }

    @Test fun serverErrorShowsMessage() = walletTest {
        ready()
        saveResult = ApiResult.Err("wallet_unavailable")
        model.onWalletClick()
        runCurrent()
        assertEquals(WalletUiState.Error("Google Wallet is not responding, try again"), model.wallet.value)
    }
}
