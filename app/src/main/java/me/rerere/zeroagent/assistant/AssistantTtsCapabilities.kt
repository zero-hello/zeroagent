package me.rerere.zeroagent.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import me.rerere.tts.provider.providers.openSystemTts
import java.util.Locale

internal data class AssistantVoice(val name: String, val languageTag: String, val network: Boolean)
internal data class AssistantTtsCapabilities(
    val engines: List<Pair<String, String>>, val voices: List<AssistantVoice>, val canRate: Boolean, val canPitch: Boolean,
    val languageSupported: Boolean, val defaultVoice: String,
)
internal suspend fun queryAssistantTts(context: Context, engineName: String, languageTag: String): AssistantTtsCapabilities {
    val tts = openSystemTts(context, engineName)
    return try {
        val locale = Locale.forLanguageTag(languageTag)
        val supported = tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        if (supported) tts.setLanguage(locale)
        AssistantTtsCapabilities(tts.engines.map { it.name to it.label },
            tts.voices.orEmpty().map { AssistantVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }.sortedBy { it.name },
            tts.setSpeechRate(1f) == TextToSpeech.SUCCESS, tts.setPitch(1f) == TextToSpeech.SUCCESS,
            supported, tts.voice?.name.orEmpty())
    } finally { tts.shutdown() }
}
