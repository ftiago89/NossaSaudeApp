package com.example.nossasaudeapp.data.repository

import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.mapper.toCreateDto
import com.example.nossasaudeapp.data.mapper.toDomain
import com.example.nossasaudeapp.data.mapper.toEntity
import com.example.nossasaudeapp.data.mapper.toPatchDto
import com.example.nossasaudeapp.data.remote.ApiException
import com.example.nossasaudeapp.data.remote.api.MembersApi
import com.example.nossasaudeapp.di.IoDispatcher
import com.example.nossasaudeapp.domain.model.Member
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
class MemberRepository @Inject constructor(
    private val dao: MemberDao,
    private val api: MembersApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeActive(): Flow<List<Member>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Member? = withContext(io) {
        dao.getById(id)?.toDomain()
    }

    suspend fun create(
        name: String,
        birthDate: Instant?,
        bloodType: com.example.nossasaudeapp.domain.model.BloodType?,
        weightKg: Double?,
        heightCm: Double?,
        allergies: List<String>,
        chronicConditions: List<String>,
    ): Member = withContext(io) {
        val now = Clock.System.now()
        val member = Member(
            id = UUID.randomUUID().toString(),
            remoteId = null,
            name = name,
            birthDate = birthDate,
            bloodType = bloodType,
            weightKg = weightKg,
            heightCm = heightCm,
            allergies = allergies,
            chronicConditions = chronicConditions,
            createdAt = now,
            updatedAt = now,
            syncedAt = null,
            deletedAt = null,
        )
        dao.insert(member.toEntity())
        member
    }

    suspend fun update(member: Member): Member = withContext(io) {
        val updated = member.copy(updatedAt = Clock.System.now())
        dao.insert(updated.toEntity())
        updated
    }

    suspend fun delete(id: String) = withContext(io) {
        val now = Clock.System.now().toEpochMilliseconds()
        dao.markDeleted(id, now)
    }

    /** Push one dirty member to the server. Returns true if successful. */
    suspend fun pushDirty(localId: String): Boolean = withContext(io) {
        val entity = dao.getById(localId) ?: return@withContext false
        val domain = entity.toDomain()
        val now = Clock.System.now().toEpochMilliseconds()
        try {
            if (entity.deletedAt != null && entity.remoteId == null) {
                // Never reached the server — just discard locally
                dao.hardDelete(entity.id)
            } else if (entity.deletedAt != null && entity.remoteId != null) {
                api.delete(entity.remoteId)
                dao.hardDelete(entity.id)
            } else if (entity.remoteId == null) {
                val created = api.create(domain.toCreateDto())
                dao.markSynced(entity.id, now, created.id)
            } else {
                val updated = api.update(entity.remoteId, domain.toPatchDto())
                dao.markSynced(entity.id, now, updated.id)
            }
            true
        } catch (e: ApiException) {
            if (e.isNotFound && entity.deletedAt != null) {
                dao.hardDelete(entity.id); true
            } else throw e
        }
    }

    suspend fun getDirtyIds(): List<String> = withContext(io) {
        dao.getDirty().map { it.id }
    }
}
