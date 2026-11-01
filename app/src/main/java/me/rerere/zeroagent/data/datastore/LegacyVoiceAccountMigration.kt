package me.rerere.zeroagent.data.datastore

import kotlinx.serialization.json.*
import me.rerere.ai.provider.*
import java.net.URI

internal data class MigratedVoiceAccount(val providers: List<ProviderSetting>, val voice: RealtimeVoiceSetting)

/** Retired dictation credentials remain local; only compatible account types migrate to native setup. */
internal fun migrateLegacyVoiceAccount(providers: List<ProviderSetting>, voice: RealtimeVoiceSetting, raw: String?, selected: String?): MigratedVoiceAccount {
    if (voice.providerId.isNotBlank()) return MigratedVoiceAccount(providers, voice)
    val previous = providers.firstNotNullOfOrNull { p -> p.models.firstOrNull { it.realtimeVoice.enabled && it.realtimeVoice.modelId.isNotBlank() }?.let { p to it } }
    if (previous != null) {
        val config = previous.second.realtimeVoice.copy(providerId = previous.first.id.toString())
        val exists = previous.first.models.any { it.modelId.removePrefix("models/") == config.modelId.removePrefix("models/") }
        val migrated = if (exists) providers else {
            val stable = kotlin.uuid.Uuid.parse(java.util.UUID.nameUUIDFromBytes(("native-voice-model:" + previous.first.id + ":" + config.modelId).toByteArray()).toString())
            val native = previous.second.copy(id = stable, modelId = config.modelId.removePrefix("models/"), displayName = config.modelId.removePrefix("models/"),
                inputModalities = listOf(Modality.TEXT, Modality.AUDIO), outputModalities = listOf(Modality.AUDIO), realtimeVoice = config)
            providers.map { if (it.id == previous.first.id) it.addModel(native) else it }
        }
        return MigratedVoiceAccount(migrated, config)
    }
    val accounts = runCatching { Json.parseToJsonElement(raw.orEmpty()) as? JsonArray }.getOrNull().orEmpty().mapNotNull { it as? JsonObject }
    fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    val candidates = accounts.filter { it.text("type") == "openai_realtime" || (it.text("type") == "cloud_speech" && it.text("service") == "GEMINI") }
    val account = candidates.firstOrNull { it.text("id") == selected } ?: candidates.firstOrNull() ?: return MigratedVoiceAccount(providers, voice)
    val key = account.text("apiKey"); if (key.isBlank()) return MigratedVoiceAccount(providers, voice)
    val gemini = account.text("service") == "GEMINI"
    val base = if (gemini) account.text("endpoint").ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.substringBefore("/models/")
        else runCatching {
            val original = URI(account.text("websocketUrl").ifBlank { "wss://api.openai.com/v1/realtime" })
            URI(if (original.scheme == "ws") "http" else "https", null, original.host, original.port, original.path.removeSuffix("/realtime"), null, null).toString()
        }.getOrElse { account.text("websocketUrl") }
    val existing = providers.firstOrNull { p -> when (p) {
        is ProviderSetting.Google -> gemini && p.apiKey == key && p.baseUrl.trimEnd('/') == base.trimEnd('/')
        is ProviderSetting.OpenAI -> !gemini && p.apiKey == key && p.baseUrl.trimEnd('/') == base.trimEnd('/')
        else -> false
    } }
    val provider = existing ?: if (gemini) ProviderSetting.Google(name = account.text("name").ifBlank { "Gemini" }, apiKey = key, baseUrl = base)
        else ProviderSetting.OpenAI(name = "OpenAI", apiKey = key, baseUrl = base)
    return MigratedVoiceAccount(if (existing == null) providers + provider else providers,
        voice.copy(providerId = provider.id.toString(), modelId = "", enabled = false, protocol = RealtimeVoiceProtocol.AUTO, endpoint = ""))
}
