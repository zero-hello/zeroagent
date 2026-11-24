package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.provider.isValidProviderBaseUrl

internal data class ProviderTextField(val value: String, val edit: (String) -> Unit)

/** Shared cloud form fields. The protocol supplies its own endpoint controls as content. */
@Composable
internal fun ProviderCloudConnectionForm(
    name: ProviderTextField,
    apiKey: ProviderTextField,
    baseUrl: ProviderTextField,
    nameLines: Int,
    connectionFields: @Composable ColumnScope.() -> Unit,
) {
    ProviderFieldGroup(stringResource(R.string.provider_identity_title)) {
        ProviderNameField(name.value, name.edit, modifier = Modifier.fillMaxWidth(), maxLines = nameLines)
    }
    ProviderFieldGroup(stringResource(R.string.provider_credentials_title)) {
        ProviderApiKeyField(apiKey.value, apiKey.edit)
    }
    ProviderFieldGroup(stringResource(R.string.provider_connection_title)) {
        ProviderBaseUrlField(
            value = baseUrl.value,
            onValueChange = baseUrl.edit,
            modifier = Modifier.fillMaxWidth(),
            isError = baseUrl.value.isNotBlank() && !baseUrl.value.isValidProviderBaseUrl(),
            supportingText = { Text(stringResource(R.string.provider_base_url_hint)) },
        )
        connectionFields()
    }
}
