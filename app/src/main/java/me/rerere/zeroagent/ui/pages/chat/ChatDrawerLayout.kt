package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.motion.MotionTokens
import me.rerere.zeroagent.ui.designsystem.rememberZeroPredictiveDismiss

/** Adaptive drawer shell. AppDrawerHost is the sole state owner and call site. */
@Composable
internal fun ChatDrawerLayout(
    isBigScreen: Boolean,
    drawerState: DrawerState,
    drawerWidth: Dp,
    enabled: Boolean = true,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // A modal drawer must not reopen after rotating back from the permanent layout.
    LaunchedEffect(isBigScreen, enabled) {
        if (isBigScreen || !enabled) drawerState.snapTo(DrawerValue.Closed)
    }
    if (isBigScreen) {
        val motionEnabled = rememberMotionEnabled()
        PermanentNavigationDrawer(
            drawerContent = {
                if (motionEnabled) {
                    AnimatedVisibility(
                        visible = enabled,
                        enter = expandHorizontally(MotionTokens.spatial(), expandFrom = Alignment.Start),
                        exit = shrinkHorizontally(MotionTokens.spatial(), shrinkTowards = Alignment.Start),
                    ) { Box(Modifier.width(drawerWidth)) { drawerContent() } }
                } else if (enabled) {
                    Box(Modifier.width(drawerWidth)) { drawerContent() }
                }
            },
            content = content,
        )
    } else {
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
        val back = rememberZeroPredictiveDismiss(enabled && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = enabled,
            scrimColor = Color.Transparent,
            drawerContent = {
                // Even with no visible sheet, preserve the two distinct anchors Material
                // calculates from the width of this slot.
                Box(Modifier.width(drawerWidth).graphicsLayer {
                    translationX = -direction * drawerWidth.toPx() * .25f * back.value
                }) {
                    if (enabled || drawerState.isOpen || drawerState.isAnimationRunning) drawerContent()
                }
            },
            content = {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val width = drawerWidth.toPx().coerceAtLeast(1f)
                    val offset = drawerState.currentOffset.takeIf(Float::isFinite) ?: -width
                    val progress = if (enabled) ((offset + width) / width).coerceIn(0f, 1f) else 0f
                    val visibleProgress = progress * (1f - .25f * back.value)
                    translationX = direction * width * visibleProgress
                    scaleX = 1f - .06f * visibleProgress
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(if (direction > 0f) 0f else 1f, .5f)
                    clip = progress > 0f
                    shape = RoundedCornerShape(24.dp * visibleProgress)
                }) { content() }
            },
        )
    }
}
