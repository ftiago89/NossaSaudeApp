package com.example.nossasaudeapp.ui.consultation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Exam
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.ui.components.NsChip
import com.example.nossasaudeapp.ui.components.NsChipVariant
import com.example.nossasaudeapp.ui.components.NsTopBar
import com.example.nossasaudeapp.ui.theme.Elevation
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius
import com.example.nossasaudeapp.ui.theme.Spacing
import com.example.nossasaudeapp.ui.util.toLongDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsultationDetailScreen(
    onBack: () -> Unit,
    onEdit: (consultationId: String) -> Unit,
    onDeleted: () -> Unit,
    onViewImages: (urls: List<String>, index: Int) -> Unit = { _, _ -> },
    viewModel: ConsultationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                NsTopBar(
                    title = state.consultation?.reason ?: "",
                    onBack = onBack,
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = NsColors.TextPrimary)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        state.consultation?.let { onEdit(it.id) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Excluir", color = NsColors.Danger) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = NsColors.Danger) },
                                    onClick = { menuExpanded = false; showDeleteDialog = true },
                                )
                            }
                        }
                    },
                )
            }

            state.consultation?.let { c ->
                item { ConsultationHeader(c) }

                if (state.prescriptionImages.isNotEmpty()) {
                    item { SectionTitle("Receitas") }
                    item {
                        ImageGrid(
                            images = state.prescriptionImages,
                            onTap = { index ->
                                viewModel.openViewer(state.prescriptionImages, index, onViewImages)
                            },
                            modifier = Modifier.padding(horizontal = Spacing.screenPaddingH, vertical = 4.dp),
                        )
                    }
                }

                if (c.medications.isNotEmpty()) {
                    item { SectionTitle("Medicamentos") }
                    items(c.medications) { med -> MedicationItem(med) }
                }

                if (c.exams.isNotEmpty()) {
                    item { SectionTitle("Exames") }
                    items(c.exams, key = { it.id }) { exam ->
                        val resultImages = state.examResultImages[exam.id] ?: emptyList()
                        ExamItem(
                            exam = exam,
                            resultImages = resultImages,
                            onViewImages = { idx ->
                                viewModel.openViewer(resultImages, idx, onViewImages)
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir consulta") },
            text = {
                val synced = state.consultation?.remoteId != null
                val message = if (synced) {
                    "Deseja excluir esta consulta? " +
                        "Como ela já foi sincronizada, a exclusão será aplicada a todos os dispositivos da família na próxima sincronização."
                } else {
                    "Deseja excluir esta consulta?"
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text("Excluir", color = NsColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsultationHeader(c: Consultation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPaddingH, vertical = Spacing.sm)
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.medium))
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = c.date.toLongDate(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NsColors.Primary,
        )

        if (!c.doctor.name.isNullOrBlank()) {
            val doctorLabel = buildString {
                append(c.doctor.name)
                val spec = c.doctor.customSpecialty?.ifBlank { null }
                    ?: c.doctor.specialty?.let { MedicalSpecialties.labelOf(it) }
                if (!spec.isNullOrBlank()) append(" · $spec")
            }
            Text(
                text = doctorLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = NsColors.TextPrimary,
            )
        }

        if (!c.clinic.isNullOrBlank()) {
            Text(
                text = c.clinic,
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary,
            )
        }

        val hasExtras = !c.notes.isNullOrBlank() || c.tags.isNotEmpty()
        if (hasExtras) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.xs),
                color = NsColors.Border,
            )
            if (!c.notes.isNullOrBlank()) {
                Text(
                    text = c.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary,
                )
            }
            if (c.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                ) {
                    c.tags.forEach { NsChip(text = it, variant = NsChipVariant.Neutral) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NsColors.TextPrimary,
        modifier = Modifier.padding(
            horizontal = Spacing.screenPaddingH,
            vertical = 12.dp,
        ),
    )
}

@Composable
private fun MedicationItem(med: Medication) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPaddingH, vertical = 4.dp)
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.small))
            .clip(RoundedCornerShape(Radius.small))
            .background(NsColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = med.name,
                style = MaterialTheme.typography.titleSmall,
                color = NsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (med.contraindicated) {
                NsChip(text = "Contraindicado", variant = NsChipVariant.Danger)
            }
        }
        val detail = listOfNotNull(
            med.dosage,
            med.frequency,
            med.form?.label,
        ).joinToString(" · ")
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
        }
        if (!med.activeIngredient.isNullOrBlank()) {
            Text(
                text = "Princípio: ${med.activeIngredient}",
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextTertiary,
            )
        }
        if (med.efficacy != null) {
            NsChip(text = med.efficacy.label, variant = NsChipVariant.Neutral)
        }
    }
}

@Composable
private fun ExamItem(
    exam: Exam,
    resultImages: List<ImageDisplay>,
    onViewImages: (index: Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPaddingH, vertical = 4.dp)
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.small))
            .clip(RoundedCornerShape(Radius.small))
            .background(NsColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(exam.name, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary)
        if (!exam.notes.isNullOrBlank()) {
            Text(exam.notes, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
        }
        if (resultImages.isNotEmpty()) {
            ImageGrid(images = resultImages, onTap = onViewImages)
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<ImageDisplay>,
    onTap: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val columns = 2
    val rows = (images.size + columns - 1) / columns
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    if (index < images.size) {
                        val img = images[index]
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(img.url)
                                .memoryCacheKey(img.cacheKey)
                                .diskCacheKey(img.cacheKey)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(Radius.small))
                                .clickable { onTap(index) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private object MedicalSpecialties {
    fun labelOf(key: String): String =
        com.example.nossasaudeapp.domain.model.MedicalSpecialties.labelOf(key)
}
