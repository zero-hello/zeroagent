package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.data.datastore.*
import me.rerere.zeroagent.ui.context.LocalASRState
import me.rerere.zeroagent.ui.context.LocalTTSState

/** Native calling has its own setup and is never gated by an ASR provider or text model. */
@Composable
fun rememberVoiceModeStarter(vm: ChatVM, settings: Settings): () -> Unit {
    val model by vm.currentChatModel.collectAsStateWithLifecycle()
    val provider = model?.let { m ->
        if (m.realtimeVoice.providerId.isNotBlank()) settings.providers.firstOrNull { it.enabled && it.id.toString() == m.realtimeVoice.providerId }
        else (m.providerOverwrite ?: m.findProvider(settings.providers))?.takeIf { it.enabled }
    }
    val assistant = settings.getCurrentAssistant()
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    var setup by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Pair<Model, ProviderSetting>?>(null) }
    if (setup) RealtimeVoiceSetupDialog(settings, model, onDismiss = { setup = false }, onStart = { voiceModel, voiceProvider ->
        val parent = settings.providers.firstOrNull { it.models.any { m -> m.id == voiceModel.id } } ?: settings.providers.firstOrNull { it.id == voiceProvider.id }
        if (parent != null) vm.updateSettings(settings.copy(providers = settings.providers.map {
            if (it.id != parent.id) it else if (it.models.any { m -> m.id == voiceModel.id }) it.editModel(voiceModel) else it.addModel(voiceModel)
        }))
        setup = false; active = voiceModel to voiceProvider
    })
    active?.let { (voiceModel, voiceProvider) ->
        RealtimeVoicePage(voiceModel, voiceProvider, assistant) { active = null }
    }
    return {
        asr.stop(); tts.stop(); vm.voiceSession.stop()
        if (model?.realtimeVoice?.enabled == true && model?.realtimeVoice?.modelId?.isNotBlank() == true && provider != null) active = model!! to provider
        else setup = true
    }
}
