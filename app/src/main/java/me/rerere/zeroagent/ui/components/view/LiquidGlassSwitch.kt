package me.rerere.zeroagent.ui.components.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * LiquidGlassSwitch - iOS 26 dynamic fluid switch.
 * Features:
 * - Fluid squash-and-stretch spring physics during state transitions
 * - Capsule track with frosted glass refraction
 * - Liquid lens thumb with sensor-driven specular glisten
 */
class LiquidGlassSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                targetFraction = if (value) 1f else 0f
                startSpring()
                onCheckedChangeListener?.invoke(value)
            }
        }

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    // Proportions (iOS standard 51x31dp)
    private val density = resources.displayMetrics.density
    private val defaultWidth = 51f * density
    private val defaultHeight = 31f * density
    private val thumbPadding = 2f * density

    // Paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(60, 255, 255, 255)
    }

    // Liquid Glass thumb
    val thumbGlass = LiquidGlassView(context).apply {
        glassStyle = LiquidGlassView.GlassStyle.TRANSPARENT
        refractionAmount = 25f
        dispersionStrength = 14f
    }

    // Spring animation
    private var currentFraction = 0f
    private var targetFraction = 0f
    private var velocity = 0f
    private var lastTimeNanos = 0L
    private var isSpringRunning = false

    private val springStiffness = 320f
    private val springDampingRatio = 0.65f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(defaultWidth.toInt(), widthMeasureSpec)
        val h = resolveSize(defaultHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val thumbDiameter = (h - 2 * thumbPadding).toInt()
        thumbGlass.cornerRadius = thumbDiameter / 2f
        thumbGlass.measure(
            MeasureSpec.makeMeasureSpec(thumbDiameter, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(thumbDiameter, MeasureSpec.EXACTLY)
        )
        thumbGlass.layout(0, 0, thumbDiameter, thumbDiameter)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            isChecked = !isChecked
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun startSpring() {
        if (!isSpringRunning) {
            isSpringRunning = true
            lastTimeNanos = SystemClock.elapsedRealtimeNanos()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isSpringRunning) return

        val dt = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
        lastTimeNanos = frameTimeNanos

        val omega0 = sqrt(springStiffness)
        val c = 2.0f * springDampingRatio * omega0
        val springForce = -springStiffness * (currentFraction - targetFraction)
        val dampingForce = -c * velocity
        val acceleration = springForce + dampingForce

        velocity += acceleration * dt
        currentFraction += velocity * dt

        invalidate()

        if (abs(currentFraction - targetFraction) < 0.001f && abs(velocity) < 0.001f) {
            currentFraction = targetFraction
            velocity = 0f
            isSpringRunning = false
            invalidate()
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = h / 2f

        // Interpolate track color: Unchecked neutral frosted <-> Checked iOS vibrant green
        val r = (0.20f * (1f - currentFraction) + 0.20f * currentFraction)
        val g = (0.22f * (1f - currentFraction) + 0.78f * currentFraction)
        val b = (0.28f * (1f - currentFraction) + 0.35f * currentFraction)
        val a = (0.35f * (1f - currentFraction) + 0.85f * currentFraction)
        trackPaint.color = Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())

        // Draw track
        val trackRect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, corner, corner, trackPaint)
        canvas.drawRoundRect(trackRect, corner, corner, borderPaint)

        // Squash and stretch thumb dynamics
        val thumbDiameter = h - 2 * thumbPadding
        val minX = thumbPadding
        val maxX = w - thumbPadding - thumbDiameter
        val thumbBaseX = minX + currentFraction * (maxX - minX)

        // Stretch along moving direction proportional to velocity
        val stretchFactor = (abs(velocity) * 0.15f).coerceIn(0f, 0.35f)
        val stretchedWidth = thumbDiameter * (1f + stretchFactor)

        canvas.save()
        canvas.translate(thumbBaseX - (stretchedWidth - thumbDiameter) / 2f, thumbPadding)
        canvas.scale(stretchedWidth / thumbDiameter, 1f)
        thumbGlass.draw(canvas)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isSpringRunning) {
            Choreographer.getInstance().removeFrameCallback(this)
            isSpringRunning = false
        }
    }
}
