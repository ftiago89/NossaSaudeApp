package com.example.nossasaudeapp.util

import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.data.local.entity.ExamEntity
import com.example.nossasaudeapp.data.local.entity.MemberEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.remote.dto.ConsultationDto
import com.example.nossasaudeapp.data.remote.dto.DoctorDto
import com.example.nossasaudeapp.data.remote.dto.ExamDto
import com.example.nossasaudeapp.data.remote.dto.MemberDto
import com.example.nossasaudeapp.data.remote.dto.PrescriptionImageDto
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Doctor
import com.example.nossasaudeapp.domain.model.Exam
import com.example.nossasaudeapp.domain.model.PrescriptionImage
import com.example.nossasaudeapp.domain.model.UploadStatus
import kotlinx.datetime.Instant

object TestFixtures {

    // Fixed epoch millis for deterministic tests — named by relative ordering
    const val T0 = 1_700_000_000_000L   // ~Nov 2023, base
    const val T1 = T0 + 1_000L
    const val T2 = T0 + 2_000L
    const val T3 = T0 + 3_000L

    // ── Members ────────────────────────────────────────────────────────────────

    fun memberEntity(
        id: String = "local-1",
        remoteId: String? = "remote-1",
        name: String = "João Silva",
        updatedAt: Long = T1,
        syncedAt: Long? = T1,
        deletedAt: Long? = null,
    ) = MemberEntity(
        id = id,
        remoteId = remoteId,
        name = name,
        birthDate = null,
        bloodType = null,
        weightKg = null,
        heightCm = null,
        allergies = emptyList(),
        chronicConditions = emptyList(),
        createdAt = T0,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        deletedAt = deletedAt,
    )

    fun memberDto(
        id: String = "remote-1",
        name: String = "João Silva",
        updatedAt: String = "2023-11-14T22:13:21Z",
        syncedAt: String? = "2023-11-14T22:13:21Z",
        deletedAt: String? = null,
    ) = MemberDto(
        id = id,
        familyId = "family-1",
        name = name,
        birthDate = null,
        bloodType = null,
        weight = null,
        height = null,
        allergies = emptyList(),
        chronicConditions = emptyList(),
        syncedAt = syncedAt,
        deletedAt = deletedAt,
        createdAt = "2023-11-14T22:13:20Z",
        updatedAt = updatedAt,
    )

    // ── Consultations ──────────────────────────────────────────────────────────

    fun consultationEntity(
        id: String = "consult-1",
        remoteId: String? = "r-consult-1",
        memberId: String = "local-1",
        updatedAt: Long = T1,
        syncedAt: Long? = T1,
        deletedAt: Long? = null,
    ) = ConsultationEntity(
        id = id,
        remoteId = remoteId,
        memberId = memberId,
        date = T0,
        reason = "Rotina",
        doctorName = null,
        doctorSpecialty = null,
        doctorCustomSpecialty = null,
        clinic = null,
        notes = null,
        tags = emptyList(),
        returnOf = null,
        createdAt = T0,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        deletedAt = deletedAt,
    )

    fun consultationDto(
        id: String = "r-consult-1",
        memberId: String = "r-member-1",
        updatedAt: String = "2023-11-14T22:13:21Z",
        syncedAt: String? = "2023-11-14T22:13:21Z",
        deletedAt: String? = null,
        exams: List<ExamDto> = emptyList(),
        prescriptionImages: List<PrescriptionImageDto> = emptyList(),
    ) = ConsultationDto(
        id = id,
        familyId = "family-1",
        memberId = memberId,
        date = "2023-11-14T10:00:00Z",
        reason = "Rotina",
        doctor = DoctorDto(),
        clinic = null,
        notes = null,
        tags = emptyList(),
        returnOf = null,
        prescriptionImages = prescriptionImages,
        medications = emptyList(),
        exams = exams,
        syncedAt = syncedAt,
        deletedAt = deletedAt,
        createdAt = "2023-11-14T22:13:20Z",
        updatedAt = updatedAt,
    )

    // ── Exams ──────────────────────────────────────────────────────────────────

    fun examEntity(
        id: String = "exam-1",
        remoteId: String? = "r-exam-1",
        consultationId: String = "consult-1",
        name: String = "Hemograma",
        orderIndex: Int = 0,
    ) = ExamEntity(
        id = id,
        remoteId = remoteId,
        consultationId = consultationId,
        orderIndex = orderIndex,
        name = name,
        notes = null,
    )

    fun examDto(
        id: String? = "r-exam-1",
        name: String = "Hemograma",
        resultImages: List<PrescriptionImageDto> = emptyList(),
    ) = ExamDto(id = id, name = name, notes = null, resultImages = resultImages)

    // ── Images ─────────────────────────────────────────────────────────────────

    fun prescriptionImageEntity(
        id: String = "img-1",
        consultationId: String = "consult-1",
        s3Key: String = "fam/member/consultation/consult-1/prescriptions/img.jpg",
        localPath: String? = null,
        uploadStatus: String = UploadStatus.UPLOADED.name,
    ) = PrescriptionImageEntity(
        id = id,
        consultationId = consultationId,
        s3Key = s3Key,
        localPath = localPath,
        uploadedAt = T1,
        uploadStatus = uploadStatus,
    )

    fun prescriptionImageDto(
        s3Key: String = "fam/member/consultation/consult-1/prescriptions/img.jpg",
    ) = PrescriptionImageDto(s3Key = s3Key, uploadedAt = "2023-11-14T22:13:21Z")

    fun resultImageEntity(
        id: String = "res-img-1",
        examId: String = "exam-1",
        s3Key: String = "fam/member/consultation/consult-1/exams/img.jpg",
        localPath: String? = null,
        uploadStatus: String = UploadStatus.UPLOADED.name,
    ) = ResultImageEntity(
        id = id,
        examId = examId,
        s3Key = s3Key,
        localPath = localPath,
        uploadedAt = T1,
        uploadStatus = uploadStatus,
    )

    // ── Domain helpers ─────────────────────────────────────────────────────────

    fun prescriptionImage(
        s3Key: String = "fam/member/consultation/consult-1/prescriptions/img.jpg",
        localPath: String? = null,
        uploadStatus: UploadStatus = UploadStatus.UPLOADED,
    ) = PrescriptionImage(
        s3Key = s3Key,
        localPath = localPath,
        uploadedAt = Instant.fromEpochMilliseconds(T1),
        uploadStatus = uploadStatus,
    )

    fun domainConsultation(
        id: String = "consult-1",
        remoteId: String? = "r-consult-1",
        memberId: String = "local-1",
        prescriptionImages: List<PrescriptionImage> = emptyList(),
        exams: List<Exam> = emptyList(),
        updatedAt: Long = T1,
        syncedAt: Long? = T1,
    ) = Consultation(
        id = id,
        remoteId = remoteId,
        memberId = memberId,
        date = Instant.fromEpochMilliseconds(T0),
        reason = "Rotina",
        doctor = Doctor(null, null, null),
        clinic = null,
        notes = null,
        tags = emptyList(),
        returnOf = null,
        prescriptionImages = prescriptionImages,
        medications = emptyList(),
        exams = exams,
        createdAt = Instant.fromEpochMilliseconds(T0),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
        deletedAt = null,
    )

    fun domainExam(
        id: String = "exam-1",
        remoteId: String? = "r-exam-1",
        name: String = "Hemograma",
        resultImages: List<PrescriptionImage> = emptyList(),
    ) = Exam(
        id = id,
        remoteId = remoteId,
        name = name,
        notes = null,
        resultImages = resultImages,
    )
}
