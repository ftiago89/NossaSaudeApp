package com.example.nossasaudeapp.data.repository

import android.content.Context
import android.net.Uri
import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.entity.PendingUploadEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.api.S3UploadApi
import com.example.nossasaudeapp.data.remote.dto.AddExamImageDto
import com.example.nossasaudeapp.data.remote.dto.AddPrescriptionImageDto
import com.example.nossasaudeapp.data.remote.dto.ConsultationPatchDto
import com.example.nossasaudeapp.data.remote.dto.RemoveExamImageDto
import com.example.nossasaudeapp.data.remote.dto.RemovePrescriptionImageDto
import com.example.nossasaudeapp.data.remote.dto.UploadUrlRequestDto
import com.example.nossasaudeapp.di.IoDispatcher
import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.domain.model.UploadType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consultationDao: ConsultationDao,
    private val pendingUploadDao: PendingUploadDao,
    private val api: ConsultationsApi,
    private val s3: S3UploadApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observePending(): Flow<List<PendingUploadEntity>> = pendingUploadDao.observeAll()

    /** Copy image from content Uri into app-private storage. Returns the absolute path. */
    suspend fun saveLocalCopy(source: Uri, subdir: String = "images"): String = withContext(io) {
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot open input stream for $source" }
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }

    /** Enqueue a prescription or exam image for later upload. Inserts local rows with PENDING status. */
    suspend fun enqueuePrescription(consultationId: String, localPath: String): String = withContext(io) {
        val placeholderKey = "local://${UUID.randomUUID()}"
        consultationDao.insertPrescriptionImages(
            listOf(
                PrescriptionImageEntity(
                    id = UUID.randomUUID().toString(),
                    consultationId = consultationId,
                    s3Key = placeholderKey,
                    localPath = localPath,
                    uploadedAt = null,
                    uploadStatus = UploadStatus.PENDING.name,
                ),
            ),
        )
        pendingUploadDao.insert(
            PendingUploadEntity(
                id = UUID.randomUUID().toString(),
                consultationId = consultationId,
                examId = null,
                type = UploadType.PRESCRIPTION.name,
                localPath = localPath,
                contentType = "image/jpeg",
                retryCount = 0,
                lastError = null,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        placeholderKey
    }

    suspend fun enqueueExam(consultationId: String, examId: String, localPath: String): String = withContext(io) {
        val placeholderKey = "local://${UUID.randomUUID()}"
        consultationDao.insertResultImages(
            listOf(
                ResultImageEntity(
                    id = UUID.randomUUID().toString(),
                    examId = examId,
                    s3Key = placeholderKey,
                    localPath = localPath,
                    uploadedAt = null,
                    uploadStatus = UploadStatus.PENDING.name,
                ),
            ),
        )
        pendingUploadDao.insert(
            PendingUploadEntity(
                id = UUID.randomUUID().toString(),
                consultationId = consultationId,
                examId = examId,
                type = UploadType.EXAM.name,
                localPath = localPath,
                contentType = "image/jpeg",
                retryCount = 0,
                lastError = null,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        placeholderKey
    }

    /**
     * Execute a single pending upload end-to-end:
     * 1) Request presigned URL from API (requires consultation remoteId).
     * 2) PUT file to S3.
     * 3) PATCH consultation to register the s3Key.
     * 4) Update local rows (swap placeholder s3Key, mark UPLOADED, delete pending).
     */
    suspend fun executePendingUpload(pending: PendingUploadEntity): Boolean = withContext(io) {
        val consultation = consultationDao.getById(pending.consultationId) ?: run {
            pendingUploadDao.delete(pending.id); return@withContext true
        }
        val remoteConsultationId = consultation.remoteId
            ?: return@withContext false // wait until consultation is synced

        val examRemoteId = pending.examId?.let { eid ->
            consultationDao.getExams(consultation.id).firstOrNull { it.id == eid }?.remoteId
        }
        if (pending.type == UploadType.EXAM.name && examRemoteId == null) {
            return@withContext false // wait until exam has a remoteId (consultation push assigns it)
        }

        val type = when (pending.type) {
            UploadType.PRESCRIPTION.name -> "prescription"
            UploadType.EXAM.name -> "exam"
            else -> "prescription"
        }
        val presigned = api.requestUploadUrl(
            id = remoteConsultationId,
            body = UploadUrlRequestDto(type = type, contentType = pending.contentType),
        )

        val uploadUrl = presigned.uploadUrl.remapLocalhostForDebug()

        val file = File(pending.localPath)
        if (!file.exists()) {
            // Local file was deleted (cache clear, uninstall/reinstall, etc.) — drop from queue
            pendingUploadDao.delete(pending.id)
            return@withContext true
        }
        val mediaType = pending.contentType.toMediaTypeOrNull()
        s3.upload(uploadUrl, pending.contentType, file.asRequestBody(mediaType))

        val now = Clock.System.now().toEpochMilliseconds()
        when (pending.type) {
            UploadType.PRESCRIPTION.name -> {
                api.update(
                    remoteConsultationId,
                    ConsultationPatchDto(addPrescriptionImage = AddPrescriptionImageDto(presigned.s3Key)),
                )
                val allImages = consultationDao.getPrescriptionImages(consultation.id)
                val placeholder = allImages.firstOrNull { it.localPath == pending.localPath && it.uploadStatus == UploadStatus.PENDING.name }
                if (placeholder != null) {
                    val remaining = allImages.filter { it.id != placeholder.id }
                    consultationDao.deletePrescriptionImages(consultation.id)
                    consultationDao.insertPrescriptionImages(
                        remaining + placeholder.copy(
                            s3Key = presigned.s3Key,
                            uploadedAt = now,
                            uploadStatus = UploadStatus.UPLOADED.name,
                        ),
                    )
                }
            }
            UploadType.EXAM.name -> {
                api.update(
                    remoteConsultationId,
                    ConsultationPatchDto(
                        addExamImage = AddExamImageDto(
                            examId = examRemoteId!!,
                            s3Key = presigned.s3Key,
                        ),
                    ),
                )
                val examId = pending.examId!!
                val results = consultationDao.getResultImages(listOf(examId))
                val placeholder = results.firstOrNull { it.localPath == pending.localPath }
                if (placeholder != null) {
                    consultationDao.deleteResultImagesFor(examId)
                    val remaining = results.filter { it.id != placeholder.id }
                    consultationDao.insertResultImages(
                        remaining + placeholder.copy(
                            s3Key = presigned.s3Key,
                            uploadedAt = now,
                            uploadStatus = UploadStatus.UPLOADED.name,
                        ),
                    )
                }
            }
        }
        pendingUploadDao.delete(pending.id)
        true
    }

    suspend fun markFailed(id: String, error: String?) = withContext(io) {
        pendingUploadDao.markFailed(id, error)
    }

    suspend fun loadImagesForConsultation(remoteConsultationId: String) =
        withContext(io) { api.getImages(remoteConsultationId) }

    /**
     * Cancel a pending upload that hasn't reached S3 yet (identified by its local file path).
     * The caller is responsible for removing the image from the local Room rows.
     */
    suspend fun cancelPendingUpload(localPath: String) = withContext(io) {
        pendingUploadDao.deleteByLocalPath(localPath)
    }

    /** Remove an already-uploaded prescription image from the server. */
    suspend fun removeUploadedPrescription(remoteConsultationId: String, s3Key: String): Unit = withContext(io) {
        api.update(remoteConsultationId, ConsultationPatchDto(removePrescriptionImage = RemovePrescriptionImageDto(s3Key)))
    }

    /** Remove an already-uploaded exam result image from the server. */
    suspend fun removeUploadedExam(remoteConsultationId: String, examRemoteId: String, s3Key: String): Unit = withContext(io) {
        api.update(remoteConsultationId, ConsultationPatchDto(removeExamImage = RemoveExamImageDto(examId = examRemoteId, s3Key = s3Key)))
    }
}

/**
 * In debug builds, presigned URLs generated by LocalStack contain "localhost" which neither the
 * emulator nor a physical device can reach. Replace with the same host used in API_BASE_URL_DEBUG
 * so the correct address is used for any device type.
 */
private fun String.remapLocalhostForDebug(): String {
    if (!com.example.nossasaudeapp.BuildConfig.DEBUG || !contains("://localhost")) return this
    val apiHost = try {
        java.net.URL(com.example.nossasaudeapp.BuildConfig.API_BASE_URL).host
    } catch (_: Exception) {
        "10.0.2.2"
    }
    return replace("://localhost", "://$apiHost")
}
