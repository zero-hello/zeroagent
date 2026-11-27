package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import me.rerere.zeroagent.ui.designsystem.ZeroIconButton as IconButton
import me.rerere.zeroagent.ui.designsystem.ZeroTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.provider.isValidProviderBaseUrl

@Composable
internal fun ProviderNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.setting_provider_page_name)) },
        modifier = modifier.fillMaxWidth(),
        maxLines = maxLines,
    )
}

@Composable
internal fun ProviderApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.trim()) },
        label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
        modifier = modifier.fillMaxWidth(),
        maxLines = 3,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) HugeIcons.ViewOff else HugeIcons.View,
                    contentDescription = stringResource(
                        if (visible) R.string.accessibility_hide_password else R.string.accessibility_show_password
                    ),
                )
            }
        },
    )
}

@Composable
internal fun ProviderBaseUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = value.isNotBlank() && !value.isValidProviderBaseUrl(),
    supportingText: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.trim()) },
        label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
    )
}
