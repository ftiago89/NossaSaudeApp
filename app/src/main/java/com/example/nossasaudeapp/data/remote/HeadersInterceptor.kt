package com.example.nossasaudeapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class HeadersInterceptor(
    private val apiKey: String,
    private val familyId: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = original.newBuilder()
            .header("x-api-key", apiKey)
            .header("X-Family-Id", familyId)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
