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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.domain.model.Consultation
import com.example.nossasaudeapp.domain.model.Member
import com.example.nossasaudeapp.domain.model.Medication
import com.example.nossasaudeapp.ui.components.NsAvatar
import com.example.nossasaudeapp.ui.components.NsChip
import com.example.nossasaudeapp.ui.components.NsChipVariant
import com.example.nossasaudeapp.ui.components.NsContraindicationAlert
import com.example.nossasaudeapp.ui.components.NsFab
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
                    Text(
                        text = "Nenhuma consulta registrada",
                        style = MaterialTheme.typography.bodySmall,
                        color = NsColors.TextTertiary,
                        modifier = Modifier.padding(
                            horizontal = Spacing.screenPaddingH,
                            vertical = 8.dp,
                        ),
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
            .padding(horizontal = Spacing.screenPaddingH, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NsAvatar(
            initials = initialsFromName(member.name),
            colorIndex = 0,
            size = 72.dp,
        )
        Text(
            text = member.name,
            style = MaterialTheme.typography.titleMedium,
            color = NsColors.TextPrimary,
        )
        val metaParts = buildList {
            member.birthDate?.let { add(ageLabel(it, Clock.System.now())) }
            member.weightKg?.let { add("%.0fkg".format(it)) }
            member.heightCm?.let { add("%.0fcm".format(it)) }
        }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextSecondary,
            )
        }
        if (member.bloodType != null) {
            NsChip(text = member.bloodType.label, variant = NsChipVariant.BloodType)
        }
        if (member.allergies.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                member.allergies.forEach { NsChip(text = it, variant = NsChipVariant.Allergy, prefix = "⚠") }
            }
        }
        if (member.chronicConditions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                member.chronicConditions.forEach { NsChip(text = it, variant = NsChipVariant.Condition) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ConsultationListItem(
    consultation: Consultation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.medium))
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = consultation.reason,
                style = MaterialTheme.typography.titleSmall,
                color = NsColors.TextPrimary,
            )
            val doctorText = consultation.doctor.name
            if (!doctorText.isNullOrBlank()) {
                Text(
                    text = doctorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary,
                )
            }
            Text(
                text = consultation.date.toShortDate(),
                style = MaterialTheme.typography.bodySmall,
                color = NsColors.TextTertiary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NsColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}
