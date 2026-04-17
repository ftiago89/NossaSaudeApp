package com.example.nossasaudeapp.ui.member

import androidx.lifecycle.SavedStateHandle
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.util.MainDispatcherRule
import com.example.nossasaudeapp.util.TestFixtures
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MemberProfileViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @MockK lateinit var memberRepository: MemberRepository
    @MockK lateinit var consultationRepository: ConsultationRepository

    private val memberId = "local-1"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun buildViewModel() = MemberProfileViewModel(
        savedStateHandle = SavedStateHandle(mapOf("memberId" to memberId)),
        memberRepository = memberRepository,
        consultationRepository = consultationRepository,
    )

    private fun buildMember() = Member(
        id = memberId,
        remoteId = "remote-1",
        name = "João Silva",
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

    // ── member present ─────────────────────────────────────────────────────────

    @Test
    fun `uiState emits member data when member is in active list`() = runTest {
        val member = buildMember()
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())
        coEvery { consultationRepository.getContraindicatedMedications(memberId) } returns emptyList()

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(member, state.member)
        assertFalse(state.isLoading)
        assertFalse(state.deleted)
    }

    @Test
    fun `uiState exposes consultations for the member`() = runTest {
        val member = buildMember()
        val consultation = TestFixtures.domainConsultation(id = "c1", memberId = memberId)
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { consultationRepository.observeByMember(memberId) } returns flowOf(listOf(consultation))
        coEvery { consultationRepository.getContraindicatedMedications(memberId) } returns emptyList()

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(1, state.consultations.size)
        assertEquals("c1", state.consultations[0].id)
    }

    // ── contraindicated medications ────────────────────────────────────────────

    @Test
    fun `uiState includes contraindicatedMeds returned by repository`() = runTest {
        val member = buildMember()
        val med = Medication(
            name = "Ibuprofeno",
            activeIngredient = null,
            dosage = null,
            form = null,
            frequency = null,
            contraindicated = true,
            restrictionReason = "alergia",
            efficacy = null,
            sideEffects = null,
        )
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())
        coEvery { consultationRepository.getContraindicatedMedications(memberId) } returns listOf(med)

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertEquals(1, state.contraindicatedMeds.size)
        assertEquals("Ibuprofeno", state.contraindicatedMeds[0].name)
    }

    @Test
    fun `uiState has empty contraindicatedMeds when none exist`() = runTest {
        val member = buildMember()
        every { memberRepository.observeActive() } returns flowOf(listOf(member))
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())
        coEvery { consultationRepository.getContraindicatedMedications(memberId) } returns emptyList()

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertTrue(state.contraindicatedMeds.isEmpty())
    }

    // ── member deleted ─────────────────────────────────────────────────────────

    @Test
    fun `uiState marks deleted true and member null when member not in active list`() = runTest {
        every { memberRepository.observeActive() } returns flowOf(emptyList())
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertTrue(state.deleted)
        assertNull(state.member)
        assertFalse(state.isLoading)
    }

    @Test
    fun `uiState marks deleted true when member list has different id`() = runTest {
        val otherMember = buildMember().copy(id = "other-id")
        every { memberRepository.observeActive() } returns flowOf(listOf(otherMember))
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())

        val state = buildViewModel().uiState.first { !it.isLoading }

        assertTrue(state.deleted)
        assertNull(state.member)
    }

    // ── deleteMember ───────────────────────────────────────────────────────────

    @Test
    fun `deleteMember calls memberRepository delete with the member id`() = runTest {
        every { memberRepository.observeActive() } returns flowOf(emptyList())
        every { consultationRepository.observeByMember(memberId) } returns flowOf(emptyList())
        coEvery { memberRepository.delete(memberId) } returns Unit

        buildViewModel().deleteMember()

        coVerify(exactly = 1) { memberRepository.delete(memberId) }
    }
}
