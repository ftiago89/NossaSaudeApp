package com.example.nossasaudeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.data.local.entity.ExamEntity
import com.example.nossasaudeapp.data.local.entity.MedicationEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {

    @Query("SELECT * FROM consultations WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeActive(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE memberId = :memberId AND deletedAt IS NULL ORDER BY date DESC")
    fun observeByMember(memberId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id")
    suspend fun getById(id: String): ConsultationEntity?

    @Query("SELECT * FROM consultations WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ConsultationEntity?

    @Query("SELECT * FROM consultations WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirty(): List<ConsultationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(consultation: ConsultationEntity)

    @Upsert
    suspend fun upsertAll(consultations: List<ConsultationEntity>)

    @Query("UPDATE consultations SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE consultations SET syncedAt = :syncedAt, remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long, remoteId: String)

    // --- Medications ---
    @Query("SELECT * FROM medications WHERE consultationId = :consultationId ORDER BY orderIndex")
    suspend fun getMedications(consultationId: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE consultationId IN (:ids)")
    suspend fun getMedicationsFor(ids: List<String>): List<MedicationEntity>

    @Query(
        "SELECT m.* FROM medications m " +
                "INNER JOIN consultations c ON c.id = m.consultationId " +
                "WHERE c.memberId = :memberId AND c.deletedAt IS NULL AND m.contraindicated = 1"
    )
    suspend fun getContraindicatedForMember(memberId: String): List<MedicationEntity>

    @Query("DELETE FROM medications WHERE consultationId = :consultationId")
    suspend fun deleteMedicationsFor(consultationId: String)

    @Insert
    suspend fun insertMedications(medications: List<MedicationEntity>)

    // --- Exams ---
    @Query("SELECT * FROM exams WHERE consultationId = :consultationId ORDER BY orderIndex")
    suspend fun getExams(consultationId: String): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE consultationId IN (:ids)")
    suspend fun getExamsFor(ids: List<String>): List<ExamEntity>

    @Query("DELETE FROM exams WHERE consultationId = :consultationId")
    suspend fun deleteExamsFor(consultationId: String)

    @Query("UPDATE exams SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateExamRemoteId(id: String, remoteId: String?)

    @Insert
    suspend fun insertExams(exams: List<ExamEntity>)

    // --- Prescription images ---
    @Query("SELECT * FROM prescription_images WHERE consultationId = :consultationId")
    suspend fun getPrescriptionImages(consultationId: String): List<PrescriptionImageEntity>

    @Query("DELETE FROM prescription_images WHERE consultationId = :consultationId")
    suspend fun deletePrescriptionImages(consultationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptionImages(images: List<PrescriptionImageEntity>)

    @Query("UPDATE prescription_images SET uploadStatus = :status, uploadedAt = :uploadedAt WHERE s3Key = :s3Key")
    suspend fun markPrescriptionUploaded(s3Key: String, status: String, uploadedAt: Long)

    // --- Result images ---
    @Query("SELECT * FROM result_images WHERE examId IN (:examIds)")
    suspend fun getResultImages(examIds: List<String>): List<ResultImageEntity>

    @Query("DELETE FROM result_images WHERE examId = :examId")
    suspend fun deleteResultImagesFor(examId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResultImages(images: List<ResultImageEntity>)

    @Query("UPDATE result_images SET uploadStatus = :status, uploadedAt = :uploadedAt WHERE s3Key = :s3Key")
    suspend fun markResultUploaded(s3Key: String, status: String, uploadedAt: Long)

    @Transaction
    suspend fun replaceFullConsultation(
        consultation: ConsultationEntity,
        medications: List<MedicationEntity>,
        exams: List<ExamEntity>,
        prescriptionImages: List<PrescriptionImageEntity>,
        resultImages: List<ResultImageEntity>,
    ) {
        insert(consultation)
        deleteMedicationsFor(consultation.id)
        if (medications.isNotEmpty()) insertMedications(medications)
        val existingExams = getExams(consultation.id)
        existingExams.forEach { deleteResultImagesFor(it.id) }
        deleteExamsFor(consultation.id)
        if (exams.isNotEmpty()) insertExams(exams)
        if (resultImages.isNotEmpty()) insertResultImages(resultImages)
        deletePrescriptionImages(consultation.id)
        if (prescriptionImages.isNotEmpty()) insertPrescriptionImages(prescriptionImages)
    }
}
