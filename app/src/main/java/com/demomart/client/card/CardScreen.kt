package com.demomart.client.card

import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demomart.client.R
import com.demomart.client.anim.AnimatedBalance
import com.demomart.client.ui.theme.Cream
import com.demomart.client.ui.theme.DeepGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(viewModel: CardViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val window = LocalActivity.current?.window
    val qrSize = 256.dp
    val pixels = with(LocalDensity.current) { qrSize.roundToPx() }
    val token = state.token
    val qr by produceState<ImageBitmap?>(null, token, pixels) {
        value = token?.let { withContext(Dispatchers.Default) { qrBitmap(it, pixels) } }
    }

    LifecycleResumeEffect(viewModel, window) {
        val nfc = context.getSystemService(NfcManager::class.java)?.defaultAdapter
        val hce = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        viewModel.setNfc(when {
            !hce || nfc == null -> NfcState.Unsupported
            nfc.isEnabled -> NfcState.Ready
            else -> NfcState.Disabled
        })
        viewModel.onVisible()

        val brightness = window?.attributes?.screenBrightness
        val keepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasKeptOn = (window?.attributes?.flags ?: 0) and keepScreenOn != 0
        window?.addFlags(keepScreenOn)
        window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
        onPauseOrDispose {
            viewModel.onHidden()
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                if (!wasKeptOn) it.clearFlags(keepScreenOn)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My card") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(DeepGreen, Color(0xFF2F7A5F))))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(painterResource(R.drawable.ic_demomart_logo), null, Modifier.size(52.dp))
                    Text(state.cardDisplay, style = MaterialTheme.typography.titleMedium, color = Cream)
                    val balance = state.balance
                    if (balance == null) {
                        Text("—", style = MaterialTheme.typography.displayMedium, color = Cream)
                    } else {
                        ProvideTextStyle(MaterialTheme.typography.displayMedium) {
                            AnimatedBalance(balance, state.lastEffect) { viewModel.consumeEffect(it.key) }
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val bitmap = qr
                    if (bitmap != null && token != null) {
                        Image(bitmap, "Loyalty QR code", Modifier.size(qrSize))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(progress = { state.secondsLeft / 30f }, modifier = Modifier.size(20.dp))
                            Text("New code in ${state.secondsLeft}s", color = DeepGreen)
                        }
                    } else {
                        Text("Card unavailable", modifier = Modifier.padding(24.dp), color = DeepGreen)
                    }
                }
            }
            when (state.nfc) {
                NfcState.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_nfc), null)
                    Text("or hold your phone near the register")
                }
                NfcState.Disabled -> TextButton({ context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text("Turn on NFC to pay by tap")
                }
                NfcState.Unsupported -> Unit
            }
        }
    }
}
