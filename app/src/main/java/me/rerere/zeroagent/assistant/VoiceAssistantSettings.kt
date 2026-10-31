package me.rerere.zeroagent.assistant

import kotlinx.serialization.Serializable

@Serializable
data class VoiceAssistantSettings(
    val enabled: Boolean = false,
    val wakeEnabled: Boolean = false,
    val wakePhrase: String = "Hey Zero",
    val wakeEngine: String = "local-acoustic-v1",
    val modelId: String = "",
    val sttProviderId: String = "",
    val systemSttNetworkAllowed: Boolean = false,
    val ttsProviderId: String = "",
    val sttLanguage: String = "",
    val ttsLanguage: String = "",
    val wakeLanguage: String = "",
    val systemTtsEngine: String = "",
    val systemTtsVoice: String = "",
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val reducedEffects: Boolean = false,
) {
    companion object { const val FIXED_WAKE_PHRASE = "Hey Zero" }
    fun sttTag(appTag: String) = sttLanguage.ifBlank { appTag }
    fun ttsTag(appTag: String) = ttsLanguage.ifBlank { appTag }
    fun wakeTag(appTag: String) = wakeLanguage.ifBlank { appTag }
}

suspend fun me.rerere.zeroagent.data.datastore.SettingsStore.updateVoiceAssistant(change: (VoiceAssistantSettings) -> VoiceAssistantSettings) =
    update { it.copy(voiceAssistant = change(it.voiceAssistant).copy(wakePhrase = VoiceAssistantSettings.FIXED_WAKE_PHRASE)) }
