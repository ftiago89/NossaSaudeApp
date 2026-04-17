package com.example.nossasaudeapp.ui.home

import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.data.sync.SyncManager
import com.example.nossasaudeapp.data.sync.SyncState
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.util.MainDispatcherRule
import com.example.nossasaudeapp.util.TestFixtures
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @MockK lateinit var memberRepository: MemberRepository
    @MockK lateinit var syncManager: SyncManager
    @MockK lateinit var consultationDao: ConsultationDao

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun buildMember(id: String = "local-1", name: String = "João Silva") = Member(
        id = id,
        remoteId = "remote-$id",
        name = name,
        birthDate = null,
        bloodType = null,
        weightKg = null,
        heightCm = null,
        allergies = emptyList(),
        chronicConditions = emptyList(),
        createdAt = Instant.fromEpochMilliseconds(TestFixtures.T0),
        updatedAt = Instant.fromEpochMilliseconds(TestFixtures.T1),
        syncedAt = Instant.fromEpochMilliseconds(TestFixtures.T1),
        deletedAt = null,
    )

    private fun buildViewModel() = HomeViewModel(memberRepository, syncManager, consultationDao)

    // ── stats ──────────────────────────────────────────────────────────────────

    @Test
    fun `uiState has correct memberCount and totalConsultations`() = runTest {
        val m1 = buildMember("m1")
        val m2 = buildMember("m2")
        val consultation = TestFixtures.consultationEntity(id = "c1", memberId = "m1")
        every { memberRepository.observeActive() } returns flowOf(listOf(m1, m2))
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(listOf(consultation))

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(2, state.stats.memberCount)
        assertEquals(1, state.stats.totalConsultations)
    }

    @Test
    fun `uiState has zero stats when there are no members or consultations`() = runTest {
        every { memberRepository.observeActive() } returns flowOf(emptyList())
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(emptyList())

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(0, state.stats.memberCount)
        assertEquals(0, state.stats.totalConsultations)
        assertEquals(true, state.members.isEmpty())
    }

    // ── member card UI ─────────────────────────────────────────────────────────

    @Test
    fun `member without consultations has null lastConsultationLabel`() = runTest {
        val member = buildMember("m1")
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(emptyList())

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertNull(state.members.first().lastConsultationLabel)
    }

    @Test
    fun `member with consultation has non-null lastConsultationLabel containing reason and date`() = runTest {
        val member = buildMember("m1")
        val consultation = TestFixtures.consultationEntity(id = "c1", memberId = "m1")
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(listOf(consultation))

        val state = buildViewModel().uiState.first { !it.isLoading }

        val label = state.members.first().lastConsultationLabel
        assertFalse("lastConsultationLabel should not be null", label.isNullOrBlank())
        // label format is "reason · date"
        assertEquals(true, label!!.contains("·"))
    }

    @Test
    fun `most recent consultation is shown when member has multiple`() = runTest {
        val member = buildMember("m1")
        val older = TestFixtures.consultationEntity(id = "c1", memberId = "m1", updatedAt = TestFixtures.T1)
            .copy(date = TestFixtures.T1, reason = "Rotina")
        val newer = TestFixtures.consultationEntity(id = "c2", memberId = "m1", updatedAt = TestFixtures.T3)
            .copy(date = TestFixtures.T3, reason = "Emergência")
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(listOf(older, newer))

        val state = buildViewModel().uiState.first { !it.isLoading }

        val label = state.members.first().lastConsultationLabel
        assertEquals(true, label?.startsWith("Emergência"))
    }

    // ── sync state ─────────────────────────────────────────────────────────────

    @Test
    fun `uiState syncState reflects SyncManager state`() = runTest {
        every { memberRepository.observeActive() } returns flowOf(emptyList())
        every { syncManager.state } returns MutableStateFlow(SyncState.Running)
        every { consultationDao.observeActive() } returns flowOf(emptyList())

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(SyncState.Running, state.syncState)
    }

    // ── syncNow ────────────────────────────────────────────────────────────────

    @Test
    fun `syncNow delegates to syncManager`() = runTest {
        every { memberRepository.observeActive() } returns flowOf(emptyList())
        every { syncManager.state } returns MutableStateFlow(SyncState.Idle)
        every { consultationDao.observeActive() } returns flowOf(emptyList())
        coEvery { syncManager.syncNow() } returns Result.success(Unit)

        buildViewModel().syncNow()

        coVerify(exactly = 1) { syncManager.syncNow() }
    }
}
