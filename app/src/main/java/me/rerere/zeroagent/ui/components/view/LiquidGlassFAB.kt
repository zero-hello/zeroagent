package me.rerere.zeroagent.ui.components.view

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * LiquidGlassFAB - Floating Action Button powered by LiquidGlassView.
 * Features:
 * - Gelatinous spring scale & touch indentation
 * - High-refraction edge compression ring and chromatic dispersion flare on press
 * - Overshoot spring rebound on release
 * - Automatic luminance-adaptive icon tinting
 */
class LiquidGlassFAB @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val glassView = LiquidGlassView(context).apply {
        glassStyle = LiquidGlassView.GlassStyle.TRANSPARENT
        refractionAmount = 35f
        dispersionStrength = 18f
        bevelWidth = 36f
    }

    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private var normalDispersion = 18f
    private var pressedDispersion = 36f
    private var normalRefraction = 35f
    private var pressedRefraction = 55f

    init {
        addView(glassView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val iconSize = (28 * resources.displayMetrics.density).toInt()
        val lp = LayoutParams(iconSize, iconSize, Gravity.CENTER)
        addView(iconView, lp)

        isClickable = true
        isFocusable = true

        // Luminance-based adaptive icon color
        glassView.onLuminanceChangeListener = { isOverLight ->
            val tint = if (isOverLight) Color.argb(220, 20, 20, 30) else Color.WHITE
            iconView.imageTintList = ColorStateList.valueOf(tint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glassView.cornerRadius = minOf(w, h) / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Downward compression + intense chromatic flare
                glassView.dispersionStrength = pressedDispersion
                glassView.refractionAmount = pressedRefraction
                animate().scaleX(0.90f).scaleY(0.90f).setDuration(120).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Jelly spring rebound with overshoot
                glassView.dispersionStrength = normalDispersion
                glassView.refractionAmount = normalRefraction
                animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(350)
                    .setInterpolator(OvershootInterpolator(2.2f))
                    .start()
            }
        }
        glassView.dispatchTouchEvent(event)
        return super.onTouchEvent(event)
    }

    fun setIconResource(@DrawableRes resId: Int) {
        iconView.setImageResource(resId)
    }

    fun setIconDrawable(drawable: Drawable?) {
        iconView.setImageDrawable(drawable)
    }

    fun setIconTint(color: Int) {
        iconView.imageTintList = ColorStateList.valueOf(color)
    }

    fun setGlassStyle(style: LiquidGlassView.GlassStyle) {
        glassView.glassStyle = style
    }
}
