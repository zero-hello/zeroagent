package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.motion.MotionTokens

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
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = enabled,
            drawerContent = {
                // Even with no visible sheet, preserve the two distinct anchors Material
                // calculates from the width of this slot.
                Box(Modifier.width(drawerWidth)) {
                    if (enabled || drawerState.isOpen || drawerState.isAnimationRunning) drawerContent()
                }
            },
            content = content,
        )
        // Keep the modal drawer ahead of back handlers registered by chat content.
        BackHandler(enabled = enabled && (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open)) {
            scope.launch { drawerState.close() }
        }
    }
}
