package com.example.nossasaudeapp.data.remote.api

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Url

interface S3UploadApi {
    @PUT
    suspend fun upload(
        @Url uploadUrl: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody,
    )
}
