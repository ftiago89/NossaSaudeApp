package com.example.nossasaudeapp.di

import com.example.nossasaudeapp.BuildConfig
import com.example.nossasaudeapp.data.remote.ErrorHandlingInterceptor
import com.example.nossasaudeapp.data.remote.HeadersInterceptor
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.api.MembersApi
import com.example.nossasaudeapp.data.remote.api.S3UploadApi
import com.example.nossasaudeapp.data.remote.api.SyncApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class S3Client

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @Named("familyId")
    fun provideFamilyId(): String = BuildConfig.FAMILY_ID

    @Provides
    @Singleton
    @Named("apiKey")
    fun provideApiKey(): String = BuildConfig.API_KEY

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideLogger(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(
        @Named("apiKey") apiKey: String,
        @Named("familyId") familyId: String,
        logger: HttpLoggingInterceptor,
        json: Json,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HeadersInterceptor(apiKey, familyId))
        .addInterceptor(ErrorHandlingInterceptor(json))
        .addInterceptor(logger)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @S3Client
    fun provideS3Client(
        logger: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @ApiClient client: OkHttpClient,
        @Named("baseUrl") baseUrl: String,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    fun provideMembersApi(retrofit: Retrofit): MembersApi = retrofit.create(MembersApi::class.java)

    @Provides
    fun provideConsultationsApi(retrofit: Retrofit): ConsultationsApi = retrofit.create(ConsultationsApi::class.java)

    @Provides
    fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun provideS3UploadApi(@S3Client client: OkHttpClient, json: Json): S3UploadApi {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://s3.amazonaws.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(S3UploadApi::class.java)
    }
}
