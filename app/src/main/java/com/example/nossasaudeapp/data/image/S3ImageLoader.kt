package com.example.nossasaudeapp.data.image

import com.example.nossasaudeapp.BuildConfig
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.dto.PresignedImageDto
import com.example.nossasaudeapp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves stable s3Keys to short-lived presigned URLs, caching them in-memory.
 * Presigned read URLs are valid for 15 minutes — we refresh after 10 minutes.
 */
@Singleton
class S3ImageLoader @Inject constructor(
    private val api: ConsultationsApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private data class Entry(val url: String, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Entry>()
    private val ttlMillis = 10 * 60 * 1000L

    /** Returns a cached presigned URL for [s3Key] without making any network call, or null if expired/absent. */
    fun cachedUrl(s3Key: String): String? {
        val entry = cache[s3Key] ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        return if (now - entry.fetchedAt < ttlMillis) entry.url else null
    }

    /** Resolve a presigned URL for a given s3Key by fetching the consultation's image manifest. */
    suspend fun presignedUrl(remoteConsultationId: String, s3Key: String): String? = withContext(io) {
        val cached = mutex.withLock { cache[s3Key] }
        val now = Clock.System.now().toEpochMilliseconds()
        if (cached != null && now - cached.fetchedAt < ttlMillis) return@withContext cached.url

        val response = api.getImages(remoteConsultationId)
        val all: List<PresignedImageDto> =
            response.prescriptions + response.exams.flatMap { it.images }
        mutex.withLock {
            all.forEach { cache[it.s3Key] = Entry(it.url.toLocalUrl(), now) }
        }
        mutex.withLock { cache[s3Key]?.url }
    }

    suspend fun clear() = mutex.withLock { cache.clear() }

    companion object {
        // LocalStack generates presigned URLs with "localhost" as host, which neither the emulator
        // nor a physical device can reach. Replace with the same host used by API_BASE_URL_DEBUG
        // so the correct address is used regardless of device type (10.0.2.2 for emulator,
        // 192.168.x.x for a physical device on the same network).
        // On release builds against real AWS S3, URLs never contain "localhost" — no-op.
        private fun String.toLocalUrl(): String {
            if (!BuildConfig.DEBUG || !contains("://localhost")) return this
            val apiHost = try {
                java.net.URL(BuildConfig.API_BASE_URL).host
            } catch (_: Exception) {
                "10.0.2.2"
            }
            return replace("://localhost", "://$apiHost")
        }
    }
}
