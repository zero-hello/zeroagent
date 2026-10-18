package me.rerere.zeroagent.ui.components.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import me.rerere.zeroagent.data.datastore.DisplaySetting
import androidx.compose.ui.res.stringResource
import me.rerere.zeroagent.R

/** Uses the exact renderer used in chat; dragging previews locally before saving. */
@Composable
fun ZeroAgentGlassPreferences(display: DisplaySetting, onUpdate: (DisplaySetting) -> Unit) {
    var opacity by remember(display.liquidGlassOpacity) { mutableFloatStateOf(display.safeGlassOpacity()) }
    val preview = display.copy(liquidGlassOpacity = opacity)
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduced = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val phase = if (display.liquidGlassEnabled && !reduced) {
        val animation = rememberInfiniteTransition(label = "live-glass-preview")
        val value by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Reverse), label = "moving-source")
        value
    } else .5f
    val haze = rememberHazeState()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass)) },
            supportingContent = { Text(stringResource(R.string.zeroagent_glass_hint)) },
            trailingContent = { Switch(checked = display.liquidGlassEnabled, onCheckedChange = { onUpdate(display.copy(liquidGlassEnabled = it, enableBlurEffect = false)) }) })
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass_preview)) }, supportingContent = {
            Box(Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(20.dp))) {
                Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                    drawRect(Brush.linearGradient(listOf(primary, secondary)))
                    for (i in 0..8) {
                        drawRect(if (i % 2 == 0) primary else secondary,
                            topLeft = Offset(i * size.width / 8f, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width / 16f, size.height))
                    }
                    drawCircle(secondary, size.height * .45f, Offset(size.width * (.1f + .65f * phase), size.height * .5f))
                    drawCircle(primary, size.height * .6f, Offset(size.width * (.9f - .55f * phase), size.height * .4f))
                }
                Surface(Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth().zeroAgentGlass(haze, radius = 24, display = preview),
                    shape = RoundedCornerShape(24.dp),
                    color = if (display.liquidGlassEnabled) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ZeroAgent", style = MaterialTheme.typography.titleMedium)
                        Text(if (display.liquidGlassEnabled) stringResource(R.string.zeroagent_glass_opacity, (opacity * 100).toInt()) else stringResource(R.string.zeroagent_glass_off), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        })
        item(headlineContent = { Text(stringResource(R.string.zeroagent_glass_opacity, (opacity * 100).toInt())) }, supportingContent = {
            Slider(value = opacity, onValueChange = { opacity = it },
                onValueChangeFinished = { onUpdate(display.copy(liquidGlassOpacity = opacity)) },
                enabled = display.liquidGlassEnabled, valueRange = .15f.. .95f)
        })
    }
}
