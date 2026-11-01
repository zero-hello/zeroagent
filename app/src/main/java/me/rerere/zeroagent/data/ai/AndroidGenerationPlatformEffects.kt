package me.rerere.zeroagent.data.ai

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import me.rerere.zeroagent.R
import me.rerere.zeroagent.service.AgentOverlay
import me.rerere.zeroagent.service.ZeroAgentAccessibilityService
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

/** Narrow Android host seam for JVM contract tests; repositories keep their existing types. */
internal interface GenerationPlatformHost {
    val packageName: String
    fun foregroundPackage(): String?
    fun showTool(name: String, owner: Any, onPause: () -> Unit)
    fun hide(owner: Any)
    fun notifyUserSwitched()
    fun launchApp()
}

class AndroidGenerationPlatformEffects internal constructor(private val host: GenerationPlatformHost) :
    GenerationPlatformEffects {
    constructor(context: Context) : this(AndroidGenerationPlatformHost(context))

    override fun onTurnStart() {
        AgentTurnTracker.reset()
        // ChatService drains these observations into the cold-memory playbook at turn completion.
        AutomationRecorder.reset()
    }

    override fun onToolExecution(name: String, owner: Any, onPause: () -> Unit) {
        if (PhoneAgentGuidance.isPhoneTool(name)) host.showTool(name, owner, onPause)
    }

    override fun onTurnComplete(owner: Any) {
        host.hide(owner)
        // Preserve short-circuiting: plain navigation must not query accessibility or return home.
        if (AgentTurnTracker.didNavigateAway() && AgentTurnTracker.didAutomate()) {
            when (autoReturnDecision(
                navigatedAway = true,
                automated = true,
                destination = AgentTurnTracker.lastDestination(),
                foreground = host.foregroundPackage(),
                ownPackage = host.packageName,
            )) {
                AutoReturnDecision.USER_SWITCHED -> host.notifyUserSwitched()
                AutoReturnDecision.RETURN -> host.launchApp()
                AutoReturnDecision.NONE -> Unit
            }
        }
        // Plain screenshot requests retain their files; both paths forget the per-turn ledger.
        if (AgentTurnTracker.didAutomate()) ScreenshotLedger.purge() else ScreenshotLedger.clear()
    }
}

private class AndroidGenerationPlatformHost(private val context: Context) : GenerationPlatformHost {
    override val packageName: String get() = context.packageName

    override fun foregroundPackage(): String? =
        ZeroAgentAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()

    override fun showTool(name: String, owner: Any, onPause: () -> Unit) {
        AgentOverlay.show(
            context,
            text = context.zeroAgentLocalizedContext().getString(R.string.zeroagent_voice_agent_action, name),
            owner = owner,
            onPause = onPause,
        )
    }

    override fun hide(owner: Any) = AgentOverlay.hide(context, owner)

    override fun notifyUserSwitched() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "ZeroAgent: skipped auto-return because you switched apps. (Safety feature)",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        launchAutoReturn(
            launch = { context.startActivity(intent) },
            onFailure = { Log.w("GenerationHandler", "auto-return launch failed", it) },
        )
    }
}
