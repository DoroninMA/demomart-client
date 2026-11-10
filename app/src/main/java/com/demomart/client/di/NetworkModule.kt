package com.demomart.client.di

import com.demomart.client.BuildConfig
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.net.MeApi
import com.demomart.client.net.MeRepository
import com.demomart.client.net.RetrofitMeRepository
import com.demomart.client.token.ServerClock
import com.demomart.client.wallet.DataStoreWalletAdded
import com.demomart.client.wallet.RetrofitWalletRepository
import com.demomart.client.wallet.WalletAddedStore
import com.demomart.client.wallet.WalletRepository
import com.google.firebase.FirebaseException
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun httpClient(auth: AuthTokenProvider, serverClock: ServerClock, deviceClock: Clock): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val sessionId = auth.sessionId
                val token = auth.token().takeIf { auth.sessionId == sessionId }
                val original = chain.request()
                val request = original.newBuilder()
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                val response = chain.proceed(request)
                if (response.code != 401 || token == null || auth.sessionId != sessionId) {
                    return@addInterceptor response
                }

                val fresh = try {
                    auth.token(forceRefresh = true)
                } catch (error: IOException) {
                    response.close()
                    throw error
                }
                if (auth.sessionId != sessionId) return@addInterceptor response
                response.close()
                val retry = original.newBuilder()
                    .apply { if (fresh != null) header("Authorization", "Bearer $fresh") }
                    .build()
                val retried = chain.proceed(retry)
                if (retried.code == 401 && auth.sessionId == sessionId) {
                    try {
                        runBlocking { auth.signOut() }
                    } catch (error: IOException) {
                        retried.close()
                        throw error
                    }
                }
                retried
            }
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).also {
                    serverClock.onServerDate(it.header("Date"), deviceClock.instant())
                }
            }
            .build()

    private fun AuthTokenProvider.token(forceRefresh: Boolean = false): String? = try {
        runBlocking { idToken(forceRefresh) }
    } catch (error: FirebaseException) {
        throw IOException("Could not get an ID token", error)
    }

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json, BuildConfig.SERVER_URL)

    internal fun retrofit(client: OkHttpClient, json: Json, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun meApi(retrofit: Retrofit): MeApi = retrofit.create(MeApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
interface MeRepositoryModule {
    @Binds fun repository(implementation: RetrofitMeRepository): MeRepository
    @Binds fun walletRepository(implementation: RetrofitWalletRepository): WalletRepository
    @Binds fun walletAddedStore(implementation: DataStoreWalletAdded): WalletAddedStore
}
