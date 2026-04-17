package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.Elevation
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

/**
 * @param searchable When false, the field is read-only and shows all options on tap (ideal for
 * short fixed lists like blood type). When true, the user can type to filter options.
 */
@Composable
fun NsSearchableDropdown(
    label: String,
    options: List<DropdownOption>,
    selectedId: String?,
    onSelect: (DropdownOption?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Selecione…",
    searchable: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = options.firstOrNull { it.id == selectedId }

    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            NsTextField(
                value = if (expanded && searchable) query else (selected?.label ?: ""),
                onValueChange = { if (searchable) { query = it; expanded = true } },
                label = label,
                placeholder = placeholder,
                readOnly = !searchable,
            )
            // Invisible tap overlay only in read-only mode to open/close the dropdown
            if (!searchable) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = !expanded },
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = NsColors.TextTertiary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 24.dp) // offset for the label above the field
                    .size(24.dp),
            )
        }

        if (expanded) {
            val filtered = if (searchable && query.isNotBlank())
                options.filter { it.label.contains(query, ignoreCase = true) }
            else
                options

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .shadow(Elevation.cardHover, RoundedCornerShape(Radius.small))
                    .clip(RoundedCornerShape(Radius.small))
                    .background(NsColors.Surface),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(filtered, key = { it.id }) { opt ->
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NsColors.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(opt)
                                    expanded = false
                                    query = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        )
                        HorizontalDivider(color = NsColors.Border)
                    }
                }
            }
        }
    }
}

data class DropdownOption(val id: String, val label: String)
