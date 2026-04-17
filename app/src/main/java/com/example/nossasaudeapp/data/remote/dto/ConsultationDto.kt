package com.example.nossasaudeapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DoctorDto(
    val name: String? = null,
    val specialty: String? = null,
    val customSpecialty: String? = null,
)

@Serializable
data class PrescriptionImageDto(
    val s3Key: String,
    val uploadedAt: String? = null,
)

@Serializable
data class MedicationDto(
    val name: String,
    val activeIngredient: String? = null,
    val dosage: String? = null,
    val form: String? = null,
    val frequency: String? = null,
    val contraindicated: Boolean = false,
    val restrictionReason: String? = null,
    val efficacy: String? = null,
    val sideEffects: String? = null,
)

/** DTO used when reading exams from the server (includes resultImages). */
@Serializable
data class ExamDto(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val notes: String? = null,
    val resultImages: List<PrescriptionImageDto> = emptyList(),
)

/** DTO used when writing exams to the server (no resultImages — managed via separate upload flow).
 *  [id] is the server-side _id; when present the server matches the existing exam and preserves
 *  its resultImages instead of creating a new document. */
@Serializable
data class ExamWriteDto(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val notes: String? = null,
)

@Serializable
data class ConsultationDto(
    @SerialName("_id") val id: String,
    val familyId: String,
    val memberId: String,
    val date: String,
    val reason: String,
    val doctor: DoctorDto = DoctorDto(),
    val clinic: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val returnOf: String? = null,
    val prescriptionImages: List<PrescriptionImageDto> = emptyList(),
    val medications: List<MedicationDto> = emptyList(),
    val exams: List<ExamDto> = emptyList(),
    val syncedAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ConsultationCreateDto(
    val memberId: String,
    val date: String,
    val reason: String,
    val doctor: DoctorDto? = null,
    val clinic: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val returnOf: String? = null,
    val medications: List<MedicationDto> = emptyList(),
    val exams: List<ExamWriteDto> = emptyList(),
)

@Serializable
data class AddPrescriptionImageDto(val s3Key: String)

@Serializable
data class AddExamImageDto(val examId: String, val s3Key: String)

@Serializable
data class RemovePrescriptionImageDto(val s3Key: String)

@Serializable
data class RemoveExamImageDto(val examId: String, val s3Key: String)

@Serializable
data class ConsultationPatchDto(
    val memberId: String? = null,
    val date: String? = null,
    val reason: String? = null,
    val doctor: DoctorDto? = null,
    val clinic: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val returnOf: String? = null,
    val medications: List<MedicationDto>? = null,
    val exams: List<ExamWriteDto>? = null,
    val addPrescriptionImage: AddPrescriptionImageDto? = null,
    val addExamImage: AddExamImageDto? = null,
    val removePrescriptionImage: RemovePrescriptionImageDto? = null,
    val removeExamImage: RemoveExamImageDto? = null,
)

@Serializable
data class UploadUrlRequestDto(
    val type: String,
    val contentType: String = "image/jpeg",
)

@Serializable
data class UploadUrlResponseDto(
    val uploadUrl: String,
    val s3Key: String,
)

@Serializable
data class PresignedImageDto(
    val s3Key: String,
    val url: String,
    val uploadedAt: String? = null,
)

@Serializable
data class ExamImagesGroupDto(
    val examId: String,
    val examName: String,
    val images: List<PresignedImageDto>,
)

@Serializable
data class ImagesResponseDto(
    val prescriptions: List<PresignedImageDto> = emptyList(),
    val exams: List<ExamImagesGroupDto> = emptyList(),
)

@Serializable
data class SyncResponseDto(
    val members: List<MemberDto> = emptyList(),
    val consultations: List<ConsultationDto> = emptyList(),
    val syncedAt: String,
)

@Serializable
data class ApiErrorDto(
    val timeStamp: String? = null,
    val status: Int? = null,
    val statusDescription: String? = null,
    val type: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
