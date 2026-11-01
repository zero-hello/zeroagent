package me.rerere.zeroagent.assistant

import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog

import android.content.Intent
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
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import me.rerere.zeroagent.ui.components.nav.BackButton
import me.rerere.zeroagent.ui.components.ui.CardGroup
import me.rerere.zeroagent.ui.components.ui.SelectTextField
import me.rerere.zeroagent.ui.components.ui.Switch
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
    val cfg = settings.voiceAssistant
    val context = LocalContext.current
    val nav = LocalNavController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var role by remember { mutableStateOf(AssistantEntry.isDefault(context)) }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { role = AssistantEntry.isDefault(context) }
    val voiceModel = settings.nativeVoiceSelection()?.first
    val busy = !state.ready || state.busy

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { role = AssistantEntry.isDefault(context) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = me.rerere.zeroagent.ui.theme.CustomColors.topBarColors,
                title = { Text(stringResource(R.string.zeroagent9_title)) },
                navigationIcon = { BackButton() }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.zeroagent9_enable)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_enable_hint)) },
                        trailingContent = {
                            Switch(
                                checked = cfg.enabled,
                                enabled = !settings.init && state.ready && !busy,
                                onCheckedChange = { vm.enable(it) }
                            )
                        }
                    )
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (state.issue != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(if (state.saveFailed) R.string.wake_v11_save_failed else assistantIssueResource(state.issue!!)))
                            state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (state.issue in setOf(AssistantIssue.MICROPHONE_PERMISSION, AssistantIssue.NOTIFICATION_PERMISSION)) {
                                TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                                }) { Text(stringResource(R.string.zeroagent9_app_permissions)) }
                            }
                            if (state.issue == AssistantIssue.MODEL_UNAVAILABLE) {
                                TextButton(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.SettingSpeech) }) {
                                    Text(stringResource(R.string.wake_v11_speech_settings))
                                }
                            }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::clearIssue) {
                                Text(stringResource(R.string.zeroagent9_dismiss))
                            }
                        }
                    }
                }
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.zeroagent9_system_entry)) }) {
                    item(
                        headlineContent = { Text(stringResource(if (role) R.string.zeroagent9_role_selected else R.string.zeroagent9_role_not_selected)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_role_hint)) }
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            if (!AssistantEntry.launchRoleSettings(context) { requestRole.launch(it) }) vm.permissionDenied(AssistantIssue.ROLE_REQUIRED)
                        },
                        enabled = !busy
                    ) {
                        Text(stringResource(R.string.zeroagent9_choose_assistant))
                    }
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { AssistantEntry.show(context) },
                        enabled = cfg.enabled && !busy
                    ) {
                        Text(stringResource(R.string.zeroagent9_try_assistant))
                    }
                }
                Text(stringResource(R.string.zeroagent9_no_overlay), style = MaterialTheme.typography.bodySmall)
            }

            item {
                CardGroup(title = { Text(stringResource(R.string.wake_v11_speech_title)) }) {
                    item(
                        headlineContent = { Text(voiceModel?.displayName?.takeIf { it.isNotBlank() } ?: voiceModel?.modelId ?: stringResource(R.string.wake_v11_voice_missing)) },
                        supportingContent = { Text(stringResource(R.string.wake_v11_shared_voice_model)) }
                    )
                }
                Button(shapes = ButtonDefaults.shapes(), onClick = { nav.navigate(Screen.SettingSpeech) }) {
                    Text(stringResource(R.string.wake_v11_speech_settings))
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text(stringResource(R.string.zeroagent9_reduced_effects)) },
                        supportingContent = { Text(stringResource(R.string.zeroagent9_compatibility)) },
                        trailingContent = {
                            Switch(cfg.reducedEffects, onCheckedChange = { enabled -> vm.update { it.copy(reducedEffects = enabled) } })
                        }
                    )
                }
            }
        }
    }
}
