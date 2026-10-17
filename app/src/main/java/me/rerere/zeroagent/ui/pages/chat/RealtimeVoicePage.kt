package me.rerere.zeroagent.ui.pages.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import me.rerere.zeroagent.data.ai.AgentIdentityPrompt
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
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var permissionPending by remember { mutableStateOf(false) }
    var outputMenu by remember { mutableStateOf(false) }
    val captionsScroll = rememberScrollState()
    var permissionDenied by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionPending = false; permission = granted; permissionDenied = !granted }
    LaunchedEffect(Unit) { if (!permission) { permissionPending = true; request.launch(Manifest.permission.RECORD_AUDIO) } }
    LaunchedEffect(permission, lifecycleState) {
        if (permission && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) session.start(nativeModel, provider, listOf(
            assistant.systemPrompt,
            AgentIdentityPrompt.build(assistant, nativeModel),
            "This is a live native-audio conversation. Respond naturally and briefly in the user's language. No phone tools are exposed in this voice session; never claim to have operated the device or searched without actual tool results."
        ).filter { it.isNotBlank() }.joinToString("\n\n"))
    }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && !permissionPending) { session.close(); onDismiss() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    LaunchedEffect(subtitles, state.userTranscript, state.assistantTranscript) {
        if (subtitles) { withFrameNanos { }; captionsScroll.animateScrollTo(captionsScroll.maxValue) }
    }
    val stateLabel = stringResource(when (state.phase) {
        RealtimePhase.CONNECTING -> R.string.za_voice_connecting
        RealtimePhase.LISTENING -> R.string.za_voice_listening
        RealtimePhase.THINKING -> R.string.za_voice_thinking
        RealtimePhase.SPEAKING -> R.string.za_voice_speaking
        RealtimePhase.MUTED -> R.string.za_voice_muted
        else -> R.string.za_voice_closed
    })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B101B), contentColor = Color(0xFFE8EDF8)) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A2D), Color(0xFF0B101B), Color(0xFF141829))))
                .safeDrawingPadding().imePadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ZeroAgent", style = MaterialTheme.typography.titleMedium)
                        Text(model.displayName.ifBlank { model.modelId }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    IconToggleButton(checked = subtitles, onCheckedChange = { subtitles = it }) {
                        Icon(HugeIcons.Text, stringResource(R.string.za_voice_subtitles), tint = if (subtitles) Color(0xFFC6D6FA) else Color.Gray)
                    }
                    Box {
                        IconButton(onClick = { outputMenu = true }) {
                            Icon(HugeIcons.VolumeHigh, stringResource(R.string.za_voice_output), tint = if (state.outputMuted) Color.Gray else Color(0xFFC6D6FA))
                        }
                        DropdownMenu(expanded = outputMenu, onDismissRequest = { outputMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (state.outputMuted) R.string.za8_voice_unmute else R.string.za8_voice_mute)) },
                                onClick = { session.muteOutput(); outputMenu = false })
                            state.outputRoutes.forEach { route ->
                                DropdownMenuItem(text = { Text((if (route.id == state.selectedOutputRoute) "✓ " else "") + stringResource(voiceOutputRouteLabel(route.type))) },
                                    onClick = { session.selectOutputRoute(route.id); outputMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(.3f))
                Text(stateLabel, style = MaterialTheme.typography.titleMedium)
                VoiceOrb(state, Modifier.fillMaxWidth().heightIn(max = 310.dp).weight(1f, fill = false))
                if (state.routeUnavailable) Text(stringResource(R.string.za8_voice_route_error), color = Color(0xFFFFC2C7))
                if (!state.echoCancellation) Text(stringResource(R.string.za_voice_echo_hint), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.fillMaxWidth().weight(.7f).padding(vertical = 12.dp)) {
                    if (subtitles) Column(Modifier.fillMaxSize().verticalScroll(captionsScroll).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.userTranscript.isNotBlank()) Text(state.userTranscript, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB8C4D9))
                        if (state.assistantTranscript.isNotBlank()) Text(state.assistantTranscript, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(stringResource(R.string.za_voice_voice_limit), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AAAC5))
                Surface(Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF222C40)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconToggleButton(checked = !state.inputMuted, onCheckedChange = { session.muteInput() }) {
                            Icon(HugeIcons.Mic01, stringResource(R.string.za_voice_microphone), tint = if (state.inputMuted) Color.Gray else Color(0xFFC6D6FA))
                        }
                        TextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), maxLines = 3,
                            placeholder = { Text(stringResource(R.string.za_voice_text), style = MaterialTheme.typography.bodyMedium) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        FilledIconButton(enabled = text.isBlank() || state.responding || state.phase == RealtimePhase.LISTENING || state.phase == RealtimePhase.MUTED,
                            onClick = {
                            when {
                                state.responding -> session.interrupt()
                                text.isNotBlank() -> { session.sendText(text); text = "" }
                                else -> onDismiss()
                            }
                        }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFBA414F))) {
                            Icon(when {
                                state.responding -> HugeIcons.Pause
                                text.isNotBlank() -> HugeIcons.ArrowUp01
                                else -> HugeIcons.Cancel01
                            }, stringResource(when {
                                state.responding -> R.string.za_voice_stop
                                text.isNotBlank() -> R.string.za_voice_send
                                else -> R.string.za_voice_end
                            }))
                        }
                    }
                }

            }
        }
        val error = if (permissionDenied) "permission" else state.error
        if (error != null) AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.za_voice_error_title)) },
            text = { Text(stringResource(voiceErrorResource(error))) }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.za_voice_ok)) } })
    }
}

internal fun voiceErrorResource(code: String): Int = when (code) {
    "permission" -> R.string.za_voice_permission
    "authentication_failed", "missing_key" -> R.string.za_voice_auth
    "unsupported_endpoint", "unsupported_protocol", "unsupported_auth", "missing_model", "unsupported_audio" -> R.string.za_voice_unsupported
    "connection_timeout" -> R.string.za_voice_timeout
    "response_timeout" -> R.string.za_voice_response_timeout
    "rate_limited" -> R.string.za_voice_quota
    "secure_endpoint_required", "invalid_endpoint" -> R.string.za_voice_secure
    "audio_error" -> R.string.za_voice_audio_error
    "audio_backpressure", "network_backpressure" -> R.string.za_voice_backpressure
    "protocol_error" -> R.string.za_voice_protocol_error
    else -> R.string.za_voice_network
}

@Composable
private fun VoiceOrb(state: RealtimeVoiceState, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val time by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "flow")
    val level by animateFloatAsState(max(state.input.amplitude, state.output.amplitude).times(3f).coerceIn(0f, 1f), tween(90), label = "voice-energy")
    val brightness = max(state.input.brightness, state.output.brightness)
    val shader = if (Build.VERSION.SDK_INT >= 33) remember { runCatching { OrbShader.create() }.getOrNull() } else null
    Canvas(modifier) {
        val r = min(size.width, size.height) * (.29f + level * .035f)
        val c = center
        drawCircle(Brush.radialGradient(listOf(Color(0x253C70B0), Color.Transparent), c, r * 1.65f), r * 1.65f, c)
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            OrbShader.update(shader, size.width, size.height, time, level, brightness)
            drawCircle(ShaderBrush(shader), r, c)
        } else {
            val bounds = Path().apply { addOval(Rect(c - Offset(r, r), c + Offset(r, r))) }
            clipPath(bounds) {
                drawCircle(Color(0xFF273C72), r, c)
                val colors = listOf(Color(0xFF8EBBFA), Color(0xFFB9A0E8), Color(0xFF69CFC4))
                colors.forEachIndexed { i, color ->
                    val spot = c + Offset(sin(time + i * 2f), cos(time + i * 2f)) * r * .55f
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .8f + level * .15f), Color.Transparent), spot, r * 1.3f), r * 1.3f, spot)
                }
            }
        }
        // Paths provide a genuinely animated liquid surface on Android 12 as well.
        for (layer in 0..3) {
            val path = Path()
            for (step in 0..100) {
                val a = step / 100f * 2f * PI.toFloat()
                val wave = r * (1f + (.025f + level * .035f) * sin(a * (3 + layer) + time * (layer + 1)))
                val p = c + Offset(cos(a) * wave, sin(a) * wave)
                if (step == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close(); drawPath(path, Color(0xFFCEE1FF).copy(alpha = .1f + brightness * .06f), style = Stroke(1.2f + layer))
        }
        for (i in 0..23) {
            val a = i * 2.39996f + sin(time) * .5f
            val distance = r * (1.2f + (i % 7) * .065f + level * .12f)
            drawCircle(Color(0xFFCAD9F2).copy(alpha = .25f + .25f * sin(time + i).pow(2)),
                1f + i % 3, c + Offset(cos(a) * distance, sin(a) * distance))
        }
    }
}

@RequiresApi(33)
private object OrbShader {
    fun create(): android.graphics.Shader = RuntimeShader("""
        uniform float2 resolution; uniform float time; uniform float energy; uniform float brightness;
        half4 main(float2 p) {
            float2 uv = (p - resolution * .5) / min(resolution.x, resolution.y);
            float2 motion = float2(sin(time), cos(time));
            float flow = sin(uv.x * 11. + motion.x * 2. + sin(uv.y * 9. + motion.y));
            float ripple = cos(uv.y * 13. - motion.y * 2. + flow * (1.2 + energy));
            float light = clamp(.5 + .28 * flow + .16 * ripple + brightness * .12, 0., 1.);
            half3 color = mix(half3(.16, .29, .57), half3(.58, .78, .98), light);
            color = mix(color, half3(.73, .57, .88), clamp(ripple * .28 + .18, 0., .45));
            return half4(color, 1.);
        }
    """.trimIndent())
    fun update(baseShader: android.graphics.Shader, width: Float, height: Float, time: Float, energy: Float, brightness: Float) {
        val shader = baseShader as RuntimeShader
        shader.setFloatUniform("resolution", width, height); shader.setFloatUniform("time", time)
        shader.setFloatUniform("energy", energy); shader.setFloatUniform("brightness", brightness)
    }
}

internal fun voiceOutputRouteLabel(type: Int): Int = when (type) {
    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> R.string.za8_voice_earpiece
    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.za8_voice_speaker
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> R.string.za8_voice_bluetooth
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.za8_voice_headset
    else -> R.string.za8_voice_other
}
