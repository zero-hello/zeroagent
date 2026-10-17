package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.RealtimeVoiceProtocol
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ui.SelectTextField

@Composable
fun RealtimeVoiceSettings(model: Model, parentProvider: ProviderSetting?, onChange: (Model) -> Unit) {
    val voice = model.realtimeVoice
    val settings = me.rerere.zeroagent.ui.context.LocalSettings.current
    val voiceProviders = settings.providers.filter { it.enabled && (it is ProviderSetting.OpenAI || (it is ProviderSetting.Google && !it.vertexAI && !it.useServiceAccount)) }
    val provider = if (voice.providerId.isBlank()) model.providerOverwrite ?: parentProvider else settings.providers.firstOrNull { it.enabled && it.id.toString() == voice.providerId }
    val gemini = voice.protocol == RealtimeVoiceProtocol.GEMINI ||
        (voice.protocol == RealtimeVoiceProtocol.AUTO && provider is ProviderSetting.Google)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.za_voice_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.za_voice_enable), modifier = Modifier.weight(1f))
            Switch(checked = voice.enabled, onCheckedChange = { onChange(model.copy(realtimeVoice = voice.copy(enabled = it))) })
        }
        Text(stringResource(R.string.za_voice_hint), style = MaterialTheme.typography.bodySmall)
        if (voice.enabled) {
            SelectTextField(value = provider?.name.orEmpty(), options = voiceProviders, readOnly = true,
                onOptionSelected = { onChange(model.copy(realtimeVoice = voice.copy(providerId = it.id.toString(), modelId = "", protocol = RealtimeVoiceProtocol.AUTO, voice = ""))) },
                optionToString = { it.name }, label = { Text(stringResource(R.string.za8_voice_provider)) }, modifier = Modifier.fillMaxWidth())
            SelectTextField(value = when (voice.protocol) {
                RealtimeVoiceProtocol.AUTO -> stringResource(R.string.za_voice_protocol_auto)
                RealtimeVoiceProtocol.OPENAI -> "OpenAI Realtime"
                RealtimeVoiceProtocol.GEMINI -> "Gemini Live"
            }, options = RealtimeVoiceProtocol.entries, readOnly = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.za_voice_protocol)) },
                onOptionSelected = { onChange(model.copy(realtimeVoice = voice.copy(protocol = it, voice = "", modelId = ""))) },
                optionToString = { when (it) { RealtimeVoiceProtocol.AUTO -> stringResource(R.string.za_voice_protocol_auto); RealtimeVoiceProtocol.OPENAI -> "OpenAI Realtime"; RealtimeVoiceProtocol.GEMINI -> "Gemini Live" } })
            provider?.let { RealtimeModelPicker(it, voice.protocol, voice.modelId) { id ->
                onChange(model.copy(realtimeVoice = voice.copy(modelId = id)))
            } }
            OutlinedTextField(value = voice.endpoint, onValueChange = { onChange(model.copy(realtimeVoice = voice.copy(endpoint = it.trim()))) },
                label = { Text(stringResource(R.string.za_voice_endpoint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SelectTextField(value = voice.voice.ifBlank { if (gemini) "Kore" else "marin" },
                options = if (gemini) listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede") else listOf("marin", "cedar", "alloy", "ash", "coral", "sage", "verse"),
                onValueChange = { onChange(model.copy(realtimeVoice = voice.copy(voice = it))) },
                onOptionSelected = { onChange(model.copy(realtimeVoice = voice.copy(voice = it))) },
                label = { Text(stringResource(R.string.za_voice_voice)) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.za_voice_voice_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
