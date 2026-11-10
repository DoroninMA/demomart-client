package com.demomart.client.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.demomart.client.account.CredentialStore
import com.demomart.client.token.ServerClock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LoyaltyHceService : HostApduService() {
    @Inject lateinit var store: CredentialStore
    @Inject lateinit var serverClock: ServerClock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        pending?.cancel()
        pending = scope.launch {
            val response = withContext(Dispatchers.IO) {
                serverClock.restore()
                HceResponder.respond(commandApdu, store.load(), serverClock.now())
            }
            sendResponseApdu(response)
        }
        return null
    }

    override fun onDeactivated(reason: Int) { pending?.cancel() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
