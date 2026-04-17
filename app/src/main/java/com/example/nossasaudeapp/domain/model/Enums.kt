package com.example.nossasaudeapp.domain.model

enum class BloodType(val label: String) {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-");

    companion object {
        fun fromLabel(value: String?): BloodType? =
            if (value == null) null else entries.firstOrNull { it.label == value }
    }
}

enum class MedicationForm(val label: String) {
    COMPRIMIDO("Comprimido"),
    CAPSULA("Cápsula"),
    LIQUIDO("Líquido"),
    POMADA("Pomada"),
    INJETAVEL("Injetável"),
    GOTAS("Gotas"),
    SPRAY("Spray"),
    ADESIVO("Adesivo"),
    OUTRO("Outro");

    companion object {
        fun fromApi(value: String?): MedicationForm? =
            if (value == null) null else runCatching { valueOf(value) }.getOrNull()
    }
}

enum class Efficacy(val label: String) {
    EFICAZ("Eficaz"),
    PARCIAL("Parcial"),
    INEFICAZ("Ineficaz");

    companion object {
        fun fromApi(value: String?): Efficacy? =
            if (value == null) null else runCatching { valueOf(value) }.getOrNull()
    }
}

enum class UploadType(val apiValue: String) {
    PRESCRIPTION("prescription"),
    EXAM("exam"),
}
