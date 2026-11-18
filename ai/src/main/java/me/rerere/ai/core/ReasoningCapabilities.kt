package me.rerere.ai.core

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.HttpException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Prefer advertised capabilities; a local thinking switch is not a cloud effort scale. */
object ReasoningCapabilities {
    private val learned = ConcurrentHashMap<String, List<String>>()
    private val mutableRevision = MutableStateFlow(0)
    val revision = mutableRevision.asStateFlow()
    private fun key(model: Model, provider: ProviderSetting) = "${provider.id}/${model.modelId}"

    /** Learn only from a real parameter-validation error; never send paid probe requests. */
    fun learnFromError(model: Model, provider: ProviderSetting, error: Throwable): Boolean {
        if (error is java.util.concurrent.CancellationException) return false
        val http = generateSequence(error) { it.cause }.take(8).filterIsInstance<HttpException>()
            .firstOrNull { it.statusCode == 400 } ?: return false
        val text = http.message.orEmpty().lowercase()
        if (!Regex("reasoning[._\\s]+effort|thinking[._\\s]*level").containsMatchIn(text)) return false
        val section = Regex("(?:supported|allowed) (?:values|levels)(?: are)?[:\\s]+([^\\n]+)")
            .find(text)?.groupValues?.get(1)?.substringBefore('.') ?: return false
        val tokens = section.split(Regex("[^a-z]+"))
        val efforts = ReasoningLevel.entries.filter { it != ReasoningLevel.AUTO && it.effort in tokens }.map { it.effort }
        if (efforts.isEmpty() || learned.put(key(model, provider), efforts) == efforts) return false
        mutableRevision.update { it + 1 }
        return true
    }
    data class Options(val levels: List<ReasoningLevel>, val detected: Boolean)
    fun forModel(model: Model?, provider: ProviderSetting? = null): Options {
        if (model == null) return Options(listOf(ReasoningLevel.AUTO), false)
        val efforts = provider?.let { learned[key(model, it)] } ?: model.supportedReasoningEfforts
        if (efforts.isNotEmpty()) {
            val advertised = efforts.mapNotNull { value ->
                if (value.lowercase() in setOf("off", "disabled")) ReasoningLevel.OFF
                else ReasoningLevel.entries.firstOrNull { it.effort == value.lowercase() }
            }.toSet()
            return Options(ReasoningLevel.entries.filter { it == ReasoningLevel.AUTO || it in advertised }, true)
        }
        // These official Moonshot models expose an on/off switch, not an effort scale.
        if (provider is ProviderSetting.OpenAI && provider.baseUrl.toHttpUrlOrNull()?.host == "api.moonshot.cn" &&
            model.modelId in setOf("kimi-k2.5", "kimi-k2.6")) {
            return Options(listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO), true)
        }
        val local = provider is ProviderSetting.LiteRtLocal || provider is ProviderSetting.LlamaCppLocal ||
            model.modelId.endsWith(".gguf", true) || model.modelId.endsWith(".litertlm", true)
        if (local) {
            val mandatory = model.modelId.contains("deepseek-r1", true)
            return Options(if (mandatory) listOf(ReasoningLevel.AUTO) else listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO), true)
        }
        if (model.supportedParameters.isNotEmpty() && ModelAbility.REASONING !in model.abilities &&
            model.supportedParameters.none { it in setOf("reasoning", "reasoning_effort", "include_reasoning", "thinking") }) {
            return Options(listOf(ReasoningLevel.OFF), true)
        }
        // A reasoning capability alone does not advertise configurable effort values.
        return Options(listOf(ReasoningLevel.AUTO), false)
    }
    fun effective(model: Model?, provider: ProviderSetting?, requested: ReasoningLevel): ReasoningLevel {
        val options = forModel(model, provider).levels
        return requested.takeIf { it in options } ?: ReasoningLevel.AUTO.takeIf { it in options } ?: options.first()
    }
}
