package com.example.nossasaudeapp.domain.model

object MedicalSpecialties {
    val cfmOrdered: List<String> = listOf(
        "CLINICA_GERAL",
        "PEDIATRIA",
        "ORTOPEDIA",
        "DERMATOLOGIA",
        "GINECOLOGIA",
        "CARDIOLOGIA",
        "OFTALMOLOGIA",
        "OTORRINOLARINGOLOGIA",
        "NEUROLOGIA",
        "PSIQUIATRIA",
        "UROLOGIA",
        "ENDOCRINOLOGIA",
        "GASTROENTEROLOGIA",
        "PNEUMOLOGIA",
        "REUMATOLOGIA",
        "NEFROLOGIA",
        "ONCOLOGIA",
        "HEMATOLOGIA",
        "INFECTOLOGIA",
        "GERIATRIA",
        "CIRURGIA_GERAL",
        "CIRURGIA_PLASTICA",
        "CIRURGIA_VASCULAR",
        "CIRURGIA_TORACICA",
        "CIRURGIA_CABECA_PESCOCO",
        "ANESTESIOLOGIA",
        "RADIOLOGIA",
        "PATOLOGIA",
        "MEDICINA_NUCLEAR",
        "MEDICINA_INTENSIVA",
        "MEDICINA_DO_TRABALHO",
        "MEDICINA_ESPORTIVA",
        "MEDICINA_DE_FAMILIA",
        "MEDICINA_DE_EMERGENCIA",
        "ALERGIA_IMUNOLOGIA",
        "NEUROPEDIATRIA",
        "NEONATOLOGIA",
        "MASTOLOGIA",
        "COLOPROCTOLOGIA",
        "HEPATOLOGIA",
        "FISIATRIA",
        "ACUPUNTURA",
        "HOMEOPATIA",
        "NUTOLOGIA",
        "ODONTOLOGIA",
        "GENETICA_MEDICA",
        "MEDICINA_LEGAL",
        "FONIATRIA",
        "HANSENOLOGIA",
        "HIPERBARISMO",
        "ANGIORRADIOLOGIA",
        "ENDOSCOPIA",
        "DENSITOMETRIA",
        "MEDICINA_DE_TRAFEGO",
        "SEXOLOGIA",
        "OTHER",
    )

    fun labelOf(apiValue: String): String {
        if (apiValue == "OTHER") return "Outra"
        return apiValue.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
