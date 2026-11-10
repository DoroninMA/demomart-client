package com.demomart.client.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.demomart.client.account.CredentialStore
import com.demomart.client.net.ApiResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface AuthTokenProvider {
    val signedIn: StateFlow<Boolean>
    val sessionId: String? get() = null
    suspend fun signInEmail(email: String, password: String): ApiResult<Unit>
    suspend fun signUpEmail(name: String, email: String, password: String): ApiResult<Unit>
    suspend fun signInGoogle(activity: Activity): ApiResult<Unit>
    suspend fun idToken(forceRefresh: Boolean = false): String?
    suspend fun signOut()
}

class FakeTokenProvider(
    private val store: CredentialStore,
    private val preferences: SharedPreferences,
) : AuthTokenProvider {
    @Volatile private var token = preferences.getString("token", null)
    override val sessionId: String? get() = token
    private val state = MutableStateFlow(token != null)
    override val signedIn = state.asStateFlow()

    override suspend fun signInEmail(email: String, password: String): ApiResult<Unit> =
        signUpEmail(email.trim().substringBefore('@').replaceFirstChar { it.titlecase() }, email, password)

    override suspend fun signUpEmail(name: String, email: String, password: String): ApiResult<Unit> {
        val address = email.trim().lowercase()
        val local = address.substringBefore('@')
        val next = "fake:seed-$local:$address:${name.trim()}"
        withContext(Dispatchers.IO) { preferences.edit().putString("token", next).commit() }
        token = next
        state.value = true
        return ApiResult.Ok(Unit)
    }

    override suspend fun signInGoogle(activity: Activity): ApiResult<Unit> = ApiResult.Err("unsupported")
    override suspend fun idToken(forceRefresh: Boolean): String? = token

    override suspend fun signOut() {
        store.clear()
        withContext(Dispatchers.IO) { preferences.edit().remove("token").commit() }
        token = null
        state.value = false
    }
}

class FirebaseTokenProvider(
    private val auth: FirebaseAuth,
    private val store: CredentialStore,
    private val context: Context,
) : AuthTokenProvider {
    override val sessionId: String? get() = auth.currentUser?.uid
    private val state = MutableStateFlow(auth.currentUser != null)
    override val signedIn = state.asStateFlow()

    init {
        auth.addAuthStateListener { state.value = it.currentUser != null }
    }

    override suspend fun signInEmail(email: String, password: String) = firebaseResult {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signUpEmail(name: String, email: String, password: String) = firebaseResult {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email.trim(), password).await().user)
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        user.getIdToken(true).await()
    }

    override suspend fun signInGoogle(activity: Activity): ApiResult<Unit> {
        val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resource == 0) return ApiResult.Err("unsupported")
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(resource))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val id = try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) return ApiResult.Err("unsupported")
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GetCredentialCancellationException) {
            return ApiResult.Err("google_cancelled")
        } catch (_: GetCredentialException) {
            return ApiResult.Err("unsupported")
        } catch (_: GoogleIdTokenParsingException) {
            return ApiResult.Err("invalid_login")
        }
        return firebaseResult {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(id, null)).await()
            nameChecked.set(false)
        }
    }

    // Firebase takes the Google name only on first sign-in, refresh it once per launch
    private val nameChecked = AtomicBoolean(false)

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val user = auth.currentUser ?: return null
        val renamed = nameChecked.compareAndSet(false, true) && syncGoogleName(user)
        return user.getIdToken(forceRefresh || renamed).await()?.token
    }

    private suspend fun syncGoogleName(user: FirebaseUser): Boolean = try {
        user.reload().await()
        val google = user.providerData
            .firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            ?.displayName?.trim()
        if (google.isNullOrEmpty() || google == user.displayName) {
            false
        } else {
            user.updateProfile(userProfileChangeRequest { displayName = google }).await()
            true
        }
    } catch (_: FirebaseException) {
        nameChecked.set(false)
        false
    }

    override suspend fun signOut() {
        store.clear()
        auth.signOut()
        state.value = false
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // already signed out locally
        }
    }

    private suspend fun firebaseResult(block: suspend () -> Unit): ApiResult<Unit> = try {
        block()
        state.value = auth.currentUser != null
        ApiResult.Ok(Unit)
    } catch (_: FirebaseNetworkException) {
        ApiResult.Err("network")
    } catch (error: FirebaseAuthException) {
        ApiResult.Err(
            when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "email_taken"
                "ERROR_WEAK_PASSWORD" -> "weak_password"
                else -> "invalid_login"
            },
        )
    }
}
