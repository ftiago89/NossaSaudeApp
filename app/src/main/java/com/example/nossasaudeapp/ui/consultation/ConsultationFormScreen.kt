package com.example.nossasaudeapp.ui.consultation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.domain.model.Efficacy
import com.example.nossasaudeapp.domain.model.MedicalSpecialties
import com.example.nossasaudeapp.domain.model.MedicationForm
import com.example.nossasaudeapp.ui.components.DropdownOption
import com.example.nossasaudeapp.ui.components.NsChipVariant
import com.example.nossasaudeapp.ui.components.NsContraindicationAlert
import com.example.nossasaudeapp.ui.components.NsSearchableDropdown
import com.example.nossasaudeapp.ui.components.NsTagInput
import com.example.nossasaudeapp.ui.components.NsTextField
import com.example.nossasaudeapp.ui.components.NsTopBar
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius
import com.example.nossasaudeapp.ui.theme.Spacing

private val specialtyOptions = MedicalSpecialties.cfmOrdered.map { key ->
    DropdownOption(key, MedicalSpecialties.labelOf(key))
}.sortedBy { it.label }
private val formOptions = MedicationForm.entries.map { DropdownOption(it.name, it.label) }.sortedBy { it.label }
private val efficacyOptions = Efficacy.entries.map { DropdownOption(it.name, it.label) }.sortedBy { it.label }

@Composable
fun ConsultationFormScreen(
    onBack: () -> Unit,
    onSaved: (consultationId: String) -> Unit,
    viewModel: ConsultationFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) state.savedId?.let { onSaved(it) }
    }

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPrescriptionImage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .statusBarsPadding(),
    ) {
        NsTopBar(
            title = if (state.isEdit) "Editar consulta" else "Nova consulta",
            onBack = onBack,
            actions = {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = NsColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                } else {
                    TextButton(onClick = { viewModel.save() }, enabled = state.isValid) {
                        Text(
                            text = "Salvar",
                            color = if (state.isValid) NsColors.Primary else NsColors.TextTertiary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
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
                Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = NsColors.Danger)
            }

            // Consultation basics
            FormSectionHeader("Consulta")

            NsTextField(
                value = state.dateText,
                onValueChange = viewModel::onDateText,
                label = "Data",
                placeholder = "dd/mm/aaaa",
                keyboardType = KeyboardType.Number,
                isError = state.dateError,
            )
            NsTextField(
                value = state.reason,
                onValueChange = viewModel::onReason,
                label = "Motivo / diagnóstico",
                required = true,
                isError = state.reasonError && state.reason.isNotBlank(),
                singleLine = false,
            )
            NsTextField(
                value = state.clinic,
                onValueChange = viewModel::onClinic,
                label = "Clínica / hospital",
            )
            NsTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = "Observações",
                singleLine = false,
            )
            NsTagInput(
                label = "Tags",
                tags = state.tags,
                onAdd = viewModel::onAddTag,
                onRemove = viewModel::onRemoveTag,
                placeholder = "Ex: urgência",
                variant = NsChipVariant.Neutral,
            )

            // Doctor
            FormSectionHeader("Médico")
            NsTextField(
                value = state.doctorName,
                onValueChange = viewModel::onDoctorName,
                label = "Nome do médico",
            )
            NsSearchableDropdown(
                label = "Especialidade",
                options = specialtyOptions,
                selectedId = state.specialty.ifBlank { null },
                onSelect = { viewModel.onSpecialty(it?.id.orEmpty()) },
                placeholder = "Selecione ou busque",
            )
            AnimatedVisibility(state.specialty == "OTHER") {
                NsTextField(
                    value = state.customSpecialty,
                    onValueChange = viewModel::onCustomSpecialty,
                    label = "Especialidade personalizada",
                )
            }
            NsTextField(
                value = state.returnOf,
                onValueChange = viewModel::onReturnOf,
                label = "Retorno de (ID da consulta anterior)",
                placeholder = "Deixe em branco se não for retorno",
            )

            // Medications
            FormSectionHeader("Medicamentos")
            state.medications.forEach { med ->
                MedicationCard(
                    draft = med,
                    isContraindicated = viewModel.isContraindicated(med.name),
                    memberName = state.memberName,
                    onUpdate = viewModel::updateMedication,
                    onRemove = { viewModel.removeMedication(med.id) },
                )
            }
            TextButton(onClick = { viewModel.addMedication() }) {
                Icon(Icons.Default.Add, null, tint = NsColors.Primary, modifier = Modifier.size(18.dp))
                Text(" Adicionar medicamento", color = NsColors.Primary)
            }

            // Prescriptions
            FormSectionHeader("Receitas")
            if (state.existingPrescriptionImages.isNotEmpty()) {
                ExistingImageGrid(
                    entries = state.existingPrescriptionImages,
                    onRemove = viewModel::deleteExistingPrescriptionImage,
                )
            }
            if (state.prescriptionLocalPaths.isNotEmpty()) {
                LocalImageGrid(
                    paths = state.prescriptionLocalPaths,
                    onRemove = viewModel::removePrescriptionImage,
                )
            }
            TextButton(onClick = {
                imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.Image, null, tint = NsColors.Primary, modifier = Modifier.size(18.dp))
                Text(" Adicionar receita", color = NsColors.Primary)
            }

            // Exams
            FormSectionHeader("Exames")
            state.exams.forEach { exam ->
                ExamCard(
                    draft = exam,
                    onUpdate = viewModel::updateExam,
                    onRemove = { viewModel.removeExam(exam.id) },
                    onAddImage = { uri -> viewModel.addExamImage(exam.id, uri) },
                    onRemoveImage = { path -> viewModel.removeExamImage(exam.id, path) },
                    onDeleteExistingImage = { s3Key -> viewModel.deleteExistingExamImage(exam.id, s3Key) },
                )
            }
            TextButton(onClick = { viewModel.addExam() }) {
                Icon(Icons.Default.Add, null, tint = NsColors.Primary, modifier = Modifier.size(18.dp))
                Text(" Adicionar exame", color = NsColors.Primary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MedicationCard(
    draft: MedicationDraft,
    isContraindicated: Boolean,
    memberName: String,
    onUpdate: (MedicationDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .border(
                width = 1.dp,
                color = if (isContraindicated) NsColors.Danger else NsColors.Border,
                shape = RoundedCornerShape(Radius.medium),
            )
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (draft.name.isBlank()) "Medicamento" else draft.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (draft.name.isBlank()) NsColors.TextTertiary else NsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remover", tint = NsColors.Danger)
            }
        }

        if (isContraindicated) {
            NsContraindicationAlert(
                medicationName = draft.name,
                memberName = memberName,
                reason = null,
            )
        }

        NsTextField(value = draft.name, onValueChange = { onUpdate(draft.copy(name = it)) }, label = "Nome", required = true)
        NsTextField(value = draft.activeIngredient, onValueChange = { onUpdate(draft.copy(activeIngredient = it)) }, label = "Princípio ativo")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NsTextField(value = draft.dosage, onValueChange = { onUpdate(draft.copy(dosage = it)) }, label = "Dosagem", modifier = Modifier.weight(1f))
            NsSearchableDropdown(
                label = "Forma",
                options = formOptions,
                selectedId = draft.form?.name,
                onSelect = { opt -> onUpdate(draft.copy(form = opt?.let { MedicationForm.entries.firstOrNull { f -> f.name == it.id } })) },
                modifier = Modifier.weight(1f),
                searchable = false,
            )
        }
        NsTextField(value = draft.frequency, onValueChange = { onUpdate(draft.copy(frequency = it)) }, label = "Frequência", placeholder = "Ex: 1x ao dia")

        NsSearchableDropdown(
            label = "Eficácia",
            options = efficacyOptions,
            selectedId = draft.efficacy?.name,
            onSelect = { opt -> onUpdate(draft.copy(efficacy = opt?.let { Efficacy.entries.firstOrNull { e -> e.name == it.id } })) },
            searchable = false,
        )
        NsTextField(value = draft.sideEffects, onValueChange = { onUpdate(draft.copy(sideEffects = it)) }, label = "Efeitos colaterais", singleLine = false)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onUpdate(draft.copy(contraindicated = !draft.contraindicated)) },
        ) {
            Checkbox(
                checked = draft.contraindicated,
                onCheckedChange = { onUpdate(draft.copy(contraindicated = it)) },
                colors = CheckboxDefaults.colors(checkedColor = NsColors.Danger),
            )
            Text("Marcar como contraindicado", style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
        }
        AnimatedVisibility(
            visible = draft.contraindicated,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            NsTextField(
                value = draft.restrictionReason,
                onValueChange = { onUpdate(draft.copy(restrictionReason = it)) },
                label = "Motivo da restrição",
            )
        }
    }
}

@Composable
private fun ExamCard(
    draft: ExamDraft,
    onUpdate: (ExamDraft) -> Unit,
    onRemove: () -> Unit,
    onAddImage: (android.net.Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
    onDeleteExistingImage: (String) -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { onAddImage(it) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .border(1.dp, NsColors.Border, RoundedCornerShape(Radius.medium))
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (draft.name.isBlank()) "Exame" else draft.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (draft.name.isBlank()) NsColors.TextTertiary else NsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remover", tint = NsColors.Danger)
            }
        }
        NsTextField(value = draft.name, onValueChange = { onUpdate(draft.copy(name = it)) }, label = "Nome do exame", required = true)
        NsTextField(value = draft.notes, onValueChange = { onUpdate(draft.copy(notes = it)) }, label = "Observações", singleLine = false)

        // Result image grid
        if (draft.existingResultImages.isNotEmpty()) {
            ExistingImageGrid(
                entries = draft.existingResultImages,
                onRemove = onDeleteExistingImage,
            )
        }
        if (draft.resultLocalPaths.isNotEmpty()) {
            LocalImageGrid(
                paths = draft.resultLocalPaths,
                onRemove = onRemoveImage,
            )
        }
        TextButton(
            onClick = { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        ) {
            Icon(Icons.Default.Image, null, tint = NsColors.AccentTeal, modifier = Modifier.size(16.dp))
            Text(" Adicionar resultado", color = NsColors.AccentTeal, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ExistingImageGrid(
    entries: List<ImageEditEntry>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 2
    val rows = (entries.size + columns - 1) / columns
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    if (index < entries.size) {
                        val entry = entries[index]
                        val model: Any? = when {
                            entry.localPath != null -> java.io.File(entry.localPath)
                            entry.presignedUrl != null -> entry.presignedUrl
                            else -> null
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(Radius.small)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(Radius.small))
                                        .background(NsColors.Border),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = NsColors.TextTertiary,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(Radius.full))
                                    .background(NsColors.Danger)
                                    .clickable { onRemove(entry.s3Key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalImageGrid(
    paths: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 2
    val rows = (paths.size + columns - 1) / columns
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    if (index < paths.size) {
                        val path = paths[index]
                        Box(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = java.io.File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(Radius.small)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(Radius.full))
                                    .background(NsColors.Danger)
                                    .clickable { onRemove(path) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NsColors.TextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
