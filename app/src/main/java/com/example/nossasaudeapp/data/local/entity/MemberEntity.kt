package com.example.nossasaudeapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    indices = [Index("remoteId", unique = true), Index("deletedAt")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val remoteId: String?,
    val name: String,
    val birthDate: Long?,
    val bloodType: String?,
    val weightKg: Double?,
    val heightCm: Double?,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val syncedAt: Long?,
    val deletedAt: Long?,
)
