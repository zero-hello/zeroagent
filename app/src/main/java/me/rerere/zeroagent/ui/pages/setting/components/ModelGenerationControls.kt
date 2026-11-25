package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.components.ai.ReasoningButton
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.ui.components.ui.Switch

@Composable
fun ModelGenerationControls(model: Model, provider: ProviderSetting?, onChange: (Model) -> Unit) {
    val g = model.generation
    if (provider is ProviderSetting.LiteRtLocal || provider is ProviderSetting.LlamaCppLocal) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("開啟對話時預先載入模型", Modifier.weight(1f))
            Switch(checked = g.warmOnChatOpen, onCheckedChange = { enabled ->
                onChange(model.copy(generation = g.copy(warmOnChatOpen = enabled)))
            })
        }
    }
    fun change(fn: (ModelGenerationSettings) -> ModelGenerationSettings) = onChange(model.copy(generation = fn(g)))
    Text(stringResource(R.string.zeroagent11_model_parameters), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.zeroagent11_model_parameters_hint), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = g.systemPrompt, onValueChange = { text -> change { it.copy(systemPrompt = text) } },
        label = { Text(stringResource(R.string.zeroagent11_system_prompt)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10)
    NumericModelParameter(model.id.toString(), "temperature", stringResource(R.string.zeroagent11_temperature), g.temperature?.toString().orEmpty(),
        ModelParameterCapabilities.supports(model, provider, "temperature"), 0.0..if (provider is ProviderSetting.Claude) 1.0 else 2.0) { value -> change { it.copy(temperature = value?.toFloat()) } }
    NumericModelParameter(model.id.toString(), "top_p", "topP", g.topP?.toString().orEmpty(), ModelParameterCapabilities.supports(model, provider, "top_p"), 0.0..1.0) { value -> change { it.copy(topP = value?.toFloat()) } }
    NumericModelParameter(model.id.toString(), "top_k", "topK", g.topK?.toString().orEmpty(), ModelParameterCapabilities.supports(model, provider, "top_k"), (if (provider is ProviderSetting.LlamaCppLocal) 0.0 else 1.0)..1000.0, integer = true) { value -> change { it.copy(topK = value?.toInt()) } }
    NumericModelParameter(model.id.toString(), "max_tokens", stringResource(R.string.zeroagent11_max_tokens), g.maxTokens?.toString().orEmpty(), ModelParameterCapabilities.supports(model, provider, "max_tokens"), 1.0..Int.MAX_VALUE.toDouble(), integer = true) { value -> change { it.copy(maxTokens = value?.toInt()) } }
    if (me.rerere.ai.provider.ModelAbility.REASONING in model.abilities) ReasoningButton(model = model, reasoningLevel = g.reasoningLevel, onUpdateReasoningLevel = { level -> change { it.copy(reasoningLevel = level) } })
    if (model.type == ModelType.CHAT) {
        me.rerere.zeroagent.ui.pages.chat.FallbackSettings(model, me.rerere.zeroagent.ui.context.LocalSettings.current) { ids ->
            change { it.copy(fallbackModelIds = ids) }
        }
        NumericModelParameter(model.id.toString(), "turn_tools", "單次任務的工具呼叫上限",
            g.maxToolCallsPerTurn?.toString().orEmpty(), true, 1.0..1000.0, integer = true) { maximum ->
            change { it.copy(maxToolCallsPerTurn = maximum?.toInt()) }
        }
        NumericModelParameter(model.id.toString(), "monthly_budget", "每月模型預算（USD）",
            g.monthlyBudgetUsd?.toString().orEmpty(), true, 0.01..1_000_000.0) { amount ->
            change { it.copy(monthlyBudgetUsd = amount) }
        }
        NumericModelParameter(model.id.toString(), "task_budget", "單次任務預算（USD）",
            g.taskBudgetUsd?.toString().orEmpty(), true, 0.01..1_000_000.0) { amount ->
            change { it.copy(taskBudgetUsd = amount) }
        }
        NumericModelParameter(model.id.toString(), "task_output_limit", "單次任務的總輸出 Token 上限",
            g.taskOutputTokenLimit?.toString().orEmpty(), ModelParameterCapabilities.supports(model, provider, "max_tokens"),
            1.0..10_000_000.0, integer = true) { maximum ->
            change { it.copy(taskOutputTokenLimit = maximum?.toInt()) }
        }
        Text("預算依模型費率估算。需設定每次請求的輸出上限。缺少費率或剩餘額度不足時會暫停新請求。",
            style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.zeroagent11_stream_output), modifier = Modifier.weight(1f))
        Switch(checked = g.streamOutput, onCheckedChange = { enabled -> change { it.copy(streamOutput = enabled) } })
    }
}

@Composable
private fun NumericModelParameter(owner: String, key: String, label: String, value: String, supported: Boolean,
                                  range: ClosedFloatingPointRange<Double>, integer: Boolean = false, onValid: (Double?) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var text by remember(owner, key, locale) { mutableStateOf(formatModelParameterNumber(value, locale)) }
    val parsed = parseModelParameterNumber(text, locale)
    val invalid = text.isNotBlank() && (parsed == null || !parsed.isFinite() || parsed !in range || (integer && parsed % 1.0 != 0.0))
    OutlinedTextField(value = text, onValueChange = {
        text = it
        if (it.isBlank()) onValid(null)
        else parseModelParameterNumber(it, locale)?.takeIf { number -> number.isFinite() && number in range && (!integer || number % 1.0 == 0.0) }?.let(onValid)
    }, enabled = supported, singleLine = true, isError = invalid,
        label = { Text(label) }, placeholder = { Text(stringResource(R.string.zeroagent11_server_default)) },
        supportingText = if (!supported) ({ Text(stringResource(R.string.zeroagent11_parameter_unavailable)) }) else if (invalid) ({ Text(stringResource(R.string.zeroagent11_parameter_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
fun GeminiSafetyControls(value: GeminiSafetySettings, onChange: (GeminiSafetySettings) -> Unit) {
    Text(stringResource(R.string.zeroagent11_safety_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.gemini_safety_scope), style = MaterialTheme.typography.bodySmall)
    val categories = listOf(
        Triple("HARM_CATEGORY_HARASSMENT", R.string.zeroagent11_safety_harassment, R.string.gemini_safety_harassment_help),
        Triple("HARM_CATEGORY_HATE_SPEECH", R.string.zeroagent11_safety_hate, R.string.gemini_safety_hate_help),
        Triple("HARM_CATEGORY_SEXUALLY_EXPLICIT", R.string.zeroagent11_safety_sexual, R.string.gemini_safety_sexual_help),
        Triple("HARM_CATEGORY_DANGEROUS_CONTENT", R.string.zeroagent11_safety_dangerous, R.string.gemini_safety_dangerous_help),
    )
    val labels = mapOf(
        GeminiSafetyThreshold.HARM_BLOCK_THRESHOLD_UNSPECIFIED to stringResource(R.string.zeroagent11_server_default),
        GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE to stringResource(R.string.gemini_safety_block_low),
        GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE to stringResource(R.string.gemini_safety_block_medium),
        GeminiSafetyThreshold.BLOCK_ONLY_HIGH to stringResource(R.string.gemini_safety_block_high),
        GeminiSafetyThreshold.BLOCK_NONE to stringResource(R.string.gemini_safety_block_none),
        GeminiSafetyThreshold.OFF to stringResource(R.string.gemini_safety_off),
    )
    categories.forEach { (category, label, description) ->
        val selected = value.thresholds[category] ?: GeminiSafetyThreshold.OFF
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SelectTextField(
                value = labels.getValue(selected),
                options = GeminiSafetyThreshold.entries,
                readOnly = true,
                onOptionSelected = { threshold -> onChange(value.copy(thresholds = value.thresholds + (category to threshold))) },
                optionToString = { labels.getValue(it) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
    var showThresholds by remember { mutableStateOf(false) }
    TextButton(onClick = { showThresholds = !showThresholds }) {
        Text(stringResource(if (showThresholds) R.string.gemini_safety_hide_help else R.string.gemini_safety_show_help))
    }
    if (showThresholds) Text(stringResource(R.string.gemini_safety_threshold_help), style = MaterialTheme.typography.bodySmall)
}
