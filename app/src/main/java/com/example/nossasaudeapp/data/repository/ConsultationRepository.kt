package com.example.nossasaudeapp.data.repository

import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.SearchDao
import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.data.local.entity.ConsultationFtsEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.mapper.ConsultationAggregate
import com.example.nossasaudeapp.data.mapper.toAggregate
import com.example.nossasaudeapp.data.mapper.toCreateDto
import com.example.nossasaudeapp.data.mapper.toDomain
import com.example.nossasaudeapp.data.mapper.toDto
import com.example.nossasaudeapp.data.mapper.toEntity
import com.example.nossasaudeapp.data.mapper.toInstantOrNull
import com.example.nossasaudeapp.data.mapper.toIso8601
import com.example.nossasaudeapp.data.remote.ApiException
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.data.remote.dto.ConsultationPatchDto
import com.example.nossasaudeapp.di.IoDispatcher
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Exam
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.domain.model.UploadStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationRepository @Inject constructor(
    private val dao: ConsultationDao,
    private val memberDao: MemberDao,
    private val searchDao: SearchDao,
    private val api: ConsultationsApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeByMember(memberId: String): Flow<List<Consultation>> =
        dao.observeByMember(memberId).map { list -> list.map { loadAggregate(it).toDomain() } }

    suspend fun getById(id: String): Consultation? = withContext(io) {
        dao.getById(id)?.let { loadAggregate(it).toDomain() }
    }

    suspend fun getContraindicatedMedications(memberId: String): List<Medication> = withContext(io) {
        dao.getContraindicatedForMember(memberId).map { it.toDomain() }
    }

    suspend fun create(
        memberId: String,
        date: Instant,
        reason: String,
        doctor: com.example.nossasaudeapp.domain.model.Doctor,
        clinic: String?,
        notes: String?,
        tags: List<String>,
        returnOf: String?,
        medications: List<Medication>,
        exams: List<Exam>,
    ): Consultation = withContext(io) {
        val now = Clock.System.now()
        val consultationId = UUID.randomUUID().toString()
        val entity = ConsultationEntity(
            id = consultationId,
            remoteId = null,
            memberId = memberId,
            date = date.toEpochMilliseconds(),
            reason = reason,
            doctorName = doctor.name,
            doctorSpecialty = doctor.specialty,
            doctorCustomSpecialty = doctor.customSpecialty,
            clinic = clinic,
            notes = notes,
            tags = tags,
            returnOf = returnOf,
            createdAt = now.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
            syncedAt = null,
            deletedAt = null,
        )
        val medEntities = medications.mapIndexed { idx, m ->
            m.toEntity(UUID.randomUUID().toString(), consultationId, idx)
        }
        val examsWithIds = exams.mapIndexed { idx, e ->
            val ensured = if (e.id.isBlank()) e.copy(id = UUID.randomUUID().toString()) else e
            ensured to idx
        }
        val examEntities = examsWithIds.map { (e, idx) -> e.toEntity(consultationId, idx) }
        dao.replaceFullConsultation(
            consultation = entity,
            medications = medEntities,
            exams = examEntities,
            prescriptionImages = emptyList(),
            resultImages = emptyList(),
        )
        updateFts(consultationId)
        loadAggregate(entity).toDomain()
    }

    suspend fun update(consultation: Consultation): Consultation = withContext(io) {
        val now = Clock.System.now()
        val entity = ConsultationEntity(
            id = consultation.id,
            remoteId = consultation.remoteId,
            memberId = consultation.memberId,
            date = consultation.date.toEpochMilliseconds(),
            reason = consultation.reason,
            doctorName = consultation.doctor.name,
            doctorSpecialty = consultation.doctor.specialty,
            doctorCustomSpecialty = consultation.doctor.customSpecialty,
            clinic = consultation.clinic,
            notes = consultation.notes,
            tags = consultation.tags,
            returnOf = consultation.returnOf,
            createdAt = consultation.createdAt.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
            syncedAt = consultation.syncedAt?.toEpochMilliseconds(),
            deletedAt = consultation.deletedAt?.toEpochMilliseconds(),
        )
        val medEntities = consultation.medications.mapIndexed { idx, m ->
            m.toEntity(UUID.randomUUID().toString(), consultation.id, idx)
        }
        val examEntities = consultation.exams.mapIndexed { idx, e ->
            val id = e.id.ifBlank { UUID.randomUUID().toString() }
            e.copy(id = id).toEntity(consultation.id, idx)
        }
        val existingPres = dao.getPrescriptionImages(consultation.id)
        val presEntities = consultation.prescriptionImages.map { img ->
            val existing = existingPres.firstOrNull { it.s3Key == img.s3Key }
            PrescriptionImageEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                consultationId = consultation.id,
                s3Key = img.s3Key,
                localPath = img.localPath ?: existing?.localPath,
                uploadedAt = img.uploadedAt?.toEpochMilliseconds() ?: existing?.uploadedAt,
                uploadStatus = img.uploadStatus.name,
            )
        }
        val resultEntities = consultation.exams.flatMap { e ->
            val eid = e.id.ifBlank { return@flatMap emptyList<ResultImageEntity>() }
            e.resultImages.map { img ->
                ResultImageEntity(
                    id = UUID.randomUUID().toString(),
                    examId = eid,
                    s3Key = img.s3Key,
                    localPath = img.localPath,
                    uploadedAt = img.uploadedAt?.toEpochMilliseconds(),
                    uploadStatus = img.uploadStatus.name,
                )
            }
        }
        dao.replaceFullConsultation(entity, medEntities, examEntities, presEntities, resultEntities)
        updateFts(consultation.id)
        loadAggregate(entity).toDomain()
    }

    suspend fun delete(id: String) = withContext(io) {
        val now = Clock.System.now().toEpochMilliseconds()
        dao.markDeleted(id, now)
        searchDao.deleteById(id)
    }

    suspend fun getDirtyIds(): List<String> = withContext(io) {
        dao.getDirty().map { it.id }
    }

    /** Push a single dirty consultation. */
    suspend fun pushDirty(localId: String): Boolean = withContext(io) {
        val entity = dao.getById(localId) ?: return@withContext false
        val aggregate = loadAggregate(entity)
        val domain = aggregate.toDomain()
        val now = Clock.System.now().toEpochMilliseconds()
        try {
            if (entity.deletedAt != null && entity.remoteId == null) {
                // Never reached the server — just discard locally
                dao.hardDelete(entity.id)
            } else if (entity.deletedAt != null && entity.remoteId != null) {
                api.delete(entity.remoteId)
                dao.hardDelete(entity.id)
            } else if (entity.remoteId == null) {
                val remoteMemberId = memberDao.getById(entity.memberId)?.remoteId
                    ?: error("Membro ${entity.memberId} ainda não sincronizado; não é possível criar a consulta no servidor.")
                val created = api.create(domain.toCreateDto(remoteMemberId))
                reconcileRemote(entity.id, created)
                dao.markSynced(entity.id, now, created.id)
            } else {
                val patched = api.update(
                    entity.remoteId,
                    ConsultationPatchDto(
                        memberId = null,
                        date = domain.date.toIso8601(),
                        reason = domain.reason,
                        doctor = domain.doctor.toDto(),
                        clinic = domain.clinic,
                        notes = domain.notes,
                        tags = domain.tags,
                        returnOf = domain.returnOf,
                        medications = domain.medications.map { it.toDto() },
                        exams = domain.exams.map { it.toDto() },
                    ),
                )
                reconcileRemote(entity.id, patched)
                dao.markSynced(entity.id, now, patched.id)
            }
            true
        } catch (e: ApiException) {
            if (e.isNotFound && entity.deletedAt != null) {
                dao.hardDelete(entity.id); true
            } else throw e
        }
    }

    /** After server create/update, persist assigned remote IDs (exam _ids) locally. */
    private suspend fun reconcileRemote(
        localId: String,
        remote: com.example.nossasaudeapp.data.remote.dto.ConsultationDto,
    ) {
        val existingExams = dao.getExams(localId)
        val byName = existingExams.groupBy { it.name }.mapValues { it.value.toMutableList() }
        // Use in-place UPDATE instead of delete+reinsert to avoid the CASCADE that would wipe
        // result_images (ResultImageEntity has onDelete=CASCADE from ExamEntity).
        remote.exams.forEachIndexed { idx, examDto ->
            val pool = byName[examDto.name]
            val local = pool?.removeFirstOrNull() ?: existingExams.getOrNull(idx)
            if (local != null) dao.updateExamRemoteId(local.id, examDto.id)
        }
    }

    suspend fun updateFts(consultationId: String) = withContext(io) {
        val entity = dao.getById(consultationId) ?: return@withContext
        val meds = dao.getMedications(consultationId)
        val exams = dao.getExams(consultationId)
        searchDao.upsert(
            ConsultationFtsEntity(
                consultationId = consultationId,
                memberId = entity.memberId,
                reason = entity.reason,
                notes = entity.notes.orEmpty(),
                doctorName = entity.doctorName.orEmpty(),
                clinic = entity.clinic.orEmpty(),
                tags = entity.tags.joinToString(" "),
                medicationNames = meds.joinToString(" ") { it.name },
                examNames = exams.joinToString(" ") { it.name },
            ),
        )
    }

    private suspend fun loadAggregate(entity: ConsultationEntity): ConsultationAggregate {
        val meds = dao.getMedications(entity.id)
        val exams = dao.getExams(entity.id)
        val pres = dao.getPrescriptionImages(entity.id)
        val results = if (exams.isEmpty()) emptyList() else dao.getResultImages(exams.map { it.id })
        return ConsultationAggregate(entity, meds, exams, pres, results)
    }

    suspend fun savePulled(dto: com.example.nossasaudeapp.data.remote.dto.ConsultationDto) = withContext(io) {
        val existingEntity = dao.getByRemoteId(dto.id)
        val localId = existingEntity?.id ?: UUID.randomUUID().toString()

        // Last-write-wins: if the local record has unsent changes, only overwrite when the
        // server version is strictly newer. This prevents a failed push followed by a
        // successful pull from silently discarding local edits.
        if (existingEntity != null) {
            val localDirty = existingEntity.syncedAt == null || existingEntity.updatedAt > existingEntity.syncedAt
            if (localDirty) {
                val remoteUpdatedAt = dto.updatedAt.toInstantOrNull()?.toEpochMilliseconds()
                if (remoteUpdatedAt == null || remoteUpdatedAt <= existingEntity.updatedAt) return@withContext
            }
        }

        // Resolve the remote memberId to a local member UUID.
        // After the push fix, dto.memberId holds the member's remoteId on the server.
        val localMemberId = memberDao.getByRemoteId(dto.memberId)?.id
            ?: error("Membro remoto ${dto.memberId} não encontrado localmente; sincronize os membros antes das consultas.")

        val existingLocalExams = existingEntity?.let { dao.getExams(it.id) }.orEmpty()
        val existingExamsMap = existingLocalExams
            .mapNotNull { it.remoteId?.let { rid -> rid to it.id } }
            .toMap()
        val existingImagesMap = existingEntity?.let { dao.getPrescriptionImages(it.id) }
            .orEmpty()
            .associate { it.s3Key to it.id }

        // Preserve local images that are still pending upload — the server doesn't know
        // about them yet, so they are absent from the DTO. Without this, savePulled would
        // delete the placeholder rows before the upload phase runs, causing photos to
        // vanish from the UI until the next sync (when the server finally has them).
        val pendingPrescriptions = existingEntity?.let {
            dao.getPrescriptionImages(it.id)
                .filter { img -> img.uploadStatus == UploadStatus.PENDING.name }
        }.orEmpty()
        val pendingResultImages = if (existingLocalExams.isEmpty()) emptyList()
        else dao.getResultImages(existingLocalExams.map { it.id })
            .filter { img -> img.uploadStatus == UploadStatus.PENDING.name }

        val aggregate = dto.toAggregate(
            localConsultationId = localId,
            localMemberId = localMemberId,
            existingExamIdByRemote = existingExamsMap,
            existingImageIdByS3 = existingImagesMap,
        )
        dao.replaceFullConsultation(
            aggregate.consultation,
            aggregate.medications,
            aggregate.exams,
            aggregate.prescriptionImages + pendingPrescriptions,
            aggregate.resultImages + pendingResultImages,
        )
        updateFts(localId)
    }
}
