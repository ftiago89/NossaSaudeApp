package com.example.nossasaudeapp.data.sync

import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.dao.SyncMetadataDao
import com.example.nossasaudeapp.data.local.entity.SyncMetadataEntity
import com.example.nossasaudeapp.data.mapper.toEntity
import com.example.nossasaudeapp.data.remote.api.SyncApi
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.data.repository.ImageRepository
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val syncApi: SyncApi,
    private val memberRepository: MemberRepository,
    private val consultationRepository: ConsultationRepository,
    private val imageRepository: ImageRepository,
    private val memberDao: MemberDao,
    private val pendingUploadDao: PendingUploadDao,
    private val syncMetadataDao: SyncMetadataDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_iso"
    }

    suspend fun syncNow(): Result<Unit> = mutex.withLock {
        withContext(io) {
            _state.value = SyncState.Running
            runCatching {
                push()
                pull()
                uploads()
                val now = Clock.System.now()
                syncMetadataDao.put(SyncMetadataEntity(KEY_LAST_SYNC, now.toString()))
                _state.value = SyncState.Success(now)
            }.onFailure { e ->
                _state.value = SyncState.Failure(e.message ?: "Falha ao sincronizar", Clock.System.now())
            }
        }
    }

    private suspend fun push() {
        memberRepository.getDirtyIds().forEach { id ->
            memberRepository.pushDirty(id)
        }
        consultationRepository.getDirtyIds().forEach { id ->
            consultationRepository.pushDirty(id)
        }
    }

    private suspend fun pull() {
        val since = syncMetadataDao.getValue(KEY_LAST_SYNC)
        val response = syncApi.sync(since)

        response.members.forEach { dto ->
            val existing = memberDao.getByRemoteId(dto.id)
            val localId = existing?.id ?: UUID.randomUUID().toString()
            val incoming = dto.toEntity(localId)
            if (existing == null) {
                memberDao.insert(incoming)
            } else if (shouldApplyRemote(existing.updatedAt, existing.syncedAt, incoming.updatedAt)) {
                memberDao.insert(incoming)
            }
        }

        response.consultations.forEach { dto ->
            runCatching { consultationRepository.savePulled(dto) }
        }
    }

    private suspend fun uploads() {
        val pending = pendingUploadDao.getAll()
        pending.forEach { item ->
            runCatching { imageRepository.executePendingUpload(item) }
                .onFailure { e -> imageRepository.markFailed(item.id, e.message) }
        }
    }

    /** Last-write-wins: apply remote only when local is not dirtier than remote. */
    private fun shouldApplyRemote(localUpdatedAt: Long, localSyncedAt: Long?, remoteUpdatedAt: Long): Boolean {
        val localDirty = localSyncedAt == null || localUpdatedAt > localSyncedAt
        if (localDirty) return remoteUpdatedAt > localUpdatedAt
        return remoteUpdatedAt > localUpdatedAt
    }
}
