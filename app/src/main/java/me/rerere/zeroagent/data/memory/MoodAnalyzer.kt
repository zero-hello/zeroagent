package me.rerere.zeroagent.data.memory

import kotlin.math.exp

/** Semantic similarity to five prototypes, not a diagnosis or a verified user emotion. */
class MoodAnalyzer(private val prototypes: List<FloatArray>) {
    init { require(prototypes.size == LABELS.size); require(prototypes.map { it.size }.distinct().size == 1) }
    data class Mood(val tag: String, val weights: List<Float>, val confident: Boolean) {
        fun temperature(base: Float?): Float? = base?.let {
            (it + when { !confident -> 0f; tag in setOf("tired", "anxious") -> -.1f; tag == "excited" -> .05f; else -> 0f }).coerceIn(0f, 2f)
        }
    }
    fun analyze(vector: FloatArray): Mood {
        require(vector.size == prototypes.first().size && vector.all { it.isFinite() })
        val similarities = prototypes.map { p -> p.indices.sumOf { (p[it] * vector[it]).toDouble() } }
        val ordered = similarities.indices.sortedByDescending { similarities[it] }
        val peak = similarities.max()
        val values = similarities.map { exp((it - peak) * 12.0) }
        val total = values.sum()
        val clear = peak > .65 && similarities[ordered[0]] - similarities[ordered[1]] > .025
        return Mood(LABELS[ordered.first()], values.map { (it / total).toFloat() }, clear)
    }
    companion object {
        val LABELS = listOf("happy", "tired", "excited", "calm", "anxious")
        val PROTOTYPES = listOf("I feel happy, content and grateful. 我感到開心、滿足和感激。",
            "I feel tired, exhausted and need rest. 我很疲憊、累了，需要休息。",
            "I feel excited and full of anticipation. 我很興奮、充滿期待與活力。",
            "I feel calm, relaxed and peaceful. 我感到平靜、放鬆與安穩。",
            "I feel anxious, worried and stressed. 我很焦慮、擔心、壓力很大。")
    }
}

/** Preserve only a recent clear cue; ambiguous turns must not erase or indefinitely renew it. */
internal fun contextualMood(current: MoodAnalyzer.Mood, previous: MoodAnalyzer.Mood?, at: Long, now: Long): MoodAnalyzer.Mood =
    if (current.confident || previous?.confident != true || now < at || now - at > 20 * 60_000L) current else previous
