package com.example.nossasaudeapp.data.repository

import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.SearchDao
import com.example.nossasaudeapp.data.local.entity.ConsultationFtsEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.remote.ApiException
import com.example.nossasaudeapp.data.remote.api.ConsultationsApi
import com.example.nossasaudeapp.domain.model.UploadStatus
import com.example.nossasaudeapp.util.TestFixtures.T1
import com.example.nossasaudeapp.util.TestFixtures.T3
import com.example.nossasaudeapp.util.TestFixtures.consultationDto
import com.example.nossasaudeapp.util.TestFixtures.consultationEntity
import com.example.nossasaudeapp.util.TestFixtures.examEntity
import com.example.nossasaudeapp.util.TestFixtures.memberEntity
import com.example.nossasaudeapp.util.TestFixtures.prescriptionImageEntity
import com.example.nossasaudeapp.util.TestFixtures.resultImageEntity
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsultationRepositoryTest {

    @MockK lateinit var dao: ConsultationDao
    @MockK lateinit var memberDao: MemberDao
    @MockK lateinit var searchDao: SearchDao
    @MockK lateinit var api: ConsultationsApi

    private lateinit var repository: ConsultationRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = ConsultationRepository(dao, memberDao, searchDao, api, UnconfinedTestDispatcher())
    }

    // Stubs all DAO calls that loadAggregate makes for a consultation with no children.
    private fun stubEmptyAggregate(consultationId: String) {
        coEvery { dao.getMedications(consultationId) } returns emptyList()
        coEvery { dao.getExams(consultationId) } returns emptyList()
        coEvery { dao.getPrescriptionImages(consultationId) } returns emptyList()
    }

    // Stubs all DAO calls that updateFts makes.
    private fun stubUpdateFts(consultationId: String) {
        coEvery { dao.getById(consultationId) } returns consultationEntity(id = consultationId)
        coEvery { dao.getMedications(consultationId) } returns emptyList()
        coEvery { dao.getExams(consultationId) } returns emptyList()
        coEvery { searchDao.upsert(any<ConsultationFtsEntity>()) } returns Unit
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — entity not found
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty returns false when entity not found`() = runTest {
        coEvery { dao.getById("ghost") } returns null

        val result = repository.pushDirty("ghost")

        assertFalse(result)
        coVerify(exactly = 0) { api.create(any()) }
        coVerify(exactly = 0) { api.update(any(), any()) }
        coVerify(exactly = 0) { api.delete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — soft-deleted, never on server
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty hard-deletes locally when deletedAt set and remoteId is null`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = null, deletedAt = 1L)
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { dao.hardDelete("c1") } returns Unit

        assertTrue(repository.pushDirty("c1"))
        coVerify(exactly = 1) { dao.hardDelete("c1") }
        coVerify(exactly = 0) { api.delete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — soft-deleted, already on server
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty calls remote DELETE then hard-deletes locally`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = "r-c1", deletedAt = 1L)
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { api.delete("r-c1") } returns Unit
        coEvery { dao.hardDelete("c1") } returns Unit

        assertTrue(repository.pushDirty("c1"))
        coVerify(exactly = 1) { api.delete("r-c1") }
        coVerify(exactly = 1) { dao.hardDelete("c1") }
    }

    @Test
    fun `pushDirty hard-deletes locally when remote DELETE returns 404`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = "r-c1", deletedAt = 1L)
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { api.delete("r-c1") } throws ApiException(404, "NOT_FOUND", "not found")
        coEvery { dao.hardDelete("c1") } returns Unit

        assertTrue(repository.pushDirty("c1"))
        coVerify(exactly = 1) { dao.hardDelete("c1") }
    }

    @Test
    fun `pushDirty re-throws non-404 errors on DELETE`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = "r-c1", deletedAt = 1L)
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { api.delete("r-c1") } throws ApiException(500, "SERVER_ERROR", "oops")

        var threw = false
        try { repository.pushDirty("c1") } catch (e: ApiException) { threw = true }

        assertTrue(threw)
        coVerify(exactly = 0) { dao.hardDelete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — new record (no remoteId) → POST
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty POSTs new consultation using member remoteId and marks synced`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = null, deletedAt = null)
        val member = memberEntity(id = "local-1", remoteId = "r-member-1")
        val responseDto = consultationDto(id = "r-c1", exams = emptyList())
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { memberDao.getById("local-1") } returns member
        coEvery { api.create(any()) } returns responseDto
        // reconcileRemote: fetches exams then updates remoteIds
        coEvery { dao.getExams("c1") } returns emptyList()
        coEvery { dao.markSynced("c1", any(), "r-c1") } returns Unit

        assertTrue(repository.pushDirty("c1"))
        coVerify(exactly = 1) { api.create(any()) }
        coVerify(exactly = 1) { dao.markSynced("c1", any(), "r-c1") }
        coVerify(exactly = 0) { api.update(any(), any()) }
    }

    @Test
    fun `pushDirty throws when member has no remoteId on POST`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = null)
        val unsyncedMember = memberEntity(id = "local-1", remoteId = null)
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { memberDao.getById("local-1") } returns unsyncedMember

        var threw = false
        try { repository.pushDirty("c1") } catch (_: IllegalStateException) { threw = true }

        assertTrue(threw)
        coVerify(exactly = 0) { api.create(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — existing record → PATCH
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty PATCHes existing consultation and marks synced`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = "r-c1")
        val responseDto = consultationDto(id = "r-c1")
        coEvery { dao.getById("c1") } returns entity
        stubEmptyAggregate("c1")
        coEvery { api.update("r-c1", any()) } returns responseDto
        coEvery { dao.getExams("c1") } returns emptyList()
        coEvery { dao.markSynced("c1", any(), "r-c1") } returns Unit

        assertTrue(repository.pushDirty("c1"))
        coVerify(exactly = 1) { api.update("r-c1", any()) }
        coVerify(exactly = 1) { dao.markSynced("c1", any(), "r-c1") }
        coVerify(exactly = 0) { api.create(any()) }
    }

    @Test
    fun `pushDirty reconciles remote exam ids after PATCH`() = runTest {
        val entity = consultationEntity(id = "c1", remoteId = "r-c1")
        val localExam = examEntity(id = "exam-1", remoteId = null, consultationId = "c1", name = "Hemograma")
        val responseDto = consultationDto(
            id = "r-c1",
            exams = listOf(com.example.nossasaudeapp.util.TestFixtures.examDto(id = "r-exam-1", name = "Hemograma")),
        )
        coEvery { dao.getById("c1") } returns entity
        coEvery { dao.getMedications("c1") } returns emptyList()
        coEvery { dao.getExams("c1") } returns listOf(localExam)
        coEvery { dao.getPrescriptionImages("c1") } returns emptyList()
        coEvery { dao.getResultImages(listOf("exam-1")) } returns emptyList()
        coEvery { api.update("r-c1", any()) } returns responseDto
        coEvery { dao.updateExamRemoteId("exam-1", "r-exam-1") } returns Unit
        coEvery { dao.markSynced("c1", any(), "r-c1") } returns Unit

        repository.pushDirty("c1")

        coVerify(exactly = 1) { dao.updateExamRemoteId("exam-1", "r-exam-1") }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // savePulled — new consultation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `savePulled inserts consultation that does not exist locally`() = runTest {
        val dto = consultationDto(id = "r-c1", memberId = "r-member-1")
        coEvery { dao.getByRemoteId("r-c1") } returns null
        coEvery { memberDao.getByRemoteId("r-member-1") } returns memberEntity(id = "local-1")
        coEvery { dao.replaceFullConsultation(any(), any(), any(), any(), any()) } returns Unit
        // updateFts — local id is a freshly generated UUID, stub broadly
        coEvery { dao.getById(any()) } returns consultationEntity()
        coEvery { dao.getMedications(any()) } returns emptyList()
        coEvery { dao.getExams(any()) } returns emptyList()
        coEvery { searchDao.upsert(any()) } returns Unit

        repository.savePulled(dto)

        coVerify(exactly = 1) { dao.replaceFullConsultation(any(), any(), any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // savePulled — last-write-wins
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `savePulled overwrites local when local is clean and remote is newer`() = runTest {
        val existingLocal = consultationEntity(
            id = "c1", remoteId = "r-c1",
            updatedAt = T1, syncedAt = T1, // clean (syncedAt == updatedAt)
        )
        // Remote updatedAt is T3, clearly newer than local T1
        val dto = consultationDto(id = "r-c1", updatedAt = "2023-11-14T22:13:23Z")
        coEvery { dao.getByRemoteId("r-c1") } returns existingLocal
        coEvery { memberDao.getByRemoteId(any()) } returns memberEntity()
        coEvery { dao.getExams("c1") } returns emptyList()
        coEvery { dao.getPrescriptionImages("c1") } returns emptyList()
        coEvery { dao.getResultImages(any()) } returns emptyList()
        coEvery { dao.replaceFullConsultation(any(), any(), any(), any(), any()) } returns Unit
        coEvery { dao.getById(any()) } returns existingLocal
        coEvery { dao.getMedications(any()) } returns emptyList()
        coEvery { searchDao.upsert(any()) } returns Unit

        repository.savePulled(dto)

        coVerify(exactly = 1) { dao.replaceFullConsultation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `savePulled skips overwrite when local is dirty and remote is not newer`() = runTest {
        val dirtyLocal = consultationEntity(
            id = "c1", remoteId = "r-c1",
            updatedAt = T3, syncedAt = T1, // dirty: updatedAt > syncedAt
        )
        // Remote updatedAt maps to T1, older than local T3
        val dto = consultationDto(id = "r-c1", updatedAt = "2023-11-14T22:13:21Z")
        coEvery { dao.getByRemoteId("r-c1") } returns dirtyLocal

        repository.savePulled(dto)

        coVerify(exactly = 0) { dao.replaceFullConsultation(any(), any(), any(), any(), any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // savePulled — preserves PENDING uploads
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `savePulled preserves PENDING prescription images when applying remote`() = runTest {
        val existingLocal = consultationEntity(id = "c1", remoteId = "r-c1", updatedAt = T1, syncedAt = T1)
        val pendingImg = prescriptionImageEntity(
            consultationId = "c1",
            s3Key = "local://pending-uuid",
            localPath = "/files/images/photo.jpg",
            uploadStatus = UploadStatus.PENDING.name,
        )
        val dto = consultationDto(id = "r-c1", updatedAt = "2023-11-14T22:13:23Z")
        coEvery { dao.getByRemoteId("r-c1") } returns existingLocal
        coEvery { memberDao.getByRemoteId(any()) } returns memberEntity()
        coEvery { dao.getExams("c1") } returns emptyList()
        coEvery { dao.getPrescriptionImages("c1") } returns listOf(pendingImg)
        coEvery { dao.getResultImages(any()) } returns emptyList()
        coEvery { dao.replaceFullConsultation(any(), any(), any(), any(), any()) } returns Unit
        coEvery { dao.getById(any()) } returns existingLocal
        coEvery { dao.getMedications(any()) } returns emptyList()
        coEvery { searchDao.upsert(any()) } returns Unit

        repository.savePulled(dto)

        val prescSlot = slot<List<PrescriptionImageEntity>>()
        coVerify { dao.replaceFullConsultation(any(), any(), any(), capture(prescSlot), any()) }
        assertTrue(
            "PENDING image must be preserved after pull",
            prescSlot.captured.any { it.s3Key == "local://pending-uuid" },
        )
    }

    @Test
    fun `savePulled throws when remote member not found locally`() = runTest {
        val dto = consultationDto(id = "r-c1", memberId = "r-member-unknown")
        coEvery { dao.getByRemoteId("r-c1") } returns null
        coEvery { memberDao.getByRemoteId("r-member-unknown") } returns null

        var threw = false
        try { repository.savePulled(dto) } catch (_: IllegalStateException) { threw = true }

        assertTrue(threw)
        coVerify(exactly = 0) { dao.replaceFullConsultation(any(), any(), any(), any(), any()) }
    }
}
