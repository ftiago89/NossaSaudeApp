package com.example.nossasaudeapp.data.mapper

import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.data.local.entity.ExamEntity
import com.example.nossasaudeapp.data.local.entity.MedicationEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.remote.dto.ConsultationCreateDto
import com.example.nossasaudeapp.data.remote.dto.ConsultationDto
import com.example.nossasaudeapp.data.remote.dto.DoctorDto
import com.example.nossasaudeapp.data.remote.dto.ExamDto
import com.example.nossasaudeapp.data.remote.dto.ExamWriteDto
import com.example.nossasaudeapp.data.remote.dto.MedicationDto
import com.example.nossasaudeapp.data.remote.dto.PrescriptionImageDto
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Doctor
import com.example.nossasaudeapp.domain.model.Efficacy
import com.example.nossasaudeapp.domain.model.Exam
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.domain.model.MedicationForm
import com.example.nossasaudeapp.domain.model.PrescriptionImage
import com.example.nossasaudeapp.domain.model.UploadStatus
import kotlinx.datetime.Instant

data class ConsultationAggregate(
    val consultation: ConsultationEntity,
    val medications: List<MedicationEntity>,
    val exams: List<ExamEntity>,
    val prescriptionImages: List<PrescriptionImageEntity>,
    val resultImages: List<ResultImageEntity>,
)

fun ConsultationAggregate.toDomain(): Consultation {
    val examById = exams.associate { entity ->
        entity.id to Exam(
            id = entity.id,
            remoteId = entity.remoteId,
            name = entity.name,
            notes = entity.notes,
            resultImages = resultImages
                .filter { it.examId == entity.id }
                .map { it.toDomain() },
        )
    }
    return Consultation(
        id = consultation.id,
        remoteId = consultation.remoteId,
        memberId = consultation.memberId,
        date = Instant.fromEpochMilliseconds(consultation.date),
        reason = consultation.reason,
        doctor = Doctor(
            name = consultation.doctorName,
            specialty = consultation.doctorSpecialty,
            customSpecialty = consultation.doctorCustomSpecialty,
        ),
        clinic = consultation.clinic,
        notes = consultation.notes,
        tags = consultation.tags,
        returnOf = consultation.returnOf,
        prescriptionImages = prescriptionImages.map { it.toDomain() },
        medications = medications.sortedBy { it.orderIndex }.map { it.toDomain() },
        exams = exams.sortedBy { it.orderIndex }.mapNotNull { examById[it.id] },
        createdAt = Instant.fromEpochMilliseconds(consultation.createdAt),
        updatedAt = Instant.fromEpochMilliseconds(consultation.updatedAt),
        syncedAt = consultation.syncedAt?.let { Instant.fromEpochMilliseconds(it) },
        deletedAt = consultation.deletedAt?.let { Instant.fromEpochMilliseconds(it) },
    )
}

fun PrescriptionImageEntity.toDomain(): PrescriptionImage = PrescriptionImage(
    s3Key = s3Key,
    localPath = localPath,
    uploadedAt = uploadedAt?.let { Instant.fromEpochMilliseconds(it) },
    uploadStatus = runCatching { UploadStatus.valueOf(uploadStatus) }.getOrDefault(UploadStatus.PENDING),
)

fun ResultImageEntity.toDomain(): PrescriptionImage = PrescriptionImage(
    s3Key = s3Key,
    localPath = localPath,
    uploadedAt = uploadedAt?.let { Instant.fromEpochMilliseconds(it) },
    uploadStatus = runCatching { UploadStatus.valueOf(uploadStatus) }.getOrDefault(UploadStatus.PENDING),
)

fun MedicationEntity.toDomain(): Medication = Medication(
    name = name,
    activeIngredient = activeIngredient,
    dosage = dosage,
    form = MedicationForm.fromApi(form),
    frequency = frequency,
    contraindicated = contraindicated,
    restrictionReason = restrictionReason,
    efficacy = Efficacy.fromApi(efficacy),
    sideEffects = sideEffects,
)

fun Medication.toEntity(id: String, consultationId: String, order: Int): MedicationEntity =
    MedicationEntity(
        id = id,
        consultationId = consultationId,
        orderIndex = order,
        name = name,
        activeIngredient = activeIngredient,
        dosage = dosage,
        form = form?.name,
        frequency = frequency,
        contraindicated = contraindicated,
        restrictionReason = restrictionReason,
        efficacy = efficacy?.name,
        sideEffects = sideEffects,
    )

fun Exam.toEntity(consultationId: String, order: Int): ExamEntity = ExamEntity(
    id = id,
    remoteId = remoteId,
    consultationId = consultationId,
    orderIndex = order,
    name = name,
    notes = notes,
)

fun MedicationDto.toDomain(): Medication = Medication(
    name = name,
    activeIngredient = activeIngredient,
    dosage = dosage,
    form = MedicationForm.fromApi(form),
    frequency = frequency,
    contraindicated = contraindicated,
    restrictionReason = restrictionReason,
    efficacy = Efficacy.fromApi(efficacy),
    sideEffects = sideEffects,
)

fun Medication.toDto(): MedicationDto = MedicationDto(
    name = name,
    activeIngredient = activeIngredient,
    dosage = dosage,
    form = form?.name,
    frequency = frequency,
    contraindicated = contraindicated,
    restrictionReason = restrictionReason,
    efficacy = efficacy?.name,
    sideEffects = sideEffects,
)

fun Exam.toDto(): ExamWriteDto = ExamWriteDto(
    id = remoteId,
    name = name,
    notes = notes,
)

fun Doctor.toDto(): DoctorDto = DoctorDto(
    name = name,
    specialty = specialty,
    customSpecialty = customSpecialty,
)

fun Consultation.toCreateDto(remoteMemberId: String): ConsultationCreateDto = ConsultationCreateDto(
    memberId = remoteMemberId,
    date = date.toIso8601(),
    reason = reason,
    doctor = doctor.toDto(),
    clinic = clinic,
    notes = notes,
    tags = tags,
    returnOf = returnOf,
    medications = medications.map { it.toDto() },
    exams = exams.map { it.toDto() },
)

/**
 * Converts a remote DTO into local aggregates. If the consultation already exists locally (by
 * remoteId), pass its localId so we preserve PKs.
 */
fun ConsultationDto.toAggregate(
    localConsultationId: String,
    localMemberId: String,
    existingExamIdByRemote: Map<String, String> = emptyMap(),
    existingImageIdByS3: Map<String, String> = emptyMap(),
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): ConsultationAggregate {
    val parsedUpdatedAt = updatedAt.toInstantOrNull()?.toEpochMilliseconds() ?: now
    val parsedSyncedAt = syncedAt.toInstantOrNull()?.toEpochMilliseconds() ?: now
    val consultation = ConsultationEntity(
        id = localConsultationId,
        remoteId = id,
        memberId = localMemberId,
        date = date.toInstantOrNull()?.toEpochMilliseconds() ?: now,
        reason = reason,
        doctorName = doctor.name,
        doctorSpecialty = doctor.specialty,
        doctorCustomSpecialty = doctor.customSpecialty,
        clinic = clinic,
        notes = notes,
        tags = tags,
        returnOf = returnOf,
        createdAt = createdAt.toInstantOrNull()?.toEpochMilliseconds() ?: now,
        updatedAt = parsedUpdatedAt,
        syncedAt = maxOf(parsedSyncedAt, parsedUpdatedAt), // prevent false dirty after pull
        deletedAt = deletedAt.toInstantOrNull()?.toEpochMilliseconds(),
    )
    val medsEntities = medications.mapIndexed { idx, dto ->
        MedicationEntity(
            id = java.util.UUID.randomUUID().toString(),
            consultationId = localConsultationId,
            orderIndex = idx,
            name = dto.name,
            activeIngredient = dto.activeIngredient,
            dosage = dto.dosage,
            form = dto.form,
            frequency = dto.frequency,
            contraindicated = dto.contraindicated,
            restrictionReason = dto.restrictionReason,
            efficacy = dto.efficacy,
            sideEffects = dto.sideEffects,
        )
    }
    val examsEntities = exams.mapIndexed { idx, dto ->
        val remoteExamId = dto.id
        val localExamId = (remoteExamId?.let { existingExamIdByRemote[it] })
            ?: java.util.UUID.randomUUID().toString()
        ExamEntity(
            id = localExamId,
            remoteId = remoteExamId,
            consultationId = localConsultationId,
            orderIndex = idx,
            name = dto.name,
            notes = dto.notes,
        )
    }
    val prescriptionEntities = prescriptionImages.map { img ->
        PrescriptionImageEntity(
            id = existingImageIdByS3[img.s3Key] ?: java.util.UUID.randomUUID().toString(),
            consultationId = localConsultationId,
            s3Key = img.s3Key,
            localPath = null,
            uploadedAt = img.uploadedAt.toInstantOrNull()?.toEpochMilliseconds(),
            uploadStatus = UploadStatus.UPLOADED.name,
        )
    }
    val resultEntities = exams.flatMapIndexed { idx, examDto ->
        val remoteExamId = examDto.id
        val localExamId = examsEntities[idx].id
        examDto.resultImages.map { img ->
            ResultImageEntity(
                id = existingImageIdByS3[img.s3Key] ?: java.util.UUID.randomUUID().toString(),
                examId = localExamId,
                s3Key = img.s3Key,
                localPath = null,
                uploadedAt = img.uploadedAt.toInstantOrNull()?.toEpochMilliseconds(),
                uploadStatus = UploadStatus.UPLOADED.name,
            )
        }
    }
    return ConsultationAggregate(
        consultation = consultation,
        medications = medsEntities,
        exams = examsEntities,
        prescriptionImages = prescriptionEntities,
        resultImages = resultEntities,
    )
}
