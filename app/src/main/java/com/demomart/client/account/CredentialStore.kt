package com.demomart.client.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.demomart.client.token.CardCredentials
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface CredentialStore {
    val credentials: Flow<CardCredentials?>
    suspend fun load(): CardCredentials?
    suspend fun save(card: CardCredentials)
    suspend fun clear()
}

interface SecretCipher {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

class KeystoreCredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : CredentialStore {
    override val credentials: Flow<CardCredentials?> = dataStore.data
        .map { prefs ->
            val encrypted = prefs[SECRET] ?: return@map null
            try {
                CardCredentials(
                    checkNotNull(prefs[CARD]), checkNotNull(prefs[DISPLAY]), checkNotNull(prefs[NAME]),
                    cipher.decrypt(Base64.getDecoder().decode(encrypted)).decodeToString(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                dataStore.edit { if (it[SECRET] == encrypted) wipe(it) }
                null
            }
        }
        .catch { if (it is IOException) emit(null) else throw it }

    override suspend fun load(): CardCredentials? = credentials.first()

    override suspend fun save(card: CardCredentials) {
        val encrypted = Base64.getEncoder().encodeToString(cipher.encrypt(card.secretBase32.encodeToByteArray()))
        dataStore.edit {
            it[CARD] = card.cardNumber
            it[DISPLAY] = card.cardDisplay
            it[NAME] = card.displayName
            it[SECRET] = encrypted
        }
    }

    override suspend fun clear() { dataStore.edit(::wipe) }

    private fun wipe(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs.remove(CARD)
        prefs.remove(DISPLAY)
        prefs.remove(NAME)
        prefs.remove(SECRET)
    }

    private companion object {
        val CARD = stringPreferencesKey("card_number")
        val DISPLAY = stringPreferencesKey("card_display")
        val NAME = stringPreferencesKey("display_name")
        val SECRET = stringPreferencesKey("card_secret_encrypted")
    }
}

class AndroidSecretCipher : SecretCipher {
    @Synchronized
    override fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
        return cipher.iv + cipher.doFinal(data)
    }

    @Synchronized
    override fun decrypt(data: ByteArray): ByteArray {
        val key = checkNotNull(existingKey())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance(KEYSTORE).run {
        load(null)
        getKey(ALIAS, null) as? SecretKey
    }

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "demomart_card_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
