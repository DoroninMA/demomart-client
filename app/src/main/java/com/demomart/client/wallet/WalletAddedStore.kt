package com.demomart.client.wallet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

interface WalletAddedStore {
    suspend fun contains(card: String): Boolean
    suspend fun markAdded(card: String)
}

class DataStoreWalletAdded @Inject constructor(
    private val store: DataStore<Preferences>,
) : WalletAddedStore {
    override suspend fun contains(card: String): Boolean = try {
        card in store.data.first()[WALLET_ADDED].orEmpty()
    } catch (_: IOException) {
        false
    }

    override suspend fun markAdded(card: String) {
        store.edit { it[WALLET_ADDED] = it[WALLET_ADDED].orEmpty() + card }
    }

    private companion object {
        val WALLET_ADDED = stringSetPreferencesKey("wallet_added")
    }
}
