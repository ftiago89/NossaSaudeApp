package com.example.nossasaudeapp.data.mapper

import com.example.nossasaudeapp.data.local.entity.MemberEntity
import com.example.nossasaudeapp.data.remote.dto.MemberCreateDto
import com.example.nossasaudeapp.data.remote.dto.MemberDto
import com.example.nossasaudeapp.data.remote.dto.MemberPatchDto
import com.example.nossasaudeapp.domain.model.BloodType
import com.example.nossasaudeapp.domain.model.Member
import kotlinx.datetime.Clock

fun MemberEntity.toDomain(): Member = Member(
    id = id,
    remoteId = remoteId,
    name = name,
    birthDate = birthDate.toInstantOrNull(),
    bloodType = BloodType.fromLabel(bloodType),
    weightKg = weightKg,
    heightCm = heightCm,
    allergies = allergies,
    chronicConditions = chronicConditions,
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(createdAt),
    updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt.toInstantOrNull(),
    deletedAt = deletedAt.toInstantOrNull(),
)

fun Member.toEntity(): MemberEntity = MemberEntity(
    id = id,
    remoteId = remoteId,
    name = name,
    birthDate = birthDate?.toEpochMilliseconds(),
    bloodType = bloodType?.label,
    weightKg = weightKg,
    heightCm = heightCm,
    allergies = allergies,
    chronicConditions = chronicConditions,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    syncedAt = syncedAt?.toEpochMilliseconds(),
    deletedAt = deletedAt?.toEpochMilliseconds(),
)

fun MemberDto.toEntity(localId: String): MemberEntity {
    val now = Clock.System.now().toEpochMilliseconds()
    val parsedUpdatedAt = updatedAt.toInstantOrNull()?.toEpochMilliseconds() ?: now
    val parsedSyncedAt = syncedAt.toInstantOrNull()?.toEpochMilliseconds() ?: now
    return MemberEntity(
        id = localId,
        remoteId = id,
        name = name,
        birthDate = birthDate.toInstantOrNull()?.toEpochMilliseconds(),
        bloodType = bloodType,
        weightKg = weight,
        heightCm = height,
        allergies = allergies,
        chronicConditions = chronicConditions,
        createdAt = createdAt.toInstantOrNull()?.toEpochMilliseconds() ?: now,
        updatedAt = parsedUpdatedAt,
        syncedAt = maxOf(parsedSyncedAt, parsedUpdatedAt), // prevent false dirty after pull
        deletedAt = deletedAt.toInstantOrNull()?.toEpochMilliseconds(),
    )
}

fun Member.toCreateDto(): MemberCreateDto = MemberCreateDto(
    name = name,
    birthDate = birthDate?.toIso8601(),
    bloodType = bloodType?.label,
    weight = weightKg,
    height = heightCm,
    allergies = allergies,
    chronicConditions = chronicConditions,
)

fun Member.toPatchDto(): MemberPatchDto = MemberPatchDto(
    name = name,
    birthDate = birthDate?.toIso8601(),
    bloodType = bloodType?.label,
    weight = weightKg,
    height = heightCm,
    allergies = allergies,
    chronicConditions = chronicConditions,
)
