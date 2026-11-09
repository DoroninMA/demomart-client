package com.demomart.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.demomart.client.account.AccountRepository
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.auth.LoginScreen
import com.demomart.client.ui.theme.DemoMartTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: AuthTokenProvider
    @Inject lateinit var account: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoMartTheme {
                Surface(Modifier.fillMaxSize()) { ClientNavigation() }
            }
        }
    }

    @Composable
    private fun ClientNavigation() {
        val nav = rememberNavController()
        val signedIn by auth.signedIn.collectAsStateWithLifecycle()
        var startRoute by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            if (auth.signedIn.value) account.ensureCredentials()
            startRoute = if (auth.signedIn.value) "home" else "login"
        }
        val start = startRoute
        if (start == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        LaunchedEffect(signedIn) {
            if (!signedIn && nav.currentDestination?.route != "login") {
                nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                account.clear()
            }
        }
        NavHost(navController = nav, startDestination = start) {
            composable("login") {
                LoginScreen(hiltViewModel()) {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                }
            }
            composable("home") {
                val profile by account.account.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                Column(Modifier.safeDrawingPadding()) {
                    Text("Hi, ${profile?.displayName ?: "there"}")
                    Button({ nav.navigate("card") }) { Text("My card") }
                    TextButton({ scope.launch { auth.signOut() } }) { Text("Sign out") }
                }
            }
            composable("card") {
                TextButton({ nav.popBackStack() }, Modifier.safeDrawingPadding()) { Text("Back") }
            }
        }
    }
}
