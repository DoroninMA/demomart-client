package com.demomart.client.net

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

interface MeRepository {
    suspend fun openSession(): ApiResult<MeSession>
    suspend fun me(): ApiResult<Me>
    suspend fun history(limit: Int = 30): ApiResult<List<HistoryItem>>
    suspend fun putFcmToken(token: String): ApiResult<Unit>
}

@Singleton
class RetrofitMeRepository @Inject constructor(
    private val api: MeApi,
    private val json: Json,
) : MeRepository {
    override suspend fun openSession() = request { api.openSession() }
    override suspend fun me() = request { api.me() }
    override suspend fun history(limit: Int) = request { api.history(limit) }
    override suspend fun putFcmToken(token: String) = request(emptyBody = Unit) { api.putFcmToken(FcmToken(token)) }

    private suspend fun <T : Any> request(emptyBody: T? = null, call: suspend () -> Response<T>): ApiResult<T> =
        try {
            val response = call()
            if (response.isSuccessful) {
                (response.body() ?: emptyBody)?.let { ApiResult.Ok(it) } ?: ApiResult.Err("server")
            } else {
                failure(response)
            }
        } catch (_: SerializationException) {
            ApiResult.Err("server")
        } catch (_: IOException) {
            ApiResult.Err("network")
        }

    private fun failure(response: Response<*>): ApiResult.Err {
        val fallback = if (response.code() == 401) "unauthenticated" else "server"
        val body = response.errorBody()?.use { it.string() }
        if (response.code() >= 500 || body.isNullOrBlank()) return ApiResult.Err(fallback)
        val error = try {
            json.decodeFromString<ErrorBody>(body)
        } catch (_: SerializationException) {
            return ApiResult.Err(fallback)
        }
        return ApiResult.Err(error.error, error.message ?: errorText(error.error))
    }
}
