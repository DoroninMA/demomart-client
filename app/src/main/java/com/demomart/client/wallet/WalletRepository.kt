package com.demomart.client.wallet

import com.demomart.client.net.ApiResult
import com.demomart.client.net.MeApi
import com.demomart.client.net.request
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface WalletRepository {
    suspend fun enabled(): Boolean
    suspend fun saveJwt(): ApiResult<String>
}

@Serializable
data class WalletStatus(val enabled: Boolean)

@Serializable
data class WalletSave(val jwt: String)

class RetrofitWalletRepository @Inject constructor(
    private val api: MeApi,
    private val json: Json,
) : WalletRepository {
    override suspend fun enabled(): Boolean =
        (json.request { api.walletStatus() } as? ApiResult.Ok)?.value?.enabled == true

    override suspend fun saveJwt(): ApiResult<String> =
        when (val result = json.request(readServerErrors = true) { api.saveWallet() }) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.jwt)
            is ApiResult.Err -> ApiResult.Err(result.code)
        }
}
