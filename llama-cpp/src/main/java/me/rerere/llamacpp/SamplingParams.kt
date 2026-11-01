package me.rerere.llamacpp

/** Applied to the native sampler for each turn, without changing the chat template blob. */
data class SamplingParams(val temperature: Float = 0.8f, val topP: Float = 0.95f, val topK: Int = 40) {
    init {
        require(temperature.isFinite() && temperature >= 0f) { "Invalid temperature" }
        require(topP.isFinite() && topP in 0f..1f) { "Invalid topP" }
        require(topK >= 0) { "Invalid topK" }
    }
}
