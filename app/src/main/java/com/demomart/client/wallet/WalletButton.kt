package com.demomart.client.wallet

import android.app.Activity
import android.view.LayoutInflater
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.demomart.client.R
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

const val REQUEST_CODE_SAVE = 1000

interface PayLauncher {
    fun available(onResult: (Boolean) -> Unit)
    fun save(jwt: String)
}

class GooglePayLauncher(private val activity: Activity) : PayLauncher {
    private val client = Pay.getClient(activity)

    override fun available(onResult: (Boolean) -> Unit) {
        client.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnCompleteListener(activity) { task ->
                onResult(task.isSuccessful && task.result == PayApiAvailabilityStatus.AVAILABLE)
            }
    }

    override fun save(jwt: String) = client.savePassesJwt(jwt, activity, REQUEST_CODE_SAVE)
}

@Singleton
class PayResults @Inject constructor() {
    private val events = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val results = events.asSharedFlow()

    fun emit(resultCode: Int) {
        events.tryEmit(resultCode)
    }
}

@Composable
fun WalletButton(enabled: Boolean, added: Boolean, onClick: () -> Unit) {
    AndroidView(
        factory = { LayoutInflater.from(it).inflate(R.layout.add_to_googlewallet_button, null) },
        modifier = Modifier.padding(8.dp).size(width = 260.dp, height = 48.dp),
        update = { view ->
            view.isEnabled = enabled
            view.contentDescription = if (added) "Open again in Google Wallet" else "Add to Google Wallet"
            view.setOnClickListener { onClick() }
        },
    )
}
