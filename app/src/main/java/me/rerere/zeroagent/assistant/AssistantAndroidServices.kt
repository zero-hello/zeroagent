package me.rerere.zeroagent.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import me.rerere.zeroagent.data.datastore.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.*
import me.rerere.zeroagent.RouteActivity
import me.rerere.zeroagent.ui.theme.ZeroAgentTheme
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext
import java.lang.ref.WeakReference

internal object AssistantEntry {
    fun isDefault(context: Context): Boolean {
        val manager = context.getSystemService(RoleManager::class.java)
        val held = try { manager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true } catch (_: Exception) { false }
        return held || VoiceInteractionService.isActiveService(context, ComponentName(context, ZeroVoiceInteractionService::class.java))
    }
    fun launchRoleSettings(context: Context, launch: (Intent) -> Unit): Boolean {
        val candidates = buildList {
            val manager = context.getSystemService(RoleManager::class.java)
            // Requesting an already-held role immediately returns without showing any UI.
            if (!isDefault(context) && manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                try { add(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)) } catch (_: Exception) { }
            }
            add(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
            add(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            add(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            try { launch(intent); return true
            } catch (_: android.content.ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
        return false
    }
    fun openSettings(context: Context) {
        context.startActivity(Intent(context, RouteActivity::class.java).putExtra("open_voice_assistant_settings", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun show(context: Context) {
        if (isDefault(context) && ZeroVoiceInteractionService.invoke()) return
        context.startActivity(Intent(context, AssistantPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun openChat(context: Context, id: kotlin.uuid.Uuid?) {
        if (id == null) { openSettings(context); return }
        context.startActivity(Intent(context, RouteActivity::class.java).putExtra("conversationId", id.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

class ZeroVoiceInteractionService : VoiceInteractionService(), KoinComponent {
    private val store: SettingsStore by inject()
    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    companion object {
        private var active = WeakReference<ZeroVoiceInteractionService>(null)
        fun invoke(): Boolean {
            val service = active.get() ?: return false
            if (!AssistantEntry.isDefault(service)) return false
            return try {
                if (!ZeroVoiceSession.listenVisible()) service.showSession(Bundle(), 0)
                true
            } catch (_: Exception) {
                AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.SERVICE_STOPPED)
                false
            }
        }
    }
    override fun onReady() {
        super.onReady(); active = WeakReference(this)
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        restoreScope.launch {
            val config = store.settingsFlow.first { !it.init }.voiceAssistant
            if (!config.enabled || !config.wakeEnabled || !AssistantEntry.isDefault(this@ZeroVoiceInteractionService)) return@launch
            if (androidx.core.content.ContextCompat.checkSelfPermission(this@ZeroVoiceInteractionService,
                    android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.MICROPHONE_PERMISSION)
                return@launch
            }
            if (!androidx.core.app.NotificationManagerCompat.from(this@ZeroVoiceInteractionService).areNotificationsEnabled()) {
                AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.NOTIFICATION_PERMISSION)
                return@launch
            }
            // The system-bound voice interaction service is an allowed microphone-FGS entry.
            try {
                androidx.core.content.ContextCompat.startForegroundService(this@ZeroVoiceInteractionService,
                    Intent(this@ZeroVoiceInteractionService, AssistantWakeService::class.java))
            } catch (_: Exception) { AssistantRuntime.update(WakeStatus.ERROR, AssistantIssue.SERVICE_STOPPED) }
        }
    }
    override fun onShutdown() {
        active.clear(); restoreScope.cancel()
        stopService(Intent(this, AssistantWakeService::class.java))
        super.onShutdown()
    }
    override fun onDestroy() {
        active.clear(); restoreScope.cancel()
        super.onDestroy()
    }
}

class ZeroVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = ZeroVoiceSession(this)
}

private class SessionViewOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    private val state = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = state.savedStateRegistry
    init { state.performAttach(); state.performRestore(null); registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
    fun show() { registry.currentState = Lifecycle.State.RESUMED }
    fun hide() { registry.currentState = Lifecycle.State.CREATED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
}

private class ZeroVoiceSession(private val app: Context) : VoiceInteractionSession(app.zeroAgentLocalizedContext()) {
    companion object {
        private var visible = WeakReference<ZeroVoiceSession>(null)
        fun listenVisible(): Boolean {
            val session = visible.get() ?: return false
            val phase = session.controller?.state?.value?.phase
            if (phase !in setOf(CapsulePhase.STARTING, CapsulePhase.LISTENING)) session.controller?.startListening()
            return true
        }
    }
    private val owner = SessionViewOwner()
    private var controller: AssistantSessionController? = null
    private var compose: ComposeView? = null
    private var touch = Rect()
    private var preserveChat = false
    override fun onCreate() {
        super.onCreate()
        setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
        window?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
    override fun onCreateContentView(): View = ComposeView(context.zeroAgentLocalizedContext()).also { view ->
        compose = view
        view.setViewTreeLifecycleOwner(owner); view.setViewTreeViewModelStoreOwner(owner); view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        render()
    }
    private fun render() {
        val localized = context.zeroAgentLocalizedContext()
        compose?.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                AssistantTheme {
                    controller?.let { current -> AssistantCapsule(current,
                        onClose = { current.close(); finish() },
                        onSettings = { preserveChat = false; current.close(); finish(); AssistantEntry.openSettings(app) },
                        onOpenChat = { preserveChat = true; current.close(true); finish(); AssistantEntry.openChat(app, current.chatId) },
                        onBounds = { rect -> if (touch != rect) { touch = rect; compose?.requestLayout() } })
                    }
                }
            }
        }
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (app.getSystemService(KeyguardManager::class.java).isDeviceLocked) { finish(); return }
        preserveChat = false; visible = WeakReference(this); owner.show()
        window?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        val current = controller
        if (current == null || current.state.value.phase == CapsulePhase.CLOSED) {
            controller = AssistantSessionController(app); render(); controller!!.start()
        } else if (current.state.value.phase != CapsulePhase.STARTING) current.startListening()
    }
    override fun onComputeInsets(outInsets: Insets) {
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(Region(touch))
    }
    override fun onHide() { visible.clear(); controller?.close(preserveChat); owner.hide(); super.onHide() }
    override fun onLockscreenShown() { controller?.close(); finish(); super.onLockscreenShown() }
    override fun onDestroy() {
        visible.clear(); controller?.close(preserveChat); owner.destroy(); compose?.disposeComposition(); super.onDestroy()
    }
}

private class AssistantPanelModel(val controller: AssistantSessionController) : ViewModel() {
    var started = false
    override fun onCleared() { controller.close() }
}

class AssistantPanelActivity : ComponentActivity() {
    private lateinit var model: AssistantPanelModel
    override fun attachBaseContext(base: Context) { super.attachBaseContext(base.zeroAgentLocalizedContext()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        model = ViewModelProvider(this, viewModelFactory { initializer { AssistantPanelModel(AssistantSessionController(applicationContext)) } })[AssistantPanelModel::class.java]
        setContent { AssistantTheme { AssistantCapsule(model.controller,
            onClose = { model.controller.close(); finish() },
            onSettings = { model.controller.close(); finish(); AssistantEntry.openSettings(this) },
            onOpenChat = { model.controller.close(true); finish(); AssistantEntry.openChat(this, model.controller.chatId) }) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { if (!model.started) { model.started = true; model.controller.start() } }
        }
    }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) { model.controller.close(); finish() } }
}

/** The system's voice-service metadata links here for user-controlled configuration. */
class AssistantSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); AssistantEntry.openSettings(this); finish() }
}
