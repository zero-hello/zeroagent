package me.rerere.zeroagent.assistant

import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog

import android.Manifest
import android.content.Intent
import android.os.Build
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
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.assistant.wake.*
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.ui.components.ui.AudioLevelWaveform
import me.rerere.zeroagent.ui.components.ui.Switch
import me.rerere.zeroagent.ui.theme.extendColors
import me.rerere.zeroagent.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import me.rerere.zeroagent.ui.components.ui.ZeroAgentScaffold as Scaffold
import me.rerere.zeroagent.ui.components.ui.ZeroAgentCard as Card

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
    var role by remember { mutableStateOf(AssistantEntry.isDefault(context)) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.enable(true) else vm.permissionDenied(AssistantIssue.MICROPHONE_PERMISSION) }
    val recordMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.record() else vm.permissionDenied(AssistantIssue.MICROPHONE_PERMISSION) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.enableWake(true) else vm.permissionDenied(AssistantIssue.NOTIFICATION_PERMISSION) }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { role = AssistantEntry.isDefault(context) }
    val voiceModel = settings.nativeVoiceSelection()?.first
    val busy = !state.ready || state.busy || state.recording || state.training
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { role = AssistantEntry.isDefault(context) }
            if (event == Lifecycle.Event.ON_STOP) { vm.stopRecording(); vm.stopPreview() }
        }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); vm.stopRecording(); vm.stopPreview() }
    }
    Scaffold(topBar = { TopAppBar(
        colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors,title = { Text(stringResource(R.string.zeroagent9_title)) }, navigationIcon = { BackButton() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding + PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                CardGroup {
                    item(headlineContent = { Text(stringResource(R.string.zeroagent9_enable)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_enable_hint)) },
                        trailingContent = { Switch(checked = cfg.enabled, enabled = !settings.init && state.ready && !busy,
                            onCheckedChange = { if (it) consent = true else vm.enable(false) }) })
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.issue != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(if (state.saveFailed) R.string.wake_v11_save_failed else assistantIssueResource(state.issue!!)))
                        state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (state.issue in setOf(AssistantIssue.MICROPHONE_PERMISSION, AssistantIssue.NOTIFICATION_PERMISSION)) TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.zeroagent9_app_permissions)) }
                        if (state.issue == AssistantIssue.MODEL_UNAVAILABLE) TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.SettingSpeech) }) {
                            Text(stringResource(R.string.wake_v11_speech_settings))
                        }
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::clearIssue) { Text(stringResource(R.string.zeroagent9_dismiss)) }
                    }
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.zeroagent9_system_entry)) }) {
                    item(headlineContent = { Text(stringResource(if (role) R.string.zeroagent9_role_selected else R.string.zeroagent9_role_not_selected)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_role_hint)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { if (!AssistantEntry.launchRoleSettings(context) { requestRole.launch(it) }) vm.permissionDenied(AssistantIssue.ROLE_REQUIRED) }, enabled = !busy) { Text(stringResource(R.string.zeroagent9_choose_assistant)) }
                    Button(shapes = ButtonDefaults.shapes(), onClick = { AssistantEntry.show(context) }, enabled = cfg.enabled && !busy) { Text(stringResource(R.string.zeroagent9_try_assistant)) }
                }
                Text(stringResource(R.string.zeroagent9_no_overlay), style = MaterialTheme.typography.bodySmall)
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.zeroagent9_wake_title)) }) {
                    item(headlineContent = { Text(stringResource(R.string.zeroagent9_wake_enable)) },
                        supportingContent = { Text(stringResource(wakeStatusResource(runtime.status))) },
                        trailingContent = { Switch(checked = cfg.wakeEnabled, enabled = !busy,
                            onCheckedChange = {
                                if (it && Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else vm.enableWake(it)
                            }) })
                }
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.memory12_wake_steps), style = MaterialTheme.typography.bodySmall)
                        if (voiceModel == null) TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.SettingSpeech) }) {
                            Text(stringResource(R.string.wake_v11_speech_settings))
                        }
                        runtime.issue?.let { Text(stringResource(assistantIssueResource(it)), color = MaterialTheme.colorScheme.error) }
                        if (cfg.wakeEnabled && runtime.status !in setOf(WakeStatus.LISTENING, WakeStatus.STARTING, WakeStatus.PAUSED)) TextButton(shapes = ButtonDefaults.shapes(), onClick = { vm.enableWake(true) }) { Text(stringResource(R.string.zeroagent9_resume_wake)) }
                        Text(stringResource(R.string.wake_v11_fixed_phrase), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.zeroagent9_wake_engine_hint), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.zeroagent9_sample_privacy), style = MaterialTheme.typography.bodySmall)
                        state.profile?.let { p -> Text(stringResource(R.string.zeroagent9_profile_ready, p.templates.size, p.phrase), style = MaterialTheme.typography.bodyMedium) }
                        if (state.profile?.phrase != null && state.profile?.phrase != VoiceAssistantSettings.FIXED_WAKE_PHRASE) Text(stringResource(R.string.zeroagent9_error_wake_phrase), color = MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = { confirmNew = 5 }) { Text(stringResource(R.string.zeroagent9_quick_setup)) }
                            OutlinedButton(enabled = !busy, onClick = { confirmNew = 10 }) { Text(stringResource(R.string.zeroagent9_recommended_setup)) }
                        }
                        Text(VoiceAssistantSettings.FIXED_WAKE_PHRASE, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.wake_v11_continuous_hint), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.zeroagent9_sample_count, state.count, state.target))
                        if (state.recording) {
                            val meter by vm.meter.collectAsStateWithLifecycle()
                            AudioLevelWaveform(levels = meter.waveform, threshold = meter.threshold)
                            Text(when {
                                state.countdown > 0 -> stringResource(R.string.wake_capture_countdown, state.countdown)
                                meter.waveform.isEmpty() -> stringResource(R.string.wake_capture_initializing)
                                else -> stringResource(when (state.phase) {
                                    WakeCapturePhase.CALIBRATING -> R.string.wake_v11_calibrating
                                    WakeCapturePhase.LISTENING -> R.string.wake_v11_listening
                                    WakeCapturePhase.SPEAKING -> R.string.wake_v11_speaking
                                    WakeCapturePhase.SAVING -> R.string.wake_v11_saving
                                    WakeCapturePhase.RESTING -> R.string.wake_capture_resting
                                })
                            })
                            meter.snrDb?.let { snr -> Text(stringResource(R.string.wake_capture_snr, snr),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (snr < 9f) MaterialTheme.extendColors.orange7 else MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(stringResource(R.string.wake_capture_threshold), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.acceptedSeconds != null) Text(stringResource(R.string.wake_capture_saved, state.count, state.target),
                            color = MaterialTheme.extendColors.green7, style = MaterialTheme.typography.labelLarge)
                        state.acceptedSeconds?.let { seconds -> state.acceptedSnr?.let { snr ->
                            Text(stringResource(R.string.wake_v11_accepted_quality, seconds, snr), style = MaterialTheme.typography.bodySmall)
                        } }
                        state.quality?.let { Text(stringResource(R.string.wake_v11_rejected, stringResource(sampleProblemResource(it))), color = MaterialTheme.colorScheme.error) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && state.count < state.target, onClick = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.record()
                                else recordMic.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text(stringResource(R.string.wake_v11_start_collection)) }
                            if (state.recording) TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::stopRecording) { Text(stringResource(R.string.zeroagent9_cancel_recording)) }
                            if (state.previewing) TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::stopPreview) { Text(stringResource(R.string.zeroagent9_stop_preview)) }
                        }
                        if (state.training) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.zeroagent9_training)) }
                        Button(enabled = !busy && state.count == state.target && WakeEngines.find(cfg.wakeEngine)?.supportsPersonalization == true, onClick = vm::train) { Text(stringResource(R.string.zeroagent9_train)) }
                        TextButton(enabled = !busy, onClick = { confirmRemove = -1 }) { Text(stringResource(R.string.zeroagent9_remove_all)) }
                    }
                }
            }
            items(state.count) { index ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.zeroagent9_sample_number, index + 1))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy, onClick = { vm.previewSample(index) }) { Text(stringResource(R.string.zeroagent9_preview_sample)) }
                            TextButton(enabled = !busy, onClick = { vm.record(index) }) { Text(stringResource(R.string.zeroagent9_rerecord)) }
                            TextButton(enabled = !busy, onClick = { confirmRemove = index }) { Text(stringResource(R.string.zeroagent9_remove)) }
                        }
                    }
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.wake_v11_speech_title)) }) {
                    item(headlineContent = { Text(voiceModel?.displayName?.takeIf { it.isNotBlank() } ?: voiceModel?.modelId ?: stringResource(R.string.wake_v11_voice_missing)) },
                        supportingContent = { Text(stringResource(R.string.wake_v11_shared_voice_model)) })
                }
                Button(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.SettingSpeech) }) { Text(stringResource(R.string.wake_v11_speech_settings)) }
                val follow = stringResource(R.string.zeroagent9_follow_app)
                LanguageField(stringResource(R.string.zeroagent9_wake_language), cfg.wakeLanguage, follow) { tag -> vm.update { it.copy(wakeLanguage = tag) } }
                Text(stringResource(R.string.wake_v11_language_preserves), style = MaterialTheme.typography.bodySmall)
            }
            item {
                CardGroup {
                    item(headlineContent = { Text(stringResource(R.string.zeroagent9_reduced_effects)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_compatibility)) },
                        trailingContent = { Switch(cfg.reducedEffects, onCheckedChange = { enabled -> vm.update { it.copy(reducedEffects = enabled) } }) })
                }
            }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.zeroagent9_consent_title)) },
        text = { Text(stringResource(R.string.wake_v11_consent_body)) },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = {
            consent = false
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.enable(true)
            else mic.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(stringResource(R.string.zeroagent9_accept_enable)) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { consent = false }) { Text(stringResource(R.string.cancel)) } })
    confirmRemove?.let { index -> AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text(stringResource(R.string.zeroagent9_remove_confirm)) },
        text = { Text(stringResource(R.string.zeroagent9_remove_hint)) }, confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = null; vm.remove(if (index < 0) null else index) }) { Text(stringResource(R.string.zeroagent9_remove)) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = null }) { Text(stringResource(R.string.cancel)) } }) }
    confirmNew?.let { count -> AlertDialog(onDismissRequest = { confirmNew = null }, title = { Text(stringResource(R.string.zeroagent9_new_recordings)) },
        text = { Text(stringResource(R.string.wake_v11_new_recordings_hint)) }, confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmNew = null; vm.newRecording(count) }) { Text(stringResource(R.string.zeroagent9_continue)) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmNew = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
private fun LanguageField(label: String, selected: String, follow: String, onSelect: (String) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    SelectTextField(value = if (selected.isBlank()) follow else Locale.forLanguageTag(selected).getDisplayName(locale), options = assistantLanguageTags, readOnly = true,
        onOptionSelected = onSelect, optionToString = { if (it.isBlank()) follow else Locale.forLanguageTag(it).getDisplayName(locale) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
internal fun wakeStatusResource(status: WakeStatus): Int = when (status) {
    WakeStatus.STOPPED -> R.string.zeroagent9_wake_stopped
    WakeStatus.STARTING -> R.string.zeroagent9_wake_starting
    WakeStatus.LISTENING -> R.string.zeroagent9_wake_listening
    WakeStatus.PAUSED -> R.string.zeroagent9_wake_paused
    WakeStatus.ERROR -> R.string.zeroagent9_wake_error
}
internal fun sampleProblemResource(problem: SampleProblem): Int = when (problem) {
    SampleProblem.TOO_QUIET -> R.string.wake_v11_quality_quiet
    SampleProblem.TOO_SHORT -> R.string.wake_v11_quality_short
    SampleProblem.TOO_LONG -> R.string.wake_v11_quality_long
    SampleProblem.NOISY -> R.string.wake_v11_quality_noisy
    SampleProblem.CLIPPED -> R.string.wake_v11_quality_clipped
    SampleProblem.INCONSISTENT -> R.string.wake_v11_quality_inconsistent
    SampleProblem.NO_SPEECH -> R.string.wake_v11_quality_timeout
    SampleProblem.TONAL -> R.string.wake_v11_quality_tonal
}
