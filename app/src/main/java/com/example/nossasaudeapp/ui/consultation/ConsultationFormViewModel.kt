package com.example.nossasaudeapp.ui.consultation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.image.S3ImageLoader
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.ImageRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Doctor
import com.example.nossasaudeapp.domain.model.Exam
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.domain.model.MedicationForm
import com.example.nossasaudeapp.domain.model.Efficacy
import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.ui.member.parseBirthDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject

data class MedicationDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val activeIngredient: String = "",
    val dosage: String = "",
    val form: MedicationForm? = null,
    val frequency: String = "",
    val contraindicated: Boolean = false,
    val restrictionReason: String = "",
    val efficacy: Efficacy? = null,
    val sideEffects: String = "",
)

/** A saved image entry shown in edit mode (either still pending upload or already on S3). */
data class ImageEditEntry(
    val s3Key: String,
    val localPath: String?,    // non-null for PENDING images
    val presignedUrl: String?, // non-null for UPLOADED images once resolved
)

data class ExamDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val notes: String = "",
    val resultLocalPaths: List<String> = emptyList(),
    val existingResultImages: List<ImageEditEntry> = emptyList(),
    val deletedResultS3Keys: List<String> = emptyList(),
)

data class ConsultationFormState(
    val memberId: String = "",
    val memberName: String = "",
    val dateText: String = "",
    val reason: String = "",
    val doctorName: String = "",
    val specialty: String = "",
    val customSpecialty: String = "",
    val clinic: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val returnOf: String = "",
    val medications: List<MedicationDraft> = emptyList(),
    val exams: List<ExamDraft> = emptyList(),
    val prescriptionLocalPaths: List<String> = emptyList(),
    val existingPrescriptionImages: List<ImageEditEntry> = emptyList(),
    val deletedPrescriptionS3Keys: List<String> = emptyList(),
    val isEdit: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val savedId: String? = null,
    val error: String? = null,
    val contraindicatedNames: Set<String> = emptySet(),
) {
    val reasonError get() = reason.isBlank()
    val dateError get() = dateText.isNotBlank() && parseBirthDate(dateText) == null
    val isValid get() = !reasonError && !dateError
}

@HiltViewModel
class ConsultationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consultationRepository: ConsultationRepository,
    private val memberRepository: MemberRepository,
    private val imageRepository: ImageRepository,
    private val s3ImageLoader: S3ImageLoader,
) : ViewModel() {

    private val paramMemberId: String? = savedStateHandle["memberId"]
    private val editId: String? = savedStateHandle["consultationId"]

    private val _state = MutableStateFlow(ConsultationFormState(isEdit = editId != null))
    val state: StateFlow<ConsultationFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val memberId = paramMemberId ?: run {
                val cons = editId?.let { consultationRepository.getById(it) }
                cons?.memberId
            } ?: return@launch
            _state.update { it.copy(memberId = memberId) }
            val member = memberRepository.getById(memberId)
            if (member != null) {
                val contraindicated = consultationRepository
                    .getContraindicatedMedications(memberId)
                    .map { it.name.lowercase() }
                    .toSet()
                _state.update { it.copy(memberName = member.name, contraindicatedNames = contraindicated) }
            }
            if (editId != null) {
                val consultation = consultationRepository.getById(editId) ?: return@launch
                val base = fromConsultation(consultation)
                _state.update { base.copy(memberName = it.memberName, contraindicatedNames = it.contraindicatedNames) }
                loadPresignedUrls(consultation.remoteId)
            } else {
                val today = formatToday()
                _state.update { it.copy(dateText = today) }
            }
        }
    }

    fun onDateText(v: String) = _state.update { it.copy(dateText = v) }
    fun onReason(v: String) = _state.update { it.copy(reason = v) }
    fun onDoctorName(v: String) = _state.update { it.copy(doctorName = v) }
    fun onSpecialty(v: String) = _state.update { it.copy(specialty = v) }
    fun onCustomSpecialty(v: String) = _state.update { it.copy(customSpecialty = v) }
    fun onClinic(v: String) = _state.update { it.copy(clinic = v) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }
    fun onAddTag(v: String) = _state.update { it.copy(tags = (it.tags + v).distinct()) }
    fun onRemoveTag(v: String) = _state.update { it.copy(tags = it.tags - v) }
    fun onReturnOf(v: String) = _state.update { it.copy(returnOf = v) }

    // Medications
    fun addMedication() = _state.update { it.copy(medications = it.medications + MedicationDraft()) }
    fun removeMedication(id: String) = _state.update { it.copy(medications = it.medications.filter { m -> m.id != id }) }
    fun updateMedication(updated: MedicationDraft) = _state.update {
        it.copy(medications = it.medications.map { m -> if (m.id == updated.id) updated else m })
    }

    // Exams
    fun addExam() = _state.update { it.copy(exams = it.exams + ExamDraft()) }
    fun removeExam(id: String) = _state.update { it.copy(exams = it.exams.filter { e -> e.id != id }) }
    fun updateExam(updated: ExamDraft) = _state.update {
        it.copy(exams = it.exams.map { e -> if (e.id == updated.id) updated else e })
    }
    fun addExamImage(examId: String, uri: Uri) {
        viewModelScope.launch {
            val path = imageRepository.saveLocalCopy(uri)
            _state.update { s ->
                s.copy(exams = s.exams.map { e ->
                    if (e.id == examId) e.copy(resultLocalPaths = e.resultLocalPaths + path) else e
                })
            }
        }
    }
    fun removeExamImage(examId: String, path: String) = _state.update { s ->
        s.copy(exams = s.exams.map { e ->
            if (e.id == examId) e.copy(resultLocalPaths = e.resultLocalPaths - path) else e
        })
    }

    // Prescriptions
    fun addPrescriptionImage(uri: Uri) {
        viewModelScope.launch {
            val path = imageRepository.saveLocalCopy(uri)
            _state.update { it.copy(prescriptionLocalPaths = it.prescriptionLocalPaths + path) }
        }
    }
    fun removePrescriptionImage(path: String) =
        _state.update { it.copy(prescriptionLocalPaths = it.prescriptionLocalPaths - path) }

    fun deleteExistingPrescriptionImage(s3Key: String) = _state.update { s ->
        s.copy(
            existingPrescriptionImages = s.existingPrescriptionImages.filter { it.s3Key != s3Key },
            deletedPrescriptionS3Keys = s.deletedPrescriptionS3Keys + s3Key,
        )
    }

    fun deleteExistingExamImage(examId: String, s3Key: String) = _state.update { s ->
        s.copy(exams = s.exams.map { e ->
            if (e.id != examId) e else e.copy(
                existingResultImages = e.existingResultImages.filter { it.s3Key != s3Key },
                deletedResultS3Keys = e.deletedResultS3Keys + s3Key,
            )
        })
    }

    private fun loadPresignedUrls(remoteId: String?) {
        if (remoteId == null) return
        viewModelScope.launch {
            runCatching {
                val s = _state.value
                val uploadedKeys = buildSet {
                    s.existingPrescriptionImages.filter { it.localPath == null }.forEach { add(it.s3Key) }
                    s.exams.flatMap { it.existingResultImages }.filter { it.localPath == null }.forEach { add(it.s3Key) }
                }
                if (uploadedKeys.isEmpty()) return@runCatching
                uploadedKeys.forEach { s3ImageLoader.presignedUrl(remoteId, it) }
                val urlByKey = uploadedKeys.associateWith { s3ImageLoader.cachedUrl(it) }
                _state.update { st ->
                    st.copy(
                        existingPrescriptionImages = st.existingPrescriptionImages.map { img ->
                            if (img.presignedUrl != null) img else img.copy(presignedUrl = urlByKey[img.s3Key])
                        },
                        exams = st.exams.map { draft ->
                            draft.copy(existingResultImages = draft.existingResultImages.map { img ->
                                if (img.presignedUrl != null) img else img.copy(presignedUrl = urlByKey[img.s3Key])
                            })
                        },
                    )
                }
            }
        }
    }

    fun isContraindicated(medicationName: String): Boolean =
        medicationName.isNotBlank() && _state.value.contraindicatedNames.contains(medicationName.lowercase())

    fun save() {
        val s = _state.value
        if (!s.isValid || s.memberId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val date = parseBirthDate(s.dateText) ?: Clock.System.now()
                val doctor = Doctor(
                    name = s.doctorName.ifBlank { null },
                    specialty = s.specialty.ifBlank { null },
                    customSpecialty = s.customSpecialty.ifBlank { null },
                )
                val medications = s.medications
                    .filter { it.name.isNotBlank() }
                    .map { it.toDomain() }
                if (editId != null) {
                    val existing = consultationRepository.getById(editId)!!
                    val existingExamById = existing.exams.associateBy { it.id }

                    // Process deleted prescription images
                    for (s3Key in s.deletedPrescriptionS3Keys) {
                        val img = existing.prescriptionImages.firstOrNull { it.s3Key == s3Key } ?: continue
                        if (img.uploadStatus == UploadStatus.PENDING) {
                            img.localPath?.let { imageRepository.cancelPendingUpload(it) }
                        } else {
                            existing.remoteId?.let { imageRepository.removeUploadedPrescription(it, s3Key) }
                        }
                    }

                    // Process deleted exam result images
                    for (draft in s.exams) {
                        if (draft.deletedResultS3Keys.isEmpty()) continue
                        val existingExam = existingExamById[draft.id] ?: continue
                        for (s3Key in draft.deletedResultS3Keys) {
                            val img = existingExam.resultImages.firstOrNull { it.s3Key == s3Key } ?: continue
                            if (img.uploadStatus == UploadStatus.PENDING) {
                                img.localPath?.let { imageRepository.cancelPendingUpload(it) }
                            } else {
                                val remoteId = existing.remoteId ?: continue
                                val examRemoteId = existingExam.remoteId ?: continue
                                imageRepository.removeUploadedExam(remoteId, examRemoteId, s3Key)
                            }
                        }
                    }

                    val exams = s.exams
                        .filter { it.name.isNotBlank() }
                        .map { draft ->
                            val existingExam = existingExamById[draft.id]
                            Exam(
                                id = draft.id,
                                remoteId = existingExam?.remoteId,
                                name = draft.name,
                                notes = draft.notes.ifBlank { null },
                                resultImages = (existingExam?.resultImages ?: emptyList())
                                    .filter { it.s3Key !in draft.deletedResultS3Keys },
                            )
                        }
                    val updated = existing.copy(
                        date = date,
                        reason = s.reason.trim(),
                        doctor = doctor,
                        clinic = s.clinic.ifBlank { null },
                        notes = s.notes.ifBlank { null },
                        tags = s.tags,
                        returnOf = s.returnOf.ifBlank { null },
                        medications = medications,
                        exams = exams,
                        prescriptionImages = existing.prescriptionImages
                            .filter { it.s3Key !in s.deletedPrescriptionS3Keys },
                    )
                    consultationRepository.update(updated)
                    s.prescriptionLocalPaths.forEach { path ->
                        imageRepository.enqueuePrescription(editId, path)
                    }
                    s.exams.forEach { draft ->
                        draft.resultLocalPaths.forEach { path ->
                            imageRepository.enqueueExam(editId, draft.id, path)
                        }
                    }
                    _state.update { it.copy(isSaving = false, saved = true, savedId = editId) }
                } else {
                    val exams = s.exams
                        .filter { it.name.isNotBlank() }
                        .map { draft ->
                            Exam(id = draft.id, remoteId = null, name = draft.name, notes = draft.notes.ifBlank { null }, resultImages = emptyList())
                        }
                    val created = consultationRepository.create(
                        memberId = s.memberId,
                        date = date,
                        reason = s.reason.trim(),
                        doctor = doctor,
                        clinic = s.clinic.ifBlank { null },
                        notes = s.notes.ifBlank { null },
                        tags = s.tags,
                        returnOf = s.returnOf.ifBlank { null },
                        medications = medications,
                        exams = exams,
                    )
                    s.prescriptionLocalPaths.forEach { path ->
                        imageRepository.enqueuePrescription(created.id, path)
                    }
                    // Match draft exams to created exams by position (same order as submitted)
                    val createdExams = created.exams
                    s.exams.filter { it.name.isNotBlank() }.forEachIndexed { i, draft ->
                        val examId = createdExams.getOrNull(i)?.id ?: return@forEachIndexed
                        draft.resultLocalPaths.forEach { path ->
                            imageRepository.enqueueExam(created.id, examId, path)
                        }
                    }
                    _state.update { it.copy(isSaving = false, saved = true, savedId = created.id) }
                }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "Erro ao salvar") }
            }
        }
    }
}

private fun MedicationDraft.toDomain() = Medication(
    name = name.trim(),
    activeIngredient = activeIngredient.ifBlank { null },
    dosage = dosage.ifBlank { null },
    form = form,
    frequency = frequency.ifBlank { null },
    contraindicated = contraindicated,
    restrictionReason = restrictionReason.ifBlank { null },
    efficacy = efficacy,
    sideEffects = sideEffects.ifBlank { null },
)

private fun fromConsultation(c: Consultation): ConsultationFormState = ConsultationFormState(
    memberId = c.memberId,
    dateText = "%02d/%02d/%04d".format(
        c.date.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfMonth,
        c.date.toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber,
        c.date.toLocalDateTime(TimeZone.currentSystemDefault()).year,
    ),
    reason = c.reason,
    doctorName = c.doctor.name.orEmpty(),
    specialty = c.doctor.specialty.orEmpty(),
    customSpecialty = c.doctor.customSpecialty.orEmpty(),
    clinic = c.clinic.orEmpty(),
    notes = c.notes.orEmpty(),
    tags = c.tags,
    returnOf = c.returnOf.orEmpty(),
    medications = c.medications.map { m ->
        MedicationDraft(
            id = UUID.randomUUID().toString(),
            name = m.name,
            activeIngredient = m.activeIngredient.orEmpty(),
            dosage = m.dosage.orEmpty(),
            form = m.form,
            frequency = m.frequency.orEmpty(),
            contraindicated = m.contraindicated,
            restrictionReason = m.restrictionReason.orEmpty(),
            efficacy = m.efficacy,
            sideEffects = m.sideEffects.orEmpty(),
        )
    },
    exams = c.exams.map { e ->
        ExamDraft(
            id = e.id,
            name = e.name,
            notes = e.notes.orEmpty(),
            existingResultImages = e.resultImages.map { img ->
                ImageEditEntry(s3Key = img.s3Key, localPath = img.localPath, presignedUrl = null)
            },
        )
    },
    existingPrescriptionImages = c.prescriptionImages.map { img ->
        ImageEditEntry(s3Key = img.s3Key, localPath = img.localPath, presignedUrl = null)
    },
    isEdit = true,
)

private fun formatToday(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(now.dayOfMonth, now.monthNumber, now.year)
}
