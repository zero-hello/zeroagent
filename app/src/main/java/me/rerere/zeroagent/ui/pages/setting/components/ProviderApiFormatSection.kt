package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.OpenAIProtocol
import me.rerere.ai.provider.resolveProviderEndpoint
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.provider.ProviderApiFormat
import me.rerere.zeroagent.data.provider.withApiFormat
import me.rerere.zeroagent.ui.components.ui.ExpressiveDivider
import me.rerere.zeroagent.ui.components.ui.SelectableOptionRow

@Composable
internal fun ProviderFieldGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            ExpressiveDivider(horizontalInset = 0.dp)
            content()
        }
    }
}

@Composable
internal fun ProviderApiFormatSection(provider: ProviderSetting, onEdit: (ProviderSetting) -> Unit) {
    if (provider is ProviderSetting.OpenAI && provider.protocol == OpenAIProtocol.COHERE_V2) {
        ProviderFieldGroup(stringResource(R.string.provider_api_format_title)) {
            Text(stringResource(R.string.provider_api_format_cohere), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (provider.builtIn) return
    if (provider is ProviderSetting.Google) {
        ProviderFieldGroup(stringResource(R.string.provider_api_format_title)) {
            Text(stringResource(R.string.provider_api_format_google_legacy), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (provider !is ProviderSetting.OpenAI && provider !is ProviderSetting.Claude) return
    val selected = when (provider) {
        is ProviderSetting.OpenAI -> if (provider.useResponseApi) ProviderApiFormat.OPENAI_RESPONSES else ProviderApiFormat.CHAT_COMPLETIONS
        else -> ProviderApiFormat.ANTHROPIC_MESSAGES
    }
    ProviderFieldGroup(stringResource(R.string.provider_api_format_title)) {
        Text(stringResource(R.string.provider_api_format_hint), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderApiFormat.entries.forEach { format ->
            SelectableOptionRow(
                selected = selected == format,
                onClick = { onEdit(provider.withApiFormat(format)) },
                label = when (format) {
                        ProviderApiFormat.CHAT_COMPLETIONS -> "Chat Completions"
                        ProviderApiFormat.OPENAI_RESPONSES -> "OpenAI Responses"
                        ProviderApiFormat.ANTHROPIC_MESSAGES -> "Anthropic Messages"
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
    }
}

@Composable
internal fun ProviderEndpointPreview(baseUrl: String, path: String) {
    // Use the exact resolver used by requests; never expose embedded URL credentials.
    val endpoint = runCatching { resolveProviderEndpoint(baseUrl, path) }.getOrNull()
    Text(
        text = if (endpoint == null) stringResource(R.string.provider_endpoint_invalid)
        else stringResource(R.string.provider_endpoint_preview, endpoint),
        style = MaterialTheme.typography.bodySmall,
        color = if (endpoint == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
