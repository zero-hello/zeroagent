package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.rerere.ai.provider.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.realtime.*
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.utils.localizedErrorMessage
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
fun VoiceConversationSettings(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val config = settings.voiceConversation
    val context = LocalContext.current
    val client: OkHttpClient = koinInject()
    val choices = settings.providers.filter { it.enabled && it.supportsNativeVoice() }
    val provider = choices.firstOrNull { it.id.toString() == config.providerId } ?: choices.firstOrNull()
    var options by remember(provider?.id) { mutableStateOf(emptyList<RealtimeModelOption>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val key = when (provider) { is ProviderSetting.OpenAI -> provider.apiKey; is ProviderSetting.Google -> provider.apiKey; else -> "" }
    val base = when (provider) { is ProviderSetting.OpenAI -> provider.baseUrl; is ProviderSetting.Google -> provider.baseUrl; else -> "" }
    fun editProvider(fn: (ProviderSetting) -> ProviderSetting) { provider?.let { selected -> update { s -> s.copy(providers = s.providers.map { if (it.id == selected.id) fn(it) else it }, voiceConversation = s.voiceConversation.copy(providerId = selected.id.toString(), modelId = "", enabled = false, protocol = RealtimeVoiceProtocol.AUTO, endpoint = "")) } } }
    LaunchedEffect(provider?.id, key, base, refresh) {
        options = emptyList(); error = null
        if (provider == null || key.isBlank() || base.isBlank()) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            delay(550)
            val result = RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO, forceRefresh = refresh > 0)
            options = result.models
            val selected = result.models.firstOrNull { it.id == config.modelId } ?: result.models.firstOrNull()
            if (selected != null) update { s ->
                val live = s.providers.firstOrNull { it.id == provider.id } ?: return@update s
                val liveKey = when (live) { is ProviderSetting.Google -> live.apiKey; is ProviderSetting.OpenAI -> live.apiKey; else -> "" }
                val liveBase = when (live) { is ProviderSetting.Google -> live.baseUrl; is ProviderSetting.OpenAI -> live.baseUrl; else -> "" }
                if (s.voiceConversation.providerId != config.providerId || !live.enabled || liveKey != key || liveBase != base) s
                else {
                    val currentModel = result.models.firstOrNull { it.id == s.voiceConversation.modelId } ?: selected
                    s.withVoiceModel(live, currentModel, result.protocol)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { error = e }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.zeroagent11_voice_service_hint), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.zeroagent12_voice_shared_accounts), style = MaterialTheme.typography.bodySmall)
        if (provider == null) {
            Text(stringResource(R.string.zeroagent11_voice_add_provider))
        }
        if (choices.none { it is ProviderSetting.OpenAI } || choices.none { it is ProviderSetting.Google }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (choices.none { it is ProviderSetting.OpenAI }) {
                    OutlinedButton(onClick = { update { it.withNativeVoiceProvider(RealtimeVoiceProtocol.OPENAI) } }) {
                        Text(stringResource(R.string.zeroagent12_voice_add_provider, "OpenAI"))
                    }
                }
                if (choices.none { it is ProviderSetting.Google }) {
                    OutlinedButton(onClick = { update { it.withNativeVoiceProvider(RealtimeVoiceProtocol.GEMINI) } }) {
                        Text(stringResource(R.string.zeroagent12_voice_add_provider, "Gemini"))
                    }
                }
            }
        }
        if (provider != null) {
            SelectTextField(value = provider.name, options = choices, readOnly = true, onOptionSelected = { p -> update { s -> s.copy(voiceConversation = s.voiceConversation.copy(providerId = p.id.toString(), modelId = "", enabled = false, protocol = RealtimeVoiceProtocol.AUTO, endpoint = "")) } },
                optionToString = { it.name }, label = { Text(stringResource(R.string.zeroagent8_voice_provider)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = key, onValueChange = { value -> editProvider { p -> when (p) { is ProviderSetting.OpenAI -> p.copy(apiKey = value.trim()); is ProviderSetting.Google -> p.copy(apiKey = value.trim()); else -> p } } },
                visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.setting_provider_page_api_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = base, onValueChange = { value -> editProvider { p -> when (p) { is ProviderSetting.OpenAI -> p.copy(baseUrl = value.trim()); is ProviderSetting.Google -> p.copy(baseUrl = value.trim()); else -> p } } },
                label = { Text(stringResource(R.string.zeroagent11_base_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(context.localizedErrorMessage(it), color = MaterialTheme.colorScheme.error) }
            SelectTextField(value = config.modelId, options = options, readOnly = true,
                onOptionSelected = { m -> update { s -> s.withVoiceModel(provider, m, s.voiceConversation.protocol) } }, optionToString = { it.name },
                label = { Text(stringResource(R.string.zeroagent8_voice_model)) }, modifier = Modifier.fillMaxWidth())
            if (!loading && key.isNotBlank() && options.isEmpty() && error == null) Text(stringResource(R.string.zeroagent8_voice_models_empty), color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !loading && key.isNotBlank(), onClick = { refresh++ }) { Text(stringResource(R.string.zeroagent11_detect_models)) }
            val voices = options.firstOrNull { it.id == config.modelId }?.voices.orEmpty().ifEmpty {
                if (config.protocol == RealtimeVoiceProtocol.GEMINI) listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede") else listOf("marin", "cedar", "alloy", "ash", "coral", "sage", "verse")
            }
            SelectTextField(value = config.voice.ifBlank { voices.first() }, options = voices, readOnly = true,
                onOptionSelected = { voice -> update { s -> s.copy(voiceConversation = s.voiceConversation.copy(voice = voice)) } }, optionToString = { it },
                label = { Text(stringResource(R.string.zeroagent_voice_voice)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun Settings.withVoiceModel(provider: ProviderSetting, selected: RealtimeModelOption, protocol: RealtimeVoiceProtocol): Settings {
    val current = providers.firstOrNull { it.id == provider.id } ?: return this
    val voice = voiceConversation.voice.takeIf { voiceConversation.protocol == protocol }.orEmpty()
    val config = voiceConversation.copy(enabled = true, providerId = provider.id.toString(), modelId = selected.id, protocol = protocol, voice = voice, endpoint = "")
    val model = current.models.firstOrNull { it.modelId.removePrefix("models/") == selected.id }
        ?: Model(modelId = selected.id, displayName = selected.name, inputModalities = listOf(Modality.TEXT, Modality.AUDIO), outputModalities = listOf(Modality.AUDIO), realtimeVoice = config, supportedParameters = selected.supportedParameters)
    return copy(providers = providers.map { if (it.id == current.id && model !in it.models) it.addModel(model) else it }, voiceConversation = config)
}

private fun ProviderSetting.supportsNativeVoice(): Boolean =
    this is ProviderSetting.OpenAI || (this is ProviderSetting.Google && !vertexAI && !useServiceAccount)

internal fun Settings.withNativeVoiceProvider(protocol: RealtimeVoiceProtocol): Settings {
    val accounts = providers.filter { provider ->
        when (protocol) {
            RealtimeVoiceProtocol.OPENAI -> provider is ProviderSetting.OpenAI
            RealtimeVoiceProtocol.GEMINI -> provider is ProviderSetting.Google && provider.supportsNativeVoice()
            else -> error("unsupported_protocol")
        }
    }
    val provider = accounts.firstOrNull { it.enabled }
        ?: accounts.firstOrNull {
            when (it) {
                is ProviderSetting.OpenAI -> it.apiKey.isNotBlank()
                is ProviderSetting.Google -> it.apiKey.isNotBlank()
                else -> false
            }
        }
        ?: accounts.firstOrNull()
        ?: when (protocol) {
            RealtimeVoiceProtocol.OPENAI -> ProviderSetting.OpenAI()
            RealtimeVoiceProtocol.GEMINI -> ProviderSetting.Google()
            else -> error("unsupported_protocol")
        }
    return copy(
        providers = if (providers.any { it.id == provider.id }) {
            providers.map { if (it.id == provider.id) it.copyProvider(enabled = true) else it }
        } else providers + provider,
        voiceConversation = voiceConversation.copy(
            providerId = provider.id.toString(), modelId = "", enabled = false,
            protocol = RealtimeVoiceProtocol.AUTO, endpoint = "",
        ),
    )
}
