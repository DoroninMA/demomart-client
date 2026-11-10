package com.demomart.client.net

import com.demomart.client.wallet.WalletSave
import com.demomart.client.wallet.WalletStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface MeApi {
    @POST("v1/me/session") suspend fun openSession(): Response<MeSession>
    @GET("v1/me") suspend fun me(): Response<Me>
    @GET("v1/me/history") suspend fun history(
        @Query("limit") limit: Int,
        @Query("before_at") beforeAt: String? = null,
        @Query("before_id") beforeId: Long? = null,
        @Query("before_kind") beforeKind: String? = null,
    ): Response<List<HistoryItem>>
    @PUT("v1/me/fcm-token") suspend fun putFcmToken(@Body token: FcmToken): Response<Unit>
    @GET("v1/me/wallet") suspend fun walletStatus(): Response<WalletStatus>
    @POST("v1/me/wallet/save") suspend fun saveWallet(): Response<WalletSave>
}
