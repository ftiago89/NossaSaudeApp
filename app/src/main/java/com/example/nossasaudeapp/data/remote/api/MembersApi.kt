package com.example.nossasaudeapp.data.remote.api

import com.example.nossasaudeapp.data.remote.dto.MemberCreateDto
import com.example.nossasaudeapp.data.remote.dto.MemberDto
import com.example.nossasaudeapp.data.remote.dto.MemberPatchDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface MembersApi {

    @POST("members")
    suspend fun create(@Body body: MemberCreateDto): MemberDto

    @GET("members")
    suspend fun list(): List<MemberDto>

    @GET("members/{id}")
    suspend fun getById(@Path("id") id: String): MemberDto

    @PATCH("members/{id}")
    suspend fun update(@Path("id") id: String, @Body body: MemberPatchDto): MemberDto

    @DELETE("members/{id}")
    suspend fun delete(@Path("id") id: String)
}
