package me.rerere.zeroagent.assistant

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.Rect
import android.os.PowerManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.theme.ZeroAgentTheme
import org.koin.compose.koinInject
import kotlin.math.*

@Composable
internal fun AssistantTheme(content: @Composable () -> Unit) {
    val store: SettingsStore = koinInject()
    val settings by store.settingsFlow.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalSettings provides settings) { ZeroAgentTheme(content = content) }
}

@Composable
internal fun AssistantCapsule(controller: AssistantSessionController, onClose: () -> Unit, onSettings: () -> Unit,
                              onOpenChat: () -> Unit, onBounds: (Rect) -> Unit = {}) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = LocalSettings.current
    val reduced = settings.voiceAssistant.reducedEffects || remember(context) {
        !ValueAnimator.areAnimatorsEnabled() || context.getSystemService(ActivityManager::class.java).isLowRamDevice || context.getSystemService(PowerManager::class.java).isPowerSaveMode
    }
    var visible by remember(controller) { mutableStateOf(false) }
    var text by remember(controller) { mutableStateOf("") }
    LaunchedEffect(controller) { visible = true }
    LaunchedEffect(state.phase) { if (state.phase == CapsulePhase.CLOSED) onClose() }
    val active = state.phase in setOf(CapsulePhase.LISTENING, CapsulePhase.THINKING, CapsulePhase.SPEAKING)
    val phase = if (active && !reduced) {
        val animation = rememberInfiniteTransition(label = "za-capsule-flow")
        animation.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6500, easing = LinearEasing)), label = "flow").value
    } else 0f
    val shape = RoundedCornerShape(30.dp)
    val cardShape = RoundedCornerShape(24.dp)
    val maxCard = (LocalConfiguration.current.screenHeightDp * .40f).dp
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 620.dp),
            enter = fadeIn(tween(if (reduced) 0 else 180)) + slideInVertically(spring(dampingRatio = .85f, stiffness = Spring.StiffnessMediumLow)) { if (reduced) 0 else it / 2 }) {
            Column(Modifier.fillMaxWidth().animateContentSize(if (reduced) tween(0) else spring(dampingRatio = .9f)).onGloballyPositioned {
                val b = it.boundsInWindow(); onBounds(Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.issue != null) Surface(shape = cardShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(assistantIssueResource(state.issue!!)), color = MaterialTheme.colorScheme.onErrorContainer)
                        state.errorSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row { TextButton(onClick = onSettings) { Text(stringResource(R.string.za9_settings)) }
                            if (controller.chatId != null) TextButton(onClick = onOpenChat) { Text(stringResource(R.string.za9_open_chat)) } }
                    }
                }
                if (state.answer.isNotBlank() && (state.textInput || state.phase in setOf(CapsulePhase.TEXT, CapsulePhase.ERROR))) {
                    Surface(shape = cardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().heightIn(max = maxCard).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.za9_last_reply), style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(state.answer, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
                Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.border(1.dp, Brush.linearGradient(listOf(Color(0xFF8B9AE4).copy(alpha = .6f), Color(0xFFB2A0DA).copy(alpha = .5f))), shape)) {
                        Canvas(Modifier.matchParentSize()) {
                            if (!reduced && active) {
                                val center = Offset(size.width * (.5f + .25f * sin(phase)), size.height * .5f)
                                drawRect(Brush.radialGradient(listOf(Color(0xFF8196E1).copy(alpha = .15f), Color.Transparent), center, size.width * .65f))
                            }
                        }
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(capsulePhaseResource(state.phase)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) { Icon(HugeIcons.Settings03, stringResource(R.string.za9_settings)) }
                                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) { Icon(HugeIcons.Cancel01, stringResource(R.string.za9_end_session)) }
                            }
                            if (state.phase == CapsulePhase.LISTENING) {
                                Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                                    val level = if (state.microphoneLevel.isFinite()) state.microphoneLevel.coerceIn(0f, 1f) else 0f
                                    for (i in 0..14) {
                                        val h = 2f + level * size.height * (.4f + .5f * sin(i * 1.7f).pow(2))
                                        val x = size.width * (i + 1) / 16
                                        drawLine(Color(0xFF8C9FE4), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 4f, StrokeCap.Round)
                                    }
                                }
                                if (state.heard.isNotBlank()) Text(state.heard, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (state.phase == CapsulePhase.THINKING) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.phase == CapsulePhase.SPEAKING) {
                                if (state.playbackDuration > 0) LinearProgressIndicator(progress = { (state.playbackPosition.toFloat() / state.playbackDuration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (state.echoWarning) Text(stringResource(R.string.za9_echo_hint), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (state.phase == CapsulePhase.LISTENING) controller.finishListening() else controller.startListening() },
                                    enabled = state.phase !in setOf(CapsulePhase.STARTING, CapsulePhase.CLOSED)) {
                                    Icon(if (state.phase == CapsulePhase.LISTENING) HugeIcons.StopCircle else HugeIcons.Mic01,
                                        stringResource(if (state.phase == CapsulePhase.LISTENING) R.string.za9_finish_listening else R.string.za9_voice_mode))
                                }
                                if ((state.textInput || state.phase in setOf(CapsulePhase.TEXT, CapsulePhase.ERROR))) TextField(value = text, onValueChange = { text = it },
                                    modifier = Modifier.weight(1f), maxLines = 4, placeholder = { Text(stringResource(R.string.za9_text_hint)) },
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                                else Text(stringResource(R.string.za9_interrupt_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                if (state.phase == CapsulePhase.SPEAKING) FilledIconButton(onClick = controller::startListening,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFB34558))) {
                                    Icon(HugeIcons.StopCircle, stringResource(R.string.za9_interrupt))
                                } else if ((state.textInput || state.phase in setOf(CapsulePhase.TEXT, CapsulePhase.ERROR))) FilledTonalIconButton(
                                    enabled = text.isNotBlank() && controller.chatId != null,
                                    onClick = { if (controller.sendText(text)) text = "" }) {
                                    Icon(HugeIcons.ArrowUp01, stringResource(R.string.za9_send))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun capsulePhaseResource(phase: CapsulePhase): Int = when (phase) {
    CapsulePhase.STARTING -> R.string.za9_starting
    CapsulePhase.LISTENING -> R.string.za9_listening
    CapsulePhase.THINKING -> R.string.za9_thinking
    CapsulePhase.SPEAKING -> R.string.za9_speaking
    CapsulePhase.TEXT -> R.string.za9_text_mode
    CapsulePhase.ERROR -> R.string.za9_error
    CapsulePhase.CLOSED -> R.string.za9_closed
}

internal fun assistantIssueResource(issue: AssistantIssue): Int = when (issue) {
    AssistantIssue.DISABLED -> R.string.za9_error_disabled
    AssistantIssue.MICROPHONE_PERMISSION -> R.string.za9_error_microphone
    AssistantIssue.NOTIFICATION_PERMISSION -> R.string.za9_error_notification
    AssistantIssue.MICROPHONE_BUSY, AssistantIssue.BUSY -> R.string.za9_error_mic_busy
    AssistantIssue.STT_UNAVAILABLE -> R.string.za9_error_stt_missing
    AssistantIssue.STT_LOCAL_UNAVAILABLE -> R.string.za9_error_local_stt
    AssistantIssue.STT_LANGUAGE -> R.string.za9_error_stt_language
    AssistantIssue.STT_NETWORK, AssistantIssue.NETWORK -> R.string.za9_error_network
    AssistantIssue.NO_SPEECH -> R.string.za9_error_no_speech
    AssistantIssue.STT_FAILED -> R.string.za9_error_stt
    AssistantIssue.TTS_UNAVAILABLE -> R.string.za9_error_tts_missing
    AssistantIssue.TTS_LANGUAGE, AssistantIssue.TTS_VOICE -> R.string.za9_error_tts_language
    AssistantIssue.TTS_FAILED -> R.string.za9_error_tts
    AssistantIssue.MODEL_UNAVAILABLE -> R.string.za9_error_model_missing
    AssistantIssue.MODEL_FAILED -> R.string.za9_error_model
    AssistantIssue.APPROVAL -> R.string.za9_error_approval
    AssistantIssue.WAKE_UNTRAINED -> R.string.za9_error_wake_untrained
    AssistantIssue.WAKE_CORRUPT -> R.string.za9_error_wake_corrupt
    AssistantIssue.WAKE_PHRASE_CHANGED -> R.string.za9_error_wake_phrase
    AssistantIssue.ROLE_REQUIRED -> R.string.za9_error_role
    AssistantIssue.LOCKED -> R.string.za9_error_locked
    AssistantIssue.SERVICE_STOPPED -> R.string.za9_error_service
    AssistantIssue.MEMORY -> R.string.za9_error_memory
    AssistantIssue.AUDIO_FOCUS -> R.string.za9_error_focus
    AssistantIssue.WAKE_ENGINE -> R.string.za9_error_wake_engine
}
