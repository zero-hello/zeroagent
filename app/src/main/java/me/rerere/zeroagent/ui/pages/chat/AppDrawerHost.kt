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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.theme.AppLayout
import kotlin.uuid.Uuid

/** One navigation-level drawer owner; pages only request that this drawer opens. */
internal class AppDrawerController(
    private val state: DrawerState,
    val isPermanent: Boolean,
    private val canOpen: () -> Boolean,
    private val scope: CoroutineScope,
) {
    fun open() {
        if (isPermanent || !canOpen()) return
        scope.launch { if (canOpen()) state.open() }
    }

    fun close() {
        if (!isPermanent && canOpen()) scope.launch { if (canOpen()) state.close() }
    }
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
    val windowWidth = currentWindowDpSize().width
    val permanent = windowWidth >= AppLayout.permanentDrawerBreakpoint
    val drawerWidth = (windowWidth * 0.88f).coerceIn(1.dp, 320.dp)
    val drawerEnabled = rememberUpdatedState(enabled && !permanent)
    val controller = remember(state, permanent, scope) {
        AppDrawerController(state, permanent, { drawerEnabled.value }, scope)
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val navigator = LocalNavController.current

    LaunchedEffect(navigationKey, enabled, permanent) {
        if (enabled && !permanent) state.close()
    }
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
            drawerWidth = drawerWidth,
            enabled = enabled,
            drawerContent = {
                ChatDrawerContent(
                    navController = navigator,
                    settings = settings,
                    currentConversationId = currentConversationId,
                    onNavigateFromDrawer = controller::close,
                )
            },
            content = { Box(Modifier.fillMaxSize()) { content() } },
        )
    }
}
