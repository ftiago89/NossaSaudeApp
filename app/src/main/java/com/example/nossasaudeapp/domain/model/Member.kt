package com.example.nossasaudeapp.domain.model

import kotlinx.datetime.Instant

data class Member(
    val id: String,
    val remoteId: String?,
    val name: String,
    val birthDate: Instant?,
    val bloodType: BloodType?,
    val weightKg: Double?,
    val heightCm: Double?,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant?,
    val deletedAt: Instant?,
)
