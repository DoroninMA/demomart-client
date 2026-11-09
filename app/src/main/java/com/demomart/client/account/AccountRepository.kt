package com.demomart.client.account

import com.demomart.client.net.ApiResult
import com.demomart.client.net.HistoryItem
import com.demomart.client.net.MeRepository
import com.demomart.client.token.CardCredentials
import com.demomart.client.token.ServerClock
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountState(
    val displayName: String,
    val cardDisplay: String,
    val balance: Long,
    val history: List<HistoryItem> = emptyList(),
    val refreshedAt: Instant? = null,
)

@Singleton
class AccountRepository @Inject constructor(
    private val me: MeRepository,
    private val store: CredentialStore,
    private val clock: ServerClock,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<AccountState?>(null)
    val account = state.asStateFlow()

    suspend fun openSession(): ApiResult<Unit> = mutex.withLock { openSessionLocked() }

    suspend fun ensureCredentials() = mutex.withLock {
        if (store.load() == null) openSessionLocked()
        Unit
    }

    suspend fun clear() = mutex.withLock { state.value = null }

    private suspend fun openSessionLocked(): ApiResult<Unit> {
        val response = me.openSession()
        if (response is ApiResult.Err) return response
        val session = (response as ApiResult.Ok).value
        if (session.role == "cashier") return ApiResult.Err("cashier_account")
        try {
            store.save(CardCredentials(session.cardNumber, session.cardDisplay, session.displayName, session.totpSecret))
        } catch (_: IOException) {
            return ApiResult.Err("storage")
        } catch (_: GeneralSecurityException) {
            return ApiResult.Err("storage")
        }
        state.value = AccountState(session.displayName, session.cardDisplay, session.balance)
        return ApiResult.Ok(Unit)
    }
}
