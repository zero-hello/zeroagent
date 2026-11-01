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
 * LiquidGlassSlider - Gelatinous elastic slider with smin mercury fluid merging.
 * Features:
 * - Damped harmonic spring physics for thumb dragging lag and rebound
 * - AGSL smin fluid fusion between thumb and finger contact point, stretching a liquid filament
 * - Dynamic sensor-reactive specular highlight
 */
class LiquidGlassSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    var value: Float = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            targetThumbX = progressToX(field)
            if (!isDragging) {
                currentThumbX = targetThumbX
            }
            invalidate()
        }

    var onValueChangedListener: ((Float) -> Unit)? = null

    // Track appearance
    private val trackHeight = 12f * resources.displayMetrics.density
    private val thumbRadius = 18f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 78, 140, 255)
        style = Paint.Style.FILL
    }

    // Liquid Glass thumb
    val thumbGlass = LiquidGlassView(context).apply {
        glassStyle = LiquidGlassView.GlassStyle.TRANSPARENT
        refractionAmount = 30f
        dispersionStrength = 16f
        cornerRadius = thumbRadius
    }

    // Spring physics state
    private var currentThumbX = 0f
    private var targetThumbX = 0f
    private var thumbVelocity = 0f
    private var lastTimeNanos = 0L
    private var isSpringRunning = false
    private var isDragging = false

    private val springStiffness = 380f
    private val springDampingRatio = 0.68f

    init {
        thumbGlass.cornerRadius = thumbRadius
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = (thumbRadius * 2).toInt()
        thumbGlass.measure(
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        )
        thumbGlass.layout(0, 0, size, size)
        targetThumbX = progressToX(value)
        currentThumbX = targetThumbX
    }

    private fun progressToX(progress: Float): Float {
        val minX = thumbRadius
        val maxX = width - thumbRadius
        return minX + progress * (maxX - minX)
    }

    private fun xToProgress(x: Float): Float {
        val minX = thumbRadius
        val maxX = width - thumbRadius
        if (maxX <= minX) return 0f
        return ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                targetThumbX = event.x.coerceIn(thumbRadius, width - thumbRadius)
                startSpring()

                // Engage liquid merge between thumb center and finger
                val dx = event.x - currentThumbX
                val dy = event.y - (height / 2f)
                thumbGlass.setSecondaryShape(
                    thumbRadius + dx,
                    thumbRadius + dy,
                    thumbRadius * 0.75f,
                    blend = 45f
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                targetThumbX = event.x.coerceIn(thumbRadius, width - thumbRadius)
                val newProgress = xToProgress(targetThumbX)
                if (abs(newProgress - value) > 0.001f) {
                    value = newProgress
                    onValueChangedListener?.invoke(value)
                }

                // Dynamic mercury tether pulling
                val dx = event.x - currentThumbX
                val dy = event.y - (height / 2f)
                thumbGlass.setSecondaryShape(
                    thumbRadius + dx,
                    thumbRadius + dy,
                    thumbRadius * 0.75f,
                    blend = 45f
                )
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                // Fluid snaps back into thumb
                thumbGlass.clearSecondaryShape()
                targetThumbX = progressToX(value)
                startSpring()
                return true
            }
        }
        return super.onTouchEvent(event)
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
        val springForce = -springStiffness * (currentThumbX - targetThumbX)
        val dampingForce = -c * thumbVelocity
        val acceleration = springForce + dampingForce

        thumbVelocity += acceleration * dt
        currentThumbX += thumbVelocity * dt

        invalidate()

        if (abs(currentThumbX - targetThumbX) < 0.2f && abs(thumbVelocity) < 0.2f) {
            currentThumbX = targetThumbX
            thumbVelocity = 0f
            isSpringRunning = false
            invalidate()
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val left = thumbRadius
        val right = width - thumbRadius

        // Inactive track
        val trackRect = RectF(left, centerY - trackHeight / 2f, right, centerY + trackHeight / 2f)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

        // Active track
        val activeRect = RectF(left, centerY - trackHeight / 2f, currentThumbX, centerY + trackHeight / 2f)
        canvas.drawRoundRect(activeRect, trackHeight / 2f, trackHeight / 2f, activeTrackPaint)

        // Render LiquidGlass Thumb at currentThumbX
        canvas.save()
        canvas.translate(currentThumbX - thumbRadius, centerY - thumbRadius)
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
