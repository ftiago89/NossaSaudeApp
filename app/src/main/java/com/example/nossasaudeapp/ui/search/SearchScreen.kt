package com.example.nossasaudeapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nossasaudeapp.ui.theme.Elevation
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius
import com.example.nossasaudeapp.ui.theme.Spacing
import com.example.nossasaudeapp.ui.util.toShortDate

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onConsultationClick: (consultationId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NsColors.Background)
            .statusBarsPadding(),
    ) {
        // Search bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { viewModel.clear(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = NsColors.TextPrimary)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Buscar consultas, médicos, medicamentos…", color = NsColors.TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NsColors.TextTertiary) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQuery("") }) {
                            Icon(Icons.Default.Close, "Limpar", tint = NsColors.TextTertiary)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* already debounced */ }),
                shape = RoundedCornerShape(Radius.full),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NsColors.Primary,
                    unfocusedBorderColor = NsColors.Border,
                    focusedTextColor = NsColors.TextPrimary,
                    unfocusedTextColor = NsColors.TextPrimary,
                    cursorColor = NsColors.Primary,
                ),
            )
        }

        when {
            state.isSearching -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NsColors.Primary)
                }
            }
            state.query.isBlank() -> {
                Text(
                    text = "Digite para buscar",
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = Spacing.screenPaddingH, vertical = 16.dp),
                )
            }
            state.results.isEmpty() -> {
                Text(
                    text = "Nenhum resultado encontrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = Spacing.screenPaddingH, vertical = 16.dp),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screenPaddingH, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(state.results, key = { it.consultation.id }) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = { onConsultationClick(item.consultation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: SearchResultItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
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
            if (item.member != null) {
                Text(
                    text = item.member.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = NsColors.Primary,
                )
            }
            Text(
                text = item.consultation.reason,
                style = MaterialTheme.typography.titleSmall,
                color = NsColors.TextPrimary,
            )
            val doctorLabel = item.consultation.doctor.name
            if (!doctorLabel.isNullOrBlank()) {
                Text(
                    text = doctorLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NsColors.TextSecondary,
                )
            }
            Text(
                text = item.consultation.date.toShortDate(),
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
