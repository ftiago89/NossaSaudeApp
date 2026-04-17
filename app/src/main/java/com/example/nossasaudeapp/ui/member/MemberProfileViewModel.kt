package com.example.nossasaudeapp.ui.member

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.domain.model.Medication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberProfileUiState(
    val member: Member? = null,
    val consultations: List<Consultation> = emptyList(),
    val contraindicatedMeds: List<Medication> = emptyList(),
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
)

@HiltViewModel
class MemberProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberRepository: MemberRepository,
    private val consultationRepository: ConsultationRepository,
) : ViewModel() {

    private val memberId: String = requireNotNull(savedStateHandle["memberId"])

    private val memberFlow = memberRepository.observeActive()
        .map { list -> list.firstOrNull { it.id == memberId } }

    private val consultationsFlow = consultationRepository.observeByMember(memberId)

    val uiState: StateFlow<MemberProfileUiState> = combine(
        memberFlow,
        consultationsFlow,
    ) { member, consultations ->
        if (member == null) {
            MemberProfileUiState(isLoading = false, deleted = true)
        } else {
            val contraindicated = consultationRepository.getContraindicatedMedications(memberId)
            MemberProfileUiState(
                member = member,
                consultations = consultations,
                contraindicatedMeds = contraindicated,
                isLoading = false,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MemberProfileUiState(),
    )

    fun deleteMember() {
        viewModelScope.launch { memberRepository.delete(memberId) }
    }
}

