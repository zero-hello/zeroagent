package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.runtime.*
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.*
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalTTSState

/** Speech configuration has one canonical home under speech services. */
@Composable
fun rememberVoiceModeStarter(vm: ChatVM, settings: Settings): () -> Unit {
    val nav = LocalNavController.current
    val tts = LocalTTSState.current
    var active by remember { mutableStateOf<Pair<Model, ProviderSetting>?>(null) }
    active?.let { (model, provider) -> RealtimeVoicePage(model, provider, settings.assistantForModel(model)) { active = null } }
    return {
        tts.stop()
        val selected = settings.nativeVoiceSelection()
        if (selected == null) nav.navigate(Screen.SettingSpeech) else active = selected
    }
}
