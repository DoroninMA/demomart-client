package com.demomart.client.net

import android.app.Activity
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.di.NetworkModule
import com.demomart.client.token.MemoryOffsetStore
import com.demomart.client.token.ServerClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RetrofitMeRepositoryTest {
    private val server = MockWebServer()
    private val instant = Instant.ofEpochSecond(1_800_000_000)
    private val device = Clock.fixed(instant, ZoneOffset.UTC)
    private val clock = ServerClock(device, MemoryOffsetStore())
    private val auth = object : AuthTokenProvider {
        override val signedIn = MutableStateFlow(true)
        @Volatile var activeSession = "anna"
        override val sessionId: String get() = activeSession
        override suspend fun idToken(forceRefresh: Boolean) = when {
            activeSession == "ben" -> "new-account"
            forceRefresh -> "fresh"
            else -> "original"
        }
        override suspend fun signOut() { signedIn.value = false }
        override suspend fun signInEmail(email: String, password: String) = error("Unused")
        override suspend fun signUpEmail(name: String, email: String, password: String) = error("Unused")
        override suspend fun signInGoogle(activity: Activity) = error("Unused")
    }
    private lateinit var repo: MeRepository
    private val session = """{"card_number":"48210093","card_display":"DM 4821 0093","display_name":"Anna K.","role":"customer","balance":1355,"totp_secret":"MZXW6"}"""

    @Before fun setUp() {
        server.start()
        val json = NetworkModule.json()
        val http = NetworkModule.httpClient(auth, clock, device)
        val retrofit = NetworkModule.retrofit(http, json, server.url("/").toString())
        repo = RetrofitMeRepository(retrofit.create(MeApi::class.java), json)
    }

    @After fun tearDown() { server.shutdown() }

    private fun enqueue(body: String = session, status: Int = 200, date: String? = null) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body).apply {
            if (date != null) setHeader("Date", date)
        })
    }

    @Test fun sessionParses() = runTest {
        enqueue()
        assertEquals("MZXW6", (repo.openSession() as ApiResult.Ok).value.totpSecret)
        assertEquals("/v1/me/session", server.takeRequest().path)
    }

    @Test fun historyParsesBreakdown() = runTest {
        enqueue("""[{"sale_id":127,"kind":"sale","at":"2026-11-09T12:00:00Z","paid_cents":1800,"points_delta":-482,"earned_points":18,"redeemed_points":500}]""")
        val sale = (repo.history() as ApiResult.Ok).value.single()
        assertEquals(18L, sale.earnedPoints)
        assertEquals(500L, sale.redeemedPoints)
    }

    @Test fun dateHeaderFeedsServerClock() = runTest {
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.plusSeconds(240).atZone(ZoneOffset.UTC))
        enqueue(date = date)
        repo.openSession()
        assertEquals(instant.epochSecond + 240, clock.now())
    }

    @Test fun emailTakenMapped() = runTest {
        enqueue("""{"error":"email_taken"}""", 409)
        assertEquals("email_taken", (repo.openSession() as ApiResult.Err).code)
    }

    @Test fun single401RetriesWithFreshToken() = runTest {
        enqueue(status = 401)
        enqueue()
        assertTrue(repo.openSession() is ApiResult.Ok)
        assertEquals("Bearer original", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
        assertTrue(auth.signedIn.value)
    }

    @Test fun double401SignsOut() = runTest {
        repeat(2) { enqueue("""{"error":"unauthenticated"}""", 401) }
        assertEquals("unauthenticated", (repo.openSession() as ApiResult.Err).code)
        assertFalse(auth.signedIn.value)
        assertEquals(2, server.requestCount)
    }

    @Test fun emptyFcmResponseSucceeds() = runTest {
        enqueue("", 204)
        assertEquals(ApiResult.Ok(Unit), repo.putFcmToken("tok"))
        assertEquals("""{"token":"tok"}""", server.takeRequest().body.readUtf8())
    }

    @Test fun stale401DoesNotRetryAsAnotherAccount() = runTest {
        changeAccountOnUnauthorized("Bearer original")
        assertEquals("unauthenticated", (repo.openSession() as ApiResult.Err).code)
        assertEquals(1, server.requestCount)
        assertTrue(auth.signedIn.value)
    }

    @Test fun staleRetryDoesNotSignOutAnotherAccount() = runTest {
        changeAccountOnUnauthorized("Bearer fresh")
        assertEquals("unauthenticated", (repo.openSession() as ApiResult.Err).code)
        assertEquals(2, server.requestCount)
        assertTrue(auth.signedIn.value)
    }

    private fun changeAccountOnUnauthorized(header: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") == header) auth.activeSession = "ben"
                return MockResponse().setResponseCode(401).setBody("""{"error":"unauthenticated"}""")
            }
        }
    }
}
