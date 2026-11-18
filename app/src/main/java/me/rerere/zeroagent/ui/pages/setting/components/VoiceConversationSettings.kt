package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.rerere.ai.provider.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.realtime.*
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.utils.localizedErrorMessage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
fun VoiceConversationSettings(
    settings: Settings,
    client: OkHttpClient = koinInject(),
    update: ((Settings) -> Settings) -> Unit,
) {
    val config = settings.voiceConversation
    val context = LocalContext.current
    val choices = settings.providers.filter { it.enabled && it.supportsNativeVoice() }
    // A removed/disabled account must never silently switch a call to another provider.
    val provider = if (config.providerId.isBlank()) choices.firstOrNull()
        else choices.firstOrNull { it.id.toString() == config.providerId }
    val key = when (provider) {
        is ProviderSetting.OpenAI -> provider.apiKey
        is ProviderSetting.Google -> provider.apiKey
        else -> ""
    }
    val base = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl
        is ProviderSetting.Google -> provider.baseUrl
        else -> ""
    }
    val headers = provider?.customHeaders.orEmpty()
    val validEndpoint = runCatching { realtimeBaseUrl(base) }.isSuccess
    var options by remember(provider?.id, key, base, headers) { mutableStateOf(emptyList<RealtimeModelOption>()) }
    var loading by remember(provider?.id, key, base, headers) { mutableStateOf(false) }
    var error by remember(provider?.id, key, base, headers) { mutableStateOf<Throwable?>(null) }
    var connected by remember(provider?.id, key, base, headers) { mutableStateOf(false) }
    var detectedProtocol by remember(provider?.id, key, base, headers) { mutableStateOf(RealtimeVoiceProtocol.AUTO) }
    var refresh by remember(provider?.id, key, base, headers) { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    fun editProvider(change: (ProviderSetting) -> ProviderSetting) {
        provider?.let { selected -> update { current ->
            current.copy(
                providers = current.providers.map { if (it.id == selected.id) change(it) else it },
                voiceConversation = current.voiceConversation.copy(
                    providerId = selected.id.toString(), modelId = "", enabled = false,
                    protocol = RealtimeVoiceProtocol.AUTO, endpoint = "",
                ),
            )
        } }
    }
    LaunchedEffect(provider?.id, key, base, headers, refresh) {
        val request = ++generation
        options = emptyList(); error = null; connected = false
        if (provider == null || key.isBlank() || !validEndpoint) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            delay(350)
            val result = RealtimeModelCatalog.discover(client, provider, RealtimeVoiceProtocol.AUTO, forceRefresh = refresh > 0)
            if (request != generation) return@LaunchedEffect
            val effectiveModels = result.models.ifEmpty {
                when (result.protocol) {
                    RealtimeVoiceProtocol.GEMINI -> listOf(
                        RealtimeModelOption("gemini-2.0-flash-exp", "Gemini 2.0 Flash (Experimental)", voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")),
                        RealtimeModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede"))
                    )
                    else -> listOf(
                        RealtimeModelOption("gpt-4o-realtime-preview", "GPT-4o Realtime Preview", voices = listOf("alloy", "echo", "shimmer", "ash", "ballad", "coral", "sage", "verse")),
                        RealtimeModelOption("gpt-4o-mini-realtime-preview", "GPT-4o Mini Realtime Preview", voices = listOf("alloy", "echo", "shimmer", "ash", "ballad", "coral", "sage", "verse"))
                    )
                }
            }
            options = effectiveModels
            detectedProtocol = result.protocol
            connected = true
            val selected = effectiveModels.firstOrNull { it.id == config.modelId } ?: effectiveModels.firstOrNull()
            if (selected != null) update { current ->
                val live = current.providers.firstOrNull { it.id == provider.id } ?: return@update current
                val liveKey = when (live) { is ProviderSetting.Google -> live.apiKey; is ProviderSetting.OpenAI -> live.apiKey; else -> "" }
                val liveBase = when (live) { is ProviderSetting.Google -> live.baseUrl; is ProviderSetting.OpenAI -> live.baseUrl; else -> "" }
                if (current.voiceConversation.providerId != config.providerId || !live.enabled ||
                    liveKey != key || liveBase != base || live.customHeaders != headers) current
                else current.withVoiceModel(live,
                    effectiveModels.firstOrNull { it.id == current.voiceConversation.modelId } ?: selected,
                    result.protocol,
                )
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: Exception) { if (request == generation) error = failure
        } finally { if (request == generation) loading = false }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.zeroagent11_voice_service_hint), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.zeroagent12_voice_shared_accounts), style = MaterialTheme.typography.bodySmall)
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
        if (choices.isNotEmpty()) {
            SelectTextField(
                value = provider?.name.orEmpty(), options = choices, readOnly = true,
                onOptionSelected = { selected -> update { current -> current.copy(
                    voiceConversation = current.voiceConversation.copy(providerId = selected.id.toString(),
                        modelId = "", enabled = false, protocol = RealtimeVoiceProtocol.AUTO, endpoint = ""),
                ) } },
                optionToString = { it.name }, label = { Text(stringResource(R.string.zeroagent8_voice_provider)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (provider != null) {
            ProviderApiKeyField(value = key, onValueChange = { value -> editProvider { live ->
                when (live) { is ProviderSetting.OpenAI -> live.copy(apiKey = value); is ProviderSetting.Google -> live.copy(apiKey = value); else -> live }
            } })
            ProviderBaseUrlField(value = base, onValueChange = { value -> editProvider { live ->
                when (live) { is ProviderSetting.OpenAI -> live.copy(baseUrl = value); is ProviderSetting.Google -> live.copy(baseUrl = value); else -> live }
            } }, isError = base.isNotBlank() && !validEndpoint,
                supportingText = if (base.isNotBlank() && !validEndpoint) { { Text(stringResource(R.string.voice_connection_endpoint_hint)) } } else null,
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = when {
                provider == null -> stringResource(R.string.voice_connection_choose_provider)
                loading -> stringResource(R.string.voice_connection_loading)
                error != null -> stringResource(R.string.voice_connection_failed)
                connected && options.isEmpty() -> stringResource(R.string.voice_connection_no_models)
                connected -> stringResource(R.string.voice_connection_ready, options.size)
                else -> stringResource(R.string.voice_connection_idle)
            },
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        error?.let { Text(
            context.localizedErrorMessage(it) + "\n" + realtimeCatalogDiagnostic(it),
            color = MaterialTheme.colorScheme.error,
        ) }
        if (provider != null) {
            SelectTextField(
                value = options.firstOrNull { it.id == config.modelId }?.name ?: config.modelId,
                options = options, readOnly = true,
                onOptionSelected = { model -> update { current -> current.withVoiceModel(provider, model, detectedProtocol) } },
                optionToString = { it.name }, label = { Text(stringResource(R.string.zeroagent8_voice_model)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(enabled = !loading && key.isNotBlank() && validEndpoint, onClick = { refresh++ }) {
                Text(stringResource(R.string.zeroagent11_detect_models))
            }
            val voices = options.firstOrNull { it.id == config.modelId }?.voices.orEmpty().ifEmpty {
                if (config.protocol == RealtimeVoiceProtocol.GEMINI) listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede")
                else listOf("marin", "cedar", "alloy", "ash", "coral", "sage", "verse")
            }
            SelectTextField(value = config.voice.ifBlank { voices.first() }, options = voices, readOnly = true,
                onOptionSelected = { voice -> update { current -> current.copy(voiceConversation = current.voiceConversation.copy(voice = voice)) } },
                optionToString = { it }, label = { Text(stringResource(R.string.zeroagent_voice_voice)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun Settings.withVoiceModel(provider: ProviderSetting, selected: RealtimeModelOption, protocol: RealtimeVoiceProtocol): Settings {
    val current = providers.firstOrNull { it.id == provider.id } ?: return this
    val base = when (current) { is ProviderSetting.OpenAI -> current.baseUrl; is ProviderSetting.Google -> current.baseUrl; else -> "" }
    val official = base.trim().toHttpUrlOrNull()?.host in setOf("api.openai.com", "generativelanguage.googleapis.com")
    val voice = voiceConversation.voice.takeIf { voiceConversation.protocol == protocol }
        .orEmpty().takeUnless { official && it == "main" }.orEmpty()
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
