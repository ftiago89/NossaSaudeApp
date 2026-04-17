package com.example.nossasaudeapp.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun stringListToJson(value: List<String>?): String =
        if (value == null) "[]" else json.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(stringListSerializer, value) }.getOrElse { emptyList() }
}
