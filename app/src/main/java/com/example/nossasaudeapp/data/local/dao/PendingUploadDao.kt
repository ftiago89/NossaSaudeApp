package com.example.nossasaudeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nossasaudeapp.data.local.entity.PendingUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUploadDao {

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE pending_uploads SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String?)

    @Query("DELETE FROM pending_uploads WHERE localPath = :localPath")
    suspend fun deleteByLocalPath(localPath: String)
}

@Dao
interface SyncMetadataDao {

    @Query("SELECT value FROM sync_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: com.example.nossasaudeapp.data.local.entity.SyncMetadataEntity)
}
