package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled

/** onSuccess 也會收到記憶體快取命中；匯出時直接顯示，不把未完成的動畫畫進檔案。 */
@Composable
fun Modifier.imageFade(key: Any?, ready: Boolean, export: Boolean = false): Modifier {
    val alpha = remember(key) { Animatable(0f) }
    val motion = rememberMotionEnabled()
    LaunchedEffect(key, ready, motion, export) {
        if (ready && motion && !export) alpha.animateTo(1f, tween(MotionTokens.ImageFadeMillis))
        else alpha.snapTo(if (ready || export) 1f else 0f)
    }
    return graphicsLayer { this.alpha = alpha.value }
}
