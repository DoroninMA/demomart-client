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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountState(
    val displayName: String,
    val cardDisplay: String,
    val balance: Long,
    val history: List<HistoryItem> = emptyList(),
    val lastEffect: BalanceEffect? = null,
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
    private var lastSeenTopKey: String? = null
    val account = state.asStateFlow()

    suspend fun openSession(): ApiResult<Unit> = mutex.withLock { openSessionLocked() }

    suspend fun ensureCredentials() = mutex.withLock {
        if (store.load() == null) openSessionLocked()
        Unit
    }

    suspend fun clear() = mutex.withLock {
        lastSeenTopKey = null
        state.value = null
    }

    suspend fun refresh(): ApiResult<Unit> = mutex.withLock { refreshLocked() }

    suspend fun poll(): ApiResult<Unit> = mutex.withLock {
        when (val profile = me.me()) {
            is ApiResult.Err -> profile
            is ApiResult.Ok -> if (profile.value.balance == state.value?.balance) ApiResult.Ok(Unit)
            else refreshLocked()
        }
    }

    fun consumeEffect(saleKey: String) {
        state.update { current ->
            if (current?.lastEffect?.key == saleKey) current.copy(lastEffect = null) else current
        }
    }

    private suspend fun refreshLocked(): ApiResult<Unit> = coroutineScope {
        val pendingProfile = async { me.me() }
        val pendingHistory = async { me.history() }
        val profileResult = pendingProfile.await()
        val historyResult = pendingHistory.await()
        if (profileResult is ApiResult.Err) return@coroutineScope profileResult
        if (historyResult is ApiResult.Err) return@coroutineScope historyResult

        val profile = (profileResult as ApiResult.Ok).value
        val items = (historyResult as ApiResult.Ok).value
        val effect = BalanceEffect.detect(lastSeenTopKey, items)
        lastSeenTopKey = items.firstOrNull()?.key.orEmpty()
        state.update { previous ->
            AccountState(
                profile.displayName, profile.cardDisplay, profile.balance, items,
                effect ?: previous?.lastEffect, Instant.ofEpochSecond(clock.now()),
            )
        }
        ApiResult.Ok(Unit)
    }

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
        lastSeenTopKey = null
        state.value = AccountState(session.displayName, session.cardDisplay, session.balance)
        refreshLocked()
        return ApiResult.Ok(Unit)
    }
}
