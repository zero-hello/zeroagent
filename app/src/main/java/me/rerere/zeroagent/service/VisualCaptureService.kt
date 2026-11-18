package me.rerere.zeroagent.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.zeroagent.R
import me.rerere.zeroagent.RouteActivity
import java.io.ByteArrayOutputStream

data class CapturedVisualFrame(val jpeg: ByteArray, val timestamp: Long)
data class VisualCaptureStatus(val active: Boolean = false, val frame: CapturedVisualFrame? = null, val error: String? = null)

/** User-initiated MediaProjection only; no accessibility or silent capture fallback. */
class VisualCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var lastFrame = 0L
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?) = null

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopCapture(); return START_NOT_STICKY }
        if (projection != null || stopping) return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("capture_data", Intent::class.java)
            else @Suppress("DEPRECATION") (intent?.getParcelableExtra("capture_data") as? Intent)
        if (data == null || intent?.getIntExtra("capture_code", 0) != Activity.RESULT_OK) {
            mutable.value = VisualCaptureStatus(error = "需要重新同意螢幕擷取。")
            stopSelf(); return START_NOT_STICKY
        }
        try {
            val channel = "visual_assistance"
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "畫面協助", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 198, Intent(this, RouteActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("open_visual_assist", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 199, Intent(this, javaClass).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("ZeroAgent 畫面協助").setContentText("僅在本機預覽；按停止保留目前畫面，再回 App 提問。")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "擷取並停止", stop).build()).build()
            startForeground(198, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            thread = HandlerThread("visual-capture").apply { start() }
            val handler = Handler(thread!!.looper)
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { Handler(Looper.getMainLooper()).post { stopCapture() } }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    try { resize(width, height, handler) }
                    catch (_: Exception) {
                        mutable.value = mutable.value.copy(error = "畫面尺寸變更失敗，請重新開始分享。")
                        Handler(Looper.getMainLooper()).post { stopCapture() }
                    }
                }
            }, handler)
            val metrics = resources.displayMetrics
            val captureReader = createReader(metrics.widthPixels, metrics.heightPixels, handler)
            display = projection!!.createVirtualDisplay("ZeroAgent visual assistance", captureReader.width,
                captureReader.height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                captureReader.surface, null, handler)
            mutable.value = VisualCaptureStatus(active = true)
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(error = "無法開始畫面擷取；請重新授權，或確認裝置未限制螢幕分享。")
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun createReader(width: Int, height: Int, handler: Handler): ImageReader {
        val scale = minOf(1f, 960f / maxOf(width, height).coerceAtLeast(1))
        return ImageReader.newInstance((width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1), PixelFormat.RGBA_8888, 2).apply {
            reader = this
            setOnImageAvailableListener({ source ->
                synchronized(this@VisualCaptureService) {
                if (stopping || reader !== source) return@synchronized
                try {
                source.acquireLatestImage()?.use { image ->
                    val now = SystemClock.elapsedRealtime()
                    if (stopping || now - lastFrame < 1000) return@use
                    lastFrame = now
                    val plane = image.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    try {
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                        try {
                            val bytes = ByteArrayOutputStream().use { output ->
                                cropped.compress(Bitmap.CompressFormat.JPEG, 80, output); output.toByteArray()
                            }
                            mutable.value = VisualCaptureStatus(active = true, frame = CapturedVisualFrame(bytes, System.currentTimeMillis()))
                        } finally { if (cropped !== padded) cropped.recycle() }
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(error = "此畫面無法擷取，受保護內容可能呈現黑色。")
                    } finally { padded.recycle() }
                }
                } catch (_: Exception) {
                    if (!stopping) mutable.value = mutable.value.copy(error = "畫面擷取中斷，請重新開始分享。")
                }
                }
            }, handler)
        }
    }

    @Synchronized
    private fun resize(width: Int, height: Int, handler: Handler) {
        if (stopping || display == null || width <= 0 || height <= 0) return
        val old = reader
        val next = createReader(width, height, handler)
        display?.resize(next.width, next.height, resources.displayMetrics.densityDpi)
        display?.surface = next.surface
        old?.close()
    }

    @Synchronized
    private fun stopCapture() {
        if (stopping) return
        stopping = true
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        runCatching { projection?.stop() }; projection = null
        thread?.quitSafely(); thread = null
        mutable.value = mutable.value.copy(active = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }

    companion object {
        const val STOP = "app.zeroagent.visual.STOP"
        private val mutable = MutableStateFlow(VisualCaptureStatus())
        val state = mutable.asStateFlow()
        fun clearFrame() { mutable.value = mutable.value.copy(frame = null) }
    }
}
