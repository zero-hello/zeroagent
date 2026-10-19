package me.rerere.zeroagent.ui.components.ui

import android.icu.text.BreakIterator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.datastore.DisplaySetting
import me.rerere.zeroagent.ui.components.message.MessageBubbleSurface
import kotlin.math.roundToInt

/** Uses the exact message surfaces and glass renderer; dragging previews before saving. */
@Composable
fun ZeroAgentGlassPreferences(display: DisplaySetting, onUpdate: (DisplaySetting) -> Unit) {
    var opacity by remember(display.liquidGlassOpacity) { mutableFloatStateOf(display.safeGlassOpacity()) }
    val preview = display.copy(liquidGlassOpacity = opacity)
    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass)) },
            supportingContent = { Text(stringResource(R.string.zeroagent_glass_hint)) },
            trailingContent = { Switch(checked = display.liquidGlassEnabled, onCheckedChange = { onUpdate(display.copy(liquidGlassEnabled = it, enableBlurEffect = false)) }) })
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass_preview)) },
            supportingContent = { ConversationGlassPreview(preview) })
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass_opacity, (opacity * 100).toInt())) }, supportingContent = {
            Slider(value = opacity, onValueChange = { opacity = it },
                onValueChangeFinished = { onUpdate(display.copy(liquidGlassOpacity = opacity)) },
                enabled = display.liquidGlassEnabled, valueRange = .15f.. .95f)
        })
    }
}

@Composable
private fun ConversationGlassPreview(display: DisplaySetting) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val reduced = android.provider.Settings.Global.getFloat(context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val moving = display.liquidGlassEnabled && !reduced
    val motion = if (moving) rememberInfiniteTransition(label = "conversation-glass").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "scene-motion",
    ) else rememberUpdatedState(.5f)
    val prompt = stringResource(R.string.glass_preview_prompt)
    val reply = stringResource(R.string.glass_preview_reply)
    val boundaries = remember(reply, locale) {
        val iterator = BreakIterator.getCharacterInstance(locale).apply { setText(reply) }
        buildList { var end = iterator.next(); while (end != BreakIterator.DONE) { add(end); end = iterator.next() } }
    }
    var shown by remember(reply) { mutableIntStateOf(reply.length) }
    LaunchedEffect(reply, moving) {
        if (!moving) { shown = reply.length; return@LaunchedEffect }
        while (isActive) {
            shown = 0
            for (end in boundaries) { shown = end; delay(65) }
            delay(1600)
        }
    }
    val haze = rememberHazeState()
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    Box(Modifier.fillMaxWidth().height(if (largeText) 300.dp else 240.dp).clip(MaterialTheme.shapes.extraLarge)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(colors.surface, colors.primaryContainer, colors.secondaryContainer),
                    start = Offset(size.width * motion.value, 0f), end = Offset(size.width, size.height)))
            }
            Column(Modifier.fillMaxWidth().padding(12.dp).offset {
                IntOffset(0, (-motion.value * 16.dp.toPx()).roundToInt())
            }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MessageBubbleSurface(user = true, opacity = display.bubbleOpacity,
                    modifier = Modifier.align(Alignment.End).fillMaxWidth(.85f)) {
                    Text(prompt, Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                }
                MessageBubbleSurface(user = false, opacity = display.bubbleOpacity,
                    modifier = Modifier.align(Alignment.Start).fillMaxWidth(.9f)) {
                    Text(reply.take(shown).ifEmpty { "…" }, Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Surface(Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth()
            .zeroAgentGlass(haze, display = display, shape = MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (display.liquidGlassEnabled) Color.Transparent else colors.surfaceContainer,
            contentColor = colors.onSurface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ZeroAgent", style = MaterialTheme.typography.titleMedium)
                Text(if (display.liquidGlassEnabled) stringResource(R.string.zeroagent_glass_opacity,
                    (display.safeGlassOpacity() * 100).toInt()) else stringResource(R.string.zeroagent_glass_off),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
