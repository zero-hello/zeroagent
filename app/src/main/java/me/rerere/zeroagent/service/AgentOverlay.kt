package me.rerere.zeroagent.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import me.rerere.zeroagent.R
import me.rerere.zeroagent.utils.zeroAgentLocalizedContext

/** A touchable capsule for the active phone operation; pausing cancels the owning run first. */
object AgentOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var view: LinearLayout? = null
    private var title: TextView? = null
    private var owner: Any? = null
    private var pauseAction: (() -> Unit)? = null
    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun show(context: Context, text: String? = null, owner: Any? = null, onPause: () -> Unit = {}) {
        val app = context.applicationContext
        if (!canShow(app)) return
        handler.post {
            val localized = app.zeroAgentLocalizedContext()
            this.owner = owner; pauseAction = onPause
            val label = text ?: localized.getString(R.string.za_voice_agent_working)
            if (view != null) { title?.text = label; return@post }
            val wm = app.getSystemService(WindowManager::class.java)
            val density = app.resources.displayMetrics.density
            val pill = LinearLayout(localized).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((14*density).toInt(), (4*density).toInt(), (6*density).toInt(), (4*density).toInt())
                background = GradientDrawable().apply { cornerRadius = 100*density; setColor(0xF0273042.toInt()); setStroke((density).toInt().coerceAtLeast(1), 0xFF66738C.toInt()) }
                elevation = 6*density
            }
            val font = runCatching { androidx.core.content.res.ResourcesCompat.getFont(localized, R.font.manrope) }.getOrNull()
            title = TextView(localized).apply {
                font?.let { typeface = it }
                this.text = label; setTextColor(Color.WHITE); textSize = 12f; maxLines = 2
                maxWidth = (200*density).toInt()
            }
            pill.addView(title)
            pill.addView(Button(localized).apply {
                font?.let { typeface = it }
                this.text = localized.getString(R.string.za_voice_pause_agent); contentDescription = this.text
                isAllCaps = false; textSize = 12f; minWidth = 0; minimumWidth = 0
                setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    // The cancellation latch and chat queue are set synchronously before returning.
                    runCatching { pauseAction?.invoke() }
                    hide(app, this@AgentOverlay.owner)
                    val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                }
            })
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (48*density).toInt()
            }
            runCatching { wm.addView(pill, params); view = pill }.onFailure { title = null; pauseAction = null; this.owner = null }
        }
    }
    fun hide(context: Context, owner: Any? = null) {
        handler.post {
            if (owner != null && this.owner !== owner) return@post
            view?.let { runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeViewImmediate(it) } }
            view = null; title = null; this.owner = null; pauseAction = null
        }
    }
}
