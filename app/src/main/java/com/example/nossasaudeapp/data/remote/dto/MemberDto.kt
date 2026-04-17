package com.example.nossasaudeapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberDto(
    @SerialName("_id") val id: String,
    val familyId: String,
    val name: String,
    val birthDate: String? = null,
    val bloodType: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val syncedAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class MemberCreateDto(
    val name: String,
    val birthDate: String? = null,
    val bloodType: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
)

@Serializable
data class MemberPatchDto(
    val name: String? = null,
    val birthDate: String? = null,
    val bloodType: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val allergies: List<String>? = null,
    val chronicConditions: List<String>? = null,
)
