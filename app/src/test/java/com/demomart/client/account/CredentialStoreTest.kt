package com.demomart.client.account

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import com.demomart.client.token.annaCredentials
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CredentialStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val file by lazy { temp.root.resolve("client.preferences_pb") }
    private val cipher = object : SecretCipher {
        var broken = false
        override fun encrypt(data: ByteArray) = data.map { (it.toInt() xor 0x55).toByte() }.toByteArray()
        override fun decrypt(data: ByteArray): ByteArray {
            check(!broken)
            return encrypt(data)
        }
    }

    private fun TestScope.store() = KeystoreCredentialStore(
        DataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = backgroundScope,
        ), cipher,
    )

    @Test fun saveLoadRoundTrip() = runTest {
        val store = store()
        store.save(annaCredentials)
        assertEquals(annaCredentials, store.load())
    }

    @Test fun secretNotStoredInPlainText() = runTest {
        store().save(annaCredentials)
        assertFalse(file.readBytes().decodeToString().contains(annaCredentials.secretBase32))
    }

    @Test fun clearWipes() = runTest {
        val store = store()
        store.save(annaCredentials)
        store.clear()
        assertNull(store.load())
    }

    @Test fun undecryptableSecretClears() = runTest {
        val store = store()
        store.save(annaCredentials)
        cipher.broken = true
        assertNull(store.load())
        cipher.broken = false
        assertNull(store.load())
    }
}
