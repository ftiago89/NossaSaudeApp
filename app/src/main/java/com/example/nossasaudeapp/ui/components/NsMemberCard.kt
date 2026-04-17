package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.Elevation
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius
import com.example.nossasaudeapp.ui.theme.Spacing

data class MemberCardUi(
    val id: String,
    val name: String,
    val ageLabel: String?,
    val weightLabel: String?,
    val bloodType: String?,
    val allergies: List<String>,
    val conditions: List<String>,
    val lastConsultationLabel: String?,
    val avatarIndex: Int,
)

@Composable
fun NsMemberCard(
    data: MemberCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Elevation.cardDefault, RoundedCornerShape(Radius.medium))
            .clip(RoundedCornerShape(Radius.medium))
            .background(NsColors.Surface)
            .clickable(onClick = onClick),
    ) {
        // Accent bar (coral) on the left
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(NsColors.Primary.copy(alpha = 0.0f)),
        )
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NsAvatar(
                    initials = initialsFromName(data.name),
                    colorIndex = data.avatarIndex,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = NsColors.TextPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val meta = buildString {
                            if (!data.ageLabel.isNullOrBlank()) append(data.ageLabel)
                            if (!data.weightLabel.isNullOrBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(data.weightLabel)
                            }
                        }
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = NsColors.TextSecondary,
                            )
                        }
                        if (!data.bloodType.isNullOrBlank()) {
                            NsChip(text = data.bloodType, variant = NsChipVariant.BloodType)
                        }
                    }
                }
            }

            if (data.allergies.isNotEmpty() || data.conditions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowChips(
                    allergies = data.allergies,
                    conditions = data.conditions,
                )
            }

            if (!data.lastConsultationLabel.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NsColors.Border),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = NsColors.TextTertiary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Última consulta: ${data.lastConsultationLabel}",
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
        }
    }
}

@Composable
private fun FlowChips(allergies: List<String>, conditions: List<String>) {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        allergies.forEach { NsChip(text = it, variant = NsChipVariant.Allergy, prefix = "⚠") }
        conditions.forEach { NsChip(text = it, variant = NsChipVariant.Condition) }
    }
}
