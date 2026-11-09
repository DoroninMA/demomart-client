package com.demomart.client.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demomart.client.account.AccountRepository
import com.demomart.client.net.ApiResult
import com.demomart.client.net.errorText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMode { SignIn, SignUp }

data class LoginState(
    val mode: LoginMode = LoginMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthTokenProvider,
    private val account: AccountRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()

    fun setName(name: String) = mutableState.update { it.copy(name = name, error = null) }
    fun setEmail(email: String) = mutableState.update { it.copy(email = email, error = null) }
    fun setPassword(password: String) = mutableState.update { it.copy(password = password, error = null) }

    fun toggleMode() = mutableState.update {
        val mode = if (it.mode == LoginMode.SignIn) LoginMode.SignUp else LoginMode.SignIn
        it.copy(mode = mode, error = null)
    }

    fun submitEmail() {
        val input = state.value
        val signingUp = input.mode == LoginMode.SignUp
        if (!input.email.contains('@') || input.password.isEmpty() || signingUp && input.name.isBlank()) {
            mutableState.update { it.copy(error = "Fill in all fields and enter a valid email") }
            return
        }
        signIn {
            if (signingUp) auth.signUpEmail(input.name, input.email, input.password)
            else auth.signInEmail(input.email, input.password)
        }
    }

    fun google(activity: Activity) = signIn { auth.signInGoogle(activity) }
    fun demo(name: String) = signIn { auth.signInEmail("${name.lowercase()}@demo.test", "") }

    private fun signIn(action: suspend () -> ApiResult<Unit>) {
        if (state.value.busy || state.value.done) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = try {
                when (val login = action()) {
                    is ApiResult.Ok -> account.openSession()
                    is ApiResult.Err -> login
                }.also {
                    if (it is ApiResult.Err && auth.signedIn.value) auth.signOut()
                }
            } catch (_: IOException) {
                ApiResult.Err("storage")
            }
            mutableState.update {
                when (result) {
                    is ApiResult.Ok -> it.copy(busy = false, password = "", done = true)
                    is ApiResult.Err -> it.copy(busy = false, error = errorText(result.code))
                }
            }
        }
    }
}
