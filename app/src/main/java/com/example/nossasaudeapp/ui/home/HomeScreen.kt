package com.example.nossasaudeapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.data.sync.SyncState
import com.example.nossasaudeapp.ui.components.MemberCardUi
import com.example.nossasaudeapp.ui.components.NsEmptyState
import com.example.nossasaudeapp.ui.components.NsFab
import com.example.nossasaudeapp.ui.components.NsIconButton
import com.example.nossasaudeapp.ui.components.NsMemberCard
import com.example.nossasaudeapp.ui.components.NsStatCardRow
import com.example.nossasaudeapp.ui.components.NsSyncBar
import com.example.nossasaudeapp.ui.components.NsSyncState
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HomeScreen(
    onMemberClick: (memberId: String) -> Unit,
    onAddMember: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                onSearch = onSearch,
                onSync = { viewModel.syncNow() },
                onSettings = onSettings,
                syncState = uiState.syncState,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenPaddingH,
                    end = Spacing.screenPaddingH,
                    top = 8.dp,
                    bottom = 100.dp, // space for FAB
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item {
                    HomeSyncBanner(syncState = uiState.syncState)
                }

                if (!uiState.isLoading && uiState.stats.memberCount > 0) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        HomeStatsRow(stats = uiState.stats)
                        Spacer(Modifier.height(8.dp))
                    }
                    item {
                        Text(
                            text = "Membros",
                            style = MaterialTheme.typography.titleMedium,
                            color = NsColors.TextPrimary,
                        )
                    }
                }

                if (!uiState.isLoading && uiState.members.isEmpty()) {
                    item {
                        NsEmptyState(
                            title = "Nenhum membro cadastrado",
                            subtitle = "Adicione o primeiro membro da família",
                            ctaText = "Adicionar membro",
                            onCta = onAddMember,
                        )
                    }
                }

                itemsIndexed(
                    items = uiState.members,
                    key = { _, m -> m.id },
                ) { index, member ->
                    AnimatedMemberCard(
                        data = member,
                        index = index,
                        onClick = { onMemberClick(member.id) },
                    )
                }
            }
        }

        NsFab(
            onClick = onAddMember,
            icon = Icons.Default.Add,
            contentDescription = "Adicionar membro",
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(end = Spacing.screenPaddingH, bottom = 32.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun HomeTopBar(
    onSearch: () -> Unit,
    onSync: () -> Unit,
    syncState: SyncState,
    onSettings: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .background(NsColors.Background)
            .padding(horizontal = Spacing.screenPaddingH, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = buildAnnotatedString {
                append("Nossa")
                withStyle(SpanStyle(color = NsColors.Primary, fontWeight = FontWeight.ExtraBold)) {
                    append("Saúde")
                }
            },
            style = MaterialTheme.typography.headlineLarge,
            color = NsColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NsIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Buscar",
                onClick = onSearch,
            )
            NsIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Sincronizar",
                onClick = onSync,
                tint = when (syncState) {
                    is SyncState.Running -> NsColors.AccentTeal
                    is SyncState.Failure -> NsColors.Danger
                    else -> NsColors.TextPrimary
                },
            )
            NsIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Configurações",
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun HomeSyncBanner(syncState: SyncState) {
    val nsSyncState = syncState.toNsSyncState() ?: return
    NsSyncBar(
        state = nsSyncState,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeStatsRow(stats: HomeStats) {
    NsStatCardRow(
        items = listOf(
            Triple(stats.memberCount.toString(), "membros", NsColors.Primary),
            Triple(stats.totalConsultations.toString(), "consultas", NsColors.AccentTeal),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AnimatedMemberCard(
    data: MemberCardUi,
    index: Int,
    onClick: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(data.id) {
        kotlinx.coroutines.delay(index * 80L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            initialOffsetY = { it / 3 },
        ),
    ) {
        NsMemberCard(data = data, onClick = onClick)
    }
}

private fun SyncState.toNsSyncState(): NsSyncState? = when (this) {
    SyncState.Idle -> null
    SyncState.Running -> NsSyncState.Syncing
    is SyncState.Success -> {
        val tz = TimeZone.currentSystemDefault()
        val local = at.toLocalDateTime(tz)
        val label = "%02d:%02d".format(local.hour, local.minute)
        NsSyncState.Synced(label)
    }
    is SyncState.Failure -> NsSyncState.Error(message)
}
