package com.demomart.client.di

import android.content.Context
import com.demomart.client.BuildConfig
import com.demomart.client.account.CredentialStore
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.auth.FakeTokenProvider
import com.demomart.client.auth.FirebaseTokenProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun auth(@ApplicationContext context: Context, store: CredentialStore): AuthTokenProvider =
        if (BuildConfig.AUTH_MODE == "fake") {
            FakeTokenProvider(store, context.getSharedPreferences("demo_session", Context.MODE_PRIVATE))
        } else {
            FirebaseTokenProvider(FirebaseAuth.getInstance(), store, context)
        }
}
