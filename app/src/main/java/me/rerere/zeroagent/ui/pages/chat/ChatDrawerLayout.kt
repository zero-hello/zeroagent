package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Shares the chat slots while keeping the existing adaptive drawer behavior. */
@Composable
internal fun ChatDrawerLayout(
    isBigScreen: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    // A modal drawer must not reopen after rotating back from the permanent layout.
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) drawerState.close()
    }
    if (isBigScreen) {
        PermanentNavigationDrawer(drawerContent = drawerContent, content = content)
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
            content = content,
        )
        // Keep the modal drawer ahead of back handlers registered by chat content.
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}
