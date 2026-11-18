package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.material3.ButtonDefaults

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import me.rerere.zeroagent.utils.localizedErrorMessage
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import me.rerere.zeroagent.ui.components.ui.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import me.rerere.zeroagent.ui.components.ui.ExpressiveDivider as HorizontalDivider
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.OpenRouterRouting
import me.rerere.ai.provider.OpenAIProtocol
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.ai.provider.ClaudePromptCacheTtl
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.AICoreProvider
import com.google.mlkit.genai.common.FeatureStatus
import me.rerere.llamacpp.LlamaCppCatalog
import me.rerere.llamacpp.LlamaCppCatalogEntry
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.litert.LiteRtCatalog
import me.rerere.locallm.litert.LiteRtCatalogEntry
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.api.HuggingFaceModelSearch
import me.rerere.zeroagent.data.provider.googleBaseUrlHasError
import me.rerere.zeroagent.data.provider.isValidProviderBaseUrl
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.pages.setting.locallm.SettingLocalLlmViewModel
import me.rerere.zeroagent.ui.pages.setting.locallm.LocalRuntimeControls
import me.rerere.zeroagent.ui.pages.setting.locallm.LocalDownloadControls
import me.rerere.zeroagent.ui.theme.JetbrainsMono
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        ProviderApiFormatSection(provider, onEdit)

        when (provider) {
            is ProviderSetting.OpenAI -> {
                ProviderConfigureOpenAI(provider, onEdit)
            }

            is ProviderSetting.Google -> {
                ProviderConfigureGoogle(provider, onEdit)
                GeminiSafetyControls(provider.safetySettings, { onEdit(provider.copy(safetySettings = it)) })
            }

            is ProviderSetting.Claude -> {
                ProviderConfigureClaude(provider, onEdit)
            }

            is ProviderSetting.AICore -> {
                ProviderConfigureAICore(provider, onEdit)
            }

            is ProviderSetting.LiteRtLocal -> {
                ProviderConfigureLiteRT(provider, onEdit)
            }

            is ProviderSetting.LlamaCppLocal -> {
                ProviderConfigureLlamaCpp(provider, onEdit)
            }

            is ProviderSetting.Codex -> Unit
            is ProviderSetting.Grok -> Unit
            is ProviderSetting.GeminiOAuth -> Unit
        }
    }
}

@Composable
private fun ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    provider.description()

    ProviderFieldGroup(stringResource(R.string.provider_identity_title)) {
    ProviderNameField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    }

    ProviderFieldGroup(stringResource(R.string.provider_credentials_title)) {
    ProviderApiKeyField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it)) },
    )
    }

    ProviderFieldGroup(stringResource(R.string.provider_connection_title)) {
    ProviderBaseUrlField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidProviderBaseUrl(),
        supportingText = { Text(stringResource(R.string.provider_base_url_hint)) },
    )

    if (provider.protocol != OpenAIProtocol.COHERE_V2) OutlinedTextField(
        value = if (provider.useResponseApi) provider.responsesPath else provider.chatCompletionsPath,
        onValueChange = {
            onEdit(
                if (provider.useResponseApi) {
                    provider.copy(responsesPath = it.trim())
                } else {
                    provider.copy(chatCompletionsPath = it.trim())
                }
            )
        },
        label = { Text(stringResource(R.string.setting_provider_page_api_path)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !provider.builtIn,
    )
    ProviderEndpointPreview(provider.baseUrl, when {
        provider.protocol == OpenAIProtocol.COHERE_V2 -> "/v2/chat"
        provider.useResponseApi -> provider.responsesPath
        else -> provider.chatCompletionsPath
    })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    if (provider.builtIn && provider.protocol != OpenAIProtocol.COHERE_V2 &&
        provider.id.toString() != "a28aacda-a9b8-4db7-874a-cbfa4bbce661") {
        RoutingToggle(stringResource(R.string.setting_provider_page_response_api), provider.useResponseApi) {
            onEdit(provider.copy(useResponseApi = it))
        }
    }

    // OpenRouter is the only OpenAI-compatible host where this does anything: it gates
    // the cache_control breakpoints required by Anthropic/Gemini/Qwen models routed
    // through it. Other models on OpenRouter cache automatically regardless.
    if (provider.baseUrl.toHttpUrlOrNull()?.host == "openrouter.ai") {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.setting_provider_page_claude_prompt_caching),
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = provider.promptCaching,
                onCheckedChange = {
                    onEdit(provider.copy(promptCaching = it))
                }
            )
        }
        OpenRouterRoutingSection(
            routing = provider.routing,
            onChange = { onEdit(provider.copy(routing = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_include_history_reasoning))
        Switch(
            checked = provider.includeHistoryReasoning,
            onCheckedChange = { onEdit(provider.copy(includeHistoryReasoning = it)) }
        )
    }
}

@Composable
private fun OpenRouterRoutingSection(
    routing: OpenRouterRouting,
    onChange: (OpenRouterRouting) -> Unit,
) {
    fun listToText(list: List<String>) = list.joinToString(", ")
    fun textToList(text: String) = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_title), style = MaterialTheme.typography.titleSmall)

    // Sort
    val sortOptions = listOf(null, "price", "throughput", "latency")
    val sortLabels = listOf("Auto", "Price", "Throughput", "Latency")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        sortOptions.forEachIndexed { index, option ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sortOptions.size),
                selected = routing.sort == option,
                onClick = { onChange(routing.copy(sort = option)) },
                label = {
                    // Equal-width segments are ~1/4 of the row, so the longest label
                    // ("Throughput") would wrap to two lines and break the pill on
                    // narrow screens. Keep it single-line and shrink to fit instead.
                    Text(
                        sortLabels[index],
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 14.sp,
                            stepSize = 1.sp,
                        ),
                    )
                },
            )
        }
    }

    OutlinedTextField(
        value = listToText(routing.order),
        onValueChange = { onChange(routing.copy(order = textToList(it))) },
        label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_order)) },
        placeholder = { Text("anthropic, google-vertex") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.only),
        onValueChange = { onChange(routing.copy(only = textToList(it))) },
        label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_only)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.ignore),
        onValueChange = { onChange(routing.copy(ignore = textToList(it))) },
        label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_ignore)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listToText(routing.fallbackModels),
        onValueChange = { onChange(routing.copy(fallbackModels = textToList(it))) },
        label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_fallback)) },
        placeholder = { Text("anthropic/claude-sonnet-4.5, deepseek/deepseek-chat") },
        supportingText = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_fallback_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )

    RoutingToggle(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_allow_extra), routing.allowFallbacks) {
        onChange(routing.copy(allowFallbacks = it))
    }
    RoutingToggle(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_parameters), routing.requireParameters) {
        onChange(routing.copy(requireParameters = it))
    }
    RoutingToggle(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_privacy), routing.dataCollection == "deny") {
        onChange(routing.copy(dataCollection = if (it) "deny" else null))
    }
    RoutingToggle(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_zdr), routing.zdr) {
        onChange(routing.copy(zdr = it))
    }

    Text(
        "Max price (USD per 1M tokens). Leave empty or tap the clear icon for no price limit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Local edit buffers so a partial decimal entry ("0.") isn't snapped away while typing:
    // the parsed Double drives routing, the raw text drives the field.
    var promptPriceText by remember { mutableStateOf(routing.maxPricePrompt?.toString() ?: "") }
    var completionPriceText by remember { mutableStateOf(routing.maxPriceCompletion?.toString() ?: "") }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = promptPriceText,
            onValueChange = {
                promptPriceText = it
                onChange(routing.copy(maxPricePrompt = it.toDoubleOrNull()))
            },
            label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_prompt_price)) },
            singleLine = true,
            trailingIcon = {
                if (promptPriceText.isNotEmpty()) {
                    IconButton(onClick = {
                        promptPriceText = ""
                        onChange(routing.copy(maxPricePrompt = null))
                    }) {
                        Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.accessibility_clear_text))
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = completionPriceText,
            onValueChange = {
                completionPriceText = it
                onChange(routing.copy(maxPriceCompletion = it.toDoubleOrNull()))
            },
            label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_completion_price)) },
            singleLine = true,
            trailingIcon = {
                if (completionPriceText.isNotEmpty()) {
                    IconButton(onClick = {
                        completionPriceText = ""
                        onChange(routing.copy(maxPriceCompletion = null))
                    }) {
                        Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.accessibility_clear_text))
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }

    OutlinedTextField(
        value = listToText(routing.quantizations),
        onValueChange = { onChange(routing.copy(quantizations = textToList(it))) },
        label = { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_router_quantization)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoutingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    provider.description()

    ProviderFieldGroup(stringResource(R.string.provider_identity_title)) {
    ProviderNameField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )
    }

    ProviderFieldGroup(stringResource(R.string.provider_credentials_title)) {
    ProviderApiKeyField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it)) },
    )
    }

    ProviderFieldGroup(stringResource(R.string.provider_connection_title)) {
    ProviderBaseUrlField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidProviderBaseUrl(),
        supportingText = { Text(stringResource(R.string.provider_base_url_hint)) },
    )
    ProviderEndpointPreview(provider.baseUrl, "messages")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_claude_prompt_caching))
        Switch(
            checked = provider.promptCaching,
            onCheckedChange = { onEdit(provider.copy(promptCaching = it)) }
        )
    }

    if (provider.promptCaching) {
        Text(stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ClaudePromptCacheTtl.entries.forEachIndexed { index, ttl ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ClaudePromptCacheTtl.entries.size
                    ),
                    label = {
                        Text(
                            when (ttl) {
                                ClaudePromptCacheTtl.FIVE_MINUTES -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_5m)
                                ClaudePromptCacheTtl.ONE_HOUR -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_1h)
                            }
                        )
                    },
                    selected = provider.promptCacheTtl == ttl,
                    onClick = { onEdit(provider.copy(promptCacheTtl = ttl)) }
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.privateKey,
                )
            )
            toaster.show(resources.getString(R.string.zeroagent_language_google_import_success), type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show(resources.getString(R.string.zeroagent_language_google_import_failed, e.message.orEmpty()), type = ToastType.Error)
        }
    }

    provider.description()

    ProviderNameField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (!(provider.vertexAI && provider.useServiceAccount)) {
        ProviderApiKeyField(
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it)) },
        )
    }

    if (!provider.vertexAI) {
        ProviderBaseUrlField(
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            isError = googleBaseUrlHasError(provider.baseUrl),
            supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                { Text(androidx.compose.ui.res.stringResource(me.rerere.zeroagent.R.string.zeroagent6s_provider_google_url)) }
            } else null,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.setting_provider_page_vertex_ai))
        Switch(
            checked = provider.vertexAI,
            onCheckedChange = { onEdit(provider.copy(vertexAI = it)) }
        )
    }

    if (provider.vertexAI) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.setting_provider_page_use_service_account))
            Switch(
                checked = provider.useServiceAccount,
                onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) }
            )
        }
    }

    if (provider.vertexAI && provider.useServiceAccount) {
        OutlinedButton(
            shapes = ButtonDefaults.shapes(), onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setting_provider_page_import_service_account_json))
        }

        OutlinedTextField(
            value = provider.serviceAccountEmail,
            onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_service_account_email)) },
            modifier = Modifier.fillMaxWidth(),
        )

        var privateKeyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = provider.privateKey,
            onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_private_key)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono),
            visualTransformation = if (privateKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { privateKeyVisible = !privateKeyVisible }) {
                    Icon(if (privateKeyVisible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = stringResource(if (privateKeyVisible) R.string.accessibility_hide_password else R.string.accessibility_show_password))
                }
            },
        )

        OutlinedTextField(
            value = provider.location,
            onValueChange = { onEdit(provider.copy(location = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_location)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = provider.projectId,
            onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
            label = { Text(stringResource(R.string.setting_provider_page_project_id)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.ProviderConfigureAICore(
    provider: ProviderSetting.AICore,
    onEdit: (provider: ProviderSetting.AICore) -> Unit,
) {
    val context = LocalContext.current
    val status by produceState<Int?>(null, provider.releaseStage, provider.models) {
        value = runCatching { AICoreProvider(context).checkStatus(provider) }
            .getOrElse { FeatureStatus.UNAVAILABLE }
    }

    provider.description()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(id = R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    // Release-stage radio. PREVIEW pulls a higher-quality but more flappy build of Gemini
    // Nano; STABLE is the default. Spec details in
    // docs/superpowers/specs/2026-05-04-aicore-provider-design.md.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.setting_provider_aicore_release_stage), modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            val stages = me.rerere.ai.provider.AICoreReleaseStage.entries
            stages.forEachIndexed { index, stage ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = stages.size),
                    label = { Text(stage.name) },
                    selected = provider.releaseStage == stage,
                    onClick = { onEdit(provider.copy(releaseStage = stage)) },
                )
            }
        }
    }

    Text(
        text = stringResource(
            when (status) {
                FeatureStatus.AVAILABLE -> R.string.setting_provider_aicore_ready
                FeatureStatus.DOWNLOADABLE -> R.string.setting_provider_aicore_downloadable
                FeatureStatus.DOWNLOADING -> R.string.setting_provider_aicore_downloading
                FeatureStatus.UNAVAILABLE -> R.string.setting_provider_aicore_unavailable
                else -> R.string.setting_provider_aicore_checking
            },
        ),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.setting_provider_aicore_status_help),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ProviderConfigureLiteRT(
    provider: ProviderSetting.LiteRtLocal,
    onEdit: (ProviderSetting.LiteRtLocal) -> Unit,
) {
    val vm = koinViewModel<SettingLocalLlmViewModel>(
        key = "configure-${LocalRuntime.LiteRT.displayName}",
        parameters = { parametersOf(LocalRuntime.LiteRT) },
    )
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val maxNumTokensOverride by vm.maxNumTokensOverride.collectAsStateWithLifecycle()
    val crashRecoveryAccel by vm.crashRecoveryAccelerator.collectAsStateWithLifecycle()
    val installedModelFiles by vm.installedModelFiles.collectAsStateWithLifecycle()
    val visionUnavailableSet by vm.visionUnavailableSet.collectAsStateWithLifecycle()
    val perfTelemetry by vm.perfTelemetry.collectAsStateWithLifecycle()

    provider.description()

    // Friendly post-crash banner. Default tone is "we handled it", not "panic".
    crashRecoveryAccel?.let { accel ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.dismissCrashRecovery() },
        ) {
            Text(
                text = stringResource(R.string.local_llm_crash_recovery_format, accel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(id = R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    // Installed model count — model management is on the Models tab (page 1).
    Text(
        text = stringResource(R.string.local_llm_installed_models_count, provider.models.size),
        style = MaterialTheme.typography.bodySmall,
    )

    // Manage installed files — rename or delete each downloaded .litertlm.
    if (provider.models.isNotEmpty()) {
        Text(
            stringResource(R.string.local_llm_manage_files_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        provider.models.forEach { model ->
            InstalledModelRow(
                model = model,
                visionUnavailable = model.modelId in visionUnavailableSet,
                // After a native crash inside liblitertlm we suppress the Re-try
                // vision button: re-trying immediately is what just crashed the app.
                // The user dismisses the crash banner (above) when they want to opt
                // back in to taking that risk; the button reappears.
                allowVisionRetry = crashRecoveryAccel == null,
                perfSample = perfTelemetry[model.modelId],
                onRename = { newName -> vm.renameModel(model.modelId, newName) },
                onDelete = { vm.deleteModel(model.modelId) },
                onRetryVision = { vm.retryVisionEncoder(model.modelId) },
            )
        }
    }

    LocalRuntimeControls(vm)
    LocalDownloadControls(vm)

    // Max-context override. Lets users push capable models (Gemma 4 E2B = 32k) past
    // Gallery's curated defaults — the model's underlying KV cache size is still the
    // hard ceiling (Qwen `ekv4096` rejects values above 4096 regardless of this).
    var maxTokensInput by remember(maxNumTokensOverride) {
        mutableStateOf(maxNumTokensOverride?.toString() ?: "")
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.local_llm_max_tokens_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.local_llm_max_tokens_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = maxTokensInput,
                onValueChange = { newValue ->
                    // Accept digits-only input; empty string = use curated default.
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        maxTokensInput = newValue
                        val parsed = newValue.toIntOrNull()?.takeIf { it in 1..131072 }
                        vm.setMaxNumTokensOverride(parsed)
                    }
                },
                placeholder = {
                    Text(stringResource(R.string.local_llm_max_tokens_placeholder))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(
                shapes = ButtonDefaults.shapes(), onClick = {
                    maxTokensInput = ""
                    vm.setMaxNumTokensOverride(null)
                },
                enabled = maxNumTokensOverride != null,
            ) {
                Text(stringResource(R.string.local_llm_max_tokens_reset))
            }
        }
    }

    // Download progress indicator.
    downloadProgress?.let { progress ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.totalBytes != null && progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(R.string.local_llm_download_progress, progress.percent),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    // Error text + optional "Delete model" action when a model file is the likely culprit.
    errorMessage?.let { msg ->
        Text(
            text = stringResource(R.string.local_llm_status_error_format, androidx.compose.ui.platform.LocalContext.current.localizedErrorMessage(raw = msg)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        // If there are installed models, offer to delete them so the user can clear a
        // broken file (e.g. wrong runtime version) without navigating away.
        if (provider.models.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                provider.models.forEach { model ->
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { vm.deleteModel(model.modelId) }) {
                        Text(
                            text = stringResource(R.string.local_llm_delete_model) +
                                if (provider.models.size > 1) " ${model.modelId}" else "",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ProviderConfigureLlamaCpp(
    provider: ProviderSetting.LlamaCppLocal,
    onEdit: (ProviderSetting.LlamaCppLocal) -> Unit,
) {
    val vm = koinViewModel<SettingLocalLlmViewModel>(
        key = "configure-${LocalRuntime.LlamaCpp.displayName}",
        parameters = { parametersOf(LocalRuntime.LlamaCpp) },
    )
    LocalRuntimeControls(vm)
    LocalDownloadControls(vm)
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val installedModelFiles by vm.installedModelFiles.collectAsStateWithLifecycle()
    val perfTelemetry by vm.perfTelemetry.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        } ?: uri.lastPathSegment ?: "model.gguf"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        vm.installPickedFile(inputStream, fileName)
    }

    provider.description()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(id = R.string.setting_provider_page_enable), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
    }

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(id = R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    // Installed model count — model management is on the Models tab (page 1).
    Text(
        text = stringResource(R.string.local_llm_installed_models_count, provider.models.size),
        style = MaterialTheme.typography.bodySmall,
    )

    // Install a GGUF the user already has on-device, via the storage file picker.
    OutlinedButton(
        shapes = ButtonDefaults.shapes(), onClick = { filePicker.launch(arrayOf("application/octet-stream")) },
        enabled = downloadProgress == null,
    ) {
        Text(stringResource(R.string.local_llm_llamacpp_import_action))
    }

    // Manage installed files — rename or delete each downloaded GGUF.
    if (provider.models.isNotEmpty()) {
        Text(
            stringResource(R.string.local_llm_manage_files_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        provider.models.forEach { model ->
            InstalledModelRow(
                model = model,
                // llama.cpp is CPU-only with no vision support in this build (vision and
                // mmproj are a spec non-goal) — there is no vision encoder that could
                // fail, so this row never shows the vision caption or retry button.
                visionUnavailable = false,
                allowVisionRetry = false,
                perfSample = perfTelemetry[model.modelId],
                onRename = { newName -> vm.renameModel(model.modelId, newName) },
                onDelete = { vm.deleteModel(model.modelId) },
                onRetryVision = {},
            )
        }
    }

    // Download progress indicator.
    downloadProgress?.let { progress ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.totalBytes != null && progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(R.string.local_llm_download_progress, progress.percent),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    // Error text.
    errorMessage?.let { msg ->
        Text(
            text = stringResource(R.string.local_llm_status_error_format, androidx.compose.ui.platform.LocalContext.current.localizedErrorMessage(raw = msg)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun HuggingFaceFileRow(
    fileName: String,
    sizeBytes: Long,
    installed: Boolean,
    downloadInProgress: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        stringResource(R.string.local_llm_hf_file_size_gb_format),
                        sizeBytes / 1_000_000_000.0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installed) {
                Text(
                    text = stringResource(R.string.local_llm_catalog_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    shapes = ButtonDefaults.shapes(), onClick = onInstall,
                    enabled = !downloadInProgress,
                ) {
                    Text(stringResource(R.string.local_llm_catalog_install))
                }
            }
        }
    }
}

@Composable
private fun LlamaCppCatalogEntryCard(
    entry: LlamaCppCatalogEntry,
    installed: Boolean,
    downloadInProgress: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                entry.repo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.tags.forEach { tag ->
                        val labelRes = when (tag) {
                            "thinking" -> R.string.local_llm_catalog_tag_thinking
                            "tools" -> R.string.local_llm_catalog_tag_tools
                            else -> null
                        }
                        val label = labelRes?.let { stringResource(it) } ?: tag
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surface,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            Text(
                text = String.format(
                    java.util.Locale.US,
                    stringResource(R.string.local_llm_catalog_size_format),
                    entry.sizeBytes / 1_000_000_000.0,
                    entry.minMemGb,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_llm_catalog_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        shapes = ButtonDefaults.shapes(), onClick = onInstall,
                        enabled = !downloadInProgress,
                    ) {
                        Text(stringResource(R.string.local_llm_catalog_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteRtCatalogEntryCard(
    entry: LiteRtCatalogEntry,
    installed: Boolean,
    downloadInProgress: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (entry.recommended) {
                    Text(
                        text = stringResource(R.string.local_llm_catalog_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.tags.forEach { tag ->
                        val labelRes = when (tag) {
                            "multimodal" -> R.string.local_llm_catalog_tag_multimodal
                            "thinking" -> R.string.local_llm_catalog_tag_thinking
                            "speculative-decoding" -> R.string.local_llm_catalog_tag_speculative
                            else -> null
                        }
                        val label = labelRes?.let { stringResource(it) } ?: tag
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surface,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            Text(
                text = String.format(
                    java.util.Locale.US,
                    stringResource(R.string.local_llm_catalog_size_format),
                    entry.sizeBytes / 1_000_000_000.0,
                    entry.minDeviceMemoryGb,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_llm_catalog_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        shapes = ButtonDefaults.shapes(), onClick = onInstall,
                        enabled = !downloadInProgress,
                    ) {
                        Text(stringResource(R.string.local_llm_catalog_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledModelRow(
    model: Model,
    visionUnavailable: Boolean,
    allowVisionRetry: Boolean,
    perfSample: me.rerere.locallm.LocalRuntimePreferences.PerfSample?,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRetryVision: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember(model.id) { mutableStateOf(model.displayName) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (renaming) {
        OutlinedTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = { Text(stringResource(R.string.local_llm_rename_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { renaming = false }) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            Button(
                shapes = ButtonDefaults.shapes(), onClick = {
                    onRename(renameText)
                    renaming = false
                },
                enabled = renameText.isNotBlank() && renameText != model.displayName,
            ) {
                Text(stringResource(R.string.local_llm_rename_save))
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    model.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
                if (visionUnavailable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.local_llm_vision_unavailable_caption),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (allowVisionRetry) {
                            TextButton(
                                shapes = ButtonDefaults.shapes(), onClick = onRetryVision,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    stringResource(R.string.local_llm_vision_retry),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                perfSample?.let { sample ->
                    Text(
                        text = stringResource(
                            R.string.local_llm_perf_telemetry_format,
                            sample.prefillTps,
                            sample.decodeTps,
                        ) + if (sample.specDecodingEngaged) " · " +
                            stringResource(R.string.local_llm_spec_decoding_engaged_short)
                        else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
            IconButton(onClick = { renaming = true }) {
                Icon(HugeIcons.Edit01, stringResource(R.string.local_llm_rename))
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(HugeIcons.Delete01, stringResource(R.string.local_llm_delete))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.local_llm_delete))
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Catalog tab uses the same lifecycle-owned downloader and installer as configuration. */
@Composable
fun ColumnScope.LocalModelDownloadContent(provider: ProviderSetting) {
    val runtime = if (provider is ProviderSetting.LiteRtLocal) LocalRuntime.LiteRT else LocalRuntime.LlamaCpp
    val vm = koinViewModel<SettingLocalLlmViewModel>(key = "configure-${runtime.displayName}", parameters = { parametersOf(runtime) })
    LocalDownloadControls(vm)
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val installedModelFiles by vm.installedModelFiles.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    if (provider is ProviderSetting.LiteRtLocal) {
    // URL install field — paste an HF URL, hit Install.
    var manualUrl by remember { mutableStateOf("") }
    OutlinedTextField(
        value = manualUrl,
        onValueChange = { manualUrl = it },
        label = { Text(stringResource(R.string.local_llm_install_url_label)) },
        supportingText = { Text(stringResource(R.string.local_llm_install_url_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            shapes = ButtonDefaults.shapes(), onClick = {
                vm.startManualDownload(manualUrl)
                manualUrl = ""
            },
            enabled = manualUrl.isNotBlank() && downloadProgress == null,
        ) {
            Text(stringResource(R.string.local_llm_install_url_action))
        }
        OutlinedButton(
            shapes = ButtonDefaults.shapes(), onClick = { vm.startDefaultDownload() },
            enabled = downloadProgress == null,
        ) {
            Text(stringResource(R.string.local_llm_download_default))
        }
    }

    // Recommended-models curated picker. Sourced from Google AI Edge Gallery's allowlist
    // (LiteRtCatalog.ENTRIES). Per-entry Install button calls the same startManualDownload
    // path the URL-paste field uses, so the install flow is identical.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.local_llm_catalog_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.local_llm_catalog_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LiteRtCatalog.ENTRIES.forEach { entry ->
            LiteRtCatalogEntryCard(
                entry = entry,
                installed = entry.modelFile in installedModelFiles,
                downloadInProgress = downloadProgress != null,
                onInstall = { vm.startManualDownload(entry.resolveUrl()) },
            )
        }
    }

    } else {
    // Curated GGUF picker (LlamaCppCatalog.ENTRIES). Per-entry Install button calls the
    // same startManualDownload path the LiteRT catalog uses below, so the install flow
    // (and the progress/error reporting at the bottom of this tile) is identical.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.local_llm_llamacpp_catalog_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.local_llm_llamacpp_catalog_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LlamaCppCatalog.ENTRIES.forEach { entry ->
            LlamaCppCatalogEntryCard(
                entry = entry,
                installed = entry.file in installedModelFiles,
                downloadInProgress = downloadProgress != null,
                onInstall = { vm.startManualDownload(entry.resolveUrl()) },
            )
        }
    }

    // HuggingFace search: the public, unauthenticated model API filtered to GGUF repos. A
    // chosen file installs through the same startManualDownload path as the URL field and
    // the curated catalog above, so download/resume/magic-byte behaviour is identical.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.local_llm_hf_search_title),
            style = MaterialTheme.typography.titleSmall,
        )

        var hfQuery by remember { mutableStateOf("") }
        val hfSearchResults by vm.hfSearchResults.collectAsStateWithLifecycle()
        val hfSearchInProgress by vm.hfSearchInProgress.collectAsStateWithLifecycle()
        val hfSearchHasMore by vm.hfSearchHasMore.collectAsStateWithLifecycle()
        val hfSelectedRepoId by vm.hfSelectedRepoId.collectAsStateWithLifecycle()
        val hfSelectedRepoFiles by vm.hfSelectedRepoFiles.collectAsStateWithLifecycle()

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hfQuery,
                onValueChange = { hfQuery = it },
                label = { Text(stringResource(R.string.local_llm_hf_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                shapes = ButtonDefaults.shapes(), onClick = { vm.searchHuggingFace(hfQuery) },
                enabled = !hfSearchInProgress,
            ) {
                Text(
                    stringResource(
                        if (hfQuery.isBlank()) R.string.local_llm_hf_browse_action
                        else R.string.local_llm_hf_search_action
                    )
                )
            }
        }

        Text(
            stringResource(R.string.local_llm_hf_catalog_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (hfSearchInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val selectedRepoId = hfSelectedRepoId
        if (selectedRepoId == null) {
            if (!hfSearchInProgress && hfSearchResults.isEmpty()) {
                Text(
                    stringResource(R.string.local_llm_hf_search_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hfSearchResults.forEach { repo ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectHuggingFaceRepo(repo.id) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(repo.id, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.local_llm_hf_downloads_format, repo.downloads),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hfSearchHasMore) {
                OutlinedButton(
                    onClick = vm::loadMoreHuggingFace,
                    enabled = !hfSearchInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.local_llm_hf_load_more))
                }
            }
        } else {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.clearHuggingFaceSelection() }) {
                Text(stringResource(R.string.local_llm_hf_back_to_results))
            }
            when (val filesResult = hfSelectedRepoFiles) {
                null -> Text(
                    stringResource(R.string.local_llm_hf_loading_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is HuggingFaceModelSearch.FilesResult.RequiresAccess -> Text(
                    stringResource(R.string.local_llm_hf_requires_access),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is HuggingFaceModelSearch.FilesResult.Error -> Text(
                    stringResource(R.string.local_llm_hf_files_error_format, filesResult.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is HuggingFaceModelSearch.FilesResult.Files -> if (filesResult.entries.isEmpty()) {
                    Text(
                        stringResource(R.string.local_llm_hf_no_gguf_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    filesResult.entries.forEach { file ->
                        HuggingFaceFileRow(
                            fileName = file.fileName,
                            sizeBytes = file.sizeBytes,
                            installed = file.fileName in installedModelFiles,
                            downloadInProgress = downloadProgress != null,
                            onInstall = { vm.installFromHuggingFace(selectedRepoId, file.fileName) },
                        )
                    }
                }
            }
        }
    }

    }
    downloadProgress?.let { progress ->
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.local_llm_download_progress, progress.percent))
    }
    errorMessage?.let { Text(LocalContext.current.localizedErrorMessage(raw = it), color = MaterialTheme.colorScheme.error) }
}
