package me.rerere.zeroagent

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import me.rerere.zeroagent.data.datastore.nativeVoiceSelection
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.zeroagent.data.datastore.assistantForModel
import me.rerere.zeroagent.data.datastore.findModelById

import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import me.rerere.zeroagent.utils.base64Encode
import me.rerere.zeroagent.ui.motion.*
import androidx.navigation3.runtime.NavEntryDecorator
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import me.rerere.zeroagent.ui.theme.AppLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.serialization.Serializable
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.DEFAULT_CODEX_PROVIDER_ID
import me.rerere.zeroagent.data.datastore.DEFAULT_GEMINI_OAUTH_PROVIDER_ID
import me.rerere.zeroagent.data.db.DatabaseMigrationTracker
import me.rerere.zeroagent.data.db.MigrationState
import me.rerere.zeroagent.data.event.AppEvent
import me.rerere.zeroagent.data.event.AppEventBus
import me.rerere.zeroagent.ui.activity.SafeModeActivity
import me.rerere.zeroagent.ui.components.ui.TTSController
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.context.LocalSharedTransitionScope
import me.rerere.zeroagent.ui.context.LocalTTSState
import me.rerere.zeroagent.ui.context.LocalToaster
import me.rerere.zeroagent.ui.context.Navigator
import me.rerere.zeroagent.ui.hooks.readBooleanPreference
import me.rerere.zeroagent.ui.hooks.readStringPreference
import me.rerere.zeroagent.ui.hooks.rememberCustomTtsState
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantExtensionsPage
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.zeroagent.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.zeroagent.ui.pages.backup.BackupPage
import me.rerere.zeroagent.ui.pages.chat.ChatPage
import me.rerere.zeroagent.ui.pages.chat.AppDrawerHost
import me.rerere.zeroagent.ui.pages.debug.DebugPage
import me.rerere.zeroagent.ui.pages.developer.DeveloperPage
import me.rerere.zeroagent.ui.pages.extensions.ExtensionsPage
import me.rerere.zeroagent.ui.pages.extensions.PromptPage
import me.rerere.zeroagent.ui.pages.extensions.QuickMessagesPage
import me.rerere.zeroagent.ui.pages.extensions.skills.SkillDetailPage
import me.rerere.zeroagent.ui.pages.extensions.skills.SkillsPage
import me.rerere.zeroagent.ui.pages.gettingstarted.GETTING_STARTED_SEEN_KEY
import me.rerere.zeroagent.ui.pages.gettingstarted.GettingStartedPage
import me.rerere.zeroagent.ui.pages.gettingstarted.hasConfiguredModel
import me.rerere.zeroagent.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.zeroagent.ui.pages.favorite.FavoritePage
import me.rerere.zeroagent.ui.pages.history.HistoryPage
import me.rerere.zeroagent.ui.pages.imggen.ImageGenPage
import me.rerere.zeroagent.ui.pages.log.LogPage
import me.rerere.zeroagent.ui.pages.search.SearchPage
import me.rerere.zeroagent.ui.pages.setting.SettingAboutPage
import me.rerere.zeroagent.ui.pages.setting.SettingAccessibilityPage
import me.rerere.zeroagent.ui.pages.setting.SettingFloatingBallPage
import me.rerere.zeroagent.ui.pages.setting.SettingNotificationsPage
import me.rerere.zeroagent.ui.pages.setting.SettingPermissionsPage
import me.rerere.zeroagent.ui.pages.setting.SettingPreferencesThemePage
import me.rerere.zeroagent.ui.pages.setting.SettingPreferencesNotificationPage
import me.rerere.zeroagent.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.zeroagent.ui.pages.setting.SettingPreferencesNetworkPage
import me.rerere.zeroagent.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.zeroagent.ui.pages.setting.SettingThemePage
import me.rerere.zeroagent.ui.pages.setting.SettingFilesPage
import me.rerere.zeroagent.ui.pages.setting.SettingMcpPage
import me.rerere.zeroagent.ui.pages.setting.SettingModelPage
import me.rerere.zeroagent.ui.pages.setting.SettingPage
import me.rerere.zeroagent.ui.pages.setting.SettingProviderDetailPage
import me.rerere.zeroagent.ui.pages.setting.SettingProviderPage
import me.rerere.zeroagent.ui.pages.setting.SettingSearchDetailPage
import me.rerere.zeroagent.ui.pages.setting.SettingSearchPage
import me.rerere.zeroagent.ui.pages.setting.SettingsSearchPage
import me.rerere.zeroagent.ui.pages.setting.SettingTTSPage
import me.rerere.zeroagent.ui.pages.setting.SettingSpeechPage
// P2-32 ssh hosts page
import me.rerere.zeroagent.ui.pages.setting.SettingSshPage
import me.rerere.zeroagent.ui.pages.share.handler.ShareHandlerPage
import me.rerere.zeroagent.ui.pages.stats.StatsPage
import me.rerere.zeroagent.ui.pages.webview.WebViewPage
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import me.rerere.zeroagent.ui.theme.ZeroAgentTheme
import me.rerere.zeroagent.utils.CrashHandler
import me.rerere.zeroagent.utils.resolveInitialChatStack
import me.rerere.zeroagent.utils.validConversationId
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val FULL_WIDTH_ROUTE = "zeroagent.fullWidthRoute"
private const val SETTINGS_SCENE = "zeroagent.settings"
private const val PROVIDER_SCENE = "zeroagent.providers"

@Composable
private fun SettingsPanePlaceholder(@androidx.annotation.StringRes message: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(message),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private const val TAG = "RouteActivity"

class RouteActivity : androidx.fragment.app.FragmentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base.zeroAgentLocalizedContext())
    }

    companion object {
        const val EXTRA_OPEN_CODEX_SETTINGS = "open_codex_settings"
        const val EXTRA_OPEN_GEMINI_SETTINGS = "open_gemini_settings"
    }

    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private var navStack: MutableList<NavKey>? = null
    private val pendingIntents = ArrayDeque<Intent>()
    private var lockedBackStack: List<NavKey>? = null
    private val incomingShares = mutableStateMapOf<String, List<Screen.Chat>>()

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            ZeroAgentTheme {
                @OptIn(coil3.annotation.ExperimentalCoilApi::class)
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(OkHttpNetworkFetcherFactory(
                                callFactory = { okHttpClient },
                                cacheStrategy = { CacheControlCacheStrategy() },
                            ))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    val requireUnlock = me.rerere.zeroagent.ui.components.ui.rememberPrivacyLock()
                    val routeState = rememberSaveableStateHolder()
                    me.rerere.zeroagent.ui.components.ui.ChatPrivacyGate(requireUnlock, "application") {
                        routeState.SaveableStateProvider("routes") { AppRoutes() }
                        me.rerere.zeroagent.ui.components.ui.ZeroAgentLaunchAnimation(enabled = savedInstanceState == null)
                    }
                }
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val backStack = navStack ?: run {
            // Compose hasn't created the nav back stack yet; process once it's ready.
            pendingIntents.addLast(intent)
            return
        }
        if (intent.getBooleanExtra("open_native_voice", false)) {
            if (backStack.lastOrNull() != Screen.NativeVoice) backStack.add(Screen.NativeVoice)
            intent.removeExtra("open_native_voice")
        }
        if (intent.getBooleanExtra("open_termux_settings", false)) {
            val destination = Screen.SettingTermux
            if (backStack.lastOrNull() != destination) backStack.add(destination)
            intent.removeExtra("open_termux_settings")
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_CODEX_SETTINGS, false)) {
            val destination = Screen.SettingProviderDetail(DEFAULT_CODEX_PROVIDER_ID.toString())
            if (backStack.lastOrNull() != destination) backStack.add(destination)
            intent.removeExtra(EXTRA_OPEN_CODEX_SETTINGS)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_GEMINI_SETTINGS, false)) {
            val destination = Screen.SettingProviderDetail(DEFAULT_GEMINI_OAUTH_PROVIDER_ID.toString())
            if (backStack.lastOrNull() != destination) backStack.add(destination)
            intent.removeExtra(EXTRA_OPEN_GEMINI_SETTINGS)
        }
        val destination = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val current = backStack.lastOrNull { it is Screen.Chat } as? Screen.Chat
                val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.toUri())
                }
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val clipUris = intent.clipData?.let { clip ->
                    (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                }.orEmpty()
                val items = (streams + clipUris).filter { it.scheme == "content" }.distinct().take(20)
                if (sharedText.isNullOrBlank() && items.isEmpty()) null
                else {
                    val chat = current ?: Screen.Chat(Uuid.random().toString())
                    val share = chat.copy(
                        text = sharedText?.take(100_000)?.base64Encode(),
                        files = items.map(Uri::toString),
                        importId = Uuid.random().toString(),
                    )
                    incomingShares[chat.id] = incomingShares[chat.id].orEmpty() + share
                    if (current != null) {
                        while (backStack.lastOrNull() != chat) backStack.removeAt(backStack.lastIndex)
                        null
                    } else chat
                }
            }
            "app.zeroagent.shortcut.NEW_CHAT" -> Screen.Chat(Uuid.random().toString())
            "app.zeroagent.shortcut.TERMINAL" -> Screen.WorkspaceTerminal("android-shell")
            "app.zeroagent.shortcut.VOICE" -> Screen.NativeVoice
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> when {
                intent.getBooleanExtra("open_new_chat", false) -> Screen.Chat(Uuid.random().toString())
                intent.getBooleanExtra("open_productivity", false) -> Screen.Productivity()
                intent.getBooleanExtra("open_visual_assist", false) -> Screen.VisualAssist
                else -> validConversationId(intent.getStringExtra("conversationId"))?.let { Screen.Chat(it) }
            }
        }
        listOf("open_new_chat", "open_productivity", "open_visual_assist").forEach { intent.removeExtra(it) }
        if (destination != null && backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val motionEnabled = me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled()
        val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        val paneDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        val expandedSettings = paneDirective.maxHorizontalPartitions >= 2
        val settingsSceneStrategy = rememberListDetailSceneStrategy<NavKey>(
            directive = paneDirective,
            shouldHandleSinglePaneLayout = false,
        )
        val settingsDetailMetadata = ListDetailSceneStrategy.detailPane(sceneKey = SETTINGS_SCENE)
        val motionDecorator = remember { NavEntryDecorator<NavKey> { entry ->
            CompositionLocalProvider(LocalMotionRoute provides entry.contentKey.toString()) {
                // Keep the host viewport stable. Each destination keeps its own width while leaving.
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier.widthIn(
                            max = if (entry.metadata[FULL_WIDTH_ROUTE] == true) Dp.Infinity else AppLayout.settingsWidth,
                        ).fillMaxSize(),
                    ) { entry.Content() }
                }
            }
        } }
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.ChatGenerationUpdate -> Unit // 本 fork 使用自有的前台服务通知，不经事件总线消费
                    is AppEvent.ChatGenerationEnded -> Unit // 本 fork 使用自有的前台服务通知，不经事件总线消费
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        // Resolve once per composition (not on every recomposition) so a later mutation of the
        // Intent's extras can't flip which rememberNavBackStack() branch below gets called.
        val deepLinkConversationId = remember { validConversationId(intent?.getStringExtra("conversationId")) }
        val initialChatIds = remember {
            resolveInitialChatStack(
                deepLinkConversationId = deepLinkConversationId,
                createNewOnStart = readBooleanPreference("create_new_conversation_on_start", true),
                lastConversationId = validConversationId(readStringPreference("lastConversationId", null)),
                newId = { Uuid.random().toString() },
            )
        }

        val backStack = if (initialChatIds.size > 1) {
            rememberNavBackStack(Screen.Chat(initialChatIds[0]), Screen.Chat(initialChatIds[1]))
        } else {
            rememberNavBackStack(Screen.Chat(initialChatIds[0]))
        }
        DisposableEffect(backStack) {
            lockedBackStack?.takeIf { it.isNotEmpty() }?.let { previous ->
                backStack.clear()
                backStack.addAll(previous)
            }
            lockedBackStack = null
            navStack = backStack
            while (pendingIntents.isNotEmpty()) {
                handleIntent(pendingIntents.removeFirst())
            }
            onDispose {
                if (navStack === backStack) {
                    lockedBackStack = backStack.toList()
                    navStack = null
                }
            }
        }

        // First-run guide. The stock app opens into an empty chat with no model, no tools and
        // no permissions — the usual "installed it, now what?" dead end. If the user has not
        // seen this guide and still has no usable model, push the checklist on top of the
        // initial chat so the very first screen a new install sees explains what to do.
        // Gated on `!settings.init` so we wait for the real settings, and on a single-entry
        // Chat stack so we never hijack a share / deep-link launch.
        val gettingStartedSeen = remember { readBooleanPreference(GETTING_STARTED_SEEN_KEY, false) }
        LaunchedEffect(settings.init) {
            if (!gettingStartedSeen && !settings.init && !settings.hasConfiguredModel() &&
                backStack.size == 1 && backStack.firstOrNull() is Screen.Chat
            ) {
                backStack.add(Screen.GettingStarted)
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
            ) {
                if (!settings.init && settings.hasConfiguredModel() && backStack.lastOrNull() is Screen.Chat) {
                    me.rerere.zeroagent.ui.components.memory.ContextualMemoryWelcome()
                }
                if (!settings.init && !me.rerere.zeroagent.ui.hooks.rememberIsPlayStoreVersion()) {
                    me.rerere.zeroagent.ui.components.ui.AppUpdatePrompt(settings.displaySetting.updateCheckDisabledUntilEpochMillis)
                }
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppDrawerHost(
                        enabled = backStack.lastOrNull() is Screen.Chat ||
                            (!expandedSettings && backStack.lastOrNull() == Screen.Setting),
                        navigationKey = backStack.lastOrNull() to backStack.size,
                        currentConversationId = backStack.filterIsInstance<Screen.Chat>().lastOrNull()?.id
                            ?.let { runCatching { Uuid.parse(it) }.getOrNull() },
                        settings = settings,
                    ) {
                    NavDisplay(
                        backStack = backStack,
                        sceneStrategies = listOf(settingsSceneStrategy),
                        sharedTransitionScope = this@SharedTransitionLayout,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            motionDecorator,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        transitionSpec = { routeTransition(forward = true, enabled = motionEnabled, rightToLeft = rightToLeft) },
                        popTransitionSpec = { routeTransition(forward = false, enabled = motionEnabled, rightToLeft = rightToLeft) },
                        predictivePopTransitionSpec = {
                            routeTransition(
                                forward = false,
                                enabled = motionEnabled,
                                rightToLeft = rightToLeft,
                                predictiveBack = true,
                            )
                        },
                        entryProvider = entryProvider {
                            entry<Screen.Chat>(metadata = mapOf(FULL_WIDTH_ROUTE to true)) { key ->
                                val conversationId = Uuid.parse(key.id)
                                me.rerere.zeroagent.ui.components.ui.ChatPrivacyGate(
                                    me.rerere.zeroagent.ui.components.ui.rememberPrivacyLock(conversationId),
                                    conversationId,
                                ) {
                                    val share = incomingShares[key.id]?.firstOrNull()
                                    ChatPage(
                                        id = conversationId,
                                        text = share?.text ?: key.text.takeIf { share == null },
                                        files = (share?.files ?: key.files).map { it.toUri() },
                                        nodeId = key.nodeId?.let { Uuid.parse(it) },
                                        importId = share?.importId ?: key.importId,
                                        onImportConsumed = {
                                            if (share != null && incomingShares[key.id]?.firstOrNull() == share) {
                                                val remaining = incomingShares[key.id].orEmpty().drop(1)
                                                if (remaining.isEmpty()) incomingShares.remove(key.id)
                                                else incomingShares[key.id] = remaining
                                            }
                                        },
                                    )
                                }
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.ZeroAgentLab>(metadata = mapOf(FULL_WIDTH_ROUTE to true)) {
                                me.rerere.zeroagent.ui.pages.lab.ZeroAgentLabPage()
                            }
                            entry<Screen.SettingFun>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.SettingFunPage()
                            }
                            entry<Screen.History> {
                                HistoryPage()
                            }

                            entry<Screen.Favorite>(metadata = settingsDetailMetadata) {
                                FavoritePage()
                            }

                            entry<Screen.ModelTools> { key ->
                                val model = settings.findModelById(kotlin.uuid.Uuid.parse(key.modelId))
                                if (model == null) SettingModelPage() else AssistantLocalToolPage(settings.assistantForModel(model).id.toString())
                            }
                            entry<Screen.ModelMemory>(metadata = settingsDetailMetadata) { key ->
                                val model = runCatching { kotlin.uuid.Uuid.parse(key.modelId) }.getOrNull()
                                    ?.let(settings::findModelById)
                                val profile = model?.let(settings::assistantForModel) ?: settings.getCurrentAssistant()
                                androidx.compose.runtime.key(profile.id) { AssistantMemoryPage(profile.id.toString()) }
                            }
                            entry<Screen.ModelMcp> { key ->
                                val model = settings.findModelById(kotlin.uuid.Uuid.parse(key.modelId))
                                if (model == null) SettingModelPage() else AssistantMcpPage(settings.assistantForModel(model).id.toString())
                            }
                            entry<Screen.NativeVoice> {
                                val chosen = settings.nativeVoiceSelection()
                                if (chosen == null) SettingSpeechPage()
                                else me.rerere.zeroagent.ui.pages.chat.RealtimeVoicePage(chosen.first, chosen.second, settings.assistantForModel(chosen.first)) { backStack.removeLastOrNull() }
                            }
                            entry<Screen.Assistant> {
                                SettingModelPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                SettingModelPage()
                            }

                            entry<Screen.AssistantBasic> { key ->
                                SettingModelPage()
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                SettingModelPage()
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }

                            entry<Screen.Translator> {
                                LaunchedEffect(Unit) {
                                    backStack.removeAll { it == Screen.Translator || it == Screen.VideoGen }
                                    if (backStack.isEmpty()) backStack.add(Screen.Chat(initialChatIds.first()))
                                }
                            }

                            entry<Screen.Setting>(metadata = ListDetailSceneStrategy.listPane(
                                sceneKey = SETTINGS_SCENE,
                                detailPlaceholder = { SettingsPanePlaceholder(R.string.adaptive_settings_choose_option) },
                            )) {
                                SettingPage(showBackButton = expandedSettings)
                            }

                            entry<Screen.SettingsSearch>(metadata = settingsDetailMetadata) {
                                SettingsSearchPage()
                            }

                            entry<Screen.Backup>(metadata = settingsDetailMetadata) {
                                BackupPage()
                            }

                            entry<Screen.ImageGen> {
                                ImageGenPage()
                            }

                            entry<Screen.VideoGen> {
                                LaunchedEffect(Unit) {
                                    backStack.removeAll { it == Screen.Translator || it == Screen.VideoGen }
                                    if (backStack.isEmpty()) backStack.add(Screen.Chat(initialChatIds.first()))
                                }
                            }

                            entry<Screen.WebView>(metadata = mapOf(FULL_WIDTH_ROUTE to true)) { key ->
                                WebViewPage(key.url, key.contentId)
                            }

                            entry<Screen.SettingTheme> {
                                SettingThemePage()
                            }

                            entry<Screen.SettingPreferencesTheme>(metadata = settingsDetailMetadata) {
                                SettingPreferencesThemePage()
                            }

                            entry<Screen.SettingPreferencesNotification>(metadata = settingsDetailMetadata) {
                                SettingPreferencesNotificationPage()
                            }

                            entry<Screen.SettingPreferencesGeneral>(metadata = settingsDetailMetadata) {
                                SettingPreferencesGeneralPage()
                            }

                            entry<Screen.SettingPreferencesUI>(metadata = settingsDetailMetadata) {
                                SettingPreferencesUIPage()
                            }

                            entry<Screen.SettingPreferencesNetwork>(metadata = settingsDetailMetadata) {
                                SettingPreferencesNetworkPage()
                            }

                            entry<Screen.SettingProvider>(metadata = ListDetailSceneStrategy.listPane(
                                sceneKey = PROVIDER_SCENE,
                                detailPlaceholder = { SettingsPanePlaceholder(R.string.adaptive_settings_choose_provider) },
                            )) {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail>(
                                metadata = ListDetailSceneStrategy.detailPane(sceneKey = PROVIDER_SCENE),
                            ) { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels>(metadata = settingsDetailMetadata) {
                                SettingModelPage()
                            }

                            entry<Screen.SettingAbout>(metadata = settingsDetailMetadata) {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch>(metadata = settingsDetailMetadata) {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingTTS> {
                                SettingTTSPage()
                            }
                            entry<Screen.SettingSearchDetail>(metadata = settingsDetailMetadata) { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech>(metadata = settingsDetailMetadata) {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMcp>(metadata = settingsDetailMetadata) {
                                SettingMcpPage()
                            }

                            entry<Screen.SettingSsh>(metadata = settingsDetailMetadata) {
                                SettingSshPage()
                            }

                            entry<Screen.SettingFiles>(metadata = settingsDetailMetadata) {
                                SettingFilesPage()
                            }

                            entry<Screen.RetiredIntegration> {
                                SettingPage()
                            }

                            entry<Screen.SettingWorkflows>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.workflow.ui.WorkflowsScreen()
                            }

                            entry<Screen.WorkflowDetail> { key ->
                                me.rerere.zeroagent.workflow.ui.WorkflowDetailScreen(workflowId = key.id)
                            }

                            entry<Screen.SettingScheduledJobs>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.scheduledjobs.ScheduledJobsScreen()
                            }

                            entry<Screen.SettingBrowser>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.browser.SettingBrowserPage()
                            }

                            entry<Screen.SettingTermux>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.termux.SettingTermuxPage()
                            }

                            entry<Screen.SettingShizuku>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.shizuku.SettingShizukuPage()
                            }

                            entry<Screen.ScheduledJobDetail> { key ->
                                me.rerere.zeroagent.ui.pages.setting.scheduledjobs.ScheduledJobDetailScreen(jobId = key.id)
                            }

                            entry<Screen.SettingDoctor>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.doctor.DoctorScreen()
                            }

                            entry<Screen.GettingStarted>(metadata = settingsDetailMetadata) {
                                GettingStartedPage()
                            }

                            entry<Screen.SettingToolApprovals>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.SettingToolApprovalsPage()
                            }

                            entry<Screen.SettingAccessibility>(metadata = settingsDetailMetadata) {
                                SettingAccessibilityPage()
                            }

                            entry<Screen.SettingFloatingBall>(metadata = settingsDetailMetadata) {
                                SettingFloatingBallPage()
                            }

                            entry<Screen.SettingNotifications>(metadata = settingsDetailMetadata) {
                                SettingNotificationsPage()
                            }

                            entry<Screen.TaskCenter>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.chat.ZeroAgentTaskCenterPage()
                            }

                            entry<Screen.Productivity>(metadata = settingsDetailMetadata) { key ->
                                me.rerere.zeroagent.ui.pages.productivity.ProductivityPage(key.conversationId)
                            }
                            entry<Screen.Projects> { key ->
                                me.rerere.zeroagent.ui.pages.productivity.ProjectsPage(key.conversationId)
                            }
                            entry<Screen.ModelCompare> {
                                me.rerere.zeroagent.ui.pages.productivity.ModelComparePage()
                            }
                            entry<Screen.AutoModel> {
                                me.rerere.zeroagent.ui.pages.productivity.AutoModelPage()
                            }
                            entry<Screen.DocumentWorkbench> {
                                me.rerere.zeroagent.ui.pages.productivity.DocumentWorkbenchPage()
                            }
                            entry<Screen.ConversationLinks> { key ->
                                me.rerere.zeroagent.ui.pages.productivity.ConversationLinksPage(key.conversationId)
                            }
                            entry<Screen.VisualAssist> {
                                me.rerere.zeroagent.ui.pages.productivity.VisualAssistPage()
                            }

                            entry<Screen.SettingRoot>(metadata = settingsDetailMetadata) {
                                me.rerere.zeroagent.ui.pages.setting.SettingRootPage()
                            }

                            entry<Screen.SettingPermissions>(metadata = settingsDetailMetadata) {
                                SettingPermissionsPage()
                            }

                            entry<Screen.Developer>(metadata = settingsDetailMetadata) {
                                DeveloperPage()
                            }

                            entry<Screen.Debug> {
                                DebugPage()
                            }

                            entry<Screen.Log>(metadata = settingsDetailMetadata) {
                                LogPage()
                            }

                            entry<Screen.Extensions>(metadata = settingsDetailMetadata) {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages>(metadata = settingsDetailMetadata) {
                                QuickMessagesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.Workspaces> {
                                WorkspaceTerminalPage("android-shell")
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceTerminalPage("android-shell")
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceTerminalPage("android-shell")
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.Stats>(metadata = settingsDetailMetadata) {
                                StatsPage()
                            }

                        }
                    )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        val importId: String? = null,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object SettingFun : Screen

    @Serializable
    data object ZeroAgentLab : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data class Productivity(val conversationId: String? = null) : Screen

    @Serializable
    data class Projects(val conversationId: String? = null) : Screen

    @Serializable
    data object ModelCompare : Screen

    @Serializable
    data object AutoModel : Screen

    @Serializable
    data object DocumentWorkbench : Screen

    @Serializable
    data object VisualAssist : Screen

    @Serializable
    data class ConversationLinks(val conversationId: String? = null) : Screen

    @Serializable
    data object NativeVoice : Screen

    @kotlinx.serialization.Serializable
    data class ModelTools(val modelId: String) : Screen

    @kotlinx.serialization.Serializable
    data class ModelMemory(val modelId: String) : Screen

    @kotlinx.serialization.Serializable
    data class ModelMcp(val modelId: String) : Screen

    @kotlinx.serialization.Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    // Retained to decode navigation state saved before this screen was retired.
    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object SettingsSearch : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    // Retained to decode navigation state saved before this screen was retired.
    @Serializable
    data object VideoGen : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingPreferencesNetwork : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data object SettingTTS : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingSsh : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    @kotlinx.serialization.SerialName("me.rerere.zeroagent.Screen.SettingTelegram")
    data object RetiredIntegration : Screen

    @Serializable
    data object SettingWorkflows : Screen

    @Serializable
    data class WorkflowDetail(val id: String) : Screen

    @Serializable
    data object SettingScheduledJobs : Screen

    @Serializable
    data object SettingBrowser : Screen

    @Serializable
    data object SettingTermux : Screen

    @Serializable
    data object SettingShizuku : Screen

    @Serializable
    data class ScheduledJobDetail(val id: String) : Screen

    @Serializable
    data object SettingDoctor : Screen

    @Serializable
    data object GettingStarted : Screen

    @Serializable
    data object SettingToolApprovals : Screen

    @Serializable
    data object SettingAccessibility : Screen

    @Serializable
    data object SettingFloatingBall : Screen

    @Serializable
    data object SettingNotifications : Screen

    @Serializable
    data object SettingRoot : Screen

    @Serializable
    data object TaskCenter : Screen

    @Serializable
    data object SettingPermissions : Screen

    @Serializable
    data object Developer : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen

}
