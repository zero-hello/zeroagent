package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import me.rerere.zeroagent.ui.motion.MotionTokens

/** Adaptive drawer shell. AppDrawerHost is the sole state owner and call site. */
@Composable
internal fun ChatDrawerLayout(
    isBigScreen: Boolean,
    drawerState: DrawerState,
    enabled: Boolean = true,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // A modal drawer must not reopen after rotating back from the permanent layout.
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) drawerState.close()
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
                    ) { drawerContent() }
                } else if (enabled) {
                    drawerContent()
                }
            },
            content = content,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = enabled,
            drawerContent = {
                // Keep the sheet available until any open drawer finishes closing.
                if (enabled || drawerState.isOpen) drawerContent()
            },
            content = content,
        )
        // Keep the modal drawer ahead of back handlers registered by chat content.
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}
