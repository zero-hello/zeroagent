package me.rerere.zeroagent.ui.pages.chat

import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import me.rerere.zeroagent.ui.designsystem.*
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.*
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.realtime.*
import me.rerere.zeroagent.data.model.Assistant
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import kotlin.math.*

@Composable
fun RealtimeVoicePage(model: Model, provider: ProviderSetting, assistant: Assistant, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val client: OkHttpClient = koinInject()
    val nativeModel = model.copy(modelId = model.nativeVoiceModelId(), displayName = model.nativeVoiceModelId())
    val session = remember(model.id, model.realtimeVoice, provider.id) { RealtimeVoiceSession(context, client) }
    val state by session.state.collectAsStateWithLifecycle()
    val feedback = rememberZeroFeedback()
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var permissionPending by remember { mutableStateOf(false) }
    var outputMenu by remember { mutableStateOf(false) }
    val captionsScroll = rememberScrollState()
    var permissionDenied by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var showText by remember { mutableStateOf(false) }
    var expandedCaptions by remember { mutableStateOf(false) }
    fun leaveVoice() {
        feedback(ZeroFeedbackEvent.VoiceEnd)
        onDismiss()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionPending = false; permission = granted; permissionDenied = !granted }
    LaunchedEffect(Unit) { if (!permission) { permissionPending = true; request.launch(Manifest.permission.RECORD_AUDIO) } }
    LaunchedEffect(permission, lifecycleState) {
        if (permission && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) session.start(nativeModel, provider, listOf(
            me.rerere.zeroagent.data.ai.ZeroAgentSystemPolicy.prompt,
            model.generation.systemPrompt,
            "This is a live native-audio conversation. Respond naturally and briefly in the user's language. No phone tools are exposed in this voice session; never claim to have operated the device or searched without actual tool results."
        ).filter { it.isNotBlank() }.joinToString("\n\n"))
    }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && !permissionPending) { session.close(); onDismiss() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    LaunchedEffect(subtitles) {
        if (subtitles) snapshotFlow { captionsScroll.maxValue }.collect { captionsScroll.scrollTo(it) }
    }
    val stateLabel = stringResource(when (state.phase) {
        RealtimePhase.CONNECTING -> R.string.zeroagent_voice_connecting
        RealtimePhase.LISTENING -> R.string.zeroagent_voice_listening
        RealtimePhase.THINKING -> R.string.zeroagent_voice_thinking
        RealtimePhase.SPEAKING -> R.string.zeroagent_voice_speaking
        RealtimePhase.MUTED -> R.string.zeroagent_voice_muted
        else -> R.string.zeroagent_voice_closed
    })
    Dialog(onDismissRequest = ::leaveVoice, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(view, window) {
            val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            val wasSet = window?.attributes?.flags?.and(flag) == flag
            val viewWasSet = view.keepScreenOn
            if (window != null) window.addFlags(flag) else view.keepScreenOn = true
            onDispose {
                if (window == null) view.keepScreenOn = viewWasSet
                else if (!wasSet) window.clearFlags(flag)
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize()
                .safeDrawingPadding().imePadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ZeroAgent", style = MaterialTheme.typography.titleMedium)
                        Text(model.displayName.ifBlank { model.modelId }, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconToggleButton(checked = subtitles, onCheckedChange = { subtitles = it }) {
                        Icon(HugeIcons.Text, stringResource(R.string.zeroagent_voice_subtitles), tint = if (subtitles) MaterialTheme.colorScheme.primary else ZeroTheme.colors.textMuted)
                    }
                    Box {
                        IconButton(onClick = { outputMenu = true }) {
                            Icon(HugeIcons.VolumeHigh, stringResource(R.string.zeroagent_voice_output), tint = if (state.outputMuted) ZeroTheme.colors.textMuted else MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = outputMenu, onDismissRequest = { outputMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (state.outputMuted) R.string.zeroagent8_voice_unmute else R.string.zeroagent8_voice_mute)) },
                                onClick = { session.muteOutput(); outputMenu = false })
                            state.outputRoutes.forEach { route ->
                                DropdownMenuItem(text = { Text((if (route.id == state.selectedOutputRoute) "✓ " else "") + stringResource(voiceOutputRouteLabel(route.type))) },
                                    onClick = { session.selectOutputRoute(route.id); outputMenu = false })
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ZeroInlineBanner(stateLabel, busy = state.phase == RealtimePhase.CONNECTING || state.phase == RealtimePhase.THINKING)
                    LiveVoiceOrb(session, state, Modifier.fillMaxWidth().height(220.dp))
                    if (state.routeUnavailable) ZeroInlineBanner(stringResource(R.string.zeroagent8_voice_route_error), error = true)
                    if (!state.echoCancellation) Text(stringResource(R.string.zeroagent_voice_echo_hint), style = MaterialTheme.typography.bodySmall)
                    if (subtitles && (state.userTranscript.isNotBlank() || state.assistantTranscript.isNotBlank())) {
                        ZeroHairlineCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ZeroCapsuleButton(onClick = { expandedCaptions = !expandedCaptions }) {
                                    Text(stringResource(if (expandedCaptions) R.string.zero_v6_captions_collapse else R.string.zero_v6_captions_expand))
                                }
                                if (expandedCaptions) Column(Modifier.heightIn(max = 230.dp).verticalScroll(captionsScroll)) {
                                    if (state.userTranscript.isNotBlank()) Text(state.userTranscript, color = ZeroTheme.colors.textSecondary)
                                    if (state.assistantTranscript.isNotBlank()) Text(state.assistantTranscript)
                                } else Text(state.assistantTranscript.ifBlank { state.userTranscript }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.zeroagent_voice_voice_limit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(showText) {
                    ZeroTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 3,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (text.isNotBlank() && session.sendText(text)) text = "" }),
                        placeholder = { Text(stringResource(R.string.zeroagent_voice_text)) })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    ZeroIconButton(onClick = {
                        if (state.responding) { session.interrupt(); feedback(ZeroFeedbackEvent.VoiceInterrupt) }
                        else { session.muteInput(); feedback(ZeroFeedbackEvent.Toggle) }
                    }) {
                        Icon(HugeIcons.Mic01, stringResource(R.string.zeroagent_voice_microphone), tint = if (state.inputMuted) ZeroTheme.colors.accentOlive else ZeroTheme.colors.textPrimary)
                    }
                    ZeroIconButton(onClick = { showText = !showText }) {
                        Icon(HugeIcons.Text, stringResource(R.string.zeroagent_voice_text))
                    }
                    ZeroIconButton(onClick = ::leaveVoice) {
                        Icon(HugeIcons.Cancel01, stringResource(R.string.zero_v6_hang_up), tint = ZeroTheme.colors.destructive)
                    }
                }
            }
        }
        val error = if (permissionDenied) "permission" else state.error
        if (error != null) AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.zeroagent_voice_error_title)) },
            text = { Text(stringResource(voiceErrorResource(error))) }, confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.zeroagent_voice_ok)) } })
    }
}

internal fun voiceErrorResource(code: String): Int = when (code) {
    "permission" -> R.string.zeroagent_voice_permission
    "authentication_failed", "missing_key" -> R.string.zeroagent_voice_auth
    "unsupported_endpoint", "unsupported_protocol", "unsupported_auth", "missing_model", "unsupported_audio" -> R.string.zeroagent_voice_unsupported
    "connection_timeout" -> R.string.zeroagent_voice_timeout
    "response_timeout" -> R.string.zeroagent_voice_response_timeout
    "rate_limited" -> R.string.zeroagent_voice_quota
    "secure_endpoint_required", "invalid_endpoint" -> R.string.zeroagent_voice_secure
    "audio_error" -> R.string.zeroagent_voice_audio_error
    "audio_backpressure", "network_backpressure" -> R.string.zeroagent_voice_backpressure
    "protocol_error" -> R.string.zeroagent_voice_protocol_error
    else -> R.string.zeroagent_voice_network
}

@Composable
private fun LiveVoiceOrb(session: RealtimeVoiceSession, state: RealtimeVoiceState, modifier: Modifier) {
    val levels = session.levels.collectAsStateWithLifecycle()
    val colors = ZeroTheme.colors
    val active = state.phase in setOf(RealtimePhase.CONNECTING, RealtimePhase.LISTENING, RealtimePhase.THINKING, RealtimePhase.SPEAKING)
    val elapsed = if (active && me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled()) {
        rememberInfiniteTransition(label = "Voice ring").animateFloat(0f, (2 * PI).toFloat(),
            infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "Voice ring phase")
    } else null
    Box(modifier.drawWithCache {
        val radius = size.minDimension * .31f
        val ring = Path()
        val line = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val glow = Stroke(width = 1.dp.toPx())
        onDrawBehind {
            val angle = elapsed?.value ?: 0f
            val samples = levels.value
            val energy = when (state.phase) {
                RealtimePhase.LISTENING -> (samples.input.amplitude * 3f).coerceIn(0f, 1f)
                RealtimePhase.SPEAKING -> (samples.output.amplitude * 3f).coerceIn(0f, 1f)
                else -> 0f
            }
            val pulse = sin(angle) * .016f
            if (state.phase == RealtimePhase.LISTENING) {
                drawCircle(colors.accentOlive, radius * (1.07f + energy * .17f + pulse), alpha = .42f, style = glow)
            }
            ring.rewind()
            repeat(96) { sample ->
                val phase = sample * (2 * PI / 96).toFloat()
                val wave = sin(phase * 5f + angle * 2f) * energy * .09f
                val r = radius * (1f + pulse + wave)
                val x = center.x + cos(phase) * r
                val y = center.y + sin(phase) * r
                if (sample == 0) ring.moveTo(x, y) else ring.lineTo(x, y)
            }
            ring.close()
            drawPath(ring, if (state.phase == RealtimePhase.ERROR) colors.destructive else colors.textPrimary, style = line)
            if (state.phase == RealtimePhase.CONNECTING || state.phase == RealtimePhase.THINKING) {
                drawCircle(colors.accentOlive, 3.dp.toPx(),
                    center = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius))
            }
        }
    })
}
internal fun voiceOutputRouteLabel(type: Int): Int = when (type) {
    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> R.string.zeroagent8_voice_earpiece
    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.zeroagent8_voice_speaker
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> R.string.zeroagent8_voice_bluetooth
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.zeroagent8_voice_headset
    else -> R.string.zeroagent8_voice_other
}
