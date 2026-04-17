package com.example.nossasaudeapp.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.nossasaudeapp.data.local.dao.PendingUploadDao
import com.example.nossasaudeapp.data.repository.ImageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingUploadDao: PendingUploadDao,
    private val imageRepository: ImageRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pending = pendingUploadDao.getAll()
        if (pending.isEmpty()) return Result.success()

        var anyDeferred = false
        var anyFailed = false
        pending.forEach { item ->
            runCatching { imageRepository.executePendingUpload(item) }
                .onSuccess { done -> if (!done) anyDeferred = true }
                .onFailure { e ->
                    anyFailed = true
                    imageRepository.markFailed(item.id, e.message)
                }
        }
        return when {
            anyFailed -> Result.retry()
            anyDeferred -> Result.success() // retry later when dependencies (remoteIds) are synced
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "upload_pending_images"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
