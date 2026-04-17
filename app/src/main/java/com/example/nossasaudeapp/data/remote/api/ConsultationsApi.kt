package com.example.nossasaudeapp.data.remote.api

import com.example.nossasaudeapp.data.remote.dto.ConsultationCreateDto
import com.example.nossasaudeapp.data.remote.dto.ConsultationDto
import com.example.nossasaudeapp.data.remote.dto.ConsultationPatchDto
import com.example.nossasaudeapp.data.remote.dto.ImagesResponseDto
import com.example.nossasaudeapp.data.remote.dto.UploadUrlRequestDto
import com.example.nossasaudeapp.data.remote.dto.UploadUrlResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ConsultationsApi {

    @POST("consultations")
    suspend fun create(@Body body: ConsultationCreateDto): ConsultationDto

    @GET("consultations")
    suspend fun list(
        @Query("memberId") memberId: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("doctor") doctor: String? = null,
        @Query("tag") tag: String? = null,
    ): List<ConsultationDto>

    @GET("consultations/{id}")
    suspend fun getById(@Path("id") id: String): ConsultationDto

    @PATCH("consultations/{id}")
    suspend fun update(@Path("id") id: String, @Body body: ConsultationPatchDto): ConsultationDto

    @DELETE("consultations/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("consultations/{id}/upload-url")
    suspend fun requestUploadUrl(
        @Path("id") id: String,
        @Body body: UploadUrlRequestDto,
    ): UploadUrlResponseDto

    @GET("consultations/{id}/images")
    suspend fun getImages(@Path("id") id: String): ImagesResponseDto
}
