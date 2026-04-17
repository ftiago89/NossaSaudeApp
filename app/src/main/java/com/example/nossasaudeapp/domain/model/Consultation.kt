package com.example.nossasaudeapp.domain.model

import kotlinx.datetime.Instant

data class Doctor(
    val name: String?,
    val specialty: String?,
    val customSpecialty: String?,
)

data class Medication(
    val name: String,
    val activeIngredient: String?,
    val dosage: String?,
    val form: MedicationForm?,
    val frequency: String?,
    val contraindicated: Boolean,
    val restrictionReason: String?,
    val efficacy: Efficacy?,
    val sideEffects: String?,
)

data class Exam(
    val id: String,
    val remoteId: String?,
    val name: String,
    val notes: String?,
    val resultImages: List<PrescriptionImage>,
)

data class PrescriptionImage(
    val s3Key: String,
    val localPath: String?,
    val uploadedAt: Instant?,
    val uploadStatus: UploadStatus,
)

enum class UploadStatus { PENDING, UPLOADED }

data class Consultation(
    val id: String,
    val remoteId: String?,
    val memberId: String,
    val date: Instant,
    val reason: String,
    val doctor: Doctor,
    val clinic: String?,
    val notes: String?,
    val tags: List<String>,
    val returnOf: String?,
    val prescriptionImages: List<PrescriptionImage>,
    val medications: List<Medication>,
    val exams: List<Exam>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant?,
    val deletedAt: Instant?,
)
