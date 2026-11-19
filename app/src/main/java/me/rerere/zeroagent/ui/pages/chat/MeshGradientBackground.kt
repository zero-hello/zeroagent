package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.zeroagent.ui.motion.FluidBackground
import kotlin.math.PI

/** Compatibility wrapper; the app now shares one draw-only fluid background. */
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) =
    FluidBackground(modifier, content)

internal fun phaseAtElapsed(elapsedMillis: Float, durationMillis: Int, loops: Int): Float {
    val totalMillis = durationMillis.toFloat() * loops
    return (elapsedMillis.mod(totalMillis) / durationMillis) * (2f * PI.toFloat())
}
