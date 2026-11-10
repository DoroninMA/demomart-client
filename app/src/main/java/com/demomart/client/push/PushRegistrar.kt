package com.demomart.client.push

import com.demomart.client.net.MeRepository
import com.google.firebase.FirebaseException
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

fun interface FcmTokenSource {
    suspend fun token(): String?
}

object NoopFcmTokenSource : FcmTokenSource {
    override suspend fun token(): String? = null
}

class FirebaseFcmTokenSource : FcmTokenSource {
    override suspend fun token(): String = FirebaseMessaging.getInstance().token.await()
}

@Singleton
class PushRegistrar @Inject constructor(
    private val me: MeRepository,
    private val tokenSource: FcmTokenSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun registerInBackground() {
        if (tokenSource !== NoopFcmTokenSource) scope.launch { register() }
    }

    suspend fun register() {
        withTimeoutOrNull(8000) {
            mutex.withLock {
                try {
                    tokenSource.token()?.let { me.putFcmToken(it) }
                } catch (_: IOException) {
                    // Registration is retried on the next session or token change.
                } catch (_: FirebaseException) {
                    // Registration is retried on the next session or token change.
                }
            }
        }
    }
}
