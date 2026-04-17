package com.example.nossasaudeapp.ui.member

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.domain.model.BloodType
import com.example.nossasaudeapp.ui.components.DateMaskVisualTransformation
import com.example.nossasaudeapp.ui.components.DropdownOption
import com.example.nossasaudeapp.ui.components.NsChipVariant
import com.example.nossasaudeapp.ui.components.NsSearchableDropdown
import com.example.nossasaudeapp.ui.components.NsTagInput
import com.example.nossasaudeapp.ui.components.NsTextField
import com.example.nossasaudeapp.ui.components.NsTopBar
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Spacing

private val bloodTypeOptions = BloodType.entries.map { DropdownOption(it.name, it.label) }.sortedBy { it.label }

@Composable
fun MemberFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MemberFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .statusBarsPadding(),
    ) {
        NsTopBar(
            title = if (state.isEdit) "Editar membro" else "Novo membro",
            onBack = onBack,
            actions = {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = NsColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                } else {
                    SaveButton(
                        enabled = state.isValid,
                        onClick = { viewModel.save() },
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.screenPaddingH, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.Danger,
                )
            }

            SectionHeader("Informações pessoais")

            NsTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = "Nome completo",
                required = true,
                isError = state.nameError && state.name.isNotBlank(),
                supportingText = if (state.nameError && state.name.isNotBlank()) "Campo obrigatório" else null,
            )

            NsTextField(
                value = state.birthDateText,
                onValueChange = viewModel::onBirthDate,
                label = "Data de nascimento",
                placeholder = "dd/mm/aaaa",
                keyboardType = KeyboardType.Number,
                isError = state.birthDateError,
                supportingText = if (state.birthDateError) "Formato inválido (dd/mm/aaaa)" else null,
                visualTransformation = DateMaskVisualTransformation,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NsTextField(
                    value = state.weightText,
                    onValueChange = viewModel::onWeight,
                    label = "Peso (kg)",
                    placeholder = "ex: 70",
                    keyboardType = KeyboardType.Decimal,
                    isError = state.weightError,
                    modifier = Modifier.weight(1f),
                )
                NsTextField(
                    value = state.heightText,
                    onValueChange = viewModel::onHeight,
                    label = "Altura (cm)",
                    placeholder = "ex: 175",
                    keyboardType = KeyboardType.Number,
                    isError = state.heightError,
                    modifier = Modifier.weight(1f),
                )
            }

            NsSearchableDropdown(
                label = "Tipo sanguíneo",
                options = bloodTypeOptions,
                selectedId = state.bloodType?.name,
                onSelect = { opt ->
                    viewModel.onBloodType(opt?.id?.let { id -> BloodType.entries.firstOrNull { it.name == id } })
                },
                placeholder = "Selecione o tipo",
                searchable = false,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("Alergias")

            NsTagInput(
                label = "Alergias",
                tags = state.allergies,
                onAdd = viewModel::onAddAllergy,
                onRemove = viewModel::onRemoveAllergy,
                placeholder = "Ex: Dipirona",
                variant = NsChipVariant.Allergy,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("Condições crônicas")

            NsTagInput(
                label = "Condição",
                tags = state.chronicConditions,
                onAdd = viewModel::onAddCondition,
                onRemove = viewModel::onRemoveCondition,
                placeholder = "Ex: Hipertensão",
                variant = NsChipVariant.Condition,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NsColors.TextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = "Salvar",
            color = if (enabled) NsColors.Primary else NsColors.TextTertiary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
