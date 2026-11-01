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
fun GeminiSafetyControls(value: GeminiSafetySettings, onChange: (GeminiSafetySettings) -> Unit, includeCivic: Boolean = false) {
    Text(stringResource(R.string.zeroagent11_safety_title), style = MaterialTheme.typography.titleMedium)
    val categories = listOf(
        "HARM_CATEGORY_HARASSMENT" to R.string.zeroagent11_safety_harassment,
        "HARM_CATEGORY_HATE_SPEECH" to R.string.zeroagent11_safety_hate,
        "HARM_CATEGORY_SEXUALLY_EXPLICIT" to R.string.zeroagent11_safety_sexual,
        "HARM_CATEGORY_DANGEROUS_CONTENT" to R.string.zeroagent11_safety_dangerous,
    ) + if (includeCivic) listOf("HARM_CATEGORY_CIVIC_INTEGRITY" to R.string.zeroagent11_safety_civic) else emptyList()
    val labels = mapOf(
        GeminiSafetyThreshold.HARM_BLOCK_THRESHOLD_UNSPECIFIED to stringResource(R.string.zeroagent11_server_default),
        GeminiSafetyThreshold.BLOCK_LOW_AND_ABOVE to stringResource(R.string.zeroagent11_safety_strict),
        GeminiSafetyThreshold.BLOCK_MEDIUM_AND_ABOVE to stringResource(R.string.zeroagent11_safety_balanced),
        GeminiSafetyThreshold.BLOCK_ONLY_HIGH to stringResource(R.string.zeroagent11_safety_high),
        GeminiSafetyThreshold.BLOCK_NONE to stringResource(R.string.zeroagent11_safety_none),
        GeminiSafetyThreshold.OFF to stringResource(R.string.zeroagent11_safety_off),
    )
    categories.forEach { (category, label) ->
        val selected = value.thresholds[category] ?: GeminiSafetyThreshold.OFF
        SelectTextField(value = labels.getValue(selected), options = GeminiSafetyThreshold.entries, readOnly = true,
            onOptionSelected = { threshold -> onChange(value.copy(thresholds = value.thresholds + (category to threshold))) },
            optionToString = { labels.getValue(it) }, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
    }
}
