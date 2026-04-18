package com.example.nossasaudeapp.ui.member

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.ui.components.NsAvatar
import com.example.nossasaudeapp.ui.components.NsChip
import com.example.nossasaudeapp.ui.components.NsChipVariant
import com.example.nossasaudeapp.ui.components.NsContraindicationAlert
import com.example.nossasaudeapp.ui.components.NsEmptyState
import com.example.nossasaudeapp.ui.components.NsFab
import com.example.nossasaudeapp.ui.components.NsStatCardRow
import com.example.nossasaudeapp.ui.components.NsTopBar
import com.example.nossasaudeapp.ui.components.initialsFromName
import com.example.nossasaudeapp.ui.theme.Elevation
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius
import com.example.nossasaudeapp.ui.theme.Spacing
import com.example.nossasaudeapp.ui.util.ageLabel
import com.example.nossasaudeapp.ui.util.toShortDate
import kotlinx.datetime.Clock

@Composable
fun MemberProfileScreen(
    onBack: () -> Unit,
    onEdit: (memberId: String) -> Unit,
    onAddConsultation: (memberId: String) -> Unit,
    onConsultationClick: (consultationId: String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: MemberProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                NsTopBar(
                    title = state.member?.name ?: "",
                    onBack = onBack,
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = NsColors.TextPrimary,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Editar") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        state.member?.let { onEdit(it.id) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Excluir", color = NsColors.Danger) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, null, tint = NsColors.Danger)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    },
                )
            }

            state.member?.let { member ->
                item { MemberHeader(member = member) }

                if (state.contraindicatedMeds.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = Spacing.screenPaddingH,
                                vertical = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.contraindicatedMeds.forEach { med ->
                                NsContraindicationAlert(
                                    medicationName = med.name,
                                    memberName = member.name,
                                    reason = med.restrictionReason,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Consultas",
                        style = MaterialTheme.typography.titleMedium,
                        color = NsColors.TextPrimary,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPaddingH,
                            vertical = 12.dp,
                        ),
                    )
                }
            }

            if (state.consultations.isEmpty() && !state.isLoading) {
                item {
                    NsEmptyState(
                        title = "Nenhuma consulta registrada",
                        subtitle = "Toque no + para adicionar a primeira consulta",
                        icon = Icons.Default.EventNote,
                        modifier = Modifier.padding(horizontal = Spacing.screenPaddingH),
                    )
                }
            }

            items(state.consultations, key = { it.id }) { consultation ->
                ConsultationListItem(
                    consultation = consultation,
                    onClick = { onConsultationClick(consultation.id) },
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenPaddingH,
                        vertical = 4.dp,
                    ),
                )
            }
        }

        state.member?.let { member ->
            NsFab(
                onClick = { onAddConsultation(member.id) },
                icon = Icons.Default.Add,
                contentDescription = "Nova consulta",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Spacing.screenPaddingH, bottom = 32.dp),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir membro") },
            text = {
                val name = state.member?.name ?: "este membro"
                val synced = state.member?.remoteId != null
                val message = if (synced) {
                    "Tem certeza que deseja excluir $name? " +
                        "Todas as consultas também serão excluídas. " +
                        "Como este membro já foi sincronizado, a exclusão será aplicada a todos os dispositivos da família na próxima sincronização."
                } else {
                    "Tem certeza que deseja excluir $name? " +
                        "Todas as consultas também serão excluídas."
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteMember()
                }) { Text("Excluir", color = NsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberHeader(member: Member) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPaddingH, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        NsAvatar(
            initials = initialsFromName(member.name),
            colorIndex = 0,
            size = 80.dp,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleLarge,
                color = NsColors.TextPrimary,
            )
            if (member.bloodType != null) {
                NsChip(text = member.bloodType.label, variant = NsChipVariant.BloodType)
            }
        }

        val statItems = buildList<Triple<String, String, Color>> {
            member.birthDate?.let { bd ->
                val ageNum = ageLabel(bd, Clock.System.now()).substringBefore(" ")
                if (ageNum.isNotBlank()) add(Triple(ageNum, "anos", NsColors.Primary))
            }
            member.weightKg?.let { add(Triple("%.0f".format(it), "kg", NsColors.AccentTeal)) }
            member.heightCm?.let { add(Triple("%.0f".format(it), "cm", NsColors.AccentTeal)) }
        }
        if (statItems.isNotEmpty()) {
            NsStatCardRow(items = statItems)
        }

        if (member.allergies.isNotEmpty()) {
            ChipGroupCard(label = "Alergias", labelColor = NsColors.AllergyText) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                ) {
                    member.allergies.forEach { NsChip(text = it, variant = NsChipVariant.Allergy, prefix = "⚠") }
                }
            }
        }

        if (member.chronicConditions.isNotEmpty()) {
            ChipGroupCard(label = "Condições crônicas", labelColor = NsColors.ConditionText) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                ) {
                    member.chronicConditions.forEach { NsChip(text = it, variant = NsChipVariant.Condition) }
                }
            }
        }
    }
}

@Composable
private fun ChipGroupCard(
    label: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.medium))
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
        content()
    }
}

@Composable
private fun ConsultationListItem(
    consultation: Consultation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateParts = consultation.date.toShortDate().split(" ")
    val day = dateParts.getOrElse(0) { "" }
    val month = dateParts.getOrElse(1) { "" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.medium))
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .background(NsColors.PrimarySoft)
                .padding(horizontal = 14.dp, vertical = Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NsColors.Primary,
            )
            Text(
                text = month,
                style = MaterialTheme.typography.labelSmall,
                color = NsColors.TextTertiary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md, vertical = Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = consultation.reason,
                style = MaterialTheme.typography.titleSmall,
                color = NsColors.TextPrimary,
            )
            val doctorName = consultation.doctor.name
            if (!doctorName.isNullOrBlank()) {
                Text(
                    text = doctorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NsColors.TextTertiary,
            modifier = Modifier
                .padding(end = Spacing.md)
                .size(20.dp),
        )
    }
}
