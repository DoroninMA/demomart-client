package com.demomart.client.auth

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.client.BuildConfig
import com.demomart.client.R

@Composable
fun LoginScreen(viewModel: LoginViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val signingUp = state.mode == LoginMode.SignUp
    val enabled = !state.busy
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(painterResource(R.drawable.ic_demomart_logo), null, Modifier.size(72.dp))
        Text("Welcome to DemoMart", style = MaterialTheme.typography.headlineMedium)
        Text("Your points and loyalty card in one place.", style = MaterialTheme.typography.bodyMedium)
        if (BuildConfig.AUTH_MODE == "firebase" && activity != null) {
            OutlinedButton({ viewModel.google(activity) }, Modifier.fillMaxWidth(), enabled = enabled) {
                Text("Continue with Google")
            }
            HorizontalDivider()
        }
        if (signingUp) {
            OutlinedTextField(
                state.name, viewModel::setName, Modifier.fillMaxWidth(),
                enabled = enabled, label = { Text("Name") }, singleLine = true,
            )
        }
        OutlinedTextField(
            state.email, viewModel::setEmail, Modifier.fillMaxWidth(),
            enabled = enabled, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            state.password, viewModel::setPassword, Modifier.fillMaxWidth(),
            enabled = enabled, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(viewModel::submitEmail, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) {
            Text(if (state.busy) "Signing in…" else if (signingUp) "Create account" else "Sign in")
        }
        TextButton(viewModel::toggleMode, enabled = enabled) {
            Text(if (signingUp) "I have an account" else "Create account")
        }
        if (BuildConfig.AUTH_MODE == "fake") {
            Text("Demo accounts", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in listOf("Anna", "Ben", "Chloe")) {
                    AssistChip(onClick = { viewModel.demo(name) }, label = { Text(name) }, enabled = enabled)
                }
            }
        }
    }
}
