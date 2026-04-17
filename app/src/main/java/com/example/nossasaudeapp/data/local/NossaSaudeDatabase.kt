package com.example.nossasaudeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.dao.SearchDao
import com.example.nossasaudeapp.data.local.dao.SyncMetadataDao
import com.example.nossasaudeapp.data.local.entity.ConsultationEntity
import com.example.nossasaudeapp.data.local.entity.ConsultationFtsEntity
import com.example.nossasaudeapp.data.local.entity.ExamEntity
import com.example.nossasaudeapp.data.local.entity.MedicationEntity
import com.example.nossasaudeapp.data.local.entity.MemberEntity
import com.example.nossasaudeapp.data.local.entity.PendingUploadEntity
import com.example.nossasaudeapp.data.local.entity.PrescriptionImageEntity
import com.example.nossasaudeapp.data.local.entity.ResultImageEntity
import com.example.nossasaudeapp.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        MemberEntity::class,
        ConsultationEntity::class,
        MedicationEntity::class,
        ExamEntity::class,
        PrescriptionImageEntity::class,
        ResultImageEntity::class,
        PendingUploadEntity::class,
        SyncMetadataEntity::class,
        ConsultationFtsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NossaSaudeDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun pendingUploadDao(): PendingUploadDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun searchDao(): SearchDao
}
