package com.example.nossasaudeapp.data.remote

import com.example.nossasaudeapp.data.remote.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

class ErrorHandlingInterceptor(
    private val json: Json,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response
        val raw = response.peekBody(Long.MAX_VALUE).string()
        val payload = runCatching {
            if (raw.isNotBlank()) json.decodeFromString(ApiErrorDto.serializer(), raw) else null
        }.getOrNull()
        throw ApiException(
            httpStatus = response.code,
            errorCode = payload?.errorCode,
            errorMessage = payload?.errorMessage ?: response.message,
            payload = payload,
        )
    }
}
