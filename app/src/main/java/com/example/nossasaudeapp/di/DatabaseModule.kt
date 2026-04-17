package com.example.nossasaudeapp.di

import android.content.Context
import androidx.room.Room
import com.example.nossasaudeapp.data.local.NossaSaudeDatabase
import com.example.nossasaudeapp.data.local.dao.ConsultationDao
import com.example.nossasaudeapp.data.local.dao.MemberDao
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.local.dao.SearchDao
import com.example.nossasaudeapp.data.local.dao.SyncMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NossaSaudeDatabase =
        Room.databaseBuilder(
            context,
            NossaSaudeDatabase::class.java,
            "nossasaude.db",
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideMemberDao(db: NossaSaudeDatabase): MemberDao = db.memberDao()

    @Provides
    fun provideConsultationDao(db: NossaSaudeDatabase): ConsultationDao = db.consultationDao()

    @Provides
    fun providePendingUploadDao(db: NossaSaudeDatabase): PendingUploadDao = db.pendingUploadDao()

    @Provides
    fun provideSyncMetadataDao(db: NossaSaudeDatabase): SyncMetadataDao = db.syncMetadataDao()

    @Provides
    fun provideSearchDao(db: NossaSaudeDatabase): SearchDao = db.searchDao()
}
