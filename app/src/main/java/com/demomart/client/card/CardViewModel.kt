package com.demomart.client.card

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.account.BalanceEffect
import com.demomart.client.account.CredentialStore
import com.demomart.client.net.ApiResult
import com.demomart.client.token.CardToken
import com.demomart.client.token.ServerClock
import com.demomart.client.wallet.PayResults
import com.demomart.client.wallet.WalletAddedStore
import com.demomart.client.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class NfcState { Ready, Disabled, Unsupported }

sealed interface WalletUiState {
    data object Hidden : WalletUiState
    data object Ready : WalletUiState
    data object Saving : WalletUiState
    data object Added : WalletUiState
    data class Error(val text: String) : WalletUiState
}

data class CardState(
    val token: String? = null,
    val secondsLeft: Int = 30,
    val cardDisplay: String = "",
    val balance: Long? = null,
    val nfc: NfcState = NfcState.Unsupported,
    val lastEffect: BalanceEffect? = null,
)

@HiltViewModel
class CardViewModel @Inject constructor(
    private val store: CredentialStore,
    clock: ServerClock,
    private val account: AccountRepository,
    private val walletRepository: WalletRepository,
    payResults: PayResults,
    private val walletAdded: WalletAddedStore,
) : ViewModel() {
    private val nfc = MutableStateFlow(NfcState.Unsupported)
    private var polling: Job? = null
    private var checkingWallet: Job? = null
    private var walletCard: String? = null
    private var pendingCard: String? = null
    private val walletState = MutableStateFlow<WalletUiState>(WalletUiState.Hidden)
    val wallet = walletState.asStateFlow()
    private val saveRequests = Channel<String>(Channel.BUFFERED)
    val launchSave = saveRequests.receiveAsFlow()

    init {
        viewModelScope.launch { payResults.results.collect(::onSaveResult) }
    }

    private val ticks = flow {
        clock.restore()
        while (true) {
            emit(clock.now())
            delay(1000)
        }
    }

    val state = combine(store.credentials, ticks, account.account, nfc) { credentials, now, profile, status ->
        CardState(
            token = credentials?.let { CardToken.make(it, now) },
            secondsLeft = CardToken.secondsLeft(now),
            cardDisplay = credentials?.cardDisplay.orEmpty(),
            balance = profile?.balance,
            nfc = status,
            lastEffect = profile?.lastEffect,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CardState())

    fun setNfc(status: NfcState) { nfc.value = status }

    fun onPayAvailability(available: Boolean) {
        checkingWallet?.cancel()
        if (!available) {
            walletCard = null
            walletState.value = WalletUiState.Hidden
            return
        }
        if (wallet.value == WalletUiState.Saving) return
        checkingWallet = viewModelScope.launch {
            val card = store.load()?.cardNumber
            if (card == null || !walletRepository.enabled()) {
                walletCard = null
                walletState.value = WalletUiState.Hidden
            } else {
                walletCard = card
                walletState.value = if (walletAdded.contains(card)) WalletUiState.Added else WalletUiState.Ready
            }
        }
    }

    fun onWalletClick() {
        val card = walletCard ?: return
        if (wallet.value == WalletUiState.Hidden || wallet.value == WalletUiState.Saving) return
        walletState.value = WalletUiState.Saving
        viewModelScope.launch {
            val result = walletRepository.saveJwt()
            if (card != walletCard || card != store.load()?.cardNumber) {
                walletState.value = WalletUiState.Hidden
                return@launch
            }
            when (result) {
                is ApiResult.Ok -> {
                    pendingCard = card
                    saveRequests.send(result.value)
                }
                is ApiResult.Err -> walletState.value = WalletUiState.Error(result.message)
            }
        }
    }

    fun onSaveResult(code: Int) {
        val card = pendingCard ?: return
        pendingCard = null
        if (card != walletCard) return
        walletState.value = when (code) {
            Activity.RESULT_OK -> WalletUiState.Added
            Activity.RESULT_CANCELED -> WalletUiState.Ready
            else -> WalletUiState.Error("Couldn't add the card to Google Wallet")
        }
        if (code == Activity.RESULT_OK) {
            viewModelScope.launch {
                try {
                    walletAdded.markAdded(card)
                } catch (_: IOException) {
                    // the pass is in Wallet anyway
                }
            }
        }
    }

    fun onVisible() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                account.poll()
            }
        }
    }

    fun onHidden() {
        polling?.cancel()
        polling = null
    }
    fun consumeEffect(key: String) = account.consumeEffect(key)
}
