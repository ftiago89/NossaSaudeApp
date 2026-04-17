package com.example.nossasaudeapp.data.repository

import android.content.Context
import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.entity.PendingUploadEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.api.S3UploadApi
import com.example.nossasaudeapp.data.remote.dto.ConsultationPatchDto
import com.example.nossasaudeapp.data.remote.dto.UploadUrlResponseDto
import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.domain.model.UploadType
import com.example.nossasaudeapp.util.TestFixtures
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ImageRepositoryTest {

    @MockK lateinit var context: Context
    @MockK lateinit var consultationDao: ConsultationDao
    @MockK lateinit var pendingUploadDao: PendingUploadDao
    @MockK lateinit var api: ConsultationsApi
    @MockK lateinit var s3: S3UploadApi

    private lateinit var repository: ImageRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = ImageRepository(
            context, consultationDao, pendingUploadDao, api, s3, UnconfinedTestDispatcher(),
        )
    }

    private fun pendingEntity(
        id: String = "pending-1",
        consultationId: String = "consult-1",
        examId: String? = null,
        type: String = UploadType.PRESCRIPTION.name,
        localPath: String = "/files/images/test.jpg",
    ) = PendingUploadEntity(
        id = id,
        consultationId = consultationId,
        examId = examId,
        type = type,
        localPath = localPath,
        contentType = "image/jpeg",
        retryCount = 0,
        lastError = null,
        createdAt = TestFixtures.T0,
    )

    // ── enqueuePrescription ───────────────────────────────────────────────────

    @Test
    fun `enqueuePrescription inserts PENDING image row and returns local placeholder key`() = runTest {
        val imageSlot = slot<List<PrescriptionImageEntity>>()
        coEvery { consultationDao.insertPrescriptionImages(capture(imageSlot)) } returns Unit
        coEvery { pendingUploadDao.insert(any()) } returns Unit

        val key = repository.enqueuePrescription("consult-1", "/files/img.jpg")

        assertTrue(key.startsWith("local://"))
        assertEquals(1, imageSlot.captured.size)
        with(imageSlot.captured[0]) {
            assertEquals(UploadStatus.PENDING.name, uploadStatus)
            assertEquals("consult-1", consultationId)
            assertEquals(key, s3Key)
            assertEquals("/files/img.jpg", localPath)
        }
    }

    @Test
    fun `enqueuePrescription inserts pending upload of PRESCRIPTION type with no examId`() = runTest {
        val pendingSlot = slot<PendingUploadEntity>()
        coEvery { consultationDao.insertPrescriptionImages(any()) } returns Unit
        coEvery { pendingUploadDao.insert(capture(pendingSlot)) } returns Unit

        repository.enqueuePrescription("consult-1", "/files/img.jpg")

        with(pendingSlot.captured) {
            assertEquals(UploadType.PRESCRIPTION.name, type)
            assertEquals("consult-1", consultationId)
            assertEquals(null, examId)
        }
    }

    // ── enqueueExam ───────────────────────────────────────────────────────────

    @Test
    fun `enqueueExam inserts PENDING result image row and returns local placeholder key`() = runTest {
        val imageSlot = slot<List<ResultImageEntity>>()
        coEvery { consultationDao.insertResultImages(capture(imageSlot)) } returns Unit
        coEvery { pendingUploadDao.insert(any()) } returns Unit

        val key = repository.enqueueExam("consult-1", "exam-1", "/files/img.jpg")

        assertTrue(key.startsWith("local://"))
        assertEquals(1, imageSlot.captured.size)
        with(imageSlot.captured[0]) {
            assertEquals(UploadStatus.PENDING.name, uploadStatus)
            assertEquals("exam-1", examId)
            assertEquals(key, s3Key)
        }
    }

    @Test
    fun `enqueueExam inserts pending upload of EXAM type with correct examId`() = runTest {
        val pendingSlot = slot<PendingUploadEntity>()
        coEvery { consultationDao.insertResultImages(any()) } returns Unit
        coEvery { pendingUploadDao.insert(capture(pendingSlot)) } returns Unit

        repository.enqueueExam("consult-1", "exam-1", "/files/img.jpg")

        assertEquals(UploadType.EXAM.name, pendingSlot.captured.type)
        assertEquals("exam-1", pendingSlot.captured.examId)
        assertEquals("consult-1", pendingSlot.captured.consultationId)
    }

    // ── executePendingUpload — early exits ────────────────────────────────────

    @Test
    fun `executePendingUpload deletes pending and returns true when consultation not found`() = runTest {
        coEvery { consultationDao.getById("consult-1") } returns null
        coEvery { pendingUploadDao.delete("pending-1") } returns Unit

        val result = repository.executePendingUpload(pendingEntity())

        assertTrue(result)
        coVerify(exactly = 1) { pendingUploadDao.delete("pending-1") }
        coVerify(exactly = 0) { api.requestUploadUrl(any(), any()) }
    }

    @Test
    fun `executePendingUpload returns false when consultation has no remoteId`() = runTest {
        coEvery { consultationDao.getById("consult-1") } returns TestFixtures.consultationEntity(remoteId = null)

        val result = repository.executePendingUpload(pendingEntity())

        assertFalse(result)
        coVerify(exactly = 0) { api.requestUploadUrl(any(), any()) }
    }

    @Test
    fun `executePendingUpload returns false when exam type and exam has no remoteId yet`() = runTest {
        coEvery { consultationDao.getById("consult-1") } returns
            TestFixtures.consultationEntity(remoteId = "r-consult-1")
        coEvery { consultationDao.getExams("consult-1") } returns listOf(
            TestFixtures.examEntity(id = "exam-1", remoteId = null, consultationId = "consult-1"),
        )

        val result = repository.executePendingUpload(
            pendingEntity(examId = "exam-1", type = UploadType.EXAM.name),
        )

        assertFalse(result)
        coVerify(exactly = 0) { api.requestUploadUrl(any(), any()) }
    }

    // ── executePendingUpload — prescription happy path ────────────────────────

    @Test
    fun `executePendingUpload prescription swaps placeholder row with real s3Key and marks UPLOADED`() = runTest {
        val tempFile = File.createTempFile("test_img", ".jpg")
        try {
            val placeholder = TestFixtures.prescriptionImageEntity(
                id = "img-pending",
                consultationId = "consult-1",
                s3Key = "local://placeholder",
                localPath = tempFile.absolutePath,
                uploadStatus = UploadStatus.PENDING.name,
            )
            val presigned = UploadUrlResponseDto(
                uploadUrl = "https://s3.example.com/upload",
                s3Key = "family/member/consultation/consult-1/prescriptions/real.jpg",
            )
            coEvery { consultationDao.getById("consult-1") } returns
                TestFixtures.consultationEntity(remoteId = "r-consult-1")
            coEvery { api.requestUploadUrl("r-consult-1", any()) } returns presigned
            coEvery { s3.upload(any(), any(), any()) } returns Unit
            coEvery { api.update(any(), any()) } returns TestFixtures.consultationDto()
            coEvery { consultationDao.getPrescriptionImages("consult-1") } returns listOf(placeholder)
            coEvery { consultationDao.deletePrescriptionImages("consult-1") } returns Unit
            val insertedSlot = slot<List<PrescriptionImageEntity>>()
            coEvery { consultationDao.insertPrescriptionImages(capture(insertedSlot)) } returns Unit
            coEvery { pendingUploadDao.delete("pending-1") } returns Unit

            val result = repository.executePendingUpload(
                pendingEntity(localPath = tempFile.absolutePath),
            )

            assertTrue(result)
            coVerify(exactly = 1) { s3.upload(any(), any(), any()) }
            coVerify(exactly = 1) { pendingUploadDao.delete("pending-1") }
            val uploaded = insertedSlot.captured.first { it.id == "img-pending" }
            assertEquals(presigned.s3Key, uploaded.s3Key)
            assertEquals(UploadStatus.UPLOADED.name, uploaded.uploadStatus)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `executePendingUpload prescription calls PATCH with addPrescriptionImage`() = runTest {
        val tempFile = File.createTempFile("test_img", ".jpg")
        try {
            val presigned = UploadUrlResponseDto(
                uploadUrl = "https://s3.example.com/upload",
                s3Key = "family/member/consultation/consult-1/prescriptions/real.jpg",
            )
            val patchSlot = slot<ConsultationPatchDto>()
            coEvery { consultationDao.getById("consult-1") } returns
                TestFixtures.consultationEntity(remoteId = "r-consult-1")
            coEvery { api.requestUploadUrl("r-consult-1", any()) } returns presigned
            coEvery { s3.upload(any(), any(), any()) } returns Unit
            coEvery { api.update("r-consult-1", capture(patchSlot)) } returns TestFixtures.consultationDto()
            coEvery { consultationDao.getPrescriptionImages("consult-1") } returns emptyList()
            coEvery { consultationDao.deletePrescriptionImages("consult-1") } returns Unit
            coEvery { consultationDao.insertPrescriptionImages(any()) } returns Unit
            coEvery { pendingUploadDao.delete(any()) } returns Unit

            repository.executePendingUpload(pendingEntity(localPath = tempFile.absolutePath))

            assertEquals(presigned.s3Key, patchSlot.captured.addPrescriptionImage?.s3Key)
        } finally {
            tempFile.delete()
        }
    }

    // ── executePendingUpload — exam happy path ────────────────────────────────

    @Test
    fun `executePendingUpload exam swaps placeholder row with real s3Key and marks UPLOADED`() = runTest {
        val tempFile = File.createTempFile("test_exam_img", ".jpg")
        try {
            val placeholder = TestFixtures.resultImageEntity(
                id = "res-pending",
                examId = "exam-1",
                s3Key = "local://placeholder",
                localPath = tempFile.absolutePath,
                uploadStatus = UploadStatus.PENDING.name,
            )
            val presigned = UploadUrlResponseDto(
                uploadUrl = "https://s3.example.com/upload",
                s3Key = "family/member/consultation/consult-1/exams/real.jpg",
            )
            coEvery { consultationDao.getById("consult-1") } returns
                TestFixtures.consultationEntity(remoteId = "r-consult-1")
            coEvery { consultationDao.getExams("consult-1") } returns listOf(
                TestFixtures.examEntity(id = "exam-1", remoteId = "r-exam-1", consultationId = "consult-1"),
            )
            coEvery { api.requestUploadUrl("r-consult-1", any()) } returns presigned
            coEvery { s3.upload(any(), any(), any()) } returns Unit
            coEvery { api.update(any(), any()) } returns TestFixtures.consultationDto()
            coEvery { consultationDao.getResultImages(listOf("exam-1")) } returns listOf(placeholder)
            coEvery { consultationDao.deleteResultImagesFor("exam-1") } returns Unit
            val insertedSlot = slot<List<ResultImageEntity>>()
            coEvery { consultationDao.insertResultImages(capture(insertedSlot)) } returns Unit
            coEvery { pendingUploadDao.delete("pending-1") } returns Unit

            val result = repository.executePendingUpload(
                pendingEntity(examId = "exam-1", type = UploadType.EXAM.name, localPath = tempFile.absolutePath),
            )

            assertTrue(result)
            coVerify(exactly = 1) { s3.upload(any(), any(), any()) }
            coVerify(exactly = 1) { pendingUploadDao.delete("pending-1") }
            val uploaded = insertedSlot.captured.first { it.id == "res-pending" }
            assertEquals(presigned.s3Key, uploaded.s3Key)
            assertEquals(UploadStatus.UPLOADED.name, uploaded.uploadStatus)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `executePendingUpload exam calls PATCH with addExamImage containing examRemoteId`() = runTest {
        val tempFile = File.createTempFile("test_exam_img", ".jpg")
        try {
            val presigned = UploadUrlResponseDto(
                uploadUrl = "https://s3.example.com/upload",
                s3Key = "family/member/consultation/consult-1/exams/real.jpg",
            )
            val patchSlot = slot<ConsultationPatchDto>()
            coEvery { consultationDao.getById("consult-1") } returns
                TestFixtures.consultationEntity(remoteId = "r-consult-1")
            coEvery { consultationDao.getExams("consult-1") } returns listOf(
                TestFixtures.examEntity(id = "exam-1", remoteId = "r-exam-1", consultationId = "consult-1"),
            )
            coEvery { api.requestUploadUrl("r-consult-1", any()) } returns presigned
            coEvery { s3.upload(any(), any(), any()) } returns Unit
            coEvery { api.update("r-consult-1", capture(patchSlot)) } returns TestFixtures.consultationDto()
            coEvery { consultationDao.getResultImages(listOf("exam-1")) } returns emptyList()
            coEvery { consultationDao.deleteResultImagesFor("exam-1") } returns Unit
            coEvery { consultationDao.insertResultImages(any()) } returns Unit
            coEvery { pendingUploadDao.delete(any()) } returns Unit

            repository.executePendingUpload(
                pendingEntity(examId = "exam-1", type = UploadType.EXAM.name, localPath = tempFile.absolutePath),
            )

            assertEquals("r-exam-1", patchSlot.captured.addExamImage?.examId)
            assertEquals(presigned.s3Key, patchSlot.captured.addExamImage?.s3Key)
        } finally {
            tempFile.delete()
        }
    }

    // ── executePendingUpload — missing local file ─────────────────────────────

    @Test
    fun `executePendingUpload drops pending and returns true when local file is missing`() = runTest {
        val presigned = UploadUrlResponseDto(
            uploadUrl = "https://s3.example.com/upload",
            s3Key = "family/member/consultation/consult-1/prescriptions/real.jpg",
        )
        coEvery { consultationDao.getById("consult-1") } returns
            TestFixtures.consultationEntity(remoteId = "r-consult-1")
        coEvery { api.requestUploadUrl("r-consult-1", any()) } returns presigned
        coEvery { pendingUploadDao.delete("pending-1") } returns Unit

        val result = repository.executePendingUpload(
            pendingEntity(localPath = "/non/existent/path/img.jpg"),
        )

        assertTrue(result)
        coVerify(exactly = 1) { pendingUploadDao.delete("pending-1") }
        coVerify(exactly = 0) { s3.upload(any(), any(), any()) }
    }

    // ── cancelPendingUpload ───────────────────────────────────────────────────

    @Test
    fun `cancelPendingUpload delegates to pendingUploadDao deleteByLocalPath`() = runTest {
        coEvery { pendingUploadDao.deleteByLocalPath("/files/img.jpg") } returns Unit

        repository.cancelPendingUpload("/files/img.jpg")

        coVerify(exactly = 1) { pendingUploadDao.deleteByLocalPath("/files/img.jpg") }
    }

    // ── removeUploadedPrescription ────────────────────────────────────────────

    @Test
    fun `removeUploadedPrescription calls api update with removePrescriptionImage`() = runTest {
        val patchSlot = slot<ConsultationPatchDto>()
        coEvery { api.update("r-c1", capture(patchSlot)) } returns TestFixtures.consultationDto()

        repository.removeUploadedPrescription(
            "r-c1",
            "family/member/consultation/c1/prescriptions/img.jpg",
        )

        assertEquals(
            "family/member/consultation/c1/prescriptions/img.jpg",
            patchSlot.captured.removePrescriptionImage?.s3Key,
        )
        assertEquals(null, patchSlot.captured.removeExamImage)
    }

    // ── removeUploadedExam ────────────────────────────────────────────────────

    @Test
    fun `removeUploadedExam calls api update with removeExamImage containing examId and s3Key`() = runTest {
        val patchSlot = slot<ConsultationPatchDto>()
        coEvery { api.update("r-c1", capture(patchSlot)) } returns TestFixtures.consultationDto()

        repository.removeUploadedExam(
            "r-c1",
            "r-exam-1",
            "family/member/consultation/c1/exams/img.jpg",
        )

        assertEquals("r-exam-1", patchSlot.captured.removeExamImage?.examId)
        assertEquals("family/member/consultation/c1/exams/img.jpg", patchSlot.captured.removeExamImage?.s3Key)
        assertEquals(null, patchSlot.captured.removePrescriptionImage)
    }
}
