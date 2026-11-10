package com.demomart.client

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.demomart.client.account.AccountRepository
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.auth.LoginScreen
import com.demomart.client.home.HomeScreen
import com.demomart.client.card.CardScreen
import com.demomart.client.push.LoyaltyMessagingService
import com.demomart.client.push.OPEN_CARD
import com.demomart.client.ui.theme.DemoMartTheme
import com.demomart.client.wallet.PayResults
import com.demomart.client.wallet.REQUEST_CODE_SAVE
import com.google.firebase.FirebaseApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: AuthTokenProvider
    @Inject lateinit var account: AccountRepository
    @Inject lateinit var payResults: PayResults

    private val openCard = MutableStateFlow(false)

    @Deprecated("Google Wallet delivers its save result through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SAVE) payResults.emit(resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCard.value = intent.getBooleanExtra(OPEN_CARD, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCard.value = intent.getBooleanExtra(OPEN_CARD, false)
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
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val shouldOpenCard by openCard.collectAsStateWithLifecycle()
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(signedIn) {
            if (!signedIn) {
                account.clear()
                getSystemService(NotificationManager::class.java).cancel(LoyaltyMessagingService.NOTIFICATION_ID)
            }
        }
        LaunchedEffect(signedIn, route) {
            if (!signedIn && route != null && route != "login") {
                nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
            }
        }
        LaunchedEffect(shouldOpenCard, signedIn, route) {
            if (shouldOpenCard && signedIn && route in listOf("home", "card")) {
                openCard.value = false
                intent.removeExtra(OPEN_CARD)
                if (route == "home") nav.navigate("card")
            }
        }
        LaunchedEffect(signedIn, route) {
            if (Build.VERSION.SDK_INT >= 33 && signedIn && route in listOf("home", "card") &&
                FirebaseApp.getApps(this@MainActivity).isNotEmpty()
            ) {
                val prefs = getSharedPreferences("notifications", MODE_PRIVATE)
                if (!prefs.getBoolean("requested", false)) {
                    prefs.edit().putBoolean("requested", true).apply()
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        NavHost(navController = nav, startDestination = start) {
            composable("login") {
                LoginScreen(hiltViewModel()) {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                }
            }
            composable(
                "home",
                exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
                popEnterTransition = { EnterTransition.None },
            ) {
                val scope = rememberCoroutineScope()
                HomeScreen(hiltViewModel(), onCard = { nav.navigate("card") }) {
                    scope.launch { auth.signOut() }
                }
            }
            composable(
                "card",
                // the card slides over home, home stays put underneath
                enterTransition = { slideInHorizontally(tween(450)) { it } },
                popExitTransition = { slideOutHorizontally(tween(450)) { it } },
            ) {
                CardScreen(hiltViewModel()) { nav.popBackStack() }
            }
        }
    }
}
