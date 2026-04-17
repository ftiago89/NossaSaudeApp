package com.example.nossasaudeapp.data.remote.api

import com.example.nossasaudeapp.data.remote.dto.SyncResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SyncApi {

    @GET("sync")
    suspend fun sync(@Query("since") since: String? = null): SyncResponseDto
}
