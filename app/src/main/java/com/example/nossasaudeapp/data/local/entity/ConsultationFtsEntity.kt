package com.example.nossasaudeapp.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "consultation_fts")
@Fts4
data class ConsultationFtsEntity(
    val consultationId: String,
    val memberId: String,
    val reason: String,
    val notes: String,
    val doctorName: String,
    val clinic: String,
    val tags: String,
    val medicationNames: String,
    val examNames: String,
)
