package com.example.nossasaudeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nossasaudeapp.data.local.entity.ConsultationFtsEntity

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConsultationFtsEntity)

    @Query("DELETE FROM consultation_fts WHERE consultationId = :consultationId")
    suspend fun deleteById(consultationId: String)

    @Query(
        "SELECT DISTINCT c.* FROM consultations c " +
                "INNER JOIN consultation_fts fts ON fts.consultationId = c.id " +
                "WHERE consultation_fts MATCH :query AND c.deletedAt IS NULL " +
                "ORDER BY c.date DESC"
    )
    suspend fun searchRaw(query: String): List<com.example.nossasaudeapp.data.local.entity.ConsultationEntity>
}
