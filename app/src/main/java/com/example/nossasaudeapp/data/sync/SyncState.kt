package com.example.nossasaudeapp.data.sync

import kotlinx.datetime.Instant

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Success(val at: Instant) : SyncState
    data class Failure(val message: String, val at: Instant) : SyncState
}
