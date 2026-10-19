package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R

/** Real microphone envelope samples; there is no timer-generated or random waveform. */
@Composable
fun AudioLevelWaveform(levels: List<Float>, threshold: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.wake_capture_waveform)
    val glass = LocalZeroAgentGlassState.current != null
    Canvas(modifier.fillMaxWidth().height(64.dp).zeroAgentGlass(shape = MaterialTheme.shapes.medium)
        .clip(MaterialTheme.shapes.medium)
        .background(if (glass) Color.Transparent else colors.surfaceContainerHigh)
        .semantics { contentDescription = label }) {
        val center = size.height / 2f
        val step = size.width / 48f
        levels.takeLast(48).forEachIndexed { index, raw ->
            val level = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
            val x = size.width - (levels.size.coerceAtMost(48) - index - .5f) * step
            val half = (level * size.height * .42f).coerceAtLeast(1.dp.toPx())
            drawLine(colors.primary, Offset(x, center - half), Offset(x, center + half),
                strokeWidth = (step * .55f).coerceAtMost(4.dp.toPx()), cap = StrokeCap.Round)
        }
        if (threshold.isFinite() && threshold > 0f) {
            val delta = threshold.coerceIn(0f, 1f) * size.height * .42f
            for (y in listOf(center - delta, center + delta)) drawLine(colors.outline,
                Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
    }
}
