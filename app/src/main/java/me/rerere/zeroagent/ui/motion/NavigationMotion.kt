package me.rerere.zeroagent.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.context.LocalSharedTransitionScope

val LocalMotionRoute = staticCompositionLocalOf<Any?> { null }
val LocalMotionContainerClaimed = staticCompositionLocalOf { false }
enum class MotionAxis { X, Y }

/** Navigation 3 的 seekable transition 由系統返回進度驅動，取消時保留目前速度。 */
fun sharedAxisTransition(forward: Boolean, enabled: Boolean, axis: MotionAxis = MotionAxis.X): ContentTransform {
    if (!enabled) return fadeIn(snap()) togetherWith fadeOut(snap())
    val sign = if (forward) 1 else -1
    val enter = if (axis == MotionAxis.X)
        slideInHorizontally(MotionTokens.spatial()) { sign * it / 16 }
    else slideInVertically(MotionTokens.spatial()) { sign * it / 16 }
    val exit = if (axis == MotionAxis.X)
        slideOutHorizontally(MotionTokens.spatial()) { -sign * it / 16 }
    else slideOutVertically(MotionTokens.spatial()) { -sign * it / 16 }
    return (enter + scaleIn(MotionTokens.spatial(), initialScale = .98f) +
        fadeIn(tween(MotionTokens.PageFadeMillis, easing = FastOutSlowInEasing))) togetherWith
        (exit + scaleOut(MotionTokens.spatial(), targetScale = .98f) + fadeOut(tween(MotionTokens.PageFadeMillis, easing = FastOutSlowInEasing)))
}

/** ScaleToBounds 避免每一影格重新量測內容；返回時使用相同卡片鍵值精準回到來源。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.motionBounds(key: Any?): Modifier {
    if (key == null || !rememberMotionEnabled()) return this
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@motionBounds.sharedBounds(
            rememberSharedContentState("container:$key"), visibility,
            boundsTransform = { _, _ -> MotionTokens.effects() },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            enter = fadeIn(tween(MotionTokens.PageFadeMillis, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(MotionTokens.PageFadeMillis, easing = FastOutSlowInEasing)),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.motionElement(key: Any): Modifier {
    if (!rememberMotionEnabled()) return this
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedContentScope.current
    return with(shared) { this@motionElement.sharedElement(
        rememberSharedContentState("element:$key"), visibility,
        boundsTransform = { _, _ -> MotionTokens.effects() },
    ) }
}
