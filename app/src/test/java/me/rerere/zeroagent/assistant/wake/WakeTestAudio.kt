package me.rerere.zeroagent.assistant.wake

import kotlin.math.*
import kotlin.random.Random

internal object WakeTestAudio {
    fun phrase(scale: Double = .08, noise: Double = .00015): ShortArray {
        val random = Random(73)
        return ShortArray(32000) { i ->
            val t = i / 16000.0
            val envelope = if (t in .4..1.45) sin(PI * (t - .4) / 1.05).pow(2) * (.65 + .35 * sin(18 * t).pow(2)) else 0.0
            val signal = envelope * (sin(2 * PI * 190 * t) + .4 * sin(2 * PI * 570 * t)) * scale
            ((signal + random.nextDouble(-noise, noise)) * 32767).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
