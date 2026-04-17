package com.example.nossasaudeapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.data.sync.SyncManager
import com.example.nossasaudeapp.data.sync.SyncState
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.ui.components.MemberCardUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.ui.util.ageLabel
import com.example.nossasaudeapp.ui.util.toShortDate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

data class HomeUiState(
    val members: List<MemberCardUi> = emptyList(),
    val syncState: SyncState = SyncState.Idle,
    val stats: HomeStats = HomeStats(),
    val isLoading: Boolean = true,
)

data class HomeStats(
    val memberCount: Int = 0,
    val totalConsultations: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val syncManager: SyncManager,
    consultationDao: ConsultationDao,
) : ViewModel() {

    /** Maps memberId → list of active consultations sorted newest first. */
    private val consultationsByMemberFlow = consultationDao.observeActive()
        .map { list ->
            list
                .filter { it.deletedAt == null }
                .sortedByDescending { it.date }
                .groupBy { it.memberId }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        memberRepository.observeActive(),
        syncManager.state,
        consultationsByMemberFlow,
    ) { members, syncState, byMember ->
        HomeUiState(
            members = members.mapIndexed { idx, m ->
                val consultations = byMember[m.id] ?: emptyList()
                m.toCardUi(idx, consultations.firstOrNull())
            },
            syncState = syncState,
            stats = HomeStats(
                memberCount = members.size,
                totalConsultations = byMember.values.sumOf { it.size },
            ),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun syncNow() {
        viewModelScope.launch { syncManager.syncNow() }
    }
}

private fun Member.toCardUi(index: Int, lastConsultation: ConsultationEntity?): MemberCardUi =
    MemberCardUi(
        id = id,
        name = name,
        ageLabel = birthDate?.let { ageLabel(it, Clock.System.now()) },
        weightLabel = weightKg?.let { "%.0fkg".format(it) },
        bloodType = bloodType?.label,
        allergies = allergies,
        conditions = chronicConditions,
        lastConsultationLabel = lastConsultation?.let { c ->
            val date = Instant.fromEpochMilliseconds(c.date).toShortDate()
            val reason = c.reason.take(30).let { if (c.reason.length > 30) "$it…" else it }
            "$reason · $date"
        },
        avatarIndex = index,
    )

