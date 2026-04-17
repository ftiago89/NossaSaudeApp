package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NsTagInput(
    label: String,
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Digite e toque em +",
    variant: NsChipVariant = NsChipVariant.Neutral,
) {
    var draft by remember { mutableStateOf("") }

    fun submit() {
        val value = draft.trim()
        if (value.isNotEmpty()) {
            onAdd(value)
            draft = ""
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NsTextField(
                value = draft,
                onValueChange = { draft = it },
                label = label,
                placeholder = placeholder,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar",
                tint = NsColors.Surface,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radius.small))
                    .background(NsColors.Primary)
                    .clickable(enabled = draft.isNotBlank()) { submit() }
                    .padding(8.dp),
            )
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.full))
                            .background(chipBackground(variant))
                            .clickable { onRemove(tag) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = chipForeground(variant),
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remover $tag",
                            tint = chipForeground(variant),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun chipBackground(variant: NsChipVariant) = when (variant) {
    NsChipVariant.Allergy -> NsColors.AllergyBg
    NsChipVariant.Condition -> NsColors.ConditionBg
    NsChipVariant.BloodType -> NsColors.BloodTypeBg
    NsChipVariant.Danger -> NsColors.DangerSoft
    NsChipVariant.Neutral -> NsColors.SurfaceWarm
}

private fun chipForeground(variant: NsChipVariant) = when (variant) {
    NsChipVariant.Allergy -> NsColors.AllergyText
    NsChipVariant.Condition -> NsColors.ConditionText
    NsChipVariant.BloodType -> NsColors.BloodTypeText
    NsChipVariant.Danger -> NsColors.Danger
    NsChipVariant.Neutral -> NsColors.TextSecondary
}
