package com.demomart.client.net

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

const val HISTORY_PAGE = 30

interface MeRepository {
    suspend fun openSession(): ApiResult<MeSession>
    suspend fun me(): ApiResult<Me>
    // before: the last item already shown, to fetch the page after it
    suspend fun history(limit: Int = HISTORY_PAGE, before: HistoryItem? = null): ApiResult<List<HistoryItem>>
    suspend fun putFcmToken(token: String): ApiResult<Unit>
}

@Singleton
class RetrofitMeRepository @Inject constructor(
    private val api: MeApi,
    private val json: Json,
) : MeRepository {
    override suspend fun openSession() = json.request { api.openSession() }
    override suspend fun me() = json.request { api.me() }
    override suspend fun history(limit: Int, before: HistoryItem?) =
        json.request { api.history(limit, before?.at, before?.saleId, before?.kind) }
    override suspend fun putFcmToken(token: String) = json.request(emptyBody = Unit) { api.putFcmToken(FcmToken(token)) }
}

internal suspend fun <T : Any> Json.request(
    emptyBody: T? = null,
    readServerErrors: Boolean = false,
    call: suspend () -> Response<T>,
): ApiResult<T> = try {
    val response = call()
    if (response.isSuccessful) {
        (response.body() ?: emptyBody)?.let { ApiResult.Ok(it) } ?: ApiResult.Err("server")
    } else {
        failure(response, readServerErrors)
    }
} catch (_: SerializationException) {
    ApiResult.Err("server")
} catch (_: IOException) {
    ApiResult.Err("network")
}

private fun Json.failure(response: Response<*>, readServerErrors: Boolean): ApiResult.Err {
    val fallback = if (response.code() == 401) "unauthenticated" else "server"
    val body = response.errorBody()?.use { it.string() }
    if ((!readServerErrors && response.code() >= 500) || body.isNullOrBlank()) return ApiResult.Err(fallback)
    val error = try {
        decodeFromString<ErrorBody>(body)
    } catch (_: SerializationException) {
        return ApiResult.Err(fallback)
    }
    return ApiResult.Err(error.error, error.message ?: errorText(error.error))
}
