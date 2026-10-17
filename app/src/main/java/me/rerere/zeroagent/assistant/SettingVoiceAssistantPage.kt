package me.rerere.zeroagent.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelType
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.assistant.wake.*
import me.rerere.zeroagent.data.datastore.getCurrentChatModel
import me.rerere.zeroagent.ui.components.ai.ModelSelector
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.uuid.Uuid

private data class VoiceProviderChoice(val id: String, val label: String)
private val assistantLanguageTags = listOf("", "en-US", "zh-TW", "zh-CN", "ja-JP", "ko-KR", "ru-RU", "ar-SA", "fr-FR", "de-DE", "es-ES", "pt-BR", "it-IT", "hi-IN", "th-TH", "vi-VN", "id-ID", "tr-TR", "uk-UA", "pl-PL")

@Composable
internal fun SettingVoiceAssistantPage(vm: VoiceAssistantSettingsVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val runtime by AssistantRuntime.wake.collectAsStateWithLifecycle()
    val cfg = settings.voiceAssistant
    val context = LocalContext.current
    val nav = LocalNavController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var consent by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Int?>(null) }
    var confirmNew by remember { mutableStateOf<Int?>(null) }
    var phrase by remember(cfg.wakePhrase) { mutableStateOf(cfg.wakePhrase) }
    var role by remember { mutableStateOf(AssistantEntry.isDefault(context)) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.enable(true) else vm.permissionDenied(AssistantIssue.MICROPHONE_PERMISSION) }
    val recordMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.record() else vm.permissionDenied(AssistantIssue.MICROPHONE_PERMISSION) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.enableWake(true) else vm.permissionDenied(AssistantIssue.NOTIFICATION_PERMISSION) }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { role = AssistantEntry.isDefault(context) }
    val appTag = context.assistantAppLanguage()
    val ttsSelected = settings.ttsProviders.firstOrNull { it.id.toString() == cfg.ttsProviderId }
    val systemTts = cfg.ttsProviderId.isBlank() || ttsSelected is TTSProviderSetting.SystemTTS
    val busy = state.busy || state.recording || state.training
    LaunchedEffect(systemTts, cfg.systemTtsEngine, cfg.ttsLanguage, appTag) { if (systemTts) vm.probeTts() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { role = AssistantEntry.isDefault(context); if (systemTts) vm.probeTts() }
            if (event == Lifecycle.Event.ON_STOP) { vm.stopRecording(); vm.stopPreview() }
        }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); vm.stopRecording(); vm.stopPreview() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.za9_title)) }, navigationIcon = { BackButton() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding + PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CardGroup {
                    item(headlineContent = { Text(stringResource(R.string.za9_enable)) },
                        supportingContent = { Text(stringResource(R.string.za9_enable_hint)) },
                        trailingContent = { Switch(checked = cfg.enabled, enabled = !settings.init && state.ready && !busy,
                            onCheckedChange = { if (it) consent = true else vm.enable(false) }) })
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.issue != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(assistantIssueResource(state.issue!!)))
                        state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (state.issue in setOf(AssistantIssue.MICROPHONE_PERMISSION, AssistantIssue.NOTIFICATION_PERMISSION)) TextButton(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.za9_app_permissions)) }
                        TextButton(onClick = vm::clearIssue) { Text(stringResource(R.string.za9_dismiss)) }
                    }
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.za9_system_entry)) }) {
                    item(headlineContent = { Text(stringResource(if (role) R.string.za9_role_selected else R.string.za9_role_not_selected)) },
                        supportingContent = { Text(stringResource(R.string.za9_role_hint)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AssistantEntry.roleIntent(context)?.let { requestRole.launch(it) } ?: vm.permissionDenied(AssistantIssue.ROLE_REQUIRED) }, enabled = !busy) { Text(stringResource(R.string.za9_choose_assistant)) }
                    Button(onClick = { AssistantEntry.show(context) }, enabled = cfg.enabled && !busy) { Text(stringResource(R.string.za9_try_assistant)) }
                }
                Text(stringResource(R.string.za9_no_overlay), style = MaterialTheme.typography.bodySmall)
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.za9_wake_title)) }) {
                    item(headlineContent = { Text(stringResource(R.string.za9_wake_enable)) },
                        supportingContent = { Text(stringResource(wakeStatusResource(runtime.status))) },
                        trailingContent = { Switch(checked = cfg.wakeEnabled, enabled = cfg.enabled && !busy && state.profile != null,
                            onCheckedChange = {
                                if (it && Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else vm.enableWake(it)
                            }) })
                }
                runtime.issue?.let { Text(stringResource(assistantIssueResource(it)), color = MaterialTheme.colorScheme.error) }
                if (cfg.wakeEnabled && runtime.status !in setOf(WakeStatus.LISTENING, WakeStatus.STARTING, WakeStatus.PAUSED)) TextButton(onClick = { vm.enableWake(true) }) { Text(stringResource(R.string.za9_resume_wake)) }
                OutlinedTextField(value = phrase, onValueChange = { if (it.length <= 64) phrase = it }, label = { Text(stringResource(R.string.za9_wake_phrase)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                TextButton(enabled = !busy && phrase.isNotBlank() && phrase.trim() != cfg.wakePhrase, onClick = { vm.changePhrase(phrase) }) { Text(stringResource(R.string.za9_apply_phrase)) }
                Text(stringResource(R.string.za9_wake_engine_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.za9_sample_privacy), style = MaterialTheme.typography.bodySmall)
                state.profile?.let { p -> Text(stringResource(R.string.za9_profile_ready, p.templates.size, p.phrase), style = MaterialTheme.typography.bodyMedium) }
                if (state.profile?.phrase != null && state.profile?.phrase != cfg.wakePhrase) Text(stringResource(R.string.za9_error_wake_phrase), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { confirmNew = 5 }) { Text(stringResource(R.string.za9_quick_setup)) }
                    OutlinedButton(enabled = !busy, onClick = { confirmNew = 10 }) { Text(stringResource(R.string.za9_recommended_setup)) }
                }
                Text(cfg.wakePhrase, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.za9_repeat_phrase), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.za9_sample_count, state.count, state.target))
                if (state.recording) { LinearProgressIndicator(progress = { state.level.times(5).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text(stringResource(R.string.za9_recording)) }
                state.quality?.let { Text(stringResource(sampleProblemResource(it)), color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && state.count < state.target, onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.record()
                        else recordMic.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(stringResource(R.string.za9_record_sample)) }
                    if (state.recording) TextButton(onClick = vm::stopRecording) { Text(stringResource(R.string.za9_cancel_recording)) }
                    if (state.previewing) TextButton(onClick = vm::stopPreview) { Text(stringResource(R.string.za9_stop_preview)) }
                }
                if (state.training) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.za9_training)) }
                Button(enabled = !busy && state.count == state.target && WakeEngines.find(cfg.wakeEngine)?.supportsPersonalization == true, onClick = vm::train) { Text(stringResource(R.string.za9_train)) }
                TextButton(enabled = !busy, onClick = { confirmRemove = -1 }) { Text(stringResource(R.string.za9_remove_all)) }
            }
            items(state.count) { index ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.za9_sample_number, index + 1))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(enabled = !busy, onClick = { vm.previewSample(index) }) { Text(stringResource(R.string.za9_preview_sample)) }
                            TextButton(enabled = !busy, onClick = { vm.record(index) }) { Text(stringResource(R.string.za9_rerecord)) }
                            TextButton(enabled = !busy, onClick = { confirmRemove = index }) { Text(stringResource(R.string.za9_remove)) }
                        }
                    }
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.za9_model_title)) }) {
                    item(headlineContent = { Text(stringResource(R.string.za9_model_hint)) }, supportingContent = { Text(stringResource(R.string.za9_model_shared)) })
                }
                ModelSelector(modelId = cfg.modelId.takeIf { it.isNotBlank() }?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: settings.getCurrentChatModel()?.id,
                    providers = settings.providers.map { it.copyProvider(models = it.models.filter { m -> Modality.TEXT in m.outputModalities }) }, type = ModelType.CHAT, allowClear = true,
                    onSelect = { model -> vm.update { it.copy(modelId = if (model.modelId.isBlank()) "" else model.id.toString()) } })
                Text(if (cfg.modelId.isBlank()) stringResource(R.string.za9_follow_chat_model) else stringResource(R.string.za9_separate_model), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { nav.navigate(Screen.SettingProvider) }) { Text(stringResource(R.string.za9_manage_models)) }
            }
            item {
                val follow = stringResource(R.string.za9_follow_app)
                CardGroup(title = { Text(stringResource(R.string.za9_languages)) }) { item(headlineContent = { Text(stringResource(R.string.za9_language_hint)) }) }
                LanguageField(stringResource(R.string.za9_stt_language), cfg.sttLanguage, follow) { tag -> vm.update { it.copy(sttLanguage = tag) } }
                if (systemTts || ttsSelected is TTSProviderSetting.XAI) {
                    LanguageField(stringResource(R.string.za9_tts_language), cfg.ttsLanguage, follow) { tag -> vm.update { it.copy(ttsLanguage = tag) } }
                } else Text(stringResource(R.string.za9_tts_auto_language), style = MaterialTheme.typography.bodySmall)
                LanguageField(stringResource(R.string.za9_wake_language), cfg.wakeLanguage, follow) { tag -> vm.update { it.copy(wakeLanguage = tag) } }
                Text(stringResource(R.string.za9_wake_language_hint), style = MaterialTheme.typography.bodySmall)
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.za9_stt_title)) }) { item(headlineContent = { Text(stringResource(R.string.za9_stt_hint)) }) }
                val systemLabel = stringResource(R.string.za9_system_stt)
                val choices = listOf(VoiceProviderChoice("", systemLabel)) + settings.asrProviders.map { VoiceProviderChoice(it.id.toString(), it.name) }
                SelectTextField(value = choices.firstOrNull { it.id == cfg.sttProviderId }?.label.orEmpty(), options = choices, readOnly = true,
                    onOptionSelected = { p -> vm.update { it.copy(sttProviderId = p.id) } }, optionToString = { it.label }, label = { Text(stringResource(R.string.za9_stt_provider)) }, modifier = Modifier.fillMaxWidth())
                if (cfg.sttProviderId.isBlank()) {
                    Row { Text(stringResource(R.string.za9_allow_system_cloud), modifier = Modifier.weight(1f)); Switch(cfg.systemSttNetworkAllowed, onCheckedChange = { value -> vm.update { it.copy(systemSttNetworkAllowed = value) } }) }
                    Text(stringResource(if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) R.string.za9_local_stt_available else R.string.za9_error_local_stt), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { nav.navigate(Screen.SettingSpeech) }) { Text(stringResource(R.string.za9_manage_speech)) }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.za9_tts_title)) }) { item(headlineContent = { Text(stringResource(R.string.za9_tts_hint)) }) }
                val defaultLabel = stringResource(R.string.za9_system_tts)
                val choices = listOf(VoiceProviderChoice("", defaultLabel)) + settings.ttsProviders.map { VoiceProviderChoice(it.id.toString(), it.name) }
                SelectTextField(value = choices.firstOrNull { it.id == cfg.ttsProviderId }?.label.orEmpty(), options = choices, readOnly = true,
                    onOptionSelected = { p -> vm.update { it.copy(ttsProviderId = p.id) } }, optionToString = { it.label }, label = { Text(stringResource(R.string.za9_tts_provider)) }, modifier = Modifier.fillMaxWidth())
                if (systemTts) {
                    if (state.probing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val tag = cfg.ttsTag(appTag)
                    val voices = state.tts?.voices.orEmpty().filter { Locale.forLanguageTag(it.languageTag).language == Locale.forLanguageTag(tag).language }
                    val defaultVoice = stringResource(R.string.za9_default_voice)
                    SelectTextField(value = cfg.systemTtsVoice.ifBlank { defaultVoice }, options = listOf(AssistantVoice("", tag, false)) + voices, readOnly = true,
                        onOptionSelected = { v -> vm.update { it.copy(systemTtsVoice = v.name) } }, optionToString = { v -> if (v.name.isBlank()) defaultVoice else "${v.name} · ${v.languageTag}" },
                        label = { Text(stringResource(R.string.za9_voice)) }, modifier = Modifier.fillMaxWidth())
                    val selectedVoice = voices.firstOrNull { it.name == cfg.systemTtsVoice.ifBlank { state.tts?.defaultVoice.orEmpty() } }
                    if (cfg.systemTtsVoice.isNotBlank() && voices.none { it.name == cfg.systemTtsVoice }) Text(stringResource(R.string.za9_error_tts_language), color = MaterialTheme.colorScheme.error)
                    if (selectedVoice?.network == true) Text(stringResource(R.string.za9_voice_network), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.za9_voice_metadata_hint), style = MaterialTheme.typography.bodySmall)
                    if (state.tts?.canRate == true) {
                        var rate by remember(cfg.speechRate) { mutableFloatStateOf(cfg.speechRate.coerceIn(.5f, 2f)) }
                        Text(stringResource(R.string.za9_rate, rate)); Slider(value = rate, onValueChange = { rate = it }, onValueChangeFinished = { vm.update { it.copy(speechRate = rate) } }, valueRange = .5f..2f)
                    }
                    if (state.tts?.canPitch == true) {
                        var pitch by remember(cfg.pitch) { mutableFloatStateOf(cfg.pitch.coerceIn(.6f, 1.5f)) }
                        Text(stringResource(R.string.za9_pitch, pitch)); Slider(value = pitch, onValueChange = { pitch = it }, onValueChangeFinished = { vm.update { it.copy(pitch = pitch) } }, valueRange = .6f..1.5f)
                    }
                    TextButton(onClick = {
                        val intent = Intent("com.android.settings.TTS_SETTINGS")
                        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
                    }) { Text(stringResource(R.string.za9_system_tts_settings)) }
                } else Text(stringResource(R.string.za9_cloud_tts_settings), style = MaterialTheme.typography.bodySmall)
                val preview = stringResource(R.string.za9_preview_text)
                Row {
                    Button(enabled = !busy && !state.previewing, onClick = { vm.previewVoice(preview) }) { Text(stringResource(R.string.za9_preview_voice)) }
                    if (state.previewing) TextButton(onClick = vm::stopPreview) { Text(stringResource(R.string.za9_stop_preview)) }
                }
            }
            item {
                Row { Text(stringResource(R.string.za9_reduced_effects), modifier = Modifier.weight(1f)); Switch(cfg.reducedEffects, onCheckedChange = { enabled -> vm.update { it.copy(reducedEffects = enabled) } }) }
                Text(stringResource(R.string.za9_compatibility), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.za9_consent_title)) },
        text = { Text(stringResource(R.string.za9_consent_body)) },
        confirmButton = { TextButton(onClick = {
            consent = false
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.enable(true)
            else mic.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(stringResource(R.string.za9_accept_enable)) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.cancel)) } })
    confirmRemove?.let { index -> AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text(stringResource(R.string.za9_remove_confirm)) },
        text = { Text(stringResource(R.string.za9_remove_hint)) }, confirmButton = { TextButton(onClick = { confirmRemove = null; vm.remove(if (index < 0) null else index) }) { Text(stringResource(R.string.za9_remove)) } },
        dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.cancel)) } }) }
    confirmNew?.let { count -> AlertDialog(onDismissRequest = { confirmNew = null }, title = { Text(stringResource(R.string.za9_new_recordings)) },
        text = { Text(stringResource(R.string.za9_new_recordings_hint)) }, confirmButton = { TextButton(onClick = { confirmNew = null; vm.newRecording(count) }) { Text(stringResource(R.string.za9_continue)) } },
        dismissButton = { TextButton(onClick = { confirmNew = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
private fun LanguageField(label: String, selected: String, follow: String, onSelect: (String) -> Unit) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    SelectTextField(value = if (selected.isBlank()) follow else Locale.forLanguageTag(selected).getDisplayName(locale), options = assistantLanguageTags, readOnly = true,
        onOptionSelected = onSelect, optionToString = { if (it.isBlank()) follow else Locale.forLanguageTag(it).getDisplayName(locale) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
internal fun wakeStatusResource(status: WakeStatus): Int = when (status) {
    WakeStatus.STOPPED -> R.string.za9_wake_stopped
    WakeStatus.STARTING -> R.string.za9_wake_starting
    WakeStatus.LISTENING -> R.string.za9_wake_listening
    WakeStatus.PAUSED -> R.string.za9_wake_paused
    WakeStatus.ERROR -> R.string.za9_wake_error
}
internal fun sampleProblemResource(problem: SampleProblem): Int = when (problem) {
    SampleProblem.TOO_QUIET -> R.string.za9_sample_quiet
    SampleProblem.TOO_SHORT -> R.string.za9_sample_short
    SampleProblem.TOO_LONG -> R.string.za9_sample_long
    SampleProblem.NOISY -> R.string.za9_sample_noisy
    SampleProblem.CLIPPED -> R.string.za9_sample_clipped
    SampleProblem.INCONSISTENT -> R.string.za9_sample_inconsistent
}
