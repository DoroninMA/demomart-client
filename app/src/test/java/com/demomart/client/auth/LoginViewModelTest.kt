package com.demomart.client.auth

import com.demomart.client.account.AccountRepository
import com.demomart.client.net.ApiResult
import com.demomart.client.testing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeAuth()
    private val me = FakeMeRepository()
    private val viewModel = LoginViewModel(auth, AccountRepository(me, MemoryCredentials(), serverClock()))

    private fun submit() {
        viewModel.setEmail("anna@demo.test")
        viewModel.setPassword("example-password")
        viewModel.submitEmail()
    }

    @Test fun loginSuccessOpensSession() = runTest {
        submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.done)
        assertEquals(1, me.sessions)
    }

    @Test fun loginSessionFailureSignsOut() = runTest {
        me.sessionError = ApiResult.Err("email_taken")
        submit()
        advanceUntilIdle()
        assertEquals(1, auth.signOuts)
        assertEquals("This email is already used by another account", viewModel.state.value.error)
    }

    @Test fun signUpPassesName() = runTest {
        viewModel.toggleMode()
        viewModel.setName("Dana")
        submit()
        advanceUntilIdle()
        assertEquals("Dana", auth.name)
        assertTrue(viewModel.state.value.done)
    }
}
