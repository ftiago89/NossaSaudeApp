package com.example.nossasaudeapp.data.repository

import com.example.nossasaudeapp.data.local.dao.SearchDao
import com.example.nossasaudeapp.di.IoDispatcher
import com.example.nossasaudeapp.domain.model.Consultation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val searchDao: SearchDao,
    private val consultationRepository: ConsultationRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** FTS4 query. Each token is prefix-matched independently (AND semantics). */
    suspend fun search(query: String): List<Consultation> = withContext(io) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val tokens = trimmed
            .split(' ', '\t', '\n')
            .map { sanitize(it) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext emptyList()
        val ftsQuery = tokens.joinToString(" ") { "$it*" }
        try {
            searchDao.searchRaw(ftsQuery).mapNotNull { entity ->
                consultationRepository.getById(entity.id)
            }
        } catch (_: android.database.sqlite.SQLiteException) {
            emptyList()
        }
    }

    // Keep only alphanumeric characters to avoid FTS4 operator injection
    // (-, (, ), :, ^, OR, AND, NOT are all FTS4 operators)
    private fun sanitize(token: String): String =
        token.replace(Regex("[^\\p{L}\\p{N}]"), "")
}
