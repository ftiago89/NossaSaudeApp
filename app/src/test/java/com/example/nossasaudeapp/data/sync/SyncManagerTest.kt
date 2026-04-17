package com.example.nossasaudeapp.data.sync

import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.dao.SyncMetadataDao
import com.example.nossasaudeapp.data.local.entity.SyncMetadataEntity
import com.example.nossasaudeapp.data.remote.ApiException
import com.example.nossasaudeapp.data.remote.api.SyncApi
import com.example.nossasaudeapp.data.remote.dto.SyncResponseDto
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.ImageRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.util.TestFixtures.T1
import com.example.nossasaudeapp.util.TestFixtures.T2
import com.example.nossasaudeapp.util.TestFixtures.T3
import com.example.nossasaudeapp.util.TestFixtures.memberDto
import com.example.nossasaudeapp.util.TestFixtures.memberEntity
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    @MockK lateinit var syncApi: SyncApi
    @MockK lateinit var memberRepository: MemberRepository
    @MockK lateinit var consultationRepository: ConsultationRepository
    @MockK lateinit var imageRepository: ImageRepository
    @MockK lateinit var memberDao: MemberDao
    @MockK lateinit var pendingUploadDao: PendingUploadDao
    @MockK lateinit var syncMetadataDao: SyncMetadataDao

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        syncManager = SyncManager(
            syncApi = syncApi,
            memberRepository = memberRepository,
            consultationRepository = consultationRepository,
            imageRepository = imageRepository,
            memberDao = memberDao,
            pendingUploadDao = pendingUploadDao,
            syncMetadataDao = syncMetadataDao,
            io = UnconfinedTestDispatcher(),
        )
        // Default stubs for a happy-path sync with nothing to do
        coEvery { memberRepository.getDirtyIds() } returns emptyList()
        coEvery { consultationRepository.getDirtyIds() } returns emptyList()
        coEvery { syncMetadataDao.getValue(any()) } returns null
        coEvery { syncMetadataDao.put(any()) } returns Unit
        coEvery { pendingUploadDao.getAll() } returns emptyList()
        coEvery { syncApi.sync(any()) } returns SyncResponseDto(
            members = emptyList(),
            consultations = emptyList(),
            syncedAt = "2023-11-14T22:13:21Z",
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SyncState transitions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `syncNow sets state to Success on happy path`() = runTest {
        assertTrue(syncManager.state.value is SyncState.Idle)

        syncManager.syncNow()

        assertTrue(syncManager.state.value is SyncState.Success)
    }

    @Test
    fun `syncNow sets state to Failure when push throws`() = runTest {
        coEvery { memberRepository.getDirtyIds() } throws ApiException(500, "ERR", "server error")

        syncManager.syncNow()

        assertTrue(syncManager.state.value is SyncState.Failure)
    }

    @Test
    fun `syncNow saves last_sync_iso to metadata on success`() = runTest {
        syncManager.syncNow()

        coVerify(exactly = 1) { syncMetadataDao.put(any<SyncMetadataEntity>()) }
    }

    @Test
    fun `syncNow does not save metadata when sync fails`() = runTest {
        coEvery { memberRepository.getDirtyIds() } throws ApiException(500, "ERR", "oops")

        syncManager.syncNow()

        coVerify(exactly = 0) { syncMetadataDao.put(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pull — member conflict resolution (shouldApplyRemote)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pull inserts member that does not exist locally`() = runTest {
        val dto = memberDto(id = "remote-new")
        coEvery { syncApi.sync(any()) } returns SyncResponseDto(
            members = listOf(dto),
            consultations = emptyList(),
            syncedAt = "2023-11-14T22:13:21Z",
        )
        coEvery { memberDao.getByRemoteId("remote-new") } returns null
        coEvery { memberDao.insert(any()) } returns Unit

        syncManager.syncNow()

        coVerify(exactly = 1) { memberDao.insert(any()) }
    }

    @Test
    fun `pull overwrites local member when remote is newer`() = runTest {
        // local.updatedAt = T1, remote.updatedAt = T3 → remote is newer → overwrite
        val existingLocal = memberEntity(
            id = "local-1",
            remoteId = "remote-1",
            updatedAt = T1,
            syncedAt = T1,
        )
        val newerDto = memberDto(
            id = "remote-1",
            updatedAt = "2023-11-14T22:13:23Z", // maps to ~T3, clearly after T1
        )
        coEvery { syncApi.sync(any()) } returns SyncResponseDto(
            members = listOf(newerDto),
            consultations = emptyList(),
            syncedAt = "2023-11-14T22:13:23Z",
        )
        coEvery { memberDao.getByRemoteId("remote-1") } returns existingLocal
        coEvery { memberDao.insert(any()) } returns Unit

        syncManager.syncNow()

        coVerify(exactly = 1) { memberDao.insert(any()) }
    }

    @Test
    fun `pull does not overwrite local member when local is newer`() = runTest {
        // remote.updatedAt is older than local.updatedAt → keep local
        val newerLocal = memberEntity(
            id = "local-1",
            remoteId = "remote-1",
            updatedAt = T3,   // local is freshest
            syncedAt = T2,
        )
        val olderDto = memberDto(
            id = "remote-1",
            updatedAt = "2023-11-14T22:13:21Z", // maps to ~T1, older than local T3
        )
        coEvery { syncApi.sync(any()) } returns SyncResponseDto(
            members = listOf(olderDto),
            consultations = emptyList(),
            syncedAt = "2023-11-14T22:13:21Z",
        )
        coEvery { memberDao.getByRemoteId("remote-1") } returns newerLocal

        syncManager.syncNow()

        coVerify(exactly = 0) { memberDao.insert(any()) }
    }

    @Test
    fun `pull passes last_sync_iso from metadata to sync API`() = runTest {
        val lastSync = "2023-11-14T10:00:00Z"
        coEvery { syncMetadataDao.getValue("last_sync_iso") } returns lastSync

        syncManager.syncNow()

        coVerify(exactly = 1) { syncApi.sync(lastSync) }
    }
}
