package com.example.nossasaudeapp.data.mapper

import kotlinx.datetime.Instant

fun String?.toInstantOrNull(): Instant? =
    if (this.isNullOrBlank()) null else runCatching { Instant.parse(this) }.getOrNull()

fun Long?.toInstantOrNull(): Instant? = this?.let { Instant.fromEpochMilliseconds(it) }

fun Instant?.toEpochMillisOrNull(): Long? = this?.toEpochMilliseconds()

fun Instant.toIso8601(): String = this.toString()
