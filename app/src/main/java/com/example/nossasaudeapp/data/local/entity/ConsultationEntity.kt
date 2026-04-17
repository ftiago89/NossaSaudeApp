package com.example.nossasaudeapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consultations",
    indices = [
        Index("memberId"),
        Index("remoteId", unique = true),
        Index("date"),
        Index("deletedAt"),
    ],
)
data class ConsultationEntity(
    @PrimaryKey val id: String,
    val remoteId: String?,
    val memberId: String,
    val date: Long,
    val reason: String,
    val doctorName: String?,
    val doctorSpecialty: String?,
    val doctorCustomSpecialty: String?,
    val clinic: String?,
    val notes: String?,
    val tags: List<String>,
    val returnOf: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long?,
    val deletedAt: Long?,
)

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("name")],
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val orderIndex: Int,
    val name: String,
    val activeIngredient: String?,
    val dosage: String?,
    val form: String?,
    val frequency: String?,
    val contraindicated: Boolean,
    val restrictionReason: String?,
    val efficacy: String?,
    val sideEffects: String?,
)

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("remoteId", unique = true)],
)
data class ExamEntity(
    @PrimaryKey val id: String,
    val remoteId: String?,
    val consultationId: String,
    val orderIndex: Int,
    val name: String,
    val notes: String?,
)

@Entity(
    tableName = "prescription_images",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("s3Key", unique = true)],
)
data class PrescriptionImageEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val s3Key: String,
    val localPath: String?,
    val uploadedAt: Long?,
    val uploadStatus: String,
)

@Entity(
    tableName = "result_images",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId"), Index("s3Key", unique = true)],
)
data class ResultImageEntity(
    @PrimaryKey val id: String,
    val examId: String,
    val s3Key: String,
    val localPath: String?,
    val uploadedAt: Long?,
    val uploadStatus: String,
)

@Entity(
    tableName = "pending_uploads",
    indices = [Index("consultationId")],
)
data class PendingUploadEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val examId: String?,
    val type: String, // prescription | exam
    val localPath: String,
    val contentType: String,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
