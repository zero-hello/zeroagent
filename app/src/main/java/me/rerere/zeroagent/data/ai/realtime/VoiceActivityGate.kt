package me.rerere.zeroagent.data.ai.realtime

/** Small allocation-free local activity gate; backend VAD still decides protocol turn boundaries. */
internal class VoiceActivityGate {
    enum class Edge { START, END }
    private var noise = .006f
    private var voiced = 0
    private var quiet = 0
    private var active = false
    fun reset() { voiced = 0; quiet = 0; active = false }
    fun accept(amplitude: Float, playback: Boolean, echoCancellation: Boolean): Edge? {
        if (!playback && amplitude < .02f) noise = .98f * noise + .02f * amplitude
        val threshold = maxOf(.018f, noise * if (playback && !echoCancellation) 6f else 3.5f)
        if (amplitude > threshold) {
            quiet = 0; voiced++
            if (!active && voiced >= 3) { active = true; return Edge.START }
        } else {
            voiced = 0; quiet++
            if (active && quiet >= 25) { active = false; return Edge.END }
        }
        return null
    }
}
