package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled

/** 共用進出：不逐影格展開版面，內容與點擊區在最終尺寸量測。 */
@Composable
fun MotionVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    label: String = "內容進出",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = rememberMotionEnabled()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (!motion) EnterTransition.None else enter ?: (
            fadeIn(tween(MotionTokens.PageFadeMillis)) +
                scaleIn(MotionTokens.spatial(), initialScale = MotionTokens.PressScale)),
        exit = if (!motion) ExitTransition.None else exit ?: (
            fadeOut(tween(MotionTokens.PageFadeMillis)) +
                scaleOut(MotionTokens.spatial(), targetScale = MotionTokens.PressScale)),
        label = label,
        content = content,
    )
}

/** 頁內狀態切換也使用合成層，關閉 AnimatedContent 的逐影格 SizeTransform。 */
@Composable
fun <S> MotionContent(
    targetState: S,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        sharedAxisTransition(forward = true, enabled = true)
    },
    label: String = "內容切換",
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val motion = rememberMotionEnabled()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (if (motion) transitionSpec() else
                (EnterTransition.None togetherWith ExitTransition.None)).using(null)
        },
        label = label,
        content = content,
    )
}

/** 串流文字僅在進入或明確模式切換時淡入，不因每個 token 重啟動畫或量測。 */
@Composable
fun Modifier.contentReveal(key: Any? = Unit): Modifier {
    val alpha = remember { Animatable(0f) }
    var entering by remember { mutableStateOf(true) }
    var previousKey by remember { mutableStateOf(key) }
    val motion = rememberMotionEnabled()
    LaunchedEffect(key, motion) {
        if (motion && (entering || previousKey != key)) {
            previousKey = key
            if (!entering) alpha.animateTo(MotionTokens.ContentChangeAlpha, tween(MotionTokens.PageFadeMillis / 3))
            entering = false
            alpha.animateTo(1f, tween(MotionTokens.PageFadeMillis))
        } else {
            entering = false
            previousKey = key
            alpha.snapTo(1f)
        }
    }
    return graphicsLayer { this.alpha = alpha.value }
}
