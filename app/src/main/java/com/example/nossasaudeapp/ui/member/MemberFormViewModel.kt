package com.example.nossasaudeapp.ui.member

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.BloodType
import com.example.nossasaudeapp.domain.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class MemberFormState(
    val name: String = "",
    val birthDateText: String = "",     // "dd/mm/yyyy"
    val bloodType: BloodType? = null,
    val weightText: String = "",
    val heightText: String = "",
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val isEdit: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val nameError: Boolean get() = name.isBlank()
    val birthDateError: Boolean get() = birthDateText.isNotBlank() && parseBirthDate(birthDateText) == null
    val weightError: Boolean get() = weightText.isNotBlank() && weightText.toDoubleOrNull() == null
    val heightError: Boolean get() = heightText.isNotBlank() && heightText.toDoubleOrNull() == null
    val isValid: Boolean get() = !nameError && !birthDateError && !weightError && !heightError
}

@HiltViewModel
class MemberFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MemberRepository,
) : ViewModel() {

    private val editId: String? = savedStateHandle.get<String>("memberId")

    private val _state = MutableStateFlow(MemberFormState(isEdit = editId != null))
    val state: StateFlow<MemberFormState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val member = repository.getById(editId) ?: return@launch
                _state.update { fromMember(member) }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onBirthDate(v: String) {
        _state.update { it.copy(birthDateText = v.filter { c -> c.isDigit() }.take(8)) }
    }
    fun onBloodType(v: BloodType?) = _state.update { it.copy(bloodType = v) }
    fun onWeight(v: String) = _state.update { it.copy(weightText = v) }
    fun onHeight(v: String) = _state.update { it.copy(heightText = v) }
    fun onAddAllergy(v: String) = _state.update { it.copy(allergies = (it.allergies + v).distinct()) }
    fun onRemoveAllergy(v: String) = _state.update { it.copy(allergies = it.allergies - v) }
    fun onAddCondition(v: String) = _state.update { it.copy(chronicConditions = (it.chronicConditions + v).distinct()) }
    fun onRemoveCondition(v: String) = _state.update { it.copy(chronicConditions = it.chronicConditions - v) }

    fun save() {
        val s = _state.value
        if (!s.isValid) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val birthDate = s.birthDateText.takeIf { it.isNotBlank() }?.let {
                    parseBirthDate(it)
                }
                if (editId != null) {
                    val existing = repository.getById(editId)!!
                    repository.update(
                        existing.copy(
                            name = s.name.trim(),
                            birthDate = birthDate,
                            bloodType = s.bloodType,
                            weightKg = s.weightText.toDoubleOrNull(),
                            heightCm = s.heightText.toDoubleOrNull(),
                            allergies = s.allergies,
                            chronicConditions = s.chronicConditions,
                        ),
                    )
                } else {
                    repository.create(
                        name = s.name.trim(),
                        birthDate = birthDate,
                        bloodType = s.bloodType,
                        weightKg = s.weightText.toDoubleOrNull(),
                        heightCm = s.heightText.toDoubleOrNull(),
                        allergies = s.allergies,
                        chronicConditions = s.chronicConditions,
                    )
                }
            }.onSuccess {
                _state.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "Erro ao salvar") }
            }
        }
    }
}

private fun fromMember(m: Member): MemberFormState = MemberFormState(
    name = m.name,
    birthDateText = m.birthDate?.let { formatInstantAsDate(it) } ?: "",
    bloodType = m.bloodType,
    weightText = m.weightKg?.let { formatDecimal(it) } ?: "",
    heightText = m.heightCm?.let { formatDecimal(it) } ?: "",
    allergies = m.allergies,
    chronicConditions = m.chronicConditions,
    isEdit = true,
)

/** Formats a Double as an integer string when it has no fractional part, otherwise uses dot as
 *  decimal separator regardless of the device locale, matching what [toDoubleOrNull] expects. */
private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private fun formatInstantAsDate(instant: Instant): String {
    val ld = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "%02d%02d%04d".format(ld.dayOfMonth, ld.monthNumber, ld.year)
}

fun parseBirthDate(text: String): Instant? {
    val digits = text.filter { it.isDigit() }
    if (digits.length != 8) return null
    return runCatching {
        val day = digits.substring(0, 2).toInt()
        val month = digits.substring(2, 4).toInt()
        val year = digits.substring(4, 8).toInt()
        LocalDate(year, month, day).atStartOfDayIn(TimeZone.currentSystemDefault())
    }.getOrNull()
}
