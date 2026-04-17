package com.example.nossasaudeapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.nossasaudeapp.ui.theme.NsColors
import com.example.nossasaudeapp.ui.theme.Radius

@Composable
fun NsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supportingText: String? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val labelText: AnnotatedString = buildAnnotatedString {
        append(label)
        if (required) {
            pushStyle(SpanStyle(color = NsColors.Primary))
            append(" *")
            pop()
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.bodySmall,
            color = NsColors.TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it, color = NsColors.TextTertiary) } },
            singleLine = singleLine,
            readOnly = readOnly,
            isError = isError,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(Radius.small),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = keyboardActions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NsColors.Primary,
                unfocusedBorderColor = NsColors.Border,
                errorBorderColor = NsColors.Danger,
                focusedTextColor = NsColors.TextPrimary,
                unfocusedTextColor = NsColors.TextPrimary,
                cursorColor = NsColors.Primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) NsColors.Danger else NsColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}
