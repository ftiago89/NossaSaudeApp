package com.example.nossasaudeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.nossasaudeapp.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirty(): List<MemberEntity>

    @Query("SELECT * FROM members")
    suspend fun getAll(): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MemberEntity)

    @Update
    suspend fun update(member: MemberEntity)

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE members SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("UPDATE members SET syncedAt = :syncedAt, remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long, remoteId: String)
}
