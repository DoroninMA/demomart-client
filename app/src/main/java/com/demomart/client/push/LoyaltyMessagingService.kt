package com.demomart.client.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.demomart.client.MainActivity
import com.demomart.client.R
import com.demomart.client.account.AccountRepository
import com.demomart.client.account.AccountState
import com.demomart.client.account.CredentialStore
import com.demomart.client.anim.effectLabel
import com.demomart.client.auth.AuthTokenProvider
import com.demomart.client.net.ApiResult
import com.demomart.client.ui.formatPoints
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val OPEN_CARD = "com.demomart.client.OPEN_CARD"

@AndroidEntryPoint
class LoyaltyMessagingService : FirebaseMessagingService() {
    @Inject lateinit var auth: AuthTokenProvider
    @Inject lateinit var store: CredentialStore
    @Inject lateinit var account: AccountRepository
    @Inject lateinit var registrar: PushRegistrar

    override fun onNewToken(token: String) {
        if (auth.signedIn.value) runBlocking {
            if (store.load() != null) registrar.register()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "balance_changed" || !auth.signedIn.value) return
        val saleId = message.data["sale_id"]?.toLongOrNull() ?: return
        val delta = message.data["delta"]?.toLongOrNull() ?: return
        runBlocking {
            withTimeoutOrNull(8000) {
                if (store.load() == null) return@withTimeoutOrNull
                if (account.refreshFromPush(saleId, delta) !is ApiResult.Ok) return@withTimeoutOrNull
                withContext(Dispatchers.Main.immediate) {
                    val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                    val profile = account.account.value
                    val newest = profile?.history?.firstOrNull()
                    if (!foreground && auth.signedIn.value && profile != null &&
                        newest?.saleId == saleId && newest.pointsDelta == delta
                    ) {
                        notifyBalance(profile)
                    }
                }
            }
        }
    }

    private fun notifyBalance(profile: AccountState) {
        val effect = profile.lastEffect ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Balance updates", NotificationManager.IMPORTANCE_DEFAULT))
        val target = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(OPEN_CARD, true)
        val pending = PendingIntent.getActivity(this, 0, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = "${effectLabel(effect).first()} · balance ${formatPoints(profile.balance)}"
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_card)
            .setContentTitle("Points updated")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "balance"
        internal const val NOTIFICATION_ID = 1
    }
}
