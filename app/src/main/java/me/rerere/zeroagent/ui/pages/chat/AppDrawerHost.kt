package me.rerere.zeroagent.ui.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.AppLayout
import kotlin.uuid.Uuid

/** One navigation-level drawer owner; pages only request that this drawer opens. */
internal class AppDrawerController(
    val state: DrawerState,
    val isPermanent: Boolean,
    private val enabled: Boolean,
    private val scope: CoroutineScope,
) {
    fun open() { if (enabled && !isPermanent) scope.launch { state.open() } }
}

internal val LocalAppDrawer = compositionLocalOf<AppDrawerController> {
    error("AppDrawerHost is required")
}

@Composable
internal fun AppDrawerHost(
    enabled: Boolean,
    navigationKey: Any?,
    currentConversationId: Uuid?,
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val permanent = currentWindowDpSize().width >= AppLayout.permanentDrawerBreakpoint
    val controller = remember(state, permanent, enabled, scope) {
        AppDrawerController(state, permanent, enabled, scope)
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val navigator = LocalNavController.current

    LaunchedEffect(navigationKey, enabled) { state.close() }
    LaunchedEffect(state.isOpen) {
        if (state.isOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    CompositionLocalProvider(LocalAppDrawer provides controller) {
        ChatDrawerLayout(
            isBigScreen = permanent,
            drawerState = state,
            enabled = enabled,
            drawerContent = {
                ChatDrawerContent(
                    navController = navigator,
                    settings = settings,
                    currentConversationId = currentConversationId,
                )
            },
            content = { Box(Modifier.fillMaxSize()) { content() } },
        )
    }
}
