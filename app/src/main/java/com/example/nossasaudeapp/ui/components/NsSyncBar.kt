package com.example.nossasaudeapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

sealed interface NsSyncState {
    data class Synced(val lastSyncLabel: String?) : NsSyncState
    data object Syncing : NsSyncState
    data class Pending(val count: Int) : NsSyncState
    data class Error(val message: String) : NsSyncState
}

@Composable
fun NsSyncBar(
    state: NsSyncState,
    modifier: Modifier = Modifier,
) {
    val (icon, fg, bg, label, trailing) = when (state) {
        is NsSyncState.Synced -> SyncBarVisuals(
            icon = Icons.Default.CheckCircle,
            foreground = NsColors.AccentTeal,
            background = NsColors.AccentTealSoft,
            label = "Sincronizado",
            trailing = state.lastSyncLabel,
        )
        NsSyncState.Syncing -> SyncBarVisuals(
            icon = Icons.Default.Refresh,
            foreground = NsColors.AccentTeal,
            background = NsColors.AccentTealSoft,
            label = "Sincronizando…",
            trailing = null,
        )
        is NsSyncState.Pending -> SyncBarVisuals(
            icon = Icons.Default.CloudOff,
            foreground = NsColors.SyncPending,
            background = Color(0xFFFFF4E0),
            label = "Pendente de envio",
            trailing = if (state.count > 0) "${state.count}" else null,
        )
        is NsSyncState.Error -> SyncBarVisuals(
            icon = Icons.Default.Warning,
            foreground = NsColors.Danger,
            background = NsColors.DangerSoft,
            label = state.message,
            trailing = null,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.small))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val iconModifier = if (state is NsSyncState.Syncing) {
            val transition = rememberInfiniteTransition(label = "sync-rotate")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle",
            )
            Modifier.size(16.dp).rotate(angle)
        } else Modifier.size(16.dp)

        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = iconModifier)

        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )

        if (trailing != null) {
            Text(
                text = trailing,
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class SyncBarVisuals(
    val icon: ImageVector,
    val foreground: Color,
    val background: Color,
    val label: String,
    val trailing: String?,
)
