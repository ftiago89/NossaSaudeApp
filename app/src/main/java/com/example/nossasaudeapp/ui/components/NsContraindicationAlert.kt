package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

@Composable
fun NsContraindicationAlert(
    medicationName: String,
    memberName: String,
    reason: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.small))
            .background(NsColors.DangerSoft)
            .height(IntrinsicSize.Min),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(NsColors.Danger),
        )
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NsColors.Danger,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Medicamento contraindicado!",
                    color = NsColors.Danger,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "\"$medicationName\" está marcado como restrito para $memberName.",
                    color = NsColors.Danger,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!reason.isNullOrBlank()) {
                    Text(
                        text = "Motivo: $reason",
                        color = NsColors.Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
