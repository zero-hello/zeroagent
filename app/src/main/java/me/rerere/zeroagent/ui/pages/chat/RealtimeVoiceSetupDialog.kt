package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.ai.provider.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.pages.setting.components.RealtimeModelPicker

@Composable
internal fun RealtimeVoiceSetupDialog(settings: Settings, current: Model?, onDismiss: () -> Unit, onStart: (Model, ProviderSetting) -> Unit) {
    val providers = settings.providers.filter { it.enabled && (it is ProviderSetting.OpenAI || (it is ProviderSetting.Google && !it.vertexAI && !it.useServiceAccount)) }
    var providerId by remember { mutableStateOf(providers.firstOrNull { p -> p.models.any { it.id == current?.id } }?.id ?: providers.firstOrNull()?.id) }
    val provider = providers.firstOrNull { it.id == providerId }
    val previousOwner = current?.realtimeVoice?.providerId?.takeIf { it.isNotBlank() }
        ?: current?.providerOverwrite?.id?.toString()
        ?: providers.firstOrNull { p -> p.models.any { it.id == current?.id } }?.id?.toString()
    val reuse = previousOwner == providerId?.toString()
    var modelId by remember(providerId) { mutableStateOf(if (reuse) current?.realtimeVoice?.modelId.orEmpty() else "") }
    var voice by remember(providerId) { mutableStateOf(if (reuse) current?.realtimeVoice?.voice.orEmpty() else "") }
    var endpoint by remember(providerId) { mutableStateOf(if (reuse) current?.realtimeVoice?.endpoint.orEmpty() else "") }
    var protocol by remember(providerId) { mutableStateOf(if (reuse) current?.realtimeVoice?.protocol ?: RealtimeVoiceProtocol.AUTO else RealtimeVoiceProtocol.AUTO) }
    val nav = LocalNavController.current
    val gemini = protocol == RealtimeVoiceProtocol.GEMINI || (protocol == RealtimeVoiceProtocol.AUTO && provider is ProviderSetting.Google)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row { BackButton(onBack = onDismiss); Text(stringResource(R.string.za_voice_title), style = MaterialTheme.typography.headlineMedium) }
                Text(stringResource(R.string.za8_voice_setup_hint))
                if (provider != null) {
                    SelectTextField(value = provider.name, options = providers, readOnly = true,
                        onOptionSelected = { providerId = it.id }, optionToString = { it.name },
                        label = { Text(stringResource(R.string.za8_voice_provider)) }, modifier = Modifier.fillMaxWidth())
                    SelectTextField(value = when (protocol) {
                        RealtimeVoiceProtocol.AUTO -> stringResource(R.string.za_voice_protocol_auto)
                        RealtimeVoiceProtocol.OPENAI -> "OpenAI Realtime"
                        RealtimeVoiceProtocol.GEMINI -> "Gemini Live"
                    }, options = RealtimeVoiceProtocol.entries, readOnly = true,
                        onOptionSelected = { protocol = it; modelId = ""; voice = "" },
                        optionToString = { when (it) {
                            RealtimeVoiceProtocol.AUTO -> stringResource(R.string.za_voice_protocol_auto)
                            RealtimeVoiceProtocol.OPENAI -> "OpenAI Realtime"
                            RealtimeVoiceProtocol.GEMINI -> "Gemini Live"
                        } }, label = { Text(stringResource(R.string.za_voice_protocol)) }, modifier = Modifier.fillMaxWidth())
                    RealtimeModelPicker(provider, protocol, modelId) { modelId = it }
                    SelectTextField(value = voice.ifBlank { if (gemini) "Kore" else "marin" },
                        options = if (gemini) listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede") else listOf("marin", "cedar", "alloy", "ash", "coral", "sage", "verse"),
                        onValueChange = { voice = it }, onOptionSelected = { voice = it },
                        label = { Text(stringResource(R.string.za_voice_voice)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it.trim() },
                        label = { Text(stringResource(R.string.za_voice_endpoint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val hasKey = when (provider) { is ProviderSetting.OpenAI -> provider.apiKey.isNotBlank(); is ProviderSetting.Google -> provider.apiKey.isNotBlank(); else -> false }
                    Button(enabled = modelId.isNotBlank() && hasKey, onClick = {
                        val anchor = current ?: provider.models.firstOrNull { it.type == ModelType.CHAT }
                            ?: Model(modelId = modelId, displayName = modelId, inputModalities = listOf(Modality.TEXT, Modality.AUDIO), outputModalities = listOf(Modality.AUDIO))
                        onStart(anchor.copy(realtimeVoice = anchor.realtimeVoice.copy(enabled = true, modelId = modelId.trim(), providerId = provider.id.toString(), voice = voice, endpoint = endpoint, protocol = protocol)), provider)
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.za8_voice_start)) }
                }
                TextButton(onClick = { onDismiss(); nav.navigate(Screen.SettingProvider) }) { Text(stringResource(R.string.za8_voice_configure_provider)) }
            }
        }
    }
}
