package com.demomart.client.testing

import android.app.Activity
import com.demomart.client.account.CredentialStore
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.net.*
import com.demomart.client.token.CardCredentials
import com.demomart.client.token.MemoryOffsetStore
import com.demomart.client.token.ServerClock
import com.demomart.client.token.annaCredentials
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemoryCredentials : CredentialStore {
    override val credentials = MutableStateFlow<CardCredentials?>(null)
    override suspend fun load() = credentials.value
    override suspend fun save(card: CardCredentials) { credentials.value = card }
    override suspend fun clear() { credentials.value = null }
}

internal class FakeMeRepository : MeRepository {
    var profile = Me("48210093", "DM 4821 0093", "Anna K.", "customer", 1355)
    var sessionError: ApiResult.Err? = null
    var historyItems = emptyList<HistoryItem>()
    var sessions = 0
    var profiles = 0
    var histories = 0
    var beforeMe: suspend () -> Unit = {}

    override suspend fun openSession(): ApiResult<MeSession> {
        sessions++
        return sessionError ?: ApiResult.Ok(
            MeSession(profile.cardNumber, profile.cardDisplay, profile.displayName, profile.role,
                profile.balance, annaCredentials.secretBase32),
        )
    }

    override suspend fun me(): ApiResult<Me> {
        profiles++
        beforeMe()
        return ApiResult.Ok(profile)
    }

    override suspend fun history(limit: Int, before: HistoryItem?): ApiResult<List<HistoryItem>> {
        histories++
        val start = if (before == null) 0 else historyItems.indexOf(before) + 1
        return ApiResult.Ok(historyItems.drop(start).take(limit))
    }

    override suspend fun putFcmToken(token: String) = ApiResult.Ok(Unit)
}

internal class FakeAuth : AuthTokenProvider {
    override val signedIn = MutableStateFlow(false)
    var signOuts = 0
    var name: String? = null
    override suspend fun signInEmail(email: String, password: String): ApiResult<Unit> {
        signedIn.value = true
        return ApiResult.Ok(Unit)
    }

    override suspend fun signUpEmail(name: String, email: String, password: String): ApiResult<Unit> {
        this.name = name
        return signInEmail(email, password)
    }

    override suspend fun signInGoogle(activity: Activity) = ApiResult.Err("unsupported")
    override suspend fun idToken(forceRefresh: Boolean) = if (signedIn.value) "token" else null
    override suspend fun signOut() { signOuts++; signedIn.value = false }
}

internal val testClock = Clock.fixed(Instant.ofEpochSecond(1_800_000_000), ZoneOffset.UTC)
internal fun serverClock(clock: Clock = testClock) = ServerClock(clock, MemoryOffsetStore())
