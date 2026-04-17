package com.example.nossasaudeapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.repository.MemberRepository
import com.example.nossasaudeapp.data.repository.SearchRepository
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
)

data class SearchResultItem(
    val consultation: Consultation,
    val member: Member?,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> performSearch(q) }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun clear() {
        _state.update { SearchUiState() }
        queryFlow.value = ""
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        val consultations = searchRepository.search(query)
        val members = memberRepository.observeActive().first()
        val memberById = members.associateBy { it.id }
        val items = consultations.map { SearchResultItem(it, memberById[it.memberId]) }
        _state.update { it.copy(results = items, isSearching = false) }
    }
}
