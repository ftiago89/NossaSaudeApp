package com.example.nossasaudeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.BuildConfig
import com.example.nossasaudeapp.data.sync.SyncState
import com.example.nossasaudeapp.ui.components.NsTopBar
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .statusBarsPadding(),
    ) {
        NsTopBar(title = "Configurações", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader("Sincronização")

            SettingsItem(
                icon = { Icon(Icons.Default.CloudSync, null, tint = NsColors.AccentTeal, modifier = Modifier.size(22.dp)) },
                title = "Sincronizar agora",
                subtitle = when (val s = state.syncState) {
                    is SyncState.Success -> "Última sync: ${s.at.toLocalDateTime(TimeZone.currentSystemDefault()).let { "%02d/%02d %02d:%02d".format(it.dayOfMonth, it.monthNumber, it.hour, it.minute) }}"
                    is SyncState.Failure -> "Erro: ${s.message}"
                    SyncState.Running -> "Sincronizando…"
                    SyncState.Idle -> "Nunca sincronizado"
                },
                onClick = { viewModel.syncNow() },
            )

            HorizontalDivider(color = NsColors.Border)

            SettingsSectionHeader("Informações")

            SettingsItem(
                icon = { Icon(Icons.Default.Info, null, tint = NsColors.TextSecondary, modifier = Modifier.size(22.dp)) },
                title = "Versão do app",
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = null,
            )
            SettingsItem(
                icon = null,
                title = "Family ID",
                subtitle = BuildConfig.FAMILY_ID.take(12) + "…",
                onClick = null,
            )
            SettingsItem(
                icon = null,
                title = "Ambiente",
                subtitle = if (BuildConfig.DEBUG) "Debug" else "Release",
                onClick = null,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NsColors.TextTertiary,
        modifier = Modifier.padding(
            horizontal = Spacing.screenPaddingH,
            vertical = 12.dp,
        ),
    )
}

@Composable
private fun SettingsItem(
    icon: (@Composable () -> Unit)?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NsColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.screenPaddingH, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = NsColors.TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NsColors.TextSecondary)
            }
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = NsColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = NsColors.Border)
}
