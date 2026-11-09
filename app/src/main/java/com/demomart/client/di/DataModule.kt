package com.demomart.client.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.demomart.client.account.AndroidSecretCipher
import com.demomart.client.account.CredentialStore
import com.demomart.client.account.KeystoreCredentialStore
import com.demomart.client.token.OffsetStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.clientDataStore by preferencesDataStore("client")

class DataStoreOffsetStore(private val dataStore: DataStore<Preferences>) : OffsetStore {
    private val key = longPreferencesKey("server_clock_offset")

    override suspend fun load(): Long = dataStore.data.first()[key] ?: 0
    override suspend fun save(offsetSeconds: Long) {
        dataStore.edit { it[key] = offsetSeconds }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.clientDataStore

    @Provides @Singleton
    fun credentials(dataStore: DataStore<Preferences>): CredentialStore =
        KeystoreCredentialStore(dataStore, AndroidSecretCipher())

    @Provides @Singleton
    fun offsetStore(dataStore: DataStore<Preferences>): OffsetStore = DataStoreOffsetStore(dataStore)

    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
