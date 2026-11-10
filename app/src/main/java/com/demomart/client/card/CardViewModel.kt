package com.demomart.client.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.account.BalanceEffect
import com.demomart.client.account.CredentialStore
import com.demomart.client.token.CardToken
import com.demomart.client.token.ServerClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class NfcState { Ready, Disabled, Unsupported }

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
    store: CredentialStore,
    clock: ServerClock,
    private val account: AccountRepository,
) : ViewModel() {
    private val nfc = MutableStateFlow(NfcState.Unsupported)
    private var polling: Job? = null

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
}
