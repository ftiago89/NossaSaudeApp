package com.example.nossasaudeapp.ui.consultation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.image.S3ImageLoader
import com.example.nossasaudeapp.data.repository.ConsultationRepository
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.PrescriptionImage
import com.example.nossasaudeapp.domain.model.UploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageDisplay(val url: String, val cacheKey: String)

data class ConsultationDetailUiState(
    val consultation: Consultation? = null,
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
    val prescriptionImages: List<ImageDisplay> = emptyList(),
    val examResultImages: Map<String, List<ImageDisplay>> = emptyMap(),
)

@HiltViewModel
class ConsultationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConsultationRepository,
    private val s3ImageLoader: S3ImageLoader,
) : ViewModel() {

    private val consultationId: String = requireNotNull(savedStateHandle["consultationId"])

    private val _state = MutableStateFlow(ConsultationDetailUiState())
    val state: StateFlow<ConsultationDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val c = repository.getById(consultationId)
            val prescImages = buildDisplayImages(c?.prescriptionImages, c?.remoteId)
            val examImages = c?.exams?.associate { exam ->
                exam.id to buildDisplayImages(exam.resultImages, c.remoteId)
            } ?: emptyMap()
            _state.update {
                it.copy(
                    consultation = c,
                    isLoading = false,
                    prescriptionImages = prescImages,
                    examResultImages = examImages,
                )
            }
        }
    }

    /**
     * Resolves presigned URLs for the full-screen viewer, called on user tap.
     *
     * If thumbnails were loaded from disk cache (no presigned URL was fetched during [refresh]),
     * this triggers one GET /images call. Otherwise the S3ImageLoader memory cache (10-min TTL)
     * returns the URLs instantly without a network round-trip.
     */
    fun openViewer(images: List<ImageDisplay>, index: Int, onReady: (List<String>, Int) -> Unit) {
        val remoteId = _state.value.consultation?.remoteId
        viewModelScope.launch {
            val urls = images.mapNotNull { img ->
                when {
                    img.url.startsWith("file://") -> img.url
                    remoteId != null -> s3ImageLoader.presignedUrl(remoteId, img.cacheKey)
                    else -> null
                }
            }
            if (urls.isNotEmpty()) onReady(urls, index)
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(consultationId)
            _state.update { it.copy(deleted = true) }
        }
    }

    private suspend fun buildDisplayImages(
        images: List<PrescriptionImage>?,
        remoteConsultationId: String?,
    ): List<ImageDisplay> {
        images ?: return emptyList()

        val uploaded = images.filter { it.uploadStatus == UploadStatus.UPLOADED }

        // If any presigned URL is missing from the in-memory cache, warm it with one GET /images.
        val needsFetch = remoteConsultationId != null && uploaded.isNotEmpty() &&
            uploaded.any { s3ImageLoader.cachedUrl(it.s3Key) == null }

        if (needsFetch) {
            s3ImageLoader.presignedUrl(remoteConsultationId!!, uploaded.first().s3Key)
        }

        return images.mapNotNull { img ->
            when (img.uploadStatus) {
                UploadStatus.PENDING ->
                    img.localPath?.let { ImageDisplay(url = "file://$it", cacheKey = "file://$it") }

                UploadStatus.UPLOADED -> {
                    val url = when {
                        remoteConsultationId == null ->
                            img.localPath?.let { "file://$it" } ?: return@mapNotNull null
                        else ->
                            s3ImageLoader.cachedUrl(img.s3Key)
                                ?: s3ImageLoader.presignedUrl(remoteConsultationId, img.s3Key)
                                ?: return@mapNotNull null
                    }
                    ImageDisplay(url = url, cacheKey = img.s3Key)
                }
            }
        }
    }
}
