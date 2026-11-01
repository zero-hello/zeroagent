package me.rerere.zeroagent.ui.components.view

import android.content.Context
import android.graphics.Color
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

/**
 * LiquidGlassTextField - Frosted glass text input field.
 * Features:
 * - Dynamic background frosted glass lens
 * - Real-time synchronization with soft keyboard (IME) translation and window resizing
 * - Adaptive luminance text and hint tinting
 */
class LiquidGlassTextField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val glassView = LiquidGlassView(context).apply {
        glassStyle = LiquidGlassView.GlassStyle.FROSTED
        cornerRadius = 24f * resources.displayMetrics.density
        refractionAmount = 20f
        dispersionStrength = 10f
    }

    val editText = EditText(context).apply {
        background = null
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        val padH = (16 * resources.displayMetrics.density).toInt()
        val padV = (12 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(140, 255, 255, 255))
        textSize = 15f
    }

    private var lastRootHeight = 0
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val root = rootView ?: return@OnGlobalLayoutListener
        val curHeight = root.height
        if (lastRootHeight != 0 && lastRootHeight != curHeight) {
            // IME keyboard opened or closed: force glass re-record to prevent backdrop freeze
            glassView.invalidate()
        }
        lastRootHeight = curHeight
    }

    init {
        addView(glassView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(editText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            glassView.invalidate()
        }

        glassView.onLuminanceChangeListener = { isOverLight ->
            if (isOverLight) {
                editText.setTextColor(Color.argb(230, 20, 20, 30))
                editText.setHintTextColor(Color.argb(120, 30, 30, 40))
            } else {
                editText.setTextColor(Color.WHITE)
                editText.setHintTextColor(Color.argb(140, 255, 255, 255))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
    }

    var text: String
        get() = editText.text?.toString() ?: ""
        set(value) {
            editText.setText(value)
        }

    var hint: String
        get() = editText.hint?.toString() ?: ""
        set(value) {
            editText.hint = value
        }

    fun addTextChangedListener(watcher: TextWatcher) {
        editText.addTextChangedListener(watcher)
    }

    fun setOnEditorActionListener(listener: TextView.OnEditorActionListener) {
        editText.setOnEditorActionListener(listener)
    }
}
