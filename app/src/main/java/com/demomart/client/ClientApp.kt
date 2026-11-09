package com.demomart.client

import android.app.Application
import com.demomart.client.token.ServerClock
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ClientApp : Application() {
    @Inject lateinit var serverClock: ServerClock

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { serverClock.restore() }
    }
}
