package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

enum class NsChipVariant { Allergy, Condition, BloodType, Neutral, Danger }

@Composable
fun NsChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: NsChipVariant = NsChipVariant.Neutral,
    prefix: String? = null,
) {
    val (bg, fg) = when (variant) {
        NsChipVariant.Allergy -> NsColors.AllergyBg to NsColors.AllergyText
        NsChipVariant.Condition -> NsColors.ConditionBg to NsColors.ConditionText
        NsChipVariant.BloodType -> NsColors.BloodTypeBg to NsColors.BloodTypeText
        NsChipVariant.Danger -> NsColors.DangerSoft to NsColors.Danger
        NsChipVariant.Neutral -> NsColors.SurfaceWarm to NsColors.TextSecondary
    }
    NsChipRaw(text = text, backgroundColor = bg, contentColor = fg, prefix = prefix, modifier = modifier)
}

@Composable
fun NsChipRaw(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    val display = if (prefix != null) "$prefix $text" else text
    Text(
        text = display,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
