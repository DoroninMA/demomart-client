package com.demomart.client.wallet

import com.demomart.client.di.NetworkModule
import com.demomart.client.net.ApiResult
import com.demomart.client.net.MeApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRepositoryTest {
    private val server = MockWebServer()
    private val json = NetworkModule.json()
    private val api = NetworkModule.retrofit(OkHttpClient(), json, server.url("/").toString())
        .create(MeApi::class.java)
    private val repository = RetrofitWalletRepository(api, json)

    @After fun shutdown() = server.shutdown()

    @Test fun readsStatusAndHidesOnFailure() = runTest {
        server.enqueue(MockResponse().setBody("""{"enabled":true}"""))
        assertTrue(repository.enabled())
        assertEquals("/v1/me/wallet", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(repository.enabled())
    }

    @Test fun savesAndReturnsJwt() = runTest {
        server.enqueue(MockResponse().setBody("""{"jwt":"signed","save_url":"https://example.test"}"""))
        assertEquals(ApiResult.Ok("signed"), repository.saveJwt())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/me/wallet/save", request.path)
    }

    @Test fun exposesWalletUnavailableFrom502() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"wallet_unavailable"}"""))
        assertEquals(ApiResult.Err("wallet_unavailable"), repository.saveJwt())
    }
}
