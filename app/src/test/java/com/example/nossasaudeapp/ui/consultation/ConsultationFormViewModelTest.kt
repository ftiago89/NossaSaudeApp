package com.example.nossasaudeapp.ui.consultation

import androidx.lifecycle.SavedStateHandle
import com.example.nossasaudeapp.data.image.S3ImageLoader
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.ImageRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.util.MainDispatcherRule
import com.example.nossasaudeapp.util.TestFixtures.domainConsultation
import com.example.nossasaudeapp.util.TestFixtures.domainExam
import com.example.nossasaudeapp.util.TestFixtures.memberEntity
import com.example.nossasaudeapp.util.TestFixtures.prescriptionImage
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConsultationFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK lateinit var consultationRepository: ConsultationRepository
    @MockK lateinit var memberRepository: MemberRepository
    @MockK lateinit var imageRepository: ImageRepository
    @MockK lateinit var s3ImageLoader: S3ImageLoader

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun createViewModel(
        memberId: String? = null,
        consultationId: String? = null,
    ): ConsultationFormViewModel {
        val handle = SavedStateHandle(
            buildMap {
                memberId?.let { put("memberId", it) }
                consultationId?.let { put("consultationId", it) }
            },
        )
        return ConsultationFormViewModel(
            handle, consultationRepository, memberRepository, imageRepository, s3ImageLoader,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation — isValid (create mode, no network calls needed)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `isValid is false when reason is blank`() = runTest {
        // No memberId → init returns early without touching repositories
        val vm = createViewModel()

        assertFalse(vm.state.value.isValid)
    }

    @Test
    fun `isValid is true when reason is filled and date is valid`() = runTest {
        val vm = createViewModel()
        vm.onReason("Rotina")
        vm.onDateText("15112023")

        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `dateError is true when date text is malformed`() = runTest {
        val vm = createViewModel()
        vm.onDateText("99992023") // invalid month/day

        assertTrue(vm.state.value.dateError)
    }

    @Test
    fun `dateError is false when date text is empty`() = runTest {
        val vm = createViewModel()
        // empty date is allowed (optional field)
        assertFalse(vm.state.value.dateError)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // deleteExistingPrescriptionImage — state mutation (edit mode required)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteExistingPrescriptionImage removes image from list and tracks s3Key`() = runTest {
        val uploadedImg = prescriptionImage(s3Key = "fam/m/consultation/c/prescriptions/img.jpg")
        val consultation = domainConsultation(prescriptionImages = listOf(uploadedImg))
        stubEditModeInit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")

        vm.deleteExistingPrescriptionImage(uploadedImg.s3Key)
        val state = vm.state.value

        assertTrue(state.existingPrescriptionImages.none { it.s3Key == uploadedImg.s3Key })
        assertTrue(state.deletedPrescriptionS3Keys.contains(uploadedImg.s3Key))
    }

    @Test
    fun `deleteExistingPrescriptionImage does not affect other existing images`() = runTest {
        val img1 = prescriptionImage(s3Key = "key1")
        val img2 = prescriptionImage(s3Key = "key2")
        val consultation = domainConsultation(prescriptionImages = listOf(img1, img2))
        stubEditModeInit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.deleteExistingPrescriptionImage("key1")

        assertTrue(vm.state.value.existingPrescriptionImages.any { it.s3Key == "key2" })
        assertEquals(1, vm.state.value.existingPrescriptionImages.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // deleteExistingExamImage — state mutation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteExistingExamImage removes result image and tracks s3Key in exam draft`() = runTest {
        val resultImg = prescriptionImage(s3Key = "fam/m/consultation/c/exams/img.jpg")
        val exam = domainExam(id = "exam-1", resultImages = listOf(resultImg))
        val consultation = domainConsultation(exams = listOf(exam))
        stubEditModeInit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.deleteExistingExamImage("exam-1", resultImg.s3Key)

        val examDraft = vm.state.value.exams.first()
        assertTrue(examDraft.existingResultImages.none { it.s3Key == resultImg.s3Key })
        assertTrue(examDraft.deletedResultS3Keys.contains(resultImg.s3Key))
    }

    @Test
    fun `deleteExistingExamImage only affects the target exam`() = runTest {
        val resultImg1 = prescriptionImage(s3Key = "key-exam-1")
        val resultImg2 = prescriptionImage(s3Key = "key-exam-2")
        val exam1 = domainExam(id = "exam-1", name = "Hemograma", resultImages = listOf(resultImg1))
        val exam2 = domainExam(id = "exam-2", name = "Urina", resultImages = listOf(resultImg2))
        val consultation = domainConsultation(exams = listOf(exam1, exam2))
        stubEditModeInit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.deleteExistingExamImage("exam-1", "key-exam-1")

        val exam2Draft = vm.state.value.exams.first { it.id == "exam-2" }
        assertTrue(exam2Draft.existingResultImages.any { it.s3Key == "key-exam-2" })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // save() — image deletion in edit mode
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `save calls cancelPendingUpload for PENDING prescription image`() = runTest {
        val pendingImg = prescriptionImage(
            s3Key = "local://pending-uuid",
            localPath = "/files/images/photo.jpg",
            uploadStatus = UploadStatus.PENDING,
        )
        val consultation = domainConsultation(
            remoteId = "r-c1",
            prescriptionImages = listOf(pendingImg),
        )
        stubEditModeInit("c1", consultation)
        stubSaveEdit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.onReason("Rotina") // ensure isValid
        vm.deleteExistingPrescriptionImage(pendingImg.s3Key)
        vm.save()

        coVerify(exactly = 1) { imageRepository.cancelPendingUpload("/files/images/photo.jpg") }
        coVerify(exactly = 0) { imageRepository.removeUploadedPrescription(any(), any()) }
    }

    @Test
    fun `save calls removeUploadedPrescription for UPLOADED prescription image`() = runTest {
        val uploadedImg = prescriptionImage(
            s3Key = "fam/m/consultation/c/prescriptions/img.jpg",
            localPath = null,
            uploadStatus = UploadStatus.UPLOADED,
        )
        val consultation = domainConsultation(
            remoteId = "r-c1",
            prescriptionImages = listOf(uploadedImg),
        )
        stubEditModeInit("c1", consultation)
        stubSaveEdit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.onReason("Rotina")
        vm.deleteExistingPrescriptionImage(uploadedImg.s3Key)
        vm.save()

        coVerify(exactly = 1) {
            imageRepository.removeUploadedPrescription("r-c1", uploadedImg.s3Key)
        }
        coVerify(exactly = 0) { imageRepository.cancelPendingUpload(any()) }
    }

    @Test
    fun `save passes consultation with deleted image filtered out to update`() = runTest {
        val img1 = prescriptionImage(s3Key = "key1")
        val img2 = prescriptionImage(s3Key = "key2")
        val consultation = domainConsultation(
            remoteId = "r-c1",
            prescriptionImages = listOf(img1, img2),
        )
        stubEditModeInit("c1", consultation)
        stubSaveEdit("c1", consultation)

        val vm = createViewModel(consultationId = "c1")
        vm.onReason("Rotina")
        vm.deleteExistingPrescriptionImage("key1")
        vm.save()

        coVerify {
            consultationRepository.update(
                match { updated ->
                    updated.prescriptionImages.none { it.s3Key == "key1" } &&
                        updated.prescriptionImages.any { it.s3Key == "key2" }
                },
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Stubs all calls made during ViewModel init in edit mode. */
    private fun stubEditModeInit(consultationId: String, consultation: com.example.nossasaudeapp.domain.model.Consultation) {
        coEvery { consultationRepository.getById(consultationId) } returns consultation
        coEvery { memberRepository.getById(consultation.memberId) } returns
            memberEntity(id = consultation.memberId).toDomain()
        coEvery { consultationRepository.getContraindicatedMedications(consultation.memberId) } returns emptyList()
        // loadPresignedUrls: only UPLOADED images (localPath == null) trigger presignedUrl call
        consultation.prescriptionImages.filter { it.localPath == null }.forEach { img ->
            coEvery { s3ImageLoader.presignedUrl(consultation.remoteId ?: any(), img.s3Key) } returns "https://s3/presigned/${img.s3Key}"
            coEvery { s3ImageLoader.cachedUrl(img.s3Key) } returns "https://s3/presigned/${img.s3Key}"
        }
        consultation.exams.flatMap { it.resultImages }.filter { it.localPath == null }.forEach { img ->
            coEvery { s3ImageLoader.presignedUrl(consultation.remoteId ?: any(), img.s3Key) } returns "https://s3/presigned/${img.s3Key}"
            coEvery { s3ImageLoader.cachedUrl(img.s3Key) } returns "https://s3/presigned/${img.s3Key}"
        }
    }

    /** Stubs all calls made during save() in edit mode. */
    private fun stubSaveEdit(
        consultationId: String,
        consultation: com.example.nossasaudeapp.domain.model.Consultation,
    ) {
        coEvery { consultationRepository.update(any()) } returns consultation
        coEvery { imageRepository.cancelPendingUpload(any()) } returns Unit
        coEvery { imageRepository.removeUploadedPrescription(any(), any()) } returns Unit
        coEvery { imageRepository.removeUploadedExam(any(), any(), any()) } returns Unit
        coEvery { imageRepository.enqueuePrescription(any(), any()) } returns "local://new"
        coEvery { imageRepository.enqueueExam(any(), any(), any()) } returns "local://new"
    }

    /** Convenience extension so we can get the domain model from a MemberEntity in tests. */
    private fun com.example.nossasaudeapp.data.local.entity.MemberEntity.toDomain() =
        com.example.nossasaudeapp.domain.model.Member(
            id = id, remoteId = remoteId, name = name,
            birthDate = null, bloodType = null,
            weightKg = null, heightCm = null,
            allergies = emptyList(), chronicConditions = emptyList(),
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(createdAt),
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt),
            syncedAt = syncedAt?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) },
            deletedAt = null,
        )
}
