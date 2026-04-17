package com.example.nossasaudeapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nossasaudeapp.data.sync.SyncManager
import com.example.nossasaudeapp.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val syncState: SyncState = SyncState.Idle)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncManager: SyncManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = syncManager.state
        .map { SettingsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun syncNow() {
        viewModelScope.launch { syncManager.syncNow() }
    }
}
