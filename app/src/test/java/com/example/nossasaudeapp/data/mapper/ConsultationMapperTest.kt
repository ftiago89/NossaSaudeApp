package com.example.nossasaudeapp.data.mapper

import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.util.TestFixtures.T1
import com.example.nossasaudeapp.util.TestFixtures.T2
import com.example.nossasaudeapp.util.TestFixtures.consultationDto
import com.example.nossasaudeapp.util.TestFixtures.consultationEntity
import com.example.nossasaudeapp.util.TestFixtures.examDto
import com.example.nossasaudeapp.util.TestFixtures.examEntity
import com.example.nossasaudeapp.util.TestFixtures.memberDto
import com.example.nossasaudeapp.util.TestFixtures.prescriptionImageDto
import com.example.nossasaudeapp.util.TestFixtures.prescriptionImageEntity
import com.example.nossasaudeapp.util.TestFixtures.resultImageEntity
import com.example.nossasaudeapp.data.mapper.ConsultationAggregate
import com.example.nossasaudeapp.data.mapper.toDomain
import com.example.nossasaudeapp.data.mapper.toAggregate
import com.example.nossasaudeapp.data.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationMapperTest {

    // ──────────────────────────────────────────────────────────────────────────
    // ConsultationAggregate.toDomain — ordering
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toDomain sorts medications by orderIndex`() {
        val consultation = consultationEntity()
        val meds = listOf(
            com.example.nossasaudeapp.data.local.entity.MedicationEntity(
                id = "m2", consultationId = "consult-1", orderIndex = 1,
                name = "Beta", activeIngredient = null, dosage = null, form = null,
                frequency = null, contraindicated = false, restrictionReason = null,
                efficacy = null, sideEffects = null,
            ),
            com.example.nossasaudeapp.data.local.entity.MedicationEntity(
                id = "m1", consultationId = "consult-1", orderIndex = 0,
                name = "Alpha", activeIngredient = null, dosage = null, form = null,
                frequency = null, contraindicated = false, restrictionReason = null,
                efficacy = null, sideEffects = null,
            ),
        )
        val aggregate = ConsultationAggregate(consultation, meds, emptyList(), emptyList(), emptyList())

        val domain = aggregate.toDomain()

        assertEquals(listOf("Alpha", "Beta"), domain.medications.map { it.name })
    }

    @Test
    fun `toDomain sorts exams by orderIndex`() {
        val consultation = consultationEntity()
        val exams = listOf(
            examEntity(id = "e2", consultationId = "consult-1", name = "Urina", orderIndex = 1),
            examEntity(id = "e1", consultationId = "consult-1", name = "Hemograma", orderIndex = 0),
        )
        val aggregate = ConsultationAggregate(consultation, emptyList(), exams, emptyList(), emptyList())

        val domain = aggregate.toDomain()

        assertEquals(listOf("Hemograma", "Urina"), domain.exams.map { it.name })
    }

    @Test
    fun `toDomain associates result images to correct exam`() {
        val consultation = consultationEntity()
        val exams = listOf(
            examEntity(id = "e1", consultationId = "consult-1", name = "Hemograma", orderIndex = 0),
            examEntity(id = "e2", consultationId = "consult-1", name = "Urina", orderIndex = 1),
        )
        val resultImages = listOf(
            resultImageEntity(id = "ri1", examId = "e1", s3Key = "key-e1"),
            resultImageEntity(id = "ri2", examId = "e2", s3Key = "key-e2"),
        )
        val aggregate = ConsultationAggregate(consultation, emptyList(), exams, emptyList(), resultImages)

        val domain = aggregate.toDomain()

        assertEquals(listOf("key-e1"), domain.exams[0].resultImages.map { it.s3Key })
        assertEquals(listOf("key-e2"), domain.exams[1].resultImages.map { it.s3Key })
    }

    @Test
    fun `toDomain falls back to PENDING for unknown uploadStatus string`() {
        val consultation = consultationEntity()
        val invalidImg = prescriptionImageEntity(uploadStatus = "INVALID_STATUS")
        val aggregate = ConsultationAggregate(consultation, emptyList(), emptyList(), listOf(invalidImg), emptyList())

        val domain = aggregate.toDomain()

        assertEquals(UploadStatus.PENDING, domain.prescriptionImages.first().uploadStatus)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ConsultationDto.toAggregate — syncedAt invariant
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toAggregate sets syncedAt to max of syncedAt and updatedAt`() {
        // updatedAt is after syncedAt in the DTO — server-side race condition
        val dto = consultationDto(
            updatedAt = "2023-11-14T22:13:22Z",  // later
            syncedAt = "2023-11-14T22:13:21Z",   // earlier
        )
        val now = T1

        val aggregate = dto.toAggregate("local-1", "member-1", now = now)

        val entity = aggregate.consultation
        // syncedAt must be >= updatedAt to prevent false dirty after pull
        assertTrue(
            "syncedAt (${entity.syncedAt}) must be >= updatedAt (${entity.updatedAt})",
            (entity.syncedAt ?: 0L) >= entity.updatedAt,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ConsultationDto.toAggregate — local PK preservation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toAggregate reuses existing local exam id when remoteId matches`() {
        val dto = consultationDto(
            exams = listOf(examDto(id = "r-exam-1", name = "Hemograma")),
        )
        val existingMap = mapOf("r-exam-1" to "local-exam-uuid")

        val aggregate = dto.toAggregate("local-c1", "local-m1", existingExamIdByRemote = existingMap)

        assertEquals("local-exam-uuid", aggregate.exams.first().id)
        assertEquals("r-exam-1", aggregate.exams.first().remoteId)
    }

    @Test
    fun `toAggregate generates new id for exam without existing local mapping`() {
        val dto = consultationDto(
            exams = listOf(examDto(id = "r-exam-new", name = "Raio-X")),
        )

        val aggregate = dto.toAggregate("local-c1", "local-m1")

        // id should be a non-blank UUID, not the remote id
        assertTrue(aggregate.exams.first().id.isNotBlank())
        assertEquals("r-exam-new", aggregate.exams.first().remoteId)
    }

    @Test
    fun `toAggregate reuses existing prescription image id by s3Key`() {
        val s3Key = "fam/m/consultation/c/prescriptions/img.jpg"
        val dto = consultationDto(prescriptionImages = listOf(prescriptionImageDto(s3Key = s3Key)))
        val existingImageMap = mapOf(s3Key to "existing-img-id")

        val aggregate = dto.toAggregate("local-c1", "local-m1", existingImageIdByS3 = existingImageMap)

        assertEquals("existing-img-id", aggregate.prescriptionImages.first().id)
    }

    @Test
    fun `toAggregate marks all prescription images as UPLOADED`() {
        val dto = consultationDto(prescriptionImages = listOf(prescriptionImageDto()))

        val aggregate = dto.toAggregate("local-c1", "local-m1")

        assertEquals(UploadStatus.UPLOADED.name, aggregate.prescriptionImages.first().uploadStatus)
    }

    @Test
    fun `toAggregate maps exams in order preserving indices`() {
        val dto = consultationDto(
            exams = listOf(
                examDto(id = "r-e1", name = "Hemograma"),
                examDto(id = "r-e2", name = "Urina"),
            ),
        )

        val aggregate = dto.toAggregate("local-c1", "local-m1")

        assertEquals(0, aggregate.exams[0].orderIndex)
        assertEquals(1, aggregate.exams[1].orderIndex)
        assertEquals("Hemograma", aggregate.exams[0].name)
        assertEquals("Urina", aggregate.exams[1].name)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MemberMapper — syncedAt invariant (same logic as ConsultationMapper)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `MemberDto toEntity sets syncedAt to max of syncedAt and updatedAt`() {
        val dto = memberDto(
            updatedAt = "2023-11-14T22:13:22Z",
            syncedAt  = "2023-11-14T22:13:20Z",  // earlier than updatedAt
        )

        val entity = dto.toEntity("local-1")

        assertTrue(
            "syncedAt (${entity.syncedAt}) must be >= updatedAt (${entity.updatedAt})",
            (entity.syncedAt ?: 0L) >= entity.updatedAt,
        )
    }

    @Test
    fun `MemberDto toEntity preserves null bloodType`() {
        val dto = memberDto().copy(bloodType = null)

        val entity = dto.toEntity("local-1")

        assertNull(entity.bloodType)
    }

    @Test
    fun `MemberDto toEntity preserves null deletedAt`() {
        val dto = memberDto(deletedAt = null)

        val entity = dto.toEntity("local-1")

        assertNull(entity.deletedAt)
    }
}
