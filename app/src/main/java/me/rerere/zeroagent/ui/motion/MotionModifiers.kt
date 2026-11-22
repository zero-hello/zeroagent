package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled

/** 同一 interactionSource 交給實際 clickable，保留原生點擊、焦點與讀屏語意。 */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = rememberMotionEnabled()
    val scale = animateFloatAsState(if (pressed && enabled && motion) MotionTokens.PressScale else 1f,
        if (motion) MotionTokens.spatial() else snap(), label = "按壓回彈")
    return graphicsLayer {
        scaleX = scale.value; scaleY = scale.value
    }
}

/** 狀態切換的可打斷彈簧；只讀取合成層屬性，不改 size/padding。 */
@Composable
fun Modifier.statePulse(selected: Boolean): Modifier {
    val motion = rememberMotionEnabled()
    val scale = animateFloatAsState(if (selected && motion) 1.06f else 1f,
        if (motion) MotionTokens.spatial() else snap(), label = "狀態彈簧")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
