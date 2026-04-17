package com.example.nossasaudeapp.data.repository

import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.remote.ApiException
import com.example.nossasaudeapp.data.remote.api.MembersApi
import com.example.nossasaudeapp.data.remote.dto.MemberDto
import com.example.nossasaudeapp.util.TestFixtures.memberDto
import com.example.nossasaudeapp.util.TestFixtures.memberEntity
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemberRepositoryTest {

    @MockK lateinit var dao: MemberDao
    @MockK lateinit var api: MembersApi

    private lateinit var repository: MemberRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = MemberRepository(dao, api, UnconfinedTestDispatcher())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — entity not found
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty returns false when entity is not found locally`() = runTest {
        coEvery { dao.getById("ghost") } returns null

        val result = repository.pushDirty("ghost")

        assertFalse(result)
        coVerify(exactly = 0) { api.create(any()) }
        coVerify(exactly = 0) { api.update(any(), any()) }
        coVerify(exactly = 0) { api.delete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — soft-deleted locally, never reached the server
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty hard-deletes locally when deletedAt set and remoteId is null`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = null, deletedAt = 1L)
        coEvery { dao.getById("local-1") } returns entity
        coEvery { dao.hardDelete("local-1") } returns Unit

        val result = repository.pushDirty("local-1")

        assertTrue(result)
        coVerify(exactly = 1) { dao.hardDelete("local-1") }
        coVerify(exactly = 0) { api.delete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — soft-deleted locally, already on server
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty calls remote delete and then hard-deletes locally`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = "remote-1", deletedAt = 1L)
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.delete("remote-1") } returns Unit
        coEvery { dao.hardDelete("local-1") } returns Unit

        val result = repository.pushDirty("local-1")

        assertTrue(result)
        coVerify(exactly = 1) { api.delete("remote-1") }
        coVerify(exactly = 1) { dao.hardDelete("local-1") }
    }

    @Test
    fun `pushDirty hard-deletes locally when remote delete returns 404`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = "remote-1", deletedAt = 1L)
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.delete("remote-1") } throws ApiException(404, "NOT_FOUND", "not found")
        coEvery { dao.hardDelete("local-1") } returns Unit

        val result = repository.pushDirty("local-1")

        assertTrue(result)
        coVerify(exactly = 1) { dao.hardDelete("local-1") }
    }

    @Test
    fun `pushDirty re-throws non-404 API errors on delete`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = "remote-1", deletedAt = 1L)
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.delete("remote-1") } throws ApiException(500, "SERVER_ERROR", "oops")

        var threw = false
        try {
            repository.pushDirty("local-1")
        } catch (e: ApiException) {
            threw = true
        }
        assertTrue(threw)
        coVerify(exactly = 0) { dao.hardDelete(any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — new record (no remoteId, not deleted) → POST
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty POSTs new member and marks it synced`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = null, deletedAt = null, syncedAt = null)
        val responseDto = memberDto(id = "remote-99")
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.create(any()) } returns responseDto
        coEvery { dao.markSynced("local-1", any(), "remote-99") } returns Unit

        val result = repository.pushDirty("local-1")

        assertTrue(result)
        coVerify(exactly = 1) { api.create(any()) }
        coVerify(exactly = 1) { dao.markSynced("local-1", any(), "remote-99") }
        coVerify(exactly = 0) { api.update(any(), any()) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // pushDirty — existing record with remoteId → PATCH
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `pushDirty PATCHes existing member and marks it synced`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = "remote-1", deletedAt = null)
        val responseDto = memberDto(id = "remote-1")
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.update("remote-1", any()) } returns responseDto
        coEvery { dao.markSynced("local-1", any(), "remote-1") } returns Unit

        val result = repository.pushDirty("local-1")

        assertTrue(result)
        coVerify(exactly = 1) { api.update("remote-1", any()) }
        coVerify(exactly = 1) { dao.markSynced("local-1", any(), "remote-1") }
        coVerify(exactly = 0) { api.create(any()) }
    }

    @Test
    fun `pushDirty does not call delete when PATCHing non-deleted member`() = runTest {
        val entity = memberEntity(id = "local-1", remoteId = "remote-1", deletedAt = null)
        val responseDto = memberDto(id = "remote-1")
        coEvery { dao.getById("local-1") } returns entity
        coEvery { api.update("remote-1", any()) } returns responseDto
        coEvery { dao.markSynced(any(), any(), any()) } returns Unit

        repository.pushDirty("local-1")

        coVerify(exactly = 0) { api.delete(any()) }
        coVerify(exactly = 0) { dao.hardDelete(any()) }
    }
}
