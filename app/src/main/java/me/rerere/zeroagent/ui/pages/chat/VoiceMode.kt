package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.findProvider
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.ui.context.LocalASRState
import me.rerere.zeroagent.ui.context.LocalTTSState
import me.rerere.zeroagent.ui.context.LocalToaster

/** Every call entry shares the native session. Dictation and read-aloud stay separate tools. */
@Composable
fun rememberVoiceModeStarter(vm: ChatVM, settings: Settings): () -> Unit {
    val model by vm.currentChatModel.collectAsStateWithLifecycle()
    val provider = model?.findProvider(settings.providers)
    val assistant = settings.getCurrentAssistant()
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val toaster = LocalToaster.current
    var open by rememberSaveable { mutableStateOf(false) }
    val missingModel = stringResource(R.string.chat_page_voice_select_model)
    val configureVoice = stringResource(R.string.za_voice_configure_model)
    if (open && model != null && provider != null) {
        RealtimeVoicePage(model!!, provider, assistant) { open = false }
    }
    return {
        when {
            model == null || provider == null -> toaster.show(missingModel)
            model?.realtimeVoice?.enabled != true -> toaster.show(configureVoice)
            else -> {
                asr.stop(); tts.stop(); vm.voiceSession.stop()
                open = true
            }
        }
    }
}
