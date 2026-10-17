package me.rerere.ai.provider

import kotlinx.serialization.Serializable

/** Explicit per-model opt-in. Audio modality alone is not evidence of duplex support. */
@Serializable
data class RealtimeVoiceSetting(
    val enabled: Boolean = false,
    val protocol: RealtimeVoiceProtocol = RealtimeVoiceProtocol.AUTO,
    val endpoint: String = "",
    val voice: String = "",
)

@Serializable
enum class RealtimeVoiceProtocol { AUTO, OPENAI, GEMINI }
