package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Slow, tonal aurora. Only the draw phase reads its clock; no blur layers or bitmap captures. */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val elapsed = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    val motion = rememberMotionEnabled()
    LaunchedEffect(owner, motion) {
        if (!motion) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = withInfiniteAnimationFrameMillis { it } - elapsed.floatValue.toLong()
            while (isActive) {
                elapsed.floatValue = (withInfiniteAnimationFrameMillis { it } - start).toFloat()
                delay(50) // Very slow drift needs at most 20 redraws per second.
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkMode.current
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val phase = phaseAtElapsed(elapsed.floatValue, 48_000, 1)
            val radius = size.maxDimension * .72f
            drawRect(colors.background)
            drawBlob(Offset(size.width * (.2f + .16f * sin(phase)), size.height * .06f),
                radius, colors.primary, if (dark) .16f else .12f)
            drawBlob(Offset(size.width * (.92f + .12f * cos(phase)), size.height * (.28f + .08f * sin(phase))),
                radius * .85f, colors.tertiary, if (dark) .12f else .1f)
            drawBlob(Offset(size.width * .22f, size.height * (.88f + .04f * cos(phase))),
                radius * .75f, colors.secondary, if (dark) .08f else .06f)
        }
        content()
    }
}

internal fun phaseAtElapsed(elapsedMillis: Float, durationMillis: Int, loops: Int): Float {
    val totalMillis = durationMillis.toFloat() * loops
    val t = elapsedMillis.mod(totalMillis)
    return (t / durationMillis) * (2f * PI.toFloat())
}

/** 画一个柔光斑:中心有色,向外渐隐到透明。 */
private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    centerAlpha: Float = 0.75f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
