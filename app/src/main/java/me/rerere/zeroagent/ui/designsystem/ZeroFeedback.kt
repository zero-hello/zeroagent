package me.rerere.zeroagent.ui.designsystem

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal enum class ZeroFeedbackEvent(val frequency: Int, val milliseconds: Int) {
    Send(620, 48), Tools(450, 45), Select(575, 42), Toggle(510, 34), Step(390, 30),
    Complete(740, 85), VoiceStart(560, 68), VoiceEnd(360, 72), VoiceInterrupt(420, 42),
}

private class ZeroSound(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val samples = java.util.concurrent.ConcurrentHashMap<ZeroFeedbackEvent, Int>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    init { pool.setOnLoadCompleteListener { _, id, result -> if (result == 0) loaded.add(id) } }

    suspend fun load() {
        mutex.lock()
        try {
            withContext(Dispatchers.IO) {
                ZeroFeedbackEvent.entries.forEach { event ->
                    if (samples.containsKey(event)) return@forEach
                    runCatching {
                        val file = File(app.cacheDir, "zero_feedback_v6_${event.name}.wav")
                        if (!file.isFile) file.writeBytes(wave(event))
                        pool.load(file.absolutePath, 1).takeIf { it > 0 }?.let { samples[event] = it }
                    }
                }
            }
        } finally { mutex.unlock() }
    }

    fun play(event: ZeroFeedbackEvent) {
        val id = samples[event] ?: return
        if (id in loaded && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        ) pool.play(id, .24f, .24f, 1, 0, 1f)
    }

    private fun wave(event: ZeroFeedbackEvent): ByteArray {
        val rate = 22_050
        val count = rate * event.milliseconds / 1_000
        return ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
            repeat(count) { sample ->
                val position = sample.toDouble() / count
                val envelope = (position * 20).coerceAtMost(1.0) * exp(-5.0 * position) * (1 - position)
                putShort((sin(2 * PI * event.frequency * sample / rate) * envelope * 8_000).toInt().toShort())
            }
        }.array()
    }

    companion object {
        private var instance: ZeroSound? = null
        @Synchronized fun get(context: Context) = instance ?: ZeroSound(context).also { instance = it }
    }
}

@Composable
internal fun PreloadZeroFeedback(enabled: Boolean) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(context, enabled) { if (enabled) ZeroSound.get(context).load() }
}

@Composable
internal fun rememberZeroFeedback(): (ZeroFeedbackEvent) -> Unit {
    val context = LocalContext.current.applicationContext
    val appearance = remember(context) { ZeroAppearance.get(context) }
    val haptic = LocalHapticFeedback.current
    return { event ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (appearance.soundEnabled) ZeroSound.get(context).play(event)
    }
}
